package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

/**
 * El plano de la muralla de una capital, decidido entero antes de poner un solo bloque (sin Minecraft: se prueba en
 * la CI con terrenos de mentira, herramientas/PruebaMuralla.java). Al ponerla ya no se mira qué hay en el suelo para
 * decidir nada; lo que salga mal es un fallo de este plano y se ve aquí.
 * <ul>
 * <li>La línea: un vértice cada PASO_TRAMO bloques por la cara de dentro del contorno.</li>
 * <li>El pie de cada vértice: el suelo más alto en su sitio y alrededor (en el borde de un cortado, el de arriba),
 * suavizado pero nunca a más de un bloque de él. El terreno se amolda luego a lo largo de la muralla a ese pie (como
 * a las casas), así que la muralla ni queda enterrada ni cuelga.</li>
 * <li>Cada tramo es MURO o CUESTA: por una ladera más empinada que PENDIENTE no se sube muralla (quedaba como una
 * cinta colgada del monte; la ladera ya defiende), y un muro suelto entre dos cuestas tampoco.</li>
 * <li>Cada calle que cruza un tramo de muro lleva su puerta: las principales, un portón (los huecos que se dan); las
 * demás, un portillo con dintel en la propia muralla. Nunca un hueco porque sí.</li>
 * <li>Torres en los quiebros, cada tanto y al acabar cada lienzo de muro, nunca encima de una puerta.</li>
 * <li>La franja (de un bloque por dentro a uno por fuera del grueso) es solo de la muralla: pisa() dice qué cajas la
 * tocan para quitarlas del plano de la capital.</li>
 * </ul>
 */
final class PlanMuralla {
	/** Separación de los vértices de la línea. */
	static final int PASO_TRAMO = 6;
	static final int[] ALTO = {4, 5, 7};
	static final int[] GRUESO = {1, 2, 3};
	/** Torres cada tanto (las pobres, sin torres) y en los quiebros de más de QUIEBRO. */
	private static final int[] ESPACIO_TORRES = {0, 44, 30};
	private static final double QUIEBRO = Math.toRadians(22);
	static final int RADIO_TORRE = 3;
	/** Hasta dónde se mira alrededor de cada vértice el suelo más alto (el de arriba de un cortado o una ladera). */
	private static final int ENTERRADA = 2;
	/** Lo más empinado (bloques de subida por bloque andado) que sube un lienzo de muralla; más, es CUESTA. */
	static final double PENDIENTE = 0.85;
	/** Alto libre bajo el dintel de un portillo (su ancho, el de la calle: de 3 a 5). */
	static final int PASO_PORTILLO = 3;

	enum Tipo {
		MURO, CUESTA
	}

	/**
	 * Un portillo: dónde cruza la calle la cara de dentro de la muralla, a qué altura va su suelo, en qué tramo y su
	 * medio ancho (1 o 2: un paso de 3 o 5).
	 */
	record Portillo(int x, int z, int y, int tramo, int medio) {
	}

	/** Una torre: su centro y la altura de su suelo. */
	record Torre(int x, int z, int y) {
	}

	final int cx;
	final int cz;
	final int nivel;
	final int[][] v;
	/** El pie (suelo del adarve menos su alto) en cada vértice. */
	final int[] pie;
	/** El tipo del tramo k (de v[k] a v[k + 1]). */
	final Tipo[] tipo;
	/** Si el tramo k toca agua (ahí no se amolda el terreno: no se echa tierra en el río; se pone reja). */
	final boolean[] agua;
	/** Si el tramo k cae dentro de la muralla del castillo (no se pone: empalma con ella). */
	final boolean[] tapado;
	final List<Torre> torres = new ArrayList<>();
	final List<Portillo> portillos = new ArrayList<>();
	/** Los huecos de los portones (x0, z0, x1, z1 seguidos) y el castillo (centro y medio lado, o null). */
	final int[] huecos;
	final int[] castillo;

	private PlanMuralla(int cx, int cz, int nivel, int[][] v, int[] huecos, int[] castillo) {
		this.cx = cx;
		this.cz = cz;
		this.nivel = nivel;
		this.v = v;
		int n = v.length;
		this.pie = new int[n];
		this.tipo = new Tipo[n];
		this.agua = new boolean[n];
		this.tapado = new boolean[n];
		this.huecos = huecos;
		this.castillo = castillo;
	}

	static int grueso(int nivel) {
		return GRUESO[Math.clamp(nivel, 0, GRUESO.length - 1)];
	}

	static int alto(int nivel) {
		return ALTO[Math.clamp(nivel, 0, ALTO.length - 1)];
	}

