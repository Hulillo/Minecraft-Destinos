package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * El terreno de una capital antes de generarlo: una rejilla de PASO bloques con la altura de arriba (el agua cuenta
 * como superficie) y si hay agua de al menos dos de hondo, según el generador. Cada punto es una sola consulta (de
 * arriba hasta el suelo, que da suelo y agua a la vez): cada consulta monta el ruido del generador entero (es casi todo lo
 * que tarda en trazarse una capital), así que todo el trazado mira esta rejilla y no vuelve a preguntar. Entre
 * puntos, la altura se interpola.
 */
final class TerrenoCapital {
	static final int PASO = 8;

	private final int x0;
	private final int z0;
	private final int lado;
	private final int[] alto;
	private final boolean[] agua;
	private final Structure.GenerationContext ctx;
	/** Sin calcular todavía (los puntos de fuera del núcleo se calculan cuando alguien los mira). */
	private static final int SIN_CALCULAR = Integer.MIN_VALUE;

	/**
	 * La rejilla del cuadrado de medio lado radio alrededor de (cx, cz). Lo de dentro del núcleo (medio lado nucleo),
	 * que lo mira todo el trazado, se calcula ya y en paralelo (cada punto es independiente y es lo que más tarda de
	 * toda la capital); lo de fuera, solo si hace falta (los huertos de las afueras).
	 */
	TerrenoCapital(Structure.GenerationContext ctx, int cx, int cz, int radio, int nucleo) {
		this.ctx = ctx;
		int n = Math.ceilDiv(radio, PASO);
		x0 = cx - n * PASO;
		z0 = cz - n * PASO;
		lado = 2 * n + 1;
		alto = new int[lado * lado];
		agua = new boolean[lado * lado];
		java.util.Arrays.fill(alto, SIN_CALCULAR);
		int m = Math.min(n, Math.ceilDiv(nucleo, PASO));
		int ladoNucleo = 2 * m + 1;
		// en un pool propio y no con parallel(): el común (ForkJoin) lo comparten otros mods y, al esperar, un hilo
		// suyo puede ponerse a hacer tareas ajenas que esperan a su vez al servidor (riesgo de dejarlo todo colgado)
		int total = ladoNucleo * ladoNucleo;
		int trozos = Math.min(HILOS, total);
		List<Callable<Void>> tareas = new ArrayList<>(trozos);
		for (int t = 0; t < trozos; t++) {
			int desde = t;
			tareas.add(() -> {
				for (int k = desde; k < total; k += trozos) {
					calcula((n - m) + k / ladoNucleo, (n - m) + k % ladoNucleo);
				}
				return null;
			});
		}
		try {
			for (Future<Void> f : POOL.invokeAll(tareas)) {
				f.get();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("terreno de la capital interrumpido", e);
		} catch (ExecutionException e) {
			throw e.getCause() instanceof RuntimeException r ? r : new IllegalStateException(e.getCause());
		}
	}

	private static final int HILOS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
	private static final ExecutorService POOL = Executors.newFixedThreadPool(HILOS, r -> {
		Thread t = new Thread(r, "Destinos Core - terreno capital");
		t.setDaemon(true);
		return t;
	});

	/** Lee la columna del generador en el punto (i, j) de la rejilla. */
	private void calcula(int i, int j) {
		int[] c = columna(ctx, x0 + i * PASO, z0 + j * PASO);
		agua[i * lado + j] = c[1] >= 2;
		alto[i * lado + j] = c[0];
	}

	/**
	 * {altura de arriba (el agua cuenta), bloques de agua o lava encima del fondo} en (x, z) según el generador, sin
	 * generar el chunk. Con el generador de ruido de siempre la columna se recorre de arriba abajo y se para en el primer
	 * bloque sólido: lo de debajo no hace falta (era un tercio de lo que se tardaba) y basta un recorrido para el suelo
	 * y el agua (antes, dos). Con otro generador, la columna entera.
	 */
	static int[] columna(Structure.GenerationContext ctx, int x, int z) {
		int abajo = ctx.heightAccessor().getMinY();
		if (ctx.chunkGenerator() instanceof es.hulillo.destinos.mixin.AccesoNoiseBasedChunkGenerator ruido) {
			int[] fluidos = {0};
			java.util.OptionalInt encima = ruido.destinos$recorre(ctx.heightAccessor(), ctx.randomState(), x, z, null, s -> {
				if (s.isAir()) {
					return false;
				}
				if (esFluido(s)) {
					fluidos[0]++;
					return false;
				}
				return true;
			});
			// da la altura de encima del bloque en que se para
			int fondo = encima.isPresent() ? encima.getAsInt() - 1 : abajo;
			return new int[] {fondo + fluidos[0], fluidos[0]};
		}
		int arriba = abajo + ctx.heightAccessor().getHeight() - 1;
		NoiseColumn col = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
		int y = arriba;
		while (y > abajo && col.getBlock(y).isAir()) {
			y--;
		}
		int superficie = y;
		while (y > abajo && esFluido(col.getBlock(y))) {
			y--;
		}
		return new int[] {superficie, superficie - y};
	}

	/** El índice del punto (i, j), calculándolo si aún no lo está. */
	private int punto(int i, int j) {
		int k = indice(i, j);
		if (alto[k] == SIN_CALCULAR) {
			calcula(k / lado, k % lado);
		}
		return k;
	}

	private static boolean esFluido(BlockState s) {
		return !s.getFluidState().isEmpty();
	}

	/** Los puntos de la rejilla que se han leído del generador. */
	int puntos() {
		int r = 0;
		for (int a : alto) {
			if (a != SIN_CALCULAR) {
				r++;
			}
		}
		return r;
	}

	private int indice(int i, int j) {
		return Math.clamp(i, 0, lado - 1) * lado + Math.clamp(j, 0, lado - 1);
	}

	/**
	 * La altura de arriba en (x, z), interpolada entre los cuatro puntos de alrededor; en una capital en terrazas, la de
	 * su bancal.
	 */
	int alto(int x, int z) {
		int obra = pisado(x, z);
		if (obra != Integer.MIN_VALUE) {
			return obra;
		}
		double h = suave(x, z);
		double w = terrazas ? peso(x, z) : 0;
		return (int) Math.round(w <= 0 ? h : h * (1 - w) + nivel(h) * w);
	}

	// ------------------------------------------------------------------ terrazas

	/** Lo que sube cada bancal sobre el de debajo. */
	static final int ESCALON = 5;
	/** El desnivel (entre el décimo más bajo y el décimo más alto del sitio) desde el que la capital se hace en terrazas. */
	private static final int DESNIVEL_TERRAZAS = 16;
	/** Y ese desnivel, además, respecto al radio del sitio: solo las laderas empinadas de verdad (un sitio grande con una cuesta suave se queda como está). */
	private static final double PENDIENTE_TERRAZAS = 0.3;
	/** La franja del borde en que los bancales se van fundiendo con el terreno de fuera. */
	private static final int BORDE_TERRAZAS = 10;

	private boolean terrazas;
	private int tcx;
	private int tcz;
	private int tradio;

	/**
	 * Si el sitio (el círculo de radio alrededor de (cx, cz)) es una ladera, la capital se hace en terrazas: desde
	 * ahora la altura de cada punto de dentro es la de su bancal (la del terreno, redondeada a escalones de ESCALON:
	 * los bordes de los bancales siguen las curvas de nivel) y todo el trazado, que solo pregunta aquí, ve llano en
	 * cada bancal y un salto entre uno y otro. Devuelve si se ha hecho.
	 */
	boolean aterraza(int cx, int cz, int radio) {
		List<Integer> h = new ArrayList<>();
		for (int x = cx - radio; x <= cx + radio; x += PASO) {
			for (int z = cz - radio; z <= cz + radio; z += PASO) {
				if (Math.hypot(x - cx, z - cz) <= radio && !agua(x, z)) {
					h.add((int) Math.round(suave(x, z)));
				}
			}
		}
		if (h.size() < 20) {
			return false;
		}
		java.util.Collections.sort(h);
		desnivelSitio = h.get(h.size() * 9 / 10) - h.get(h.size() / 10);
		if (desnivelSitio < Math.max(DESNIVEL_TERRAZAS, radio * PENDIENTE_TERRAZAS)) {
			return false;
		}
		terrazas = true;
		tcx = cx;
		tcz = cz;
		tradio = radio + BORDE_TERRAZAS;
		return true;
	}

	/** Las obras ya decididas en una capital en terrazas: {minX, minZ, maxX, maxZ, suelo}. */
	private final List<int[]> pisadas = new ArrayList<>();

	/**
	 * En una capital en terrazas, el suelo de la caja queda desde ahora a la altura y (el de una obra ya decidida: la
	 * plaza, el castillo, una casa noble): lo que se trace después (las calles) llega a ella a su altura y no a la del
	 * bancal que había debajo. Sin terrazas no hace nada: la obra se asienta en el terreno, que apenas cambia.
	 */
	void pisa(BoundingBox b, int y) {
		if (terrazas) {
			pisadas.add(new int[] {b.minX(), b.minZ(), b.maxX(), b.maxZ(), y});
		}
	}

	private int pisado(int x, int z) {
		for (int[] p : pisadas) {
			if (x >= p[0] && x <= p[2] && z >= p[1] && z <= p[3]) {
				return p[4];
			}
		}
		return Integer.MIN_VALUE;
	}

	private int desnivelSitio;

	/** El desnivel del sitio que midió aterraza (entre el décimo más bajo y el décimo más alto). */
	int desnivelSitio() {
		return desnivelSitio;
	}

	boolean enTerrazas() {
		return terrazas;
	}

	/** El radio hasta el que llegan las terrazas (con su borde). */
	int radioTerrazas() {
		return tradio;
	}

	private static double nivel(double h) {
		return Math.round(h / ESCALON) * (double) ESCALON;
	}

	/** Cuánto manda el bancal en (x, z): 1 dentro, bajando a 0 por el borde; 0 fuera y sobre el agua. */
	private double peso(int x, int z) {
		double d = Math.hypot(x - tcx, z - tcz);
		if (d >= tradio || agua(x, z)) {
			return 0;
		}
		return Math.min(1, (tradio - d) / BORDE_TERRAZAS);
	}

	/**
	 * La altura a la que hay que dejar el suelo de (x, z) para hacer su bancal, o Integer.MIN_VALUE donde no se toca
	 * (fuera, en el borde donde se funde con el terreno, y sobre el agua).
	 */
	int bancal(int x, int z) {
		if (!terrazas || peso(x, z) < 1) {
			return Integer.MIN_VALUE;
		}
		return (int) nivel(suave(x, z));
	}

	/** La altura del terreno en (x, z), interpolada entre los cuatro puntos de alrededor, sin redondear. */
	private double suave(int x, int z) {
		double fx = (x - x0) / (double) PASO;
		double fz = (z - z0) / (double) PASO;
		int i = (int) Math.floor(fx);
		int j = (int) Math.floor(fz);
		double tx = fx - i;
		double tz = fz - j;
		double a = alto[punto(i, j)] * (1 - tx) + alto[punto(i + 1, j)] * tx;
		double b = alto[punto(i, j + 1)] * (1 - tx) + alto[punto(i + 1, j + 1)] * tx;
		return a * (1 - tz) + b * tz;
	}

	/** Columnas leídas una a una del generador (alto, agua), para lo que no puede fiarse de la rejilla interpolada. */
	private final java.util.Map<Long, int[]> exactas = new java.util.HashMap<>();

	private int[] exacta(int x, int z) {
		return exactas.computeIfAbsent(Geo.clave(x, z), k -> columna(ctx, x, z));
	}

	/**
	 * La altura de arriba en (x, z) leída del generador en esa misma columna, sin interpolar. La rejilla (un punto cada
	 * PASO bloques) no ve un cortado, una zanja o un río estrecho entre dos puntos: lo que tiene que casar con el suelo
	 * bloque a bloque (la muralla) pregunta aquí. Cada consulta nueva cuesta lo que un punto de la rejilla.
	 */
	int exacto(int x, int z) {
		int obra = pisado(x, z);
		if (obra != Integer.MIN_VALUE) {
			return obra;
		}
		double w = terrazas ? peso(x, z) : 0;
		if (w >= 1) {
			return (int) nivel(suave(x, z));
		}
		int h = exacta(x, z)[0];
		return w <= 0 ? h : (int) Math.round(h * (1 - w) + nivel(suave(x, z)) * w);
	}

	/** Si en la columna (x, z) hay agua de al menos dos de hondo, leída del generador en esa misma columna. */
	boolean aguaExacta(int x, int z) {
		return exacta(x, z)[1] >= 2;
	}

	/** Si en (x, z) hay agua (el punto de la rejilla más cercano). */
	boolean agua(int x, int z) {
		return agua[punto(Math.round((x - x0) / (float) PASO), Math.round((z - z0) / (float) PASO))];
	}

	/** Si el centro o alguna esquina de la caja cae sobre el agua. */
	boolean aguaEsquinas(BoundingBox b) {
		return agua(b.getCenter().getX(), b.getCenter().getZ()) || agua(b.minX(), b.minZ()) || agua(b.maxX(), b.minZ())
				|| agua(b.minX(), b.maxZ()) || agua(b.maxX(), b.maxZ());
	}

	/** Si la caja está entera sobre el agua (su centro y sus cuatro esquinas). */
	boolean todoAgua(BoundingBox b) {
		return agua(b.getCenter().getX(), b.getCenter().getZ()) && agua(b.minX(), b.minZ()) && agua(b.maxX(), b.minZ())
				&& agua(b.minX(), b.maxZ()) && agua(b.maxX(), b.maxZ());
	}

	/** Si algo de la caja (centro, esquinas o puntos de la rejilla de dentro) cae sobre el agua. */
	boolean agua(BoundingBox b) {
		if (agua(b.getCenter().getX(), b.getCenter().getZ()) || agua(b.minX(), b.minZ()) || agua(b.maxX(), b.minZ())
				|| agua(b.minX(), b.maxZ()) || agua(b.maxX(), b.maxZ())) {
			return true;
		}
		for (int x = b.minX(); x <= b.maxX(); x += PASO) {
			for (int z = b.minZ(); z <= b.maxZ(); z += PASO) {
				if (agua(x, z)) {
					return true;
				}
			}
		}
		return false;
	}

	/** {altura mediana, desnivel} del cuadrado de medio lado medio en (x, z), con 3 x 3 puntos. */
	int[] relieve(int x, int z, int medio) {
		int[] h = new int[9];
		int k = 0;
		for (int i = -1; i <= 1; i++) {
			for (int j = -1; j <= 1; j++) {
				h[k++] = alto(x + i * medio, z + j * medio);
			}
		}
		java.util.Arrays.sort(h);
		return new int[] {h[4], h[8] - h[0]};
	}

	/** El desnivel (más alto menos más bajo) dentro de la caja, mirando sus esquinas, su centro y la rejilla. */
	int desnivel(BoundingBox b) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int x = b.minX(); x <= b.maxX() + PASO - 1; x += PASO) {
			for (int z = b.minZ(); z <= b.maxZ() + PASO - 1; z += PASO) {
				int h = alto(Math.min(x, b.maxX()), Math.min(z, b.maxZ()));
				min = Math.min(min, h);
				max = Math.max(max, h);
			}
		}
		int h = alto(b.getCenter().getX(), b.getCenter().getZ());
		return Math.max(max, h) - Math.min(min, h);
	}
}
