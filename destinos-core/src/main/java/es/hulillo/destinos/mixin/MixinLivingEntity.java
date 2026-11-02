package es.hulillo.destinos.mixin;

import es.hulillo.destinos.ComidaCaliente;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Comida caliente: al terminar de comer o beber, ComidaCaliente decide si da calor. */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {
	@Shadow protected ItemStack useItem;

	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void destinos$comidaCaliente(CallbackInfo ci) {
		if (!((Object) this instanceof ServerPlayer jugador)) return;
		if (!jugador.isUsingItem() || useItem.isEmpty()) return;
		// Igual que Minecraft: si cambió el objeto de la mano, no se come nada
		if (!ItemStack.matches(useItem, jugador.getItemInHand(jugador.getUsedItemHand()))) return;
		ComidaCaliente.comida(jugador, useItem);
	}
}
