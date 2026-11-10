package es.hulillo.destinos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Un tramo de una calle de la capital: el trozo recto entre dos puntos de su curva, pintado como con una brocha
 * redonda de radio bloques alrededor de la línea (así las diagonales tienen el mismo ancho que las rectas y los
 * tramos se empalman sin huecos en las curvas). La altura va de y0 a y1 a lo largo del tramo, ya suavizada a lo largo
 * de toda la calle: donde el suelo queda un poco por encima se rebaja y donde queda un poco por debajo se rellena,
 * con un escalón de cuneta a los lados; si la diferencia es grande, la calle sigue el suelo. Solo se toca el suelo
 * natural, nunca las casas. Sobre el agua cruza por un puente de piedra medio bloque por encima, con pretil a los
 * lados y pilares hasta el fondo; en el pantano, por una pasarela de mangle sobre pilotes.
 *
 * No cuenta para el allanado del terreno (que con cajas finas a la altura de cada tramo dejaba la calle en
 * terrazas rectangulares): el allanado lo hace ella misma, columna a columna.
 */
public final class CalleCapital extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) CalleCapital::new;
	/** Cada cuántos bloques el puente lleva pilares hasta el fondo. */
	private static final int PILARES = 5;
	/** Lo hondo que puede ser un hueco para cruzarlo con un puente (más, es un barranco: ahí no hay calle). */
	private static final int HONDO_PUENTE = 24;
	/** Lo que se rebaja o se rellena como mucho para que la calle vaya suave; con más, sigue el suelo. */
	private static final int MAX_CORTE = 5;
	private static final int MAX_RELLENO = 4;
	/** Lo ancho del talud a cada lado de la calle, en bloques. */
	private static final double CUNETA = 3.5;
	/** La cuesta (bloques que sube por bloque que avanza) desde la que la calle lleva escalones. */
	private static final double ESCALINATA = 0.3;
	/** Lo que se rellena como mucho en el borde de una calle que va por tierra junto a una bajada. */
	private static final int BORDE_HONDO = 5;
	/** Sin altura guardada (piezas de antes): la calle sigue el suelo. */
	private static final int SIN_ALTURA = Integer.MIN_VALUE;

	private final int x0;
	private final int z0;
	private final int y0;
	private final int x1;
	private final int z1;
	private final int y1;
	/** Medio ancho de la calle, en décimas de bloque. */
	private final int radio;
	private final boolean puente;
	/** Lo que se puede rebajar o rellenar como mucho (0: lo de siempre); las rampas, como la subida al castillo, más. */
	private final int maximo;
	/** En el pantano: sobre el agua, pasarela de mangle sobre pilotes (también en rondas y callejones). */
	private boolean madera;
	/** Si el terreno se amolda a este tramo antes de generarse (por tierra; no sobre el agua ni sobre una zanja que cruza por un puente). */
	private boolean molde;

	CalleCapital(int x0, int z0, int y0, int x1, int z1, int y1, double radio, boolean puente) {
		this(x0, z0, y0, x1, z1, y1, radio, puente, 0);
	}

	CalleCapital(int x0, int z0, int y0, int x1, int z1, int y1, double radio, boolean puente, int maximo) {
		super(TIPO, 0, caja(x0, z0, y0, x1, z1, y1, radio, maximo));
		this.maximo = maximo;
		this.x0 = x0;
		this.z0 = z0;
		this.y0 = y0;
		this.x1 = x1;
		this.z1 = z1;
		this.y1 = y1;
		this.radio = (int) Math.round(radio * 10);
		this.puente = puente;
	}

	int x0() {
		return x0;
	}

	/** El trozo para el plano de la muralla: x0, z0, y0, x1, z1, y1 y el radio en décimas. */
	int[] trozo() {
		return new int[] {x0, z0, y0, x1, z1, y1, radio};
	}

	/** Si es una calle menor (ronda, callejón o acceso): más estrecha que las principales. */
	boolean menor() {
		return radio < 20;
	}

	int z0() {
		return z0;
	}

	int x1() {
		return x1;
	}

	int z1() {
		return z1;
	}

	private static BoundingBox caja(int x0, int z0, int y0, int x1, int z1, int y1, double radio, int maximo) {
		int m = (int) Math.ceil(radio) + 1 + (int) Math.ceil(CUNETA);
		int h = Math.max(MAX_CORTE, maximo);
		return new BoundingBox(Math.min(x0, x1) - m, Math.min(y0, y1) - h - 1, Math.min(z0, z1) - m, Math.max(x0, x1) + m,
				Math.max(y0, y1) + h + 2, Math.max(z0, z1) + m);
	}

	private CalleCapital(CompoundTag t) {
		super(TIPO, t);
		x0 = t.getIntOr("x0", 0);
		z0 = t.getIntOr("z0", 0);
		x1 = t.getIntOr("x1", 0);
		z1 = t.getIntOr("z1", 0);
		y0 = t.getIntOr("y0", SIN_ALTURA);
		y1 = t.getIntOr("y1", SIN_ALTURA);
		radio = t.getIntOr("radio", 10);
		puente = t.getBooleanOr("puente", false);
		maximo = t.getIntOr("max", 0);
		madera = t.getBooleanOr("madera", false);
		molde = t.getBooleanOr("molde", false);
	}

	/** La vuelve pasarela de madera sobre el agua (las capitales del pantano). */
	CalleCapital deMadera() {
		madera = true;
		return this;
	}

	/** El terreno se amoldará a este tramo (lo dice el plano: va por tierra). */
	CalleCapital conMolde() {
		molde = true;
		return this;
	}

	/**
	 * Los moldes del tramo cerca del chunk: cuadraditos del ancho de la calle, uno detrás de otro sin pisarse, cada uno
	 * a la altura de la calle en su centro. El relleno de Minecraft rebaja lo que queda por encima y rellena lo de
	 * debajo, como bajo las casas, y la calle sale ya con su rampa hecha: al ponerse solo pinta el suelo. (Con una caja
	 * por tramo entero, a una sola altura, quedaban terrazas rectangulares.)
	 */
	void moldes(ChunkPos chunk, int margen, java.util.List<StructurePiece> r) {
		if (!molde || y0 == SIN_ALTURA) {
			return;
		}
		int medio = Math.max(1, (int) Math.round(radio / 10.0));
		double largo = Math.hypot(x1 - x0, z1 - z0);
		int n = Math.max(1, (int) Math.round(largo / (2 * medio + 1)));
		for (int k = 0; k < n; k++) {
			double t = (k + 0.5) / n;
			int x = (int) Math.round(x0 + t * (x1 - x0));
			int z = (int) Math.round(z0 + t * (z1 - z0));
			if (x + medio < chunk.getMinBlockX() - margen || x - medio > chunk.getMaxBlockX() + margen || z + medio < chunk.getMinBlockZ() - margen
					|| z - medio > chunk.getMaxBlockZ() + margen) {
				continue;
			}
			// el suelo de la calle queda en y: el aire empieza en y + 1
			int y = (int) Math.floor(y0 + t * (y1 - y0)) + 1;
			r.add(new Molde(new BoundingBox(x - medio, y, z - medio, x + medio, y + 1, z + medio)));
		}
	}

	/** Deja en las terrazas el suelo de esta calle, columna a columna; sobre un puente, sin tocar. */
	void encaja(TerrazaCapital terrazas) {
		BoundingBox mia = getBoundingBox();
		double r = radio / 10.0;
		double dx = x1 - x0;
		double dz = z1 - z0;
		double l2 = dx * dx + dz * dz;
		for (int x = mia.minX(); x <= mia.maxX(); x++) {
			for (int z = mia.minZ(); z <= mia.maxZ(); z++) {
				double t = l2 == 0 ? 0 : Math.clamp(((x - x0) * dx + (z - z0) * dz) / l2, 0, 1);
				if (Math.hypot(x0 + t * dx - x, z0 + t * dz - z) > r + 0.3) {
					continue;
				}
				if (!molde || y0 == SIN_ALTURA) {
					terrazas.suelta(x, z);
				} else {
					terrazas.fija(x, z, (int) Math.floor(y0 + t * (y1 - y0)));
				}
			}
		}
	}

	/** Una caja sin bloques, solo para que el terreno se amolde a ella. No se guarda ni se pone. */
	private static final class Molde extends StructurePiece {
		Molde(BoundingBox caja) {
			super(TIPO, 0, caja);
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar, BoundingBox caja,
				ChunkPos chunk, BlockPos pivote) {
		}
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "calle"), TIPO);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		t.putInt("x0", x0);
		t.putInt("z0", z0);
		t.putInt("x1", x1);
		t.putInt("z1", z1);
		t.putInt("y0", y0);
		t.putInt("y1", y1);
		t.putInt("radio", radio);
		t.putBoolean("puente", puente);
		t.putInt("max", maximo);
		t.putBoolean("madera", madera);
		t.putBoolean("molde", molde);
	}

	@Override
	public boolean isCloseToChunk(ChunkPos chunk, int distancia) {
		return y0 == SIN_ALTURA && super.isCloseToChunk(chunk, distancia);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar,
			BoundingBox caja, ChunkPos chunk, BlockPos pivote) {
		BoundingBox mia = getBoundingBox();
		int ax = Math.max(mia.minX(), caja.minX());
		int bx = Math.min(mia.maxX(), caja.maxX());
		int az = Math.max(mia.minZ(), caja.minZ());
		int bz = Math.min(mia.maxZ(), caja.maxZ());
		double r = radio / 10.0;
		double dx = x1 - x0;
		double dz = z1 - z0;
		double l2 = dx * dx + dz * dz;
		// el pretil, unido a lo largo si el tramo va casi recto por un eje; en diagonal, postes sueltos
		// lo que sube la calle por cada bloque que avanza
		double pendiente = l2 == 0 || y0 == SIN_ALTURA ? 0 : Math.abs(y1 - y0) / Math.sqrt(l2);
		Boolean aLoLargoDeZ = Math.abs(dz) >= 2 * Math.abs(dx) ? Boolean.TRUE : Math.abs(dx) >= 2 * Math.abs(dz) ? Boolean.FALSE : null;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = ax; x <= bx; x++) {
			for (int z = az; z <= bz; z++) {
				double t = l2 == 0 ? 0 : Math.clamp(((x - x0) * dx + (z - z0) * dz) / l2, 0, 1);
				double d = Math.hypot(x0 + t * dx - x, z0 + t * dz - z);
				if (d > r + CUNETA) {
					continue;
				}
				boolean calle = d <= r + 0.3;
				if (d <= r + 1.5) {
					SueloNatural.tala(level, caja, x, z);
				}
				double yf = y0 == SIN_ALTURA ? 0 : y0 + t * (y1 - y0);
				int y = y0 == SIN_ALTURA ? SIN_ALTURA : (int) Math.floor(yf);
				// en cuesta, justo antes de cada subida de un bloque va una fila de escalones (solo una: la de la
				// columna cuya vecina cuesta arriba ya está un bloque más alta)
				// y solo en tramos rectos por un eje, con cuesta de verdad y lejos de sus puntas (en cruces y curvas
				// se mezclaban escalones de calles distintas, mirando cada uno a un lado)
				boolean escalon = y0 != SIN_ALTURA && aLoLargoDeZ != null && pendiente >= ESCALINATA && t > 0.15 && t < 0.85
						&& Math.floor(yf + pendiente) > y;
				BlockState s = SueloNatural.superficie(level, pos, x, z);
				if (s.is(Blocks.WATER)) {
					if ((puente || madera) && d <= r + 1.3) {
						puente(level, pos, !calle, Math.floorMod(x + z, madera ? 3 : PILARES) == 0, aLoLargoDeZ, madera);
					}
				} else if (y != SIN_ALTURA && pos.getY() < y - relleno() && y - pos.getY() <= HONDO_PUENTE && d <= r + 1.3
						&& hueco(level, (int) Math.round(x0 + t * dx), (int) Math.round(z0 + t * dz), y)) {
					// una zanja o un cauce (tapado en el perfil, o que se abrió después): la calle lo cruza por un puente, ya
					// desde donde no se puede rellenar (antes, entre relleno y corte quedaba un hoyo sin calle)
					puenteSeco(level, pos, x, y, z, !calle, Math.floorMod(x + z, PILARES) == 0, aLoLargoDeZ, madera);
				} else if (y != SIN_ALTURA && calle && pos.getY() < y && y - pos.getY() <= BORDE_HONDO) {
					// el borde de la calle sobre una bajada (el centro va por tierra): se rellena, sin puente ni pretil
					s = allana(level, pos, s, y, 0, corte(), BORDE_HONDO);
					pon(level, pos, s);
				} else if (y != SIN_ALTURA && Math.abs(pos.getY() - y) > corte() + 1) {
					// un acantilado o un barranco que el trazado no vio: ahí no se pinta calle
					continue;
				} else if (calle) {
					s = allana(level, pos, s, y, 0, corte(), relleno());
					pon(level, pos, s);
					if (escalon && pos.getY() == y) {
						escalon(level, pos, s, dx, dz, y1 > y0);
					}
				} else if (y != SIN_ALTURA && SueloNatural.natural(s)) {
					// la cuneta (solo sobre suelo natural: nunca tapa otra calle ni una obra): un talud entre la calle y el suelo de al lado (un bloque más por cada uno que se aleja),
					// para que no quede un tajo de tierra
					allana(level, pos, s, y, Math.max(1, (int) Math.ceil(d - r - 0.3)), corte(), relleno());
				}
			}
		}
	}

	/**
	 * Lleva el suelo de pos (cuyo bloque de arriba es s) a la altura y (más o menos holgura): rebaja lo natural que
	 * sobra o rellena con tierra lo que falta, si es poco; si no, o si hay algo que no es natural, lo deja. Devuelve el
	 * bloque de arriba, con pos en él.
	 */
	private int corte() {
		return Math.max(MAX_CORTE, maximo);
	}

	private int relleno() {
		return Math.max(MAX_RELLENO, maximo);
	}

	private static BlockState allana(WorldGenLevel level, BlockPos.MutableBlockPos pos, BlockState s, int y, int holgura, int maxCorte,
			int maxRelleno) {
		if (y == SIN_ALTURA) {
			return s;
		}
		int h = pos.getY();
		if (h > y + holgura && h - y <= maxCorte) {
			int x = pos.getX();
			int z = pos.getZ();
			for (int k = y + holgura + 1; k <= h; k++) {
				pos.set(x, k, z);
				BlockState b = level.getBlockState(pos);
				if (!SueloNatural.natural(b) && !b.canBeReplaced() && !b.is(BlockTags.LEAVES) && !b.is(BlockTags.LOGS)) {
					pos.set(x, h, z);
					return s;
				}
			}
			// y lo de encima de lo que se quita (las plantas ya no están; los troncos de un árbol sí se quedan)
			for (int k = h; k > y + holgura; k--) {
				pos.set(x, k, z);
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
			pos.set(x, y + holgura, z);
			BlockState abajo = level.getBlockState(pos);
			// lo que queda arriba es lo de debajo de la hierba: vuelve a ser hierba en la cuneta
			if (holgura > 0 && abajo.is(Blocks.DIRT) && s.is(Blocks.GRASS_BLOCK)) {
				level.setBlock(pos, s, 2);
				return s;
			}
			return abajo;
		}
		if (h < y - holgura && y - h <= maxRelleno) {
			int x = pos.getX();
			int z = pos.getZ();
			for (int k = h + 1; k <= y - holgura; k++) {
				pos.set(x, k, z);
				if (!level.getBlockState(pos).canBeReplaced()) {
					pos.set(x, k - 1, z);
					return level.getBlockState(pos);
				}
			}
			BlockState relleno = SueloNatural.natural(s) ? s : Blocks.DIRT.defaultBlockState();
			for (int k = h; k <= y - holgura; k++) {
				pos.set(x, k, z);
				level.setBlock(pos, k == y - holgura ? relleno : Blocks.DIRT.defaultBlockState(), 2);
			}
			return relleno;
		}
		return s;
	}

	/**
	 * Una casilla del puente sobre el agua de pos: losa medio bloque por encima (se sube desde la orilla sin saltar),
	 * pretil unido a lo largo (aLoLargoDeZ) y, a trechos, el pilar hasta el fondo.
	 */
	/**
	 * Un tramo de puente sobre un hueco seco, a la altura y de la calle: tablero de piedra (o de madera), pretil a los
	 * lados y, cada tanto, pilares hasta el fondo. El pretil no pisa el tablero de otro tramo.
	 */
	/**
	 * Un escalón sobre la calle en pos (su suelo, de bloque s), con el lado alto hacia donde sube: de piedra, o de
	 * arenisca en la arena. Así las cuestas son una escalinata y no pegotes de un bloque.
	 */
	private static void escalon(WorldGenLevel level, BlockPos.MutableBlockPos pos, BlockState s, double dx, double dz, boolean sube) {
		pos.move(0, 1, 0);
		if (!level.getBlockState(pos).canBeReplaced()) {
			return;
		}
		Block b = s.is(Blocks.SMOOTH_SANDSTONE) || s.is(Blocks.SAND) || s.is(Blocks.SANDSTONE) ? Blocks.SMOOTH_SANDSTONE_STAIRS
				: s.is(Blocks.DIRT_PATH) ? Blocks.MOSSY_COBBLESTONE_STAIRS : Blocks.COBBLESTONE_STAIRS;
		net.minecraft.core.Direction arriba = Math.abs(dx) >= Math.abs(dz)
				? (dx > 0 == sube ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST)
				: (dz > 0 == sube ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH);
		level.setBlock(pos, b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, arriba), 2);
	}

	/**
	 * Si por el centro de la calle en (x, z) hay un hueco que cruzar a la altura y (o ya está el tablero del puente):
	 * solo entonces el borde lleva puente y pretil; si no, era una bajada a un lado y quedaban pretiles por el suelo.
	 */
	private boolean hueco(WorldGenLevel level, int x, int z, int y) {
		int g = SueloNatural.cima(level, x, z);
		if (g >= y - relleno()) {
			BlockState b = level.getBlockState(new BlockPos(x, g, z));
			return g == y && (b.is(Blocks.STONE_BRICKS) || b.is(Blocks.SPRUCE_PLANKS));
		}
		return y - g <= HONDO_PUENTE;
	}

	private static void puenteSeco(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, boolean pretil, boolean pilar,
			Boolean aLoLargoDeZ, boolean madera) {
		Block tablero = madera ? Blocks.SPRUCE_PLANKS : Blocks.STONE_BRICKS;
		Block baranda = madera ? Blocks.SPRUCE_FENCE : Blocks.STONE_BRICK_WALL;
		BlockState actual = level.getBlockState(pos.set(x, y, z));
		if (!pretil) {
			level.setBlock(pos, tablero.defaultBlockState(), 2);
			pos.set(x, y + 1, z);
			if (level.getBlockState(pos).is(baranda)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		} else {
			if (!actual.isAir() && !actual.canBeReplaced()) {
				return;
			}
			level.setBlock(pos, tablero.defaultBlockState(), 2);
			BlockState muro = baranda.defaultBlockState();
			if (aLoLargoDeZ != null) {
				if (madera) {
					muro = aLoLargoDeZ ? muro.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true)
							: muro.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true);
				} else {
					muro = aLoLargoDeZ ? muro.setValue(BlockStateProperties.NORTH_WALL, WallSide.LOW).setValue(BlockStateProperties.SOUTH_WALL, WallSide.LOW)
							: muro.setValue(BlockStateProperties.EAST_WALL, WallSide.LOW).setValue(BlockStateProperties.WEST_WALL, WallSide.LOW);
				}
			}
			level.setBlock(pos.set(x, y + 1, z), muro, 2);
		}
		if (!pilar) {
			return;
		}
		BlockState relleno = (madera ? Blocks.SPRUCE_LOG : Blocks.STONE_BRICKS).defaultBlockState();
		for (int k = y - 1; k > y - HONDO_PUENTE - 2 && k > level.getMinY(); k--) {
			BlockState b = level.getBlockState(pos.set(x, k, z));
			if (!b.isAir() && !b.canBeReplaced() && !b.is(Blocks.WATER)) {
				break;
			}
			level.setBlock(pos, relleno, 2);
		}
	}

	private static void puente(WorldGenLevel level, BlockPos.MutableBlockPos pos, boolean pretil, boolean pilar, Boolean aLoLargoDeZ,
			boolean madera) {
		Block suelo = madera ? Blocks.MANGROVE_SLAB : Blocks.STONE_BRICK_SLAB;
		Block baranda = madera ? Blocks.MANGROVE_FENCE : Blocks.STONE_BRICK_WALL;
		int agua = pos.getY();
		pos.setY(agua + 1);
		BlockState losa = level.getBlockState(pos);
		if (!pretil) {
			level.setBlock(pos, suelo.defaultBlockState(), 2);
			// si el pretil de otro tramo cayó aquí (en una curva), fuera: esto es calle
			pos.setY(agua + 2);
			if (level.getBlockState(pos).is(baranda)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
			return;
		}
		// el pretil no se pone encima de la calle de otro tramo
		if (losa.is(suelo)) {
			return;
		}
		level.setBlock(pos, suelo.defaultBlockState(), 2);
		pos.setY(agua + 2);
		BlockState muro = baranda.defaultBlockState();
		if (aLoLargoDeZ != null) {
			if (madera) {
				muro = aLoLargoDeZ ? muro.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true)
						: muro.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true);
			} else {
				muro = aLoLargoDeZ ? muro.setValue(BlockStateProperties.NORTH_WALL, WallSide.LOW).setValue(BlockStateProperties.SOUTH_WALL, WallSide.LOW)
						: muro.setValue(BlockStateProperties.EAST_WALL, WallSide.LOW).setValue(BlockStateProperties.WEST_WALL, WallSide.LOW);
			}
		}
		level.setBlock(pos, muro, 2);
		if (pilar) {
			for (int y = agua; y > level.getMinY(); y--) {
				pos.setY(y);
				BlockState b = level.getBlockState(pos);
				if (y != agua && !b.is(Blocks.WATER) && !b.canBeReplaced()) {
					break;
				}
				level.setBlock(pos, (madera ? Blocks.MANGROVE_LOG : Blocks.STONE_BRICKS).defaultBlockState(), 2);
			}
		}
	}

	private static void pon(WorldGenLevel level, BlockPos.MutableBlockPos pos, BlockState s) {
		BlockState nuevo = null;
		if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL)
				|| s.is(Blocks.MYCELIUM) || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.SNOW_BLOCK)) {
			nuevo = Blocks.DIRT_PATH.defaultBlockState();
		} else if (s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)) {
			nuevo = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
		} else if (s.is(Blocks.GRAVEL) || s.is(Blocks.MUD) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.TERRACOTTA)
				|| s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.CALCITE)) {
			nuevo = Blocks.COBBLESTONE.defaultBlockState();
		}
		// el resto (casas, caminos ya puestos, hielo…) se deja como está
		if (nuevo != null) {
			level.setBlock(pos, nuevo, 2);
		}
	}
}
