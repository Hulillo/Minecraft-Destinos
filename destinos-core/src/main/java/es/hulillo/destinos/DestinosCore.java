package es.hulillo.destinos;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Destinos Core: el datapack "Destinos: Reinos" viene dentro del mod (se carga solo en todos los mundos) y
 * su función geo/territorio llama a /destinoscore territorio, que reparte el mundo siguiendo el terreno.
 * Además manda a los clientes con el mod las zonas calculadas, para pintar los reinos en sus mapas, y lleva la
 * política viva (PoliticaMundo): guerras, conquistas y alianzas que cambian quién manda en cada ducado.
 */
public final class DestinosCore implements ModInitializer {
	public static final String MOD_ID = "destinos_core";
	public static final Logger LOG = LoggerFactory.getLogger("Destinos Core");
	private static final int TICKS_PRECARGA = 100;
	/** Las zonas se calculan en segundo plano en 1 o 2 hilos, dejando el resto de núcleos a la carga de chunks. */
	private static final int HILOS = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 2));
	private static final ExecutorService HILO = Executors.newFixedThreadPool(HILOS, r -> {
		Thread t = new Thread(r, "Destinos Core - terreno");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private static volatile MapaTerreno mapa;
	private static final EnvioTerritorios ENVIO = new EnvioTerritorios();
	/** Mapa para el que se mandaron las zonas: si cambia (otra sal), se manda todo otra vez. */
	private static MapaTerreno mapaEnviado;
	private static PoliticaMundo politica;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("destinoscore")
						.then(Commands.literal("territorio").executes(ctx -> territorio(ctx.getSource())))
						.then(Commands.literal("estado").executes(ctx -> estado(ctx.getSource())))
						.then(Commands.literal("politica").executes(ctx -> politica(ctx.getSource()))
								.then(Commands.literal("reinos").executes(ctx -> ranking(ctx.getSource())))
								.then(Commands.literal("ritmo")
										.then(Commands.argument("dias", IntegerArgumentType.integer(1, 30))
												.executes(ctx -> ritmo(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "dias"))))))
						.then(Commands.literal("turno").executes(ctx -> turno(ctx.getSource(), 1))
								.then(Commands.argument("n", IntegerArgumentType.integer(1, 500))
										.executes(ctx -> turno(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))))
						.then(Commands.literal("cronica").executes(ctx -> cronica(ctx.getSource())))));
		PayloadTypeRegistry.playS2C().register(PaqueteTerritorio.TIPO, PaqueteTerritorio.CODEC);
		PayloadTypeRegistry.playS2C().register(PaquetePolitica.TIPO, PaquetePolitica.CODEC);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ENVIO.olvida(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (politica != null) {
				politica.guarda(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			mapa = null;
			politica = null;
			mapaEnviado = null;
			ENVIO.olvidaTodo();
		});
		ServerTickEvents.END_SERVER_TICK.register(DestinosCore::tick);
		LOG.info("Destinos Core listo: fronteras que siguen el terreno y política viva");
	}

	private static void tick(MinecraftServer server) {
		PoliticaMundo pol = politica;
		// los turnos se miran en cada tick para que /destinoscore turno responda enseguida
		if (pol != null && mapa != null) {
			// las zonas con ducados de otro dueño se vuelven a mandar a los mapas de los clientes
			int antes = pol.turno();
			ENVIO.olvidaZonas(pol.tick(server));
			if (pol.turno() != antes) {
				// guerras y "cambios recientes" del mapa: el estado político nuevo a todos
				ENVIO.olvidaPolitica();
			}
		}
		if (server.getTickCount() % TICKS_PRECARGA == 0) {
			precarga(server);
		}
	}

	private static void precarga(MinecraftServer server) {
		MapaTerreno m = mapa(server);
		if (m == null) {
			return;
		}
		if (m != mapaEnviado) {
			ENVIO.olvidaTodo();
			mapaEnviado = m;
		}
		PoliticaMundo pol = politica;
		if (pol.alimenta(server, m)) {
			// capitales nuevas: el estado político (con sus coronas) otra vez a todos
			ENVIO.olvidaPolitica();
		}
		PaquetePolitica estado = pol.estadoMapas();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.level().dimension() == Level.OVERWORLD) {
				m.precarga(p.getBlockX(), p.getBlockZ());
				m.precargaLejos(p.getBlockX(), p.getBlockZ());
				ENVIO.envia(m, pol, estado, p);
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
			politica = PoliticaMundo.de(server, sal);
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
		r = politica.aplica(r);
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
		ServerPlayer p = fuente.getPlayer();
		if (p != null && p.level().dimension() == Level.OVERWORLD) {
			// para comprobar en cada mundo que la altura estimada sin generar chunks se parece a la de verdad
			int x = p.getBlockX();
			int z = p.getBlockZ();
			MapaTerreno.Casilla c = new MuestreadorMundo(fuente.getServer().overworld()).muestra(x, z);
			int real = p.level().getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
			String alturas = "Aquí: suelo estimado " + c.altura() + " · suelo real " + real + (c.oceano() ? " · lo toma por MAR" : "");
			fuente.sendSuccess(() -> Component.literal(alturas), false);
		}
		return 1;
	}

	private static int politica(CommandSourceStack fuente) {
		MapaTerreno m = mapa(fuente.getServer());
		if (m == null) {
			fuente.sendFailure(Component.literal("Destinos Core: esperando al datapack (no hay sal todavía)"));
			return 0;
		}
		politica.estado(fuente, m);
		return 1;
	}

	/** Los comandos que cambian el mundo: desde la consola, funciones o un jugador en creativo. */
	private static boolean puedeTocar(CommandSourceStack fuente) {
		ServerPlayer p = fuente.getPlayer();
		if (p == null || p.isCreative()) {
			return true;
		}
		fuente.sendFailure(Component.translatableWithFallback("destinos.politica.permiso",
				"Solo en modo creativo o desde la consola."));
		return false;
	}

	private static int turno(CommandSourceStack fuente, int n) {
		if (!puedeTocar(fuente) || mapa(fuente.getServer()) == null) {
			return 0;
		}
		politica.fuerza(n, fuente);
		fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.forzado",
				"Pasan %s turnos de política…", n), false);
		return 1;
	}

	private static int ritmo(CommandSourceStack fuente, int dias) {
		if (!puedeTocar(fuente) || mapa(fuente.getServer()) == null) {
			return 0;
		}
		politica.ritmo(fuente.getServer(), dias);
		fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.ritmo",
				"Ahora pasa un turno de política cada %s días de juego.", dias), false);
		return 1;
	}

	private static int ranking(CommandSourceStack fuente) {
		if (mapa(fuente.getServer()) == null) {
			return 0;
		}
		politica.ranking(fuente);
		return 1;
	}

	private static int cronica(CommandSourceStack fuente) {
		if (mapa(fuente.getServer()) == null) {
			return 0;
		}
		politica.cronica(fuente);
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
