package es.hulillo.destinos.cliente.diorama.mixin;

import dev.diorama.render.MapLevel;
import dev.diorama.render.MapViewport;
import es.hulillo.destinos.cliente.diorama.CapaDiorama;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diorama dibuja sus casillas y luego la niebla: los reinos van en medio. Solo se aplica si está Diorama.
 * En este paquete solo puede haber mixins: Mixin no deja cargar otras clases de un paquete de mixins.
 */
@Mixin(value = MapViewport.class, remap = false)
abstract class MixinMapViewport {
	@Inject(method = "drawFog", at = @At("HEAD"))
	private void destinos$pintaReinos(GuiGraphics graphics, MapLevel level, MapViewport.View view, CallbackInfo ci) {
		CapaDiorama.dibuja(graphics, view);
	}
}