	/**
	 * El plano alrededor de (cx, cz) con los radios del contorno; alto da la altura del suelo y aguaEn si hay agua.
	 * huecos: las cajas delante y detrás de los portones; castillo: centro y medio lado de su muralla (o null); calles:
	 * los trozos de calle (x0, z0, y0, x1, z1, y1 y el radio en décimas).
	 */
	static PlanMuralla traza(int cx, int cz, int nivel, int[] radios, IntBinaryOperator alto, BiPredicate<Integer, Integer> aguaEn,
			int[] castillo, int[] huecos, List<int[]> calles) {
		nivel = Math.clamp(nivel, 0, ALTO.length - 1);
		PlanMuralla p = new PlanMuralla(cx, cz, nivel, vertices(cx, cz, radios), huecos, castillo);
		p.pies(alto);
		p.tipos(aguaEn);
		p.portillos(calles);
		p.torres();
		return p;
	}

	/** Los vértices de la línea de la muralla (su cara de dentro), cada PASO_TRAMO bloques más o menos. */
	static int[][] vertices(int cx, int cz, int[] radios) {
		double medio = java.util.Arrays.stream(radios).average().orElse(80);
		int n = Math.max(24, (int) Math.round(2 * Math.PI * medio / PASO_TRAMO));
		int[][] v = new int[n][2];
		for (int k = 0; k < n; k++) {
			double a = k * 3600.0 / n;
			double r = radioEn(radios, a);
			double ang = Math.toRadians(a / 10);
			v[k][0] = cx + (int) Math.round(Math.cos(ang) * r);
			v[k][1] = cz + (int) Math.round(Math.sin(ang) * r);
		}
		return v;
	}

	/** El radio en el ángulo a (décimas de grado), entre los de los arcos. */
	static double radioEn(int[] radios, double a) {
		int arcos = radios.length;
		double f = Math.floorMod((long) Math.floor(a * 1000), 3600_000L) / 1000.0 / 3600 * arcos;
		int i = (int) Math.floor(f);
		double t = f - i;
		return radios[i % arcos] * (1 - t) + radios[(i + 1) % arcos] * t;
	}

	int n() {
		return v.length;
	}

	int siguiente(int k) {
		return (k + 1) % v.length;
	}

	double largo(int k) {
		int[] a = v[k];
		int[] b = v[siguiente(k)];
		return Math.hypot(b[0] - a[0], b[1] - a[1]);
	}

	/** El suelo que cuenta en (x, z): el más alto entre el sitio y ENTERRADA bloques hacia dentro y hacia fuera. */
	int sueloEn(IntBinaryOperator alto, int x, int z) {
		double[] f = fuera(x, z);
		int dx = (int) Math.round(f[0] * ENTERRADA);
		int dz = (int) Math.round(f[1] * ENTERRADA);
		return Math.max(alto.applyAsInt(x, z), Math.max(alto.applyAsInt(x + dx, z + dz), alto.applyAsInt(x - dx, z - dz)));
	}

	/** El suelo más alto en el vértice y a ENTERRADA bloques; el pie, suavizado a lo largo pero a un bloque de él. */
	private void pies(IntBinaryOperator alto) {
		int n = n();
		int[] suelo = new int[n];
		for (int k = 0; k < n; k++) {
			suelo[k] = sueloEn(alto, v[k][0], v[k][1]);
		}
		for (int k = 0; k < n; k++) {
			double liso = (suelo[(k + n - 1) % n] + 2.0 * suelo[k] + suelo[(k + 1) % n]) / 4;
			pie[k] = Math.clamp(Math.round(liso), suelo[k] - 1, suelo[k] + 1);
		}
	}

	/** Cuántos tramos empinados seguidos hacen una ladera sin muro; menos, la muralla los salva escalonada (sin hueco). */
	static final int LADERA = 3;

