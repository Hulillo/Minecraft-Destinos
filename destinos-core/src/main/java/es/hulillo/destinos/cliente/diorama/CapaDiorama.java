package es.hulillo.destinos.cliente.diorama;

import dev.diorama.Projection;
import dev.diorama.bake.TileKey;
import dev.diorama.render.MapViewport;
import es.hulillo.destinos.MapaTerreno;
import es.hulillo.destinos.PaquetePolitica;
import es.hulillo.destinos.PaqueteTerritorio;
import es.hulillo.destinos.cliente.TerritoriosCliente;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.Level;

/**
 * Pinta los reinos sobre el mapa de Diorama, justo antes de su niebla (así lo inexplorado sigue tapado).
 * Diorama no tiene API: MixinMapViewport llama aquí. Su cámara es oblicua (u = x, v = z·sin − y·cos), así que
 * cada casilla de 64×64 se coloca con la altura del suelo que manda el servidor.
 */
public final class CapaDiorama {
	private static final int C = MapaTerreno.CASILLA;
	private static final int ALFA_RELLENO = 0x38;
	/** Las aguas territoriales, más suaves que la tierra. */
	private static final int ALFA_AGUAS = 0x1c;
	private static final int ALFA_DUCADO = 0xa0;
	private static final int ALFA_REINO = 0xf0;
	/** Ducados que acaban de cambiar de dueño: brillo dorado que late. */
	private static final int ORO = 0xffc83d;
	private static final int ALFA_ORO_MAX = 0x78;
	/** Frentes de guerra: línea roja y, con zoom suficiente, una trinchera de X sobre la frontera. */
	private static final int ROJO = 0xd42a2a;
	private static final float CASILLA_MIN_TRINCHERA = 10;
	/** Por debajo de estos píxeles por casilla las fronteras de ducado serían solo ruido. */
	private static final float CASILLA_MIN_DUCADOS = 6;
	/** Las de reino se ven antes, para que de lejos se lea el mapa político. */
	private static final float CASILLA_MIN_REINOS = 2;
	/** Para recortar al círculo del minimapa redondo se pinta en tiras de este alto. */
	private static final int TIRA = 2;
	private static final int Y_MIN = -64;
	private static final int Y_MAX = 320;
	/** View.round() solo existe desde Diorama 0.4: se busca una vez y, si no está, el mapa es cuadrado. */
	private static final Method REDONDO = buscaRedondo();

	private CapaDiorama() {
	}

