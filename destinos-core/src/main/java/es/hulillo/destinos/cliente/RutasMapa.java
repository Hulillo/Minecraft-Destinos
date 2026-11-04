package es.hulillo.destinos.cliente;

import es.hulillo.destinos.Geo;
import es.hulillo.destinos.MapaTerreno;
import es.hulillo.destinos.PaquetePolitica;
import es.hulillo.destinos.PaqueteTerritorio;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Las rutas entre capitales que se dibujan en el mapa: cada capital se une con sus dos vecinas más cercanas por el
 * camino más cómodo por tierra (A* por casillas: rodea el mar y lo que no se ha explorado, prefiere lo llano y evita
 * cruzar ríos), y luego se suaviza. Trabaja con datos que no cambian (las zonas son inmutables), así que va en otro hilo.
 */
final class RutasMapa {
	static final int N = MapaTerreno.NUCLEO;
	static final int CASILLA = MapaTerreno.CASILLA;
	/** Hasta dónde se busca vecina (en bloques) y cuántas casillas mira el A* como mucho por ruta. */
	private static final int LEJOS = 7000;
	private static final int MAX_PASOS = 80000;
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	private RutasMapa() {
	}

	/** Las rutas como listas de puntos x, z, x, z… en casillas del mundo (con decimales). */
	static List<float[]> calcula(Map<Long, PaqueteTerritorio> zonas, List<PaquetePolitica.Capital> capitales) {
		List<float[]> rutas = new ArrayList<>();
		Set<Long> hechas = new HashSet<>();
		for (int a = 0; a < capitales.size(); a++) {
			PaquetePolitica.Capital ca = capitales.get(a);
			// las dos más cercanas
			int b1 = -1;
			int b2 = -1;
			double d1 = Double.MAX_VALUE;
			double d2 = Double.MAX_VALUE;
			for (int b = 0; b < capitales.size(); b++) {
				if (b == a) {
					continue;
				}
				PaquetePolitica.Capital cb = capitales.get(b);
				double d = Math.hypot(ca.x() - cb.x(), ca.z() - cb.z());
				if (d > LEJOS) {
					continue;
				}
				if (d < d1) {
					d2 = d1;
					b2 = b1;
					d1 = d;
					b1 = b;
				} else if (d < d2) {
					d2 = d;
					b2 = b;
				}
			}
			for (int b : new int[] {b1, b2}) {
				if (b < 0 || !hechas.add(Geo.clave(Math.min(a, b), Math.max(a, b)))) {
					continue;
				}
				PaquetePolitica.Capital cb = capitales.get(b);
				float[] r = camino(zonas, Math.floorDiv(ca.x(), CASILLA), Math.floorDiv(ca.z(), CASILLA), Math.floorDiv(cb.x(), CASILLA),
						Math.floorDiv(cb.z(), CASILLA));
				if (r != null) {
					rutas.add(suaviza(suaviza(r)));
				}
			}
		}
		return rutas;
	}

	/** A* de casilla a casilla; null si no hay camino por tierra conocida. */
	private static float[] camino(Map<Long, PaqueteTerritorio> zonas, int ax, int az, int bx, int bz) {
		if (altura(zonas, ax, az) == Integer.MIN_VALUE || altura(zonas, bx, bz) == Integer.MIN_VALUE) {
			return null;
		}
		Map<Long, Double> coste = new HashMap<>();
		Map<Long, Long> de = new HashMap<>();
		PriorityQueue<double[]> abiertos = new PriorityQueue<>((p, q) -> Double.compare(p[0], q[0]));
		long inicio = Geo.clave(ax, az);
		long fin = Geo.clave(bx, bz);
		coste.put(inicio, 0.0);
		abiertos.add(new double[] {Math.hypot(bx - ax, bz - az), ax, az, 0});
		int pasos = 0;
		while (!abiertos.isEmpty() && pasos++ < MAX_PASOS) {
			double[] p = abiertos.poll();
			int x = (int) p[1];
			int z = (int) p[2];
			long k = Geo.clave(x, z);
			if (p[3] > coste.getOrDefault(k, Double.MAX_VALUE)) {
				continue;
			}
			if (k == fin) {
				List<Long> cola = new ArrayList<>();
				for (Long c = fin; c != null; c = de.get(c)) {
					cola.add(c);
				}
				float[] r = new float[cola.size() * 2];
				for (int q = 0; q < cola.size(); q++) {
					long c = cola.get(cola.size() - 1 - q);
					r[q * 2] = Geo.claveX(c) + 0.5f;
					r[q * 2 + 1] = Geo.claveZ(c) + 0.5f;
				}
				return r;
			}
			int h = altura(zonas, x, z);
			for (int[] d : DIRS) {
				int nx = x + d[0];
				int nz = z + d[1];
				int nh = altura(zonas, nx, nz);
				if (nh == Integer.MIN_VALUE) {
					continue;
				}
				double paso = (d[0] != 0 && d[1] != 0 ? 1.414 : 1) * (1 + Math.abs(nh - h) / 6.0) + (rio(zonas, nx, nz) ? 3 : 0);
				double c = p[3] + paso;
				long nk = Geo.clave(nx, nz);
				if (c < coste.getOrDefault(nk, Double.MAX_VALUE)) {
					coste.put(nk, c);
					de.put(nk, k);
					abiertos.add(new double[] {c + Math.hypot(bx - nx, bz - nz), nx, nz, c});
				}
			}
		}
		return null;
	}

	/** Altura de la casilla si es tierra conocida; si es mar o no se sabe, Integer.MIN_VALUE (no se pasa). */
	private static int altura(Map<Long, PaqueteTerritorio> zonas, int tx, int tz) {
		PaqueteTerritorio z = zonas.get(Geo.clave(Math.floorDiv(tx, N), Math.floorDiv(tz, N)));
		if (z == null) {
			return Integer.MIN_VALUE;
		}
		int i = tx - z.wx() * N;
		int j = tz - z.wz() * N;
		return z.ducado(i, j).oceano() ? Integer.MIN_VALUE : z.altura(i, j);
	}

	private static boolean rio(Map<Long, PaqueteTerritorio> zonas, int tx, int tz) {
		PaqueteTerritorio z = zonas.get(Geo.clave(Math.floorDiv(tx, N), Math.floorDiv(tz, N)));
		return z != null && z.rio(tx - z.wx() * N, tz - z.wz() * N);
	}

	/** Una pasada de Chaikin: quita las esquinas de casilla para que la ruta se vea como un camino. */
	private static float[] suaviza(float[] p) {
		int n = p.length / 2;
		if (n < 3) {
			return p;
		}
		float[] r = new float[(n - 1) * 4];
		int k = 0;
		r[k++] = p[0];
		r[k++] = p[1];
		for (int i = 0; i < n - 1; i++) {
			float ax = p[i * 2];
			float az = p[i * 2 + 1];
			float bx = p[i * 2 + 2];
			float bz = p[i * 2 + 3];
			if (i > 0) {
				r[k++] = 0.75f * ax + 0.25f * bx;
				r[k++] = 0.75f * az + 0.25f * bz;
			}
			if (i < n - 2) {
				r[k++] = 0.25f * ax + 0.75f * bx;
				r[k++] = 0.25f * az + 0.75f * bz;
			}
		}
		r[k++] = p[p.length - 2];
		r[k++] = p[p.length - 1];
		return java.util.Arrays.copyOf(r, k);
	}
}
