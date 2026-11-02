package es.hulillo.destinos;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * La moneda del mundo, la misma en todos los reinos: cobre, plata y oro (10 de una = 1 de la siguiente). Hacia abajo
 * se cambia en la mesa de crafteo (1 de plata da 10 de cobre); hacia arriba, usando un montón de 10 o más (se juntan
 * 10 en 1 de la siguiente). Los precios se llevan siempre en cobre.
 */
final class Monedas {
	static Item COBRE;
	static Item PLATA;
	static Item ORO;

	private Monedas() {
	}

	static void registra() {
		ORO = registra("moneda_oro", null);
		PLATA = registra("moneda_plata", ORO);
		COBRE = registra("moneda_cobre", PLATA);
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(e -> {
			e.accept(COBRE);
			e.accept(PLATA);
			e.accept(ORO);
		});
	}

	private static Item registra(String nombre, Item siguiente) {
		ResourceKey<Item> clave = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, nombre));
		return Registry.register(BuiltInRegistries.ITEM, clave, new Moneda(new Item.Properties().setId(clave), siguiente));
	}

	/** Una moneda: usarla con 10 o más en la mano las junta en una de la siguiente. */
	static final class Moneda extends Item {
		private final Item siguiente;

		Moneda(Item.Properties p, Item siguiente) {
			super(p);
			this.siguiente = siguiente;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand mano) {
			ItemStack s = player.getItemInHand(mano);
			if (siguiente == null || s.getCount() < 10) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp) {
				int juntas = s.getCount() / 10;
				s.shrink(juntas * 10);
				da(sp, new ItemStack(siguiente, juntas));
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** Paga una cantidad en cobre con las monedas más gordas posibles. */
	static void paga(ServerPlayer p, int cobre) {
		da(p, new ItemStack(ORO, cobre / 100));
		da(p, new ItemStack(PLATA, cobre / 10 % 10));
		da(p, new ItemStack(COBRE, cobre % 10));
	}

	/** Las pilas de monedas que hacen esa cantidad en cobre (para meterlas en un cofre). */
	static java.util.List<ItemStack> pilas(int cobre) {
		java.util.List<ItemStack> l = new java.util.ArrayList<>();
		if (cobre >= 100) {
			l.add(new ItemStack(ORO, Math.min(64, cobre / 100)));
		}
		if (cobre / 10 % 10 > 0) {
			l.add(new ItemStack(PLATA, cobre / 10 % 10));
		}
		if (cobre % 10 > 0) {
			l.add(new ItemStack(COBRE, cobre % 10));
		}
		return l;
	}

	private static void da(ServerPlayer p, ItemStack s) {
		while (!s.isEmpty()) {
			ItemStack trozo = s.split(s.getMaxStackSize());
			if (!p.getInventory().add(trozo)) {
				p.drop(trozo, false);
			}
		}
	}

	/** "2 oro 3 plata 5 cobre" (sin las que son 0). */
	static Component texto(int cobre) {
		MutableComponent t = Component.empty();
		int[] partes = {cobre / 100, cobre / 10 % 10, cobre % 10};
		String[] claves = {"oro", "plata", "cobre"};
		ChatFormatting[] colores = {ChatFormatting.GOLD, ChatFormatting.GRAY, ChatFormatting.RED};
		boolean algo = false;
		for (int i = 0; i < 3; i++) {
			if (partes[i] == 0 && (algo || i < 2)) {
				continue;
			}
			if (algo) {
				t.append(" ");
			}
			t.append(Component.translatableWithFallback("destinos.moneda." + claves[i], "%s " + claves[i], partes[i]).withStyle(colores[i]));
			algo = true;
		}
		return t;
	}
}
