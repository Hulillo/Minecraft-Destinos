package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * Los bichos de cada ducado para el modo Fauna del mapa político: se miran los biomas en el centro del ducado y
 * alrededor (con el generador, sin cargar chunks) y se juntan sus listas de aparición, así que entran solos los
 * animales de los mods. Se apunta cada ducado una vez por partida. Solo desde el hilo del servidor.
 */
final class FaunaDucados {
	/** Puntos que se miran: el centro y cuatro alrededor a esta distancia. */
	private static final int ALREDEDOR = 320;
	private static final int MAX_ANIMALES = 5;
	private static final int MAX_PELIGROS = 3;
	/** Monstruos de todas partes que no merece la pena contar en cada ducado. */
	private static final Set<String> COMUNES = Set.of("minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:spider",
			"minecraft:enderman", "minecraft:witch", "minecraft:slime", "minecraft:zombie_villager", "minecraft:drowned");

	/** Animales y peligros de un ducado, por nombre de entidad (el cliente lo traduce). */
	record Fauna(List<Component> animales, List<Component> peligros) {
	}

	private final Map<Long, Fauna> hechas = new HashMap<>();
	private final BiomeSource biomas;
	private final Climate.Sampler clima;
	private final int y;
	private final int sal;

	private FaunaDucados(ServerLevel level, int sal) {
		this.biomas = level.getChunkSource().getGenerator().getBiomeSource();
		this.clima = level.getChunkSource().randomState().sampler();
		this.y = level.getChunkSource().getGenerator().getSeaLevel() + 8;
		this.sal = sal;
	}

	private static FaunaDucados actual;

	static FaunaDucados de(ServerLevel level, int sal) {
		if (actual == null || actual.sal != sal) {
			actual = new FaunaDucados(level, sal);
		}
		return actual;
	}

	static void olvida() {
		actual = null;
	}

	Fauna de(int dcx, int dcz) {
		return hechas.computeIfAbsent(Geo.clave(dcx, dcz), k -> calcula(dcx, dcz));
	}

	private Fauna calcula(int dcx, int dcz) {
		int[] c = Geo.centroDucado(dcx, dcz, sal);
		int[][] puntos = {{0, 0}, {ALREDEDOR, 0}, {-ALREDEDOR, 0}, {0, ALREDEDOR}, {0, -ALREDEDOR}};
		Map<EntityType<?>, Integer> animales = new LinkedHashMap<>();
		Map<EntityType<?>, Integer> peligros = new LinkedHashMap<>();
		for (int[] p : puntos) {
			Holder<Biome> b = biomas.getNoiseBiome(QuartPos.fromBlock(c[0] + p[0]), QuartPos.fromBlock(y), QuartPos.fromBlock(c[1] + p[1]), clima);
			MobSpawnSettings ajustes = b.value().getMobSettings();
			suma(animales, ajustes, MobCategory.CREATURE);
			suma(peligros, ajustes, MobCategory.MONSTER);
		}
		return new Fauna(mejores(animales, MAX_ANIMALES, false), mejores(peligros, MAX_PELIGROS, true));
	}

	private static void suma(Map<EntityType<?>, Integer> cuenta, MobSpawnSettings ajustes, MobCategory categoria) {
		for (Weighted<MobSpawnSettings.SpawnerData> w : ajustes.getMobs(categoria).unwrap()) {
			cuenta.merge(w.value().type(), w.weight(), Integer::sum);
		}
	}

	/** Los que más salen; en los peligros, quitando los monstruos de siempre. */
	private static List<Component> mejores(Map<EntityType<?>, Integer> cuenta, int max, boolean sinComunes) {
		List<Map.Entry<EntityType<?>, Integer>> l = new ArrayList<>(cuenta.entrySet());
		l.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		List<Component> res = new ArrayList<>();
		for (Map.Entry<EntityType<?>, Integer> e : l) {
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getKey());
			if (sinComunes && COMUNES.contains(id.toString())) {
				continue;
			}
			res.add(e.getKey().getDescription());
			if (res.size() >= max) {
				break;
			}
		}
		return res;
	}
}
