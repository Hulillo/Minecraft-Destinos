package es.hulillo.destinos;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/**
 * La capital de un reino: el pueblo que tocaría en ese bioma (de vanilla o de cualquier mod, como Towns and
 * Towers), pero mucho más grande. No tiene piezas propias: copia la estructura de pueblo con más profundidad y
 * la genera en el centro y en cuatro barrios alrededor, sin que las piezas de un barrio pisen las de otro.
 * Sale donde dice ColocacionCapital.
 *
 * La copia se hace con el codec de las estructuras (JSON de ida y vuelta) para no depender de los campos internos
 * de JigsawStructure, que cambian entre versiones.
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

	/** Pueblos agrandados ya preparados (por pueblo, profundidad y distancia), para los registros del mundo abierto. */
	private static final Map<String, Optional<Structure>> COPIAS = new ConcurrentHashMap<>();
	/** Capitales ya preparadas, por chunk (las últimas; hay pocas y se piden varias veces seguidas). */
	private static final Map<Long, Optional<Structure.GenerationStub>> PREPARADAS = new java.util.LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Optional<Structure.GenerationStub>> mas) {
			return size() > 16;
		}
	};
	private static volatile RegistryAccess registrosCopias;

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
		int x = ctx.chunkPos().getMiddleBlockX();
		int z = ctx.chunkPos().getMiddleBlockZ();
		int y = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(),
				ctx.randomState());
		Holder<Biome> bioma = ctx.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
				QuartPos.fromBlock(z), ctx.randomState().sampler());
		Registry<Structure> estructuras = ctx.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		// los pueblos que pueden salir en este bioma; si ninguno, uno vanilla según el clima
		List<Holder.Reference<Structure>> pueblos = new ArrayList<>();
		estructuras.listElements().forEach(h -> {
			if (h.value() instanceof JigsawStructure && esPueblo(estructuras, h) && h.value().biomes().contains(bioma)) {
				pueblos.add(h);
			}
		});
		// el que toca en este chunk primero; si no puede empezar (algunos pueblos de mods piden un sitio muy
		// concreto), los demás del bioma y al final el vanilla del clima
		List<Holder.Reference<Structure>> orden = new ArrayList<>();
		if (!pueblos.isEmpty()) {
			pueblos.sort((a, b) -> String.valueOf(estructuras.getKey(a.value())).compareTo(String.valueOf(estructuras.getKey(b.value()))));
			int i = Math.floorMod(Geo.hash(ctx.chunkPos().x, ctx.chunkPos().z, (int) ctx.seed()), pueblos.size());
			for (int k = 0; k < pueblos.size(); k++) {
				orden.add(pueblos.get((i + k) % pueblos.size()));
			}
		}
		float temperatura = bioma.value().getBaseTemperature();
		String porDefecto = temperatura < 0.2F ? "village_snowy" : temperatura > 1.5F ? "village_desert" : "village_plains";
		estructuras.get(Identifier.withDefaultNamespace(porDefecto)).filter(h -> !orden.contains(h)).ifPresent(orden::add);
		Holder.Reference<Structure> pueblo = null;
		Identifier id = null;
		Optional<Montado> plaza = Optional.empty();
		// primero sin pueblos abandonados; si con ningún estilo sale otra cosa, mejor uno así que una corona vacía
		// y si en el sitio justo no cabe ninguno, la plaza un poco más allá (la capital sigue siendo de este chunk)
		for (int pasada = 0; pasada < 2 && pueblo == null; pasada++) {
			boolean abandonadoVale = pasada == 1;
			for (int[] d : DESPLAZAMIENTOS) {
				if (pueblo != null) {
					break;
				}
				Structure.GenerationContext aqui = d[0] == 0 && d[1] == 0 ? ctx : new Structure.GenerationContext(
						ctx.registryAccess(), ctx.chunkGenerator(), ctx.biomeSource(), ctx.randomState(),
						ctx.structureTemplateManager(), ctx.seed(), new ChunkPos(ctx.chunkPos().x + d[0], ctx.chunkPos().z + d[1]),
						ctx.heightAccessor(), b -> true);
				for (Holder.Reference<Structure> candidato : orden) {
					Identifier idCandidato = estructuras.getKey(candidato.value());
					plaza = agrandado(ctx.registryAccess(), idCandidato, candidato.value(), MAS_PROFUNDIDAD, DISTANCIA)
							.flatMap(s -> monta(s, aqui, abandonadoVale));
					if (plaza.isPresent()) {
						pueblo = candidato;
						id = idCandidato;
						break;
					}
					DestinosCore.LOG.info("Destinos Core: la capital del chunk {} ({}) no ha podido empezar con {}{} en {}; prueba otro",
							ctx.chunkPos(), bioma.unwrapKey().map(Object::toString).orElse("?"), idCandidato,
							abandonadoVale ? " (ni abandonado)" : "", aqui.chunkPos());
				}
			}
		}
		if (pueblo == null) {
			DestinosCore.LOG.warn("Destinos Core: la capital del chunk {} no ha podido empezar con ningún pueblo", ctx.chunkPos());
			return Optional.empty();
		}
		// los barrios: el mismo pueblo, en las cuatro diagonales (menos los que caen en el agua)
		Agua agua = new Agua(ctx);
		List<Montado> barrios = new ArrayList<>();
		Optional<Structure> barrio = agrandado(ctx.registryAccess(), id, pueblo.value(), MAS_PROFUNDIDAD_BARRIO, DISTANCIA_BARRIO);
		if (barrio.isPresent()) {
			for (int[] d : new int[][] {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
				ChunkPos sitio = new ChunkPos(ctx.chunkPos().x + d[0] * SEPARACION_BARRIO / 16,
						ctx.chunkPos().z + d[1] * SEPARACION_BARRIO / 16);
				Structure.GenerationContext otro = new Structure.GenerationContext(ctx.registryAccess(), ctx.chunkGenerator(),
						ctx.biomeSource(), ctx.randomState(), ctx.structureTemplateManager(), ctx.seed(), sitio,
						ctx.heightAccessor(), b -> true);
				if (!agua.en(sitio.getMiddleBlockX(), sitio.getMiddleBlockZ())) {
					monta(barrio.get(), otro, false).ifPresent(barrios::add);
				}
			}
		}
		Montado principal = plaza.get();
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
		MurallaCapital.piezas(principal.position().getX(), principal.position().getZ(), nivel, (mx, mz) ->
				ctx.chunkGenerator().getFirstOccupiedHeight(mx, mz, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(),
						ctx.randomState()), muralla);
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
	private record Montado(net.minecraft.core.BlockPos position, List<StructurePiece> piezas) {
	}

	/** Dónde probar la plaza, en chunks desde el de la capital: primero el suyo y luego alrededor. */
	private static final int[][] DESPLAZAMIENTOS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** Intentos de montar un pueblo que no salga abandonado antes de pasar al siguiente estilo. */
	private static final int INTENTOS = 6;

	/**
	 * Monta el pueblo en ese chunk, pero no uno abandonado: los pueblos zombi de vanilla (y los abandonados de
	 * algunos mods) salen del mismo pueblo, con una plaza de inicio rara; si toca una, se vuelve a tirar con otra
	 * semilla. Las casas abandonadas sueltas dentro de un pueblo normal se quitan.
	 */
	private static Optional<Montado> monta(Structure pueblo, Structure.GenerationContext ctx, boolean abandonadoVale) {
		for (int intento = 0; intento < INTENTOS; intento++) {
			// el pueblo copiado tampoco mira el bioma (ya lo ha elegido prepara para este sitio)
			Structure.GenerationContext c = new Structure.GenerationContext(ctx.registryAccess(),
					ctx.chunkGenerator(), ctx.biomeSource(), ctx.randomState(), ctx.structureTemplateManager(),
					ctx.seed() + intento * 0x9E3779B97F4A7C15L, ctx.chunkPos(), ctx.heightAccessor(), b -> true);
			// con otra semilla puede tocar otra plaza de inicio que sí quepa: no se rinde a la primera
			Optional<Structure.GenerationStub> stub = pueblo.findValidGenerationPoint(c);
			if (stub.isEmpty()) {
				continue;
			}
			List<StructurePiece> piezas = stub.get().getPiecesBuilder().build().pieces();
			if (piezas.isEmpty()) {
				continue;
			}
			// abandonado de verdad: la mayoría de sus piezas (los pueblos zombi lo son enteros); una casa suelta
			// con un nombre así no basta
			int abandonadas = 0;
			for (StructurePiece pieza : piezas) {
				abandonadas += abandonada(pieza) ? 1 : 0;
			}
			if (!abandonadoVale && abandonadas * 2 > piezas.size()) {
				DestinosCore.LOG.info("Destinos Core: la capital del chunk {} salía abandonada ({} de {} piezas, plaza {}); se vuelve a tirar",
						ctx.chunkPos(), abandonadas, piezas.size(), piezas.get(0) instanceof PoolElementStructurePiece p ? p.getElement() : "?");
				continue;
			}
			List<StructurePiece> buenas = new ArrayList<>(piezas.size());
			for (int i = 0; i < piezas.size(); i++) {
				if (i == 0 || abandonadoVale || !abandonada(piezas.get(i))) {
					buenas.add(piezas.get(i));
				}
			}
			return Optional.of(new Montado(stub.get().position(), buenas));
		}
		return Optional.empty();
	}

	/** Si la pieza es de un pueblo abandonado (por el nombre de su plantilla). */
	private static boolean abandonada(StructurePiece pieza) {
		if (!(pieza instanceof PoolElementStructurePiece p)) {
			return false;
		}
		String plantilla = p.getElement().toString().toLowerCase(java.util.Locale.ROOT);
		for (String no : ABANDONADO) {
			if (plantilla.contains(no)) {
				return true;
			}
		}
		return false;
	}

	private static final String[] ABANDONADO = {"zombie", "abandon", "ruin", "destroyed", "burn", "haunt", "ghost",
			"derelict"};

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
			int y = ctx.chunkGenerator().getFirstOccupiedHeight((ax + bx) / 2, (az + bz) / 2, Heightmap.Types.WORLD_SURFACE_WG,
					ctx.heightAccessor(), ctx.randomState());
			calles.add(new CalleCapital(ax, az, bx, bz, y));
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
			// la casilla mezclada: con x en los bits altos y z en los bajos, Long.hashCode (x ^ z) se repite mucho
			return visto.computeIfAbsent((((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL)) * 0x9E3779B97F4A7C15L, k ->
					ctx.chunkGenerator().getBaseHeight((x & ~15) + 8, (z & ~15) + 8, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(),
							ctx.randomState()) < ctx.chunkGenerator().getSeaLevel());
		}

		/** Si la pieza está casi toda sobre el agua: el centro y al menos dos esquinas. */
		boolean tapa(StructurePiece pieza) {
			BoundingBox c = pieza.getBoundingBox();
			if (!en(c.getCenter().getX(), c.getCenter().getZ())) {
				return false;
			}
			int esquinas = (en(c.minX(), c.minZ()) ? 1 : 0) + (en(c.maxX(), c.minZ()) ? 1 : 0)
					+ (en(c.minX(), c.maxZ()) ? 1 : 0) + (en(c.maxX(), c.maxZ()) ? 1 : 0);
			return esquinas >= 2;
		}
	}

	/**
	 * Un pueblo de verdad para copiar: del tag de pueblos o con "village" en el nombre, pero no los campamentos,
	 * ruinas o pueblos dispersos que algunos mods llaman así (structory:outcast_villager_*,
	 * nova_structures:remnant_*, towns_and_towers:village_sparse_*…).
	 */
	private static boolean esPueblo(Registry<Structure> estructuras, Holder.Reference<Structure> h) {
		Identifier id = estructuras.getKey(h.value());
		if (id == null || id.getNamespace().equals(DestinosCore.MOD_ID)) {
			return false;
		}
		String ruta = id.getPath();
		for (String no : NO_PUEBLO) {
			if (ruta.contains(no)) {
				return false;
			}
		}
		return h.is(StructureTags.VILLAGE) || ruta.matches("(.*[/_])?villages?([/_].*)?");
	}

	private static final String[] NO_PUEBLO = {"villager", "outcast", "remnant", "ruin", "abandon", "sparse", "zombie",
			"camp", "pillager", "illager", "outpost", "destroyed", "burn", "haunt", "ghost", "ancient", "lost", "tiny",
			"small", "hamlet", "derelict"};

	/** El pueblo con más profundidad, el alcance dado y válido en cualquier bioma del mundo normal. */
	private static Optional<Structure> agrandado(RegistryAccess registros, Identifier k, Structure pueblo, int mas,
			int alcance) {
		if (registrosCopias != registros) {
			COPIAS.clear();
			registrosCopias = registros;
		}
		return COPIAS.computeIfAbsent(k + "|" + mas + "|" + alcance, clave -> {
			RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registros);
			Optional<JsonElement> json = Structure.DIRECT_CODEC.encodeStart(ops, pueblo).result();
			if (json.isEmpty() || !json.get().isJsonObject()) {
				DestinosCore.LOG.warn("Destinos Core: no se ha podido copiar el pueblo {} para una capital", k);
				return Optional.empty();
			}
			JsonObject o = json.get().getAsJsonObject().deepCopy();
			int profundidad = o.has("size") ? o.get("size").getAsInt() : 6;
			o.addProperty("size", Math.min(20, profundidad + mas));
			JsonElement distancia = o.get("max_distance_from_center");
			if (distancia != null && distancia.isJsonObject()) {
				distancia.getAsJsonObject().addProperty("horizontal", alcance);
			} else {
				o.addProperty("max_distance_from_center", alcance);
			}
			// el bioma ya lo ha mirado findGenerationPoint (o es el pueblo de respaldo): que no lo vuelva a mirar
			o.addProperty("biomes", "#destinos_core:capital");
			Optional<Structure> copia = Structure.DIRECT_CODEC.parse(ops, o).resultOrPartial(error ->
					DestinosCore.LOG.warn("Destinos Core: el pueblo {} agrandado no es válido ({}); la capital usará el normal", k, error));
			if (copia.isEmpty()) {
				return Optional.of(pueblo);
			}
			DestinosCore.LOG.info("Destinos Core: capitales con el estilo de {} (profundidad {}, {} bloques)", k,
					Math.min(20, profundidad + mas), alcance);
			return copia;
		});
	}

	@Override
	public StructureType<?> type() {
		return TIPO;
	}
}
