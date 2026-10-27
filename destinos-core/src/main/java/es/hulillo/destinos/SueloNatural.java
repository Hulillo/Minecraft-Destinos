package es.hulillo.destinos;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** El suelo sobre el que se ponen las calles y la muralla de una capital. */
final class SueloNatural {
	/** Tierra donde se puede construir: lo que deja el generador, no casas ni caminos. */
	private static final Set<Block> NATURAL = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL,
			Blocks.MYCELIUM, Blocks.ROOTED_DIRT, Blocks.SNOW_BLOCK, Blocks.SAND, Blocks.RED_SAND, Blocks.SANDSTONE,
			Blocks.GRAVEL, Blocks.STONE, Blocks.MUD, Blocks.CLAY, Blocks.TERRACOTTA, Blocks.DEEPSLATE, Blocks.TUFF,
			Blocks.CALCITE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.PACKED_ICE, Blocks.MOSS_BLOCK);

	private SueloNatural() {
	}

	/**
	 * Deja pos en el bloque de arriba del todo en (x, z) y lo devuelve. Las plantas y la nieve de encima se quitan y
	 * entonces se mira el de debajo; el agua se queda (es la superficie).
	 */
	static BlockState superficie(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		pos.set(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
		BlockState s = level.getBlockState(pos);
		if (s.canBeReplaced() && !s.is(Blocks.WATER) || s.is(BlockTags.FLOWERS)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			pos.move(0, -1, 0);
			s = level.getBlockState(pos);
		}
		return s;
	}

	static boolean natural(BlockState s) {
		return NATURAL.contains(s.getBlock());
	}

	static boolean arena(BlockState s) {
		return s.is(Blocks.SAND) || s.is(Blocks.RED_SAND) || s.is(Blocks.SANDSTONE);
	}
}
