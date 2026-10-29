package es.hulillo.destinos;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/**
 * Los pueblos de los que sale una capital: cuáles se prueban en cada sitio y su copia agrandada. La copia se hace
 * con el codec de las estructuras (JSON de ida y vuelta) para no depender de los campos internos de
 * JigsawStructure, que cambian entre versiones.
 */
public final class PueblosCapital {
	/** Trozos de nombre de los pueblos (y plazas, y casas) abandonados, zombi o en ruinas. */
	private static final String[] ABANDONADO = {"zombie", "abandon", "ruin", "destroyed", "burn", "haunt", "ghost",
		"derelict"};
	/** Estructuras con "village" en el nombre que no son pueblos de verdad. */
	private static final String[] NO_PUEBLO = {"villager", "outcast", "remnant", "ruin", "abandon", "sparse", "zombie",
		"camp", "pillager", "illager", "outpost", "destroyed", "burn", "haunt", "ghost", "ancient", "lost", "tiny",
		"small", "hamlet", "derelict"};
	/** Si ningún pueblo del bioma cabe, otro de verdad antes que una corona vacía: los de vanilla, en este orden. */
	private static final String[] VANILLA = {"village_plains", "village_taiga", "village_savanna", "village_snowy",
		"village_desert"};

	/** Pueblos agrandados ya preparados (por pueblo, profundidad y distancia), para los registros del mundo abierto. */
	private static final Map<String, Optional<Structure>> COPIAS = new ConcurrentHashMap<>();
	private static volatile RegistryAccess registrosCopias;

	private PueblosCapital() {
	}

	/**
	 * Los pueblos que se prueban para la capital del chunk (cx, cz), en orden: los que pueden salir en su bioma (el
	 * que toca en este chunk primero; algunos pueblos de mods piden un sitio muy concreto), luego el vanilla del
	 * clima, luego los demás vanilla y al final cualquier otro pueblo.
	 */
	static List<Holder.Reference<Structure>> candidatos(RegistryAccess registros, Holder<Biome> bioma, int cx, int cz, long semilla) {
		Registry<Structure> estructuras = registros.lookupOrThrow(Registries.STRUCTURE);
		Comparator<Holder.Reference<Structure>> porNombre = Comparator.comparing(h -> String.valueOf(estructuras.getKey(h.value())));
		List<Holder.Reference<Structure>> delBioma = new ArrayList<>();
		List<Holder.Reference<Structure>> resto = new ArrayList<>();
		estructuras.listElements().forEach(h -> {
			if (h.value() instanceof JigsawStructure && esPueblo(estructuras, h)) {
				(h.value().biomes().contains(bioma) ? delBioma : resto).add(h);
			}
		});
		List<Holder.Reference<Structure>> orden = new ArrayList<>();
		if (!delBioma.isEmpty()) {
			delBioma.sort(porNombre);
			int i = Math.floorMod(Geo.hash(cx, cz, (int) semilla), delBioma.size());
			for (int k = 0; k < delBioma.size(); k++) {
				orden.add(delBioma.get((i + k) % delBioma.size()));
			}
		}
		float temperatura = bioma.value().getBaseTemperature();
		String porDefecto = temperatura < 0.2F ? "village_snowy" : temperatura > 1.5F ? "village_desert" : "village_plains";
		agregaVanilla(estructuras, porDefecto, orden);
		for (String otro : VANILLA) {
			agregaVanilla(estructuras, otro, orden);
		}
		resto.removeIf(orden::contains);
		resto.sort(porNombre);
		orden.addAll(resto);
		return orden;
	}

	private static void agregaVanilla(Registry<Structure> estructuras, String nombre, List<Holder.Reference<Structure>> orden) {
		estructuras.get(Identifier.withDefaultNamespace(nombre)).filter(h -> !orden.contains(h)).ifPresent(orden::add);
	}

	/** A cuántos chunks de una capital no puede empezar un pueblo suelto (la capital llega a unos 8 de su centro). */
	private static final int SIN_PUEBLOS = 14;

	/**
	 * Si un pueblo suelto (del tag de pueblos o con "village" en el nombre, de cualquier mod) no debe salir aquí por
	 * caer encima de una capital. Los pueblos agrandados de la propia capital no están en el registro: nunca se vetan.
	 */
	public static boolean vetado(Structure estructura, Structure.GenerationContext ctx) {
		if (estructura instanceof EstructuraCapital) {
			return false;
		}
		Registry<Structure> estructuras = ctx.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Identifier id = estructuras.getKey(estructura);
		if (id == null || id.getNamespace().equals(DestinosCore.MOD_ID)) {
			return false;
		}
		if (!estructuras.wrapAsHolder(estructura).is(StructureTags.VILLAGE) && !id.getPath().contains("village")) {
			return false;
		}
		return ColocacionCapital.capitalCerca(ctx.chunkPos().x, ctx.chunkPos().z, SIN_PUEBLOS) != null;
	}

	/**
	 * Un pueblo de verdad para copiar: del tag de pueblos o con "village" en el nombre, pero no los campamentos,
	 * ruinas o pueblos dispersos que algunos mods llaman así (structory:outcast_villager_*,
	 * nova_structures:remnant_*, towns_and_towers:village_sparse_*…).
	 */
	private static boolean esPueblo(Registry<Structure> estructuras, Holder.Reference<Structure> h) {
		Identifier id = estructuras.getKey(h.value());
		if (id == null || id.getNamespace().equals(DestinosCore.MOD_ID)) {
			return false;
		}
		String ruta = id.getPath();
		return !contieneAlguna(ruta, NO_PUEBLO) && (h.is(StructureTags.VILLAGE) || ruta.matches("(.*[/_])?villages?([/_].*)?"));
	}

