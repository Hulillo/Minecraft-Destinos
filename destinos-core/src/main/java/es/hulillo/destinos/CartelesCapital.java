package es.hulillo.destinos;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Los carteles de las puertas de las capitales: "Bienvenido a <ducado>, capital del <reino>". Al generarse el
 * cartel no se sabe aún cómo se llama nada (los nombres los pone el datapack con el servidor en marcha), así que se
 * deja una marca en la cara de atrás (que en un cartel de pared no se ve) y la de delante se escribe cada vez que se
 * carga el chunk: así sigue al reino que mande hoy en el ducado. Si la zona aún no está calculada, se vuelve a probar
 * un poco después.
 */
final class CartelesCapital {
	private static final String MARCA = "destinos:capital";
	private static final int TICKS_REINTENTO = 100;
	private static final int MAX_PENDIENTES = 256;
	private static final Deque<BlockPos> PENDIENTES = new ArrayDeque<>();

	private CartelesCapital() {
	}

	/** Al generar: el cartel recién puesto en sitio lleva la marca detrás y un saludo provisional delante. */
	static void marca(WorldGenLevel level, BlockPos sitio) {
		if (level.getBlockEntity(sitio) instanceof SignBlockEntity s) {
			s.setText(new SignText().setMessage(0, Component.literal(MARCA)), false);
			s.setText(new SignText().setMessage(1, bienvenida()), true);
			s.setWaxed(true);
		}
	}

	/** Al cargar un chunk (en el hilo del servidor): escribe los carteles marcados que tenga. */
	static void alCargar(ServerLevel level, LevelChunk chunk) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		for (BlockEntity be : chunk.getBlockEntities().values()) {
			if (be instanceof SignBlockEntity s && marcado(s) && !escribe(level.getServer(), s) && PENDIENTES.size() < MAX_PENDIENTES) {
				PENDIENTES.add(s.getBlockPos());
			}
		}
	}

	/** Cada poco: los que no se pudieron escribir (zona sin calcular, ducado sin conocer), otra vez. */
	static void tick(MinecraftServer server) {
		if (PENDIENTES.isEmpty() || server.getTickCount() % TICKS_REINTENTO != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		for (int n = PENDIENTES.size(); n > 0; n--) {
			BlockPos p = PENDIENTES.poll();
			if (!level.isLoaded(p)) {
				continue;
			}
			if (level.getBlockEntity(p) instanceof SignBlockEntity s && marcado(s) && !escribe(server, s)) {
				PENDIENTES.add(p);
			}
		}
	}

	static void olvida() {
		PENDIENTES.clear();
	}

	private static boolean marcado(SignBlockEntity s) {
		return MARCA.equals(s.getBackText().getMessage(0, false).getString());
	}

	private static Component bienvenida() {
		return Component.translatableWithFallback("destinos.cartel.bienvenida", "Bienvenido a");
	}

	/** Escribe la cara de delante; false si aún no se puede. */
	private static boolean escribe(MinecraftServer server, SignBlockEntity s) {
		MapaTerreno mapa = DestinosCore.mapaActual();
		PoliticaMundo pol = DestinosCore.politica();
		if (mapa == null || pol == null) {
			return false;
		}
		BlockPos p = s.getBlockPos();
		MapaTerreno.Resultado r = mapa.consultaSiLista(p.getX(), p.getZ());
		if (r == null || r.oceano()) {
			return r != null;
		}
		Component[] nombres = pol.cartel(server, r.dcx(), r.dcz());
		if (nombres == null) {
			return false;
		}
		SignText delante = new SignText()
				.setMessage(0, bienvenida().copy().withStyle(ChatFormatting.ITALIC))
				.setMessage(1, nombres[0])
				.setMessage(2, nombres[1] == null ? Component.translatableWithFallback("destinos.cartel.libre", "tierra libre")
						: Component.translatableWithFallback("destinos.cartel.capital", "capital del"))
				.setMessage(3, nombres[1] == null ? Component.empty() : nombres[1]);
		s.setText(delante, true);
		s.setChanged();
		if (s.getLevel() != null) {
			s.getLevel().sendBlockUpdated(p, s.getBlockState(), s.getBlockState(), 3);
		}
		return true;
	}
}