	/**
	 * MURO o CUESTA: cuesta, solo las laderas de verdad (LADERA tramos empinados seguidos o más); un escalón suelto del
	 * terreno no abre un hueco en la muralla. Sin lienzos sueltos de un solo tramo entre dos laderas. Agua y castillo.
	 */
	private void tipos(BiPredicate<Integer, Integer> aguaEn) {
		int n = n();
		for (int k = 0; k < n; k++) {
			int s = siguiente(k);
			tipo[k] = Math.abs(pie[s] - pie[k]) > Math.max(1, largo(k) * PENDIENTE) ? Tipo.CUESTA : Tipo.MURO;
			int mx = (v[k][0] + v[s][0]) / 2;
			int mz = (v[k][1] + v[s][1]) / 2;
			agua[k] = aguaEn.test(v[k][0], v[k][1]) || aguaEn.test(v[s][0], v[s][1]) || aguaEn.test(mx, mz);
			tapado[k] = enCastillo(v[k][0], v[k][1], -2) && enCastillo(v[s][0], v[s][1], -2);
		}
		// las cuestas cortas (menos de LADERA tramos seguidos) vuelven a ser muro
		Tipo[] crudo = tipo.clone();
		for (int k = 0; k < n; k++) {
			if (crudo[k] != Tipo.CUESTA) {
				continue;
			}
			int seguidos = 1;
			for (int j = 1; j < n && crudo[(k + j) % n] == Tipo.CUESTA; j++) {
				seguidos++;
			}
			for (int j = 1; j < n && crudo[(k + n - j) % n] == Tipo.CUESTA; j++) {
				seguidos++;
			}
			if (seguidos < LADERA) {
				tipo[k] = Tipo.MURO;
			}
		}
		Tipo[] antes = tipo.clone();
		for (int k = 0; k < n; k++) {
			if (antes[k] == Tipo.MURO && antes[(k + n - 1) % n] == Tipo.CUESTA && antes[siguiente(k)] == Tipo.CUESTA) {
				tipo[k] = Tipo.CUESTA;
			}
		}
	}

	/** Si en el tramo k se pone muralla (muro, fuera del castillo). */
	boolean hayMuro(int k) {
		return tipo[k] == Tipo.MURO && !tapado[k];
	}

	/**
	 * Un portillo donde cada calle cruza un tramo de muro, salvo en los portones (sus huecos) y el castillo; un cruce que
	 * ya cae en el paso de otro no lleva otro. Su suelo, a la altura de la calle en el cruce.
	 */
	private void portillos(List<int[]> calles) {
		int grueso = grueso(nivel);
		for (int[] c : calles) {
			for (int k = 0; k < n(); k++) {
				if (!hayMuro(k) || agua[k]) {
					continue;
				}
				int[] a = v[k];
				int[] b = v[siguiente(k)];
				double[] t = cruce(a[0], a[1], b[0], b[1], c[0], c[1], c[3], c[4]);
				if (t == null) {
					continue;
				}
				int x = (int) Math.round(a[0] + (b[0] - a[0]) * t[0]);
				int z = (int) Math.round(a[1] + (b[1] - a[1]) * t[0]);
				int y = (int) Math.round(c[2] + (c[5] - c[2]) * t[1]);
				if (enHueco(x, z, grueso + 2) || enCastillo(x, z, 2)) {
					continue;
				}
				// el cruce ya cae en el paso de otro (dos trozos seguidos de la misma calle): ese le vale
				if (portillos.stream().anyMatch(q -> Math.hypot(q.x() - x, q.z() - z) <= q.medio() + 0.5)) {
					continue;
				}
				int medio = Math.clamp((int) Math.floor(c[6] / 10.0), 1, 2);
				portillos.add(new Portillo(x, z, y, k, medio));
			}
		}
	}

	/**
	 * Dónde se cortan los segmentos (a, b) y (c, d): {t en ab, u en cd} entre 0 y 1, o null si no se cortan. Una calle
	 * que solo roza la línea en su punta también cuenta (así no se cuela por la junta de dos trozos).
	 */
	static double[] cruce(double ax, double az, double bx, double bz, double cx, double cz, double dx, double dz) {
		double rx = bx - ax;
		double rz = bz - az;
		double sx = dx - cx;
		double sz = dz - cz;
		double den = rx * sz - rz * sx;
		if (Math.abs(den) < 1e-9) {
			return null;
		}
		double qx = cx - ax;
		double qz = cz - az;
		double t = (qx * sz - qz * sx) / den;
		double u = (qx * rz - qz * rx) / den;
		double e = 1e-6;
		return t < -e || t > 1 + e || u < -e || u > 1 + e ? null : new double[] {Math.clamp(t, 0, 1), Math.clamp(u, 0, 1)};
	}

