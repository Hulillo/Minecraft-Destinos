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
		pon(region, chunk, estructuras, generador, false);
	}

	/**
	 * Al acabar la decoración del chunk, otra pasada por lo construido: lo que se ha hecho después (los ríos de los
	 * mods de ríos, que se excavan como decoración y partían casas y plazas por la mitad; los medios bloques de terreno)
	 * lo ha podido romper. Las casas, obras, plazas, calles y muralla se vuelven a poner igual (las calles hacen
	 * puente donde ahora hay agua y la muralla su reja); la gente y el adorno no, que saldrían dos veces. Y los medios
	 * bloques de terreno que han quedado pegados a lo construido se quitan.
	 */
	public static void repasa(WorldGenLevel region, ChunkAccess chunk, StructureManager estructuras, ChunkGenerator generador) {
		pon(region, chunk, estructuras, generador, true);
	}

	/** Si en el repaso la pieza se vuelve a poner: no la gente (plantillas de un bloque con su ser), ni el adorno, ni las marcas, ni la muralla. */
	private static boolean seRepasa(StructurePiece pieza) {
		// la muralla tampoco: en el repaso su columna encontraba su propio adarve (de piedra, como las calles) y le
		// ponía encima un dintel de puerta, y quedaban almenas flotando
		if (pieza instanceof AdornoCapital || pieza instanceof MarcaAsentamiento || pieza instanceof MurallaCapital) {
			return false;
		}
		BoundingBox b = pieza.getBoundingBox();
		return !(pieza instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece) || b.getXSpan() > 2 || b.getZSpan() > 2;
	}

	/** Quita los medios bloques de terreno de los mods (Terrain Slabs…) de las columnas de la caja b (y uno alrededor) dentro de caja. */
	private static void sinLosas(WorldGenLevel region, BoundingBox b, BoundingBox caja) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = Math.max(b.minX() - 1, caja.minX()); x <= Math.min(b.maxX() + 1, caja.maxX()); x++) {
			for (int z = Math.max(b.minZ() - 1, caja.minZ()); z <= Math.min(b.maxZ() + 1, caja.maxZ()); z++) {
				int y = SueloNatural.cima(region, x, z);
				if (y >= b.minY() - 2 && y <= b.maxY() + 1 && SueloNatural.deTerreno(region.getBlockState(pos.set(x, y, z)))) {
					region.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
	}

	/** Si este hilo está en el repaso: las obras no vuelven a poner sus seres (guardias, caballos…). */
	private static final ThreadLocal<Boolean> REPASO = ThreadLocal.withInitial(() -> false);

	static boolean enRepaso() {
		return REPASO.get();
	}

	private static void pon(WorldGenLevel region, ChunkAccess chunk, StructureManager estructuras, ChunkGenerator generador, boolean repaso) {
		REPASO.set(repaso);
		try {
			ponTrozo(region, chunk, estructuras, generador, repaso);
		} finally {
			REPASO.set(false);
		}
	}

	private static void ponTrozo(WorldGenLevel region, ChunkAccess chunk, StructureManager estructuras, ChunkGenerator generador, boolean repaso) {
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
		long semilla = azar.setDecorationSeed(level.getSeed(), cp.getMinBlockX(), cp.getMinBlockZ());
		int n = 0;
		for (Plan plan : planes) {
			if (!plan.caja().intersects(caja)) {
				continue;
			}
			if (!repaso && plan.empezado().compareAndSet(false, true)) {
				BlockPos c = plan.caja().getCenter();
				DestinosCore.LOG.info("Destinos Core: se empieza a construir {} de {}, {} ({} piezas) por el chunk {}, {}", plan.que(), c.getX(), c.getZ(),
						plan.piezas().size(), cp.x, cp.z);
			}
			for (StructurePiece pieza : plan.piezas()) {
				// cada pieza con su azar (el mismo en la construcción y en el repaso: sale igual)
				n++;
				if (pieza.getBoundingBox().intersects(caja) && (!repaso || seRepasa(pieza))) {
					azar.setFeatureSeed(semilla, n, 0);
					if (repaso) {
						sinLosas(region, pieza.getBoundingBox(), caja);
					}
					long t = System.nanoTime();
					pieza.postProcess(region, estructuras, generador, azar, caja, cp, plan.pivote());
					TiemposPiezas.suma(pieza, System.nanoTime() - t);
				}
			}
		}
		if (repaso && chunk instanceof net.minecraft.world.level.chunk.ProtoChunk proto) {
			desatasca(region, proto);
		}
	}

	/** Los seres que se sacan si quedan atascados o en el agua (lo que ponen los pueblos y su gente). */
	private static final java.util.Set<String> DE_TIERRA = java.util.Set.of("minecraft:villager", "minecraft:iron_golem", "minecraft:cat",
			"minecraft:horse", "minecraft:donkey", "minecraft:sheep", "minecraft:pig", "minecraft:chicken", "minecraft:cow",
			"minecraft:zombie_villager", "minecraft:armor_stand", "minecraft:mannequin", "minecraft:wandering_trader", "destinos_core:persona");

	/** Lo que se busca alrededor de un ser atascado: hasta tantos bloques a los lados, y de uno abajo a tres arriba. */
	private static final int HUECO = 4;

	/**
	 * Los seres que han quedado dentro de un bloque o en el agua (los aldeanos a la puerta de su casa, los gólems de
	 * la plaza, cuando lo de después ha puesto un puesto o un río encima) se pasan al sitio libre más cercano, en el
	 * suelo: si no, se ahogaban nada más cargar. El chunk aún no tiene entidades vivas, solo sus datos.
	 */
	private static void desatasca(WorldGenLevel region, net.minecraft.world.level.chunk.ProtoChunk chunk) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (net.minecraft.nbt.CompoundTag ser : chunk.getEntities()) {
			net.minecraft.nbt.ListTag p = ser.getListOrEmpty("Pos");
			if (p.size() != 3) {
				continue;
			}
			double x = p.getDoubleOr(0, 0);
			double y = p.getDoubleOr(1, 0);
			double z = p.getDoubleOr(2, 0);
			String id = ser.getStringOr("id", "");
			// solo la gente y los animales de tierra de los pueblos: las barcas y los bichos de agua van en el agua
			if (!DE_TIERRA.contains(id)) {
				continue;
			}
			int alto = id.endsWith("iron_golem") ? 3 : 2;
			int bx = net.minecraft.util.Mth.floor(x);
			int by = net.minecraft.util.Mth.floor(y);
			int bz = net.minecraft.util.Mth.floor(z);
			if (libre(region, pos, bx, by, bz, alto)) {
				continue;
			}
			ChunkPos cp = chunk.getPos();
			buscar:
			for (int r = 0; r <= HUECO; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r || (bx + dx) >> 4 != cp.x || (bz + dz) >> 4 != cp.z) {
							continue;
						}
						for (int dy : new int[] {0, 1, -1, 2, 3}) {
							if (libre(region, pos, bx + dx, by + dy, bz + dz, alto)) {
								net.minecraft.nbt.ListTag nueva = new net.minecraft.nbt.ListTag();
								nueva.add(net.minecraft.nbt.DoubleTag.valueOf(bx + dx + 0.5));
								nueva.add(net.minecraft.nbt.DoubleTag.valueOf(by + dy));
								nueva.add(net.minecraft.nbt.DoubleTag.valueOf(bz + dz + 0.5));
								ser.put("Pos", nueva);
								break buscar;
							}
						}
					}
				}
			}
		}
	}

	/** Si en (x, y, z) cabe de pie un ser de alto bloques: suelo firme debajo y nada sólido ni agua donde va. */
	private static boolean libre(WorldGenLevel region, BlockPos.MutableBlockPos pos, int x, int y, int z, int alto) {
		if (!region.getBlockState(pos.set(x, y - 1, z)).blocksMotion()) {
			return false;
		}
		for (int k = 0; k < alto; k++) {
			var s = region.getBlockState(pos.set(x, y + k, z));
			if (s.blocksMotion() || !s.getFluidState().isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
