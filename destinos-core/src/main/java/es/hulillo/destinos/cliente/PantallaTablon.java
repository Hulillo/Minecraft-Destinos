package es.hulillo.destinos.cliente;

import es.hulillo.destinos.PaqueteAccionEncargo;
import es.hulillo.destinos.PaqueteTablon;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * La pantalla del tablón: un pergamino con los encargos del día (pestaña "Tablón") y los que llevas (pestaña "En
 * curso"). Coger o dejar uno se pide al servidor, que contesta con el tablón ya cambiado.
 */
public final class PantallaTablon extends Screen {
	private static final int TINTA = 0xFF3B2A1A;
	private static final int TINTA_SUAVE = 0xFF6B5640;
	private static final int PERGAMINO = 0xFFEADBB4;
	private static final int MARCO = 0xFF5A3D22;
	private static final int RAYA = 0xFFC9B68C;

	private final PaqueteTablon datos;
	private boolean enCurso;
	private int desplaza;
	private int x0;
	private int y0;
	private int ancho;
	private int alto;
	private int arriba;
	private int abajo;

	private PantallaTablon(PaqueteTablon datos, boolean enCurso, int desplaza) {
		super(datos.cabecera());
		this.datos = datos;
		this.enCurso = enCurso;
		this.desplaza = desplaza;
	}

	/** Llega un tablón del servidor: se abre, o se refresca si ya estaba abierto (sin cambiar de pestaña). */
	public static void abre(PaqueteTablon p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen instanceof PantallaTablon ya && ya.datos.pos().equals(p.pos())) {
			mc.setScreen(new PantallaTablon(p, ya.enCurso, ya.desplaza));
		} else {
			mc.setScreen(new PantallaTablon(p, false, 0));
		}
	}

	private List<PaqueteTablon.Linea> lineas() {
		return enCurso ? datos.activos() : datos.ofertas();
	}

	private int anchoTexto() {
		return ancho - 84;
	}

	private int altoLinea(PaqueteTablon.Linea l) {
		return 12 + font.split(l.texto(), anchoTexto()).size() * 9 + font.split(l.detalle(), anchoTexto()).size() * 9 + 8;
	}

	private int altoTotal() {
		int h = 0;
		for (PaqueteTablon.Linea l : lineas()) {
			h += altoLinea(l);
		}
		return h;
	}

	@Override
	protected void init() {
		ancho = Math.min(400, width - 20);
		alto = Math.min(height - 20, 320);
		x0 = (width - ancho) / 2;
		y0 = (height - alto) / 2;
		arriba = y0 + 46;
		abajo = y0 + alto - 28;
		desplaza = Math.max(0, Math.min(desplaza, altoTotal() - (abajo - arriba)));
		Button tablon = addRenderableWidget(Button.builder(Component.translatableWithFallback("destinos.encargo.pestana.tablon", "Tablón"),
				b -> cambia(false)).bounds(x0 + 8, abajo + 6, 80, 18).build());
		Button curso = addRenderableWidget(Button.builder(Component.translatableWithFallback("destinos.encargo.pestana.curso",
				"En curso (%s/%s)", datos.activos().size(), datos.maxActivos()), b -> cambia(true)).bounds(x0 + 92, abajo + 6, 110, 18).build());
		tablon.active = enCurso;
		curso.active = !enCurso;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x0 + ancho - 68, abajo + 6, 60, 18).build());
		List<PaqueteTablon.Linea> l = lineas();
		boolean lleno = datos.activos().size() >= datos.maxActivos();
		int y = arriba - desplaza;
		for (int i = 0; i < l.size(); i++) {
			PaqueteTablon.Linea linea = l.get(i);
			if (y >= arriba && y + 20 <= abajo && !linea.cogida()) {
				int indice = i;
				Button b = addRenderableWidget(Button.builder(enCurso
								? Component.translatableWithFallback("destinos.encargo.dejar", "Dejar")
								: Component.translatableWithFallback("destinos.encargo.coger", "Coger"),
						x -> ClientPlayNetworking.send(new PaqueteAccionEncargo(!enCurso, datos.pos(), indice)))
						.bounds(x0 + ancho - 68, y + 4, 60, 16).build());
				b.active = enCurso || !lleno;
			}
			y += altoLinea(linea);
		}
	}

	private void cambia(boolean curso) {
		enCurso = curso;
		desplaza = 0;
		rebuildWidgets();
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		int max = Math.max(0, altoTotal() - (abajo - arriba));
		int nuevo = Math.max(0, Math.min(max, desplaza - (int) (sy * 18)));
		if (nuevo != desplaza) {
			desplaza = nuevo;
			rebuildWidgets();
		}
		return true;
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
		centrado(g, datos.cabecera(), y0 + 8, TINTA);
		centrado(g, datos.subtitulo(), y0 + 20, TINTA_SUAVE);
		centrado(g, datos.reputacion(), y0 + 31, TINTA_SUAVE);
		g.fill(x0 + 8, arriba - 3, x0 + ancho - 8, arriba - 2, RAYA);
		g.fill(x0 + 8, abajo + 1, x0 + ancho - 8, abajo + 2, RAYA);
		List<PaqueteTablon.Linea> l = lineas();
		if (l.isEmpty()) {
			centrado(g, enCurso
					? Component.translatableWithFallback("destinos.encargo.pantalla.ninguno", "No llevas ningún encargo.")
					: Component.translatableWithFallback("destinos.encargo.pantalla.vacio", "Hoy no hay nada clavado en el tablón."),
					arriba + 20, TINTA_SUAVE);
			return;
		}
		g.enableScissor(x0, arriba, x0 + ancho, abajo);
		int y = arriba - desplaza;
		for (PaqueteTablon.Linea linea : l) {
			int h = altoLinea(linea);
			if (y + h >= arriba && y <= abajo) {
				pinta(g, linea, y);
			}
			y += h;
		}
		g.disableScissor();
	}

	private void pinta(GuiGraphics g, PaqueteTablon.Linea l, int y) {
		int x = x0 + 12;
		int color = l.cogida() ? TINTA_SUAVE : TINTA;
		g.drawString(font, l.titulo(), x, y + 2, color, false);
		if (l.cogida()) {
			Component hecho = Component.translatableWithFallback("destinos.encargo.ya_cogido", "✔ Cogido");
			g.drawString(font, hecho, x0 + ancho - 12 - font.width(hecho), y + 6, TINTA_SUAVE, false);
		}
		int yy = y + 13;
		for (FormattedCharSequence s : font.split(l.texto(), anchoTexto())) {
			g.drawString(font, s, x, yy, TINTA_SUAVE, false);
			yy += 9;
		}
		for (FormattedCharSequence s : font.split(l.detalle(), anchoTexto())) {
			g.drawString(font, s, x, yy, TINTA, false);
			yy += 9;
		}
		g.fill(x0 + 12, y + altoLinea(l) - 3, x0 + ancho - 12, y + altoLinea(l) - 2, RAYA);
	}

	private void centrado(GuiGraphics g, Component c, int y, int color) {
		g.drawString(font, c, x0 + (ancho - font.width(c)) / 2, y, color, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