	/** Torres en los quiebros, cada ESPACIO_TORRES y al acabar cada lienzo de muro; nunca sobre una puerta. */
	private void torres() {
		int espacio = ESPACIO_TORRES[nivel];
		if (espacio <= 0) {
			return;
		}
		int n = n();
		int medio = grueso(nivel) / 2;
		double andado = espacio / 2.0;
		for (int k = 0; k < n; k++) {
			int ka = (k + n - 1) % n;
			int[] a = v[ka];
			int[] b = v[k];
			int[] c = v[siguiente(k)];
			andado += Math.hypot(b[0] - a[0], b[1] - a[1]);
			boolean muroAntes = hayMuro(ka);
			boolean muroDespues = hayMuro(k);
			if (!muroAntes && !muroDespues) {
				continue;
			}
			double giro = Math.abs(Math.atan2(c[1] - b[1], c[0] - b[0]) - Math.atan2(b[1] - a[1], b[0] - a[0]));
			giro = Math.min(giro, 2 * Math.PI - giro);
			boolean remate = muroAntes != muroDespues && andado >= espacio / 3.0;
			if (!remate && andado < espacio && !(giro > QUIEBRO && andado >= espacio / 2.0)) {
				continue;
			}
			double[] f = fuera(b[0], b[1]);
			int tx = b[0] + (int) Math.round(f[0] * medio);
			int tz = b[1] + (int) Math.round(f[1] * medio);
			if (enCastillo(tx, tz, RADIO_TORRE + 2) || enHueco(tx, tz, RADIO_TORRE + 4) || agua[ka] && agua[k]
					|| portillos.stream().anyMatch(q -> Math.hypot(q.x() - tx, q.z() - tz) < RADIO_TORRE + 4)) {
				continue;
			}
			andado = 0;
			torres.add(new Torre(tx, tz, pie[k]));
		}
	}

	/** La dirección hacia fuera del anillo en (x, z), de largo 1. */
	double[] fuera(double x, double z) {
		double d = Math.max(1e-6, Math.hypot(x - cx, z - cz));
		return new double[] {(x - cx) / d, (z - cz) / d};
	}

	boolean enHueco(int x, int z, int margen) {
		for (int k = 0; k + 3 < huecos.length; k += 4) {
			if (x >= huecos[k] - margen && x <= huecos[k + 2] + margen && z >= huecos[k + 1] - margen && z <= huecos[k + 3] + margen) {
				return true;
			}
		}
		return false;
	}

	boolean enCastillo(int x, int z, int margen) {
		return castillo != null && Math.max(Math.abs(x - castillo[0]), Math.abs(z - castillo[1])) <= castillo[2] + margen;
	}

	/**
	 * Si la caja (en planta: x0, z0, x1, z1) toca la franja de algún tramo de muro: de un bloque por dentro de la línea
	 * a uno por fuera del grueso, o alguna de sus torres. Lo que la toca no cabe en la capital (la muralla no esquiva nada).
	 */
	boolean pisa(int x0, int z0, int x1, int z1) {
		int grueso = grueso(nivel);
		for (Torre t : torres) {
			int r = RADIO_TORRE + 1;
			if (t.x() + r >= x0 && t.x() - r <= x1 && t.z() + r >= z0 && t.z() - r <= z1) {
				return true;
			}
		}
		for (int k = 0; k < n(); k++) {
			if (!hayMuro(k)) {
				continue;
			}
			int[] a = v[k];
			int[] b = v[siguiente(k)];
			double largo = Math.max(1e-6, largo(k));
			double[] f = fuera((a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0);
			for (double t = 0; t <= largo; t += 1) {
				double px = a[0] + (b[0] - a[0]) * t / largo;
				double pz = a[1] + (b[1] - a[1]) * t / largo;
				for (int h = -1; h <= grueso + 1; h++) {
					int x = (int) Math.round(px + f[0] * h);
					int z = (int) Math.round(pz + f[1] * h);
					if (x >= x0 && x <= x1 && z >= z0 && z <= z1) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** El portillo cuyo paso incluye el punto (x, z) del tramo k (proyectado en su línea), o null. */
	Portillo portilloEn(int k, double qx, double qz) {
		for (Portillo q : portillos) {
			if (Math.abs(q.tramo() - k) > 1 && Math.abs(q.tramo() - k) < n() - 1) {
				continue;
			}
			if (Math.hypot(q.x() - qx, q.z() - qz) <= q.medio() + 0.5) {
				return q;
			}
		}
		return null;
	}

	/** Resumen para /destinoscore muralla y para la prueba. */
	String resumen() {
		int muro = 0;
		int cuesta = 0;
		int aguas = 0;
		for (int k = 0; k < n(); k++) {
			if (tapado[k]) {
				continue;
			}
			if (tipo[k] == Tipo.MURO) {
				muro++;
			} else {
				cuesta++;
			}
			if (agua[k]) {
				aguas++;
			}
		}
		return n() + " tramos: " + muro + " de muro, " + cuesta + " de cuesta, " + aguas + " con agua · " + torres.size() + " torres · "
				+ portillos.size() + " portillos · " + huecos.length / 4 + " portones";
	}
}
