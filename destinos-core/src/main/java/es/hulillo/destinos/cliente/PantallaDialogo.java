package es.hulillo.destinos.cliente;

import es.hulillo.destinos.AspectoPersona;
import es.hulillo.destinos.PaqueteDialogo;
import es.hulillo.destinos.PaqueteRespuesta;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;

/**
 * v0.7.1 La corte: hablando con alguien. Su cara (de su skin), su nombre y cargo, lo que dice y lo que se le puede
 * contestar. Cada respuesta va al servidor, que contesta con otro PaqueteDialogo (y esta pantalla se rehace).
 */
public final class PantallaDialogo extends Screen {
	private static final int TINTA = 0xFF3B2A1A;
	private static final int TINTA_SUAVE = 0xFF6B5640;
	private static final int PERGAMINO = 0xFFEADBB4;
	private static final int MARCO = 0xFF5A3D22;
	private static final int RAYA = 0xFFC9B68C;
	/** Lado de cada píxel de la cara. */
	private static final int PIXEL = 6;
	private static final int CARA = 8 * PIXEL;

	private final PaqueteDialogo datos;
	private final int[] skin;
	private List<FormattedCharSequence> lineas = List.of();
	private int x0;
	private int y0;
	private int ancho;
	private int alto;

	private PantallaDialogo(PaqueteDialogo datos) {
		super(datos.nombre());
		this.datos = datos;
		this.skin = AspectoPersona.pixeles(datos.aspecto());
	}

	public static void abre(PaqueteDialogo p) {
		Minecraft.getInstance().setScreen(new PantallaDialogo(p));
	}

	@Override
	protected void init() {
		ancho = Math.min(320, width - 20);
		lineas = font.split(datos.texto(), ancho - 24);
		alto = 12 + CARA + 10 + lineas.size() * 10 + 8 + datos.opciones().size() * 22 + 6;
		x0 = (width - ancho) / 2;
		y0 = Math.max(4, (height - alto) / 2);
		int y = y0 + 12 + CARA + 10 + lineas.size() * 10 + 8;
		for (PaqueteDialogo.Opcion o : datos.opciones()) {
			addRenderableWidget(Button.builder(o.texto(), b -> elige(o.clave())).bounds(x0 + 12, y, ancho - 24, 20).build());
			y += 22;
		}
	}

	private void elige(String clave) {
		if (ClientPlayNetworking.canSend(PaqueteRespuesta.TIPO)) {
			ClientPlayNetworking.send(new PaqueteRespuesta(datos.entidad(), clave));
		}
		if ("adios".equals(clave)) {
			onClose();
		}
	}

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
		super.renderBackground(g, mx, my, pt);
		g.fill(x0 - 3, y0 - 3, x0 + ancho + 3, y0 + alto + 3, MARCO);
		g.fill(x0, y0, x0 + ancho, y0 + alto, PERGAMINO);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		super.render(g, mx, my, pt);
		int cx = x0 + 12;
		int cy = y0 + 12;
		g.fill(cx - 2, cy - 2, cx + CARA + 2, cy + CARA + 2, MARCO);
		// la cara de la skin (8..15, 8..15) y encima lo que lleve en la capa del sombrero (corona, pelo…)
		for (int j = 0; j < 8; j++) {
			for (int i = 0; i < 8; i++) {
				int c = skin[(8 + j) * 64 + 8 + i];
				int s = skin[(8 + j) * 64 + 40 + i];
				if ((s >>> 24) != 0) {
					c = s;
				}
				g.fill(cx + i * PIXEL, cy + j * PIXEL, cx + (i + 1) * PIXEL, cy + (j + 1) * PIXEL, c);
			}
		}
		int tx = cx + CARA + 12;
		g.drawString(font, datos.nombre(), tx, cy + 10, TINTA, false);
		g.drawString(font, datos.cargo(), tx, cy + 24, TINTA_SUAVE, false);
		int y = cy + CARA + 8;
		g.fill(x0 + 8, y - 3, x0 + ancho - 8, y - 2, RAYA);
		for (FormattedCharSequence l : lineas) {
			g.drawString(font, l, cx, y + 2, TINTA, false);
			y += 10;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
