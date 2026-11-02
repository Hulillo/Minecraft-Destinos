package es.hulillo.destinos.cliente;

import es.hulillo.destinos.PaqueteMazmorra;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * La barra de la mazmorra: el nombre y una barra fina centrados arriba, pero más abajo que las barras de jefe de
 * vanilla (para no pegarse al borde ni chocar con ellas). Roja en las oleadas y morada con el jefe.
 */
public final class BarraMazmorra {
	private static final int ANCHO = 182;
	/** Altura de la barra: unas líneas por debajo del borde de arriba. */
	private static final int Y = 36;
	private static PaqueteMazmorra actual;
	private static long ultima;
	/** Lo que se ve de la barra, que va detrás del valor de verdad para que baje suave. */
	private static float mostrado = 1;

	private BarraMazmorra() {
	}

	public static void recibe(PaqueteMazmorra p) {
		actual = p.visible() ? p : null;
		ultima = System.currentTimeMillis();
	}

	public static void borra() {
		actual = null;
	}

	public static void pinta(GuiGraphics g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		PaqueteMazmorra p = actual;
		// si el servidor deja de mandarla (te vas lejos, se corta), se quita sola
		if (p == null || mc.options.hideGui || System.currentTimeMillis() - ultima > 5000) {
			mostrado = 1;
			return;
		}
		mostrado += (p.progreso() - mostrado) * 0.15f;
		int x = (g.guiWidth() - ANCHO) / 2;
		int w = mc.font.width(p.nombre());
		g.drawString(mc.font, p.nombre(), (g.guiWidth() - w) / 2, Y - 10, 0xFFFFFFFF, true);
		int lleno = p.jefe() ? 0xFFB050E0 : 0xFFE04040;
		int fondo = p.jefe() ? 0xFF3A1A4A : 0xFF4A1A1A;
		g.fill(x - 1, Y - 1, x + ANCHO + 1, Y + 6, 0xFF000000);
		g.fill(x, Y, x + ANCHO, Y + 5, fondo);
		g.fill(x, Y, x + Math.round(ANCHO * Math.max(0, Math.min(1, mostrado))), Y + 5, lleno);
		g.fill(x, Y, x + Math.round(ANCHO * Math.max(0, Math.min(1, mostrado))), Y + 1, 0x40FFFFFF);
	}
}
