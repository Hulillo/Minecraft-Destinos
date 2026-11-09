package es.hulillo.destinos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Un muelle de madera junto al río, el lago o el mar: desde la orilla en (x, z) se adentra en el agua hacia dir. El
 * sencillo es una pasarela de tres de ancho con pilotes, norays, farol en la punta y barcas. El grande (forma
 * PUERTO) es un puerto de verdad: pasarela larga con barandilla y farolas, pantalanes a los lados con barcas en sus
 * amarres, y al final una plataforma de carga con mercancía apilada (barriles, fardos, sacos) y una o dos grúas de
 * madera con su carga colgando de la cadena. Cada uno sale distinto (largo, ancho, pantalanes, grúas) por su semilla.
 * Mira el agua de verdad al ponerse (también la que tiene algas o hierba marina): donde no hay agua, no hace nada.
 */
public final class MuelleCapital extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) MuelleCapital::new;
	static final int LARGO = 9;

	private final int x;
	private final int z;
	private final int dir;
	/** Lo que se adentra en el agua (LARGO en las capitales; más en los puertos, con espigón en T al final). */
	private final int largo;
	/** SENCILLO o PUERTO. */
	private final int forma;
	private final int semilla;

	static final int SENCILLO = 0;
	static final int PUERTO = 1;

	MuelleCapital(int x, int z, int y, Direction dir) {
		this(x, z, y, dir, LARGO);
	}

	MuelleCapital(int x, int z, int y, Direction dir, int largo) {
		this(x, z, y, dir, largo, SENCILLO, 0);
	}

	private MuelleCapital(int x, int z, int y, Direction dir, int largo, int forma, int semilla) {
		super(TIPO, 0, forma == PUERTO ? cajaPuerto(x, z, y, dir, largo, semilla) : caja(x, z, y, dir, largo));
		this.x = x;
		this.z = z;
		this.dir = dir.get2DDataValue();
		this.largo = largo;
		this.forma = forma;
		this.semilla = semilla;
	}

	/** Un puerto grande que se adentra `largo` bloques (su ancho, pantalanes y grúas salen de la semilla). */
	static MuelleCapital puerto(int x, int z, int y, Direction dir, int largo, int semilla) {
		return new MuelleCapital(x, z, y, dir, largo, PUERTO, semilla);
	}

	/** Desde qué largo lleva espigón en T al final. */
	private static final int CON_ESPIGON = 14;
	private static final int ESPIGON = 4;

	int x() {
		return x;
	}

	int z() {
		return z;
	}

	Direction dir() {
		return Direction.from2DDataValue(dir);
	}

	private static BoundingBox caja(int x, int z, int y, Direction d, int largo) {
		int x1 = x + d.getStepX() * largo;
		int z1 = z + d.getStepZ() * largo;
		int m = largo >= CON_ESPIGON ? ESPIGON + 2 : 2;
		return new BoundingBox(Math.min(x, x1) - m, y - 2, Math.min(z, z1) - m, Math.max(x, x1) + m, y + 3, Math.max(z, z1) + m);
	}

	private static BoundingBox cajaPuerto(int x, int z, int y, Direction d, int largo, int semilla) {
		Forma f = new Forma(largo, semilla);
		int m = f.lado() + GRUA_BRAZO + 1;
		int x1 = x + d.getStepX() * (largo + 2);
		int z1 = z + d.getStepZ() * (largo + 2);
		return new BoundingBox(Math.min(x, x1) - m, y - 2, Math.min(z, z1) - m, Math.max(x, x1) + m, y + GRUA_ALTO + 2, Math.max(z, z1) + m);
	}

	/** Lo que sale del muelle de la plataforma a cada lado, en el más ancho de los puertos (para buscarle sitio). */
	static int ladoMaximo() {
		return ANCHO_MIN + ANCHO_VARIA - 1 + GRUA_BRAZO + 1;
	}

	private MuelleCapital(CompoundTag t) {
		super(TIPO, t);
		x = t.getIntOr("x", 0);
		z = t.getIntOr("z", 0);
		dir = t.getIntOr("dir", 0);
		largo = t.getIntOr("largo", LARGO);
		forma = t.getIntOr("forma", SENCILLO);
		semilla = t.getIntOr("semilla", 0);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "muelle"), TIPO);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		t.putInt("x", x);
		t.putInt("z", z);
		t.putInt("dir", dir);
		t.putInt("largo", largo);
		t.putInt("forma", forma);
		t.putInt("semilla", semilla);
	}

	@Override
	public boolean isCloseToChunk(ChunkPos chunk, int distancia) {
		return false;
	}

	/**
	 * La altura del agua en (x, z), o MIN_VALUE si arriba del todo no hay agua. Cuenta como agua lo que la lleva
	 * dentro (hierba marina, algas, bloques anegados): antes se quitaban como plantas, se veía la arena del fondo y el
	 * muelle se quedaba en pilotes sueltos. Si ya hay tablazón nuestra sobre el agua (el repaso), su altura.
	 */
	private static int agua(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		pos.set(x, SueloNatural.cima(level, x, z), z);
		for (int i = 0; i < 3; i++) {
			BlockState s = level.getBlockState(pos);
			if (s.getFluidState().is(FluidTags.WATER)) {
				return pos.getY();
			}
			if (s.is(Blocks.SPRUCE_PLANKS) && level.getBlockState(pos.below()).getFluidState().is(FluidTags.WATER)) {
				return pos.getY();
			}
			// lo que haya encima de la tablazón (barandilla, farolas, mercancía) o una flor en la orilla
			if (!s.isAir() && !s.canBeReplaced() && !s.is(Blocks.SPRUCE_FENCE) && !s.is(Blocks.LANTERN) && !s.is(Blocks.BARREL)
					&& !s.is(Blocks.HAY_BLOCK) && !s.is(Blocks.SPRUCE_LOG) && !s.is(Blocks.WHITE_WOOL) && !s.is(Blocks.BROWN_WOOL)) {
				return Integer.MIN_VALUE;
			}
			pos.move(0, -1, 0);
		}
		return Integer.MIN_VALUE;
	}

	/** Pilotes de tronco desde debajo de (x, y, z) hasta el fondo. */
	private static void pilote(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
		BlockState tronco = Blocks.SPRUCE_LOG.defaultBlockState();
		for (int k = y - 1; k > level.getMinY(); k--) {
			BlockState b = level.getBlockState(pos.set(x, k, z));
			if (!b.getFluidState().is(FluidTags.WATER) && !b.canBeReplaced()) {
				break;
			}
			level.setBlock(pos, tronco, 2);
		}
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar,
			BoundingBox caja, ChunkPos chunk, BlockPos pivote) {
		if (forma == PUERTO) {
			puerto(level, caja);
			return;
		}
		Direction d = Direction.from2DDataValue(dir);
		Direction lado = d.getClockWise();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState tabla = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState valla = Blocks.SPRUCE_FENCE.defaultBlockState();
		int ultimo = -1;
		int aguaY = Integer.MIN_VALUE;
		boolean espigon = largo >= CON_ESPIGON;
		for (int i = 0; i <= largo; i++) {
			int ancho = espigon && i >= largo - 2 ? ESPIGON : 1;
			for (int w = -ancho; w <= ancho; w++) {
				int cx = x + d.getStepX() * i + lado.getStepX() * w;
				int cz = z + d.getStepZ() * i + lado.getStepZ() * w;
				if (!caja.isInside(cx, caja.minY(), cz)) {
					continue;
				}
				int y = agua(level, pos, cx, cz);
				if (y == Integer.MIN_VALUE) {
					continue;
				}
				aguaY = y;
				ultimo = Math.max(ultimo, i);
				level.setBlock(pos.set(cx, y, cz), tabla, 2);
				// pilotes hasta el fondo en las esquinas de cada tramo, con su noray encima
				if (Math.abs(w) == ancho && i % 3 == 0 || ancho > 1 && i == largo && w % 2 == 0) {
					pilote(level, pos, cx, y, cz);
					level.setBlock(pos.set(cx, y + 1, cz), valla, 2);
				}
			}
		}
		if (ultimo < 2 || aguaY == Integer.MIN_VALUE) {
			return;
		}
		// el farol en la punta y las barcas amarradas a los lados
		int px = x + d.getStepX() * ultimo;
		int pz = z + d.getStepZ() * ultimo;
		if (caja.isInside(px, caja.minY(), pz)) {
			level.setBlock(pos.set(px, aguaY + 1, pz), valla, 2);
			level.setBlock(pos.set(px, aguaY + 2, pz), Blocks.LANTERN.defaultBlockState(), 2);
		}
		// en los muelles largos, una barca cada cinco bloques a cada lado (antes del espigón)
		for (int k = ultimo - 1; k >= 2; k -= 5) {
			for (int s = -1; s <= 1; s += 2) {
				int bx = x + d.getStepX() * (espigon && k >= largo - 3 ? k - 4 : k) + lado.getStepX() * 2 * s;
				int bz = z + d.getStepZ() * (espigon && k >= largo - 3 ? k - 4 : k) + lado.getStepZ() * 2 * s;
				barca(level, caja, pos, bx, aguaY, bz, d);
			}
			if (largo < CON_ESPIGON) {
				break;
			}
		}
	}

	private static void barca(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos, int bx, int aguaY, int bz, Direction mira) {
		if (caja.isInside(bx, caja.minY(), bz) && level.getBlockState(pos.set(bx, aguaY, bz)).is(Blocks.WATER)
				&& level.getBlockState(pos.set(bx, aguaY + 1, bz)).isAir()) {
			SeresObra.pon(level, new Obra.Ser(SeresObra.BARCA, 0, 0, 0, mira), new BlockPos(bx, aguaY + 1, bz), 0);
		}
	}

	// ---- el puerto grande ----

	/** Media anchura de la plataforma de carga (sin la pasarela) y lo que varía de un puerto a otro. */
	private static final int ANCHO_MIN = 5;
	private static final int ANCHO_VARIA = 4;
	/** Fondo de la plataforma de carga, a lo largo del muelle. */
	private static final int FONDO_MIN = 6;
	private static final int FONDO_VARIA = 3;
	/** Separación de los pantalanes a lo largo de la pasarela, y su largo. */
	private static final int CADA_PANTALAN = 6;
	private static final int PANTALAN_MIN = 4;
	private static final int PANTALAN_VARIA = 4;
	/** La grúa: alto del mástil y lo que sale el brazo sobre el agua. */
	private static final int GRUA_ALTO = 7;
	private static final int GRUA_BRAZO = 3;
	/** Cada cuánto va una farola en la barandilla. */
	private static final int FAROLA = 5;

	/**
	 * La forma del puerto, toda sacada de la semilla: cuánto sale la plataforma a cada lado (a veces solo a uno, en
	 * L), su fondo, los pantalanes (a qué lado y de qué largo) y las grúas.
	 */
	private record Forma(int largo, int izq, int der, int fondo, int[] pantalanes, boolean gruaIzq, boolean gruaDer, int semilla) {
		Forma(int largo, int semilla) {
			this(largo, semilla, Geo.hash(semilla, largo, 61331));
		}

		private Forma(int largo, int semilla, int h) {
			this(largo, lado(h, 0), lado(h, 1), FONDO_MIN + Math.floorMod(h >> 6, FONDO_VARIA),
					pantalanes(largo, FONDO_MIN + Math.floorMod(h >> 6, FONDO_VARIA), semilla),
					lado(h, 0) >= 4 && Math.floorMod(h >> 9, 3) != 0, lado(h, 1) >= 4 && Math.floorMod(h >> 11, 3) != 1, semilla);
		}

		/** Lo que sale la plataforma hacia un lado: lo normal, o 1 (solo la pasarela) en uno de cada tres puertos en L. */
		private static int lado(int h, int cual) {
			int ancho = ANCHO_MIN + Math.floorMod(h >> (cual * 3), ANCHO_VARIA);
			int ele = Math.floorMod(h >> 13, 6);
			return ele == cual ? 1 : ancho;
		}

		/** Los pantalanes: de cada uno, su lado (-1, 1 o 0 a ambos) y su largo, a lo largo de la pasarela. */
		private static int[] pantalanes(int largo, int fondo, int semilla) {
			int n = Math.max(0, (largo - fondo - 4) / CADA_PANTALAN);
			int[] r = new int[n * 2];
			for (int k = 0; k < n; k++) {
				int h = Geo.hash(semilla, k, 4721);
				r[k * 2] = Math.floorMod(h, 3) - 1;
				r[k * 2 + 1] = PANTALAN_MIN + Math.floorMod(h >> 3, PANTALAN_VARIA);
			}
			return r;
		}

		int lado() {
			return Math.max(izq, der);
		}

		int inicioPlataforma() {
			return largo - fondo;
		}

		/** Dónde empieza el pantalán k a lo largo del muelle. */
		int pantalan(int k) {
			return 4 + k * CADA_PANTALAN;
		}

		/** Si (i, w) es tablazón: pasarela, plataforma o pantalán. */
		boolean tabla(int i, int w) {
			if (i < 0 || i > largo) {
				return false;
			}
			if (Math.abs(w) <= 1) {
				return true;
			}
			if (i >= inicioPlataforma()) {
				return w < 0 ? -w <= izq : w <= der;
			}
			for (int k = 0; k * 2 < pantalanes.length; k++) {
				int p = pantalan(k);
				int s = pantalanes[k * 2];
				if (i >= p && i <= p + 1 && (s == 0 || Integer.signum(w) == s) && Math.abs(w) <= 1 + pantalanes[k * 2 + 1]) {
					return true;
				}
			}
			return false;
		}

		/** Si (i, w) es de la plataforma de carga (sin la pasarela del medio ni el borde). */
		boolean carga(int i, int w) {
			return i > inicioPlataforma() && i < largo && Math.abs(w) >= 3 && tabla(i, w) && tabla(i, w + Integer.signum(w));
		}
	}

	private void puerto(WorldGenLevel level, BoundingBox caja) {
		Direction d = Direction.from2DDataValue(dir);
		Direction lado = d.getClockWise();
		Forma f = new Forma(largo, semilla);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int m = f.lado() + GRUA_BRAZO;
		// la tablazón, donde hay agua; la altura del agua de este chunk
		int aguaY = Integer.MIN_VALUE;
		boolean[][] puesta = new boolean[largo + 1][2 * m + 1];
		for (int i = 0; i <= largo; i++) {
			for (int w = -m; w <= m; w++) {
				if (!f.tabla(i, w)) {
					continue;
				}
				int cx = x + d.getStepX() * i + lado.getStepX() * w;
				int cz = z + d.getStepZ() * i + lado.getStepZ() * w;
				if (!caja.isInside(cx, caja.minY(), cz)) {
					continue;
				}
				int y = agua(level, pos, cx, cz);
				if (y == Integer.MIN_VALUE) {
					continue;
				}
				aguaY = aguaY == Integer.MIN_VALUE ? y : Math.max(aguaY, y);
				puesta[i][w + m] = true;
			}
		}
		if (aguaY == Integer.MIN_VALUE) {
			return;
		}
		BlockState tabla = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState valla = Blocks.SPRUCE_FENCE.defaultBlockState();
		BlockState poste = Blocks.SPRUCE_LOG.defaultBlockState();
		BlockState farol = Blocks.LANTERN.defaultBlockState();
		int y = aguaY;
		for (int i = 0; i <= largo; i++) {
			for (int w = -m; w <= m; w++) {
				if (!puesta[i][w + m]) {
					continue;
				}
				int cx = x + d.getStepX() * i + lado.getStepX() * w;
				int cz = z + d.getStepZ() * i + lado.getStepZ() * w;
				level.setBlock(pos.set(cx, y, cz), tabla, 2);
				// lo de encima, limpio (algas que asomaban, flores de la orilla)
				for (int k = 1; k <= 3; k++) {
					BlockState b = level.getBlockState(pos.set(cx, y + k, cz));
					if (!b.isAir() && b.canBeReplaced()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					}
				}
				boolean borde = !f.tabla(i + 1, w) && i < largo || !f.tabla(i, w - 1) || !f.tabla(i, w + 1) || i == largo;
				boolean esquina = (!f.tabla(i, w - 1) || !f.tabla(i, w + 1)) && (!f.tabla(i + 1, w) || !f.tabla(i - 1, w));
				// pilotes: en las esquinas y cada tres por los bordes
				if (borde && (esquina || Math.floorMod(i + w, 3) == 0)) {
					pilote(level, pos, cx, y, cz);
				}
				if (!borde || i < 2) {
					continue;
				}
				// el borde: barandilla con farolas, menos en los pantalanes (ahí se amarra) y en el desembarcadero
				// del frente de la plataforma, que queda abierto para las barcas
				boolean pantalan = i < f.inicioPlataforma() && Math.abs(w) > 1;
				boolean desembarco = i == largo && Math.abs(w) <= 2;
				if (pantalan) {
					// en la punta de cada pantalán, un noray y un farol
					if (!f.tabla(i, w + Integer.signum(w))) {
						boolean conFarol = f.tabla(i + 1, w);
						level.setBlock(pos.set(cx, y + 1, cz), conFarol ? poste : valla, 2);
						if (conFarol) {
							level.setBlock(pos.set(cx, y + 2, cz), farol, 2);
						}
					}
					continue;
				}
				if (desembarco) {
					if (Math.abs(w) == 2) {
						level.setBlock(pos.set(cx, y + 1, cz), valla, 2);
					}
					continue;
				}
				// sin cerrar el paso de la pasarela a los pantalanes
				if (Math.abs(w) == 1 && i < f.inicioPlataforma() && f.tabla(i, w * 2)) {
					continue;
				}
				if (Math.floorMod(i + Math.abs(w) * 3, FAROLA) == 0) {
					level.setBlock(pos.set(cx, y + 1, cz), poste, 2);
					level.setBlock(pos.set(cx, y + 2, cz), farol, 2);
				} else {
					level.setBlock(pos.set(cx, y + 1, cz), valla, 2);
				}
			}
		}
		// la mercancía de la plataforma: montones de 2×2 con un pasillo entre ellos, cada montón de una cosa
		BlockState[] cosas = {Blocks.BARREL.defaultBlockState(), Blocks.HAY_BLOCK.defaultBlockState(), Blocks.WHITE_WOOL.defaultBlockState(),
				Blocks.BROWN_WOOL.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState()};
		int p0 = f.inicioPlataforma() + 1;
		for (int i = p0; i < largo; i++) {
			for (int w = -m; w <= m; w++) {
				if (!puesta[i][w + m] || !f.carga(i, w)) {
					continue;
				}
				int ci = (i - p0) / 3;
				int cw = (Math.abs(w) - 3) / 3 * Integer.signum(w);
				if ((i - p0) % 3 == 2 || (Math.abs(w) - 3) % 3 == 2) {
					continue;
				}
				int h = Geo.hash(semilla + ci, cw, 9151);
				if (Math.floorMod(h, 10) < 3 || cercaDeGrua(f, i, w)) {
					continue;
				}
				BlockState cosa = cosas[Math.floorMod(h >> 4, cosas.length)];
				int alto = 1 + Math.floorMod(Geo.hash(i, w, semilla + 77), 2);
				int cx = x + d.getStepX() * i + lado.getStepX() * w;
				int cz = z + d.getStepZ() * i + lado.getStepZ() * w;
				for (int k = 1; k <= alto; k++) {
					level.setBlock(pos.set(cx, y + k, cz), cosa, 2);
				}
			}
		}
		// las grúas, en las esquinas de fuera de la plataforma, con el brazo sobre el agua y la carga colgando
		if (f.gruaIzq()) {
			grua(level, caja, pos, d, lado, -1, f.izq(), y);
		}
		if (f.gruaDer()) {
			grua(level, caja, pos, d, lado, 1, f.der(), y);
		}
		// barcas: en los amarres entre pantalanes y delante del desembarcadero
		for (int k = 0; k * 2 < f.pantalanes().length; k++) {
			int p = f.pantalan(k) + 3;
			for (int s = -1; s <= 1; s += 2) {
				int ss = f.pantalanes()[k * 2];
				if (ss != 0 && ss != s) {
					continue;
				}
				int w = s * (2 + f.pantalanes()[k * 2 + 1] / 2);
				barca(level, caja, pos, x + d.getStepX() * p + lado.getStepX() * w, y, z + d.getStepZ() * p + lado.getStepZ() * w, lado);
			}
		}
		barca(level, caja, pos, x + d.getStepX() * (largo + 2), y, z + d.getStepZ() * (largo + 2), d);
		if (Math.floorMod(semilla, 2) == 0) {
			int w = 1 + f.lado() / 2;
			barca(level, caja, pos, x + d.getStepX() * (largo + 2) + lado.getStepX() * w, y, z + d.getStepZ() * (largo + 2) + lado.getStepZ() * w, d);
		}
	}

	private boolean cercaDeGrua(Forma f, int i, int w) {
		return i >= largo - 2 && (f.gruaIzq() && w <= -f.izq() + 2 || f.gruaDer() && w >= f.der() - 2);
	}

	/** Una grúa de madera en la esquina de fuera del lado s, con el brazo hacia fuera y una carga en la cadena. */
	private void grua(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos, Direction d, Direction lado, int s, int ancho, int y) {
		int i = largo - 1;
		int w0 = s * (ancho - 1);
		BlockState mastil = Blocks.SPRUCE_LOG.defaultBlockState();
		BlockState brazo = Blocks.SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, lado.getAxis());
		int bx = x + d.getStepX() * i + lado.getStepX() * w0;
		int bz = z + d.getStepZ() * i + lado.getStepZ() * w0;
		for (int k = 1; k <= GRUA_ALTO; k++) {
			pon(level, caja, pos, bx, y + k, bz, mastil);
		}
		// riostra: un tronco a cada lado del pie
		pon(level, caja, pos, bx - d.getStepX(), y + 1, bz - d.getStepZ(), Blocks.SPRUCE_FENCE.defaultBlockState());
		for (int k = 1; k <= GRUA_BRAZO + 1; k++) {
			pon(level, caja, pos, bx + lado.getStepX() * s * k, y + GRUA_ALTO, bz + lado.getStepZ() * s * k, brazo);
		}
		// el contrapeso del otro lado y la carga colgando de la cadena en la punta
		pon(level, caja, pos, bx - lado.getStepX() * s, y + GRUA_ALTO, bz - lado.getStepZ() * s, brazo);
		pon(level, caja, pos, bx - lado.getStepX() * s, y + GRUA_ALTO - 1, bz - lado.getStepZ() * s, Blocks.BARREL.defaultBlockState());
		int px = bx + lado.getStepX() * s * (GRUA_BRAZO + 1);
		int pz = bz + lado.getStepZ() * s * (GRUA_BRAZO + 1);
		BlockState cadena = cadena();
		for (int k = GRUA_ALTO - 1; k >= GRUA_ALTO - 3; k--) {
			pon(level, caja, pos, px, y + k, pz, cadena);
		}
		BlockState carga = Math.floorMod(semilla + s, 2) == 0 ? Blocks.BARREL.defaultBlockState() : Blocks.HAY_BLOCK.defaultBlockState();
		pon(level, caja, pos, px, y + GRUA_ALTO - 4, pz, carga);
	}

	private static void pon(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState b) {
		if (caja.isInside(x, caja.minY(), z)) {
			level.setBlock(pos.set(x, y, z), b, 2);
		}
	}

	/** La cadena de hierro (se llama iron_chain desde que hay cadenas de cobre); si no está, una valla. */
	private static BlockState cadena() {
		Block b = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("iron_chain"));
		if (b == null || b == Blocks.AIR) {
			b = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("chain"));
		}
		return b == null || b == Blocks.AIR ? Blocks.SPRUCE_FENCE.defaultBlockState() : b.defaultBlockState();
	}
}
