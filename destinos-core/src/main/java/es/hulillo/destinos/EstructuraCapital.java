package es.hulillo.destinos;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/**
 * La capital de un reino: el pueblo que tocaría en ese bioma (de vanilla o de cualquier mod, como Towns and
 * Towers), pero más grande. No tiene piezas propias: copia la estructura de pueblo, le sube la profundidad y la
 * distancia máxima, y la genera. Sale donde dice ColocacionCapital.
 *
 * La copia se hace con el codec de las estructuras (JSON de ida y vuelta) para no depender de los campos internos
 * de JigsawStructure, que cambian entre versiones.
 */
public final class EstructuraCapital extends Structure {
	public static final MapCodec<EstructuraCapital> CODEC = simpleCodec(EstructuraCapital::new);
	public static final StructureType<EstructuraCapital> TIPO = () -> CODEC;
	/** Profundidad extra sobre la del pueblo (el máximo del juego es 20) y hasta dónde llegan sus calles. */
	private static final int MAS_PROFUNDIDAD = 4;
	private static final int DISTANCIA = 128;

	/** Pueblos agrandados ya preparados, por estructura de pueblo, para los registros del mundo abierto. */
	private static final Map<Identifier, Optional<Structure>> COPIAS = new ConcurrentHashMap<>();
	private static volatile RegistryAccess registrosCopias;

	public EstructuraCapital(Structure.StructureSettings ajustes) {
		super(ajustes);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "capital"), TIPO);
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext ctx) {
		int x = ctx.chunkPos().getMiddleBlockX();
		int z = ctx.chunkPos().getMiddleBlockZ();
		int y = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(),
				ctx.randomState());
		Holder<Biome> bioma = ctx.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
				QuartPos.fromBlock(z), ctx.randomState().sampler());
		Registry<Structure> estructuras = ctx.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		// los pueblos que pueden salir en este bioma; si ninguno, uno vanilla según el clima
		List<Holder.Reference<Structure>> pueblos = new ArrayList<>();
		estructuras.listElements().forEach(h -> {
			if (h.value() instanceof JigsawStructure && esPueblo(estructuras, h) && h.value().biomes().contains(bioma)) {
				pueblos.add(h);
			}
		});
		Holder.Reference<Structure> pueblo;
		if (!pueblos.isEmpty()) {
			pueblos.sort((a, b) -> String.valueOf(estructuras.getKey(a.value())).compareTo(String.valueOf(estructuras.getKey(b.value()))));
			int i = Math.floorMod(Geo.hash(ctx.chunkPos().x, ctx.chunkPos().z, (int) ctx.seed()), pueblos.size());
			pueblo = pueblos.get(i);
		} else {
			float temperatura = bioma.value().getBaseTemperature();
			String porDefecto = temperatura < 0.2F ? "village_snowy" : temperatura > 1.5F ? "village_desert" : "village_plains";
			Optional<Holder.Reference<Structure>> h = estructuras.get(Identifier.withDefaultNamespace(porDefecto));
			if (h.isEmpty()) {
				return Optional.empty();
			}
			pueblo = h.get();
		}
		Optional<Structure> grande = agrandado(ctx.registryAccess(), estructuras.getKey(pueblo.value()), pueblo.value());
		return grande.flatMap(s -> s.findValidGenerationPoint(ctx));
	}

	private static boolean esPueblo(Registry<Structure> estructuras, Holder.Reference<Structure> h) {
		Identifier id = estructuras.getKey(h.value());
		return id != null && !id.getNamespace().equals(DestinosCore.MOD_ID)
				&& (h.is(StructureTags.VILLAGE) || id.getPath().contains("village"));
	}

	/** El pueblo con más profundidad, más alcance y válido en cualquier bioma del mundo normal. */
	private static Optional<Structure> agrandado(RegistryAccess registros, Identifier k, Structure pueblo) {
		if (registrosCopias != registros) {
			COPIAS.clear();
			registrosCopias = registros;
		}
		return COPIAS.computeIfAbsent(k, clave -> {
			RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registros);
			Optional<JsonElement> json = Structure.DIRECT_CODEC.encodeStart(ops, pueblo).result();
			if (json.isEmpty() || !json.get().isJsonObject()) {
				DestinosCore.LOG.warn("Destinos Core: no se ha podido copiar el pueblo {} para una capital", k);
				return Optional.empty();
			}
			JsonObject o = json.get().getAsJsonObject().deepCopy();
			int profundidad = o.has("size") ? o.get("size").getAsInt() : 6;
			o.addProperty("size", Math.min(20, profundidad + MAS_PROFUNDIDAD));
			JsonElement distancia = o.get("max_distance_from_center");
			if (distancia != null && distancia.isJsonObject()) {
				distancia.getAsJsonObject().addProperty("horizontal", DISTANCIA);
			} else {
				o.addProperty("max_distance_from_center", DISTANCIA);
			}
			// el bioma ya lo ha mirado findGenerationPoint (o es el pueblo de respaldo): que no lo vuelva a mirar
			o.addProperty("biomes", "#minecraft:is_overworld");
			Optional<Structure> copia = Structure.DIRECT_CODEC.parse(ops, o).result();
			if (copia.isEmpty()) {
				DestinosCore.LOG.warn("Destinos Core: el pueblo {} agrandado no es válido; la capital usará el normal", k);
				return Optional.of(pueblo);
			}
			DestinosCore.LOG.info("Destinos Core: capitales con el estilo de {} (profundidad {})", k,
					Math.min(20, profundidad + MAS_PROFUNDIDAD));
			return copia;
		});
	}

	@Override
	public StructureType<?> type() {
		return TIPO;
	}
}
