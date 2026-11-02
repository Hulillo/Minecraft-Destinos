package es.hulillo.destinos;

import es.hulillo.destinos.politica.Politica;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Lo de los encargos de embajada que no es del tablón: la carta sellada (un papel con brillo que lleva el número del
 * encargo), los cazarrecompensas que salen una vez por el camino y los guardias de las capitales, que salen contra
 * quien tiene mala reputación con su reino o viene de un reino en guerra con él.
 */
final class Embajadas {
	private static final String CLAVE_CARTA = "destinos_carta";
	static final String GUARDIA = "destinos_guardia";
	static final String CAZADOR = "destinos_cazador";
	/** Con esta reputación o menos, los guardias de las capitales de ese reino van a por ti. */
	static final int REP_GUARDIAS = -20;
	/** Radio de una capital para sus guardias, y tiempo sin volver a salir contra el mismo jugador. */
	private static final int RADIO_GUARDIAS = 72;
	private static final int ESPERA_GUARDIAS = 12000;

	/** Guardias en marcha: a qué jugador persiguen. No se guardan: al reiniciar se van. */
	private static final Map<UUID, UUID> guardias = new HashMap<>();
	/** Hasta cuándo (hora del mundo) no vuelven a salir guardias contra cada jugador. */
	private static final Map<UUID, Long> calma = new HashMap<>();

	private Embajadas() {
	}

	static void olvida() {
		guardias.clear();
		calma.clear();
	}

	// ------------------------------------------------------------------ la carta

