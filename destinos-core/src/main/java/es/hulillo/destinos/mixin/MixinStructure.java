package es.hulillo.destinos.mixin;

import es.hulillo.destinos.PueblosCapital;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sin pueblos sueltos (de Minecraft o de mods) encima de una capital: la capital ya trae el suyo. */
@Mixin(Structure.class)
public abstract class MixinStructure {
	@Inject(method = "findValidGenerationPoint", at = @At("HEAD"), cancellable = true)
	private void destinos$sinPuebloJuntoACapital(Structure.GenerationContext ctx, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
		if (PueblosCapital.vetado((Structure) (Object) this, ctx)) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
