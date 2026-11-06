package es.hulillo.destinos;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * v0.6.3: un pueblo propio de un ducado (aldea, mina, aserradero, puerto, ciudad o puesto de vigía). Se traza como
 * una capital pequeña, sin castillo (EstructuraCapital con el Plano de su oficio); sale donde dice ColocacionPueblo.
 */
public final class EstructuraPueblo extends EstructuraCapital {
	public static final MapCodec<EstructuraPueblo> CODEC = simpleCodec(EstructuraPueblo::new);
	public static final StructureType<EstructuraPueblo> TIPO = () -> CODEC;

	public EstructuraPueblo(Structure.StructureSettings ajustes) {
		super(ajustes);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "pueblo"), TIPO);
	}

	/** El del pueblo de este chunk; con /place, sin pueblo en el mapa, una aldea. */
	@Override
	protected Optional<Plano> plano(Structure.GenerationContext ctx) {
		MapaTerreno.Pueblo p = ColocacionPueblo.puebloEn(ctx.chunkPos().x, ctx.chunkPos().z);
		return Optional.of(p != null ? Plano.pueblo(p.tipo(), p.dcx(), p.dcz()) : Plano.pueblo(MapaTerreno.TipoPueblo.aldea, Plano.NINGUNO, Plano.NINGUNO));
	}

	@Override
	public StructureType<?> type() {
		return TIPO;
	}
}