	public static void dibuja(GuiGraphics g, MapViewport.View vista) {
		if (!TerritoriosCliente.visible()) {
			TerritoriosCliente.ultimoPintado = () -> "Diorama: mapa político apagado (tecla del mapa político)";
			return;
		}
		if (TerritoriosCliente.vacio()) {
			TerritoriosCliente.ultimoPintado = () -> "Diorama: aún no hay territorios";
			return;
		}
		if (vista.layer() != TileKey.SURFACE) {
			TerritoriosCliente.ultimoPintado = () -> "Diorama: estás viendo cuevas, no la superficie";
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
			TerritoriosCliente.ultimoPintado = () -> "Diorama: no estás en el mundo normal";
			return;
		}
		// Dentro de una capa, el juego ordena lo que se pinta por tipo: los rectángulos lisos irían antes que las
		// casillas de Diorama (que llevan textura) y quedarían tapados. En una capa nueva van encima, y la
		// niebla de Diorama, que se pinta después, sigue tapándolos.
		g.nextStratum();
		Lienzo lienzo = new Lienzo(g, redondo(vista) ? vista : null);
		double u0 = vista.minU();
		double u1 = u0 + vista.width() / vista.zoom();
		double v0 = vista.minV();
		double v1 = v0 + vista.height() / vista.zoom();
		int tx0 = Math.floorDiv((int) Math.floor(u0), C);
		int tx1 = Math.floorDiv((int) Math.ceil(u1), C);
		int tz0 = Math.floorDiv((int) Math.floor(Projection.zAt(v0, Y_MIN)), C);
		int tz1 = Math.floorDiv((int) Math.ceil(Projection.zAt(v1, Y_MAX)), C);
		float px = (float) (C * vista.zoom());
		boolean bordes = px >= CASILLA_MIN_REINOS;
		boolean ducados = px >= CASILLA_MIN_DUCADOS;
		// grosor según el zoom: la de reino, bastante más gorda que la de ducado
		int gDucado = Math.max(1, Math.round(px / 16));
		int gReino = Math.max(2, Math.round(px / 7));
		boolean trinchera = px >= CASILLA_MIN_TRINCHERA;
		// el brillo dorado late una vez cada ~2 segundos
		double fase = (System.currentTimeMillis() % 2000L) / 2000.0 * Math.PI * 2;
		int alfaOro = (int) (ALFA_ORO_MAX * (0.35 + 0.65 * (0.5 + 0.5 * Math.sin(fase))));
		int sinDatos = 0;
		int mar = 0;
		int fuera = 0;
		int pintadas = 0;
		for (int tz = tz0; tz <= tz1; tz++) {
			for (int tx = tx0; tx <= tx1; tx++) {
				PaqueteTerritorio.Ducado d = TerritoriosCliente.ducado(tx, tz);
				if (d == null || d.deNadie()) {
					if (d == null) {
						sinDatos++;
					} else {
						mar++;
					}
					continue;
				}
				int alto = TerritoriosCliente.altura(tx, tz);
				int x0 = Math.round(vista.screenX(tx * C));
				int x1 = Math.round(vista.screenX((tx + 1) * C));
				int y0 = Math.round(vista.screenY(Projection.v(borde(alto, tx, tz - 1), tz * C)));
				int y1 = Math.round(vista.screenY(Projection.v(borde(alto, tx, tz + 1), (tz + 1) * C)));
				if (x1 <= vista.x() || x0 >= vista.x() + vista.width() || y1 <= vista.y() || y0 >= vista.y() + vista.height()) {
					fuera++;
					continue;
				}
				pintadas++;
				lienzo.rect(x0, y0, x1, y1, (d.oceano() ? ALFA_AGUAS : ALFA_RELLENO) << 24 | d.color());
				boolean reciente = TerritoriosCliente.reciente(d);
				if (reciente) {
					lienzo.rect(x0, y0, x1, y1, alfaOro << 24 | ORO);
				}
				if (bordes) {
					// la línea no puede comerse más de un tercio de la casilla
					int tope = Math.max(1, Math.min(x1 - x0, y1 - y0) / 3);
					int gr = Math.min(gReino, tope);
					int gd = ducados ? Math.min(gDucado, tope) : 0;
					int color = reciente ? ORO : oscuro(d.color());
					PaqueteTerritorio.Ducado este = TerritoriosCliente.ducado(tx + 1, tz);
					PaqueteTerritorio.Ducado sur = TerritoriosCliente.ducado(tx, tz + 1);
					PaqueteTerritorio.Ducado oeste = TerritoriosCliente.ducado(tx - 1, tz);
					PaqueteTerritorio.Ducado norte = TerritoriosCliente.ducado(tx, tz - 1);
					boolean guerraE = TerritoriosCliente.enGuerra(d, este);
					boolean guerraS = TerritoriosCliente.enGuerra(d, sur);
					int g0 = grosor(d, este, gr, gd);
					int g1 = grosor(d, sur, gr, gd);
					int g2 = grosor(d, oeste, gr, gd);
					int g3 = grosor(d, norte, gr, gd);
					linea(lienzo, x1 - Math.abs(g0), y0, x1, y1, g0, guerraE, color);
					linea(lienzo, x0, y1 - Math.abs(g1), x1, y1, g1, guerraS, color);
					linea(lienzo, x0, y0, x0 + Math.abs(g2), y1, g2, TerritoriosCliente.enGuerra(d, oeste), color);
					linea(lienzo, x0, y0, x1, y0 + Math.abs(g3), g3, TerritoriosCliente.enGuerra(d, norte), color);
					// la trinchera se dibuja una sola vez sobre cada frontera: desde la casilla del oeste o del norte
					if (trinchera) {
						int r = Math.max(2, Math.min(x1 - x0, y1 - y0) / 5);
						if (guerraE) {
							equis(lienzo, x1, (y0 + y1) / 2, r);
						}
						if (guerraS) {
							equis(lienzo, (x0 + x1) / 2, y1, r);
						}
					}
				}
			}
		}
		if (bordes) {
			for (PaquetePolitica.Capital c : TerritoriosCliente.capitales()) {
				int alto = TerritoriosCliente.altura(Math.floorDiv(c.x(), C), Math.floorDiv(c.z(), C));
				if (alto == TerritoriosCliente.SIN_ALTURA) {
					continue;
				}
				int sx = Math.round(vista.screenX(c.x()));
				int sy = Math.round(vista.screenY(Projection.v(alto, c.z())));
				if (sx < vista.x() || sx >= vista.x() + vista.width() || sy < vista.y() || sy >= vista.y() + vista.height()) {
					continue;
				}
				corona(lienzo, sx, sy, Math.max(2, Math.min(4, Math.round(px / 20))), c.perdida() ? GRIS : ORO);
			}
		}
		resumen[0] = pintadas;
		resumen[1] = mar;
		resumen[2] = sinDatos;
		resumen[3] = fuera;
		resumen[4] = tx0;
		resumen[5] = tx1;
		resumen[6] = tz0;
		resumen[7] = tz1;
		zoom = vista.zoom();
		TerritoriosCliente.ultimoPintado = CapaDiorama::resumen;
	}

