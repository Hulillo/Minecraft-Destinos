package es.hulillo.destinos.tornados.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Los cúmulos de tormenta de W&T sueltan tornados sin mirar estación ni bioma: fuera. Solo se aplica si está W&T. */
@Pseudo
@Mixin(targets = "net.weather_classic.global.Wind", remap = false)
abstract class MixinCumulosWT {
	@Inject(method = "scheduleCluster", at = @At("HEAD"), cancellable = true, remap = false)
	private void destinos$sinCumulos(boolean rapido, CallbackInfo ci) {
		ci.cancel();
	}
}
