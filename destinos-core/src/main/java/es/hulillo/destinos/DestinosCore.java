package es.hulillo.destinos;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Destinos Core: el datapack "Destinos: Reinos" viene dentro del mod (se carga solo en todos los mundos) y
 * su función geo/territorio llama a /destinoscore territorio, que reparte el mundo siguiendo el terreno.
 */
public final class DestinosCore implements ModInitializer {
	public static final String MOD_ID = "destinos_core";
	public static final Logger LOG = LoggerFactory.getLogger("Destinos Core");
	private static final int TICKS_PRECARGA = 100;
	/** Una zona tarda decenas de segundos en un solo hilo: se reparten entre varios, dejando núcleos al juego. */
	private static final int HILOS = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() - 2));
	private static final ExecutorService HILO = Executors.newFixedThreadPool(HILOS, r -> {
		Thread t = new Thread(r, "Destinos Core - terreno");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private static volatile MapaTerreno mapa;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("destinoscore")
						.then(Commands.literal("territorio").executes(ctx -> territorio(ctx.getSource())))
						.then(Commands.literal("estado").executes(ctx -> estado(ctx.getSource())))));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> mapa = null);
		ServerTickEvents.END_SERVER_TICK.register(DestinosCore::precarga);
		LOG.info("Destinos Core listo: fronteras que siguen el terreno");
	}

	private static void precarga(MinecraftServer server) {
		if (server.getTickCount() % TICKS_PRECARGA != 0) {
			return;
		}
		MapaTerreno m = mapa(server);
		if (m == null) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.level().dimension() == Level.OVERWORLD) {
				m.precarga(p.getBlockX(), p.getBlockZ());
			}
		}
	}

	/** El mapa del mundo actual. Se rehace si cambia la sal (otro mundo). Null si el datapack aún no ha cargado. */
	private static MapaTerreno mapa(MinecraftServer server) {
		Integer sal = lee(server, "#sal", "dr.const");
		if (sal == null) {
			return null;
		}
		MapaTerreno m = mapa;
		if (m == null || m.sal() != sal) {
			m = new MapaTerreno(new MuestreadorMundo(server.overworld()), sal, HILO);
			mapa = m;
		}
		return m;
	}

	/**
	 * Entrada: #vx #vz (dr.calc) · Salida: #tdx #tdz #tkx #tkz #tw #tmar (dr.calc), como geo/territorio.
	 * Si la zona aún se está calculando deja #tlisto a 0 y no toca nada más: el datapack lo vuelve a intentar.
	 */
	private static int territorio(CommandSourceStack fuente) {
		MinecraftServer server = fuente.getServer();
		MapaTerreno m = mapa(server);
		Integer vx = lee(server, "#vx", "dr.calc");
		Integer vz = lee(server, "#vz", "dr.calc");
		if (m == null || vx == null || vz == null) {
			fuente.sendFailure(Component.literal("Destinos Core: falta la sal o #vx/#vz; ¿está cargado el datapack?"));
			return 0;
		}
		MapaTerreno.Resultado r = m.consultaSiLista(vx, vz);
		if (r == null) {
			escribe(server, "#tlisto", 0);
			return 1;
		}
		escribe(server, "#tlisto", 1);
		escribe(server, "#tdx", r.dcx());
		escribe(server, "#tdz", r.dcz());
		escribe(server, "#tkx", r.kx());
		escribe(server, "#tkz", r.kz());
		escribe(server, "#tw", r.salvaje() ? 1 : 0);
		escribe(server, "#tmar", r.oceano() ? 1 : 0);
		return 1;
	}

	private static int estado(CommandSourceStack fuente) {
		MapaTerreno m = mapa(fuente.getServer());
		String texto = m == null
				? "Destinos Core: esperando al datapack (no hay sal todavía)"
				: "Destinos Core: sal " + m.sal() + " · zonas de terreno calculadas: " + m.ventanasCalculadas();
		fuente.sendSuccess(() -> Component.literal(texto), false);
		return 1;
	}

	private static Integer lee(MinecraftServer server, String nombre, String objetivo) {
		Scoreboard sb = server.getScoreboard();
		Objective o = sb.getObjective(objetivo);
		if (o == null) {
			return null;
		}
		ReadOnlyScoreInfo info = sb.getPlayerScoreInfo(ScoreHolder.forNameOnly(nombre), o);
		return info == null ? null : info.value();
	}

	private static void escribe(MinecraftServer server, String nombre, int valor) {
		Scoreboard sb = server.getScoreboard();
		Objective o = sb.getObjective("dr.calc");
		if (o != null) {
			sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly(nombre), o).set(valor);
		}
	}
}
