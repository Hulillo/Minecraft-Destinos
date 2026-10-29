package es.hulillo.destinos;

import java.util.List;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Un trozo de la muralla de una capital (un arco del anillo o una torre), según lo rico que es el reino:
 * 0 empalizada de troncos, 1 muralla de piedra con almenas, 2 muralla de ladrillo de piedra más gruesa y alta,
 * con torres. Sigue el suelo columna a columna y deja hueco donde hay calles o casas (las puertas salen solas
 * donde la cruza una calle) y donde hay agua. En el desierto, de arenisca.
 *
 * El anillo no es un círculo: cada arco va de un radio a otro (el contorno de lo construido, con un margen), así la
 * muralla se ciñe a la ciudad y se abomba donde hay más casas.
 */
public final class MurallaCapital extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) MurallaCapital::new;
	/**
	 * Radio interior del anillo según el nivel: rodea el centro de la capital, más ancho cuanto más rica (la rica
	 * necesita sitio para el castillo, el mercado y las casas nobles).
	 */
	private static final int[] RADIOS = {76, 88, 108};
	static final int ARCOS = 64;

	static int radio(int nivel) {
		return RADIOS[Math.clamp(nivel, 0, RADIOS.length - 1)];
	}
	static final int TORRES = 8;
	private static final int RADIO_TORRE = 3;
	private static final int[] ALTO = {4, 5, 7};
	private static final int[] GRUESO = {1, 2, 3};

	static int alto(int nivel) {
		return ALTO[Math.clamp(nivel, 0, ALTO.length - 1)];
	}

	static int grueso(int nivel) {
		return GRUESO[Math.clamp(nivel, 0, GRUESO.length - 1)];
	}
	/** El alto libre bajo el dintel de las puertas. */
	private static final int PASO = 4;

	private final int cx;
	private final int cz;
	private final int nivel;
	/** Arco: ángulos en décimas de grado [a0, a1). Torre: a1 < 0 y a0 es su ángulo. */
	private final int a0;
	private final int a1;
	/** El radio interior del arco en a0 y en a1 (entre medias, en línea). */
	private final int r0;
	private final int r1;
	/** El castillo, si lo hay: centro y medio lado de su muralla (ahí la de la ciudad se para y empalma con ella). */
	private final int kx;
	private final int kz;
	private final int km;
	/** Cajas (x0, z0, x1, z1 seguidos) donde no se pone muralla: delante y detrás de las puertas. */
	private final int[] huecos;

	private MurallaCapital(int cx, int cz, int nivel, int a0, int a1, int r0, int r1, int[] castillo, int[] huecos, BoundingBox caja) {
		super(TIPO, 0, caja);
		this.cx = cx;
		this.cz = cz;
		this.nivel = nivel;
		this.a0 = a0;
		this.a1 = a1;
		this.r0 = r0;
		this.r1 = r1;
		this.kx = castillo == null ? 0 : castillo[0];
		this.kz = castillo == null ? 0 : castillo[1];
		this.km = castillo == null ? -1 : castillo[2];
		this.huecos = huecos;
	}

	private MurallaCapital(CompoundTag t) {
		super(TIPO, t);
		cx = t.getIntOr("cx", 0);
		cz = t.getIntOr("cz", 0);
		nivel = t.getIntOr("nivel", 0);
		a0 = t.getIntOr("a0", 0);
		a1 = t.getIntOr("a1", 0);
		r0 = t.getIntOr("r0", radio(nivel));
		r1 = t.getIntOr("r1", radio(nivel));
		kx = t.getIntOr("kx", 0);
		kz = t.getIntOr("kz", 0);
		km = t.getIntOr("km", -1);
		huecos = t.getIntArray("huecos").orElse(new int[0]);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "muralla"), TIPO);
	}

	/**
	 * El radio interior de la muralla en cada uno de los ARCOS: lo justo para dejar dentro las cajas dadas (con un
	 * margen), nunca menos de minimo ni más de maximo, y suavizado para que no haga picos.
	 */
	static int[] contorno(int cx, int cz, List<BoundingBox> cajas, int margen, int minimo, int maximo) {
		double[] r = new double[ARCOS];
		java.util.Arrays.fill(r, minimo);
		for (BoundingBox b : cajas) {
			for (int x = b.minX(); x <= b.maxX() + 3; x += 4) {
				for (int z = b.minZ(); z <= b.maxZ() + 3; z += 4) {
					int px = Math.min(x, b.maxX());
					int pz = Math.min(z, b.maxZ());
					double d = Math.hypot(px - cx, pz - cz) + margen;
					int i = Math.floorMod((int) Math.floor(Math.toDegrees(Math.atan2(pz - cz, px - cx)) / 360 * ARCOS), ARCOS);
					r[i] = Math.max(r[i], d);
				}
			}
		}
		// un arco no puede quedar muy por dentro de sus vecinos (si no, la muralla se mete en pico entre dos barrios)
		double[] crudo = r.clone();
		for (int i = 0; i < ARCOS; i++) {
			r[i] = Math.max(crudo[i], Math.max(crudo[(i + 1) % ARCOS], crudo[(i + ARCOS - 1) % ARCOS]) - 4);
		}
		// y suave: media con los vecinos, sin bajar nunca de lo que hace falta para dejar las casas dentro
		for (int pasada = 0; pasada < 3; pasada++) {
			double[] g = r.clone();
			for (int i = 0; i < ARCOS; i++) {
				g[i] = Math.max(crudo[i], (r[(i + ARCOS - 1) % ARCOS] + 2 * r[i] + r[(i + 1) % ARCOS]) / 4);
			}
			r = g;
		}
		int[] radios = new int[ARCOS];
		for (int i = 0; i < ARCOS; i++) {
			radios[i] = (int) Math.round(Math.clamp(r[i], minimo, maximo));
		}
		return radios;
	}

	/** El radio interior de la muralla en el ángulo a (décimas de grado), entre los de los arcos. */
	static double radioEn(int[] radios, double a) {
		double f = Math.floorMod((long) Math.floor(a * 1000), 3600_000L) / 1000.0 / 3600 * ARCOS;
		int i = (int) Math.floor(f);
		double t = f - i;
		return radios[i % ARCOS] * (1 - t) + radios[(i + 1) % ARCOS] * t;
	}

	/**
	 * Las piezas de la muralla alrededor de (cx, cz) con los radios del contorno (null: el círculo de su nivel);
	 * alturaEn da la altura del suelo para cada caja fina. Dentro de la muralla del castillo (centro y medio lado en
	 * castillo, o null) no se pone nada: la de la ciudad llega hasta la suya y ahí empalma.
	 */
	static void piezas(int cx, int cz, int nivel, int[] radios, IntBinaryOperator alturaEn, int[] castillo, int[] huecos, List<StructurePiece> piezas) {
		int grueso = GRUESO[nivel];
		if (radios == null) {
			radios = new int[ARCOS];
			java.util.Arrays.fill(radios, radio(nivel));
		}
		// las torres antes que los arcos: así el arco ve la torre ya puesta y no la atraviesa
		if (nivel >= 1) {
			// torres entre las diagonales, por donde llegan las calles de los barrios
			int torres = nivel == 2 ? TORRES : TORRES / 2;
			for (int i = 0; i < torres; i++) {
				int a = 225 + i * 3600 / torres;
				double r = Math.toRadians(a / 10.0);
				double rt = radioEn(radios, a) + grueso / 2.0;
				int tx = cx + (int) Math.round(Math.cos(r) * rt);
				int tz = cz + (int) Math.round(Math.sin(r) * rt);
				if (enCastillo(castillo, tx, tz, RADIO_TORRE + 2) || enHueco(huecos, tx, tz, RADIO_TORRE + 4)) {
					continue;
				}
				int y = alturaEn.applyAsInt(tx, tz);
				piezas.add(new MurallaCapital(cx, cz, nivel, a, -1, 0, 0, castillo, huecos, new BoundingBox(tx - RADIO_TORRE - 1, y, tz - RADIO_TORRE - 1,
						tx + RADIO_TORRE + 1, y + 1, tz + RADIO_TORRE + 1)));
			}
		}
		for (int i = 0; i < ARCOS; i++) {
			int a0 = i * 3600 / ARCOS;
			int a1 = (i + 1) * 3600 / ARCOS;
			int r0 = radios[i];
			int r1 = radios[(i + 1) % ARCOS];
			int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
			boolean tapa = true;
			for (int k = 0; k <= 4; k++) {
				double a = Math.toRadians((a0 + (a1 - a0) * k / 4.0) / 10.0);
				double rk = r0 + (r1 - r0) * k / 4.0;
				tapa &= enCastillo(castillo, cx + (int) Math.round(Math.cos(a) * (rk + grueso / 2.0)), cz + (int) Math.round(Math.sin(a) * (rk + grueso / 2.0)), -2);
				for (double r : new double[] {rk - 1, rk + grueso + 1}) {
					int x = cx + (int) Math.floor(Math.cos(a) * r);
					int z = cz + (int) Math.floor(Math.sin(a) * r);
					minX = Math.min(minX, x);
					minZ = Math.min(minZ, z);
					maxX = Math.max(maxX, x);
					maxZ = Math.max(maxZ, z);
				}
			}
			// solo se quitan los arcos que caen enteros dentro del castillo; los demás se paran columna a columna en su muralla
			if (tapa) {
				continue;
			}
			int y = alturaEn.applyAsInt((minX + maxX) / 2, (minZ + maxZ) / 2);
			piezas.add(new MurallaCapital(cx, cz, nivel, a0, a1, r0, r1, castillo, huecos, new BoundingBox(minX - 1, y, minZ - 1, maxX + 1, y + 1, maxZ + 1)));
		}
	}

	private static boolean enHueco(int[] huecos, int x, int z, int margen) {
		for (int k = 0; k + 3 < huecos.length; k += 4) {
			if (x >= huecos[k] - margen && x <= huecos[k + 2] + margen && z >= huecos[k + 1] - margen && z <= huecos[k + 3] + margen) {
				return true;
			}
		}
		return false;
	}

	private static boolean enCastillo(int[] castillo, int x, int z, int margen) {
		return castillo != null && Math.max(Math.abs(x - castillo[0]), Math.abs(z - castillo[1])) <= castillo[2] + margen;
	}

	/** No cuenta para el allanado: va columna a columna sobre el suelo (y sobre el agua, sin echar tierra en ella). */
	@Override
	public boolean isCloseToChunk(ChunkPos chunk, int distancia) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		t.putInt("cx", cx);
		t.putInt("cz", cz);
		t.putInt("nivel", nivel);
		t.putInt("a0", a0);
		t.putInt("a1", a1);
		t.putInt("r0", r0);
		t.putInt("r1", r1);
		t.putInt("kx", kx);
		t.putInt("kz", kz);
		t.putInt("km", km);
		t.putIntArray("huecos", huecos);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar,
			BoundingBox caja, ChunkPos chunk, BlockPos pivote) {
		BoundingBox mia = getBoundingBox();
		int grueso = GRUESO[nivel];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		if (a1 < 0) {
			torre(level, caja, pos);
			return;
		}
		for (int x = Math.max(mia.minX(), caja.minX()); x <= Math.min(mia.maxX(), caja.maxX()); x++) {
			for (int z = Math.max(mia.minZ(), caja.minZ()); z <= Math.min(mia.maxZ(), caja.maxZ()); z++) {
				if (km >= 0 && Math.max(Math.abs(x - kx), Math.abs(z - kz)) <= km || enHueco(huecos, x, z, 0)) {
					continue;
				}
				double d = Math.hypot(x - cx, z - cz);
				int a = (int) Math.floor(Math.toDegrees(Math.atan2(z - cz, x - cx)) * 10);
				a = Math.floorMod(a, 3600);
				if (a < a0 || a >= a1) {
					continue;
				}
				// el radio en este ángulo, y el grueso medido de través (si el arco va en diagonal, más hondo)
				double ri = r0 + (r1 - r0) * (a - a0) / (double) (a1 - a0);
				double inclinado = Math.hypot(1, (r1 - r0) / (Math.toRadians((a1 - a0) / 10.0) * Math.max(1, ri)));
				double g = grueso * inclinado;
				if (d < ri || d >= ri + g) {
					continue;
				}
				// empalizada en punta (un tronco sí y otro no, más alto); almenas en el borde de fuera (por dentro queda
				// el adarve, el paso de ronda por lo alto de la muralla)
				boolean almena = nivel == 0 ? (x + z) % 2 == 0 : d >= ri + g - 1 && (x + z) % 2 == 0;
				columna(level, pos, x, z, ALTO[nivel] + (almena ? 1 : 0));
			}
		}
	}

	/**
	 * Una torre de la muralla, hueca: puerta a ras de suelo hacia la ciudad, escala de mano por dentro hasta la azotea
	 * almenada, un piso a la altura del adarve con salida a la muralla por los dos lados y faroles dentro.
	 */
	private void torre(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos) {
		BoundingBox mia = getBoundingBox();
		int tx = (mia.minX() + mia.maxX()) / 2;
		int tz = (mia.minZ() + mia.maxZ()) / 2;
		int y0 = mia.minY();
		int adarve = y0 + ALTO[nivel];
		int azotea = y0 + ALTO[nivel] + 4;
		// hacia la ciudad (la puerta) y a lo largo de la muralla (las salidas al adarve)
		net.minecraft.core.Direction dentro = Math.abs(cx - tx) >= Math.abs(cz - tz)
				? (cx > tx ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST)
				: (cz > tz ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH);
		net.minecraft.core.Direction lado = dentro.getClockWise();
		int r = RADIO_TORRE;
		BlockState suelo = Blocks.SPRUCE_PLANKS.defaultBlockState();
		for (int x = Math.max(mia.minX(), caja.minX()); x <= Math.min(mia.maxX(), caja.maxX()); x++) {
			for (int z = Math.max(mia.minZ(), caja.minZ()); z <= Math.min(mia.maxZ(), caja.maxZ()); z++) {
				int ox = x - tx;
				int oz = z - tz;
				double d = Math.hypot(ox, oz);
				if (d > r + 0.5) {
					continue;
				}
				BlockState s = SueloNatural.superficie(level, pos, x, z);
				int h = pos.getY();
				if (!SueloNatural.natural(s) && !s.is(Blocks.WATER) && !s.canBeReplaced()) {
					continue;
				}
				BlockState bloque = bloque(SueloNatural.arena(s));
				BlockState base = nivel == 0 ? bloque : (SueloNatural.arena(s) ? Blocks.SANDSTONE : Blocks.COBBLESTONE).defaultBlockState();
				// cimiento hasta el suelo de la torre
				for (int y = Math.min(h, y0); y <= y0; y++) {
					level.setBlock(pos.set(x, y, z), base, 2);
				}
				if (d > r - 0.5) {
					// el muro de la torre, con sus huecos
					boolean puerta = ox == dentro.getStepX() * r && oz == dentro.getStepZ() * r;
					boolean salida = Math.abs(ox) == Math.abs(lado.getStepX() * r) && Math.abs(oz) == Math.abs(lado.getStepZ() * r)
							&& (ox == 0 || oz == 0);
					boolean almena = (x + z) % 2 == 0;
					for (int y = y0 + 1; y <= azotea + (almena ? 1 : 0); y++) {
						boolean hueco = puerta && y <= y0 + 2 || salida && y > adarve && y <= adarve + 2;
						level.setBlock(pos.set(x, y, z), hueco ? Blocks.AIR.defaultBlockState() : bloque, 2);
					}
					continue;
				}
				// por dentro: la escala de mano contra el muro de fuera, suelos de madera y aire
				boolean escala = ox == -dentro.getStepX() * (r - 1) && oz == -dentro.getStepZ() * (r - 1);
				for (int y = y0 + 1; y <= azotea + 2; y++) {
					BlockState b;
					if (escala && y <= azotea) {
						b = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, dentro);
					} else if (y == adarve || y == azotea) {
						b = suelo;
					} else {
						b = Blocks.AIR.defaultBlockState();
					}
					level.setBlock(pos.set(x, y, z), b, 2);
				}
				// faroles: uno abajo y otro en el piso del adarve
				if (ox == lado.getStepX() * (r - 1) && oz == lado.getStepZ() * (r - 1)) {
					level.setBlock(pos.set(x, y0 + 1, z), Blocks.LANTERN.defaultBlockState(), 2);
				}
				if (ox == -lado.getStepX() * (r - 1) && oz == -lado.getStepZ() * (r - 1)) {
					level.setBlock(pos.set(x, adarve + 1, z), Blocks.LANTERN.defaultBlockState(), 2);
				}
			}
		}
	}

	private BlockState bloque(boolean arena) {
		return switch (nivel) {
			case 0 -> arena ? Blocks.STRIPPED_ACACIA_LOG.defaultBlockState() : Blocks.SPRUCE_LOG.defaultBlockState();
			case 1 -> arena ? Blocks.SANDSTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
			default -> arena ? Blocks.CUT_SANDSTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
		};
	}

	/**
	 * Una columna de muralla sobre el suelo natural; nada si hay agua o casa. Sobre una calle, la puerta: se deja
	 * paso de cuatro de alto y encima va el dintel, así la muralla no se corta donde la cruza una calle.
	 */
	private void columna(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int alto) {
		BlockState s = SueloNatural.superficie(level, pos, x, z);
		if (calle(s)) {
			if (alto <= PASO) {
				return;
			}
			boolean arena = s.is(Blocks.SMOOTH_SANDSTONE);
			BlockState dintel = switch (nivel) {
				case 0 -> arena ? Blocks.STRIPPED_ACACIA_LOG.defaultBlockState() : Blocks.SPRUCE_LOG.defaultBlockState();
				case 1 -> arena ? Blocks.SANDSTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
				default -> arena ? Blocks.CUT_SANDSTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
			};
			for (int i = 1; i <= alto; i++) {
				pos.move(0, 1, 0);
				BlockState aqui = level.getBlockState(pos);
				if (!aqui.isAir() && !aqui.canBeReplaced()) {
					return;
				}
				if (i > PASO) {
					level.setBlock(pos, dintel, 2);
				}
			}
			return;
		}
		if (s.is(Blocks.WATER)) {
			reja(level, pos, alto);
			return;
		}
		if (!SueloNatural.natural(s)) {
			return;
		}
		boolean arena = SueloNatural.arena(s);
		BlockState bloque = switch (nivel) {
			case 0 -> arena ? Blocks.STRIPPED_ACACIA_LOG.defaultBlockState() : Blocks.SPRUCE_LOG.defaultBlockState();
			case 1 -> arena ? Blocks.SANDSTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
			default -> arena ? Blocks.CUT_SANDSTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
		};
		// cimiento: un bloque hundido en el suelo para que no quede colgando en las cuestas
		level.setBlock(pos, nivel == 0 ? bloque : (arena ? Blocks.SANDSTONE : Blocks.COBBLESTONE).defaultBlockState(), 2);
		for (int i = 1; i <= alto; i++) {
			pos.move(0, 1, 0);
			BlockState aqui = level.getBlockState(pos);
			if (!aqui.isAir() && !aqui.canBeReplaced()) {
				break;
			}
			level.setBlock(pos, bloque, 2);
		}
	}

	/**
	 * Donde la muralla cruza el agua (un río, la orilla de un lago): una reja de hierro desde el fondo hasta dos por
	 * encima del agua, que deja pasar el agua pero no a la gente, y encima la muralla. En las pobres, la empalizada
	 * sigue por el agua sobre pilotes.
	 */
	private void reja(WorldGenLevel level, BlockPos.MutableBlockPos pos, int alto) {
		int agua = pos.getY();
		int x = pos.getX();
		int z = pos.getZ();
		BlockState bloque = bloque(false);
		int fondo = agua;
		while (fondo > level.getMinY() && level.getBlockState(pos.set(x, fondo - 1, z)).is(Blocks.WATER) && agua - fondo < 24) {
			fondo--;
		}
		BlockState barrote = nivel == 0 ? Blocks.SPRUCE_FENCE.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState();
		for (int y = fondo; y <= agua + 2; y++) {
			BlockState b = barrote;
			if (y <= agua && b.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
				b = b.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
			}
			level.setBlock(pos.set(x, y, z), b, 2);
		}
		for (int y = agua + 3; y <= agua + Math.max(alto, 4); y++) {
			level.setBlock(pos.set(x, y, z), bloque, 2);
		}
	}

	/** El suelo de una calle (de las de la capital o de los caminos de los pueblos). */
	private static boolean calle(BlockState s) {
		return s.is(Blocks.DIRT_PATH) || s.is(Blocks.SMOOTH_SANDSTONE) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.OAK_PLANKS);
	}
}
