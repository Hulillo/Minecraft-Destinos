package es.hulillo.destinos;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
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
	 * entonces se mira el de debajo; el agua se queda (es la superficie). Los medios bloques de terreno de los mods
	 * (los de césped de Terrain Slabs…) también se quitan, para que las calles y la muralla pisen el suelo de debajo.
	 */
	static BlockState superficie(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		pos.set(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
		BlockState s = level.getBlockState(pos);
		for (int i = 0; i < 2 && (s.canBeReplaced() && !s.is(Blocks.WATER) || s.is(BlockTags.FLOWERS) || deTerreno(s)); i++) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			pos.move(0, -1, 0);
			s = level.getBlockState(pos);
		}
		return s;
	}

	/** Un medio bloque o capa de terreno puesto por un mod (no las losas de las casas, que son de Minecraft). */
	static boolean deTerreno(BlockState s) {
		var id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
		if (id.getNamespace().equals("minecraft")) {
			return false;
		}
		return s.getBlock() instanceof SlabBlock || s.getBlock() instanceof StairBlock || id.getPath().contains("slab");
	}

	static boolean natural(BlockState s) {
		return NATURAL.contains(s.getBlock());
	}

	static boolean arena(BlockState s) {
		return s.is(Blocks.SAND) || s.is(Blocks.RED_SAND) || s.is(Blocks.SANDSTONE);
	}
}
