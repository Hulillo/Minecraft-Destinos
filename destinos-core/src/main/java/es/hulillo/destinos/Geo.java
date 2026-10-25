package es.hulillo.destinos;

/**
 * Matemática del mapa político, idéntica a las funciones geo/* del datapack y a herramientas/sim.py.
 * Aritmética int de Java = scoreboard de Minecraft (desbordamiento incluido), floorDiv y floorMod.
 * Si cambias una constante aquí, cámbiala también en datapack/constantes.py (PruebaGeo lo comprueba en la CI).
 */
public final class Geo {
	public static final int S_DUCADO = 1500;
	public static final int S_REINO = 6000;
	public static final int WARP = 1200;
	public static final int D_FRONTERA = 2800;
	public static final int P_FRONTERA = 45;
	public static final int P_SALVAJE = 4;
	public static final int K_JZ = 7919;
	public static final int K_REINO = 104729;
	public static final int K_SALV = 1299709;
	public static final int K_WARP = 15485863;
	/** Colores de reino (RGB), en el mismo orden que COLORES de constantes.py. */
	public static final int[] COLORES = {
		0xc0392b, 0x2e5fa8, 0x2e8b3a, 0x7b3fa0, 0xc9a227, 0xd8d8d8, 0xa0582c, 0x138d90, 0xb0306a, 0x5b6f7c,
	};
	public static final int SAL_COLOR = 5;

	private Geo() {
	}

	/** Celda ganadora de un Voronoi: índices de celda, centro en bloques y distancia² al centro. */
	public record Celda(int cx, int cz, int ctx, int ctz, int dist) {
	}

	/** Reino al que pertenece un ducado y si es tierra salvaje. */
	public record Reino(int kx, int kz, boolean salvaje) {
	}

	public static int hash(int hx, int hz, int hs) {
		int h = hx * 73856093;
		h += hz * 19349663;
		h += hs;
		h *= h;
		h += hs;
		h *= 1103515245;
		h += 12345;
		h = Math.floorDiv(h, 65536);
		if (h < 0) {
			h = -h;
		}
		return h;
	}

	/** Centro con jitter de la celda (cx, cz) de tamaño s: {x, z}. */
	public static int[] centro(int cx, int cz, int s, int sal) {
		int j0 = s / 8;
		int jr = s * 3 / 4;
		int jx = j0 + Math.floorMod(hash(cx, cz, sal), jr);
		int jz = j0 + Math.floorMod(hash(cx, cz, sal + K_JZ), jr);
		return new int[] {cx * s + jx, cz * s + jz};
	}

	public static Celda voronoi(int vx, int vz, int s, int sal) {
		int cx = Math.floorDiv(vx, s);
		int cz = Math.floorDiv(vz, s);
		int best = Integer.MAX_VALUE;
		Celda res = null;
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				int hx = cx + ox;
				int hz = cz + oz;
				int[] c = centro(hx, hz, s, sal);
				int dx = vx - c[0];
				int dz = vz - c[1];
				int d = dx * dx + dz * dz;
				if (d < best) {
					best = d;
					res = new Celda(hx, hz, c[0], c[1], d);
				}
			}
		}
		return res;
	}

	/** Reino y salvaje de un ducado (celda dcx, dcz con centro dctx, dctz). Igual que geo/territorio. */
	public static Reino reinoDeDucado(int dcx, int dcz, int dctx, int dctz, int sal) {
		int wx = dctx + Math.floorMod(hash(dcx, dcz, sal + K_WARP), 2 * WARP) - WARP;
		int wz = dctz + Math.floorMod(hash(dcx, dcz, sal + K_WARP + K_JZ), 2 * WARP) - WARP;
		Celda k = voronoi(wx, wz, S_REINO, sal + K_REINO);
		int tirada = Math.floorMod(hash(dcx, dcz, sal + K_SALV), 100);
		boolean salvaje = tirada < P_SALVAJE || (k.dist() > D_FRONTERA * D_FRONTERA && tirada < P_FRONTERA);
		return new Reino(k.cx(), k.cz(), salvaje);
	}

	/** Color (RGB) del reino de celda (kx, kz): el mismo que elige reino/generar_reino. */
	public static int colorReino(int kx, int kz, int sal) {
		return COLORES[Math.floorMod(hash(kx, kz, sal + SAL_COLOR), COLORES.length)];
	}

	/** Territorio geométrico (sin terreno), el mismo que calcula el datapack solo. */
	public static int[] territorioGeometrico(int x, int z, int sal) {
		Celda d = voronoi(x, z, S_DUCADO, sal);
		Reino r = reinoDeDucado(d.cx(), d.cz(), d.ctx(), d.ctz(), sal);
		return new int[] {d.cx(), d.cz(), r.kx(), r.kz(), r.salvaje() ? 1 : 0};
	}
}
