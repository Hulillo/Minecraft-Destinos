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
 * Un tramo de la calle que une el centro de una capital con uno de sus barrios: 3 bloques de camino sobre el
 * suelo natural que rodea las casas en vez de pisarlas. La calle se parte en tramos cortos con una caja fina a la
 * altura del suelo, así el terreno se allana un poco bajo cada tramo (como con las piezas de los pueblos) en vez de
 * en una caja enorme. Sobre el agua, las avenidas (puente) cruzan por un puente de piedra medio bloque por encima,
 * con pretil a los lados y pilares hasta el fondo; las rondas se paran en la orilla.
 */
public final class CalleCapital extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) CalleCapital::new;
	private static final int ANCHO = 1;
	/** Cada cuántos bloques el puente lleva pilares hasta el fondo. */
	private static final int PILARES = 5;

	private final int x0;
	private final int z0;
	private final int x1;
	private final int z1;
	private final boolean puente;

	CalleCapital(int x0, int z0, int x1, int z1, int y, boolean puente) {
		super(TIPO, 0, new BoundingBox(Math.min(x0, x1) - ANCHO - 1, y, Math.min(z0, z1) - ANCHO - 1,
				Math.max(x0, x1) + ANCHO + 1, y + 2, Math.max(z0, z1) + ANCHO + 1));
		this.x0 = x0;
		this.z0 = z0;
		this.x1 = x1;
		this.z1 = z1;
		this.puente = puente;
	}

	private CalleCapital(CompoundTag t) {
		super(TIPO, t);
		x0 = t.getIntOr("x0", 0);
		z0 = t.getIntOr("z0", 0);
		x1 = t.getIntOr("x1", 0);
		z1 = t.getIntOr("z1", 0);
		puente = t.getBooleanOr("puente", false);
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
		t.putBoolean("puente", puente);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar,
			BoundingBox caja, ChunkPos chunk, BlockPos pivote) {
		int pasos = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
		// de través: el puente lleva el pretil una casilla por fuera del camino, a cada lado
		int tx = Math.abs(z1 - z0) >= Math.abs(x1 - x0) ? 1 : 0;
		int tz = 1 - tx;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i <= pasos; i++) {
			int cx = x0 + Math.round((x1 - x0) * (float) i / Math.max(1, pasos));
			int cz = z0 + Math.round((z1 - z0) * (float) i / Math.max(1, pasos));
			for (int k = -ANCHO - 1; k <= ANCHO + 1; k++) {
				int x = cx + k * tx;
				int z = cz + k * tz;
				if (x < caja.minX() || x > caja.maxX() || z < caja.minZ() || z > caja.maxZ()) {
					continue;
				}
				boolean pretil = Math.abs(k) > ANCHO;
				BlockState s = SueloNatural.superficie(level, pos, x, z);
				if (s.is(Blocks.WATER)) {
					if (puente) {
						puente(level, pos, pretil, Math.floorMod(cx + cz, PILARES) == 0, tx == 1);
					}
				} else if (!pretil) {
					pon(level, pos, s);
				}
			}
		}
	}

	/**
	 * Una casilla del puente sobre el agua de pos: losa medio bloque por encima (se sube desde la orilla sin saltar),
	 * pretil unido a lo largo (aLoLargoDeZ) y, a trechos, el pilar hasta el fondo.
	 */
	private static void puente(WorldGenLevel level, BlockPos.MutableBlockPos pos, boolean pretil, boolean pilar, boolean aLoLargoDeZ) {
		int agua = pos.getY();
		pos.setY(agua + 1);
		level.setBlock(pos, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 2);
		if (!pretil) {
			return;
		}
		pos.setY(agua + 2);
		BlockState muro = Blocks.STONE_BRICK_WALL.defaultBlockState();
		muro = aLoLargoDeZ ? muro.setValue(BlockStateProperties.NORTH_WALL, WallSide.LOW).setValue(BlockStateProperties.SOUTH_WALL, WallSide.LOW)
				: muro.setValue(BlockStateProperties.EAST_WALL, WallSide.LOW).setValue(BlockStateProperties.WEST_WALL, WallSide.LOW);
		level.setBlock(pos, muro, 2);
		if (pilar) {
			for (int y = agua; y > level.getMinY(); y--) {
				pos.setY(y);
				BlockState b = level.getBlockState(pos);
				if (y != agua && !b.is(Blocks.WATER) && !b.canBeReplaced()) {
					break;
				}
				level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 2);
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
