package es.hulillo.destinos.cliente;

import es.hulillo.destinos.MapaTerreno;
import es.hulillo.destinos.PaqueteTerritorio;

/**
 * El relieve de una zona visto de cerca, con más detalle del que traen los datos (una altura cada 64 bloques): las
 * alturas se interpolan con curvas suaves (Catmull-Rom) entre los centros de las casillas, con un poco de ruido para
 * que costas, curvas de nivel y ríos no salgan rectos ni en cuadros, y encima sombra del noroeste, curvas de nivel,
 * costa y ríos finos que serpentean. El ruido va en coordenadas del mundo, así que dos zonas vecinas casan en el borde.
 * Trabaja con datos copiados (Rejilla), de modo que puede ir en otro hilo.
 */
final class RelieveMapa {
	static final int N = MapaTerreno.NUCLEO;
	static final int CASILLA = MapaTerreno.CASILLA;
	/** Casillas de más por cada lado que se copian de las zonas vecinas (las curvas piden dos). */
	static final int ANILLO = 2;
	static final int LADO = N + 2 * ANILLO;
	static final byte OCEANO = 1;
	static final byte RIO = 2;
	/** Casillas de río que en realidad son un lago (muchas juntas): se pintan como agua, no como rayas. */
	static final byte LAGO = 4;
	static final byte AGUA = OCEANO | LAGO;

	/** Cada cuántos bloques va una curva de nivel, y cada cuántas una más marcada. */
	private static final int CURVA = 24;
	private static final int MAESTRA = 5;
	/** Medio ancho de un río en bloques. */
	private static final double RIO_MEDIO = 6;
	/** Cuánto se tuercen las cosas (en bloques) y a qué escala, y cuánto relieve pequeño se añade. */
	private static final double TUERCE = 30;
	private static final double TUERCE_ESCALA = 100;
	private static final double TUERCE_FINO = 9;
	private static final double TUERCE_FINO_ESCALA = 30;
	private static final double GRANO = 2;
	/** Lo que serpentean además los ríos (en bloques) y cada cuánto. */
	private static final double MEANDRO = 16;
	private static final double MEANDRO_ESCALA = 36;

	private static final int COLOR_RIO = 0xFF4A86C0;
	private static final int COLOR_COSTA = 0xFF4A3D2C;
	private static final int COLOR_CURVA = 0xFF4A3520;

	/** Alturas y marcas de las casillas de la zona y de un anillo de ANILLO alrededor, fila a fila. */
	record Rejilla(int wx, int wz, int[] alturas, byte[] marcas) {
		/** Copia lo que hace falta de la zona y sus vecinas (en el hilo del cliente). */
		static Rejilla de(PaqueteTerritorio z) {
			int[] h = new int[LADO * LADO];
			byte[] m = new byte[LADO * LADO];
			int ox = z.wx() * N;
			int oz = z.wz() * N;
			for (int b = 0; b < LADO; b++) {
				for (int a = 0; a < LADO; a++) {
					int i = a - ANILLO;
					int j = b - ANILLO;
					PaqueteTerritorio q = z;
					int qi = i;
					int qj = j;
					if (i < 0 || j < 0 || i >= N || j >= N) {
						PaqueteTerritorio v = TerritoriosCliente.zona(ox + i, oz + j);
						if (v != null) {
							q = v;
							qi = ox + i - v.wx() * N;
							qj = oz + j - v.wz() * N;
						} else {
							// sin vecina se repite el borde de la zona
							qi = Math.max(0, Math.min(N - 1, i));
							qj = Math.max(0, Math.min(N - 1, j));
						}
					}
					int c = a + b * LADO;
					h[c] = q.altura(qi, qj);
					m[c] = (byte) ((q.ducado(qi, qj).oceano() ? OCEANO : 0) | (q.rio(qi, qj) ? RIO : 0));
				}
			}
			marcaLagos(m);
			return new Rejilla(z.wx(), z.wz(), h, m);
		}

		/**
		 * Un lago es un montón de casillas de río juntas: las que tienen río en 5 o más de sus 8 vecinas, y las de río
		 * pegadas a esas (la orilla).
		 */
		private static void marcaLagos(byte[] m) {
			boolean[] centro = new boolean[LADO * LADO];
			for (int b = 0; b < LADO; b++) {
				for (int a = 0; a < LADO; a++) {
					if ((m[a + b * LADO] & RIO) != 0 && vecinasDeRio(m, a, b) >= 5) {
						centro[a + b * LADO] = true;
					}
				}
			}
			for (int b = 0; b < LADO; b++) {
				for (int a = 0; a < LADO; a++) {
					int c = a + b * LADO;
					if ((m[c] & RIO) == 0) {
						continue;
					}
					boolean lago = centro[c];
					for (int db = -1; db <= 1 && !lago; db++) {
						for (int da = -1; da <= 1 && !lago; da++) {
							int na = a + da;
							int nb = b + db;
							lago = na >= 0 && nb >= 0 && na < LADO && nb < LADO && centro[na + nb * LADO];
						}
					}
					if (lago) {
						m[c] |= LAGO;
					}
				}
			}
		}

