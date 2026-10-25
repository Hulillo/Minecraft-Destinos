package es.hulillo.destinos;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Lee el terreno directamente del generador del mundo, sin generar chunks. Se puede llamar desde otro hilo.
 *
 * Calcular la altura exacta (getBaseHeight) cuesta milisegundos por punto y congelaba la carga de chunks,
 * así que la altura se estima con la función "depth" del generador: depth = gradiente(y) + offset, y la
 * superficie está donde depth ≈ 0. Ignora los picos afilados, pero las montañas ya las marca su bioma.
 */
final class MuestreadorMundo implements MapaTerreno.Muestreador {
	/** Los ríos son estrechos: además del centro de la casilla se mira a esta distancia en 4 direcciones. */
	private static final int RADIO_RIO = 24;

	private final BiomeSource biomas;
	private final Climate.Sampler clima;
	private final DensityFunction profundidad;
	private final int nivelMar;
	private final ChunkGeneratorStructureState estructuras;
	private final StructureSet pueblos;

	MuestreadorMundo(ServerLevel level) {
		RandomState aleatorio = level.getChunkSource().randomState();
		this.biomas = level.getChunkSource().getGenerator().getBiomeSource();
		this.clima = aleatorio.sampler();
		this.profundidad = aleatorio.router().depth();
		this.nivelMar = level.getChunkSource().getGenerator().getSeaLevel();
		this.estructuras = level.getChunkSource().getGeneratorState();
		this.pueblos = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
				.getValue(BuiltinStructureSets.VILLAGES);
	}

	private int altura(int x, int z) {
		// gradiente(0) = 1 en el mundo normal (de 1.5 en y=-64 a -1.5 en y=320)
		double offset = profundidad.compute(new DensityFunction.SinglePointContext(x, 0, z)) - 1.0;
		double y = (1.5 + offset) * 128 - 64;
		return Double.isFinite(y) ? (int) Math.round(y) : nivelMar;
	}

	private Holder<Biome> bioma(int x, int y, int z) {
		return biomas.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), clima);
	}

	@Override
	public MapaTerreno.Casilla muestra(int x, int z) {
		int suelo = altura(x, z);
		int y = Math.max(suelo, nivelMar);
		Holder<Biome> b = bioma(x, y, z);
		boolean oceano = b.is(BiomeTags.IS_OCEAN) || suelo < nivelMar - 12;
		boolean rio = b.is(BiomeTags.IS_RIVER)
				|| bioma(x + RADIO_RIO, y, z).is(BiomeTags.IS_RIVER) || bioma(x - RADIO_RIO, y, z).is(BiomeTags.IS_RIVER)
				|| bioma(x, y, z + RADIO_RIO).is(BiomeTags.IS_RIVER) || bioma(x, y, z - RADIO_RIO).is(BiomeTags.IS_RIVER);
		boolean montana = b.is(BiomeTags.IS_MOUNTAIN) || suelo > nivelMar + 60;
		return new MapaTerreno.Casilla(y, oceano, rio, montana);
	}

	/** Mira los sitios donde el juego intentaría poner un pueblo y si su bioma lo permite. */
	@Override
	public boolean hayPueblo(int x, int z, int radio) {
		if (pueblos == null || !(pueblos.placement() instanceof RandomSpreadStructurePlacement colocacion)) {
			return false;
		}
		int paso = colocacion.spacing();
		long semilla = estructuras.getLevelSeed();
		int r0x = Math.floorDiv((x - radio) >> 4, paso);
		int r1x = Math.floorDiv((x + radio) >> 4, paso);
		int r0z = Math.floorDiv((z - radio) >> 4, paso);
		int r1z = Math.floorDiv((z + radio) >> 4, paso);
		for (int rx = r0x; rx <= r1x; rx++) {
			for (int rz = r0z; rz <= r1z; rz++) {
				ChunkPos c = colocacion.getPotentialStructureChunk(semilla, rx * paso, rz * paso);
				int bx = c.getMiddleBlockX();
				int bz = c.getMiddleBlockZ();
				long dx = bx - x;
				long dz = bz - z;
				if (dx * dx + dz * dz > (long) radio * radio
						|| !colocacion.isStructureChunk(estructuras, c.x, c.z)) {
					continue;
				}
				Holder<Biome> b = bioma(bx, Math.max(altura(bx, bz), nivelMar), bz);
				for (StructureSet.StructureSelectionEntry e : pueblos.structures()) {
					if (e.structure().value().biomes().contains(b)) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
