package es.hulillo.destinos.cliente;

import es.hulillo.destinos.Geo;
import es.hulillo.destinos.MapaTerreno;
import es.hulillo.destinos.PaqueteTerritorio;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;

/**
 * Los accidentes del mapa con nombre propio: sierras (casillas de montaña juntas), ríos y mares. Se buscan en las
 * zonas que tiene el cliente y el nombre sale de la sal y del sitio más alto (o más hondo, o la desembocadura), así
 * que el mismo accidente se llama siempre igual. Solo desde el hilo del cliente.
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

	private static PaqueteTerritorio.Ducado ducado(int tx, int tz) {
		return TerritoriosCliente.ducado(tx, tz);
	}

	private static boolean sierra(int tx, int tz) {
		PaqueteTerritorio z = TerritoriosCliente.zona(tx, tz);
		if (z == null) {
			return false;
		}
		int i = tx - z.wx() * MapaTerreno.NUCLEO;
		int j = tz - z.wz() * MapaTerreno.NUCLEO;
		return !z.ducado(i, j).oceano() && (z.montana(i, j) || z.altura(i, j) >= ALTURA_SIERRA);
	}

	private static boolean rio(int tx, int tz) {
		PaqueteTerritorio z = TerritoriosCliente.zona(tx, tz);
		if (z == null) {
			return false;
		}
		int i = tx - z.wx() * MapaTerreno.NUCLEO;
		int j = tz - z.wz() * MapaTerreno.NUCLEO;
		return !z.ducado(i, j).oceano() && z.rio(i, j);
	}

	private static boolean mar(int tx, int tz) {
		PaqueteTerritorio.Ducado d = ducado(tx, tz);
		return d != null && d.oceano();
	}

	interface Filtro {
		boolean es(int tx, int tz);
	}

	/** Todos los rótulos de lo que hay en las zonas recibidas. */
	static List<Rotulo> calcula() {
		List<Rotulo> res = new ArrayList<>();
		Set<Long> vistos = new HashSet<>();
		Set<Long> vistosRio = new HashSet<>();
		Set<Long> vistosMar = new HashSet<>();
		int sal = TerritoriosCliente.sal();
		for (PaqueteTerritorio z : TerritoriosCliente.zonasRecibidas()) {
			for (int j = 0; j < MapaTerreno.NUCLEO; j++) {
				for (int i = 0; i < MapaTerreno.NUCLEO; i++) {
					int tx = z.wx() * MapaTerreno.NUCLEO + i;
					int tz = z.wz() * MapaTerreno.NUCLEO + j;
					grupo(tx, tz, GeografiaMapa::sierra, vistos, MIN_SIERRA, Tipo.SIERRA, sal, res);
					grupo(tx, tz, GeografiaMapa::rio, vistosRio, MIN_RIO, Tipo.RIO, sal, res);
					grupo(tx, tz, GeografiaMapa::mar, vistosMar, MIN_MAR, Tipo.MAR, sal, res);
				}
			}
		}
		return res;
	}

	private static void grupo(int tx, int tz, Filtro filtro, Set<Long> vistos, int minimo, Tipo tipo, int sal, List<Rotulo> res) {
		long k = Geo.clave(tx, tz);
		if (vistos.contains(k) || !filtro.es(tx, tz)) {
			return;
		}
		List<int[]> celdas = new ArrayList<>();
		ArrayDeque<int[]> cola = new ArrayDeque<>();
		vistos.add(k);
		cola.add(new int[] {tx, tz});
		while (!cola.isEmpty()) {
			int[] c = cola.poll();
			celdas.add(c);
			for (int[] d : CRUZ) {
				int nx = c[0] + d[0];
				int nz = c[1] + d[1];
				long nk = Geo.clave(nx, nz);
				if (!vistos.contains(nk) && filtro.es(nx, nz)) {
					vistos.add(nk);
					cola.add(new int[] {nx, nz});
				}
			}
		}
		if (celdas.size() < minimo) {
			return;
		}
		// el sitio que da el nombre: la cumbre de la sierra, lo más hondo del mar, la desembocadura del río
		int[] ref = celdas.get(0);
		int mejor = TerritoriosCliente.altura(ref[0], ref[1]);
		double sx = 0;
		double sz = 0;
		for (int[] c : celdas) {
			int h = TerritoriosCliente.altura(c[0], c[1]);
			boolean gana = tipo == Tipo.SIERRA ? h > mejor : h < mejor;
			if (gana || (h == mejor && (c[0] < ref[0] || (c[0] == ref[0] && c[1] < ref[1])))) {
				mejor = h;
				ref = c;
			}
			sx += c[0] + 0.5;
			sz += c[1] + 0.5;
		}
		Tipo t = tipo == Tipo.MAR && celdas.size() < MAR_GRANDE ? Tipo.GOLFO : tipo;
		double x;
		double z;
		if (tipo == Tipo.RIO) {
			// en un río el centro puede caer fuera del agua: se pone en una casilla del propio río
			int[] m = celdas.get(celdas.size() / 2);
			x = m[0] + 0.5;
			z = m[1] + 0.5;
		} else {
			x = sx / celdas.size();
			z = sz / celdas.size();
		}
		res.add(new Rotulo(x * MapaTerreno.CASILLA, z * MapaTerreno.CASILLA, texto(t, nombre(sal, ref[0], ref[1], t)), t, celdas.size()));
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
