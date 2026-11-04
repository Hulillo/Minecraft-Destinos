package es.hulillo.destinos.cliente;

import es.hulillo.destinos.Geo;
import es.hulillo.destinos.MapaTerreno;
import java.util.Map;
import java.util.Set;

/**
 * El mapa visto de lejos, pintado como un atlas: una sola imagen del trozo de mundo que se ve (y un poco más
 * alrededor), a la resolución de verdad de la pantalla. Cada píxel toma el color de la casilla que tiene debajo y las
 * fronteras (costa, reino y frentes de guerra) son rayas finas allí donde cambia de un píxel al de al lado, así que
 * se ven limpias a cualquier distancia. Trabaja solo con datos copiados, de modo que puede ir en otro hilo.
 */
final class LienzoMapa {
	static final int N = MapaTerreno.NUCLEO;
	static final int CASILLA = MapaTerreno.CASILLA;

	/** Lo que hace falta saber de una zona, ya calculado en el hilo del cliente (no cambia una vez hecho). */
	record Datos(int[] colores, long[] dueno, byte[] marcas) {
		static final byte OCEANO = 1;
		static final byte SALVAJE = 2;
	}

	/** Un lienzo hecho: dónde empieza (en bloques), cuántos bloques mide cada píxel y sus píxeles en ARGB. */
	record Hecho(double bx0, double bz0, double bloquesPorPixel, int ancho, int alto, int[] pixeles) {
	}

	private LienzoMapa() {
	}

	/** Clave del par de reinos (sin orden) para saber si están en guerra. */
	static long par(long a, long b) {
		return a < b ? a * 31 + b : b * 31 + a;
	}

	/**
	 * Pinta el lienzo. grosor es el ancho de las fronteras de reino en píxeles (las de costa van siempre a 1); conFronteras
	 * a false deja solo la costa (para los modos donde las fronteras no cuentan).
	 */
	static Hecho pinta(Map<Long, Datos> zonas, Set<Long> guerras, double bx0, double bz0, double bpp, int ancho, int alto, int grosor,
			boolean conFronteras, int costa, int frontera, int frente) {
		int[] px = new int[ancho * alto];
		// filas guardadas: la de ahora y las "grosor" de antes (casilla dentro de su zona y la zona de cada píxel)
		int filas = grosor + 1;
		int[][] celdas = new int[filas][ancho];
		Datos[][] datos = new Datos[filas][ancho];
		// en qué casilla cae cada columna (es igual en todas las filas)
		int[] tx = new int[ancho];
		for (int x = 0; x < ancho; x++) {
			tx[x] = (int) Math.floor((bx0 + (x + 0.5) * bpp) / CASILLA);
		}
		for (int y = 0; y < alto; y++) {
			int[] celda = celdas[y % filas];
			Datos[] zona = datos[y % filas];
			int tz = (int) Math.floor((bz0 + (y + 0.5) * bpp) / CASILLA);
			int wz = Math.floorDiv(tz, N);
			int j = tz - wz * N;
			int ultimaWx = Integer.MIN_VALUE;
			Datos ultima = null;
			for (int x = 0; x < ancho; x++) {
				int wx = Math.floorDiv(tx[x], N);
				if (wx != ultimaWx) {
					ultimaWx = wx;
					ultima = zonas.get(Geo.clave(wx, wz));
				}
				zona[x] = ultima;
				celda[x] = ultima == null ? -1 : (tx[x] - wx * N) + j * N;
			}
			for (int x = 0; x < ancho; x++) {
				Datos d = zona[x];
				if (d == null) {
					continue;
				}
				int c = celda[x];
				int color = d.colores[c];
				// frontera con los píxeles de la izquierda o de arriba, hasta "grosor" de distancia
				int linde = 0;
				for (int k = 1; k <= grosor && linde < 4; k++) {
					if (x - k >= 0) {
						linde = Math.max(linde, linde(d, c, zona[x - k], celda[x - k], guerras, conFronteras, k));
					}
					if (y - k >= 0) {
						int f = (y - k) % filas;
						linde = Math.max(linde, linde(d, c, datos[f][x], celdas[f][x], guerras, conFronteras, k));
					}
				}
				if (linde == 4) {
					color = frente;
				} else if (linde == 3) {
					color = frontera;
				} else if (linde == 2) {
					color = costa;
				}
				px[x + y * ancho] = color;
			}
		}
		return new Hecho(bx0, bz0, bpp, ancho, alto, px);
	}

	/** 0 nada, 2 costa, 3 frontera de reino, 4 frente de guerra (como las lindes de la pantalla). */
	private static int linde(Datos a, int ca, Datos b, int cb, Set<Long> guerras, boolean conFronteras, int distancia) {
		if (b == null || (a == b && ca == cb)) {
			return 0;
		}
		boolean oa = (a.marcas[ca] & Datos.OCEANO) != 0;
		boolean ob = (b.marcas[cb] & Datos.OCEANO) != 0;
		if (oa != ob) {
			// la costa siempre a un píxel
			return distancia == 1 ? 2 : 0;
		}
		if (oa || !conFronteras) {
			return 0;
		}
		long da = a.dueno[ca];
		long db = b.dueno[cb];
		if (da == db) {
			return 0;
		}
		boolean sa = (a.marcas[ca] & Datos.SALVAJE) != 0;
		boolean sb = (b.marcas[cb] & Datos.SALVAJE) != 0;
		if (!sa && !sb && guerras.contains(par(da, db))) {
			return 4;
		}
		return 3;
	}
}
