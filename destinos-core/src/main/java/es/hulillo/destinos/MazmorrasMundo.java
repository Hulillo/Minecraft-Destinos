package es.hulillo.destinos;

import es.hulillo.destinos.politica.Grafo;
import es.hulillo.destinos.politica.Politica;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Las mazmorras despertadas: estructuras que ya trae el mundo (de vanilla o de mods: criptas, minas, templos,
 * mansiones…) que se apuntan al cargar sus chunks, sin buscar ni generar nada. Una mazmorra se despierta con un
 * encargo del tablón o, de noche, sola. Despierta, al entrar salen oleadas de bichos desde rincones que no se ven y al
 * final un jefe; al matarlo aparece un cofre con botín y monedas y la mazmorra queda limpia unos días.
 * Se guarda en el storage destinos:mazmorras. Solo se usa desde el hilo del servidor.
 */
final class MazmorrasMundo {
	private static final Identifier STORAGE = Identifier.fromNamespaceAndPath("destinos", "mazmorras");
	/** Estructuras que son mazmorra aunque su nombre no lo diga, y las que no lo son aunque lo parezca. */
	static final TagKey<Structure> SI = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "mazmorra"));
	static final TagKey<Structure> NO = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "no_mazmorra"));
	/** Nombres que nunca son mazmorra (pueblos, tabernas, pozos, portales…). */
	private static final String[] NUNCA = {"village", "tavern", "town", "house", "inn", "farm", "camp", "well", "portal", "treasure",
			"windmill", "capital", "market", "stable", "bridge", "road", "pier", "dock", "garden", "statue", "fossil", "geode"};
	static final String TAG = "destinos_mazmorra";
	static final int OLEADAS = 3;
	static final int JEFE = OLEADAS + 1;
	private static final int DIA = 24000;
	private static final int MAX_CONOCIDAS = 5000;
	/** Segundos sin nadie cerca antes de que la oleada en curso se pierda (y vuelva a empezar al volver). */
	private static final int PACIENCIA = 30;
	/** Dónde se busca de noche una mazmorra que despertar alrededor de cada jugador. */
	private static final int NOCHE_MIN = 150;
	private static final int NOCHE_MAX = 1000;
	private static final String[] RUMBOS = {"e", "se", "s", "so", "o", "no", "n", "ne"};

	private static MazmorrasMundo actual;

	/** Por clave mezclada (Geo.mezcla), para que el HashMap no choque. */
	private final Map<Long, Mazmorra> conocidas = new HashMap<>();
	private final Set<Long> despiertas = new HashSet<>();
	private final Map<Structure, Optional<Mazmorra.Tema>> temas = new HashMap<>();
	// lo de abajo no se guarda: es de la pelea en curso
	/** Quién ve la barra de cada mazmorra. */
	private final Map<Long, Set<UUID>> barras = new HashMap<>();
	private final Map<Long, Integer> ausente = new HashMap<>();
	private final Map<Long, Integer> espera = new HashMap<>();
	private final Map<Long, BlockPos> ultimaMuerte = new HashMap<>();
	private final Map<Long, List<UUID>> ayudantes = new HashMap<>();
	private long ultimaNoche = -1;
	private boolean sucio;
	private boolean llena;

	private MazmorrasMundo() {
	}

	static MazmorrasMundo de(MinecraftServer server) {
		if (actual == null) {
			actual = carga(server);
		}
		return actual;
	}

	/** El de ahora, o null si aún no se ha cargado (para no cargarlo desde donde no hay servidor a mano). */
	static MazmorrasMundo siHay() {
		return actual;
	}

	static void olvida() {
		actual = null;
	}

	private static long k(long clave) {
		return Geo.mezcla(clave);
	}

	Mazmorra get(long clave) {
		return conocidas.get(k(clave));
	}

	// ------------------------------------------------------------------ guardado

	private static MazmorrasMundo carga(MinecraftServer server) {
		MazmorrasMundo mm = new MazmorrasMundo();
		CompoundTag t = server.getCommandStorage().get(STORAGE);
		mm.ultimaNoche = t.getLongOr("noche", -1);
		ListTag l = t.getListOrEmpty("conocidas");
		for (int i = 0; i < l.size(); i++) {
			Mazmorra m = Mazmorra.carga(l.getCompoundOrEmpty(i));
			if (m != null) {
				mm.conocidas.put(k(m.clave), m);
				if (m.estado == Mazmorra.Estado.despierta) {
					mm.despiertas.add(m.clave);
				}
			}
		}
		return mm;
	}

	void guarda(MinecraftServer server) {
		CompoundTag t = new CompoundTag();
		t.putLong("noche", ultimaNoche);
		ListTag l = new ListTag();
		for (Mazmorra m : conocidas.values()) {
			l.add(m.guarda());
		}
		t.put("conocidas", l);
		server.getCommandStorage().set(STORAGE, t);
		sucio = false;
	}

	// ------------------------------------------------------------------ apuntar las que hay

	/** Al cargar un chunk: si en él empieza una estructura que parece mazmorra, se apunta (una sola vez). */
	static void alCargar(ServerLevel level, LevelChunk chunk) {
		// en un mundo nuevo los chunks del spawn se cargan antes de que exista el storage
		if (level.dimension() != Level.OVERWORLD || chunk.getAllStarts().isEmpty() || level.getServer().getCommandStorage() == null) {
			return;
		}
		de(level.getServer()).apunta(level, chunk);
	}

	private void apunta(ServerLevel level, LevelChunk chunk) {
		for (Map.Entry<Structure, StructureStart> en : chunk.getAllStarts().entrySet()) {
			StructureStart start = en.getValue();
			if (!start.isValid()) {
				continue;
			}
			Optional<Mazmorra.Tema> tema = tema(level, en.getKey());
			if (tema.isEmpty()) {
				continue;
			}
			long clave = start.getChunkPos().toLong();
			if (conocidas.containsKey(k(clave))) {
				continue;
			}
			if (conocidas.size() >= MAX_CONOCIDAS) {
				if (!llena) {
					llena = true;
					DestinosCore.LOG.info("Destinos Core: ya hay {} mazmorras apuntadas, no se apuntan más", MAX_CONOCIDAS);
				}
				return;
			}
			Identifier id = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(en.getKey());
			Mazmorra m = new Mazmorra(clave, String.valueOf(id), tema.get(), start.getBoundingBox(), estrellas(start));
			conocidas.put(k(clave), m);
			sucio = true;
		}
	}

	/** Lejos del centro del mundo, más difícil: de 1 a 5 estrellas. */
	private static int estrellas(StructureStart s) {
		int x = s.getBoundingBox().getCenter().getX();
		int z = s.getBoundingBox().getCenter().getZ();
		double d = Math.sqrt((double) x * x + (double) z * z);
		int e = 1 + (d > 1200 ? 1 : 0) + (d > 3000 ? 1 : 0) + (d > 6000 ? 1 : 0) + (Math.floorMod(Geo.hash(x, z, 77), 3) == 0 ? 1 : 0);
		return Math.min(5, e);
	}

	private Optional<Mazmorra.Tema> tema(ServerLevel level, Structure s) {
		return temas.computeIfAbsent(s, st -> {
			Registry<Structure> reg = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			var h = reg.wrapAsHolder(st);
			Identifier id = reg.getKey(st);
			if (id == null || h.is(NO) || h.is(StructureTags.VILLAGE) || id.getNamespace().equals(DestinosCore.MOD_ID)) {
				return Optional.empty();
			}
			String ruta = id.getPath();
			Mazmorra.Tema t = Mazmorra.Tema.de(ruta);
			if (h.is(SI)) {
				return Optional.of(t != null ? t : Mazmorra.Tema.cripta);
			}
			for (String n : NUNCA) {
				if (ruta.contains(n)) {
					return Optional.empty();
				}
			}
			return Optional.ofNullable(t);
		});
	}

	// ------------------------------------------------------------------ para los encargos

	/**
	 * La mazmorra apuntada más cercana a (x, z), a menos de r bloques, que no esté limpia ni en plena pelea y con
	 * como mucho maxEstrellas. Null si no hay.
	 */
	Mazmorra cercana(int x, int z, int r, int maxEstrellas) {
		Mazmorra mejor = null;
		long mejorD = (long) r * r;
		for (Mazmorra m : conocidas.values()) {
			if (m.estrellas > maxEstrellas || m.estado == Mazmorra.Estado.limpia || m.oleada > 0) {
				continue;
			}
			long dx = m.x() - x;
			long dz = m.z() - z;
			long d = dx * dx + dz * dz;
			if (d < mejorD) {
				mejorD = d;
				mejor = m;
			}
		}
		return mejor;
	}

	/** Un encargo la despierta hasta que venza (si ya estaba despierta, se queda hasta lo que dure más). */
	void despierta(MinecraftServer server, long clave, long hasta) {
		Mazmorra m = get(clave);
		if (m == null) {
			return;
		}
		if (m.estado == Mazmorra.Estado.limpia) {
			// el encargo manda: vuelve a llenarse de bichos
			m.oleada = 0;
		}
		m.estado = Mazmorra.Estado.despierta;
		m.hasta = Math.max(m.hasta, hasta);
		despiertas.add(clave);
		sucio = true;
	}

	/** Las despertadas solas de noche a menos de r bloques de p, de la más cercana a la más lejana. */
	List<Mazmorra> nocturnas(ServerPlayer p, int r, int max) {
		List<Mazmorra> l = new ArrayList<>();
		for (long c : despiertas) {
			Mazmorra m = get(c);
			if (m != null && m.nocturna && m.estado == Mazmorra.Estado.despierta && d2(m, p) < (double) r * r) {
				l.add(m);
			}
		}
		l.sort((a, b) -> Double.compare(d2(a, p), d2(b, p)));
		return l.size() > max ? l.subList(0, max) : l;
	}

	private static double d2(Mazmorra m, Entity p) {
		double dx = m.x() - p.getX();
		double dz = m.z() - p.getZ();
		return dx * dx + dz * dz;
	}

	/** {ducado, reino} de la mazmorra (reino = Politica.NADIE si es tierra salvaje), o null si la zona no está calculada. */
	static long[] dueno(MinecraftServer server, int x, int z) {
		MapaTerreno mapa = DestinosCore.mapa(server);
		PoliticaMundo pol = DestinosCore.politica();
		if (mapa == null || pol == null) {
			return null;
		}
		MapaTerreno.Resultado r = mapa.consultaSiLista(x, z);
		if (r == null) {
			return null;
		}
		r = pol.aplica(r);
		long ducado = Grafo.id(r.dcx(), r.dcz());
		if (pol.grafo().ducado(ducado) == null) {
			return null;
		}
		return new long[] {ducado, r.salvaje() || r.oceano() ? Politica.NADIE : Grafo.id(r.kx(), r.kz())};
	}

	/** El ducado de la mazmorra ("Leonledo"), o "tierras sin nombre". */
	static Component donde(MinecraftServer server, Mazmorra m) {
		long[] d = dueno(server, m.x(), m.z());
		PoliticaMundo pol = DestinosCore.politica();
		return d == null || pol == null ? Component.translatableWithFallback("destinos.mazmorra.sin_nombre", "tierras sin nombre")
				: pol.nombreDucado(server, d[0]);
	}

	/** "la cripta de Leonledo". */
	static Component nombre(MinecraftServer server, Mazmorra m) {
		long[] d = dueno(server, m.x(), m.z());
		PoliticaMundo pol = DestinosCore.politica();
		Component donde = d == null || pol == null
				? Component.translatableWithFallback("destinos.mazmorra.sin_nombre", "tierras sin nombre")
				: pol.nombreDucado(server, d[0]);
		return Component.translatableWithFallback("destinos.mazmorra." + m.tema.name(), "la mazmorra de %s", donde);
	}

	// ------------------------------------------------------------------ cada segundo

	void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		long ahora = level.getDayTime();
		noche(server, level, ahora);
		for (long c : new ArrayList<>(despiertas)) {
			Mazmorra m = get(c);
			if (m == null) {
				despiertas.remove(c);
				continue;
			}
			pelea(server, level, m, ahora);
		}
		if (sucio && server.getTickCount() % 2400 == 0) {
			guarda(server);
		}
	}

	private void pelea(MinecraftServer server, ServerLevel level, Mazmorra m, long ahora) {
		List<ServerPlayer> dentro = new ArrayList<>();
		List<ServerPlayer> cerca = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || !p.isAlive()) {
				continue;
			}
			if (m.dentro(p.getX(), p.getY(), p.getZ(), 3)) {
				dentro.add(p);
			}
			if (m.dentro(p.getX(), p.getY(), p.getZ(), 24)) {
				cerca.add(p);
			}
		}
		if (m.oleada == 0) {
			if (ahora > m.hasta) {
				duerme(server, m);
				return;
			}
			if (dentro.isEmpty() || level.getDifficulty() == Difficulty.PEACEFUL) {
				return;
			}
			m.oleada = 1;
			m.salieron = 0;
			m.vivos.clear();
			sucio = true;
			for (ServerPlayer p : dentro) {
				// título corto (el nombre largo con el ducado se salía de la pantalla) y el sitio en el subtítulo
				p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("☠ ").append(corto(m)).withStyle(ChatFormatting.DARK_RED)));
				p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatableWithFallback("destinos.mazmorra.despierta",
						"%s · algo se mueve en la oscuridad…", donde(server, m)).withStyle(ChatFormatting.GRAY)));
			}
			EncargosMundo.sonido(server, dentro.get(0), "minecraft:event.raid.horn", 0.8f, 0.7f);
		}
		Set<UUID> conBarra = barras.computeIfAbsent(m.clave, c -> new HashSet<>());
		for (UUID u : new ArrayList<>(conBarra)) {
			if (cerca.stream().noneMatch(p -> p.getUUID().equals(u))) {
				conBarra.remove(u);
				ocultaBarra(server, u);
			}
		}
		if (cerca.isEmpty()) {
			int a = ausente.merge(m.clave, 1, Integer::sum);
			if (a >= PACIENCIA) {
				// se han ido (o han muerto): la oleada en curso se pierde y vuelve a empezar cuando vuelvan
				ausente.remove(m.clave);
				quitaVivos(level, m);
				m.salieron = 0;
				sucio = true;
				if (ahora > m.hasta) {
					m.oleada = 0;
					duerme(server, m);
				}
			}
			return;
		}
		ausente.remove(m.clave);
		cerca.forEach(p -> conBarra.add(p.getUUID()));
		if (m.salieron == 0) {
			int e = espera.getOrDefault(m.clave, 0);
			if (e > 0) {
				espera.put(m.clave, e - 1);
				return;
			}
			ServerPlayer objetivo = dentro.isEmpty() ? cerca.get(0) : dentro.get(level.getRandom().nextInt(dentro.size()));
			sale(server, level, m, objetivo);
			sucio = true;
		}
		// los que ya no están (muertos, o idos a chunks sin cargar) dejan de contar
		m.vivos.removeIf(u -> {
			Entity e = level.getEntity(u);
			return e == null || !e.isAlive();
		});
		if (m.oleada == JEFE) {
			poderes(level, m, cerca);
		}
		PaqueteMazmorra barra = barra(server, level, m);
		cerca.stream().filter(p -> ServerPlayNetworking.canSend(p, PaqueteMazmorra.TIPO)).forEach(p -> ServerPlayNetworking.send(p, barra));
		if (!m.vivos.isEmpty() || m.salieron == 0) {
			return;
		}
		sucio = true;
		if (m.oleada < JEFE) {
			m.oleada++;
			m.salieron = 0;
			espera.put(m.clave, 3);
			for (ServerPlayer p : cerca) {
				p.displayClientMessage(m.oleada == JEFE
						? Component.translatableWithFallback("destinos.mazmorra.viene_jefe", "¡Algo grande se acerca!").withStyle(ChatFormatting.RED)
						: Component.translatableWithFallback("destinos.mazmorra.siguiente", "Oleada %s de %s", m.oleada, OLEADAS)
								.withStyle(ChatFormatting.YELLOW), true);
			}
			return;
		}
		limpia(server, level, m, cerca);
	}

	private static PaqueteMazmorra barra(MinecraftServer server, ServerLevel level, Mazmorra m) {
		if (m.oleada == JEFE) {
			Entity jefe = m.vivos.isEmpty() ? null : level.getEntity(m.vivos.get(0));
			return new PaqueteMazmorra(true, jefe != null ? jefe.getDisplayName() : corto(m),
					jefe instanceof LivingEntity l ? Math.max(0, l.getHealth() / l.getMaxHealth()) : 0, true);
		}
		return new PaqueteMazmorra(true, Component.translatableWithFallback("destinos.mazmorra.barra", "%s · oleada %s/%s", corto(m), m.oleada,
				OLEADAS), m.salieron == 0 ? 1 : (float) m.vivos.size() / m.salieron, false);
	}

	/** "La mina maldita": el nombre sin el ducado, para títulos y barras (que no se salgan de la pantalla). */
	static Component corto(Mazmorra m) {
		return Component.translatableWithFallback("destinos.mazmorra.corto." + m.tema.name(), "La mazmorra");
	}

	private static void ocultaBarra(MinecraftServer server, UUID u) {
		ServerPlayer p = server.getPlayerList().getPlayer(u);
		if (p != null && ServerPlayNetworking.canSend(p, PaqueteMazmorra.TIPO)) {
			ServerPlayNetworking.send(p, PaqueteMazmorra.NADA);
		}
	}

	private void duerme(MinecraftServer server, Mazmorra m) {
		boolean avisa = m.nocturna;
		m.estado = Mazmorra.Estado.dormida;
		m.nocturna = false;
		m.oleada = 0;
		despiertas.remove(m.clave);
		quitaBarra(server, m);
		sucio = true;
		if (avisa) {
			avisaCerca(server, m);
		}
	}

	private void quitaBarra(MinecraftServer server, Mazmorra m) {
		Set<UUID> b = barras.remove(m.clave);
		if (b != null) {
			b.forEach(u -> ocultaBarra(server, u));
		}
		ausente.remove(m.clave);
		espera.remove(m.clave);
		ayudantes.remove(m.clave);
	}

	private void quitaVivos(ServerLevel level, Mazmorra m) {
		for (UUID u : m.vivos) {
			Entity e = level.getEntity(u);
			if (e != null) {
				e.discard();
			}
		}
		m.vivos.clear();
		for (UUID u : ayudantes.getOrDefault(m.clave, List.of())) {
			Entity e = level.getEntity(u);
			if (e != null) {
				e.discard();
			}
		}
		ayudantes.remove(m.clave);
	}

	// ------------------------------------------------------------------ bichos

	/** Saca la oleada en curso (o el jefe) alrededor del jugador, en sitios de la mazmorra que él no ve. */
	private void sale(MinecraftServer server, ServerLevel level, Mazmorra m, ServerPlayer objetivo) {
		RandomSource r = level.getRandom();
		m.vivos.clear();
		if (m.oleada == JEFE) {
			BlockPos p = sitio(level, m, objetivo, r);
			Mob jefe = p == null ? null : bicho(level, m.tema.jefe, p, m, objetivo, true);
			if (jefe != null) {
				m.vivos.add(jefe.getUUID());
			}
			m.salieron = Math.max(1, m.vivos.size());
			EncargosMundo.sonido(server, objetivo, "minecraft:entity.wither.spawn", 0.5f, 1.2f);
			return;
		}
		int n = Math.min(10, 1 + m.estrellas + m.oleada);
		for (int i = 0; i < n; i++) {
			BlockPos p = sitio(level, m, objetivo, r);
			if (p == null) {
				continue;
			}
			EntityType<? extends Mob> tipo = m.tema.bichos.get(r.nextInt(m.tema.bichos.size()));
			Mob b = bicho(level, tipo, p, m, objetivo, false);
			if (b != null) {
				m.vivos.add(b.getUUID());
			}
		}
		// si no cabe nadie (estructura muy pequeña o tapiada), cuenta como superada
		m.salieron = Math.max(1, m.vivos.size());
	}

	/**
	 * Un sitio para un bicho: dentro de la mazmorra, a 6-16 bloques del jugador, con suelo y dos de aire (o de agua,
	 * si son ahogados). Mejor uno que no se vea desde los ojos del jugador; si no hay, uno a la vista.
	 */
	private static BlockPos sitio(ServerLevel level, Mazmorra m, ServerPlayer p, RandomSource r) {
		BlockPos aLaVista = null;
		BlockPos base = p.blockPosition();
		boolean agua = m.tema == Mazmorra.Tema.ahogados;
		for (int i = 0; i < 40; i++) {
			int dx = r.nextInt(33) - 16;
			int dz = r.nextInt(33) - 16;
			if (dx * dx + dz * dz < 36) {
				continue;
			}
			BlockPos s = suelo(level, base.offset(dx, 4, dz), agua);
			if (s == null || !m.dentro(s, 1)) {
				continue;
			}
			Vec3 ojo = p.getEyePosition();
			Vec3 cabeza = new Vec3(s.getX() + 0.5, s.getY() + 1.5, s.getZ() + 0.5);
			boolean oculto = level.clip(new ClipContext(ojo, cabeza, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
					.getType() != HitResult.Type.MISS;
			if (oculto) {
				return s;
			}
			if (aLaVista == null) {
				aLaVista = s;
			}
		}
		return aLaVista;
	}

	/** Bajando desde p (como mucho 10), el primer hueco de dos con suelo firme debajo. */
	private static BlockPos suelo(ServerLevel level, BlockPos p, boolean agua) {
		if (!level.isLoaded(p)) {
			return null;
		}
		for (int i = 0; i < 10; i++) {
			BlockPos a = p.below(i);
			BlockPos b = a.below();
			if (libre(level, a, agua) && libre(level, a.above(), agua) && level.getBlockState(b).isFaceSturdy(level, b, Direction.UP)) {
				return a;
			}
		}
		return null;
	}

	private static boolean libre(ServerLevel level, BlockPos p, boolean agua) {
		BlockState s = level.getBlockState(p);
		return s.getCollisionShape(level, p).isEmpty() && (s.getFluidState().isEmpty() || agua) && !s.is(Blocks.LAVA);
	}

	private Mob bicho(ServerLevel level, EntityType<? extends Mob> tipo, BlockPos p, Mazmorra m, ServerPlayer objetivo, boolean jefe) {
		Mob b = tipo.create(level, EntitySpawnReason.EVENT);
		if (b == null) {
			return null;
		}
		b.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
		b.finalizeSpawn(level, level.getCurrentDifficultyAt(p), EntitySpawnReason.EVENT, null);
		b.setPersistenceRequired();
		b.addTag(TAG);
		if (m.estrellas >= 3) {
			b.setItemSlot(EquipmentSlot.HEAD, new ItemStack(m.estrellas >= 5 ? Items.IRON_HELMET : Items.CHAINMAIL_HELMET));
		}
		if (m.estrellas >= 4) {
			b.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
		}
		if (jefe) {
			b.setCustomName(Component.translatableWithFallback("destinos.mazmorra.jefe." + m.tema.name(), "Señor de la mazmorra")
					.withStyle(ChatFormatting.DARK_PURPLE));
			b.setCustomNameVisible(true);
			vida(b, 40 + 25 * m.estrellas);
			AttributeInstance empuje = b.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (empuje != null) {
				empuje.setBaseValue(0.6);
			}
			if (tipo == EntityType.DROWNED) {
				b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
			} else if (tipo == EntityType.WITHER_SKELETON || tipo == EntityType.HUSK || tipo == EntityType.VINDICATOR) {
				b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(m.estrellas >= 4 ? Items.DIAMOND_AXE : Items.IRON_AXE));
			}
			b.setItemSlot(EquipmentSlot.CHEST, new ItemStack(m.estrellas >= 4 ? Items.DIAMOND_CHESTPLATE : Items.IRON_CHESTPLATE));
			b.setDropChance(EquipmentSlot.MAINHAND, 0.25f);
			b.setDropChance(EquipmentSlot.CHEST, 0.15f);
			b.setGlowingTag(true);
		}
		level.addFreshEntityWithPassengers(b);
		if (objetivo != null) {
			b.setTarget(objetivo);
		}
		return b;
	}

	private static void vida(Mob b, double v) {
		AttributeInstance a = b.getAttribute(Attributes.MAX_HEALTH);
		if (a != null) {
			a.setBaseValue(v);
			b.setHealth(b.getMaxHealth());
		}
	}

	/** El jefe llama a los suyos cada 8 segundos mientras le quede gente para llamar (como mucho 3 a la vez). */
	private void poderes(ServerLevel level, Mazmorra m, List<ServerPlayer> cerca) {
		if (m.vivos.isEmpty() || level.getServer().getTickCount() % 160 != 0) {
			return;
		}
		Entity jefe = level.getEntity(m.vivos.get(0));
		if (!(jefe instanceof Mob j)) {
			return;
		}
		List<UUID> suyos = ayudantes.computeIfAbsent(m.clave, c -> new ArrayList<>());
		suyos.removeIf(u -> {
			Entity e = level.getEntity(u);
			return e == null || !e.isAlive();
		});
		if (suyos.size() >= 3) {
			return;
		}
		ServerPlayer objetivo = cerca.get(level.getRandom().nextInt(cerca.size()));
		for (int i = 0; i < 2 && suyos.size() < 3; i++) {
			BlockPos p = suelo(level, j.blockPosition().offset(level.getRandom().nextInt(7) - 3, 2, level.getRandom().nextInt(7) - 3),
					m.tema == Mazmorra.Tema.ahogados);
			if (p == null) {
				continue;
			}
			Mob b = bicho(level, m.tema.bichos.get(0), p, m, objetivo, false);
			if (b != null) {
				suyos.add(b.getUUID());
			}
		}
		objetivo.displayClientMessage(Component.translatableWithFallback("destinos.mazmorra.llamada", "¡El jefe llama a los suyos!")
				.withStyle(ChatFormatting.RED), true);
	}

	/** Al morir algo de una mazmorra: si era el jefe, se apunta dónde (ahí sale el cofre). */
	void alMorir(LivingEntity muerto) {
		UUID u = muerto.getUUID();
		for (long c : despiertas) {
			Mazmorra m = get(c);
			if (m != null && m.vivos.remove(u)) {
				sucio = true;
				if (m.oleada == JEFE) {
					ultimaMuerte.put(m.clave, muerto.blockPosition());
				}
				return;
			}
		}
	}

	// ------------------------------------------------------------------ limpia

	private void limpia(MinecraftServer server, ServerLevel level, Mazmorra m, List<ServerPlayer> cerca) {
		boolean nocturna = m.nocturna;
		m.estado = Mazmorra.Estado.limpia;
		m.hasta = level.getDayTime() + 7L * DIA;
		m.oleada = 0;
		m.salieron = 0;
		m.nocturna = false;
		despiertas.remove(m.clave);
		quitaVivos(level, m);
		quitaBarra(server, m);
		sucio = true;
		BlockPos donde = ultimaMuerte.remove(m.clave);
		cofre(level, m, donde != null ? donde : cerca.get(0).blockPosition());
		Component nombre = nombre(server, m);
		for (ServerPlayer p : cerca) {
			p.connection.send(new ClientboundSetTitleTextPacket(Component.translatableWithFallback("destinos.mazmorra.limpia",
					"¡Mazmorra limpia!").withStyle(ChatFormatting.GOLD)));
			p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatableWithFallback("destinos.mazmorra.cofre",
					"Ha aparecido un cofre con el botín")));
		}
		EncargosMundo.sonido(server, cerca.get(0), "minecraft:ui.toast.challenge_complete", 0.7f, 0.9f);
		DestinosCore.LOG.info("Destinos Core: mazmorra limpia en {}, {} ({}, {}★)", m.x(), m.z(), m.estructura, m.estrellas);
		EncargosMundo.de(server).mazmorraLimpia(server, m, cerca, nocturna);
		if (nocturna) {
			avisaCerca(server, m);
		}
	}

	/** El cofre del final: el botín de su tipo de estructura y unas monedas. */
	private static void cofre(ServerLevel level, Mazmorra m, BlockPos cerca) {
		BlockPos sitio = null;
		for (int r = 0; r <= 3 && sitio == null; r++) {
			for (int dx = -r; dx <= r && sitio == null; dx++) {
				for (int dz = -r; dz <= r && sitio == null; dz++) {
					BlockPos s = suelo(level, cerca.offset(dx, 2, dz), false);
					if (s != null && level.getBlockState(s).canBeReplaced()) {
						sitio = s;
					}
				}
			}
		}
		if (sitio == null) {
			sitio = cerca;
		}
		level.setBlock(sitio, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
		RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), sitio, m.tema.botin);
		BlockEntity be = level.getBlockEntity(sitio);
		if (be instanceof Container c) {
			// primero el botín de la tabla, para que las monedas y las rarezas vayan a los huecos que deja
			if (be instanceof RandomizableContainer rc) {
				rc.unpackLootTable(null);
			}
			List<ItemStack> extra = new ArrayList<>(Monedas.pilas(5 + 3 * m.estrellas * m.estrellas));
			extra.addAll(rarezas(level, m));
			RandomSource r = level.getRandom();
			for (ItemStack s : extra) {
				List<Integer> huecos = new ArrayList<>();
				for (int i = 0; i < c.getContainerSize(); i++) {
					if (c.getItem(i).isEmpty()) {
						huecos.add(i);
					}
				}
				if (huecos.isEmpty()) {
					break;
				}
				c.setItem(huecos.get(r.nextInt(huecos.size())), s);
			}
		}
	}

	/** Una rareza: qué objeto, cuántos (de min a max), cuánto sale (peso) y desde cuántas estrellas. */
	private record Rareza(net.minecraft.world.item.Item item, int min, int max, int peso, int estrellas) {
	}

	private static final List<Rareza> RAREZAS = List.of(
			new Rareza(Items.DIAMOND, 1, 3, 10, 1),
			new Rareza(Items.EMERALD, 2, 6, 10, 1),
			new Rareza(Items.GOLDEN_APPLE, 1, 2, 8, 1),
			new Rareza(Items.EXPERIENCE_BOTTLE, 4, 10, 8, 1),
			new Rareza(Items.NAME_TAG, 1, 1, 5, 1),
			new Rareza(Items.SADDLE, 1, 1, 4, 1),
			new Rareza(Items.BOOK, 1, 1, 9, 1),
			new Rareza(Items.DIAMOND_HORSE_ARMOR, 1, 1, 3, 2),
			new Rareza(Items.IRON_SWORD, 1, 1, 7, 1),
			new Rareza(Items.IRON_AXE, 1, 1, 5, 1),
			new Rareza(Items.BOW, 1, 1, 6, 1),
			new Rareza(Items.CROSSBOW, 1, 1, 4, 2),
			new Rareza(Items.IRON_CHESTPLATE, 1, 1, 4, 2),
			new Rareza(Items.IRON_HELMET, 1, 1, 4, 1),
			new Rareza(Items.SHIELD, 1, 1, 4, 1),
			new Rareza(Items.DIAMOND_SWORD, 1, 1, 4, 3),
			new Rareza(Items.DIAMOND_PICKAXE, 1, 1, 3, 3),
			new Rareza(Items.DIAMOND_CHESTPLATE, 1, 1, 2, 4),
			new Rareza(Items.DIAMOND_HELMET, 1, 1, 2, 4),
			new Rareza(Items.TRIDENT, 1, 1, 1, 4),
			new Rareza(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 1, 1, 6, 2),
			new Rareza(Items.MUSIC_DISC_OTHERSIDE, 1, 1, 3, 3),
			new Rareza(Items.MUSIC_DISC_PIGSTEP, 1, 1, 2, 3),
			new Rareza(Items.TOTEM_OF_UNDYING, 1, 1, 2, 4),
			new Rareza(Items.ENCHANTED_GOLDEN_APPLE, 1, 1, 1, 4),
			new Rareza(Items.NETHERITE_SCRAP, 1, 2, 2, 5),
			new Rareza(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 1, 1, 5));

	/** Plantillas de adorno de armadura: una al azar cuando sale la de adorno. */
	private static final List<net.minecraft.world.item.Item> ADORNOS = List.of(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
			Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
			Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
			Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
			Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
			Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE);

	/**
	 * Las rarezas del cofre: 2 a 4 según las estrellas, sacadas por peso entre las que tocan a esas estrellas. El
	 * libro sale encantado (más fuerte cuantas más estrellas) y las mazmorras de ahogados pueden dar un corazón del mar.
	 */
	private static List<ItemStack> rarezas(ServerLevel level, Mazmorra m) {
		RandomSource r = level.getRandom();
		List<Rareza> posibles = new ArrayList<>();
		for (Rareza x : RAREZAS) {
			if (m.estrellas >= x.estrellas()) {
				posibles.add(x);
			}
		}
		if (m.tema == Mazmorra.Tema.ahogados) {
			posibles.add(new Rareza(Items.HEART_OF_THE_SEA, 1, 1, 4, 1));
		}
		int total = posibles.stream().mapToInt(Rareza::peso).sum();
		List<ItemStack> l = new ArrayList<>();
		int n = 2 + m.estrellas / 2;
		for (int i = 0; i < n; i++) {
			int t = r.nextInt(total);
			Rareza elegida = posibles.get(0);
			for (Rareza x : posibles) {
				t -= x.peso();
				if (t < 0) {
					elegida = x;
					break;
				}
			}
			net.minecraft.world.item.Item item = elegida.item() == Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE
					? ADORNOS.get(r.nextInt(ADORNOS.size())) : elegida.item();
			ItemStack s = new ItemStack(item, elegida.min() + r.nextInt(elegida.max() - elegida.min() + 1));
			if (item == Items.BOOK || s.isDamageableItem()) {
				// el libro y las armas y armaduras salen encantadas, más cuantas más estrellas
				s = EnchantmentHelper.enchantItem(r, s, 10 + 6 * m.estrellas, level.registryAccess(), Optional.empty());
			}
			l.add(s);
		}
		return l;
	}

	// ------------------------------------------------------------------ de noche

	/** Al caer la noche, cerca de cada jugador alguna mazmorra puede despertarse sola (calavera en el mapa). */
	private void noche(MinecraftServer server, ServerLevel level, long ahora) {
		long dia = Math.floorDiv(ahora, DIA);
		long hora = Math.floorMod(ahora, DIA);
		if (hora < 13000 || hora > 22000 || dia == ultimaNoche) {
			return;
		}
		ultimaNoche = dia;
		sucio = true;
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		RandomSource r = level.getRandom();
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || r.nextInt(100) >= 40 || nocturnas(p, 1500, 9).size() >= 2) {
				continue;
			}
			List<Mazmorra> candidatas = new ArrayList<>();
			for (Mazmorra m : conocidas.values()) {
				double d = d2(m, p);
				boolean libre = m.estado == Mazmorra.Estado.dormida || m.estado == Mazmorra.Estado.limpia && ahora > m.hasta;
				if (libre && d > (double) NOCHE_MIN * NOCHE_MIN && d < (double) NOCHE_MAX * NOCHE_MAX) {
					candidatas.add(m);
				}
			}
			if (candidatas.isEmpty()) {
				continue;
			}
			Mazmorra m = candidatas.get(r.nextInt(candidatas.size()));
			m.estado = Mazmorra.Estado.despierta;
			m.nocturna = true;
			m.oleada = 0;
			m.hasta = ahora + 3L * DIA;
			despiertas.add(m.clave);
			DestinosCore.LOG.info("Destinos Core: de noche se despierta la mazmorra de {}, {} ({})", m.x(), m.z(), m.estructura);
			Component nombre = nombre(server, m);
			for (ServerPlayer q : level.players()) {
				if (d2(m, q) < 1500.0 * 1500.0) {
					q.sendSystemMessage(Component.translatableWithFallback("destinos.mazmorra.noche", "☠ Algo se ha despertado en %s · %s",
							nombre, EncargosMundo.distancia(m.x() - q.getX(), m.z() - q.getZ())).withStyle(ChatFormatting.DARK_RED));
				}
			}
			avisaCerca(server, m);
		}
	}

	/** Los jugadores de cerca vuelven a recibir su lista (para la calavera del mapa y del HUD). */
	private static void avisaCerca(MinecraftServer server, Mazmorra m) {
		for (ServerPlayer p : server.overworld().players()) {
			if (d2(m, p) < 2500.0 * 2500.0) {
				EncargosMundo.de(server).envia(server, p);
			}
		}
	}

	// ------------------------------------------------------------------ comandos

	/** /destinoscore mazmorras */
	String cuenta(ServerPlayer p) {
		int[] e = new int[3];
		Map<Mazmorra.Tema, Integer> porTema = new HashMap<>();
		for (Mazmorra m : conocidas.values()) {
			e[m.estado.ordinal()]++;
			porTema.merge(m.tema, 1, Integer::sum);
		}
		StringBuilder b = new StringBuilder("Destinos Core: mazmorras · " + conocidas.size() + " apuntadas (" + e[0] + " dormidas, " + e[1]
				+ " despiertas, " + e[2] + " limpias) · " + porTema);
		if (p != null) {
			Mazmorra c = cercana(p.getBlockX(), p.getBlockZ(), 100000, 5);
			if (c != null) {
				b.append(" · la más cercana: ").append(c.estructura).append(" en ").append(c.x()).append(", ").append(c.z())
						.append(" (").append(c.tema).append(", ").append(c.estrellas).append("★)");
			}
		}
		return b.toString();
	}

	/** /destinoscore mazmorras despierta: despierta la más cercana al jugador durante un día, para probar. */
	String despiertaCercana(MinecraftServer server, ServerPlayer p) {
		Mazmorra c = cercana(p.getBlockX(), p.getBlockZ(), 100000, 5);
		if (c == null) {
			return "Destinos Core: no hay ninguna mazmorra apuntada";
		}
		c.nocturna = true;
		despierta(server, c.clave, server.overworld().getDayTime() + DIA);
		avisaCerca(server, c);
		return "Destinos Core: despierta " + c.estructura + " en " + c.x() + ", " + c.z() + " (" + c.tema + ", " + c.estrellas + "★)";
	}

	/**
	 * /destinoscore mazmorras prueba (para la CI, sin jugadores): en la mazmorra apuntada más cercana a pos saca una
	 * oleada y el jefe en su centro, los quita y pone el cofre del final. Dice cuántos han salido.
	 */
	String prueba(ServerLevel level, BlockPos pos) {
		Mazmorra m = cercana(pos.getX(), pos.getZ(), 100000, 5);
		if (m == null) {
			return "Destinos Core: mazmorra de prueba · no hay ninguna apuntada";
		}
		BlockPos centro = new BlockPos(m.x(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				m.x(), m.z()), m.z());
		if (!level.isLoaded(centro)) {
			return "Destinos Core: mazmorra de prueba · " + m.estructura + " no está cargada";
		}
		int n = 0;
		List<Mob> sacados = new ArrayList<>();
		for (EntityType<? extends Mob> t : m.tema.bichos) {
			Mob b = bicho(level, t, centro, m, null, false);
			if (b != null) {
				sacados.add(b);
			}
		}
		Mob jefe = bicho(level, m.tema.jefe, centro, m, null, true);
		boolean jefeBien = jefe != null && jefe.getMaxHealth() >= 40;
		if (jefe != null) {
			sacados.add(jefe);
		}
		n = sacados.size();
		sacados.forEach(Entity::discard);
		cofre(level, m, centro.above(2));
		return "Destinos Core: mazmorra de prueba · " + m.estructura + " (" + m.tema + ", " + m.estrellas + "★) · " + n + " bichos · jefe "
				+ (jefeBien ? "bien" : "MAL") + " · cofre en " + centro.toShortString();
	}

	static String rumbo(double dx, double dz) {
		return RUMBOS[Math.floorMod((int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4)), 8)];
	}

	static MutableComponent estrellasTexto(int e) {
		return Component.literal("★".repeat(e) + "☆".repeat(Math.max(0, 5 - e))).withStyle(ChatFormatting.GOLD);
	}
}
