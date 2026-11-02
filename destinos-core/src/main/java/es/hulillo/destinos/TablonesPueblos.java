package es.hulillo.destinos;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Pone el tablón de encargos junto a la campana de cada pueblo y de cada capital. Las campanas se ven al cargar sus
 * chunks (las trae el pueblo, de vanilla o de cualquier mod); se apuntan y en el siguiente tick se busca al lado un
 * hueco con suelo firme. Cada campana se mira una sola vez (EncargosMundo.campanas): si alguien rompe el tablón, no
 * vuelve a salir. Las campanas que no son de un pueblo (las que pone un jugador) no llevan tablón.
 */
final class TablonesPueblos {
	private static final int MAX_PENDIENTES = 256;
	private static final Deque<BlockPos> PENDIENTES = new ArrayDeque<>();

	private TablonesPueblos() {
	}

	static void alCargar(ServerLevel level, LevelChunk chunk) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		for (BlockEntity be : chunk.getBlockEntities().values()) {
			if (be instanceof BellBlockEntity && PENDIENTES.size() < MAX_PENDIENTES) {
				PENDIENTES.add(be.getBlockPos());
			}
		}
	}

	static void olvida() {
		PENDIENTES.clear();
	}

	static void tick(MinecraftServer server) {
		if (PENDIENTES.isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		EncargosMundo em = EncargosMundo.de(server);
		for (int n = 0; n < 4 && !PENDIENTES.isEmpty(); n++) {
			BlockPos campana = PENDIENTES.poll();
			if (em.campanas.contains(campana.asLong()) || !level.isLoaded(campana)) {
				continue;
			}
			em.campanas.add(campana.asLong());
			em.ensucia();
			if (!dePueblo(level, campana)) {
				continue;
			}
			BlockPos sitio = hueco(level, campana);
			if (sitio == null) {
				DestinosCore.LOG.info("Destinos Core: no hay sitio para el tablón junto a la campana de {}", campana.toShortString());
				continue;
			}
			level.setBlock(sitio, TablonBlock.TABLON.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, mira(sitio, campana)), 3);
			DestinosCore.LOG.info("Destinos Core: tablón de encargos junto a la campana de {}", campana.toShortString());
		}
	}

	/** Si la campana está dentro de un pueblo o de una capital. */
	private static boolean dePueblo(ServerLevel level, BlockPos p) {
		if (level.structureManager().getStructureWithPieceAt(p, StructureTags.VILLAGE).isValid()) {
			return true;
		}
		Structure capital = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.getValue(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "capital"));
		return capital != null && level.structureManager().getStructureWithPieceAt(p, capital).isValid();
	}

	/** Un hueco a 2-4 bloques de la campana, con suelo firme debajo y aire encima, o null. */
	private static BlockPos hueco(ServerLevel level, BlockPos campana) {
		for (int r = 2; r <= 4; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy = 1; dy >= -3; dy--) {
						BlockPos p = campana.offset(dx, dy, dz);
						if (cabe(level, p)) {
							return p;
						}
					}
				}
			}
		}
		return null;
	}

	private static boolean cabe(ServerLevel level, BlockPos p) {
		if (!level.isLoaded(p)) {
			return false;
		}
		BlockState aqui = level.getBlockState(p);
		BlockState arriba = level.getBlockState(p.above());
		BlockPos abajo = p.below();
		return aqui.isAir() && arriba.isAir() && level.getBlockState(abajo).isFaceSturdy(level, abajo, Direction.UP)
				&& level.getBlockState(abajo).getFluidState().isEmpty();
	}

	/** El tablón mira hacia la campana (la cara con los papeles hacia ella). */
	private static Direction mira(BlockPos tablon, BlockPos campana) {
		int dx = campana.getX() - tablon.getX();
		int dz = campana.getZ() - tablon.getZ();
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}
}
