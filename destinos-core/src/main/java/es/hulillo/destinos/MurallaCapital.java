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
 */
public final class MurallaCapital extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) MurallaCapital::new;
	/** Radio interior del anillo: rodea el centro de la capital (sus calles llegan a 80 bloques). */
	static final int RADIO = 88;
	static final int ARCOS = 64;
	static final int TORRES = 8;
	private static final int RADIO_TORRE = 3;
	private static final int[] ALTO = {4, 5, 7};
	private static final int[] GRUESO = {1, 2, 3};
	/** El alto libre bajo el dintel de las puertas. */
	private static final int PASO = 4;

	private final int cx;
	private final int cz;
	private final int nivel;
	/** Arco: ángulos en décimas de grado [a0, a1). Torre: a1 < 0 y a0 es su ángulo. */
	private final int a0;
	private final int a1;

	private MurallaCapital(int cx, int cz, int nivel, int a0, int a1, BoundingBox caja) {
		super(TIPO, 0, caja);
		this.cx = cx;
		this.cz = cz;
		this.nivel = nivel;
		this.a0 = a0;
		this.a1 = a1;
	}

	private MurallaCapital(CompoundTag t) {
		super(TIPO, t);
		cx = t.getIntOr("cx", 0);
		cz = t.getIntOr("cz", 0);
		nivel = t.getIntOr("nivel", 0);
		a0 = t.getIntOr("a0", 0);
		a1 = t.getIntOr("a1", 0);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "muralla"), TIPO);
	}

	/** Las piezas de la muralla alrededor de (cx, cz); alturaEn da la altura del suelo para cada caja fina. */
	static void piezas(int cx, int cz, int nivel, IntBinaryOperator alturaEn, List<StructurePiece> piezas) {
		int grueso = GRUESO[nivel];
		// las torres antes que los arcos: así el arco ve la torre ya puesta y no la atraviesa
		if (nivel == 2) {
			// torres entre las diagonales, por donde llegan las calles de los barrios
			for (int i = 0; i < TORRES; i++) {
				int a = 225 + i * 3600 / TORRES;
				double r = Math.toRadians(a / 10.0);
				int tx = cx + (int) Math.round(Math.cos(r) * (RADIO + grueso / 2.0));
				int tz = cz + (int) Math.round(Math.sin(r) * (RADIO + grueso / 2.0));
				int y = alturaEn.applyAsInt(tx, tz);
				piezas.add(new MurallaCapital(cx, cz, nivel, a, -1, new BoundingBox(tx - RADIO_TORRE - 1, y, tz - RADIO_TORRE - 1,
						tx + RADIO_TORRE + 1, y + 1, tz + RADIO_TORRE + 1)));
			}
		}
		for (int i = 0; i < ARCOS; i++) {
			int a0 = i * 3600 / ARCOS;
			int a1 = (i + 1) * 3600 / ARCOS;
			int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
			for (int k = 0; k <= 4; k++) {
				double a = Math.toRadians((a0 + (a1 - a0) * k / 4.0) / 10.0);
				for (int r : new int[] {RADIO - 1, RADIO + grueso + 1}) {
					int x = cx + (int) Math.floor(Math.cos(a) * r);
					int z = cz + (int) Math.floor(Math.sin(a) * r);
					minX = Math.min(minX, x);
					minZ = Math.min(minZ, z);
					maxX = Math.max(maxX, x);
					maxZ = Math.max(maxZ, z);
				}
			}
			int y = alturaEn.applyAsInt((minX + maxX) / 2, (minZ + maxZ) / 2);
			piezas.add(new MurallaCapital(cx, cz, nivel, a0, a1, new BoundingBox(minX - 1, y, minZ - 1, maxX + 1, y + 1, maxZ + 1)));
		}
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		t.putInt("cx", cx);
		t.putInt("cz", cz);
		t.putInt("nivel", nivel);
		t.putInt("a0", a0);
		t.putInt("a1", a1);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar,
			BoundingBox caja, ChunkPos chunk, BlockPos pivote) {
		BoundingBox mia = getBoundingBox();
		int grueso = GRUESO[nivel];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		double tx = 0;
		double tz = 0;
		if (a1 < 0) {
			tx = (mia.minX() + mia.maxX()) / 2.0;
			tz = (mia.minZ() + mia.maxZ()) / 2.0;
		}
		for (int x = Math.max(mia.minX(), caja.minX()); x <= Math.min(mia.maxX(), caja.maxX()); x++) {
			for (int z = Math.max(mia.minZ(), caja.minZ()); z <= Math.min(mia.maxZ(), caja.maxZ()); z++) {
				int alto;
				boolean almena;
				if (a1 < 0) {
					double d = Math.hypot(x - tx, z - tz);
					if (d > RADIO_TORRE + 0.5) {
						continue;
					}
					alto = ALTO[nivel] + 4;
					almena = d > RADIO_TORRE - 0.5 && (x + z) % 2 == 0;
				} else {
					double d = Math.hypot(x - cx, z - cz);
					if (d < RADIO || d >= RADIO + grueso) {
						continue;
					}
					int a = (int) Math.floor(Math.toDegrees(Math.atan2(z - cz, x - cx)) * 10);
					a = Math.floorMod(a, 3600);
					if (a < a0 || a >= a1) {
						continue;
					}
					alto = ALTO[nivel];
					// empalizada en punta (un tronco sí y otro no, más alto); almenas en el borde de fuera
					almena = nivel == 0 ? (x + z) % 2 == 0 : d >= RADIO + grueso - 1 && (x + z) % 2 == 0;
				}
				columna(level, pos, x, z, alto + (almena ? 1 : 0));
			}
		}
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

	/** El suelo de una calle (de las de la capital o de los caminos de los pueblos). */
	private static boolean calle(BlockState s) {
		return s.is(Blocks.DIRT_PATH) || s.is(Blocks.SMOOTH_SANDSTONE) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.OAK_PLANKS);
	}
}