	static ItemStack carta(Component de, Component para, int id) {
		ItemStack s = new ItemStack(Items.PAPER);
		s.set(DataComponents.CUSTOM_NAME, Component.translatableWithFallback("destinos.embajada.carta", "✉ Carta sellada del %s al %s", de, para)
				.withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GOLD)));
		s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		CompoundTag t = new CompoundTag();
		t.putInt(CLAVE_CARTA, id);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
		return s;
	}

	private static boolean esCarta(ItemStack s, int id) {
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		return d != null && d.copyTag().getIntOr(CLAVE_CARTA, -1) == id;
	}

	static void da(ServerPlayer p, ItemStack carta) {
		if (!p.getInventory().add(carta)) {
			p.drop(carta, false);
		}
	}

	static boolean tiene(ServerPlayer p, int id) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (esCarta(p.getInventory().getItem(i), id)) {
				return true;
			}
		}
		return false;
	}

	/** Quita la carta del inventario (al entregarla, o al dejar o perder el encargo). */
	static void quita(ServerPlayer p, int id) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (esCarta(p.getInventory().getItem(i), id)) {
				p.getInventory().setItem(i, ItemStack.EMPTY);
			}
		}
	}

	/**
	 * Si el jugador está ante el trono de un castillo de capital: a 4 bloques o menos del respaldo de oro (dos bloques
	 * de oro uno encima del otro, que solo lleva el trono).
	 */
	static boolean junto(ServerLevel level, ServerPlayer p) {
		BlockPos c = p.blockPosition();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				for (int dy = -1; dy <= 2; dy++) {
					BlockPos b = c.offset(dx, dy, dz);
					if (level.getBlockState(b).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK)
							&& level.getBlockState(b.above()).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ cazarrecompensas

	/** Una banda de cazarrecompensas sale a por el jugador desde detrás de él. Devuelve cuántos han salido. */
	static int emboscada(ServerLevel level, ServerPlayer p, int estrellas) {
		int n = 2 + (estrellas + 1) / 2;
		int salen = 0;
		double giro = Math.toRadians(p.getYRot() + 90);
		for (int i = 0; i < n; i++) {
			// detrás del jugador (la mirada es -sin, cos), algo abiertos
			double a = giro + Math.PI + (i - n / 2.0) * 0.35;
			BlockPos s = suelo(level, p.getBlockX() + (int) (Math.cos(a) * 22), p.getBlockZ() + (int) (Math.sin(a) * 22));
			if (s == null) {
				continue;
			}
			Mob m = (i % 2 == 0 ? EntityType.VINDICATOR : EntityType.PILLAGER).create(level, EntitySpawnReason.EVENT);
			if (m == null) {
				continue;
			}
			m.snapTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0, 0);
			m.finalizeSpawn(level, level.getCurrentDifficultyAt(s), EntitySpawnReason.EVENT, null);
			m.setPersistenceRequired();
			m.addTag(CAZADOR);
			m.setCustomName(Component.translatableWithFallback("destinos.embajada.cazador", "Cazarrecompensas"));
			m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(estrellas >= 3 ? Items.CHAINMAIL_HELMET : Items.LEATHER_HELMET));
			if (estrellas >= 4) {
				m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
			}
			m.setTarget(p);
			level.addFreshEntityWithPassengers(m);
			salen++;
		}
		return salen;
	}

	private static BlockPos suelo(ServerLevel level, int x, int z) {
		BlockPos p = new BlockPos(x, 64, z);
		if (!level.isLoaded(p)) {
			return null;
		}
		BlockPos s = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		return level.getBlockState(s.below()).getFluidState().isEmpty() ? s : null;
	}

	// ------------------------------------------------------------------ guardias

	/**
	 * Cada 5 s: si un jugador entra en una capital cuyo reino no lo quiere (reputación de REP_GUARDIAS o menos, o
	 * trae una carta de un reino en guerra con él), salen gólems de la guardia a por él. Los que ya han salido siguen
	 * con él mientras esté cerca y se van cuando se aleja.
	 */
	static void tick(MinecraftServer server, EncargosMundo em, PoliticaMundo pol) {
		if (server.getTickCount() % 100 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		for (Iterator<Map.Entry<UUID, UUID>> it = guardias.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, UUID> g = it.next();
			Entity e = level.getEntity(g.getKey());
			ServerPlayer p = server.getPlayerList().getPlayer(g.getValue());
			if (!(e instanceof Mob m) || !m.isAlive()) {
				it.remove();
			} else if (p == null || p.level() != level || p.distanceToSqr(m) > 64 * 64 || p.isDeadOrDying()) {
				m.discard();
				it.remove();
			} else {
				m.setTarget(p);
			}
		}
		long ahora = level.getDayTime();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.level() != level || p.isCreative() || p.isSpectator() || calma.getOrDefault(p.getUUID(), 0L) > ahora) {
				continue;
			}
			long[] c = pol.capitalCerca(p.getBlockX(), p.getBlockZ(), RADIO_GUARDIAS);
			if (c == null || c[0] == Politica.SALVAJE) {
				continue;
			}
			long reino = c[0];
			boolean malaRep = em.rep(p, reino) <= REP_GUARDIAS;
			boolean deEnemigo = em.cartaDeEnemigo(p, reino, pol);
			if (!malaRep && !deEnemigo) {
				continue;
			}
			calma.put(p.getUUID(), ahora + ESPERA_GUARDIAS);
			int n = em.rep(p, reino) <= -50 ? 3 : 2;
			int salen = 0;
			for (int i = 0; i < n; i++) {
				double a = level.getRandom().nextDouble() * Math.PI * 2;
				BlockPos s = suelo(level, p.getBlockX() + (int) (Math.cos(a) * 14), p.getBlockZ() + (int) (Math.sin(a) * 14));
				if (s == null) {
					continue;
				}
				Mob g = EntityType.IRON_GOLEM.create(level, EntitySpawnReason.EVENT);
				if (g == null) {
					continue;
				}
				g.snapTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0, 0);
				g.addTag(GUARDIA);
				g.setCustomName(Component.translatableWithFallback("destinos.embajada.guardia", "Guardia del %s", pol.nombreReino(server, reino)));
				g.setTarget(p);
				level.addFreshEntity(g);
				guardias.put(g.getUUID(), p.getUUID());
				salen++;
			}
			if (salen > 0) {
				p.displayClientMessage(Component.translatableWithFallback(deEnemigo ? "destinos.embajada.guardias_guerra" : "destinos.embajada.guardias",
						deEnemigo ? "¡Guardias! El %s está en guerra con quien te manda." : "¡Guardias! En el %s no te quieren por aquí.",
						pol.nombreReino(server, reino)).withStyle(ChatFormatting.RED), true);
				EncargosMundo.sonido(server, p, "minecraft:entity.iron_golem.repair", 1, 0.6f);
			}
		}
	}

	/** Al morir un guardia, se suelta. */
	static void alMorir(Entity muerto) {
		guardias.remove(muerto.getUUID());
	}
}
