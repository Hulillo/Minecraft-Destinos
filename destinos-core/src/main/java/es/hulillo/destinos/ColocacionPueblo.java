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
 * v0.6.3: dónde se generan los pueblos propios de los ducados (aldeas, minas, aserraderos, puertos, ciudades y
 * puestos de vigía): en el chunk del sitio que MapaTerreno elige para cada uno. Lo usa el grupo de estructuras
 * destinos_core:pueblos (datos del mod, en build.py). Como con las capitales, sin sal no sale ninguno.
 */
public final class ColocacionPueblo extends StructurePlacement {
	public static final MapCodec<ColocacionPueblo> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Vec3i.offsetCodec(16).optionalFieldOf("locate_offset", Vec3i.ZERO).forGetter(ColocacionPueblo::locateOffset),
			StructurePlacement.FrequencyReductionMethod.CODEC.optionalFieldOf("frequency_reduction_method",
					StructurePlacement.FrequencyReductionMethod.DEFAULT).forGetter(ColocacionPueblo::frequencyReductionMethod),
			Codec.floatRange(0.0F, 1.0F).optionalFieldOf("frequency", 1.0F).forGetter(ColocacionPueblo::frequency),
			ExtraCodecs.NON_NEGATIVE_INT.fieldOf("salt").forGetter(ColocacionPueblo::salt),
			StructurePlacement.ExclusionZone.CODEC.optionalFieldOf("exclusion_zone").forGetter(ColocacionPueblo::exclusionZone)
	).apply(i, ColocacionPueblo::new));
	public static final StructurePlacementType<ColocacionPueblo> TIPO = () -> CODEC;

	public ColocacionPueblo(Vec3i locateOffset, StructurePlacement.FrequencyReductionMethod reduccion, float frecuencia,
			int sal, Optional<StructurePlacement.ExclusionZone> exclusion) {
		super(locateOffset, reduccion, frecuencia, sal, exclusion);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "pueblo"), TIPO);
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState estado, int cx, int cz) {
		MapaTerreno.Pueblo p = puebloEn(cx, cz);
		if (p != null) {
			DestinosCore.LOG.info("Destinos Core: toca generar {} en {}, {} (chunk {}, {}) · pedido por {}", p.tipo(), p.x(), p.z(), cx, cz,
					ColocacionCapital.quien());
		}
		return p != null;
	}

	private record Cerca(MapaTerreno mapa, int dcx, int dcz, List<MapaTerreno.Pueblo> pueblos) {
	}

	private static final ThreadLocal<Cerca> CERCA = new ThreadLocal<>();

	/** Los pueblos propios de los 3×3 ducados alrededor de la celda del chunk (cx, cz), una vez por ducado y hilo. */
	private static List<MapaTerreno.Pueblo> cerca(int cx, int cz) {
		MapaTerreno mapa = DestinosCore.mapaActual();
		if (mapa == null) {
			return List.of();
		}
		int dcx = Geo.celdaDucado(cx * 16 + 8);
		int dcz = Geo.celdaDucado(cz * 16 + 8);
		Cerca c = CERCA.get();
		if (c == null || c.mapa() != mapa || c.dcx() != dcx || c.dcz() != dcz) {
			List<MapaTerreno.Pueblo> res = new ArrayList<>();
			for (int ox = -1; ox <= 1; ox++) {
				for (int oz = -1; oz <= 1; oz++) {
					res.addAll(mapa.pueblos(dcx + ox, dcz + oz));
				}
			}
			c = new Cerca(mapa, dcx, dcz, res);
			CERCA.set(c);
		}
		return c.pueblos();
	}

	/** El pueblo propio cuyo sitio cae en el chunk (cx, cz), o null. */
	static MapaTerreno.Pueblo puebloEn(int cx, int cz) {
		for (MapaTerreno.Pueblo p : cerca(cx, cz)) {
			if (p.x() >> 4 == cx && p.z() >> 4 == cz) {
				return p;
			}
		}
		return null;
	}

	/** El pueblo propio a menos de radio chunks (en cada eje) del chunk (cx, cz), o null. */
	static MapaTerreno.Pueblo puebloCerca(int cx, int cz, int radio) {
		for (MapaTerreno.Pueblo p : cerca(cx, cz)) {
			if (Math.abs((p.x() >> 4) - cx) <= radio && Math.abs((p.z() >> 4) - cz) <= radio) {
				return p;
			}
		}
		return null;
	}

	@Override
	public StructurePlacementType<?> type() {
		return TIPO;
	}
}
