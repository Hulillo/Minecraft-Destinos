package es.hulillo.destinos.tornados.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * W&T decide cada día si llueve o hay tormenta (y de sus tormentas fuertes salen los tornados). Fuera: la lluvia la
 * llevan vanilla y Serene Seasons, y los tornados, TornadosEstacion. Solo se aplica si está W&T.
 */
@Pseudo
@Mixin(targets = "net.weather_classic.global.Weather", remap = false)
abstract class MixinTiempoWT {
	@Inject(method = "updateRainAndStormStatusByDay", at = @At("HEAD"), cancellable = true, remap = false)
	private void destinos$sinTiempoPropio(boolean forzar, CallbackInfo ci) {
		ci.cancel();
	}
}
