package es.hulillo.destinos;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * La capital de un reino: el pueblo que tocaría en ese bioma (de vanilla o de cualquier mod, como Towns and
 * Towers), pero mucho más grande. No tiene piezas propias: copia la estructura de pueblo con más profundidad y
 * la genera en el centro y en cuatro barrios alrededor, sin que las piezas de un barrio pisen las de otro.
 * Sale donde dice ColocacionCapital; qué pueblo se copia y cómo lo decide PueblosCapital.
 */
public final class EstructuraCapital extends Structure {
	public static final MapCodec<EstructuraCapital> CODEC = simpleCodec(EstructuraCapital::new);
	public static final StructureType<EstructuraCapital> TIPO = () -> CODEC;
	/** Profundidad extra del centro y de los barrios sobre la del pueblo (el máximo del juego es 20). */
	private static final int MAS_PROFUNDIDAD = 4;
	private static final int MAS_PROFUNDIDAD_BARRIO = 2;
	/**
	 * Hasta dónde llegan las calles del centro y de cada barrio, y a qué distancia del centro (en cada eje) nace
	 * cada barrio. Todo tiene que quedar a menos de 128 bloques del chunk de la capital: más lejos el juego ya no
	 * pone las piezas.
	 */
	private static final int DISTANCIA = 80;
	private static final int DISTANCIA_BARRIO = 48;
	private static final int SEPARACION_BARRIO = 64;
	/** Largo de cada tramo de calle entre el centro y un barrio. */
	private static final int TRAMO = 16;
	/** Agua seguida que puede cruzar una calle (con tablones); más, y esa calle no se hace. */
	private static final int MAX_AGUA_CALLE = 8;

