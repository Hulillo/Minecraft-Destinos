package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Las calles de una capital trazadas sobre el terreno: cada calle es el camino más barato por una rejilla de PASO
 * bloques, donde subir cuesta (mucho si es empinado), el agua cuesta (se cruza por puente, mejor por lo estrecho) y
 * las obras grandes no se pisan. Lo ya trazado sale más barato, así las calles se juntan en vez de ir en paralelo, y
 * la subida a lo alto serpentea sola. Después se suavizan las curvas.
 */
final class TrazadoCapital {
	static final int PASO = 4;
	/** Lo que pesa la pendiente: una de cada cuatro (25 %) ya cuesta el doble. */
	private static final double K_CUESTA = 16;
	/** Más empinado que esto ya es casi una pared: solo si no queda otra. */
	private static final double CUESTA_MAX = 0.8;
	private static final double K_PARED = 40;
	private static final double K_AGUA = 5;
	/** Lo que cuesta ir por una calle ya trazada o por la plaza, frente a abrir camino nuevo. */
	private static final double YA_TRAZADA = 0.35;
	private static final double EN_PLAZA = 0.3;
	/** Cerca del origen y del destino se puede pisar su obra (el camino sale de una puerta y llega a otra). */
	private static final int LLEGADA = 2;
	private static final int[][] VECINOS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {2, 1}, {2, -1}, {-2, 1},
			{-2, -1}, {1, 2}, {1, -2}, {-1, 2}, {-1, -2}};

	private final TerrenoCapital terreno;
	private final int x0;
	private final int z0;
	private final int lado;
	private final int[] alto;
	private final boolean[] agua;
	private final boolean[] prohibido;
	private final boolean[] trazado;
	private final boolean[] plaza;

	/**
	 * La rejilla del círculo de radio alrededor de (cx, cz): fuera de él y dentro de las obras (con un margen) no se
	 * pasa; la plaza, de radio rPlaza en (px, pz), sale barata.
	 */
	TrazadoCapital(TerrenoCapital terreno, int cx, int cz, int radio, List<BoundingBox> obras, int px, int pz, int rPlaza) {
		this.terreno = terreno;
		int n = Math.ceilDiv(radio, PASO);
		x0 = cx - n * PASO;
		z0 = cz - n * PASO;
		lado = 2 * n + 1;
		alto = new int[lado * lado];
		agua = new boolean[lado * lado];
		prohibido = new boolean[lado * lado];
		trazado = new boolean[lado * lado];
		plaza = new boolean[lado * lado];
		for (int i = 0; i < lado; i++) {
			for (int j = 0; j < lado; j++) {
				int x = x(i);
				int z = z(j);
				int k = i * lado + j;
				alto[k] = terreno.alto(x, z);
				agua[k] = terreno.agua(x, z);
				long dx = x - cx;
				long dz = z - cz;
				prohibido[k] = dx * dx + dz * dz > (long) radio * radio;
				for (BoundingBox b : obras) {
					if (x >= b.minX() - 2 && x <= b.maxX() + 2 && z >= b.minZ() - 2 && z <= b.maxZ() + 2) {
						prohibido[k] = true;
					}
				}
				long qx = x - px;
				long qz = z - pz;
				plaza[k] = qx * qx + qz * qz <= (long) rPlaza * rPlaza;
			}
		}
	}

	private int x(int i) {
		return x0 + i * PASO;
	}

	private int z(int j) {
		return z0 + j * PASO;
	}

	private int i(int x) {
		return Math.clamp(Math.round((x - x0) / (float) PASO), 0, lado - 1);
	}

	private int j(int z) {
		return Math.clamp(Math.round((z - z0) / (float) PASO), 0, lado - 1);
	}

	/**
	 * La calle más barata de (ax, az) a (bx, bz), ya suavizada, en bloques; vacía si no hay camino. Lo que traza
	 * queda como calle para las siguientes.
	 */
	List<int[]> calle(int ax, int az, int bx, int bz) {
		int inicio = i(ax) * lado + j(az);
		int fin = i(bx) * lado + j(bz);
		int fi = fin / lado;
		int fj = fin % lado;
		int si = inicio / lado;
		int sj = inicio % lado;
		double[] coste = new double[lado * lado];
		Arrays.fill(coste, Double.MAX_VALUE);
		int[] desde = new int[lado * lado];
		Arrays.fill(desde, -1);
		PriorityQueue<double[]> cola = new PriorityQueue<>((p, q) -> Double.compare(p[0], q[0]));
		coste[inicio] = 0;
		cola.add(new double[] {0, inicio});
		while (!cola.isEmpty()) {
			double[] e = cola.poll();
			int k = (int) e[1];
			if (k == fin) {
				break;
			}
			int ki = k / lado;
			int kj = k % lado;
			if (e[0] > coste[k] + estimado(ki, kj, fi, fj)) {
				continue;
			}
			for (int[] d : VECINOS) {
				int ni = ki + d[0];
				int nj = kj + d[1];
				if (ni < 0 || nj < 0 || ni >= lado || nj >= lado) {
					continue;
				}
				int m = ni * lado + nj;
				// cerca de los extremos se puede pisar una obra (se sale de su puerta y se llega a la otra)
				boolean llega = Math.abs(ni - fi) <= LLEGADA && Math.abs(nj - fj) <= LLEGADA
						|| Math.abs(ni - si) <= LLEGADA && Math.abs(nj - sj) <= LLEGADA;
				if (prohibido[m] && !llega) {
					continue;
				}
				// los saltos de caballo, solo si la casilla de en medio también se puede pisar (no cortan esquinas)
				if (Math.abs(d[0]) + Math.abs(d[1]) == 3) {
					int mi = ki + Integer.signum(d[0]) * (Math.abs(d[0]) == 2 ? 1 : 0);
					int mj = kj + Integer.signum(d[1]) * (Math.abs(d[1]) == 2 ? 1 : 0);
					if (prohibido[mi * lado + mj] && !llega) {
						continue;
					}
				}
				double c = coste[k] + paso(k, m, Math.hypot(d[0], d[1]) * PASO);
				if (c < coste[m]) {
					coste[m] = c;
					desde[m] = k;
					cola.add(new double[] {c + estimado(ni, nj, fi, fj), m});
				}
			}
		}
		if (desde[fin] == -1 && fin != inicio) {
			return List.of();
		}
		List<int[]> puntos = new ArrayList<>();
		for (int k = fin; k != -1; k = desde[k]) {
			trazado[k] = true;
			puntos.add(0, new int[] {x(k / lado), z(k % lado)});
		}
		puntos.set(0, new int[] {ax, az});
		puntos.set(puntos.size() - 1, new int[] {bx, bz});
		return suaviza(puntos);
	}

	private double estimado(int i, int j, int fi, int fj) {
		return Math.hypot(i - fi, j - fj) * PASO * YA_TRAZADA;
	}

	/** Lo que cuesta ir de la casilla a a la b (a largo bloques). */
	private double paso(int a, int b, double largo) {
		double cuesta = Math.abs(alto[b] - alto[a]) / largo;
		double c = largo * (1 + K_CUESTA * cuesta * cuesta);
		if (cuesta > CUESTA_MAX) {
			c += largo * K_PARED;
		}
		if (agua[b]) {
			c += largo * K_AGUA;
		}
		if (trazado[b]) {
			c *= YA_TRAZADA;
		} else if (plaza[b]) {
			c *= EN_PLAZA;
		}
		return c;
	}

	/** Curvas suaves (dos pasadas de Chaikin, que redondean las esquinas sin mover los extremos). */
	private static List<int[]> suaviza(List<int[]> p) {
		List<double[]> q = new ArrayList<>();
		for (int[] a : p) {
			q.add(new double[] {a[0], a[1]});
		}
		for (int pasada = 0; pasada < 2 && q.size() > 2; pasada++) {
			List<double[]> r = new ArrayList<>();
			r.add(q.get(0));
			for (int k = 0; k + 1 < q.size(); k++) {
				double[] a = q.get(k);
				double[] b = q.get(k + 1);
				r.add(new double[] {a[0] * 0.75 + b[0] * 0.25, a[1] * 0.75 + b[1] * 0.25});
				r.add(new double[] {a[0] * 0.25 + b[0] * 0.75, a[1] * 0.25 + b[1] * 0.75});
			}
			r.add(q.get(q.size() - 1));
			q = r;
		}
		// y a tramos de unos 6 bloques (más cortos hacen muchas piezas; más largos, curvas de polígono)
		List<int[]> s = new ArrayList<>();
		double acum = 0;
		s.add(new int[] {(int) Math.round(q.get(0)[0]), (int) Math.round(q.get(0)[1])});
		for (int k = 1; k < q.size(); k++) {
			acum += Math.hypot(q.get(k)[0] - q.get(k - 1)[0], q.get(k)[1] - q.get(k - 1)[1]);
			if (acum >= 6 || k == q.size() - 1) {
				s.add(new int[] {(int) Math.round(q.get(k)[0]), (int) Math.round(q.get(k)[1])});
				acum = 0;
			}
		}
		return s;
	}

	/** Las casillas (en bloques) que ocupan las calles dadas, con su ancho a cada lado. */
	static Set<Long> ocupa(List<List<int[]>> calles, int ancho) {
		Set<Long> r = new HashSet<>();
		for (List<int[]> c : calles) {
			for (int k = 0; k + 1 < c.size(); k++) {
				int[] a = c.get(k);
				int[] b = c.get(k + 1);
				int pasos = Math.max(1, Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1])));
				for (int t = 0; t <= pasos; t++) {
					int x = a[0] + Math.round((b[0] - a[0]) * (float) t / pasos);
					int z = a[1] + Math.round((b[1] - a[1]) * (float) t / pasos);
					for (int dx = -ancho; dx <= ancho; dx++) {
						for (int dz = -ancho; dz <= ancho; dz++) {
							r.add(Geo.clave(x + dx, z + dz));
						}
					}
				}
			}
		}
		return r;
	}

	TerrenoCapital terreno() {
		return terreno;
	}
}
