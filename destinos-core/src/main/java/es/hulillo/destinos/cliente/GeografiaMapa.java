package es.hulillo.destinos.cliente;

import es.hulillo.destinos.Geo;
import es.hulillo.destinos.MapaTerreno;
import es.hulillo.destinos.PaqueteTerritorio;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * Los accidentes del mapa con nombre propio: sierras (casillas de montaña juntas), ríos y mares. Se buscan en las
 * zonas que tiene el cliente y el nombre sale de la sal y del sitio más alto (o más hondo, o la desembocadura), así
 * que el mismo accidente se llama siempre igual.
 */
final class GeografiaMapa {
	private static final int MIN_SIERRA = 6;
	private static final int MIN_RIO = 6;
	private static final int MIN_MAR = 30;
	private static final int MAR_GRANDE = 400;
	private static final int ALTURA_SIERRA = 150;
	private static final int[][] CRUZ = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final String[] PRE = {"Al", "Bel", "Cas", "Dor", "Es", "Fal", "Gar", "Hu", "Ir", "Jar", "Lor", "Mar", "Nav", "Or", "Pe",
		"Quel", "Ros", "Sal", "Tor", "Ur", "Val", "Zar", "Bre", "Cor", "Gal", "Mon", "Ter", "Vel"};
	private static final String[] MED = {"a", "e", "i", "o", "u", "an", "er", "ol", "ar", "in", "en", "or"};
	private static final String[] SUF = {"da", "nia", "rra", "bar", "dor", "lla", "mar", "nes", "ral", "sa", "tea", "via", "gón", "ria",
		"zón", "dal"};

	enum Tipo {
		SIERRA, RIO, MAR, GOLFO
	}

	/** Un rótulo: dónde va (en bloques), qué dice y de qué es. */
	record Rotulo(double x, double z, Component texto, Tipo tipo, int casillas) {
	}

	private GeografiaMapa() {
	}

	/** Lo que se busca: sierras (montaña o casilla alta), ríos y mares. Un bit de cada en las banderas de cada casilla. */
	private static final int B_SIERRA = 1;
	private static final int B_RIO = 2;
	private static final int B_MAR = 4;
	private static final int[] BITS = {B_SIERRA, B_RIO, B_MAR};
	private static final int[] MINIMOS = {MIN_SIERRA, MIN_RIO, MIN_MAR};
	private static final Tipo[] TIPOS = {Tipo.SIERRA, Tipo.RIO, Tipo.MAR};

	/** Una zona con sus banderas por casilla y lo que ya se ha visitado de cada tipo. */
	private static final class Celdas {
		final PaqueteTerritorio zona;
		final byte[] banderas = new byte[N * N];
		final byte[] vistos = new byte[N * N];

		Celdas(PaqueteTerritorio z) {
			zona = z;
			for (int j = 0; j < N; j++) {
				for (int i = 0; i < N; i++) {
					PaqueteTerritorio.Ducado d = z.ducado(i, j);
					int b = 0;
					if (d.oceano()) {
						b = B_MAR;
					} else {
						if (z.montana(i, j) || z.altura(i, j) >= ALTURA_SIERRA) {
							b |= B_SIERRA;
						}
						if (z.rio(i, j)) {
							b |= B_RIO;
						}
					}
					banderas[i + j * N] = (byte) b;
				}
			}
		}
	}

	private static final int N = MapaTerreno.NUCLEO;

	/**
	 * Todos los rótulos de lo que hay en estas zonas. No toca nada del cliente (las zonas no cambian una vez
	 * recibidas), así que se puede llamar fuera del hilo del cliente para que no dé tirones.
	 */
	static List<Rotulo> calcula(List<PaqueteTerritorio> zonas, int sal) {
		Map<Long, Celdas> todas = new HashMap<>();
		for (PaqueteTerritorio z : zonas) {
			todas.put(Geo.clave(z.wx(), z.wz()), new Celdas(z));
		}
		List<Rotulo> res = new ArrayList<>();
		for (Celdas c : todas.values()) {
			int ox = c.zona.wx() * N;
			int oz = c.zona.wz() * N;
			for (int k = 0; k < N * N; k++) {
				for (int t = 0; t < 3; t++) {
					if ((c.banderas[k] & BITS[t]) != 0 && (c.vistos[k] & BITS[t]) == 0) {
						grupo(todas, ox + k % N, oz + k / N, t, sal, res);
					}
				}
			}
		}
		return res;
	}