	/** Capitales ya preparadas, por chunk (las últimas; hay pocas y se piden varias veces seguidas). */
	private static final Map<Long, Optional<Structure.GenerationStub>> PREPARADAS = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Optional<Structure.GenerationStub>> mas) {
			return size() > 16;
		}
	};

	public EstructuraCapital(Structure.StructureSettings ajustes) {
		super(ajustes);
	}

	/** Al cerrar el mundo: fuera las capitales preparadas, que guardan cosas de ese mundo. */
	static void olvida() {
		synchronized (PREPARADAS) {
			PREPARADAS.clear();
		}
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "capital"), TIPO);
	}

	/**
	 * Sin mirar el bioma: dónde va la capital lo decide ColocacionCapital, y el bioma justo en el centro puede ser
	 * uno que no está en ningún tag (ríos y biomas de mods). Con la comprobación de vanilla, si la generación del
	 * chunk veía un bioma así, la capital se quedaba sin generar aunque la corona sí saliera en el mapa.
	 */
	@Override
	public Optional<Structure.GenerationStub> findValidGenerationPoint(Structure.GenerationContext ctx) {
		return findGenerationPoint(ctx);
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext ctx) {
		// la generación y las búsquedas de estructuras (/locate, RoadWeaver…) preguntan por el mismo chunk: la
		// capital se prepara una vez (son cientos de piezas) y las demás veces se reutiliza
		long clave = ctx.chunkPos().toLong() * 31 + ctx.seed();
		synchronized (PREPARADAS) {
			Optional<Structure.GenerationStub> ya = PREPARADAS.get(clave);
			if (ya != null) {
				return ya;
			}
		}
		Optional<Structure.GenerationStub> stub = prepara(ctx);
		synchronized (PREPARADAS) {
			PREPARADAS.put(clave, stub);
		}
		return stub;
	}

	private Optional<Structure.GenerationStub> prepara(Structure.GenerationContext ctx) {
		Holder<Biome> bioma = biomaCentro(ctx);
		// en el sitio justo; si ahí no cabe ningún pueblo, la plaza un poco más allá (la capital sigue siendo de
		// este chunk). Nunca uno abandonado.
		Optional<Plaza<Montado>> encontrada = primeraQueEmpieza(ctx, bioma, true, EstructuraCapital::monta);
		if (encontrada.isEmpty()) {
			DestinosCore.LOG.warn("Destinos Core: la capital del chunk {} no ha podido empezar con ningún pueblo", ctx.chunkPos());
			return Optional.empty();
		}
		Holder.Reference<Structure> pueblo = encontrada.get().pueblo();
		Montado principal = encontrada.get().resultado();
		// los barrios: el mismo pueblo, en las cuatro diagonales (menos los que caen en el agua)
		Agua agua = new Agua(ctx);
		List<Montado> barrios = new ArrayList<>();
		Optional<Structure> barrio = PueblosCapital.agrandado(ctx.registryAccess(), pueblo, MAS_PROFUNDIDAD_BARRIO, DISTANCIA_BARRIO);
		if (barrio.isPresent()) {
			for (int[] d : DIAGONALES) {
				ChunkPos sitio = new ChunkPos(ctx.chunkPos().x + d[0] * SEPARACION_BARRIO / 16,
						ctx.chunkPos().z + d[1] * SEPARACION_BARRIO / 16);
				if (!agua.en(sitio.getMiddleBlockX(), sitio.getMiddleBlockZ())) {
					monta(barrio.get(), contexto(ctx, sitio, ctx.seed())).ifPresent(barrios::add);
				}
			}
		}
		// calles del centro a cada barrio, para que no queden como pueblos sueltos
		// (solo por tierra: una calle que tendría que cruzar un lago o un brazo de mar no se hace)
		List<List<CalleCapital>> calles = new ArrayList<>();
		for (Montado b : barrios) {
			calles.add(calles(ctx, agua, principal.position().getX(), principal.position().getZ(), b.position().getX(),
					b.position().getZ()));
		}
		// la muralla, según lo rico que es el reino (con /place, sin capital en el mapa: normal)
		MapaTerreno.Capital esta = ColocacionCapital.capitalEn(ctx.chunkPos().x, ctx.chunkPos().z);
		int nivel = esta != null ? esta.nivel() : 1;
		List<StructurePiece> muralla = new ArrayList<>();
		MurallaCapital.piezas(principal.position().getX(), principal.position().getZ(), nivel, (mx, mz) -> suelo(ctx, mx, mz),
				muralla);
		return Optional.of(new Structure.GenerationStub(principal.position(), piezas -> {
			List<StructurePiece> puestas = new ArrayList<>();
			List<StructurePiece> delCentro = principal.piezas();
			for (int i = 0; i < delCentro.size(); i++) {
				// la plaza siempre; el resto, si no está metido en el mar (calles y casas flotando)
				if (i == 0 || !agua.tapa(delCentro.get(i))) {
					puestas.add(delCentro.get(i));
					piezas.addPiece(delCentro.get(i));
				}
			}
			int enCalles = 0;
			for (int i = 0; i < barrios.size(); i++) {
				int antes = puestas.size();
				for (StructurePiece pieza : barrios.get(i).piezas()) {
					// una pieza de barrio que choca con algo ya puesto o cae en el agua se queda fuera
					if (!agua.tapa(pieza) && puestas.stream().noneMatch(otra -> otra.getBoundingBox().intersects(pieza.getBoundingBox()))) {
						puestas.add(pieza);
						piezas.addPiece(pieza);
					}
				}
				// la calle, solo hasta barrios que de verdad tienen casas
				if (puestas.size() - antes >= 3) {
					calles.get(i).forEach(piezas::addPiece);
					enCalles += calles.get(i).size();
				}
			}
			// la muralla al final, para que vea las calles y casas ya puestas y les deje hueco
			muralla.forEach(piezas::addPiece);
			DestinosCore.LOG.info("Destinos Core: capital en {}, {}, {} (muralla de nivel {}): {} de {} piezas del centro, {} barrios, {} piezas en total",
					principal.position().getX(), principal.position().getY(), principal.position().getZ(), nivel, delCentro.stream()
							.filter(puestas::contains).count(), delCentro.size(), barrios.size(), puestas.size() + enCalles);
		}));
	}

	/** Un pueblo ya montado: dónde empieza y sus piezas (sin las casas abandonadas sueltas). */
	private record Montado(BlockPos position, List<StructurePiece> piezas) {
	}

	/** La plaza de la capital: de qué pueblo es y lo que ha dado probarla. */
	private record Plaza<T>(Holder.Reference<Structure> pueblo, T resultado) {
	}

	/** Dónde probar la plaza, en chunks desde el de la capital: primero el suyo y luego alrededor. */
	private static final int[][] DESPLAZAMIENTOS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final int[][] DIAGONALES = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

	/** Intentos de montar un pueblo que no salga abandonado antes de pasar al siguiente estilo. */
	private static final int INTENTOS = 6;

	/**
	 * Prueba los pueblos candidatos del bioma en cada desplazamiento (el chunk de la capital y luego los de al lado)
	 * hasta que uno empieza: prueba recibe la copia agrandada y el contexto de ese chunk. avisa: apunta en el log
	 * cada pueblo que no ha podido.
	 */
	private static <T> Optional<Plaza<T>> primeraQueEmpieza(Structure.GenerationContext ctx, Holder<Biome> bioma, boolean avisa,
			BiFunction<Structure, Structure.GenerationContext, Optional<T>> prueba) {
		List<Holder.Reference<Structure>> orden = PueblosCapital.candidatos(ctx.registryAccess(), bioma, ctx.chunkPos().x,
				ctx.chunkPos().z, ctx.seed());
		for (int[] d : DESPLAZAMIENTOS) {
			Structure.GenerationContext aqui = contexto(ctx, new ChunkPos(ctx.chunkPos().x + d[0], ctx.chunkPos().z + d[1]), ctx.seed());
			for (Holder.Reference<Structure> candidato : orden) {
				Optional<T> resultado = PueblosCapital.agrandado(ctx.registryAccess(), candidato, MAS_PROFUNDIDAD, DISTANCIA)
						.flatMap(copia -> prueba.apply(copia, aqui));
				if (resultado.isPresent()) {
					return Optional.of(new Plaza<>(candidato, resultado.get()));
				}
				if (avisa) {
					DestinosCore.LOG.info("Destinos Core: la capital del chunk {} ({}) no ha podido empezar con {} en {}; prueba otro",
							ctx.chunkPos(), bioma.unwrapKey().map(Object::toString).orElse("?"),
							ctx.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(candidato.value()), aqui.chunkPos());
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * Si en (x, z) puede empezar una capital (algún pueblo, en su chunk o al lado). Solo prueba la plaza de inicio,
	 * sin montar el pueblo entero, así que es barato. Lo usa MapaTerreno para elegir el sitio antes de poner la
	 * corona: así la corona va siempre donde la capital sí sale.
	 */
	static boolean cabe(ServerLevel level, int x, int z) {
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		Structure.GenerationContext ctx = new Structure.GenerationContext(level.registryAccess(), gen, gen.getBiomeSource(),
				level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(),
				new ChunkPos(x >> 4, z >> 4), level, b -> true);
		return primeraQueEmpieza(ctx, biomaCentro(ctx), false, (copia, aqui) -> {
			for (int intento = 0; intento < INTENTOS; intento++) {
				if (copia.findValidGenerationPoint(contexto(aqui, aqui.chunkPos(), aqui.seed() + intento * Geo.MEZCLA)).isPresent()) {
					return Optional.of(Boolean.TRUE);
				}
			}
			return Optional.<Boolean>empty();
		}).isPresent();
	}

	/** El mismo contexto en otro chunk y con otra semilla, y sin mirar el bioma (la capital ya ha elegido pueblo). */
	private static Structure.GenerationContext contexto(Structure.GenerationContext ctx, ChunkPos chunk, long semilla) {
		return new Structure.GenerationContext(ctx.registryAccess(), ctx.chunkGenerator(), ctx.biomeSource(), ctx.randomState(),
				ctx.structureTemplateManager(), semilla, chunk, ctx.heightAccessor(), b -> true);
	}

	/** Altura de la superficie en (x, z) según el generador, sin generar el chunk. */
	private static int suelo(Structure.GenerationContext ctx, int x, int z) {
		return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(),
				ctx.randomState());
	}

	/** El bioma en la superficie del centro del chunk: decide el estilo de la capital. */
	private static Holder<Biome> biomaCentro(Structure.GenerationContext ctx) {
		int x = ctx.chunkPos().getMiddleBlockX();
		int z = ctx.chunkPos().getMiddleBlockZ();
		return ctx.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(suelo(ctx, x, z)), QuartPos.fromBlock(z),
				ctx.randomState().sampler());
	}

	/**
	 * Monta el pueblo en ese chunk, pero no uno abandonado: los pueblos zombi de vanilla (y los abandonados de
	 * algunos mods) salen del mismo pueblo, con una plaza de inicio rara; si toca una, se vuelve a tirar con otra
	 * semilla. Las casas abandonadas sueltas dentro de un pueblo normal se quitan.
	 */
	private static Optional<Montado> monta(Structure pueblo, Structure.GenerationContext ctx) {
		for (int intento = 0; intento < INTENTOS; intento++) {
			// con otra semilla puede tocar otra plaza de inicio que sí quepa: no se rinde a la primera
			Optional<Structure.GenerationStub> stub = pueblo.findValidGenerationPoint(contexto(ctx, ctx.chunkPos(),
					ctx.seed() + intento * Geo.MEZCLA));
			if (stub.isEmpty()) {
				continue;
			}
			List<StructurePiece> piezas = stub.get().getPiecesBuilder().build().pieces();
			if (piezas.isEmpty()) {
				continue;
			}
			// abandonado de verdad: la mayoría de sus piezas (los pueblos zombi lo son enteros); una casa suelta
			// con un nombre así no basta
			long abandonadas = piezas.stream().filter(PueblosCapital::abandonada).count();
			if (abandonadas * 2 > piezas.size()) {
				DestinosCore.LOG.info("Destinos Core: la capital del chunk {} salía abandonada ({} de {} piezas, plaza {}); se vuelve a tirar",
						ctx.chunkPos(), abandonadas, piezas.size(), piezas.get(0) instanceof PoolElementStructurePiece p ? p.getElement() : "?");
				continue;
			}
			List<StructurePiece> buenas = new ArrayList<>(piezas.size());
			for (int i = 0; i < piezas.size(); i++) {
				if (i == 0 || !PueblosCapital.abandonada(piezas.get(i))) {
					buenas.add(piezas.get(i));
				}
			}
			return Optional.of(new Montado(stub.get().position(), buenas));
		}
		return Optional.empty();
	}

	/** Tramos de calle de (x0, z0) a (x1, z1), cada uno a la altura del suelo en su mitad. */
	private static List<CalleCapital> calles(Structure.GenerationContext ctx, Agua agua, int x0, int z0, int x1, int z1) {
		List<CalleCapital> calles = new ArrayList<>();
		int largo = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
		int seguidos = 0;
		for (int d = 0; d <= largo; d += 4) {
			seguidos = agua.en(x0 + (x1 - x0) * d / Math.max(1, largo), z0 + (z1 - z0) * d / Math.max(1, largo)) ? seguidos + 1 : 0;
			if (seguidos > MAX_AGUA_CALLE / 4) {
				return calles;
			}
		}
		int tramos = Math.max(1, (largo + TRAMO - 1) / TRAMO);
		for (int i = 0; i < tramos; i++) {
			int ax = x0 + (x1 - x0) * i / tramos;
			int az = z0 + (z1 - z0) * i / tramos;
			int bx = x0 + (x1 - x0) * (i + 1) / tramos;
			int bz = z0 + (z1 - z0) * (i + 1) / tramos;
			calles.add(new CalleCapital(ax, az, bx, bz, suelo(ctx, (ax + bx) / 2, (az + bz) / 2)));
		}
		return calles;
	}

	/** Dónde hay agua en vez de tierra (fondo por debajo del nivel del mar), mirado por casillas de 16 bloques. */
	private static final class Agua {
		private final Structure.GenerationContext ctx;
		private final Map<Long, Boolean> visto = new ConcurrentHashMap<>();

		Agua(Structure.GenerationContext ctx) {
			this.ctx = ctx;
		}

		boolean en(int x, int z) {
			return visto.computeIfAbsent(Geo.mezcla(Geo.clave(x >> 4, z >> 4)), k ->
					ctx.chunkGenerator().getBaseHeight((x & ~15) + 8, (z & ~15) + 8, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(),
							ctx.randomState()) < ctx.chunkGenerator().getSeaLevel());
		}

		/** Si la pieza está casi toda sobre el agua: el centro y al menos dos esquinas. */
		boolean tapa(StructurePiece pieza) {
			BoundingBox c = pieza.getBoundingBox();
			return en(c.getCenter().getX(), c.getCenter().getZ()) && esquinasMojadas(c) >= 2;
		}

		private int esquinasMojadas(BoundingBox c) {
			return (en(c.minX(), c.minZ()) ? 1 : 0) + (en(c.maxX(), c.minZ()) ? 1 : 0)
					+ (en(c.minX(), c.maxZ()) ? 1 : 0) + (en(c.maxX(), c.maxZ()) ? 1 : 0);
		}
	}

	@Override
	public StructureType<?> type() {
		return TIPO;
	}
}
