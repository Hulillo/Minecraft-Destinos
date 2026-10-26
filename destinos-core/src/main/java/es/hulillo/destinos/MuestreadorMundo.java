package es.hulillo.destinos;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.StructureTags;
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
 * El gradiente cambia según el mundo (Lithosphere lo estira hasta y=512), así que se mide en dos alturas
 * en vez de suponer el del mundo normal.
 */
final class MuestreadorMundo implements MapaTerreno.Muestreador {
	/** Los ríos son estrechos: además del centro de la casilla se mira a esta distancia en 4 direcciones. */
	private static final int RADIO_RIO = 24;
	private static final int[][] CRUZ = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	/** Alturas a las que se mide depth para sacar su pendiente. */
	private static final int Y_BAJA = 0;
	private static final int Y_ALTA = 128;

	/**
	 * Familias de bioma, en orden de prioridad (el primero que encaja gana). Las etiquetas comunes de Fabric
	 * las rellenan también los mods de biomas como WWOO. 0 = otra cosa.
	 */
	@SuppressWarnings("unchecked")
	private static final TagKey<Biome>[] FAMILIAS = new TagKey[] {
		BiomeTags.IS_OCEAN, BiomeTags.IS_RIVER, ConventionalBiomeTags.IS_SNOWY, BiomeTags.IS_BADLANDS,
		ConventionalBiomeTags.IS_DESERT, BiomeTags.IS_JUNGLE, BiomeTags.IS_SAVANNA, ConventionalBiomeTags.IS_SWAMP,
		BiomeTags.IS_TAIGA, BiomeTags.IS_MOUNTAIN, BiomeTags.IS_FOREST, ConventionalBiomeTags.IS_PLAINS,
	};

	private final BiomeSource biomas;
	private final Climate.Sampler clima;
	private final DensityFunction profundidad;
	private final int nivelMar;
	private final ChunkGeneratorStructureState estructuras;
	private final StructureSet pueblos;
	/** Todos los grupos de estructuras que ponen pueblos (los vanilla y los de mods como Towns and Towers). */
	private final List<StructureSet> gruposPueblo = new ArrayList<>();

	/** Culturas del datapack en su orden de detección (cultura/detectar) y si son de montaña. */
	private static final String[] CULTURAS = {"norte", "cerezo", "bambu", "selva", "andina", "sabana", "desierto", "estepa", "costa"};
	private static final boolean[] DE_MONTANA = {true, false, false, false, true, false, false, true, false};
	@SuppressWarnings("unchecked")
	private static final TagKey<Biome>[] TAGS_CULTURA = new TagKey[CULTURAS.length];
	private static final TagKey<Biome> ISLAS = TagKey.create(Registries.BIOME,
			Identifier.fromNamespaceAndPath("destinos", "cultura/islas"));

