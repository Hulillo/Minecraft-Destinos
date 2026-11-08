package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;

/**
 * Cómo salen en el mundo las capitales y los pueblos propios, sin depender del sistema de estructuras de Minecraft
 * (que con C2ME o Structurify a veces los preparaba y no los guardaba en su chunk):
 * <ol>
 * <li>cada asentamiento se traza una vez, a partir de la semilla, y el plano (sus piezas) se guarda en memoria;</li>
 * <li>al hacer el terreno de cada chunk (paso NOISE), las piezas de cerca lo amoldan como antes (relleno por debajo y
 * desmonte por encima, el "beard" de Minecraft): así nada queda medio enterrado;</li>
 * <li>al decorar cada chunk (antes de los árboles y la hierba), cada chunk pone su trozo de las piezas que lo tocan.</li>
 * </ol>
 * Todo sale del plano, que es siempre el mismo para la misma semilla: da igual en qué orden o en qué hilo se generen
 * los chunks. Solo en el mundo normal y en chunks nuevos.
 */
public final class ObrasMundo {
	static final Identifier CAPITAL = Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "capital");
	static final Identifier PUEBLO = Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "pueblo");
	/** Lo más lejos que llega un asentamiento desde su sitio, en chunks (su radio, la muralla y el relleno). */
	static final int ALCANCE = 10;
	/** Hasta dónde amolda el terreno una pieza (como Minecraft). */
	private static final int BARBA = 12;
	/** Planos en memoria; si hay más, se tiran (se pueden volver a trazar, salen iguales). */
	private static final int MAX_PLANES = 96;

	/** El plano de un asentamiento: sus piezas, lo que ocupa y el pivote con que se ponen (como StructureStart). */
	record Plan(String que, List<StructurePiece> piezas, BoundingBox caja, BlockPos pivote, AtomicBoolean empezado) {
	}

	private static final Plan NINGUNO = new Plan("", List.of(), null, null, new AtomicBoolean());
	private static final ConcurrentHashMap<Long, CompletableFuture<Plan>> PLANES = new ConcurrentHashMap<>();

	private ObrasMundo() {
	}

	/** Al cerrar el mundo. */
	static void olvida() {
		PLANES.clear();
	}

	/** El mundo del servidor detrás de un nivel de generación, si es el mundo normal; si no, null. */
	static ServerLevel normal(LevelAccessor l) {
		ServerLevel s = l instanceof WorldGenRegion r ? r.getLevel() : l instanceof ServerLevel sl ? sl : null;
		return s != null && s.dimension() == Level.OVERWORLD ? s : null;
	}

	/** Los planos de los asentamientos que pueden tocar el chunk (cx, cz). */
	static List<Plan> cerca(ServerLevel level, int cx, int cz) {
		List<Plan> r = new ArrayList<>(2);
		MapaTerreno.Capital c = ColocacionCapital.capitalCerca(cx, cz, ALCANCE);
		if (c != null) {
			mete(r, plan(level, CAPITAL, c.x(), c.z()));
		}
		for (MapaTerreno.Pueblo p : ColocacionPueblo.pueblosCerca(cx, cz, ALCANCE)) {
			mete(r, plan(level, PUEBLO, p.x(), p.z()));
		}
		return r;
	}

	private static void mete(List<Plan> r, Plan p) {
		if (p.caja() != null) {
			r.add(p);
		}
	}

	/** El plano del asentamiento (capital o pueblo) con sitio en (x, z): se traza una sola vez aunque lo pidan varios hilos. */
	static Plan plan(ServerLevel level, Identifier que, int x, int z) {
		long clave = ChunkPos.asLong(x >> 4, z >> 4) * 2 + (que.equals(CAPITAL) ? 0 : 1);
		CompletableFuture<Plan> f = PLANES.get(clave);
		if (f == null) {
			if (PLANES.size() > MAX_PLANES) {
				PLANES.clear();
			}
			CompletableFuture<Plan> nuevo = new CompletableFuture<>();
			f = PLANES.putIfAbsent(clave, nuevo);
			if (f == null) {
				f = nuevo;
				Plan p = NINGUNO;
				try {
					p = traza(level, que, x, z);
				} catch (RuntimeException e) {
					DestinosCore.LOG.error("Destinos Core: no se ha podido trazar {} de {}, {}", que.getPath(), x, z, e);
				}
				nuevo.complete(p);
			}
		}
		return f.join();
	}

	/** El plano ya trazado del asentamiento con sitio en (x, z), sin trazarlo si no lo está; o null. */
	static Plan trazado(Identifier que, int x, int z) {
		CompletableFuture<Plan> f = PLANES.get(ChunkPos.asLong(x >> 4, z >> 4) * 2 + (que.equals(CAPITAL) ? 0 : 1));
		return f == null ? null : f.getNow(null);
	}

	private static Plan traza(ServerLevel level, Identifier que, int x, int z) {
		if (!(level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(que) instanceof EstructuraCapital e)) {
			return NINGUNO;
		}
		StructureStart inicio = e.inicio(level, x, z);
		if (inicio == null || !inicio.isValid()) {
			return NINGUNO;
		}
		List<StructurePiece> piezas = List.copyOf(inicio.getPieces());
		// como StructureStart.placeInChunk: el pivote es el centro de la primera pieza, a su altura de abajo
		BoundingBox primera = piezas.get(0).getBoundingBox();
		BlockPos c = primera.getCenter();
		return new Plan(que.getPath(), piezas, inicio.getBoundingBox(), new BlockPos(c.getX(), primera.minY(), c.getZ()), new AtomicBoolean());
	}

	/** Si p está dentro de alguna pieza de un asentamiento ya trazado (para los tablones de las campanas). */
	static boolean enAsentamiento(BlockPos p) {
		for (CompletableFuture<Plan> f : PLANES.values()) {
			Plan plan = f.getNow(null);
			if (plan == null || plan.caja() == null || !plan.caja().inflatedBy(2).isInside(p)) {
				continue;
			}
			for (StructurePiece pieza : plan.piezas()) {
				if (!(pieza instanceof AdornoCapital) && pieza.getBoundingBox().inflatedBy(2).isInside(p)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Estructura de mentira con que se le pasan al relleno de Minecraft las piezas de los planos (beard_thin, como antes). */
	private static final EstructuraCapital AMOLDA = new EstructuraCapital(new Structure.StructureSettings(HolderSet.empty(), Map.of(),
			GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.BEARD_THIN));

	/**
	 * El terreno del chunk se amolda a las piezas de cerca como hace Minecraft con las estructuras que lo piden: a las
	 * estructuras que ya iba a tener en cuenta se les añade una con las piezas de los planos que tocan el chunk (casas
	 * de pueblo con su altura de suelo, el resto por su caja), y el relleno de Minecraft hace lo demás.
	 */
	public static List<StructureStart> amolda(StructureManager estructuras, ChunkPos chunk, List<StructureStart> originales) {
		ServerLevel level = normal(((es.hulillo.destinos.mixin.AccesoStructureManager) estructuras).destinos$level());
		if (level == null) {
			return originales;
		}
		List<StructurePiece> cercanas = new ArrayList<>();
		for (Plan plan : cerca(level, chunk.x, chunk.z)) {
			if (!cerca(plan.caja(), chunk)) {
				continue;
			}
			for (StructurePiece pieza : plan.piezas()) {
				if (pieza.isCloseToChunk(chunk, BARBA) && !(pieza instanceof AdornoCapital) && !(pieza instanceof MuelleCapital)
						&& !(pieza instanceof MarcaAsentamiento)) {
					cercanas.add(pieza);
				}
			}
		}
		if (cercanas.isEmpty()) {
			return originales;
		}
		List<StructureStart> r = new ArrayList<>(originales);
		r.add(new StructureStart(AMOLDA, chunk, 0, new PiecesContainer(cercanas)));
		return r;
	}

	private static boolean cerca(BoundingBox caja, ChunkPos chunk) {
		return caja.maxX() >= chunk.getMinBlockX() - BARBA && caja.minX() <= chunk.getMaxBlockX() + BARBA
				&& caja.maxZ() >= chunk.getMinBlockZ() - BARBA && caja.minZ() <= chunk.getMaxBlockZ() + BARBA;
	}

	/** Pone en el chunk su trozo de las piezas de los asentamientos que lo tocan (al empezar su decoración). */
	public static void construye(WorldGenLevel region, ChunkAccess chunk, StructureManager estructuras, ChunkGenerator generador) {
		ServerLevel level = normal(region);
		if (level == null) {
			return;
		}
		ChunkPos cp = chunk.getPos();
		List<Plan> planes = cerca(level, cp.x, cp.z);
		if (planes.isEmpty()) {
			return;
		}
		BoundingBox caja = new BoundingBox(cp.getMinBlockX(), region.getMinY(), cp.getMinBlockZ(), cp.getMaxBlockX(),
				region.getMinY() + region.getHeight() - 1, cp.getMaxBlockZ());
		WorldgenRandom azar = new WorldgenRandom(new LegacyRandomSource(0L));
		azar.setDecorationSeed(level.getSeed(), cp.getMinBlockX(), cp.getMinBlockZ());
		for (Plan plan : planes) {
			if (!plan.caja().intersects(caja)) {
				continue;
			}
			if (plan.empezado().compareAndSet(false, true)) {
				BlockPos c = plan.caja().getCenter();
				DestinosCore.LOG.info("Destinos Core: se empieza a construir {} de {}, {} ({} piezas) por el chunk {}, {}", plan.que(), c.getX(), c.getZ(),
						plan.piezas().size(), cp.x, cp.z);
			}
			for (StructurePiece pieza : plan.piezas()) {
				if (pieza.getBoundingBox().intersects(caja)) {
					long t = System.nanoTime();
					pieza.postProcess(region, estructuras, generador, azar, caja, cp, plan.pivote());
					TiemposPiezas.suma(pieza, System.nanoTime() - t);
				}
			}
		}
	}
}
