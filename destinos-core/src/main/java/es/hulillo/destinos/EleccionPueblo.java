package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.6.3 Pueblos especializados: qué pueblos propios tiene cada ducado con señor y dónde. Uno con oficio (aldea,
 * mina, aserradero, puerto, o la ciudad del reino), sacado de lo que da su tierra, y un puesto de vigía si linda con
 * otro reino. El ducado de la capital no lleva pueblo propio (ya tiene la capital). Como las capitales, solo depende
 * de la sal y del terreno: sale igual se explore como se explore.
 */
final class EleccionPueblo {
	/** Separación de los 3×3 puntos de cada ducado en los que se mira el terreno (igual que para la capital). */
	private static final int PASO = 128;
	/** Puntos de tierra que hacen falta para que el ducado tenga pueblo (si no, es casi todo mar). */
	private static final int MIN_TIERRA = 4;
	/** Puntos de mar que hacen falta para que el pueblo sea un puerto. */
	private static final int MIN_MAR_PUERTO = 2;
	/** Mineral total (de 0 a 3 por punto; 3 en montañas y badlands) desde el que el ducado es de mina. */
	private static final int MIN_MINERAL = 12;
	/** Madera total (de 0 a 3 por punto; 3 en bosques y taigas) desde la que el ducado es de aserradero. */
	private static final int MIN_MADERA = 15;
	/** Lo lejos que hay que estar de una capital (en bloques) para que salga un pueblo o un puesto. */
	private static final int LEJOS_CAPITAL = 320;
	/** El puesto de vigía, a esta fracción del camino del centro del ducado al del vecino (antes de la frontera). */
	private static final double FRONTERA = 0.4;
	private static final int K_PUESTO = 9187331;

	private final MapaTerreno.Muestreador muestreador;
	private final MapaTerreno mapa;
	private final int sal;

	EleccionPueblo(MapaTerreno.Muestreador muestreador, MapaTerreno mapa, int sal) {
		this.muestreador = muestreador;
		this.mapa = mapa;
		this.sal = sal;
	}

	private record Punto(int x, int z, MapaTerreno.Casilla casilla, boolean costa) {
	}

	/** Los pueblos propios del ducado (dcx, dcz): ninguno, uno o dos (el del oficio y un puesto). */
	List<MapaTerreno.Pueblo> calcula(int dcx, int dcz) {
		int[] c = Geo.centroDucado(dcx, dcz, sal);
		Geo.Reino reino = Geo.reinoDeDucado(dcx, dcz, c[0], c[1], sal);
		if (reino.salvaje()) {
			return List.of();
		}
		MapaTerreno.Capital capital = mapa.capital(reino.kx(), reino.kz());
		List<MapaTerreno.Pueblo> res = new ArrayList<>(2);
		boolean deCapital = capital != null && capital.dcx() == dcx && capital.dcz() == dcz;
		if (!deCapital) {
			boolean ciudad = capital != null && capital.ciudad() == Geo.clave(dcx, dcz);
			boolean puerto = capital != null && capital.puerto() == Geo.clave(dcx, dcz);
			MapaTerreno.Pueblo p = deOficio(dcx, dcz, c, ciudad, puerto);
			if (p != null && lejosDeCapitales(p.x(), p.z(), dcx, dcz)) {
				res.add(p);
			}
		}
		MapaTerreno.Pueblo puesto = puesto(dcx, dcz, c, reino);
		if (puesto != null && lejosDeCapitales(puesto.x(), puesto.z(), dcx, dcz)
				&& res.stream().noneMatch(p -> cerca(p, puesto.x(), puesto.z(), LEJOS_CAPITAL))) {
			res.add(puesto);
		}
		return res;
	}