	static {
		for (int i = 0; i < CULTURAS.length; i++) {
			TAGS_CULTURA[i] = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("destinos", "cultura/" + CULTURAS[i]));
		}
	}

	MuestreadorMundo(ServerLevel level) {
		RandomState aleatorio = level.getChunkSource().randomState();
		this.biomas = level.getChunkSource().getGenerator().getBiomeSource();
		this.clima = aleatorio.sampler();
		this.profundidad = aleatorio.router().depth();
		this.nivelMar = level.getChunkSource().getGenerator().getSeaLevel();
		this.estructuras = level.getChunkSource().getGeneratorState();
		this.pueblos = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
				.getValue(BuiltinStructureSets.VILLAGES);
		for (StructureSet grupo : level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)) {
			if (grupo.placement() instanceof RandomSpreadStructurePlacement
					&& grupo.structures().stream().anyMatch(e -> e.structure().is(StructureTags.VILLAGE))) {
				gruposPueblo.add(grupo);
			}
		}
	}

	private int altura(int x, int z) {
		// depth baja en línea recta con la altura: con dos medidas se ve dónde cruza el cero
		double baja = profundidad.compute(new DensityFunction.SinglePointContext(x, Y_BAJA, z));
		double alta = profundidad.compute(new DensityFunction.SinglePointContext(x, Y_ALTA, z));
		double pendiente = (alta - baja) / (Y_ALTA - Y_BAJA);
		double y = Y_BAJA - baja / pendiente;
		return pendiente < 0 && Double.isFinite(y) ? (int) Math.round(y) : nivelMar;
	}

	private Holder<Biome> bioma(int x, int y, int z) {
		return biomas.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), clima);
	}

	@Override
	public MapaTerreno.Casilla muestra(int x, int z) {
		int suelo = altura(x, z);
		int y = Math.max(suelo, nivelMar);
		Holder<Biome> b = bioma(x, y, z);
		// el mar lo dice el bioma: la altura estimada puede fallar con generadores de terreno de mods
		boolean oceano = b.is(BiomeTags.IS_OCEAN);
		boolean rio = b.is(BiomeTags.IS_RIVER) || hayRioCerca(x, y, z);
		boolean montana = b.is(BiomeTags.IS_MOUNTAIN) || suelo > nivelMar + 60;
		return new MapaTerreno.Casilla(y, oceano, rio, montana, familia(b));
	}

	@Override
	public boolean agua(int x, int z) {
		// el bioma (mar, río) y, para lagos y charcas, el suelo estimado claramente por debajo del mar
		int suelo = altura(x, z);
		Holder<Biome> b = bioma(x, Math.max(suelo, nivelMar), z);
		return b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_RIVER) || b.is(BiomeTags.IS_BEACH) || suelo < nivelMar - 2;
	}

	private boolean hayRioCerca(int x, int y, int z) {
		for (int[] d : CRUZ) {
			if (bioma(x + d[0] * RADIO_RIO, y, z + d[1] * RADIO_RIO).is(BiomeTags.IS_RIVER)) {
				return true;
			}
		}
		return false;
	}

	private static int familia(Holder<Biome> b) {
		for (int i = 0; i < FAMILIAS.length; i++) {
			if (b.is(FAMILIAS[i])) {
				return i + 1;
			}
		}
		return 0;
	}

	@Override
	public List<int[]> pueblos(int x0, int z0, int x1, int z1) {
		List<int[]> res = new ArrayList<>();
		long semilla = estructuras.getLevelSeed();
		for (StructureSet grupo : gruposPueblo) {
			RandomSpreadStructurePlacement colocacion = (RandomSpreadStructurePlacement) grupo.placement();
			int paso = colocacion.spacing();
			for (int rx = Math.floorDiv(x0 >> 4, paso); rx <= Math.floorDiv((x1 - 1) >> 4, paso); rx++) {
				for (int rz = Math.floorDiv(z0 >> 4, paso); rz <= Math.floorDiv((z1 - 1) >> 4, paso); rz++) {
					ChunkPos c = colocacion.getPotentialStructureChunk(semilla, rx * paso, rz * paso);
					int bx = c.getMiddleBlockX();
					int bz = c.getMiddleBlockZ();
					if (bx < x0 || bz < z0 || bx >= x1 || bz >= z1 || !colocacion.isStructureChunk(estructuras, c.x, c.z)) {
						continue;
					}
					Holder<Biome> b = bioma(bx, Math.max(altura(bx, bz), nivelMar), bz);
					for (StructureSet.StructureSelectionEntry e : grupo.structures()) {
						if (e.structure().is(StructureTags.VILLAGE) && e.structure().value().biomes().contains(b)) {
							res.add(new int[] {bx, bz});
							break;
						}
					}
				}
			}
		}
		return res;
	}

	/** La misma elección que cultura/detectar del datapack: islas si hay mar cálido cerca, si no, la primera que encaja. */
	@Override
	public boolean culturaDeMontana(int x, int z) {
		int y = Math.max(altura(x, z), nivelMar);
		for (int[] d : new int[][] {{0, 0}, {48, 0}, {-48, 0}, {0, 48}, {0, -48}}) {
			if (bioma(x + d[0], y, z + d[1]).is(ISLAS)) {
				return false;
			}
		}
		Holder<Biome> b = bioma(x, y, z);
		for (int i = 0; i < CULTURAS.length; i++) {
			if (b.is(TAGS_CULTURA[i])) {
				return DE_MONTANA[i];
			}
		}
		return false;
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
