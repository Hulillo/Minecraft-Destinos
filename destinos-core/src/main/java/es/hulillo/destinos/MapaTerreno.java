package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Reparte el mundo en ducados siguiendo el terreno.
 *
 * Las semillas son los mismos centros de ducado que usa el datapack (Geo.centro), pero cada casilla de
 * 64×64 bloques pertenece al ducado al que se llega "más barato" andando: subir montañas, cruzar ríos y
 * atravesar el mar cuesta caro, así que las fronteras acaban en crestas, ríos y costas.
 *
 * El mundo se calcula por ventanas fijas (núcleo de 48×48 casillas + margen de 24). Cada casilla pertenece
 * al núcleo de una sola ventana, así que el resultado es determinista: no depende del orden de exploración.
 * El reino y si es tierra salvaje salen del ducado con la misma fórmula del datapack (Geo.reinoDeDucado).
 */
public final class MapaTerreno {
	public static final int CASILLA = 64;
	public static final int NUCLEO = 48;
	public static final int MARGEN = 24;
	public static final int N = NUCLEO + 2 * MARGEN;
	/** Casillas de mar cruzadas a partir de las cuales una tierra se considera isla sin señor. */
	public static final int MAR_ISLA = 6;
	private static final int RADIO_AJUSTE = 6;
	private static final double DIAGONAL = Math.sqrt(2);

	/** Lo que se sabe del terreno en el centro de una casilla. */
	public record Casilla(int altura, boolean oceano, boolean rio, boolean montana) {
	}

	/** Fuente del terreno: el generador del mundo en el juego, o ruido sintético en las pruebas. */
	public interface Muestreador {
		Casilla muestra(int bloqueX, int bloqueZ);
	}

	/** Territorio de un punto: ducado (celda), reino (celda), salvaje y si la casilla es mar. */
	public record Resultado(int dcx, int dcz, int kx, int kz, boolean salvaje, boolean oceano) {
	}

	private record Semilla(int cx, int cz, int ctx, int ctz) {
	}

	private record Ventana(int[] dueno, boolean[] isla, boolean[] oceano, List<Semilla> semillas) {
	}

	private final Muestreador muestreador;
	private final int sal;
	private final ExecutorService hilo;
	private final ConcurrentHashMap<Long, CompletableFuture<Ventana>> ventanas = new ConcurrentHashMap<>();

	public MapaTerreno(Muestreador muestreador, int sal, ExecutorService hilo) {
		this.muestreador = muestreador;
		this.sal = sal;
		this.hilo = hilo;
	}

	public int sal() {
		return sal;
	}

	public Resultado consulta(int x, int z) {
		int tx = Math.floorDiv(x, CASILLA);
		int tz = Math.floorDiv(z, CASILLA);
		int wx = Math.floorDiv(tx, NUCLEO);
		int wz = Math.floorDiv(tz, NUCLEO);
		Ventana v = ventana(wx, wz).join();
		int i = (tx - (wx * NUCLEO - MARGEN)) + (tz - (wz * NUCLEO - MARGEN)) * N;
		int d = v.dueno()[i];
		if (d < 0) {
			int[] g = Geo.territorioGeometrico(x, z, sal);
			return new Resultado(g[0], g[1], g[2], g[3], g[4] == 1, v.oceano()[i]);
		}
		Semilla s = v.semillas().get(d);
		Geo.Reino r = Geo.reinoDeDucado(s.cx(), s.cz(), s.ctx(), s.ctz(), sal);
		return new Resultado(s.cx(), s.cz(), r.kx(), r.kz(), r.salvaje() || v.isla()[i], v.oceano()[i]);
	}

