package es.hulillo.destinos.cliente.diorama;

import dev.diorama.Projection;
import dev.diorama.bake.TileKey;
import dev.diorama.render.MapViewport;
import es.hulillo.destinos.MapaTerreno;
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
			TerritoriosCliente.ultimoPintado = "Diorama: mapa político apagado (tecla del mapa político)";
			return;
		}
		if (TerritoriosCliente.vacio()) {
			TerritoriosCliente.ultimoPintado = "Diorama: aún no hay territorios";
			return;
		}
		if (vista.layer() != TileKey.SURFACE) {
			TerritoriosCliente.ultimoPintado = "Diorama: estás viendo cuevas, no la superficie";
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
			TerritoriosCliente.ultimoPintado = "Diorama: no estás en el mundo normal";
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
				if (bordes) {
					// la línea no puede comerse más de un tercio de la casilla
					int tope = Math.max(1, Math.min(x1 - x0, y1 - y0) / 3);
					int gr = Math.min(gReino, tope);
					int gd = ducados ? Math.min(gDucado, tope) : 0;
					int color = oscuro(d.color());
					int g0 = grosor(d, TerritoriosCliente.ducado(tx + 1, tz), gr, gd);
					int g1 = grosor(d, TerritoriosCliente.ducado(tx, tz + 1), gr, gd);
					int g2 = grosor(d, TerritoriosCliente.ducado(tx - 1, tz), gr, gd);
					int g3 = grosor(d, TerritoriosCliente.ducado(tx, tz - 1), gr, gd);
					linea(lienzo, x1 - Math.abs(g0), y0, x1, y1, g0, color);
					linea(lienzo, x0, y1 - Math.abs(g1), x1, y1, g1, color);
					linea(lienzo, x0, y0, x0 + Math.abs(g2), y1, g2, color);
					linea(lienzo, x0, y0, x1, y0 + Math.abs(g3), g3, color);
				}
			}
		}
		TerritoriosCliente.ultimoPintado = "Diorama: " + pintadas + " casillas pintadas, " + mar + " de mar internacional, "
				+ sinDatos + " sin datos, " + fuera + " fuera de pantalla (x " + tx0 + ".." + tx1 + ", z " + tz0 + ".." + tz1
				+ ", zoom " + String.format("%.3f", vista.zoom()) + ")";
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

	/** Línea por dentro de la casilla: la de reino (grosor positivo), más oscura y opaca. */
	private static void linea(Lienzo lienzo, int x0, int y0, int x1, int y1, int grosor, int color) {
		if (grosor == 0 || x0 >= x1 || y0 >= y1) {
			return;
		}
		int c = grosor > 0 ? ALFA_REINO << 24 | oscuro(color) : ALFA_DUCADO << 24 | color;
		lienzo.rect(x0, y0, x1, y1, c);
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
