package es.hulillo.destinos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Quita los trozos de árbol que quedan volando en los asentamientos: al despejar calles y casas, la tala solo puede
 * tocar su chunk, y la copa o las ramas que caían en el de al lado se quedaban en el aire sin tronco. Un grupo de
 * troncos unidos (también en diagonal) que no toca nada firme (ni tierra, ni piedra, ni una casa: solo aire, hojas o
 * plantas) está suelto y se quita; las vigas de las casas tocan tablones o piedra y se quedan. Las hojas que quedan
 * sin tronco se caen solas.
 */
final class TroncosSueltos {
	/** Hasta dónde se mira por debajo de lo más alto de cada columna. */
	private static final int HONDO = 40;
	/** Un grupo más grande que esto se da por bueno (un árbol de verdad, o algo construido). */
	private static final int MAX_GRUPO = 400;

	private TroncosSueltos() {
	}

	static void quita(WorldGenLevel level, BoundingBox caja) {
		Set<BlockPos> visto = new HashSet<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = caja.minX(); x <= caja.maxX(); x++) {
			for (int z = caja.minZ(); z <= caja.maxZ(); z++) {
				int cima = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
				for (int y = cima; y > cima - HONDO && y > level.getMinY(); y--) {
					pos.set(x, y, z);
					if (!level.getBlockState(pos).is(BlockTags.LOGS) || visto.contains(pos)) {
						continue;
					}
					List<BlockPos> grupo = grupo(level, pos.immutable(), caja, visto);
					if (grupo != null) {
						for (BlockPos p : grupo) {
							if (caja.isInside(p)) {
								level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
							}
						}
					}
				}
			}
		}
	}

	/** El grupo de troncos unido a desde, si está suelto; null si toca algo firme o es muy grande. */
	private static List<BlockPos> grupo(WorldGenLevel level, BlockPos desde, BoundingBox caja, Set<BlockPos> visto) {
		List<BlockPos> grupo = new ArrayList<>();
		ArrayDeque<BlockPos> cola = new ArrayDeque<>();
		cola.add(desde);
		visto.add(desde);
		boolean firme = false;
		while (!cola.isEmpty()) {
			BlockPos p = cola.poll();
			grupo.add(p);
			if (grupo.size() > MAX_GRUPO) {
				firme = true;
			}
			for (Direction d : Direction.values()) {
				if (apoya(level.getBlockState(p.relative(d)))) {
					firme = true;
				}
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos q = p.offset(dx, dy, dz);
						// sin salir de los chunks de al lado (lo que se puede leer al decorar)
						if (Math.abs(q.getX() - caja.getCenter().getX()) > 23 || Math.abs(q.getZ() - caja.getCenter().getZ()) > 23) {
							continue;
						}
						if (!visto.contains(q) && level.getBlockState(q).is(BlockTags.LOGS)) {
							visto.add(q);
							cola.add(q);
						}
					}
				}
			}
		}
		return firme ? null : grupo;
	}

	/** Si el bloque sostiene un tronco: todo lo que no es aire, hojas, otro tronco o una planta. */
	private static boolean apoya(BlockState s) {
		return !s.isAir() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && !s.canBeReplaced() && s.getFluidState().isEmpty()
				&& !s.is(Blocks.VINE) && !s.is(Blocks.COCOA) && !s.is(Blocks.BEE_NEST) && !s.is(Blocks.MOSS_CARPET);
	}
}
