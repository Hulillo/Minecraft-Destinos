package es.hulillo.destinos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.ChunkPos;

/**
 * Pone el tablón de encargos junto a la campana de cada pueblo y de cada capital. Las campanas se ven al cargar sus
 * chunks (las trae el pueblo, de vanilla o de cualquier mod); se apuntan y en el siguiente tick se busca al lado un
 * hueco en el suelo de la plaza. Cada campana se mira una sola vez (EncargosMundo.campanas): si alguien rompe el tablón, no
 * vuelve a salir. Las campanas que no son de un pueblo (las que pone un jugador) no llevan tablón.
 */
final class TablonesPueblos {
	private static final int MAX_PENDIENTES = 256;
	private static final Deque<BlockPos> PENDIENTES = new ArrayDeque<>();
	/** Para /destinoscore tablones: chunks mirados, campanas vistas, tablones puestos, campanas sin pueblo y sin sitio. */
	private static final int[] CUENTA = new int[5];
	private static Structure capital;

	private TablonesPueblos() {
	}

	static void alCargar(ServerLevel level, LevelChunk chunk) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		// se miran los bloques y no los datos de bloque: en un chunk recién generado aún no están creados
		if (chunk.getAllReferences().isEmpty()) {
			return; // las campanas de pueblo siempre van en chunks de una estructura
		}
		CUENTA[0]++;
		LevelChunkSection[] secciones = chunk.getSections();
		for (int i = 0; i < secciones.length; i++) {
			LevelChunkSection s = secciones[i];
			if (s.hasOnlyAir() || !s.maybeHas(b -> b.is(Blocks.BELL))) {
				continue;
			}
			int y0 = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (s.getBlockState(x, y, z).is(Blocks.BELL) && PENDIENTES.size() < MAX_PENDIENTES) {
							CUENTA[1]++;
							PENDIENTES.add(new BlockPos(chunk.getPos().getMinBlockX() + x, y0 + y, chunk.getPos().getMinBlockZ() + z));
						}
					}
				}
			}
		}
	}

	static void olvida() {
		PENDIENTES.clear();
		capital = null;
	}

	static String cuenta() {
		return "Destinos Core: tablones · " + CUENTA[0] + " chunks mirados · " + CUENTA[1] + " campanas vistas · " + CUENTA[2]
				+ " tablones puestos · " + CUENTA[3] + " campanas sin pueblo · " + CUENTA[4] + " sin sitio · " + PENDIENTES.size()
				+ " pendientes";
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
			if (!level.hasChunksAt(campana.offset(-7, 0, -7), campana.offset(7, 0, 7))) {
				// mirar alturas en un chunk sin cargar lo generaría aquí mismo: se espera a que estén los de al lado
				PENDIENTES.addLast(campana);
				return;
			}
			em.campanas.add(campana.asLong());
			em.ensucia();
			if (!dePueblo(level, campana)) {
				CUENTA[3]++;
				DestinosCore.LOG.info("Destinos Core: la campana de {} no es de ningún pueblo: sin tablón", campana.toShortString());
				continue;
			}
			BlockPos sitio = hueco(level, campana);
			if (sitio == null) {
				CUENTA[4]++;
				DestinosCore.LOG.info("Destinos Core: no hay sitio para el tablón junto a la campana de {}", campana.toShortString());
				continue;
			}
			CUENTA[2]++;
			level.setBlock(sitio, TablonBlock.TABLON.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, mira(sitio, campana)), 3);
			DestinosCore.LOG.info("Destinos Core: tablón de encargos junto a la campana de {}", campana.toShortString());
		}
	}

	/**
	 * Si la campana está dentro de un pueblo o de una capital. Solo mira chunks ya cargados: getStructureWithPieceAt
	 * carga (o genera) el chunk de inicio de cada estructura que pase por aquí, que puede estar lejos, y eso desde el
	 * hilo del servidor puede dejarlo esperando a la generación. Si el inicio no está cargado, basta con que el chunk de
	 * la campana sea de un pueblo o de una capital.
	 */
	private static boolean dePueblo(ServerLevel level, BlockPos p) {
		LevelChunk aqui = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(p.getX()), SectionPos.blockToSectionCoord(p.getZ()));
		if (aqui == null) {
			return false;
		}
		if (capital == null) {
			capital = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
					.getValue(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "capital"));
		}
		var estructuras = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (Map.Entry<Structure, LongSet> ref : aqui.getAllReferences().entrySet()) {
			Structure s = ref.getKey();
			boolean vale = s == capital || estructuras.wrapAsHolder(s).is(StructureTags.VILLAGE);
			if (!vale) {
				continue;
			}
			for (long inicio : ref.getValue()) {
				LevelChunk c = level.getChunkSource().getChunkNow(ChunkPos.getX(inicio), ChunkPos.getZ(inicio));
				if (c == null) {
					return true; // sin cargar: no se mira pieza a pieza
				}
				StructureStart st = c.getStartForStructure(s);
				if (st != null && st.isValid()) {
					for (StructurePiece pieza : st.getPieces()) {
						if (pieza.getBoundingBox().isInside(p)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	/**
	 * Un sitio en el suelo de la plaza a 3-6 bloques de la campana, o null. El suelo de la plaza es la altura más
	 * repetida alrededor de la campana; solo valen columnas a esa altura (±1) con las cuatro de al lado también, así que
	 * nunca sale encima de un pozo, de un tejado o de un puesto, ni al borde de un escalón.
	 */
	private static BlockPos hueco(ServerLevel level, BlockPos campana) {
		int[] cuenta = new int[64];
		int base = campana.getY() - 32;
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				int h = suelo(level, campana.getX() + dx, campana.getZ() + dz) - base;
				if (h >= 0 && h < cuenta.length) {
					cuenta[h]++;
				}
			}
		}
		int mejor = -1;
		for (int i = 0; i < cuenta.length; i++) {
			if (mejor < 0 || cuenta[i] > cuenta[mejor]) {
				mejor = i;
			}
		}
		if (mejor < 0 || cuenta[mejor] == 0) {
			return null;
		}
		int plaza = base + mejor;
		for (int r = 3; r <= 6; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = campana.getX() + dx;
					int z = campana.getZ() + dz;
					int h = suelo(level, x, z);
					if (Math.abs(h - plaza) <= 1 && llano(level, x, z, h)) {
						BlockPos p = new BlockPos(x, h, z);
						if (cabe(level, p)) {
							return p;
						}
					}
				}
			}
		}
		return null;
	}

	/** El primer hueco encima del suelo (sin contar hojas). */
	private static int suelo(ServerLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	private static boolean llano(ServerLevel level, int x, int z, int h) {
		return suelo(level, x + 1, z) == h && suelo(level, x - 1, z) == h && suelo(level, x, z + 1) == h && suelo(level, x, z - 1) == h;
	}

	private static boolean cabe(ServerLevel level, BlockPos p) {
		if (!level.isLoaded(p)) {
			return false;
		}
		BlockState aqui = level.getBlockState(p);
		BlockState arriba = level.getBlockState(p.above());
		BlockPos abajo = p.below();
		BlockState suelo = level.getBlockState(abajo);
		return aqui.isAir() && arriba.isAir() && suelo.isFaceSturdy(level, abajo, Direction.UP) && suelo.getFluidState().isEmpty()
				&& !suelo.is(BlockTags.LEAVES) && !suelo.is(BlockTags.LOGS) && !suelo.is(BlockTags.PLANKS)
				&& !suelo.is(BlockTags.WOODEN_SLABS);
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
