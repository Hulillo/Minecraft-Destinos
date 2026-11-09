package es.hulillo.destinos.cliente.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import es.hulillo.destinos.DestinosCore;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Smooth Mount da por hecho que todo lo que tiene modelo de jugador es un jugador: con los maniquís de Humanoid
 * Villagers (los aldeanos con cara de persona), sobre todo si van montados en una barca, no encuentra su animación y
 * revienta el juego entero al dibujarlos. Aquí, si la postura del modelo falla dentro de Smooth Mount, ese aldeano
 * se dibuja con la postura que tenga y el juego sigue; cualquier otro fallo se deja pasar como siempre.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinPoseSegura {
	@Unique
	private static boolean destinos$avisado;

	@WrapOperation(method = "submit", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)V"))
	private void destinos$poseSegura(EntityModel<?> modelo, EntityRenderState estado, Operation<Void> original) {
		try {
			original.call(modelo, estado);
		} catch (RuntimeException fallo) {
			if (!deSmoothMount(fallo)) {
				throw fallo;
			}
			if (!destinos$avisado) {
				destinos$avisado = true;
				DestinosCore.LOG.warn("Destinos Core: Smooth Mount ha fallado al animar un modelo que no es un jugador; se dibuja sin su animación", fallo);
			}
		}
	}

	@Unique
	private static boolean deSmoothMount(Throwable t) {
		for (StackTraceElement e : t.getStackTrace()) {
			if (e.getClassName().contains("smoothmount")) {
				return true;
			}
		}
		return false;
	}
}
