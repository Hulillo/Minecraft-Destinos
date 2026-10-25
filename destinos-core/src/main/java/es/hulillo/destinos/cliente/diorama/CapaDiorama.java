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
	private static final int ALFA_DUCADO = 0x80;
	private static final int ALFA_REINO = 0xd0;
	/** Por debajo de estos píxeles por casilla las fronteras serían solo ruido. */
	private static final float CASILLA_MIN_BORDES = 6;
	/** Para recortar al círculo del minimapa redondo se pinta en tiras de este alto. */
	private static final int TIRA = 2;
	private static final int Y_MIN = -64;
	private static final int Y_MAX = 320;
	/** View.round() solo existe desde Diorama 0.4: se busca una vez y, si no está, el mapa es cuadrado. */
	private static final Method REDONDO = buscaRedondo();

	private CapaDiorama() {
	}

	public static void dibuja(GuiGraphics g, MapViewport.View vista) {
		if (TerritoriosCliente.vacio() || vista.layer() != TileKey.SURFACE) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
			return;
		}
		Lienzo lienzo = new Lienzo(g, redondo(vista) ? vista : null);
		double u0 = vista.minU();
		double u1 = u0 + vista.width() / vista.zoom();
		double v0 = vista.minV();
		double v1 = v0 + vista.height() / vista.zoom();
		int tx0 = Math.floorDiv((int) Math.floor(u0), C);
		int tx1 = Math.floorDiv((int) Math.ceil(u1), C);
		int tz0 = Math.floorDiv((int) Math.floor(Projection.zAt(v0, Y_MIN)), C);
		int tz1 = Math.floorDiv((int) Math.ceil(Projection.zAt(v1, Y_MAX)), C);
		boolean bordes = C * vista.zoom() >= CASILLA_MIN_BORDES;
		for (int tz = tz0; tz <= tz1; tz++) {
			for (int tx = tx0; tx <= tx1; tx++) {
				PaqueteTerritorio.Ducado d = TerritoriosCliente.ducado(tx, tz);
				if (d == null || d.oceano()) {
					continue;
				}
				int alto = TerritoriosCliente.altura(tx, tz);
				int x0 = Math.round(vista.screenX(tx * C));
				int x1 = Math.round(vista.screenX((tx + 1) * C));
				int y0 = Math.round(vista.screenY(Projection.v(borde(alto, tx, tz - 1), tz * C)));
				int y1 = Math.round(vista.screenY(Projection.v(borde(alto, tx, tz + 1), (tz + 1) * C)));
				if (x1 <= vista.x() || x0 >= vista.x() + vista.width() || y1 <= vista.y() || y0 >= vista.y() + vista.height()) {
					continue;
				}
				lienzo.rect(x0, y0, x1, y1, ALFA_RELLENO << 24 | d.color());
				if (bordes) {
					frontera(lienzo, d, TerritoriosCliente.ducado(tx + 1, tz), x1 - 1, y0, x1, y1);
					frontera(lienzo, d, TerritoriosCliente.ducado(tx, tz + 1), x0, y1 - 1, x1, y1);
					frontera(lienzo, d, TerritoriosCliente.ducado(tx - 1, tz), x0, y0, x0 + 1, y1);
					frontera(lienzo, d, TerritoriosCliente.ducado(tx, tz - 1), x0, y0, x1, y0 + 1);
				}
			}
		}
	}

	/** Altura del borde con la casilla vecina: la media, para que filas a distinta altura no dejen huecos. */
	private static int borde(int alto, int tx, int tz) {
		int otro = TerritoriosCliente.altura(tx, tz);
		return otro == TerritoriosCliente.SIN_ALTURA ? alto : (alto + otro) / 2;
	}

	/** Línea por dentro de la casilla si el vecino es de otro ducado (más marcada si es de otro reino). */
	private static void frontera(Lienzo lienzo, PaqueteTerritorio.Ducado d, PaqueteTerritorio.Ducado vecino,
			int x0, int y0, int x1, int y1) {
		if (vecino == null || d.mismoDucado(vecino)) {
			return;
		}
		boolean reino = vecino.oceano() || !d.mismoReino(vecino);
		int alfa = reino ? ALFA_REINO : ALFA_DUCADO;
		lienzo.rect(x0, y0, x1, y1, alfa << 24 | oscuro(d.color()));
	}

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
