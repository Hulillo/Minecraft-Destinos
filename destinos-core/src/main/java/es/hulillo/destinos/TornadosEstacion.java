package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.ServerLevelData;

/**
 * Tornados con sentido, si está Weather and Tornadoes Remastered (W&T). W&T trae su propio tiempo: nunca deja que
 * la lluvia vanilla empiece (solo la apaga) y cada día decide él si llueve o hay tormenta, así que las estaciones de
 * Serene Seasons dejan de mandar. Aquí:
 * <ul>
 * <li>Su lluvia diaria y sus cúmulos se quitan (mixins de es.hulillo.destinos.tornados.mixin).</li>
 * <li>La lluvia y las tormentas vanilla vuelven a empezar cuando les toca: justo antes de que W&T avance el
 * tiempo, si al temporizador le queda un tick, se enciende y se le da la duración de vanilla. Así Serene Seasons, que
 * ajusta esos temporizadores, vuelve a decidir cuándo llueve.</li>
 * <li>Los tornados salen solo en tormentas, con el jugador a cielo abierto en llanuras, sabanas o praderas y según
 * la estación: primavera alta, verano media, otoño baja e invierno casi nunca. Se invocan con el comando de W&T.</li>
 * </ul>
 * Las trombas marinas de W&T (lluvia con viento sobre el agua) se quedan: ya salen donde tienen sentido.
 */
final class TornadosEstacion {
	private static final int CADA = 200;
	/** Tras un tornado, medio día sin otro. */
	private static final int ESPERA = 12000;
	/** Duraciones de vanilla (las mismas que usa W&T al contar). */
	private static final int LLUVIA_MIN = 12000;
	private static final int LLUVIA_MAX = 24000;
	private static final int TORMENTA_MIN = 3600;
	private static final int TORMENTA_MAX = 15600;
	/** Biomas abiertos de mods de terreno que no llevan las etiquetas de llanura o sabana. */
	private static final Pattern ABIERTO = Pattern.compile("plains|savanna|meadow|prairie|grassland|steppe|shrubland|pampa|field");
	private static final Pattern FRIO = Pattern.compile("snow|frozen|ice|tundra");

	private static long proximo;

	private TornadosEstacion() {
	}

	static void registra() {
		if (!FabricLoader.getInstance().isModLoaded("weather_classic")) {
			return;
		}
		// START_WORLD_TICK va antes de advanceWeatherCycle, que es donde W&T cuenta los temporizadores
		ServerTickEvents.START_WORLD_TICK.register(TornadosEstacion::lluviaVanilla);
		ServerTickEvents.END_WORLD_TICK.register(TornadosEstacion::tornados);
		DestinosCore.LOG.info("Destinos Core: W&T presente; la lluvia sigue las estaciones y los tornados, el terreno");
	}

	private static void lluviaVanilla(ServerLevel mundo) {
		if (mundo.dimension() != Level.OVERWORLD) {
			return;
		}
		ServerLevelData datos = mundo.getServer().getWorldData().overworldData();
		if (datos.getClearWeatherTime() > 0) {
			// /weather clear: W&T ya lo cuenta igual que vanilla
			return;
		}
		RandomSource r = mundo.getRandom();
		if (!datos.isThundering() && datos.getThunderTime() == 1) {
			datos.setThundering(true);
			datos.setThunderTime(TORMENTA_MIN + r.nextInt(TORMENTA_MAX - TORMENTA_MIN + 1));
		}
		if (!datos.isRaining() && datos.getRainTime() == 1) {
			datos.setRaining(true);
			datos.setRainTime(LLUVIA_MIN + r.nextInt(LLUVIA_MAX - LLUVIA_MIN + 1));
		}
	}

