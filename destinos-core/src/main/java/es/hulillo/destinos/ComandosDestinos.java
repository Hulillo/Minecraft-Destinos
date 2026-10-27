package es.hulillo.destinos;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** /destinoscore: lo que usa el datapack (territorio), diagnósticos y la política viva. */
final class ComandosDestinos {
	private ComandosDestinos() {
	}

	static void registra(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("destinoscore")
				.then(Commands.literal("territorio").executes(ctx -> territorio(ctx.getSource())))
				.then(Commands.literal("estado").executes(ctx -> estado(ctx.getSource())))
				.then(Commands.literal("capital").executes(ctx -> capital(ctx.getSource())))
				.then(Commands.literal("revisa").executes(ctx -> revisa(ctx.getSource(), 2))
						.then(Commands.argument("reinos", IntegerArgumentType.integer(0, 6))
								.executes(ctx -> revisa(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "reinos")))))
				.then(Commands.literal("politica").executes(ctx -> conPolitica(ctx.getSource(), false, (pol, m) -> pol.estado(ctx.getSource(), m)))
						.then(Commands.literal("reinos").executes(ctx -> conPolitica(ctx.getSource(), false, (pol, m) -> pol.ranking(ctx.getSource()))))
						.then(Commands.literal("ritmo")
								.then(Commands.argument("dias", IntegerArgumentType.integer(1, 30))
										.executes(ctx -> ritmo(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "dias"))))))
				.then(Commands.literal("turno").executes(ctx -> turno(ctx.getSource(), 1))
						.then(Commands.argument("n", IntegerArgumentType.integer(1, 500))
								.executes(ctx -> turno(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("cronica").executes(ctx -> conPolitica(ctx.getSource(), false, (pol, m) -> pol.cronica(ctx.getSource())))));
	}

	/**
	 * Hace algo con la política y el mapa del mundo, si el datapack ya ha puesto la sal. tocar: cambia el mundo, así
	 * que solo desde la consola, funciones o un jugador en creativo.
	 */
	private static int conPolitica(CommandSourceStack fuente, boolean tocar, BiConsumer<PoliticaMundo, MapaTerreno> accion) {
		if (tocar && !puedeTocar(fuente)) {
			return 0;
		}
		MapaTerreno m = DestinosCore.mapa(fuente.getServer());
		if (m == null) {
			fuente.sendFailure(Component.literal("Destinos Core: esperando al datapack (no hay sal todavía)"));
			return 0;
		}
		accion.accept(DestinosCore.politica(), m);
		return 1;
	}

	private static boolean puedeTocar(CommandSourceStack fuente) {
		ServerPlayer p = fuente.getPlayer();
		if (p == null || p.isCreative()) {
			return true;
		}
		fuente.sendFailure(Component.translatableWithFallback("destinos.politica.permiso",
				"Solo en modo creativo o desde la consola."));
		return false;
	}

	/**
	 * Entrada: #vx #vz (dr.calc) · Salida: #tdx #tdz #tkx #tkz #tw #tmar (dr.calc), como geo/territorio.
	 * Si la zona aún se está calculando deja #tlisto a 0 y no toca nada más: el datapack lo vuelve a intentar.
	 */
	private static int territorio(CommandSourceStack fuente) {
		MinecraftServer server = fuente.getServer();
		MapaTerreno m = DestinosCore.mapa(server);
		Integer vx = Marcador.lee(server, "#vx", Marcador.CALC);
		Integer vz = Marcador.lee(server, "#vz", Marcador.CALC);
		if (m == null || vx == null || vz == null) {
			fuente.sendFailure(Component.literal("Destinos Core: falta la sal o #vx/#vz; ¿está cargado el datapack?"));
			return 0;
		}
		MapaTerreno.Resultado r = m.consultaSiLista(vx, vz);
		if (r == null) {
			Marcador.escribe(server, "#tlisto", 0);
			return 1;
		}
		r = DestinosCore.politica().aplica(r);
		Marcador.escribe(server, "#tlisto", 1);
		Marcador.escribe(server, "#tdx", r.dcx());
		Marcador.escribe(server, "#tdz", r.dcz());
		Marcador.escribe(server, "#tkx", r.kx());
		Marcador.escribe(server, "#tkz", r.kz());
		Marcador.escribe(server, "#tw", r.salvaje() ? 1 : 0);
		Marcador.escribe(server, "#tmar", r.oceano() ? 1 : 0);
		return 1;
	}

	private static int estado(CommandSourceStack fuente) {
		MapaTerreno m = DestinosCore.mapa(fuente.getServer());
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
		MapaTerreno m = DestinosCore.mapa(server);
		if (m == null) {
			fuente.sendFailure(Component.literal("Destinos Core: esperando al datapack (no hay sal todavía)"));
			return 0;
		}
		ServerLevel level = server.overworld();
		BlockPos aqui = BlockPos.containing(fuente.getPosition());
		// la capital más cercana de los reinos de los ducados de alrededor, como las busca la colocación de
		// capitales (sin esperar a que se calcule la zona)
		MapaTerreno.Capital cap = null;
		for (Geo.Reino reino : Geo.reinosCerca(Geo.celdaDucado(aqui.getX()), Geo.celdaDucado(aqui.getZ()), m.sal())) {
			MapaTerreno.Capital c = m.capital(reino.kx(), reino.kz());
			if (c != null && (cap == null || aqui.distToCenterSqr(c.x(), aqui.getY(), c.z())
					< aqui.distToCenterSqr(cap.x(), aqui.getY(), cap.z()))) {
				cap = c;
			}
		}
		if (cap == null) {
			fuente.sendFailure(Component.literal("Destinos Core: por aquí no hay ningún reino con capital"));
			return 0;
		}
		Structure estructura = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.getValue(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "capital"));
		if (estructura == null) {
			fuente.sendFailure(Component.literal("Destinos Core: no está registrada la estructura destinos_core:capital"));
			return 0;
		}
		StructureStart start = level.getChunk(cap.x() >> 4, cap.z() >> 4).getStartForStructure(estructura);
		String delInicio = start == null || !start.isValid()
				? "su chunk NO tiene la capital"
				: "su chunk tiene la capital: " + start.getPieces().size() + " piezas, de " + start.getBoundingBox().minX() + ", "
						+ start.getBoundingBox().minZ() + " a " + start.getBoundingBox().maxX() + ", " + start.getBoundingBox().maxZ();
		boolean conoce = !level.getChunk(aqui).getReferencesForStructure(estructura).isEmpty();
		boolean dentro = level.structureManager().getStructureWithPieceAt(aqui, estructura).isValid();
		String texto = "Destinos Core: capital del reino en " + cap.x() + ", " + cap.z() + " (chunk " + (cap.x() >> 4) + ", "
				+ (cap.z() >> 4) + ", nivel " + cap.nivel() + ") · " + delInicio + " · este chunk "
				+ (conoce ? "la conoce" : "NO la conoce") + (dentro ? " · estás dentro de una pieza" : "");
		DestinosCore.LOG.info(texto);
		fuente.sendSuccess(() -> Component.literal(texto), false);
		return 1;
	}

	/**
	 * /destinoscore revisa [n]: elige la capital de los reinos a n celdas de reino de donde estás (sin generar
	 * nada) y comprueba con la altura de verdad que todas están en tierra. Dice cuántas hay de cada nivel, para
	 * calibrar la riqueza. Para la CI y para buscar capitales mal puestas.
	 */
	private static int revisa(CommandSourceStack fuente, int n) {
		MinecraftServer server = fuente.getServer();
		MapaTerreno m = DestinosCore.mapa(server);
		if (m == null) {
			fuente.sendFailure(Component.literal("Destinos Core: esperando al datapack (no hay sal todavía)"));
			return 0;
		}
		ServerLevel level = server.overworld();
		BlockPos aqui = BlockPos.containing(fuente.getPosition());
		int kx0 = Math.floorDiv(aqui.getX(), Geo.S_REINO);
		int kz0 = Math.floorDiv(aqui.getZ(), Geo.S_REINO);
		long inicio = System.currentTimeMillis();
		int reinos = 0;
		int[] niveles = new int[3];
		List<String> mojadas = new ArrayList<>();
		for (int kx = kx0 - n; kx <= kx0 + n; kx++) {
			for (int kz = kz0 - n; kz <= kz0 + n; kz++) {
				reinos++;
				MapaTerreno.Capital c = m.capital(kx, kz);
				if (c == null) {
					continue;
				}
				niveles[c.nivel()]++;
				if (!EstructuraCapital.enTierra(level, c.x(), c.z())) {
					mojadas.add(c.x() + ", " + c.z());
				}
			}
		}
		String texto = "Destinos Core: " + reinos + " reinos revisados en " + (System.currentTimeMillis() - inicio)
				+ " ms · capitales pobres " + niveles[0] + ", normales " + niveles[1] + ", ricas " + niveles[2]
				+ " · capitales en el agua: " + mojadas.size() + (mojadas.isEmpty() ? "" : " (" + String.join("; ", mojadas) + ")");
		DestinosCore.LOG.info(texto);
		fuente.sendSuccess(() -> Component.literal(texto), false);
		return mojadas.isEmpty() ? 1 : 0;
	}

	private static int turno(CommandSourceStack fuente, int n) {
		return conPolitica(fuente, true, (pol, m) -> {
			pol.fuerza(n, fuente);
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.forzado",
					"Pasan %s turnos de política…", n), false);
		});
	}

	private static int ritmo(CommandSourceStack fuente, int dias) {
		return conPolitica(fuente, true, (pol, m) -> {
			pol.ritmo(fuente.getServer(), dias);
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.ritmo",
					"Ahora pasa un turno de política cada %s días de juego.", dias), false);
		});
	}
}