	/** Lo que se pintó en el último fotograma (casillas pintadas, de mar, sin datos, fuera, x0, x1, z0, z1) y el zoom. */
	private static final int[] resumen = new int[8];
	private static double zoom;

	private static String resumen() {
		int[] r = resumen;
		return "Diorama: " + r[0] + " casillas pintadas, " + r[1] + " de mar internacional, " + r[2] + " sin datos, " + r[3]
				+ " fuera de pantalla (x " + r[4] + ".." + r[5] + ", z " + r[6] + ".." + r[7] + ", zoom "
				+ String.format("%.3f", zoom) + ")";
	}

	/** Altura del borde con la casilla vecina: la media, para que filas a distinta altura no dejen huecos. */
	private static int borde(int alto, int tx, int tz) {
		int otro = TerritoriosCliente.altura(tx, tz);
		return otro == TerritoriosCliente.SIN_ALTURA ? alto : (alto + otro) / 2;
	}

	/**
	 * Grosor de la frontera con la casilla vecina: 0 si no hay, positivo si es de reino (la costa al mar de nadie
	 * cuenta como reino) y negativo si es solo de ducado. Sin arrays: esto se llama 4 veces por casilla y fotograma.
	 */
	private static int grosor(PaqueteTerritorio.Ducado d, PaqueteTerritorio.Ducado vecino, int gReino, int gDucado) {
		if (vecino == null || d.mismoDucado(vecino)) {
			return 0;
		}
		return vecino.deNadie() || !d.mismoReino(vecino) ? gReino : -gDucado;
	}

	/** Línea por dentro de la casilla: la de reino (grosor positivo), más oscura y opaca; roja si es frente de guerra. */
	private static void linea(Lienzo lienzo, int x0, int y0, int x1, int y1, int grosor, boolean guerra, int color) {
		if (grosor == 0 || x0 >= x1 || y0 >= y1) {
			return;
		}
		int c = guerra && grosor > 0 ? ALFA_REINO << 24 | ROJO
				: grosor > 0 ? ALFA_REINO << 24 | oscuro(color) : ALFA_DUCADO << 24 | color;
		lienzo.rect(x0, y0, x1, y1, c);
	}

