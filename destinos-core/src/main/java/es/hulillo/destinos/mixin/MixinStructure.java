package es.hulillo.destinos.mixin;

import com.mojang.datafixers.util.Either;
import es.hulillo.destinos.PueblosCapital;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sin pueblos sueltos ni otras obras (de Minecraft o de mods) encima de una capital o un pueblo propio: primero por
 * dónde empiezan (barato, antes de prepararlas) y luego, ya preparadas, por dónde caen sus piezas.
 */
@Mixin(Structure.class)
public abstract class MixinStructure {
	@Inject(method = "findValidGenerationPoint", at = @At("HEAD"), cancellable = true)
	private void destinos$sinPuebloJuntoACapital(Structure.GenerationContext ctx, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
		if (PueblosCapital.vetado((Structure) (Object) this, ctx)) {
			cir.setReturnValue(Optional.empty());
		}
	}

	@Inject(method = "findValidGenerationPoint", at = @At("RETURN"), cancellable = true)
	private void destinos$sinPiezasEnAsentamiento(Structure.GenerationContext ctx, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
		Optional<Structure.GenerationStub> stub = cir.getReturnValue();
		if (stub == null || stub.isEmpty()) {
			return;
		}
		// las piezas se preparan una vez y se devuelven ya hechas (si no, Minecraft las volvería a preparar)
		StructurePiecesBuilder piezas = stub.get().getPiecesBuilder();
		if (PueblosCapital.choca((Structure) (Object) this, ctx, piezas.build().pieces())) {
			cir.setReturnValue(Optional.empty());
		} else {
			cir.setReturnValue(Optional.of(new Structure.GenerationStub(stub.get().position(), Either.right(piezas))));
		}
	}
}