	private static Celdas celdas(Map<Long, Celdas> todas, int tx, int tz) {
		return todas.get(Geo.clave(Math.floorDiv(tx, N), Math.floorDiv(tz, N)));
	}

	/** Recorre el grupo de casillas del tipo t que empieza en (tx, tz) y, si es bastante grande, le pone nombre. */
	private static void grupo(Map<Long, Celdas> todas, int tx, int tz, int t, int sal, List<Rotulo> res) {
		int bit = BITS[t];
		Tipo tipo = TIPOS[t];
		Enteros celdas = new Enteros();
		Celdas c0 = celdas(todas, tx, tz);
		c0.vistos[Math.floorMod(tx, N) + Math.floorMod(tz, N) * N] |= (byte) bit;
		celdas.add(tx);
		celdas.add(tz);
		// la lista de celdas hace de cola: se va leyendo por detrás mientras se añaden vecinas
		for (int q = 0; q < celdas.size(); q += 2) {
			int cx = celdas.get(q);
			int cz = celdas.get(q + 1);
			for (int[] d : CRUZ) {
				int nx = cx + d[0];
				int nz = cz + d[1];
				Celdas c = celdas(todas, nx, nz);
				if (c == null) {
					continue;
				}
				int k = Math.floorMod(nx, N) + Math.floorMod(nz, N) * N;
				if ((c.banderas[k] & bit) != 0 && (c.vistos[k] & bit) == 0) {
					c.vistos[k] |= (byte) bit;
					celdas.add(nx);
					celdas.add(nz);
				}
			}
		}
		int n = celdas.size() / 2;
		if (n < MINIMOS[t]) {
			return;
		}
		// el sitio que da el nombre: la cumbre de la sierra, lo más hondo del mar, la desembocadura del río
		int rx = celdas.get(0);
		int rz = celdas.get(1);
		int mejor = altura(todas, rx, rz);
		double sx = 0;
		double sz = 0;
		for (int q = 0; q < celdas.size(); q += 2) {
			int x = celdas.get(q);
			int z = celdas.get(q + 1);
			int h = altura(todas, x, z);
			boolean gana = tipo == Tipo.SIERRA ? h > mejor : h < mejor;
			if (gana || (h == mejor && (x < rx || (x == rx && z < rz)))) {
				mejor = h;
				rx = x;
				rz = z;
			}
			sx += x + 0.5;
			sz += z + 0.5;
		}
		Tipo tt = tipo == Tipo.MAR && n < MAR_GRANDE ? Tipo.GOLFO : tipo;
		double x;
		double z;
		if (tipo == Tipo.RIO) {
			// en un río el centro puede caer fuera del agua: se pone en una casilla del propio río
			int m = (n / 2) * 2;
			x = celdas.get(m) + 0.5;
			z = celdas.get(m + 1) + 0.5;
		} else {
			x = sx / n;
			z = sz / n;
		}
		res.add(new Rotulo(x * MapaTerreno.CASILLA, z * MapaTerreno.CASILLA, texto(tt, nombre(sal, rx, rz, tt)), tt, n));
	}

	private static int altura(Map<Long, Celdas> todas, int tx, int tz) {
		Celdas c = celdas(todas, tx, tz);
		return c.zona.altura(Math.floorMod(tx, N), Math.floorMod(tz, N));
	}

	private static String nombre(int sal, int tx, int tz, Tipo t) {
		int h = Geo.hash(tx, tz, sal + 977 + t.ordinal() * 31);
		int a = Math.floorMod(h, PRE.length);
		int b = Math.floorMod(h >>> 8, MED.length);
		int c = Math.floorMod(h >>> 16, SUF.length);
		return PRE[a] + MED[b] + SUF[c];
	}

	private static Component texto(Tipo t, String nombre) {
		return switch (t) {
			case SIERRA -> Component.translatableWithFallback("destinos.mapa.geo.sierra", "Sierra de %s", nombre);
			case RIO -> Component.translatableWithFallback("destinos.mapa.geo.rio", "Río %s", nombre);
			case MAR -> Component.translatableWithFallback("destinos.mapa.geo.mar", "Mar de %s", nombre);
			case GOLFO -> Component.translatableWithFallback("destinos.mapa.geo.golfo", "Golfo de %s", nombre);
		};
	}
}
