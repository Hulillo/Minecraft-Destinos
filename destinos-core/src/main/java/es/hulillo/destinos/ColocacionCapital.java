package es.hulillo.destinos;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Dónde se generan las capitales: en el chunk del sitio que MapaTerreno elige para cada reino, uno por reino.
 * Lo usa el grupo de estructuras destinos_core:capitales (datos del mod, en build.py).
 *
 * Mientras el mundo no tiene sal (el datapack aún no ha cargado) no sale ninguna: los primeros chunks alrededor
 * del spawn se quedan sin capital, pero el resto del mundo la tiene donde toca.
 */
public final class ColocacionCapital extends StructurePlacement {
	public static final MapCodec<ColocacionCapital> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Vec3i.offsetCodec(16).optionalFieldOf("locate_offset", Vec3i.ZERO).forGetter(ColocacionCapital::locateOffset),
			StructurePlacement.FrequencyReductionMethod.CODEC.optionalFieldOf("frequency_reduction_method",
					StructurePlacement.FrequencyReductionMethod.DEFAULT).forGetter(ColocacionCapital::frequencyReductionMethod),
			Codec.floatRange(0.0F, 1.0F).optionalFieldOf("frequency", 1.0F).forGetter(ColocacionCapital::frequency),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("salt").forGetter(ColocacionCapital::salt),
			StructurePlacement.ExclusionZone.CODEC.optionalFieldOf("exclusion_zone").forGetter(ColocacionCapital::exclusionZone)
	).apply(i, ColocacionCapital::new));
	public static final StructurePlacementType<ColocacionCapital> TIPO = () -> CODEC;
	private static volatile boolean avisado;

	public ColocacionCapital(Vec3i locateOffset, StructurePlacement.FrequencyReductionMethod reduccion, float frecuencia,
			int sal, Optional<StructurePlacement.ExclusionZone> exclusion) {
		super(locateOffset, reduccion, frecuencia, sal, exclusion);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "capital"), TIPO);
	}

	/**
	 * El sitio de la capital está a menos de 200 bloques del centro de su ducado, así que su ducado es el del chunk
	 * o uno de los 8 de alrededor: se mira la capital del reino de cada uno.
	 */
	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState estado, int cx, int cz) {
		MapaTerreno.Capital capital = capitalEn(cx, cz);
		if (capital != null) {
			DestinosCore.LOG.info("Destinos Core: toca generar la capital (nivel {}) en {}, {}", capital.nivel(), capital.x(),
					capital.z());
		}
		return capital != null;
	}

	private record Cerca(MapaTerreno mapa, int dcx, int dcz, List<MapaTerreno.Capital> capitales) {
	}

	private static final ThreadLocal<Cerca> CERCA = new ThreadLocal<>();

	/** Las capitales de los reinos de los ducados de alrededor de (dcx, dcz). */
	private static List<MapaTerreno.Capital> capitalesCerca(MapaTerreno mapa, int dcx, int dcz) {
		List<MapaTerreno.Capital> res = new ArrayList<>();
		for (Geo.Reino r : Geo.reinosCerca(dcx, dcz, mapa.sal())) {
			MapaTerreno.Capital capital = mapa.capital(r.kx(), r.kz());
			if (capital != null) {
				res.add(capital);
			}
		}
		return res;
	}

	/** La capital cuyo sitio cae en el chunk (cx, cz), o null si en ese chunk no va ninguna. */
	static MapaTerreno.Capital capitalEn(int cx, int cz) {
		MapaTerreno mapa = DestinosCore.mapaActual();
		if (mapa == null) {
			if (!avisado) {
				avisado = true;
				DestinosCore.LOG.info("Destinos Core: se generan chunks antes de que el datapack ponga la sal; ahí no habrá capitales");
			}
			return null;
		}
		int dcx = Geo.celdaDucado(cx * 16 + 8);
		int dcz = Geo.celdaDucado(cz * 16 + 8);
		// los chunks de un mismo ducado miran las mismas capitales: se buscan una vez por ducado y hilo
		Cerca cerca = CERCA.get();
		if (cerca == null || cerca.mapa() != mapa || cerca.dcx() != dcx || cerca.dcz() != dcz) {
			cerca = new Cerca(mapa, dcx, dcz, capitalesCerca(mapa, dcx, dcz));
			CERCA.set(cerca);
		}
		for (MapaTerreno.Capital capital : cerca.capitales()) {
			if (capital.x() >> 4 == cx && capital.z() >> 4 == cz) {
				return capital;
			}
		}
		return null;
	}

	@Override
	public StructurePlacementType<?> type() {
		return TIPO;
	}
}
