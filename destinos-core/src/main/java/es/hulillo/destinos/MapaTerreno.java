package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.TreeMap;
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
	/** Casillas de mar desde la costa que siguen siendo del ducado; más allá, mar internacional. */
	public static final int AGUAS_TERRITORIALES = 4;
	private static final int RADIO_AJUSTE = 6;
	private static final double DIAGONAL = Math.sqrt(2);
	/** Coste extra de entrar en un río: los ríos son la frontera natural más clara. */
	private static final double COSTE_RIO = 12;
	private static final double COSTE_MONTANA = 2.5;
	private static final double COSTE_MAR = 20;
	/** Cada tantos bloques de desnivel entre casillas suman 1 al coste. */
	private static final double DESNIVEL_POR_PUNTO = 6.0;
	/** Coste extra de pasar a otro tipo de bioma: un desierto entero tiende a quedar en el mismo lado. */
	private static final double COSTE_BIOMA = 5;
	/** Las tierras salvajes se extienden más despacio: los ducados con señor llegan antes a ríos y crestas. */
	private static final double FRENO_SALVAJE = 1.6;
	/** Un ducado salvaje con un pueblo a esta distancia de su centro pasa a tener señor. */
	public static final int RADIO_PUEBLO = 900;

	/** Lo que se sabe del terreno en el centro de una casilla. */
	/** familia: tipo de bioma (desierto, selva, bosque…) para que las fronteras prefieran los cambios de paisaje. */
	public record Casilla(int altura, boolean oceano, boolean rio, boolean montana, int familia) {
	}

	/** Fuente del terreno: el generador del mundo en el juego, o ruido sintético en las pruebas. */
	public interface Muestreador {
		Casilla muestra(int bloqueX, int bloqueZ);

		/** ¿Puede aparecer un pueblo a menos de `radio` bloques de (x, z)? */
		default boolean hayPueblo(int bloqueX, int bloqueZ, int radio) {
			return false;
		}

		/** Sitios {x, z} donde saldrá un pueblo dentro del rectángulo [x0, x1) × [z0, z1). */
		default List<int[]> pueblos(int x0, int z0, int x1, int z1) {
			return List.of();
		}

		/** Si en (x, z) hay agua (mar, río o lago) en vez de tierra donde construir. */
		default boolean agua(int bloqueX, int bloqueZ) {
			return muestra(bloqueX, bloqueZ).oceano();
		}

		/** Si en (x, z) hay una cultura de montaña (nórdica, andina o de la estepa), como la elige el datapack. */
		default boolean culturaDeMontana(int bloqueX, int bloqueZ) {
			return false;
		}
	}

	/**
	 * La capital de un reino: el ducado donde está y el sitio en bloques. montana: el reino es de cultura de
	 * montaña y ha preferido levantarla en las alturas. nivel: lo rica y grande que es (0 pobre, 1 normal, 2 rica),
	 * por los recursos de su ducado y los ducados del reino; decide su muralla.
	 */
	public record Capital(int dcx, int dcz, int x, int z, boolean montana, int nivel) {
	}

	/** Territorio de un punto: ducado (celda), reino (celda), salvaje y si la casilla es mar. */
	public record Resultado(int dcx, int dcz, int kx, int kz, boolean salvaje, boolean oceano) {
	}

	private record Semilla(int cx, int cz, int ctx, int ctz, Geo.Reino reino, boolean salvaje) {
	}

	/**
	 * recurso: comida, mineral y madera de cada casilla, juntos en un byte (ver recurso()). pueblos: casillas del
	 * núcleo donde sale un pueblo.
	 */
	private record Ventana(int[] dueno, boolean[] isla, boolean[] oceano, boolean[] dificil, int[] mar, int[] altura,
			byte[] recurso, List<Semilla> semillas, int[] pueblos) {
	}

	/**
	 * Un ducado visto en el núcleo de una ventana: su reino del mapa base, cuántas casillas de tierra tiene ahí,
	 * lo que dan esas casillas (comida, mineral, madera) y cuántos pueblos hay en ellas.
	 */
	public record DucadoVisto(int dcx, int dcz, int kx, int kz, boolean salvaje, int casillas, int comida, int mineral,
			int madera, int pueblos) {
	}

	/** Dos ducados que se tocan por tierra: casillas de frontera y cuántas cruzan un río o una montaña. */
	public record Frontera(int dcx1, int dcz1, int dcx2, int dcz2, int casillas, int dificiles) {
	}

	/** Ducados y fronteras del núcleo de una ventana: lo que necesita la política para saber quién toca a quién. */
	public record Vecindario(List<DucadoVisto> ducados, List<Frontera> fronteras) {
	}

	/** Lo que se pinta en una casilla: su territorio y, si es mar, si ya está lejos de toda costa. */
	public record Entrada(Resultado territorio, boolean internacional) {
	}

	/**
	 * El núcleo de una ventana, listo para pintarlo en un mapa: cada casilla (i + j * NUCLEO, empezando en la
	 * casilla wx * NUCLEO, wz * NUCLEO) apunta a una entrada de la paleta y lleva la altura del suelo.
	 */
	public record Zona(int wx, int wz, List<Entrada> paleta, short[] indice, short[] altura) {
	}

	private final Muestreador muestreador;
	private final int sal;
	private final ExecutorService hilo;
	private final ConcurrentHashMap<Long, CompletableFuture<Ventana>> ventanas = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, CompletableFuture<Capital>> capitales = new ConcurrentHashMap<>();

	public MapaTerreno(Muestreador muestreador, int sal, ExecutorService hilo) {
		this.muestreador = muestreador;
		this.sal = sal;
		this.hilo = hilo;
	}

	public int sal() {
		return sal;
	}

	/** Territorio de un punto. Bloquea hasta que su zona esté calculada (para pruebas y herramientas). */
	public Resultado consulta(int x, int z) {
		return resultado(ventanaDe(x, z).join(), x, z);
	}

	/**
	 * Territorio de un punto sin bloquear: null si su zona aún se está calculando (y la pide).
	 * Es lo que usa el juego, para que el servidor no se congele esperando al cálculo.
	 */
	public Resultado consultaSiLista(int x, int z) {
		CompletableFuture<Ventana> f = ventanaDe(x, z);
		if (!f.isDone()) {
			return null;
		}
		if (f.isCompletedExceptionally()) {
			// si el terreno no se pudo leer, mejor fronteras geométricas que ninguna
			return geometrico(x, z, false);
		}
		return resultado(f.join(), x, z);
	}

	/** Pide en segundo plano las ventanas alrededor de un punto para que luego no haya tirones. */
	public void precarga(int x, int z) {
		int wx = indiceVentana(x);
		int wz = indiceVentana(z);
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				ventana(wx + ox, wz + oz);
			}
		}
	}

	/** Hasta dónde se calcula en segundo plano para la política (en ventanas alrededor del jugador). */
	public static final int RADIO_LEJOS = 2;

	/**
	 * Para que la política tenga reinos con los que moverse, se calculan también las ventanas del anillo de
	 * fuera (5×5 en total), pero sin quitar sitio a lo cercano: solo cuando las 3×3 de alrededor están hechas,
	 * y de una en una.
	 */
	public void precargaLejos(int x, int z) {
		int wx = indiceVentana(x);
		int wz = indiceVentana(z);
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				CompletableFuture<Ventana> f = ventanas.get(clave(wx + ox, wz + oz));
				if (f == null || !f.isDone()) {
					return;
				}
			}
		}
		for (int ox = -RADIO_LEJOS; ox <= RADIO_LEJOS; ox++) {
			for (int oz = -RADIO_LEJOS; oz <= RADIO_LEJOS; oz++) {
				CompletableFuture<Ventana> f = ventanas.get(clave(wx + ox, wz + oz));
				if (f == null) {
					ventana(wx + ox, wz + oz);
					return;
				}
				if (!f.isDone()) {
					return;
				}
			}
		}
	}

	/** El núcleo de la ventana (wx, wz) si ya está calculado, o null (sin pedirlo). */
	public Zona zonaSiLista(int wx, int wz) {
		CompletableFuture<Ventana> f = ventanas.get(clave(wx, wz));
		if (f == null || !f.isDone() || f.isCompletedExceptionally()) {
			return null;
		}
		Ventana v = f.join();
		LinkedHashMap<Entrada, Integer> paleta = new LinkedHashMap<>();
		short[] indice = new short[NUCLEO * NUCLEO];
		short[] altura = new short[NUCLEO * NUCLEO];
		for (int j = 0; j < NUCLEO; j++) {
			for (int i = 0; i < NUCLEO; i++) {
				int x = (wx * NUCLEO + i) * CASILLA;
				int z = (wz * NUCLEO + j) * CASILLA;
				int k = (i + MARGEN) + (j + MARGEN) * N;
				Resultado r = resultado(v, x, z);
				Entrada e = new Entrada(r, r.oceano() && v.mar()[k] > AGUAS_TERRITORIALES);
				indice[i + j * NUCLEO] = (short) (int) paleta.computeIfAbsent(e, c -> paleta.size());
				altura[i + j * NUCLEO] = (short) v.altura()[k];
			}
		}
		return new Zona(wx, wz, List.copyOf(paleta.keySet()), indice, altura);
	}

	/** Claves de las ventanas ya calculadas (sin error). */
	public List<Long> ventanasListas() {
		List<Long> res = new ArrayList<>();
		ventanas.forEach((k, f) -> {
			if (f.isDone() && !f.isCompletedExceptionally()) {
				res.add(k);
			}
		});
		return res;
	}

	/** Ducados y fronteras de la ventana (wx, wz) si ya está calculada, o null. */
	public Vecindario vecindarioSiListo(long clave) {
		CompletableFuture<Ventana> f = ventanas.get(clave);
		if (f == null || !f.isDone() || f.isCompletedExceptionally()) {
			return null;
		}
		Ventana v = f.join();
		int ns = v.semillas().size();
		int[] casillas = new int[ns];
		int[][] recursos = new int[ns][4];
		Map<Long, int[]> pares = new TreeMap<>();
		for (int j = MARGEN; j < MARGEN + NUCLEO; j++) {
			for (int i = MARGEN; i < MARGEN + NUCLEO; i++) {
				int k = i + j * N;
				int a = v.dueno()[k];
				if (a < 0 || v.oceano()[k]) {
					continue;
				}
				casillas[a]++;
				int rec = v.recurso()[k];
				recursos[a][0] += rec & 7;
				recursos[a][1] += rec >> 3 & 3;
				recursos[a][2] += rec >> 5 & 3;
				// este y sur siempre; oeste y norte solo si el vecino es margen (si es núcleo ya se contó desde él)
				for (int[] d : new int[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
					int ni = i + d[0];
					int nj = j + d[1];
					boolean nucleo = ni >= MARGEN && nj >= MARGEN && ni < MARGEN + NUCLEO && nj < MARGEN + NUCLEO;
					if ((d[0] < 0 || d[1] < 0) && nucleo) {
						continue;
					}
					int nk = ni + nj * N;
					int b = v.dueno()[nk];
					if (b < 0 || b == a || v.oceano()[nk]) {
						continue;
					}
					int[] par = pares.computeIfAbsent((long) Math.min(a, b) * ns + Math.max(a, b), c -> new int[2]);
					par[0]++;
					if (v.dificil()[k] || v.dificil()[nk]) {
						par[1]++;
					}
				}
			}
		}
		for (int k : v.pueblos()) {
			int a = v.dueno()[k];
			if (a >= 0 && !v.oceano()[k]) {
				recursos[a][3]++;
			}
		}
		List<DucadoVisto> ducados = new ArrayList<>();
		for (int s = 0; s < ns; s++) {
			Semilla se = v.semillas().get(s);
			if (casillas[s] > 0) {
				int[] r = recursos[s];
				ducados.add(new DucadoVisto(se.cx(), se.cz(), se.reino().kx(), se.reino().kz(), se.salvaje(), casillas[s],
						r[0], r[1], r[2], r[3]));
			}
		}
		List<Frontera> fronteras = new ArrayList<>();
		pares.forEach((clavePar, par) -> {
			Semilla a = v.semillas().get((int) (clavePar / ns));
			Semilla b = v.semillas().get((int) (clavePar % ns));
			fronteras.add(new Frontera(a.cx(), a.cz(), b.cx(), b.cz(), par[0], par[1]));
		});
		return new Vecindario(ducados, fronteras);
	}

	private Resultado resultado(Ventana v, int x, int z) {
		int tx = Math.floorDiv(x, CASILLA);
		int tz = Math.floorDiv(z, CASILLA);
		int i = (tx - origenVentana(indiceVentana(x))) + (tz - origenVentana(indiceVentana(z))) * N;
		int d = v.dueno()[i];
		if (d < 0) {
			return geometrico(x, z, v.oceano()[i]);
		}
		Semilla s = v.semillas().get(d);
		return new Resultado(s.cx(), s.cz(), s.reino().kx(), s.reino().kz(), s.salvaje() || v.isla()[i], v.oceano()[i]);
	}

	/** Lo que diría el datapack solo, sin mirar el terreno. */
	private Resultado geometrico(int x, int z, boolean oceano) {
		int[] g = Geo.territorioGeometrico(x, z, sal);
		return new Resultado(g[0], g[1], g[2], g[3], g[4] == 1, oceano);
	}

	/** Ventana cuyo núcleo contiene la coordenada de bloque (en un eje). */
	public static int indiceVentana(int bloque) {
		return Math.floorDiv(Math.floorDiv(bloque, CASILLA), NUCLEO);
	}

	/** Primera casilla (con margen) de la ventana w en un eje. */
	private static int origenVentana(int w) {
		return w * NUCLEO - MARGEN;
	}

	private CompletableFuture<Ventana> ventanaDe(int x, int z) {
		return ventana(indiceVentana(x), indiceVentana(z));
	}

	public int ventanasCalculadas() {
		return ventanas.size();
	}

	public static long clave(int wx, int wz) {
		return ((long) wx << 32) ^ (wz & 0xffffffffL);
	}

	private CompletableFuture<Ventana> ventana(int wx, int wz) {
		return ventanas.computeIfAbsent(clave(wx, wz), k -> CompletableFuture.supplyAsync(() -> calcula(wx, wz), hilo));
	}

	private Ventana calcula(int wx, int wz) {
		int bx = origenVentana(wx);
		int bz = origenVentana(wz);
		int total = N * N;
		Casilla[] terreno = muestrea(bx, bz);
		List<Semilla> semillas = new ArrayList<>();
		List<Integer> origen = new ArrayList<>();
		buscaSemillas(bx, bz, terreno, semillas, origen);

		// Dijkstra con varias fuentes: cada casilla se queda con el ducado más barato de alcanzar.
		double[] coste = new double[total];
		Arrays.fill(coste, Double.POSITIVE_INFINITY);
		int[] dueno = new int[total];
		Arrays.fill(dueno, -1);
		int[] mar = new int[total];
		PriorityQueue<double[]> cola = new PriorityQueue<>((p, q) -> {
			int c = Double.compare(p[0], q[0]);
			if (c != 0) {
				return c;
			}
			c = Double.compare(p[2], q[2]);
			return c != 0 ? c : Double.compare(p[1], q[1]);
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
			boolean salvaje = semillas.get(s).salvaje();
			int i = k % N;
			int j = k / N;
			for (int dj = -1; dj <= 1; dj++) {
				for (int di = -1; di <= 1; di++) {
					int ni = i + di;
					int nj = j + dj;
					if ((di == 0 && dj == 0) || ni < 0 || nj < 0 || ni >= N || nj >= N) {
						continue;
					}
					int nk = ni + nj * N;
					double nc = e[0] + costePaso(terreno[k], terreno[nk], di != 0 && dj != 0, salvaje);
					if (nc < coste[nk] || (nc == coste[nk] && s < dueno[nk])) {
						coste[nk] = nc;
						dueno[nk] = s;
						mar[nk] = mar[k] + (terreno[nk].oceano() ? 1 : 0);
						cola.add(new double[] {nc, nk, s});
					}
				}
			}
		}

		boolean[] isla = new boolean[total];
		for (int k = 0; k < total; k++) {
			isla[k] = !terreno[k].oceano() && mar[k] >= MAR_ISLA;
		}
		boolean[] oceano = new boolean[total];
		int[] altura = new int[total];
		boolean[] dificil = new boolean[total];
		byte[] recurso = new byte[total];
		for (int k = 0; k < total; k++) {
			oceano[k] = terreno[k].oceano();
			altura[k] = terreno[k].altura();
			dificil[k] = terreno[k].rio() || terreno[k].montana();
			recurso[k] = recurso(terreno[k]);
		}
		// pueblos del núcleo (los del margen ya los contará la ventana vecina)
		int x0 = (bx + MARGEN) * CASILLA;
		int z0 = (bz + MARGEN) * CASILLA;
		List<int[]> sitios = muestreador.pueblos(x0, z0, x0 + NUCLEO * CASILLA, z0 + NUCLEO * CASILLA);
		int[] pueblos = new int[sitios.size()];
		for (int p = 0; p < pueblos.length; p++) {
			pueblos[p] = (Math.floorDiv(sitios.get(p)[0], CASILLA) - bx) + (Math.floorDiv(sitios.get(p)[1], CASILLA) - bz) * N;
		}
		return new Ventana(dueno, isla, oceano, dificil, mar, altura, recurso, semillas, pueblos);
	}

	// Familias de MuestreadorMundo.FAMILIAS (+1): 1 océano, 2 río, 3 nieve, 4 badlands, 5 desierto, 6 selva,
	// 7 sabana, 8 pantano, 9 taiga, 10 montaña, 11 bosque, 12 llanura; 0 otra cosa
	private static final int[] COMIDA = {2, 0, 3, 0, 0, 0, 2, 2, 2, 1, 0, 2, 3};
	private static final int[] MADERA = {1, 0, 0, 0, 0, 0, 3, 1, 2, 3, 0, 3, 0};

	/**
	 * Lo que da una casilla: comida (0-4, llanuras y ríos), mineral (0-3, montañas y badlands) y madera (0-3,
	 * bosques). Va en un byte: comida en los bits 0-2, mineral en 3-4 y madera en 5-6.
	 */
	static byte recurso(Casilla c) {
		if (c.oceano()) {
			return 0;
		}
		int f = c.familia() >= 0 && c.familia() < COMIDA.length ? c.familia() : 0;
		int comida = COMIDA[f] + (c.rio() ? 2 : 0);
		int mineral = f == 4 ? 3 : f == 3 || f == 5 ? 1 : 0;
		if (c.montana()) {
			comida--;
			mineral = 3;
		}
		comida = Math.max(0, Math.min(4, comida));
		return (byte) (comida | mineral << 3 | MADERA[f] << 5);
	}

	// ------------------------------------------------------------------ capitales

	/** Ducados alrededor del centro del reino entre los que se elige la capital (en cada eje, a cada lado). */
	private static final int RADIO_CAPITAL = 3;
	/** Las culturas de montaña la levantan en las alturas una de cada tantas veces (en %). */
	private static final int PROB_CAPITAL_MONTANA = 50;
	private static final int K_CAPITAL = 7368787;
	private static final int PASO_CAPITAL = 128;
	/** Ducados mejor puntuados en los que se mira el agua de cerca, y separación de esos puntos. */
	private static final int CANDIDATAS_CAPITAL = 4;
	private static final int PASO_AGUA = 40;
	/** Riqueza (ver nivel) desde la que una capital es normal y rica. */
	private static final double RIQUEZA_NORMAL = 5.0;
	private static final double RIQUEZA_RICA = 7.0;

	/**
	 * La capital del reino (kx, kz) sin bloquear: null si aún se está eligiendo (y la pide), vacío si el reino no
	 * tiene ningún ducado donde ponerla.
	 */
	private final Map<Long, CompletableFuture<Capital>> enLinea = new ConcurrentHashMap<>();

	public Optional<Capital> capitalSiLista(int kx, int kz) {
		CompletableFuture<Capital> f = capitales.computeIfAbsent(clave(kx, kz),
				k -> CompletableFuture.supplyAsync(() -> calculaCapital(kx, kz), hilo));
		if (!f.isDone()) {
			return null;
		}
		return f.isCompletedExceptionally() ? Optional.empty() : Optional.ofNullable(f.join());
	}

	/**
	 * Capital del reino, ya calculada o calculándola aquí mismo (para la generación del mundo, pruebas y
	 * herramientas). No espera al hilo del terreno, que puede estar ocupado con zonas enteras: si la está
	 * calculando él, se calcula otra vez aquí (sale igual).
	 */
	public Capital capital(int kx, int kz) {
		long k = clave(kx, kz);
		CompletableFuture<Capital> f = capitales.get(k);
		if (f != null && f.isDone() && !f.isCompletedExceptionally()) {
			return f.join();
		}
		// varios hilos de generación pueden pedir la misma capital a la vez: la calcula el primero y los demás
		// le esperan (sale en milisegundos), en vez de calcularla todos
		CompletableFuture<Capital> mia = new CompletableFuture<>();
		CompletableFuture<Capital> otra = enLinea.putIfAbsent(k, mia);
		if (otra != null) {
			return otra.join();
		}
		try {
			Capital c = calculaCapital(kx, kz);
			capitales.put(k, CompletableFuture.completedFuture(c));
			mia.complete(c);
			return c;
		} catch (RuntimeException e) {
			mia.completeExceptionally(e);
			enLinea.remove(k);
			throw e;
		}
	}

	/**
	 * Elige la capital mirando los ducados con señor del reino cerca de su centro: gana el que tiene más tierra
	 * llana, río y comida, y está más cerca del centro. Si el reino es de cultura de montaña, a veces prefiere las
	 * alturas. Solo depende de la sal y del terreno, así que sale igual se explore como se explore.
	 */
	private Capital calculaCapital(int kx, int kz) {
		int[] k = Geo.centro(kx, kz, Geo.S_REINO, sal + Geo.K_REINO);
		boolean montana = muestreador.culturaDeMontana(k[0], k[1])
				&& Math.floorMod(Geo.hash(kx, kz, sal + K_CAPITAL), 100) < PROB_CAPITAL_MONTANA;
		int c0x = Math.floorDiv(k[0], Geo.S_DUCADO);
		int c0z = Math.floorDiv(k[1], Geo.S_DUCADO);
		List<Candidata> candidatas = new ArrayList<>();
		for (int cx = c0x - RADIO_CAPITAL; cx <= c0x + RADIO_CAPITAL; cx++) {
			for (int cz = c0z - RADIO_CAPITAL; cz <= c0z + RADIO_CAPITAL; cz++) {
				int[] c = Geo.centro(cx, cz, Geo.S_DUCADO, sal);
				Geo.Reino r = Geo.reinoDeDucado(cx, cz, c[0], c[1], sal);
				if (r.kx() != kx || r.kz() != kz || r.salvaje()) {
					continue;
				}
				// 3×3 puntos alrededor del centro del ducado
				double puntos = 0;
				int recursos = 0;
				int tierra = 0;
				int alta = Integer.MIN_VALUE;
				int baja = Integer.MAX_VALUE;
				List<int[]> sitios = new ArrayList<>();
				for (int dz = -1; dz <= 1; dz++) {
					for (int dx = -1; dx <= 1; dx++) {
						int x = c[0] + dx * PASO_CAPITAL;
						int z = c[1] + dz * PASO_CAPITAL;
						Casilla t = muestreador.muestra(x, z);
						if (t.oceano()) {
							continue;
						}
						tierra++;
						alta = Math.max(alta, t.altura());
						baja = Math.min(baja, t.altura());
						int rec = recurso(t);
						recursos += (rec & 7) + (rec >> 3 & 3) + (rec >> 5 & 3);
						puntos += (rec & 7) * 0.5;
						if (t.rio()) {
							puntos += 1;
						}
						if (t.montana()) {
							puntos += montana ? 2 : -1;
						}
						sitios.add(new int[] {x, z, t.montana() ? 1 : 0});
					}
				}
				if (tierra < 5) {
					continue;
				}
				if (!montana) {
					puntos -= (alta - baja) / 12.0;
				}
				double dx = c[0] - k[0];
				double dz = c[1] - k[1];
				puntos += tierra * 0.5 - Math.sqrt(dx * dx + dz * dz) / 600.0;
				candidatas.add(new Candidata(cx, cz, puntos, sitios, recursos));
			}
		}
		// las mejores, mirando de cerca el agua alrededor de cada sitio posible: una capital entre islas o junto a
		// un lago se queda en la plaza, porque las calles sobre el agua no se ponen
		candidatas.sort((a, b) -> Double.compare(b.puntos(), a.puntos()));
		Capital mejor = null;
		double puntosMejor = Double.NEGATIVE_INFINITY;
		int ducadosReino = candidatas.size();
		for (int i = 0; i < Math.min(CANDIDATAS_CAPITAL, candidatas.size()); i++) {
			Candidata cand = candidatas.get(i);
			boolean hayMontana = cand.sitios().stream().anyMatch(s -> s[2] == 1);
			for (int[] s : cand.sitios()) {
				if (montana && hayMontana && s[2] == 0) {
					continue;
				}
				double puntos = cand.puntos() - aguaAlrededor(s[0], s[1]) * 0.6;
				if (puntos > puntosMejor) {
					puntosMejor = puntos;
					mejor = new Capital(cand.cx(), cand.cz(), s[0], s[1], montana && s[2] == 1, nivel(cand.recursos(), ducadosReino));
				}
			}
		}
		return mejor;
	}

	private record Candidata(int cx, int cz, double puntos, List<int[]> sitios, int recursos) {
	}

	/**
	 * Riqueza de la capital: recursos medios por casilla de su ducado (0-10, lo normal 3-5) más el tamaño del
	 * reino cerca de su centro (ducados con señor, hasta 49).
	 */
	static int nivel(int recursos, int ducados) {
		double riqueza = recursos / 9.0 + ducados / 8.0;
		return riqueza < RIQUEZA_NORMAL ? 0 : riqueza < RIQUEZA_RICA ? 1 : 2;
	}

	/** Cuántos de los 5×5 puntos alrededor de (x, z), hasta donde llega la capital, son agua. */
	private int aguaAlrededor(int x, int z) {
		int agua = 0;
		for (int dz = -2; dz <= 2; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				if (muestreador.agua(x + dx * PASO_AGUA, z + dz * PASO_AGUA)) {
					agua++;
				}
			}
		}
		return agua;
	}


	/** Terreno en el centro de cada casilla de la ventana (con margen). */
	private Casilla[] muestrea(int bx, int bz) {
		Casilla[] terreno = new Casilla[N * N];
		for (int j = 0; j < N; j++) {
			for (int i = 0; i < N; i++) {
				terreno[i + j * N] = muestreador.muestra((bx + i) * CASILLA + CASILLA / 2, (bz + j) * CASILLA + CASILLA / 2);
			}
		}
		return terreno;
	}

	/** Centros de ducado dentro de la ventana, movidos a tierra firme si caen en el mar. */
	private void buscaSemillas(int bx, int bz, Casilla[] terreno, List<Semilla> semillas, List<Integer> origen) {
		int c0x = Math.floorDiv(bx * CASILLA, Geo.S_DUCADO) - 1;
		int c1x = Math.floorDiv((bx + N) * CASILLA, Geo.S_DUCADO) + 1;
		int c0z = Math.floorDiv(bz * CASILLA, Geo.S_DUCADO) - 1;
		int c1z = Math.floorDiv((bz + N) * CASILLA, Geo.S_DUCADO) + 1;
		for (int cx = c0x; cx <= c1x; cx++) {
			for (int cz = c0z; cz <= c1z; cz++) {
				int[] c = Geo.centro(cx, cz, Geo.S_DUCADO, sal);
				int ti = Math.floorDiv(c[0], CASILLA) - bx;
				int tj = Math.floorDiv(c[1], CASILLA) - bz;
				if (ti < 0 || tj < 0 || ti >= N || tj >= N) {
					continue;
				}
				int k = ajustaATierra(ti, tj, terreno);
				if (k < 0) {
					continue;
				}
				// solo depende del ducado, así que todas las ventanas que lo ven están de acuerdo
				Geo.Reino r = Geo.reinoDeDucado(cx, cz, c[0], c[1], sal);
				boolean salvaje = r.salvaje() && !muestreador.hayPueblo(c[0], c[1], RADIO_PUEBLO);
				semillas.add(new Semilla(cx, cz, c[0], c[1], r, salvaje));
				origen.add(k);
			}
		}
	}

	/** Lo que cuesta andar de una casilla a la vecina: ríos, montañas, mar y cambios de bioma lo encarecen. */
	private static double costePaso(Casilla desde, Casilla hacia, boolean diagonal, boolean salvaje) {
		double paso = 1 + Math.abs(hacia.altura() - desde.altura()) / DESNIVEL_POR_PUNTO;
		if (hacia.montana()) {
			paso += COSTE_MONTANA;
		}
		if (hacia.rio() && !desde.rio()) {
			paso += COSTE_RIO;
		}
		if (hacia.familia() != desde.familia()) {
			paso += COSTE_BIOMA;
		}
		if (hacia.oceano()) {
			paso += COSTE_MAR;
		}
		if (diagonal) {
			paso *= DIAGONAL;
		}
		return salvaje ? paso * FRENO_SALVAJE : paso;
	}

	/** Casilla de tierra más cercana a (ti, tj) en un radio pequeño, o -1 si todo es mar. */
	private static int ajustaATierra(int ti, int tj, Casilla[] terreno) {
		if (!terreno[ti + tj * N].oceano()) {
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
					if (i < 0 || j < 0 || i >= N || j >= N || terreno[i + j * N].oceano()) {
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