	/** El pueblo con oficio del ducado, o null si no hay tierra donde ponerlo. */
	private MapaTerreno.Pueblo deOficio(int dcx, int dcz, int[] c, boolean ciudad, boolean puerto) {
		Punto[] puntos = new Punto[9];
		MapaTerreno.Casilla[] casillas = new MapaTerreno.Casilla[9];
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				int k = dx + 1 + (dz + 1) * 3;
				casillas[k] = muestreador.muestra(c[0] + dx * PASO, c[1] + dz * PASO);
			}
		}
		int tierra = 0;
		int mar = 0;
		int comida = 0;
		int mineral = 0;
		int madera = 0;
		for (int k = 0; k < 9; k++) {
			MapaTerreno.Casilla t = casillas[k];
			if (t.oceano()) {
				mar++;
				continue;
			}
			tierra++;
			int rec = Recursos.de(t);
			comida += Recursos.comida(rec);
			mineral += Recursos.mineral(rec);
			madera += Recursos.madera(rec);
			int i = k % 3;
			int j = k / 3;
			boolean costa = false;
			for (int[] v : new int[][] {{i - 1, j}, {i + 1, j}, {i, j - 1}, {i, j + 1}}) {
				if (v[0] >= 0 && v[0] < 3 && v[1] >= 0 && v[1] < 3 && casillas[v[0] + v[1] * 3].oceano()) {
					costa = true;
				}
			}
			puntos[k] = new Punto(c[0] + (i - 1) * PASO, c[1] + (j - 1) * PASO, t, costa);
		}
		// el puerto seguro del reino va aunque el ducado sea más mar que tierra
		if (tierra < (puerto ? 1 : MIN_TIERRA)) {
			return null;
		}
		boolean hayCosta = false;
		for (Punto p : puntos) {
			hayCosta |= p != null && p.costa();
		}
		MapaTerreno.TipoPueblo tipo = puerto ? MapaTerreno.TipoPueblo.puerto : tipo(ciudad, mar, hayCosta, comida, mineral, madera);
		Punto sitio = sitio(tipo, puntos);
		return sitio == null ? null : new MapaTerreno.Pueblo(dcx, dcz, sitio.x(), sitio.z(), tipo);
	}

	/** El oficio del pueblo por lo que da su tierra (pura, para la prueba). */
	static MapaTerreno.TipoPueblo tipo(boolean ciudad, int mar, boolean costa, int comida, int mineral, int madera) {
		if (ciudad) {
			return MapaTerreno.TipoPueblo.ciudad;
		}
		if (mar >= MIN_MAR_PUERTO && costa) {
			return MapaTerreno.TipoPueblo.puerto;
		}
		boolean mina = mineral >= MIN_MINERAL;
		boolean bosque = madera >= MIN_MADERA;
		if (mina && (!bosque || mineral * 4 >= madera * 3)) {
			return MapaTerreno.TipoPueblo.mina;
		}
		if (bosque && madera > comida) {
			return MapaTerreno.TipoPueblo.aserradero;
		}
		return MapaTerreno.TipoPueblo.aldea;
	}

	/**
	 * Dónde va el pueblo entre los puntos de tierra del ducado: el puerto en la costa, la mina al pie de la montaña,
	 * el resto en lo más llano (lo que menos se aleja de la altura de sus vecinos). Solo donde un pueblo
	 * puede empezar de verdad.
	 */
	private Punto sitio(MapaTerreno.TipoPueblo tipo, Punto[] puntos) {
		Punto mejor = null;
		double puntosMejor = Double.NEGATIVE_INFINITY;
		for (int k = 0; k < 9; k++) {
			Punto p = puntos[k];
			if (p == null) {
				continue;
			}
			double v = -desnivel(puntos, k);
			switch (tipo) {
				// en la costa; si el reino solo tiene río grande, junto al río
				case puerto -> v += p.costa() ? 100 : p.casilla().rio() ? 60 : 0;
				// la mina, al pie de la montaña: la bocamina se mete en la ladera y el pueblo queda en lo llano
				case mina -> v += (!p.casilla().montana() && junto(puntos, k, q -> q.casilla().montana()) ? 100 : p.casilla().montana() ? 40 : 0);
				default -> v += k == 4 ? 2 : 0;
			}
			if (v > puntosMejor && muestreador.capitalCabe(p.x(), p.z())) {
				puntosMejor = v;
				mejor = p;
			}
		}
		return mejor;
	}

	/** Si alguno de los vecinos de tierra del punto k cumple la condición. */
	private static boolean junto(Punto[] puntos, int k, java.util.function.Predicate<Punto> condicion) {
		int i = k % 3;
		int j = k / 3;
		for (int[] v : new int[][] {{i - 1, j}, {i + 1, j}, {i, j - 1}, {i, j + 1}}) {
			if (v[0] >= 0 && v[0] < 3 && v[1] >= 0 && v[1] < 3 && puntos[v[0] + v[1] * 3] != null && condicion.test(puntos[v[0] + v[1] * 3])) {
				return true;
			}
		}
		return false;
	}

	/** Lo que se alejan de la altura del punto k sus vecinos de tierra (media). */
	private static double desnivel(Punto[] puntos, int k) {
		int i = k % 3;
		int j = k / 3;
		int suma = 0;
		int n = 0;
		for (int[] v : new int[][] {{i - 1, j}, {i + 1, j}, {i, j - 1}, {i, j + 1}}) {
			if (v[0] >= 0 && v[0] < 3 && v[1] >= 0 && v[1] < 3 && puntos[v[0] + v[1] * 3] != null) {
				suma += Math.abs(puntos[v[0] + v[1] * 3].casilla().altura() - puntos[k].casilla().altura());
				n++;
			}
		}
		return n == 0 ? 0 : (double) suma / n;
	}

	/**
	 * El puesto de vigía, si el ducado linda con un ducado de otro reino: camino de la frontera con el primero de
	 * ellos (en un orden que sale de la sal), en tierra donde quepa.
	 */
	private MapaTerreno.Pueblo puesto(int dcx, int dcz, int[] c, Geo.Reino reino) {
		int[][] vecinos = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
		int giro = Math.floorMod(Geo.hash(dcx, dcz, sal + K_PUESTO), 4);
		for (int n = 0; n < 4; n++) {
			int[] v = vecinos[(n + giro) % 4];
			int vx = dcx + v[0];
			int vz = dcz + v[1];
			int[] cv = Geo.centroDucado(vx, vz, sal);
			Geo.Reino otro = Geo.reinoDeDucado(vx, vz, cv[0], cv[1], sal);
			if (otro.salvaje() || otro.kx() == reino.kx() && otro.kz() == reino.kz()) {
				continue;
			}
			int x = c[0] + (int) Math.round((cv[0] - c[0]) * FRONTERA);
			int z = c[1] + (int) Math.round((cv[1] - c[1]) * FRONTERA);
			if (!muestreador.agua(x, z) && muestreador.capitalCabe(x, z)) {
				return new MapaTerreno.Pueblo(dcx, dcz, x, z, MapaTerreno.TipoPueblo.puesto);
			}
		}
		return null;
	}

	/** Si (x, z) queda lejos de las capitales de los reinos de alrededor del ducado. */
	private boolean lejosDeCapitales(int x, int z, int dcx, int dcz) {
		for (Geo.Reino r : Geo.reinosCerca(dcx, dcz, sal)) {
			MapaTerreno.Capital cap = mapa.capital(r.kx(), r.kz());
			if (cap != null && Math.abs(cap.x() - x) < LEJOS_CAPITAL && Math.abs(cap.z() - z) < LEJOS_CAPITAL) {
				return false;
			}
		}
		return true;
	}

	private static boolean cerca(MapaTerreno.Pueblo p, int x, int z, int d) {
		return Math.abs(p.x() - x) < d && Math.abs(p.z() - z) < d;
	}
}
