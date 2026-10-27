package es.hulillo.destinos;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
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
	private static final Identifier MUNDO = Identifier.fromNamespaceAndPath("destinos", "mundo");

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("destinoscore")
						.then(Commands.literal("territorio").executes(ctx -> territorio(ctx.getSource())))
						.then(Commands.literal("estado").executes(ctx -> estado(ctx.getSource())))
						.then(Commands.literal("capital").executes(ctx -> capital(ctx.getSource())))
						.then(Commands.literal("politica").executes(ctx -> politica(ctx.getSource()))
								.then(Commands.literal("reinos").executes(ctx -> ranking(ctx.getSource())))
								.then(Commands.literal("ritmo")
										.then(Commands.argument("dias", IntegerArgumentType.integer(1, 30))
												.executes(ctx -> ritmo(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "dias"))))))
						.then(Commands.literal("turno").executes(ctx -> turno(ctx.getSource(), 1))
								.then(Commands.argument("n", IntegerArgumentType.integer(1, 500))
										.executes(ctx -> turno(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))))
						.then(Commands.literal("cronica").executes(ctx -> cronica(ctx.getSource())))));
		ColocacionCapital.registra();
		EstructuraCapital.registra();
		CalleCapital.registra();
		MurallaCapital.registra();
		PayloadTypeRegistry.playS2C().register(PaqueteTerritorio.TIPO, PaqueteTerritorio.CODEC);
		PayloadTypeRegistry.playS2C().register(PaquetePolitica.TIPO, PaquetePolitica.CODEC);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ENVIO.olvida(handler.getPlayer().getUUID()));
		// la sal tiene que estar antes del primer chunk (los de alrededor del spawn se generan antes de que el
		// datapack cargue); si no, ahí no salen capitales
		ServerWorldEvents.LOAD.register(DestinosCore::salTemprana);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			MapaTerreno m = mapa;
			if (m != null) {
				guardaSal(server, m.sal());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (politica != null) {
				politica.guarda(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			mapa = null;
			politica = null;
			EstructuraCapital.olvida();
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
		// el estado político solo se monta si algún jugador no lo tiene aún
		PaquetePolitica[] estado = new PaquetePolitica[1];
		java.util.function.Supplier<PaquetePolitica> deAhora = () -> estado[0] != null ? estado[0] : (estado[0] = pol.estadoMapas());
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.level().dimension() == Level.OVERWORLD) {
				m.precarga(p.getBlockX(), p.getBlockZ());
				m.precargaLejos(p.getBlockX(), p.getBlockZ());
				ENVIO.envia(m, pol, deAhora, p);
			}
		}
	}

	/** El mapa del mundo que está abierto, o null (para la generación del mundo, que no sabe del servidor). */
	static MapaTerreno mapaActual() {
		return mapa;
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
		if (politica == null || politica.sal() != sal) {
			politica = PoliticaMundo.de(server, sal);
		}
		return m;
	}

	/**
	 * Al abrir el overworld, antes de generar nada: la sal del mundo (la guardada o, en un mundo nuevo, una sacada
	 * de la semilla, que se deja en el storage para que el datapack la adopte en vez de tirar otra).
	 */
	private static void salTemprana(MinecraftServer server, ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		Integer guardada = null;
		try {
			CompoundTag t = server.getCommandStorage().get(MUNDO);
			if (t.contains("sal")) {
				guardada = t.getIntOr("sal", 0);
			}
		} catch (RuntimeException e) {
			// el storage aún no está (mundo nuevo)
		}
		int sal = guardada != null ? guardada : salDeSemilla(level.getSeed());
		// en un mundo nuevo el storage aún no existe aquí: la sal se guarda en SERVER_STARTED, antes del primer tick
		mapa = new MapaTerreno(new MuestreadorMundo(level), sal, HILO);
	}

	private static int salDeSemilla(long semilla) {
		long h = (semilla ^ 0x5DEECE66DL) * 0x9E3779B97F4A7C15L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return 1 + (int) Math.floorMod(h, 2_000_000_000L);
	}

	/** Deja la sal en destinos:mundo si aún no hay (el datapack solo tira una si falta). */
	private static void guardaSal(MinecraftServer server, int sal) {
		try {
			CompoundTag t = server.getCommandStorage().get(MUNDO);
			if (!t.contains("sal")) {
				t.putInt("sal", sal);
				server.getCommandStorage().set(MUNDO, t);
			}
		} catch (RuntimeException e) {
			LOG.warn("Destinos Core: no se ha podido guardar la sal temprana", e);
		}
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

	/**
	 * /destinoscore capital: la capital del reino donde estás, si su chunk tiene la estructura (y cuántas piezas)
	 * y si el chunk donde estás la conoce. Para ver por qué una corona sale sin pueblo.
	 */
	private static int capital(CommandSourceStack fuente) {
		MinecraftServer server = fuente.getServer();
		MapaTerreno m = mapa(server);
		if (m == null) {
			fuente.sendFailure(Component.literal("Destinos Core: esperando al datapack (no hay sal todavía)"));
			return 0;
		}
		ServerLevel level = server.overworld();
		BlockPos aqui = BlockPos.containing(fuente.getPosition());
		// la capital más cercana de los reinos de los ducados de alrededor, como las busca la colocación de
		// capitales (sin esperar a que se calcule la zona)
		int dcx = Math.floorDiv(aqui.getX(), Geo.S_DUCADO);
		int dcz = Math.floorDiv(aqui.getZ(), Geo.S_DUCADO);
		MapaTerreno.Capital cap = null;
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				int[] centro = Geo.centro(dcx + ox, dcz + oz, Geo.S_DUCADO, m.sal());
				Geo.Reino reino = Geo.reinoDeDucado(dcx + ox, dcz + oz, centro[0], centro[1], m.sal());
				MapaTerreno.Capital c = reino.salvaje() ? null : m.capital(reino.kx(), reino.kz());
				if (c != null && (cap == null || aqui.distToCenterSqr(c.x(), aqui.getY(), c.z())
						< aqui.distToCenterSqr(cap.x(), aqui.getY(), cap.z()))) {
					cap = c;
				}
			}
		}
		if (cap == null) {
			fuente.sendFailure(Component.literal("Destinos Core: por aquí no hay ningún reino con capital"));
			return 0;
		}
		Structure estructura = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.getValue(Identifier.fromNamespaceAndPath(MOD_ID, "capital"));
		if (estructura == null) {
			fuente.sendFailure(Component.literal("Destinos Core: no está registrada la estructura destinos_core:capital"));
			return 0;
		}
		ChunkAccess inicio = level.getChunk(cap.x() >> 4, cap.z() >> 4);
		StructureStart start = inicio.getStartForStructure(estructura);
		String delInicio = start == null || !start.isValid()
				? "su chunk NO tiene la capital"
				: "su chunk tiene la capital: " + start.getPieces().size() + " piezas, de " + start.getBoundingBox().minX() + ", "
						+ start.getBoundingBox().minZ() + " a " + start.getBoundingBox().maxX() + ", " + start.getBoundingBox().maxZ();
		boolean conoce = !level.getChunk(aqui).getReferencesForStructure(estructura).isEmpty();
		boolean dentro = level.structureManager().getStructureWithPieceAt(aqui, estructura).isValid();
		String texto = "Destinos Core: capital del reino en " + cap.x() + ", " + cap.z() + " (chunk " + (cap.x() >> 4) + ", "
				+ (cap.z() >> 4) + ", nivel " + cap.nivel() + ") · " + delInicio + " · este chunk "
				+ (conoce ? "la conoce" : "NO la conoce") + (dentro ? " · estás dentro de una pieza" : "");
		LOG.info(texto);
		fuente.sendSuccess(() -> Component.literal(texto), false);
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
