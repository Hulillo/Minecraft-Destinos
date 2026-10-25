package es.hulillo.destinos;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Lee el terreno directamente del generador del mundo, sin generar chunks: la altura sale del ruido del
 * terreno (incluye Lithosphere y compañía) y el bioma de la fuente de biomas. Se puede llamar desde otro hilo.
 */
final class MuestreadorMundo implements MapaTerreno.Muestreador {
	private final ServerLevel level;
	private final ChunkGenerator generador;
	private final RandomState aleatorio;
	private final int nivelMar;

	MuestreadorMundo(ServerLevel level) {
		this.level = level;
		this.generador = level.getChunkSource().getGenerator();
		this.aleatorio = level.getChunkSource().randomState();
		this.nivelMar = generador.getSeaLevel();
	}

	@Override
	public MapaTerreno.Casilla muestra(int x, int z) {
		int suelo = generador.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, aleatorio);
		int y = Math.max(suelo, nivelMar);
		Holder<Biome> bioma = generador.getBiomeSource().getNoiseBiome(
				QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), aleatorio.sampler());
		boolean oceano = bioma.is(BiomeTags.IS_OCEAN) || suelo < nivelMar - 6;
		boolean rio = bioma.is(BiomeTags.IS_RIVER);
		boolean montana = bioma.is(BiomeTags.IS_MOUNTAIN) || suelo > nivelMar + 60;
		return new MapaTerreno.Casilla(y, oceano, rio, montana);
	}
}