	/** Pide en segundo plano las ventanas alrededor de un punto para que luego no haya tirones. */
	public void precarga(int x, int z) {
		int wx = Math.floorDiv(Math.floorDiv(x, CASILLA), NUCLEO);
		int wz = Math.floorDiv(Math.floorDiv(z, CASILLA), NUCLEO);
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				ventana(wx + ox, wz + oz);
			}
		}
	}

	public int ventanasCalculadas() {
		return ventanas.size();
	}

	private CompletableFuture<Ventana> ventana(int wx, int wz) {
		long clave = ((long) wx << 32) ^ (wz & 0xffffffffL);
		return ventanas.computeIfAbsent(clave, k -> CompletableFuture.supplyAsync(() -> calcula(wx, wz), hilo));
	}

	private Ventana calcula(int wx, int wz) {
		int bx = wx * NUCLEO - MARGEN;
		int bz = wz * NUCLEO - MARGEN;
		int total = N * N;
		int[] altura = new int[total];
		boolean[] oceano = new boolean[total];
		boolean[] rio = new boolean[total];
		boolean[] montana = new boolean[total];
		for (int j = 0; j < N; j++) {
			for (int i = 0; i < N; i++) {
				Casilla c = muestreador.muestra((bx + i) * CASILLA + CASILLA / 2, (bz + j) * CASILLA + CASILLA / 2);
				int k = i + j * N;
				altura[k] = c.altura();
				oceano[k] = c.oceano();
				rio[k] = c.rio();
				montana[k] = c.montana();
			}
		}

		// Semillas: centros de ducado dentro de la ventana, movidos a tierra firme si caen en el mar.
		List<Semilla> semillas = new ArrayList<>();
		List<Integer> origen = new ArrayList<>();
		int minX = bx * CASILLA;
		int minZ = bz * CASILLA;
		int maxX = (bx + N) * CASILLA;
		int maxZ = (bz + N) * CASILLA;
		for (int cx = Math.floorDiv(minX, Geo.S_DUCADO) - 1; cx <= Math.floorDiv(maxX, Geo.S_DUCADO) + 1; cx++) {
			for (int cz = Math.floorDiv(minZ, Geo.S_DUCADO) - 1; cz <= Math.floorDiv(maxZ, Geo.S_DUCADO) + 1; cz++) {
				int[] c = Geo.centro(cx, cz, Geo.S_DUCADO, sal);
				int ti = Math.floorDiv(c[0], CASILLA) - bx;
				int tj = Math.floorDiv(c[1], CASILLA) - bz;
				if (ti < 0 || tj < 0 || ti >= N || tj >= N) {
					continue;
				}
				int k = ajustaATierra(ti, tj, oceano);
				if (k < 0) {
					continue;
				}
				semillas.add(new Semilla(cx, cz, c[0], c[1]));
				origen.add(k);
			}
		}

		// Dijkstra con varias fuentes: cada casilla se queda con el ducado más barato de alcanzar.
		double[] coste = new double[total];
		Arrays.fill(coste, Double.POSITIVE_INFINITY);
		int[] dueno = new int[total];
		Arrays.fill(dueno, -1);
		int[] mar = new int[total];
		PriorityQueue<double[]> cola = new PriorityQueue<>((a, b) -> {
			int c = Double.compare(a[0], b[0]);
			if (c != 0) {
				return c;
			}
			c = Double.compare(a[2], b[2]);
			return c != 0 ? c : Double.compare(a[1], b[1]);
		});
		for (int s = 0; s < semillas.size(); s++) {
			int k = origen.get(s);
			if (coste[k] > 0) {
				coste[k] = 0;
				dueno[k] = s;
				cola.add(new double[] {0, k, s});
			}
		}
		while (!cola.isEmpty()) {
			double[] e = cola.poll();
			int k = (int) e[1];
			int s = (int) e[2];
			if (e[0] > coste[k] || dueno[k] != s) {
				continue;
			}
			int i = k % N;
			int j = k / N;
			for (int dj = -1; dj <= 1; dj++) {
				for (int di = -1; di <= 1; di++) {
					if (di == 0 && dj == 0) {
						continue;
					}
					int ni = i + di;
					int nj = j + dj;
					if (ni < 0 || nj < 0 || ni >= N || nj >= N) {
						continue;
					}
					int nk = ni + nj * N;
					double paso = 1 + Math.abs(altura[nk] - altura[k]) / 6.0;
					if (montana[nk]) {
						paso += 2.5;
					}
					if (rio[nk] && !rio[k]) {
						paso += 8;
					}
					if (oceano[nk]) {
						paso += 20;
					}
					if (di != 0 && dj != 0) {
						paso *= DIAGONAL;
					}
					double nc = e[0] + paso;
					if (nc < coste[nk] || (nc == coste[nk] && s < dueno[nk])) {
						coste[nk] = nc;
						dueno[nk] = s;
						mar[nk] = mar[k] + (oceano[nk] ? 1 : 0);
						cola.add(new double[] {nc, nk, s});
					}
				}
			}
		}

		boolean[] isla = new boolean[total];
		for (int k = 0; k < total; k++) {
			isla[k] = !oceano[k] && mar[k] >= MAR_ISLA;
		}
		return new Ventana(dueno, isla, oceano, semillas);
	}

	/** Casilla de tierra más cercana a (ti, tj) en un radio pequeño, o -1 si todo es mar. */
	private static int ajustaATierra(int ti, int tj, boolean[] oceano) {
		if (!oceano[ti + tj * N]) {
			return ti + tj * N;
		}
		for (int r = 1; r <= RADIO_AJUSTE; r++) {
			int mejor = -1;
			int mejorD = Integer.MAX_VALUE;
			for (int dj = -r; dj <= r; dj++) {
				for (int di = -r; di <= r; di++) {
					if (Math.max(Math.abs(di), Math.abs(dj)) != r) {
						continue;
					}
					int i = ti + di;
					int j = tj + dj;
					if (i < 0 || j < 0 || i >= N || j >= N || oceano[i + j * N]) {
						continue;
					}
					int d = di * di + dj * dj;
					if (d < mejorD) {
						mejorD = d;
						mejor = i + j * N;
					}
				}
			}
			if (mejor >= 0) {
				return mejor;
			}
		}
		return -1;
	}
}
