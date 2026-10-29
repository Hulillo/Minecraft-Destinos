package es.hulillo.destinos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Un muelle de madera de la capital junto al río, el lago o el mar: desde la orilla en (x, z) se adentra en el agua
 * hacia dir, tres de ancho, con la tablazón a ras del agua, pilotes de tronco hasta el fondo, norays de valla, un farol
 * en la punta y una barca amarrada a cada lado. Mira el agua de verdad al ponerse: donde no hay agua, no hace nada.
 */
public final class MuelleCapital extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) MuelleCapital::new;
	static final int LARGO = 9;

	private final int x;
	private final int z;
	private final int dir;

	MuelleCapital(int x, int z, int y, Direction dir) {
		super(TIPO, 0, caja(x, z, y, dir));
		this.x = x;
		this.z = z;
		this.dir = dir.get2DDataValue();
	}

	int x() {
		return x;
	}

	int z() {
		return z;
	}

	Direction dir() {
		return Direction.from2DDataValue(dir);
	}

	private static BoundingBox caja(int x, int z, int y, Direction d) {
		int x1 = x + d.getStepX() * LARGO;
		int z1 = z + d.getStepZ() * LARGO;
		return new BoundingBox(Math.min(x, x1) - 2, y - 2, Math.min(z, z1) - 2, Math.max(x, x1) + 2, y + 3, Math.max(z, z1) + 2);
	}

	private MuelleCapital(CompoundTag t) {
		super(TIPO, t);
		x = t.getIntOr("x", 0);
		z = t.getIntOr("z", 0);
		dir = t.getIntOr("dir", 0);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "muelle"), TIPO);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		t.putInt("x", x);
		t.putInt("z", z);
		t.putInt("dir", dir);
	}

	@Override
	public boolean isCloseToChunk(ChunkPos chunk, int distancia) {
		return false;
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar,
			BoundingBox caja, ChunkPos chunk, BlockPos pivote) {
		Direction d = Direction.from2DDataValue(dir);
		Direction lado = d.getClockWise();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState tabla = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState pilote = Blocks.SPRUCE_LOG.defaultBlockState();
		BlockState valla = Blocks.SPRUCE_FENCE.defaultBlockState();
		int ultimo = -1;
		int aguaY = Integer.MIN_VALUE;
		for (int i = 0; i <= LARGO; i++) {
			for (int w = -1; w <= 1; w++) {
				int cx = x + d.getStepX() * i + lado.getStepX() * w;
				int cz = z + d.getStepZ() * i + lado.getStepZ() * w;
				if (!caja.isInside(cx, caja.minY(), cz)) {
					continue;
				}
				BlockState s = SueloNatural.superficie(level, pos, cx, cz);
				if (!s.is(Blocks.WATER)) {
					continue;
				}
				int y = pos.getY();
				aguaY = y;
				ultimo = Math.max(ultimo, i);
				level.setBlock(pos.set(cx, y, cz), tabla, 2);
				// pilotes hasta el fondo en las esquinas de cada tramo, con su noray encima
				if (w != 0 && i % 3 == 0) {
					for (int k = y - 1; k > level.getMinY(); k--) {
						BlockState b = level.getBlockState(pos.set(cx, k, cz));
						if (!b.is(Blocks.WATER) && !b.canBeReplaced()) {
							break;
						}
						level.setBlock(pos, pilote, 2);
					}
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
		for (int s = -1; s <= 1; s += 2) {
			int bx = x + d.getStepX() * (ultimo - 1) + lado.getStepX() * 2 * s;
			int bz = z + d.getStepZ() * (ultimo - 1) + lado.getStepZ() * 2 * s;
			if (caja.isInside(bx, caja.minY(), bz) && level.getBlockState(pos.set(bx, aguaY, bz)).is(Blocks.WATER)
					&& level.getBlockState(pos.set(bx, aguaY + 1, bz)).isAir()) {
				SeresObra.pon(level, new Obra.Ser(SeresObra.BARCA, 0, 0, 0, d), new BlockPos(bx, aguaY + 1, bz), 0);
			}
		}
	}
}
