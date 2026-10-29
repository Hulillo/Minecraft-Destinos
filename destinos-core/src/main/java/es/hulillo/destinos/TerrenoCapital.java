package es.hulillo.destinos;

import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * El terreno de una capital antes de generarlo: una rejilla de PASO bloques con la altura de arriba (el agua cuenta
 * como superficie) y si hay agua de al menos dos de hondo, según el generador. Cada punto es una sola consulta (la
 * columna entera, que da suelo y agua a la vez): cada consulta monta el ruido del generador entero, así que todo el
 * trazado de la capital mira esta rejilla y no vuelve a preguntar. Entre puntos, la altura se interpola.
 */
final class TerrenoCapital {
	static final int PASO = 8;

	private final int x0;
	private final int z0;
	private final int lado;
	private final int[] alto;
	private final boolean[] agua;

	/** La rejilla del cuadrado de medio lado radio alrededor de (cx, cz). */
	TerrenoCapital(Structure.GenerationContext ctx, int cx, int cz, int radio) {
		int n = Math.ceilDiv(radio, PASO);
		x0 = cx - n * PASO;
		z0 = cz - n * PASO;
		lado = 2 * n + 1;
		alto = new int[lado * lado];
		agua = new boolean[lado * lado];
		int arriba = ctx.heightAccessor().getMinY() + ctx.heightAccessor().getHeight() - 1;
		int abajo = ctx.heightAccessor().getMinY();
		for (int i = 0; i < lado; i++) {
			for (int j = 0; j < lado; j++) {
				NoiseColumn col = ctx.chunkGenerator().getBaseColumn(x0 + i * PASO, z0 + j * PASO, ctx.heightAccessor(), ctx.randomState());
				int y = arriba;
				while (y > abajo && col.getBlock(y).isAir()) {
					y--;
				}
				int superficie = y;
				while (y > abajo && esFluido(col.getBlock(y))) {
					y--;
				}
				alto[i * lado + j] = superficie;
				agua[i * lado + j] = superficie - y >= 2;
			}
		}
	}

	private static boolean esFluido(BlockState s) {
		return !s.getFluidState().isEmpty();
	}

	int puntos() {
		return lado * lado;
	}

	private int indice(int i, int j) {
		return Math.clamp(i, 0, lado - 1) * lado + Math.clamp(j, 0, lado - 1);
	}

	/** La altura de arriba en (x, z), interpolada entre los cuatro puntos de alrededor. */
	int alto(int x, int z) {
		double fx = (x - x0) / (double) PASO;
		double fz = (z - z0) / (double) PASO;
		int i = (int) Math.floor(fx);
		int j = (int) Math.floor(fz);
		double tx = fx - i;
		double tz = fz - j;
		double a = alto[indice(i, j)] * (1 - tx) + alto[indice(i + 1, j)] * tx;
		double b = alto[indice(i, j + 1)] * (1 - tx) + alto[indice(i + 1, j + 1)] * tx;
		return (int) Math.round(a * (1 - tz) + b * tz);
	}

	/** Si en (x, z) hay agua (el punto de la rejilla más cercano). */
	boolean agua(int x, int z) {
		return agua[indice(Math.round((x - x0) / (float) PASO), Math.round((z - z0) / (float) PASO))];
	}

	/** Si el centro o alguna esquina de la caja cae sobre el agua. */
	boolean aguaEsquinas(BoundingBox b) {
		return agua(b.getCenter().getX(), b.getCenter().getZ()) || agua(b.minX(), b.minZ()) || agua(b.maxX(), b.minZ())
				|| agua(b.minX(), b.maxZ()) || agua(b.maxX(), b.maxZ());
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
