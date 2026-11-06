package es.hulillo.destinos.cliente;

import es.hulillo.destinos.AspectoPersona;
import es.hulillo.destinos.PaqueteDialogo;
import es.hulillo.destinos.PaqueteRespuesta;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * v0.7.1 La corte: hablando con alguien. Un panel oscuro con marco dorado, su retrato (la cara de su skin), nombre y
 * cargo, lo que dice (va saliendo letra a letra; un clic lo enseña entero) y lo que se le puede contestar (clic o
 * teclas 1-9). Cada respuesta va al servidor, que contesta con otro PaqueteDialogo y esta pantalla se rehace.
 * Los colores del texto que vienen del chat se aclaran si se perderían sobre el fondo oscuro.
 */
public final class PantallaDialogo extends Screen {
	private static final int FONDO = 0xF2161210;
	private static final int FONDO_BAJO = 0xF2241A12;
	private static final int ORO = 0xFFD8B04E;
	private static final int ORO_OSCURO = 0xFF7A5C22;
	private static final int TEXTO = 0xFFEDE3CC;
	private static final int TEXTO_SUAVE = 0xFFB8A888;
	private static final int OPCION = 0x66000000;
	private static final int OPCION_ENCIMA = 0xCC3A2A14;
	/** Lado de cada píxel del retrato. */
	private static final int PIXEL = 7;
	private static final int CARA = 8 * PIXEL;
	private static final int ALTO_OPCION = 18;
	private static final int LINEA = 11;
	/** Milisegundos por letra al ir saliendo el texto. */
	private static final int MS_LETRA = 18;
	/** Lo más oscuro que puede ser un color de texto sobre el fondo (luminancia de 0 a 255). */
	private static final int LUZ_MINIMA = 150;

	private final PaqueteDialogo datos;
	private final int[] skin;
	/** El texto en trozos de un solo estilo, ya con los colores aclarados. */
	private final List<Trozo> trozos;
	private final int letras;
	private final long empieza = System.currentTimeMillis();
	private boolean entero;
	private int x0;
	private int y0;
	private int ancho;
	private int alto;
	private int arribaTexto;
	private int arribaOpciones;
	private int lineasTexto;

	private record Trozo(String texto, Style estilo) {
	}

	private PantallaDialogo(PaqueteDialogo datos) {
		super(datos.nombre());
		this.datos = datos;
		this.skin = AspectoPersona.pixeles(datos.aspecto());
		this.trozos = trozos(datos.texto());
		int n = 0;
		for (Trozo t : trozos) {
			n += t.texto().length();
		}
		this.letras = n;
	}

	public static void abre(PaqueteDialogo p) {
		Minecraft.getInstance().setScreen(new PantallaDialogo(p));
	}

