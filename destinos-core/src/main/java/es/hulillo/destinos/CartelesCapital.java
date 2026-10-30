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
