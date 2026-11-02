package es.hulillo.destinos;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Los carteles de las puertas de las capitales: "Bienvenido a <ducado>, capital del <reino>". Al generarse el
 * cartel no se sabe aún cómo se llama nada (los nombres los pone el datapack con el servidor en marcha), así que se
 * genera en blanco y se escribe al cargar el chunk; entonces se le deja una marca en la cara de atrás (que en un
 * cartel de pared no se ve) y la de delante se vuelve a escribir cada vez que se carga: así sigue al reino que mande
 * hoy en el ducado. Si la zona aún no está calculada, se vuelve a probar
 * un poco después.
 */
final class CartelesCapital {
	private static final String MARCA = "destinos:capital";
	private static final int TICKS_REINTENTO = 100;
	private static final int MAX_PENDIENTES = 256;
	private static final Deque<BlockPos> PENDIENTES = new ArrayDeque<>();

	private CartelesCapital() {
	}

	/** El cartel de las capitales: se genera en blanco y sin encerar. */
	static final Block CARTEL = Blocks.DARK_OAK_WALL_SIGN;

	/** Carteles recién cargados: se escriben en el siguiente tick. */
	private static final Deque<BlockPos> NUEVOS = new ArrayDeque<>();

	/**
	 * Al cargar un chunk (en el hilo del servidor): apunta los carteles marcados que tenga. No se escriben aquí: el
	 * chunk aún no cuenta como cargado y escribir el cartel lo marca como cambiado, que pide ese mismo chunk al mundo;
	 * con C2ME el servidor se quedaba esperándolo para siempre (el mundo congelado al volver a una capital).
	 */
	static void alCargar(ServerLevel level, LevelChunk chunk) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		for (BlockEntity be : chunk.getBlockEntities().values()) {
			if (be instanceof SignBlockEntity s && marcado(s) && NUEVOS.size() < MAX_PENDIENTES) {
				NUEVOS.add(s.getBlockPos());
			}
		}
	}

	/** Cada tick, los recién cargados; cada poco, los que no se pudieron escribir (zona sin calcular, ducado sin conocer). */
	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		while (!NUEVOS.isEmpty()) {
			BlockPos p = NUEVOS.poll();
			if (level.isLoaded(p)) {
				intenta(server, level, p);
			} else if (PENDIENTES.size() < MAX_PENDIENTES) {
				PENDIENTES.add(p); // aún no ha acabado de cargar: en el próximo reintento
			}
		}
		if (PENDIENTES.isEmpty() || server.getTickCount() % TICKS_REINTENTO != 0) {
			return;
		}
		for (int n = PENDIENTES.size(); n > 0; n--) {
			intenta(server, level, PENDIENTES.poll());
		}
	}

	private static void intenta(MinecraftServer server, ServerLevel level, BlockPos p) {
		if (!level.isLoaded(p)) {
			return;
		}
		if (level.getBlockEntity(p) instanceof SignBlockEntity s && marcado(s) && !escribe(server, s) && PENDIENTES.size() < MAX_PENDIENTES) {
			PENDIENTES.add(p);
		}
	}

	static void olvida() {
		NUEVOS.clear();
		PENDIENTES.clear();
	}

	/** Si es un cartel de capital: ya escrito (con la marca detrás) o recién generado (en blanco y sin encerar). */
	private static boolean marcado(SignBlockEntity s) {
		if (MARCA.equals(s.getBackText().getMessage(0, false).getString())) {
			return true;
		}
		return s.getBlockState().is(CARTEL) && !s.isWaxed() && vacio(s.getFrontText()) && vacio(s.getBackText());
	}

	private static boolean vacio(SignText t) {
		for (int i = 0; i < 4; i++) {
			if (!t.getMessage(i, false).getString().isEmpty()) {
				return false;
			}
		}
		return true;
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
		// la marca detrás (no se ve en un cartel de pared) y encerado, para que nadie lo cambie
		s.setText(new SignText().setMessage(0, Component.literal(MARCA)), false);
		s.setText(delante, true);
		s.setWaxed(true);
		return true;
	}
}
