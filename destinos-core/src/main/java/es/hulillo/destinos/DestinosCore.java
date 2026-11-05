package es.hulillo.destinos;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
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
	/** Cada cuánto se guarda el terreno leído mientras se juega (5 minutos). */
	private static final int TICKS_GUARDA_TERRENO = 20 * 60 * 5;
	private static final ExecutorService HILO = Executors.newFixedThreadPool(HILOS, r -> {
		Thread t = new Thread(r, "Destinos Core - terreno");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private static volatile MapaTerreno mapa;
	/** Zonas que se mandan como mucho cada vez que el mapa político las pide (unos 8 KB cada una). */
	private static final int ZONAS_POR_PETICION = 96;
	private static final EnvioTerritorios ENVIO = new EnvioTerritorios();
	/** Mapa para el que se mandaron las zonas: si cambia (otra sal), se manda todo otra vez. */
	private static MapaTerreno mapaEnviado;
	private static PoliticaMundo politica;
	private static final ReparaCapitales REPARA = new ReparaCapitales();
	private static final Identifier MUNDO = Identifier.fromNamespaceAndPath("destinos", "mundo");

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ComandosDestinos.registra(dispatcher));
		ColocacionCapital.registra();
		EstructuraCapital.registra();
		CalleCapital.registra();
		MurallaCapital.registra();
		CastilloCapital.registra();
		AdornoCapital.registra();
		CasaNoble.registra();
		PlazaMercado.registra();
		PlazuelaCapital.registra();
		PuertaCapital.registra();
		PlazaMayor.registra();
		MuelleCapital.registra();
		FaroCapital.registra();
		HuertoCapital.registra();
		TablonBlock.registra();
		Monedas.registra();
		TornadosEstacion.registra();
		PayloadTypeRegistry.playS2C().register(PaqueteTerritorio.TIPO, PaqueteTerritorio.CODEC);
		PayloadTypeRegistry.playS2C().register(PaqueteVerMapa.TIPO, PaqueteVerMapa.CODEC);
		PayloadTypeRegistry.playS2C().register(PaquetePolitica.TIPO, PaquetePolitica.CODEC);
		PayloadTypeRegistry.playS2C().register(PaqueteTablon.TIPO, PaqueteTablon.CODEC);
		PayloadTypeRegistry.playS2C().register(PaqueteEncargos.TIPO, PaqueteEncargos.CODEC);
		PayloadTypeRegistry.playS2C().register(PaqueteMazmorra.TIPO, PaqueteMazmorra.CODEC);
		PayloadTypeRegistry.playS2C().register(PaqueteFichas.TIPO, PaqueteFichas.CODEC);
		PayloadTypeRegistry.playS2C().register(PaqueteArbol.TIPO, PaqueteArbol.CODEC);
		PayloadTypeRegistry.playC2S().register(PaqueteAccionEncargo.TIPO, PaqueteAccionEncargo.CODEC);
		PayloadTypeRegistry.playC2S().register(PaquetePedirMapa.TIPO, PaquetePedirMapa.CODEC);
		PayloadTypeRegistry.playC2S().register(PaquetePedirArbol.TIPO, PaquetePedirArbol.CODEC);
		// Fabric ya entrega los paquetes en el hilo del servidor
		ServerPlayNetworking.registerGlobalReceiver(PaqueteAccionEncargo.TIPO, (paquete, contexto) ->
				EncargosMundo.accion(contexto.player(), paquete));
		ServerPlayNetworking.registerGlobalReceiver(PaquetePedirMapa.TIPO, (paquete, contexto) ->
				pideMapa(contexto.server(), contexto.player(), paquete.fichas()));
		ServerPlayNetworking.registerGlobalReceiver(PaquetePedirArbol.TIPO, (paquete, contexto) -> {
			PaqueteArbol a = politica == null ? null : politica.arbol(paquete.kx(), paquete.kz(), paquete.persona());
			if (a != null && ServerPlayNetworking.canSend(contexto.player(), PaqueteArbol.TIPO)) {
				ServerPlayNetworking.send(contexto.player(), a);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				EncargosMundo.de(server).envia(server, handler.getPlayer()));
		ServerLivingEntityEvents.AFTER_DEATH.register((muerto, fuente) -> {
			if (muerto.level() instanceof ServerLevel sl) {
				EncargosMundo.de(sl.getServer()).alMorir(sl.getServer(), muerto, fuente);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ENVIO.olvida(handler.getPlayer().getUUID()));
		// la sal tiene que estar antes del primer chunk (los de alrededor del spawn se generan antes de que el
		// datapack cargue); si no, ahí no salen capitales
		ServerWorldEvents.LOAD.register(DestinosCore::salTemprana);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Vigia.arranca(server);
			MapaTerreno m = mapa;
			if (m != null) {
				guardaSal(server, m.sal());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Vigia.para();
			guardaTerreno(server);
			TiemposPiezas.escribe();
			if (politica != null) {
				politica.guarda(server);
			}
			EncargosMundo.de(server).guarda(server);
			MazmorrasMundo.de(server).guarda(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			mapa = null;
			politica = null;
			EstructuraCapital.olvida();
			REPARA.olvida();
			CartelesCapital.olvida();
			TablonesPueblos.olvida();
			EncargosMundo.olvida();
			MazmorrasMundo.olvida();
			FaunaDucados.olvida();
			mapaEnviado = null;
			ENVIO.olvidaTodo();
		});
		ServerChunkEvents.CHUNK_LOAD.register(CartelesCapital::alCargar);
		ServerChunkEvents.CHUNK_LOAD.register(TablonesPueblos::alCargar);
		ServerChunkEvents.CHUNK_LOAD.register(MazmorrasMundo::alCargar);
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
		MapaTerreno m = mapa;
		if (m != null && server.getTickCount() % TICKS_GUARDA_TERRENO == 0) {
			// cada pocos minutos, fuera del hilo del servidor: si el juego se cierra de golpe no se pierde lo explorado
			java.nio.file.Path fichero = ficheroTerreno(server);
			HILO.execute(() -> {
				try {
					m.guardaSiCambia(fichero);
				} catch (java.io.IOException | RuntimeException e) {
					LOG.warn("Destinos Core: no se pudo guardar el terreno leído", e);
				}
			});
		}
		if (m != null) {
			REPARA.tick(server, m);
		}
		CartelesCapital.tick(server);
		TablonesPueblos.tick(server);
		EncargosMundo.de(server).tick(server);
		MazmorrasMundo.de(server).tick(server);
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
		Supplier<PaquetePolitica> deAhora = () -> estado[0] != null ? estado[0] : (estado[0] = pol.estadoMapas());
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.level().dimension() == Level.OVERWORLD) {
				m.precarga(p.getBlockX(), p.getBlockZ());
				m.precargaLejos(p.getBlockX(), p.getBlockZ());
				ENVIO.envia(m, pol, deAhora, p);
			}
		}
		// lo explorado en otras partidas vuelve al mapa sin tener que pasar otra vez por allí
		m.rehaceGuardadas();
	}

	/**
	 * Alguien ha abierto el mapa político: le van todas las zonas calculadas que no tenga (por tandas, el cliente
	 * vuelve a pedir mientras siga abierto) y, si las pide, las fichas de reinos y ducados.
	 */
	private static void pideMapa(MinecraftServer server, ServerPlayer p, boolean fichas) {
		MapaTerreno m = mapa(server);
		PoliticaMundo pol = politica;
		if (m == null || pol == null) {
			return;
		}
		if (m != mapaEnviado) {
			ENVIO.olvidaTodo();
			mapaEnviado = m;
		}
		ENVIO.enviaTodas(m, pol, p, ZONAS_POR_PETICION);
		if (fichas && ServerPlayNetworking.canSend(p, PaqueteFichas.TIPO)) {
			ServerPlayNetworking.send(p, pol.fichas(server, p, EncargosMundo.de(server)));
		}
	}

	/** El mapa del mundo que está abierto, o null (para la generación del mundo, que no sabe del servidor). */
	static MapaTerreno mapaActual() {
		return mapa;
	}

	/** El mapa del mundo actual. Se rehace si cambia la sal (otro mundo). Null si el datapack aún no ha cargado. */
	static MapaTerreno mapa(MinecraftServer server) {
		Integer sal = Marcador.lee(server, "#sal", Marcador.CONST);
		if (sal == null) {
			return null;
		}
		MapaTerreno m = mapa;
		if (m == null || m.sal() != sal) {
			m = new MapaTerreno(new MuestreadorMundo(server.overworld()), sal, HILO);
			cargaTerreno(server, m);
			mapa = m;
		}
		if (politica == null || politica.sal() != sal) {
			politica = PoliticaMundo.de(server, sal);
		}
		return m;
	}

	/** La política del mundo abierto. Solo después de que mapa(server) haya devuelto un mapa. */
	static PoliticaMundo politica() {
		return politica;
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
		MapaTerreno m = new MapaTerreno(new MuestreadorMundo(level), sal, HILO);
		cargaTerreno(server, m);
		mapa = m;
	}

	/** Dónde se guarda el terreno ya leído del generador, dentro de la carpeta del mundo. */
	private static java.nio.file.Path ficheroTerreno(MinecraftServer server) {
		return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve("destinos_terreno.dat");
	}

	/** Lee el terreno guardado: así el mapa de lo ya explorado sale al momento al volver a entrar. */
	private static void cargaTerreno(MinecraftServer server, MapaTerreno m) {
		try {
			long t = System.nanoTime();
			int n = m.cargaTerreno(ficheroTerreno(server));
			if (n > 0) {
				LOG.info("Destinos Core: terreno guardado cargado ({} regiones, {} ms)", n, (System.nanoTime() - t) / 1_000_000);
			}
		} catch (java.io.IOException | RuntimeException e) {
			LOG.warn("Destinos Core: no se pudo leer el terreno guardado; se leerá otra vez del generador", e);
		}
	}

	private static void guardaTerreno(MinecraftServer server) {
		MapaTerreno m = mapa;
		if (m == null) {
			return;
		}
		try {
			m.guardaTerreno(ficheroTerreno(server));
		} catch (java.io.IOException | RuntimeException e) {
			LOG.warn("Destinos Core: no se pudo guardar el terreno leído", e);
		}
	}

	private static int salDeSemilla(long semilla) {
		long h = (semilla ^ 0x5DEECE66DL) * Geo.MEZCLA;
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
}