	/** Parte el texto en trozos de un estilo y aclara los colores que no se leerían sobre el fondo oscuro. */
	private static List<Trozo> trozos(Component c) {
		List<Trozo> l = new ArrayList<>();
		c.visit((estilo, texto) -> {
			if (!texto.isEmpty()) {
				l.add(new Trozo(texto, contraste(estilo)));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return l;
	}

	private static Style contraste(Style s) {
		TextColor color = s.getColor();
		if (color == null) {
			return s.withColor(TEXTO & 0xFFFFFF);
		}
		int c = color.getValue();
		int r = c >> 16 & 255;
		int g = c >> 8 & 255;
		int b = c & 255;
		int luz = (r * 299 + g * 587 + b * 114) / 1000;
		if (luz >= LUZ_MINIMA) {
			return s;
		}
		// se mezcla con el color del texto hasta que se lea
		double t = Math.min(1, (LUZ_MINIMA - luz) / (double) Math.max(1, 255 - luz) + 0.25);
		int tr = TEXTO >> 16 & 255;
		int tg = TEXTO >> 8 & 255;
		int tb = TEXTO & 255;
		int nr = (int) (r + (tr - r) * t);
		int ng = (int) (g + (tg - g) * t);
		int nb = (int) (b + (tb - b) * t);
		return s.withColor(nr << 16 | ng << 8 | nb);
	}

	/** Las primeras n letras del texto, con sus estilos. */
	private Component hasta(int n) {
		MutableComponent t = Component.empty();
		for (Trozo tr : trozos) {
			if (n <= 0) {
				break;
			}
			String s = tr.texto().length() <= n ? tr.texto() : tr.texto().substring(0, n);
			t.append(Component.literal(s).withStyle(tr.estilo()));
			n -= s.length();
		}
		return t;
	}

	private int anchoTexto() {
		return ancho - 28;
	}

	@Override
	protected void init() {
		ancho = Math.min(360, width - 20);
		// el alto se calcula con el texto entero, para que el panel no crezca mientras sale
		lineasTexto = font.split(hasta(letras), anchoTexto()).size();
		arribaTexto = 14 + CARA + 14;
		arribaOpciones = arribaTexto + lineasTexto * LINEA + 12;
		alto = arribaOpciones + datos.opciones().size() * (ALTO_OPCION + 3) + 10;
		x0 = (width - ancho) / 2;
		y0 = Math.max(4, (height - alto) / 2);
	}

	private boolean terminado() {
		return entero || (System.currentTimeMillis() - empieza) / MS_LETRA >= letras;
	}

	private void elige(int i) {
		if (i < 0 || i >= datos.opciones().size()) {
			return;
		}
		String clave = datos.opciones().get(i).clave();
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
		if (ClientPlayNetworking.canSend(PaqueteRespuesta.TIPO)) {
			ClientPlayNetworking.send(new PaqueteRespuesta(datos.entidad(), clave));
		}
		if ("adios".equals(clave)) {
			onClose();
		}
	}

	/** La opción bajo el ratón, o -1. */
	private int opcionEn(double mx, double my) {
		if (mx < x0 + 12 || mx >= x0 + ancho - 12) {
			return -1;
		}
		for (int i = 0; i < datos.opciones().size(); i++) {
			int y = y0 + arribaOpciones + i * (ALTO_OPCION + 3);
			if (my >= y && my < y + ALTO_OPCION) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doble) {
		if (e.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(e, doble);
		}
		if (!terminado()) {
			// el primer clic enseña el texto entero
			entero = true;
			return true;
		}
		int i = opcionEn(e.x(), e.y());
		if (i >= 0) {
			elige(i);
			return true;
		}
		return super.mouseClicked(e, doble);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		int k = e.key();
		if (k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_9) {
			if (!terminado()) {
				entero = true;
			} else {
				elige(k - GLFW.GLFW_KEY_1);
			}
			return true;
		}
		if ((k == GLFW.GLFW_KEY_SPACE || k == GLFW.GLFW_KEY_ENTER) && !terminado()) {
			entero = true;
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
		super.renderBackground(g, mx, my, pt);
		// marco: oro, sombra y oro apagado; y el fondo con un degradado
		g.fill(x0 - 4, y0 - 4, x0 + ancho + 4, y0 + alto + 4, ORO);
		g.fill(x0 - 3, y0 - 3, x0 + ancho + 3, y0 + alto + 3, 0xFF0C0906);
		g.fill(x0 - 1, y0 - 1, x0 + ancho + 1, y0 + alto + 1, ORO_OSCURO);
		g.fillGradient(x0, y0, x0 + ancho, y0 + alto, FONDO, FONDO_BAJO);
		// rombos en las esquinas
		for (int[] e : new int[][] {{x0 - 4, y0 - 4}, {x0 + ancho - 1, y0 - 4}, {x0 - 4, y0 + alto - 1}, {x0 + ancho - 1, y0 + alto - 1}}) {
			g.fill(e[0] + 1, e[1], e[0] + 4, e[1] + 5, ORO);
			g.fill(e[0], e[1] + 1, e[0] + 5, e[1] + 4, ORO);
			g.fill(e[0] + 2, e[1] + 2, e[0] + 3, e[1] + 3, 0xFF8A1E1E);
		}
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		super.render(g, mx, my, pt);
		int cx = x0 + 14;
		int cy = y0 + 14;
		// el retrato: marco dorado y la cara de la skin (8..15, 8..15) con lo que lleve en la capa de encima
		g.fill(cx - 3, cy - 3, cx + CARA + 3, cy + CARA + 3, ORO);
		g.fill(cx - 2, cy - 2, cx + CARA + 2, cy + CARA + 2, 0xFF0C0906);
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
		int tx = cx + CARA + 14;
		g.drawString(font, datos.nombre().copy().withStyle(st -> st.withBold(true).withColor(ORO & 0xFFFFFF)), tx, cy + 12, ORO, true);
		for (FormattedCharSequence l : font.split(datos.cargo(), x0 + ancho - 14 - tx)) {
			g.drawString(font, l, tx, cy + 26, TEXTO_SUAVE, true);
			break;
		}
		// el adorno que separa el retrato de lo que dice
		int sy = y0 + arribaTexto - 7;
		g.fill(x0 + 14, sy, x0 + ancho - 14, sy + 1, ORO_OSCURO);
		int mid = x0 + ancho / 2;
		g.fill(mid - 2, sy - 2, mid + 3, sy + 3, ORO);
		g.fill(mid - 1, sy - 1, mid + 2, sy + 2, 0xFF8A1E1E);
		// lo que dice, letra a letra
		int n = terminado() ? letras : (int) ((System.currentTimeMillis() - empieza) / MS_LETRA);
		int y = y0 + arribaTexto;
		for (FormattedCharSequence l : font.split(hasta(n), anchoTexto())) {
			g.drawString(font, l, x0 + 14, y, TEXTO, true);
			y += LINEA;
		}
		// las respuestas
		boolean listo = terminado();
		int encima = listo ? opcionEn(mx, my) : -1;
		for (int i = 0; i < datos.opciones().size(); i++) {
			int oy = y0 + arribaOpciones + i * (ALTO_OPCION + 3);
			boolean sobre = i == encima;
			g.fill(x0 + 12, oy, x0 + ancho - 12, oy + ALTO_OPCION, sobre ? OPCION_ENCIMA : OPCION);
			g.fill(x0 + 12, oy, x0 + 14, oy + ALTO_OPCION, sobre ? ORO : ORO_OSCURO);
			if (sobre) {
				g.fill(x0 + 12, oy + ALTO_OPCION - 1, x0 + ancho - 12, oy + ALTO_OPCION, ORO_OSCURO);
			}
			int color = !listo ? 0xFF6A6050 : sobre ? ORO : TEXTO;
			Component num = Component.literal((i + 1) + ".");
			g.drawString(font, num, x0 + 20, oy + 5, listo ? ORO_OSCURO : 0xFF4A4030, false);
			g.drawString(font, datos.opciones().get(i).texto(), x0 + 34, oy + 5, color, true);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
