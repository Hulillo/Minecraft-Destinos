package es.hulillo.destinos;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Comer o beber algo caliente te da un rato de Resistencia a la escarcha de Homeostatic
 * (mientras dura, la temperatura del cuerpo no baja). Qué cuenta como caliente lo dicen
 * los tags destinos_core:comida_caliente/{corta,media,larga}. Sin Homeostatic no hace nada.
 */
public final class ComidaCaliente {
	private static final Identifier ESCARCHA = Identifier.fromNamespaceAndPath("homeostatic", "frost_resistance");
	private static final TagKey<Item> CORTA = tag("corta");
	private static final TagKey<Item> MEDIA = tag("media");
	private static final TagKey<Item> LARGA = tag("larga");

	private ComidaCaliente() {}

	private static TagKey<Item> tag(String duracion) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "comida_caliente/" + duracion));
	}

	/** Ticks de calor que da esta comida; 0 si no es caliente. */
	static int ticks(ItemStack comida) {
		if (comida.is(LARGA)) return 4 * 60 * 20;
		if (comida.is(MEDIA)) return 3 * 60 * 20;
		if (comida.is(CORTA)) return 2 * 60 * 20;
		return 0;
	}

	/** Lo llama el mixin justo antes de que el jugador termine de comerse algo. */
	public static void comida(ServerPlayer jugador, ItemStack comida) {
		int ticks = ticks(comida);
		if (ticks <= 0) return;
		BuiltInRegistries.MOB_EFFECT.get(ESCARCHA).ifPresent(efecto -> {
			MobEffectInstance actual = jugador.getEffect(efecto);
			// No acortar un calor que ya dure más (p. ej. la poción larga)
			if (actual != null && actual.getDuration() >= ticks) return;
			jugador.addEffect(new MobEffectInstance(efecto, ticks, 0, false, false, true));
		});
	}
}
