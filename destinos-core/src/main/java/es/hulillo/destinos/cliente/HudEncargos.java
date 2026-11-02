package es.hulillo.destinos.cliente;

import es.hulillo.destinos.PaqueteEncargos;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Arriba a la izquierda, una línea por encargo en curso: qué es, a cuánto está, hacia dónde y cuántos días quedan. */
public final class HudEncargos {
	private static final String[] RUMBOS = {"e", "se", "s", "so", "o", "no", "n", "ne"};
	private static List<PaqueteEncargos.Activo> activos = List.of();

	private HudEncargos() {
	}

	public static void recibe(PaqueteEncargos p) {
		activos = p.activos();
	}

	public static void borra() {
		activos = List.of();
	}

	public static void pinta(GuiGraphics g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (activos.isEmpty() || mc.player == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		int y = 4;
		for (PaqueteEncargos.Activo a : activos) {
			Component linea = linea(mc, a);
			int w = mc.font.width(linea);
			g.fill(2, y - 2, 8 + w, y + 10, 0x80000000);
			g.drawString(mc.font, linea, 5, y, 0xFFFFFFFF, true);
			y += 13;
		}
	}

	private static Component linea(Minecraft mc, PaqueteEncargos.Activo a) {
		double dx = a.x() + 0.5 - mc.player.getX();
		double dz = a.z() + 0.5 - mc.player.getZ();
		int d = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		int rumbo = Math.floorMod((int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4)), 8);
		long dias = Math.max(0, (a.vence() - mc.level.getDayTime() + 23999) / 24000);
		MutableComponent t = Component.literal("◆ ").withColor(0xE8B84A).append(a.titulo());
		t.append(Component.literal(" · ").withColor(0x9E9E9E));
		t.append(Component.translatableWithFallback("destinos.encargo.hud", "%s m %s · %s d", d,
				Component.translatableWithFallback("destinos.rumbo." + RUMBOS[rumbo], RUMBOS[rumbo].toUpperCase()), dias).withColor(0xD0D0D0));
		return t;
	}
}