	/**
	 * Una X roja de radio r centrada en (cx, cy), hecha a escalones: las marcas de la trinchera. Primero va una X negra
	 * algo más gorda debajo, para que el contorno la separe de la línea roja del frente.
	 */
	private static void equis(Lienzo lienzo, int cx, int cy, int r) {
		int t = Math.max(1, r / 3);
		int b = Math.max(1, t / 2);
		trazos(lienzo, cx, cy, r, t, b, 0xff << 24);
		trazos(lienzo, cx, cy, r, t, 0, 0xff << 24 | ROJO);
	}

	private static void trazos(Lienzo lienzo, int cx, int cy, int r, int t, int b, int c) {
		for (int i = -r; i <= r; i++) {
			lienzo.rect(cx + i - b, cy + i - b, cx + i + t + b, cy + i + t + b, c);
			lienzo.rect(cx + i - b, cy - i - b, cx + i + t + b, cy - i + t + b, c);
		}
	}

	/** Corona de una capital, en píxeles de s×s centrada en (cx, cy), con contorno negro. */
	private static final String[] CORONA = {"x.x.x", "xxxxx", "xxxxx"};
	/** Corona de una capital que hoy tiene otro reino. */
	private static final int GRIS = 0x9e9e9e;

	private static void corona(Lienzo lienzo, int cx, int cy, int s, int color) {
		int x0 = cx - 5 * s / 2;
		int y0 = cy - 3 * s / 2;
		for (int borde = 1; borde >= 0; borde--) {
			int c = borde == 1 ? 0xff000000 : 0xff << 24 | color;
			for (int j = 0; j < CORONA.length; j++) {
				for (int i = 0; i < CORONA[j].length(); i++) {
					if (CORONA[j].charAt(i) == 'x') {
						lienzo.rect(x0 + i * s - borde, y0 + j * s - borde, x0 + (i + 1) * s + borde, y0 + (j + 1) * s + borde, c);
					}
				}
			}
		}
	}

	/** El color de la casilla oscurecido a 3/5 (líneas de ducado); aplicado dos veces, el de reino. */
	private static int oscuro(int rgb) {
		return (rgb >> 16 & 0xff) * 3 / 5 << 16 | (rgb >> 8 & 0xff) * 3 / 5 << 8 | (rgb & 0xff) * 3 / 5;
	}

	private static Method buscaRedondo() {
		try {
			return MapViewport.View.class.getMethod("round");
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

	private static boolean redondo(MapViewport.View vista) {
		try {
			return REDONDO != null && (boolean) REDONDO.invoke(vista);
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	/** Rellena rectángulos, recortados al círculo si el minimapa es redondo (como Circle.in de Diorama). */
	private record Lienzo(GuiGraphics g, MapViewport.View circulo) {
		void rect(int x0, int y0, int x1, int y1, int color) {
			if (circulo == null) {
				g.fill(x0, y0, x1, y1, color);
				return;
			}
			float cx = circulo.x() + circulo.width() / 2.0F;
			float cy = circulo.y() + circulo.height() / 2.0F;
			float r = Math.min(circulo.width(), circulo.height()) / 2.0F;
			// casi todo cae entero dentro del círculo: un solo rectángulo en vez de tiras
			float dx = Math.max(Math.abs(x0 - cx), Math.abs(x1 - cx));
			float dy0 = Math.max(Math.abs(y0 - cy), Math.abs(y1 - cy));
			if (dx * dx + dy0 * dy0 <= r * r) {
				g.fill(x0, y0, x1, y1, color);
				return;
			}
			for (int y = y0; y < y1; y += TIRA) {
				int yf = Math.min(y + TIRA, y1);
				float dy = Math.max(Math.abs(y - cy), Math.abs(yf - cy));
				if (dy >= r) {
					continue;
				}
				float media = (float) Math.sqrt(r * r - dy * dy);
				int a = Math.max(x0, Math.round(cx - media));
				int b = Math.min(x1, Math.round(cx + media));
				if (a < b) {
					g.fill(a, y, b, yf, color);
				}
			}
		}
	}
}
