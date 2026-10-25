import es.hulillo.destinos.Geo;
import es.hulillo.destinos.MapaTerreno;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/**
 * Vista previa sin Minecraft: terreno sintético (ruido) + fronteras calculadas por MapaTerreno.
 * Herramienta de desarrollo, no es un test de Gradle.
 * Uso: java VistaPrevia <sal> <salida.png> [geo|terreno] [radio] [altura_base]
 */
public final class VistaPrevia {
	private VistaPrevia() {
	}

	static double ruido(long semilla, double x, double z) {
		int x0 = (int) Math.floor(x);
		int z0 = (int) Math.floor(z);
		double fx = x - x0;
		double fz = z - z0;
		double a = valor(semilla, x0, z0);
		double b = valor(semilla, x0 + 1, z0);
		double c = valor(semilla, x0, z0 + 1);
		double d = valor(semilla, x0 + 1, z0 + 1);
		double sx = fx * fx * (3 - 2 * fx);
		double sz = fz * fz * (3 - 2 * fz);
		return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sz;
	}

	static double valor(long s, int x, int z) {
		long h = s ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
		h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return ((h >>> 11) & 0xFFFFF) / (double) 0xFFFFF * 2 - 1;
	}

	static double fractal(long s, double x, double z) {
		double t = 0;
		double amp = 1;
		double f = 1;
		for (int o = 0; o < 5; o++) {
			t += ruido(s + o, x * f, z * f) * amp;
			amp *= 0.5;
			f *= 2;
		}
		return t;
	}

	public static void main(String[] args) throws Exception {
		int sal = Integer.parseInt(args[0]);
		boolean geometrico = args.length > 2 && args[2].equals("geo");
		int radio = args.length > 3 ? Integer.parseInt(args[3]) : 24000;
		double base = args.length > 4 ? Double.parseDouble(args[4]) : 64;
		MapaTerreno.Muestreador terreno = (x, z) -> {
			double h = base + fractal(1234, x / 9000.0, z / 9000.0) * 70 + Math.max(0, fractal(77, x / 3000.0, z / 3000.0)) * 60;
			double r = Math.abs(fractal(999, x / 6000.0, z / 6000.0));
			boolean oceano = h < 60;
			boolean rio = !oceano && r < 0.035 && h < 115;
			return new MapaTerreno.Casilla((int) h, oceano, rio, h > 120);
		};
		MapaTerreno mapa = new MapaTerreno(terreno, sal, Executors.newFixedThreadPool(4));
		int px = 600;
		int paso = 2 * radio / px;
		int[][] ducado = new int[px][px];
		int[][] reino = new int[px][px];
		BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
		for (int j = 0; j < px; j++) {
			for (int i = 0; i < px; i++) {
				int x = -radio + i * paso;
				int z = -radio + j * paso;
				MapaTerreno.Casilla c = terreno.muestra(x, z);
				int d;
				int k;
				boolean salvaje;
				if (geometrico) {
					int[] g = Geo.territorioGeometrico(x, z, sal);
					d = g[0] * 7919 + g[1];
					k = g[2] * 7919 + g[3];
					salvaje = g[4] == 1;
				} else {
					MapaTerreno.Resultado r = mapa.consulta(x, z);
					d = r.dcx() * 7919 + r.dcz();
					k = r.kx() * 7919 + r.kz();
					salvaje = r.salvaje();
				}
				ducado[j][i] = d;
				reino[j][i] = k;
				int rgb;
				if (c.oceano()) {
					rgb = 0x2a5d8f;
				} else if (c.rio()) {
					rgb = 0x4f9fd6;
				} else if (salvaje) {
					rgb = 0x6b6b6b;
				} else {
					int hh = Geo.hash(k, k >> 7, 31337);
					rgb = ((hh * 97) % 160 + 70) << 16 | ((hh * 57) % 160 + 70) << 8 | ((hh * 131) % 160 + 70);
				}
				if (c.montana() && !c.oceano()) {
					rgb = mezcla(rgb, 0xffffff, 0.35);
				}
				img.setRGB(i, j, rgb);
			}
		}
		for (int j = 1; j < px; j++) {
			for (int i = 1; i < px; i++) {
				boolean bk = reino[j][i] != reino[j][i - 1] || reino[j][i] != reino[j - 1][i];
				boolean bd = ducado[j][i] != ducado[j][i - 1] || ducado[j][i] != ducado[j - 1][i];
				if (bk) {
					img.setRGB(i, j, 0x101010);
				} else if (bd) {
					img.setRGB(i, j, mezcla(img.getRGB(i, j) & 0xffffff, 0, 0.4));
				}
			}
		}
		ImageIO.write(img, "png", new File(args[1]));
		System.out.println("ventanas calculadas: " + mapa.ventanasCalculadas());
		System.exit(0);
	}

	static int mezcla(int a, int b, double t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return r << 16 | g << 8 | bl;
	}
}
