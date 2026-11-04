package es.hulillo.destinos.cliente;

import es.hulillo.destinos.Geo;
import es.hulillo.destinos.MapaTerreno;
import java.util.HashMap;
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
	/**
	 * El mejor sitio para el nombre de cada reino: la casilla de sus tierras más alejada de cualquier borde (otro reino,
	 * el mar, tierras salvajes o lo que aún no se ha explorado). Da, por reino, {x, z, distancia} en casillas del mundo
	 * (x y z al centro de la casilla). Es una distancia de chaflán 3-4 en dos pasadas sobre todo lo conocido.
	 */
	static Map<Long, float[]> polos(Map<Long, Datos> zonas) {
		Map<Long, float[]> polos = new HashMap<>();
		if (zonas.isEmpty()) {
			return polos;
		}
		int wx0 = Integer.MAX_VALUE;
		int wz0 = Integer.MAX_VALUE;
		int wx1 = Integer.MIN_VALUE;
		int wz1 = Integer.MIN_VALUE;
		for (long k : zonas.keySet()) {
			wx0 = Math.min(wx0, Geo.claveX(k));
			wx1 = Math.max(wx1, Geo.claveX(k));
			wz0 = Math.min(wz0, Geo.claveZ(k));
			wz1 = Math.max(wz1, Geo.claveZ(k));
		}
		int ancho = (wx1 - wx0 + 1) * N;
		int alto = (wz1 - wz0 + 1) * N;
		// dueño de cada casilla; las que no son de ningún reino (mar, salvajes, sin explorar) son borde
		long[] dueno = new long[ancho * alto];
		java.util.Arrays.fill(dueno, Long.MIN_VALUE);
		for (Map.Entry<Long, Datos> e : zonas.entrySet()) {
			int bx = (Geo.claveX(e.getKey()) - wx0) * N;
			int bz = (Geo.claveZ(e.getKey()) - wz0) * N;
			Datos d = e.getValue();
			for (int j = 0; j < N; j++) {
				for (int i = 0; i < N; i++) {
					int c = i + j * N;
					if ((d.marcas[c] & (Datos.OCEANO | Datos.SALVAJE)) == 0) {
						dueno[bx + i + (bz + j) * ancho] = d.dueno[c];
					}
				}
			}
		}
		int[] dist = new int[ancho * alto];
		for (int y = 0; y < alto; y++) {
			for (int x = 0; x < ancho; x++) {
				int c = x + y * ancho;
				long k = dueno[c];
				if (k == Long.MIN_VALUE) {
					continue;
				}
				int m = Integer.MAX_VALUE / 2;
				m = Math.min(m, x > 0 && dueno[c - 1] == k ? dist[c - 1] + 3 : 3);
				m = Math.min(m, y > 0 && dueno[c - ancho] == k ? dist[c - ancho] + 3 : 3);
				m = Math.min(m, x > 0 && y > 0 && dueno[c - ancho - 1] == k ? dist[c - ancho - 1] + 4 : 3);
				m = Math.min(m, x < ancho - 1 && y > 0 && dueno[c - ancho + 1] == k ? dist[c - ancho + 1] + 4 : 3);
				// el borde del mapa conocido también es borde
				if (x == 0 || y == 0 || x == ancho - 1 || y == alto - 1) {
					m = 3;
				}
				dist[c] = m;
			}
		}
		for (int y = alto - 1; y >= 0; y--) {
			for (int x = ancho - 1; x >= 0; x--) {
				int c = x + y * ancho;
				long k = dueno[c];
				if (k == Long.MIN_VALUE) {
					continue;
				}
				int m = dist[c];
				if (x < ancho - 1) {
					m = Math.min(m, dueno[c + 1] == k ? dist[c + 1] + 3 : 3);
				}
				if (y < alto - 1) {
					m = Math.min(m, dueno[c + ancho] == k ? dist[c + ancho] + 3 : 3);
					if (x < ancho - 1) {
						m = Math.min(m, dueno[c + ancho + 1] == k ? dist[c + ancho + 1] + 4 : 3);
					}
					if (x > 0) {
						m = Math.min(m, dueno[c + ancho - 1] == k ? dist[c + ancho - 1] + 4 : 3);
					}
				}
				dist[c] = m;
				float[] p = polos.get(k);
				if (p == null || m > p[2] * 3) {
					polos.put(k, new float[] {wx0 * N + x + 0.5f, wz0 * N + y + 0.5f, m / 3f});
				}
			}
		}
		return polos;
	}
}