	/** Si la pieza es de un pueblo abandonado (por el nombre de su plantilla). */
	static boolean abandonada(StructurePiece pieza) {
		return pieza instanceof PoolElementStructurePiece p && contieneAlguna(p.getElement().toString(), ABANDONADO);
	}

	/** Si el nombre (de una pieza o una plantilla) es de algo abandonado, zombi o en ruinas. */
	static boolean abandonado(String nombre) {
		return contieneAlguna(nombre, ABANDONADO);
	}

	private static boolean contieneAlguna(String texto, String[] trozos) {
		String t = texto.toLowerCase(Locale.ROOT);
		for (String trozo : trozos) {
			if (t.contains(trozo)) {
				return true;
			}
		}
		return false;
	}

	/** El pueblo con más profundidad, el alcance dado y válido en cualquier bioma del mundo normal. */
	static Optional<Structure> agrandado(RegistryAccess registros, Holder.Reference<Structure> pueblo, int mas, int alcance) {
		if (registrosCopias != registros) {
			COPIAS.clear();
			registrosCopias = registros;
		}
		Identifier k = registros.lookupOrThrow(Registries.STRUCTURE).getKey(pueblo.value());
		return COPIAS.computeIfAbsent(k + "|" + mas + "|" + alcance, clave -> copia(registros, k, pueblo.value(), mas, alcance));
	}

	private static Optional<Structure> copia(RegistryAccess registros, Identifier k, Structure pueblo, int mas, int alcance) {
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registros);
		Optional<JsonElement> json = Structure.DIRECT_CODEC.encodeStart(ops, pueblo).result();
		if (json.isEmpty() || !json.get().isJsonObject()) {
			DestinosCore.LOG.warn("Destinos Core: no se ha podido copiar el pueblo {} para una capital", k);
			return Optional.empty();
		}
		JsonObject o = json.get().getAsJsonObject().deepCopy();
		int profundidad = Math.min(20, (o.has("size") ? o.get("size").getAsInt() : 6) + mas);
		o.addProperty("size", profundidad);
		JsonElement distancia = o.get("max_distance_from_center");
		if (distancia != null && distancia.isJsonObject()) {
			distancia.getAsJsonObject().addProperty("horizontal", alcance);
		} else {
			o.addProperty("max_distance_from_center", alcance);
		}
		// el bioma ya lo ha mirado findGenerationPoint (o es el pueblo de respaldo): que no lo vuelva a mirar
		o.addProperty("biomes", "#destinos_core:capital");
		sinPlazasAbandonadas(registros, ops, o, k);
		Optional<Structure> copia = Structure.DIRECT_CODEC.parse(ops, o).resultOrPartial(error ->
				DestinosCore.LOG.warn("Destinos Core: el pueblo {} agrandado no es válido ({}); la capital usará el normal", k, error));
		if (copia.isEmpty()) {
			return Optional.of(pueblo);
		}
		DestinosCore.LOG.info("Destinos Core: capitales con el estilo de {} (profundidad {}, {} bloques)", k, profundidad, alcance);
		return copia;
	}

	/**
	 * Cambia la lista de plazas de inicio del pueblo copiado por la misma sin las abandonadas (los pueblos zombi de
	 * vanilla y los abandonados de mods empiezan por una plaza así): la capital no puede salir abandonada nunca, en
	 * vez de tirar otra vez cuando sale. La lista va dentro de la copia, sin registrar nada.
	 */
	private static void sinPlazasAbandonadas(RegistryAccess registros, RegistryOps<JsonElement> ops, JsonObject o, Identifier k) {
		JsonElement inicio = o.get("start_pool");
		if (inicio == null || !inicio.isJsonPrimitive()) {
			return;
		}
		Identifier idPool = Identifier.tryParse(inicio.getAsString());
		Optional<Holder.Reference<StructureTemplatePool>> pool = idPool == null ? Optional.empty()
				: registros.lookupOrThrow(Registries.TEMPLATE_POOL).get(idPool);
		if (pool.isEmpty()) {
			return;
		}
		Optional<JsonElement> json = StructureTemplatePool.DIRECT_CODEC.encodeStart(ops, pool.get().value()).result();
		if (json.isEmpty() || !json.get().isJsonObject() || !json.get().getAsJsonObject().has("elements")) {
			return;
		}
		JsonObject limpio = json.get().getAsJsonObject().deepCopy();
		JsonArray elementos = limpio.getAsJsonArray("elements");
		JsonArray buenos = new JsonArray();
		for (JsonElement e : elementos) {
			if (!contieneAlguna(e.toString(), ABANDONADO)) {
				buenos.add(e);
			}
		}
		if (buenos.isEmpty() || buenos.size() == elementos.size()) {
			return;
		}
		limpio.add("elements", buenos);
		o.add("start_pool", limpio);
		DestinosCore.LOG.info("Destinos Core: capitales de {} sin {} plazas abandonadas", k, elementos.size() - buenos.size());
	}
}
