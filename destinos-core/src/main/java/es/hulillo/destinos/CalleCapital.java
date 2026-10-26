package es.hulillo.destinos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Un tramo de la calle que une el centro de una capital con uno de sus barrios: 3 bloques de camino sobre el
 * suelo natural (tablones sobre el agua) que rodea las casas en vez de pisarlas. La calle se parte en tramos
 * cortos con una caja fina a la altura del suelo, así el terreno se allana un poco bajo cada tramo (como con
 * las piezas de los pueblos) en vez de en una caja enorme.
 */
public final class CalleCapital extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) CalleCapital::new;
	private static final int ANCHO = 1;

	private final int x0;
	private final int z0;
	private final int x1;
	private final int z1;

	CalleCapital(int x0, int z0, int x1, int z1, int y) {
		super(TIPO, 0, new BoundingBox(Math.min(x0, x1) - ANCHO, y, Math.min(z0, z1) - ANCHO,
				Math.max(x0, x1) + ANCHO, y + 2, Math.max(z0, z1) + ANCHO));
		this.x0 = x0;
		this.z0 = z0;
		this.x1 = x1;
		this.z1 = z1;
	}

	private CalleCapital(CompoundTag t) {
		super(TIPO, t);
		x0 = t.getIntOr("x0", 0);
		z0 = t.getIntOr("z0", 0);
		x1 = t.getIntOr("x1", 0);
		z1 = t.getIntOr("z1", 0);
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
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar,
			BoundingBox caja, ChunkPos chunk, BlockPos pivote) {
		int pasos = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i <= pasos; i++) {
			int cx = x0 + Math.round((x1 - x0) * (float) i / Math.max(1, pasos));
			int cz = z0 + Math.round((z1 - z0) * (float) i / Math.max(1, pasos));
			for (int ox = -ANCHO; ox <= ANCHO; ox++) {
				for (int oz = -ANCHO; oz <= ANCHO; oz++) {
					int x = cx + ox;
					int z = cz + oz;
					if (x < caja.minX() || x > caja.maxX() || z < caja.minZ() || z > caja.maxZ()) {
						continue;
					}
					pon(level, pos, x, z);
				}
			}
		}
	}

	private static void pon(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
		pos.set(x, y, z);
		BlockState s = level.getBlockState(pos);
		// plantas y nieve encima del suelo: fuera, y se mira el bloque de debajo
		if (s.canBeReplaced() || s.is(BlockTags.FLOWERS)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			pos.move(0, -1, 0);
			s = level.getBlockState(pos);
		}
		BlockState nuevo = null;
		if (s.is(Blocks.WATER)) {
			nuevo = Blocks.OAK_PLANKS.defaultBlockState();
		} else if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL)
				|| s.is(Blocks.MYCELIUM) || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.SNOW_BLOCK)) {
			nuevo = Blocks.DIRT_PATH.defaultBlockState();
		} else if (s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)) {
			nuevo = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
		} else if (s.is(Blocks.GRAVEL) || s.is(Blocks.STONE) || s.is(Blocks.MUD)) {
			nuevo = Blocks.COBBLESTONE.defaultBlockState();
		}
		// el resto (casas, caminos ya puestos, hielo…) se deja como está
		if (nuevo != null) {
			level.setBlock(pos, nuevo, 2);
		}
	}
}