		private static int vecinasDeRio(byte[] m, int a, int b) {
			int n = 0;
			for (int db = -1; db <= 1; db++) {
				for (int da = -1; da <= 1; da++) {
					int na = a + da;
					int nb = b + db;
					if ((da != 0 || db != 0) && na >= 0 && nb >= 0 && na < LADO && nb < LADO && (m[na + nb * LADO] & RIO) != 0) {
						n++;
					}
				}
			}
			return n;
		}

		/** Cuántas vecinas había al copiarla (si cambia, hay que rehacerla para que case en los bordes). */
		static int vecinas(PaqueteTerritorio z) {
			int n = 0;
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					if ((dx != 0 || dz != 0) && TerritoriosCliente.zona((z.wx() + dx) * N, (z.wz() + dz) * N) != null) {
						n |= 1 << ((dx + 1) + (dz + 1) * 3);
					}
				}
			}
			return n;
		}

		int h(int a, int b) {
			return alturas[a + b * LADO];
		}

		boolean es(int a, int b, byte bit) {
			return (marcas[a + b * LADO] & bit) != 0;
		}
	}

	private RelieveMapa() {
	}

	/** Pinta la zona a k píxeles por casilla; devuelve los píxeles en ARGB, fila a fila (lado N*k). */
	static int[] pinta(Rejilla r, int k) {
		int w = N * k;
		int ww = w + 2;
		double bpt = (double) CASILLA / k;
		double ox = (double) r.wx() * N * CASILLA;
		double oz = (double) r.wz() * N * CASILLA;
		// para cada píxel (con uno de margen por lado): punto torcido en casillas de la rejilla, altura y mar
		float[] hu = new float[ww * ww];
		float[] mar = new float[ww * ww];
		float[] pu = new float[ww * ww];
		float[] pv = new float[ww * ww];
		float[] ru = new float[ww * ww];
		float[] rv = new float[ww * ww];
		for (int y = 0; y < ww; y++) {
			for (int x = 0; x < ww; x++) {
				double bx = ox + (x - 1 + 0.5) * bpt;
				double bz = oz + (y - 1 + 0.5) * bpt;
				double tx = bx + TUERCE * ruido(bx / TUERCE_ESCALA, bz / TUERCE_ESCALA, 11)
						+ TUERCE_FINO * ruido(bx / TUERCE_FINO_ESCALA, bz / TUERCE_FINO_ESCALA, 13);
				double tz = bz + TUERCE * ruido(bx / TUERCE_ESCALA, bz / TUERCE_ESCALA, 17)
						+ TUERCE_FINO * ruido(bx / TUERCE_FINO_ESCALA, bz / TUERCE_FINO_ESCALA, 19);
				// en unidades de la rejilla: el centro de la casilla (a, b) cae en (a, b)
				double u = (tx - ox) / CASILLA - 0.5 + ANILLO;
				double v = (tz - oz) / CASILLA - 0.5 + ANILLO;
				int c = x + y * ww;
				pu[c] = (float) u;
				pv[c] = (float) v;
				ru[c] = (float) (u + MEANDRO / CASILLA * ruido(bx / MEANDRO_ESCALA, bz / MEANDRO_ESCALA, 31));
				rv[c] = (float) (v + MEANDRO / CASILLA * ruido(bx / MEANDRO_ESCALA, bz / MEANDRO_ESCALA, 37));
				hu[c] = (float) (curva(r, u, v) + GRANO * (ruido(bx / 14, bz / 14, 23) + 0.3 * ruido(bx / 6, bz / 6, 29)));
				mar[c] = (float) mar(r, u, v);
			}
		}
		float[] rio = rios(r, ru, rv, ww, k);
		int[] px = new int[w * w];
		double norma = 2 * Math.sqrt(2) * bpt;
		for (int y = 0; y < w; y++) {
			for (int x = 0; x < w; x++) {
				int c = (x + 1) + (y + 1) * ww;
				double h = hu[c];
				int color;
				if (mar[c] >= 0.5f) {
					// más hondo, más oscuro; junto a la costa, un poco más claro
					double t = Math.max(0, Math.min(1, (62 - h) / 40.0));
					color = ModoMapa.agua(t);
					if (mar[c - 1] < 0.5f || mar[c + 1] < 0.5f || mar[c - ww] < 0.5f || mar[c + ww] < 0.5f) {
						color = ModoMapa.mezcla(color, 0xFFD8E6E8, 0.35);
					}
				} else {
					color = ModoMapa.tierra((int) Math.round(h));
					// sombra: lado que mira al noroeste más claro, al sudeste más oscuro
					double s = (hu[c + 1 + ww] - hu[c - 1 - ww]) / norma * 1.4;
					s = Math.max(-1, Math.min(1, s));
					color = s >= 0 ? ModoMapa.mezcla(color, 0xFFFFFFFF, s * 0.3) : ModoMapa.mezcla(color, 0xFF000000, -s * 0.4);
					// curva de nivel donde cambia de escalón con el píxel de la derecha o el de abajo
					int n = Math.floorDiv((int) Math.floor(h), CURVA);
					int nd = Math.floorDiv((int) Math.floor(hu[c + 1]), CURVA);
					int na = Math.floorDiv((int) Math.floor(hu[c + ww]), CURVA);
					if (n != nd || n != na) {
						int m = Math.max(n, Math.max(nd, na));
						color = ModoMapa.mezcla(color, COLOR_CURVA, Math.floorMod(m, MAESTRA) == 0 ? 0.42 : 0.24);
					}
					float ri = rio[c];
					if (ri > 0) {
						color = ModoMapa.mezcla(color, COLOR_RIO, ri);
					}
					if (mar[c - 1] >= 0.5f || mar[c + 1] >= 0.5f || mar[c - ww] >= 0.5f || mar[c + ww] >= 0.5f) {
						color = ModoMapa.mezcla(color, COLOR_COSTA, 0.7);
					}
				}
				px[x + y * w] = color;
			}
		}
		return px;
	}

	/**
	 * Cuánto río hay en cada píxel (0 a 1, con el borde suavizado): rayas de centro a centro de las casillas de río
	 * vecinas (o hasta el mar), medidas desde el punto torcido para que serpenteen.
	 */
	private static float[] rios(Rejilla r, float[] pu, float[] pv, int ww, int k) {
		float[] cob = new float[ww * ww];
		// medio ancho en casillas; como mínimo medio píxel largo para que se vea
		double medio = Math.max(RIO_MEDIO / CASILLA, 0.6 / k);
		// cuánto se mueve un punto como mucho al torcerlo, en píxeles
		int holgura = (int) Math.ceil((TUERCE + TUERCE_FINO + MEANDRO) / CASILLA * k) + 2;
		for (int b = 0; b < LADO; b++) {
			for (int a = 0; a < LADO; a++) {
				if (!r.es(a, b, RIO) || r.es(a, b, AGUA)) {
					continue;
				}
				boolean solo = true;
				for (int[] d : new int[][] {{1, 0}, {0, 1}, {1, 1}, {-1, 1}, {-1, 0}, {0, -1}, {-1, -1}, {1, -1}}) {
					int na = a + d[0];
					int nb = b + d[1];
					if (na < 0 || nb < 0 || na >= LADO || nb >= LADO || !(r.es(na, nb, RIO) || r.es(na, nb, AGUA))) {
						continue;
					}
					solo = false;
					boolean haciaMar = r.es(na, nb, AGUA) || !r.es(na, nb, RIO);
					// hacia otro río solo en la mitad de las direcciones (la otra la pone el vecino)
					if (!haciaMar && (d[1] < 0 || (d[1] == 0 && d[0] < 0))) {
						continue;
					}
					// en diagonal solo si no hay ya un camino recto, para no hacer triángulos
					if (d[0] != 0 && d[1] != 0 && (r.es(a + d[0], b, (byte) (RIO | AGUA)) || r.es(a, b + d[1], (byte) (RIO | AGUA)))) {
						continue;
					}
					// al mar se llega hasta la mitad, que de ahí sigue la costa
					double fin = haciaMar ? 0.6 : 1;
					raya(cob, pu, pv, ww, k, holgura, a, b, a + d[0] * fin, b + d[1] * fin, medio);
				}
				if (solo) {
					raya(cob, pu, pv, ww, k, holgura, a, b, a, b, medio * 1.6);
				}
			}
		}
		return cob;
	}

	private static void raya(float[] cob, float[] pu, float[] pv, int ww, int k, int holgura, double ax, double az, double bx, double bz,
			double medio) {
		// en píxeles (del lienzo con margen): la rejilla (a, b) va en (a - ANILLO + 0.5) * k - 0.5 + 1
		int x0 = Math.max(0, (int) Math.floor((Math.min(ax, bx) - ANILLO + 0.5 - medio) * k) - holgura);
		int x1 = Math.min(ww - 1, (int) Math.ceil((Math.max(ax, bx) - ANILLO + 0.5 + medio) * k) + 1 + holgura);
		int y0 = Math.max(0, (int) Math.floor((Math.min(az, bz) - ANILLO + 0.5 - medio) * k) - holgura);
		int y1 = Math.min(ww - 1, (int) Math.ceil((Math.max(az, bz) - ANILLO + 0.5 + medio) * k) + 1 + holgura);
		double dx = bx - ax;
		double dz = bz - az;
		double l2 = dx * dx + dz * dz;
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				int c = x + y * ww;
				double px = pu[c] - ax;
				double pz = pv[c] - az;
				double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, (px * dx + pz * dz) / l2));
				double ex = px - t * dx;
				double ez = pz - t * dz;
				double d = Math.sqrt(ex * ex + ez * ez);
				float f = (float) Math.max(0, Math.min(1, (medio - d) * k + 0.5));
				if (f > cob[c]) {
					cob[c] = f;
				}
			}
		}
	}

	/** Altura interpolada con Catmull-Rom en el punto (u, v) de la rejilla. */
	private static double curva(Rejilla r, double u, double v) {
		int a0 = (int) Math.floor(u);
		int b0 = (int) Math.floor(v);
		double tu = u - a0;
		double tv = v - b0;
		double total = 0;
		for (int n = -1; n <= 2; n++) {
			int b = Math.max(0, Math.min(LADO - 1, b0 + n));
			double fila = 0;
			for (int m = -1; m <= 2; m++) {
				int a = Math.max(0, Math.min(LADO - 1, a0 + m));
				fila += r.h(a, b) * peso(m, tu);
			}
			total += fila * peso(n, tv);
		}
		return total;
	}

	/** Peso Catmull-Rom del punto m (-1, 0, 1 o 2) a la distancia t del 0. */
	private static double peso(int m, double t) {
		double t2 = t * t;
		double t3 = t2 * t;
		return switch (m) {
			case -1 -> -0.5 * t3 + t2 - 0.5 * t;
			case 0 -> 1.5 * t3 - 2.5 * t2 + 1;
			case 1 -> -1.5 * t3 + 2 * t2 + 0.5 * t;
			default -> 0.5 * t3 - 0.5 * t2;
		};
	}

	/** Cuánto mar hay en (u, v): las casillas de mar valen 1, interpoladas con suavidad. */
	private static double mar(Rejilla r, double u, double v) {
		int a0 = Math.max(0, Math.min(LADO - 2, (int) Math.floor(u)));
		int b0 = Math.max(0, Math.min(LADO - 2, (int) Math.floor(v)));
		double tu = suave(Math.max(0, Math.min(1, u - a0)));
		double tv = suave(Math.max(0, Math.min(1, v - b0)));
		double m00 = r.es(a0, b0, AGUA) ? 1 : 0;
		double m10 = r.es(a0 + 1, b0, AGUA) ? 1 : 0;
		double m01 = r.es(a0, b0 + 1, AGUA) ? 1 : 0;
		double m11 = r.es(a0 + 1, b0 + 1, AGUA) ? 1 : 0;
		return (m00 * (1 - tu) + m10 * tu) * (1 - tv) + (m01 * (1 - tu) + m11 * tu) * tv;
	}

	private static double suave(double t) {
		return t * t * (3 - 2 * t);
	}

	/** Ruido de valor suave entre -1 y 1, igual en todos los clientes para el mismo punto. */
	static double ruido(double x, double z, int sal) {
		int xi = (int) Math.floor(x);
		int zi = (int) Math.floor(z);
		double fx = suave(x - xi);
		double fz = suave(z - zi);
		double a = azar(xi, zi, sal);
		double b = azar(xi + 1, zi, sal);
		double c = azar(xi, zi + 1, sal);
		double d = azar(xi + 1, zi + 1, sal);
		return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
	}

	private static double azar(int x, int z, int sal) {
		int h = x * 0x27D4EB2D ^ z * 0x165667B1 ^ sal * 0x9E3779B9;
		h ^= h >>> 15;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		h *= 0xC2B2AE35;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / 8388607.5 - 1;
	}
}
