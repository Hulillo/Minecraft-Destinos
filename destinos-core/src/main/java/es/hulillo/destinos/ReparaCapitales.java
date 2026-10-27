package es.hulillo.destinos;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * Red de seguridad de las capitales: si un jugador llega a una capital del mapa y su chunk ya está generado sin
 * ella (con algunos mods de generación en paralelo la estructura se prepara pero no llega a guardarse en el chunk),
 * se pone entera en ese momento, como /place, y se apunta en el chunk para que /locate y los mapas la encuentren.
 * Solo cuando todos sus chunks están cargados, para no dejarla a medias. Como el chunk ya tiene árboles y no hay
 * adaptación del terreno, antes se talan los árboles sobre cada pieza y bajo las casas se allana el suelo.
 */
final class ReparaCapitales {
	/** Cada cuánto se mira (en ticks) y a qué distancia de un jugador. */
	private static final int CADA = 100;
	private static final int DISTANCIA = 192;
	private static final Identifier CAPITAL = Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "capital");
	/** Bloques de más alrededor de cada pieza donde se talan árboles, y hondura máxima del relleno bajo las casas. */
	private static final int MARGEN = 3;
	private static final int RELLENO = 12;

	/** Capitales ya puestas a mano o que no se han podido poner (por chunk), para no intentarlo cada vez. */
	private final Set<Long> miradas = new HashSet<>();

	void olvida() {
		miradas.clear();
	}

	void tick(MinecraftServer server, MapaTerreno m) {
		if (server.getTickCount() % CADA != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		Structure estructura = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(CAPITAL);
		if (!(estructura instanceof EstructuraCapital capital)) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			for (Geo.Reino reino : Geo.reinosCerca(Geo.celdaDucado(p.getBlockX()), Geo.celdaDucado(p.getBlockZ()), m.sal())) {
				var lista = m.capitalSiLista(reino.kx(), reino.kz());
				if (lista == null || lista.isEmpty()) {
					continue;
				}
				MapaTerreno.Capital c = lista.get();
				long dx = c.x() - p.getBlockX();
				long dz = c.z() - p.getBlockZ();
				if (dx * dx + dz * dz <= (long) DISTANCIA * DISTANCIA) {
					mira(level, capital, c);
				}
			}
		}
	}

	private void mira(ServerLevel level, EstructuraCapital capital, MapaTerreno.Capital c) {
		ChunkPos sitio = new ChunkPos(c.x() >> 4, c.z() >> 4);
		if (miradas.contains(sitio.toLong())) {
			return;
		}
		LevelChunk chunk = level.getChunkSource().getChunkNow(sitio.x, sitio.z);
		if (chunk == null) {
			return;
		}
		StructureStart ya = chunk.getStartForStructure(capital);
		if (ya != null && ya.isValid() || !chunk.getReferencesForStructure(capital).isEmpty()) {
			miradas.add(sitio.toLong());
			return;
		}
		StructureStart inicio = capital.inicio(level, c.x(), c.z());
		if (inicio == null || !inicio.isValid()) {
			miradas.add(sitio.toLong());
			DestinosCore.LOG.warn("Destinos Core: la capital de {}, {} no está en el mundo y tampoco se ha podido poner ahora", c.x(), c.z());
			return;
		}
		BoundingBox caja = inicio.getBoundingBox();
		ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(caja.minX()), SectionPos.blockToSectionCoord(caja.minZ()));
		ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(caja.maxX()), SectionPos.blockToSectionCoord(caja.maxZ()));
		// todos sus chunks cargados, o se espera a la próxima vez
		for (int x = min.x; x <= max.x; x++) {
			for (int z = min.z; z <= max.z; z++) {
				if (level.getChunkSource().getChunkNow(x, z) == null) {
					return;
				}
			}
		}
		miradas.add(sitio.toLong());
		DestinosCore.LOG.warn("Destinos Core: la capital de {}, {} se preparó pero no llegó a su chunk; se pone ahora ({} piezas)",
				c.x(), c.z(), inicio.getPieces().size());
		for (StructurePiece pieza : inicio.getPieces()) {
			prepara(level, pieza);
		}
		// las calles y la muralla buscan el suelo con estos mapas de altura: que ya no vean los árboles
		for (int x = min.x; x <= max.x; x++) {
			for (int z = min.z; z <= max.z; z++) {
				Heightmap.primeHeightmaps(level.getChunkSource().getChunkNow(x, z),
						EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
			}
		}
		chunk.setStartForStructure(capital, inicio);
		for (int x = min.x; x <= max.x; x++) {
			for (int z = min.z; z <= max.z; z++) {
				ChunkPos cp = new ChunkPos(x, z);
				LevelChunk aqui = level.getChunkSource().getChunkNow(x, z);
				aqui.addReferenceForStructure(capital, sitio.toLong());
				inicio.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
						new BoundingBox(cp.getMinBlockX(), level.getMinY(), cp.getMinBlockZ(), cp.getMaxBlockX(),
								level.getMinY() + level.getHeight() - 1, cp.getMaxBlockZ()), cp);
			}
		}
	}

	/**
	 * Tala los árboles sobre la pieza (y un poco alrededor). Si es una casa rígida, además quita el monte que quede
	 * por encima de su base y rellena de tierra el hueco de debajo, como hace la generación normal.
	 */
	private static void prepara(ServerLevel level, StructurePiece pieza) {
		BoundingBox caja = pieza.getBoundingBox();
		boolean casa = pieza instanceof PoolElementStructurePiece p
				&& p.getElement().getProjection() == StructureTemplatePool.Projection.RIGID;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = caja.minX() - MARGEN; x <= caja.maxX() + MARGEN; x++) {
			for (int z = caja.minZ() - MARGEN; z <= caja.maxZ() + MARGEN; z++) {
				boolean dentro = x >= caja.minX() && x <= caja.maxX() && z >= caja.minZ() && z <= caja.maxZ();
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				// de arriba abajo: copas, troncos y plantas fuera; en la casa, también la tierra por encima de su base
				for (; y >= level.getMinY(); y--) {
					BlockState s = level.getBlockState(pos.set(x, y, z));
					boolean quita = s.isAir() || arbol(s) || casa && dentro && y > caja.minY() && SueloNatural.natural(s);
					if (!quita) {
						break;
					}
					if (!s.isAir()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					}
				}
				if (casa && dentro) {
					for (int yy = caja.minY() - 1; yy >= caja.minY() - RELLENO; yy--) {
						BlockState s = level.getBlockState(pos.set(x, yy, z));
						if (!s.isAir() && !s.canBeReplaced() && !arbol(s)) {
							break;
						}
						level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	/** Lo que deja un árbol o la vegetación encima del suelo (el agua no). */
	private static boolean arbol(BlockState s) {
		if (!s.getFluidState().isEmpty() && !s.is(BlockTags.LEAVES)) {
			return false;
		}
		return s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.canBeReplaced() || s.is(BlockTags.FLOWERS)
				|| s.is(Blocks.VINE) || s.is(Blocks.COCOA) || s.is(Blocks.BAMBOO) || s.is(Blocks.BEE_NEST)
				|| s.is(Blocks.MANGROVE_ROOTS) || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK)
				|| s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.MOSS_CARPET);
	}
}