	private static void tornados(ServerLevel mundo) {
		if (mundo.dimension() != Level.OVERWORLD || mundo.getGameTime() % CADA != 0) {
			return;
		}
		ServerLevelData datos = mundo.getServer().getWorldData().overworldData();
		long ahora = mundo.getGameTime();
		if (proximo - ahora > ESPERA) {
			// otro mundo con menos tiempo jugado: la espera guardada no vale
			proximo = 0;
		}
		if (!datos.isThundering() || ahora < proximo) {
			return;
		}
		String estacion = Estaciones.estacion(mundo);
		float prob = probabilidad(estacion);
		RandomSource r = mundo.getRandom();
		List<ServerPlayer> jugadores = new ArrayList<>(mundo.players());
		Collections.shuffle(jugadores);
		for (ServerPlayer p : jugadores) {
			if (p.isSpectator() || r.nextFloat() >= prob) {
				continue;
			}
			// bajo tierra no se ve el tornado
			if (p.getBlockY() < mundo.getSeaLevel() - 8 || !mundo.canSeeSky(p.blockPosition().above())) {
				continue;
			}
			int distancia = 6 + r.nextInt(5);
			if (!abierto(mundo, p, distancia)) {
				continue;
			}
			int tipo = tipo(estacion, r);
			CommandSourceStack fuente = mundo.getServer().createCommandSourceStack()
					.withLevel(mundo)
					.withPosition(p.position())
					.withSuppressedOutput();
			// W&T lo pone a esa distancia (en chunks) del jugador más cercano a la fuente, que es p
			mundo.getServer().getCommands().performPrefixedCommand(fuente, "weather_mod spawn tornado " + tipo + " " + distancia);
			proximo = ahora + ESPERA;
			DestinosCore.LOG.info("Destinos Core: tornado (tipo {} de W&T) a {} chunks de {} en {}", tipo, distancia,
					p.getScoreboardName(), estacion == null ? "un mundo sin estaciones" : estacion);
			return;
		}
	}

	/** Probabilidad en cada mirada (cada 10 s) de tormenta: una tormenta dura de 3 a 13 minutos. */
	private static float probabilidad(String estacion) {
		if (estacion == null) {
			return 0.008f;
		}
		return switch (estacion) {
			case "SPRING" -> 0.015f;
			case "SUMMER" -> 0.008f;
			case "AUTUMN" -> 0.003f;
			default -> 0.0003f;
		};
	}

	/**
	 * El tipo para /weather_mod spawn tornado: 2 es el más flojo y 5 el más fuerte (0 es una tromba, 1 la de soga y
	 * 6 el personalizado). En primavera salen los fuertes; en invierno, si sale alguno, es flojo.
	 */
	private static int tipo(String estacion, RandomSource r) {
		int[] pesos = switch (estacion == null ? "SUMMER" : estacion) {
			case "SPRING" -> new int[] {40, 30, 20, 10};
			case "SUMMER" -> new int[] {55, 30, 12, 3};
			case "AUTUMN" -> new int[] {70, 25, 5, 0};
			default -> new int[] {100, 0, 0, 0};
		};
		int tirada = r.nextInt(100);
		for (int i = 0; i < pesos.length; i++) {
			tirada -= pesos[i];
			if (tirada < 0) {
				return 2 + i;
			}
		}
		return 2;
	}

	/**
	 * El jugador en terreno abierto y, a la distancia donde saldrá el tornado, sobre todo terreno abierto. Solo se
	 * miran chunks cargados (W&T tampoco pone tornados en los demás), para no generar nada.
	 */
	private static boolean abierto(ServerLevel mundo, ServerPlayer p, int distancia) {
		if (!abierto(mundo.getBiome(p.blockPosition()))) {
			return false;
		}
		int radio = distancia * 16;
		int miradas = 0;
		int abiertas = 0;
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			BlockPos pos = BlockPos.containing(p.getX() + Math.cos(a) * radio, p.getY(), p.getZ() + Math.sin(a) * radio);
			if (!mundo.hasChunkAt(pos)) {
				continue;
			}
			miradas++;
			if (abierto(mundo.getBiome(pos))) {
				abiertas++;
			}
		}
		return miradas > 0 && abiertas * 2 >= miradas;
	}

	private static boolean abierto(Holder<Biome> bioma) {
		String nombre = bioma.getRegisteredName();
		if (bioma.is(ConventionalBiomeTags.IS_SNOWY) || FRIO.matcher(nombre).find()) {
			return false;
		}
		return bioma.is(ConventionalBiomeTags.IS_PLAINS) || bioma.is(BiomeTags.IS_SAVANNA) || ABIERTO.matcher(nombre).find();
	}
}
