package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.List;

/**
 * Elige dónde va la capital de cada reino mirando los ducados con señor cerca de su centro: gana el que tiene más
 * tierra llana, río y comida, y está más cerca del centro. Si el reino es de cultura de montaña, a veces prefiere
 * las alturas. Solo depende de la sal y del terreno, así que sale igual se explore como se explore.
 */
final class EleccionCapital {
	/** Ducados alrededor del centro del reino entre los que se elige la capital (en cada eje, a cada lado). */
	private static final int RADIO_CAPITAL = 3;
	/** Las culturas de montaña la levantan en las alturas una de cada tantas veces (en %). */
	private static final int PROB_CAPITAL_MONTANA = 50;
	private static final int K_CAPITAL = 7368787;
	private static final int PASO_CAPITAL = 128;
	/** Ducados mejor puntuados en los que se mira el agua de cerca, y separación de esos puntos. */
	private static final int CANDIDATAS_CAPITAL = 4;
	private static final int PASO_AGUA = 40;
	/** Lo que resta cada punto de agua alrededor de un sitio. */
	private static final double PESO_AGUA = 0.6;
	/** Riqueza (ver nivel) desde la que una capital es normal y rica. */
	private static final double RIQUEZA_NORMAL = 5.0;
	private static final double RIQUEZA_RICA = 7.0;

	private final MapaTerreno.Muestreador muestreador;
	private final int sal;

	EleccionCapital(MapaTerreno.Muestreador muestreador, int sal) {
		this.muestreador = muestreador;
		this.sal = sal;
	}

	/** Un ducado del reino que puede tener la capital: su puntuación y los 3×3 sitios de tierra donde iría. */
	private record Candidata(int cx, int cz, double puntos, List<Sitio> sitios, int recursos) {
	}

	private record Sitio(int x, int z, boolean montana) {
	}

	/** La capital del reino (kx, kz), o null si no tiene ningún ducado donde quepa. */
	MapaTerreno.Capital calcula(int kx, int kz) {
		int[] k = Geo.centroReino(kx, kz, sal);
		boolean montana = muestreador.culturaDeMontana(k[0], k[1])
				&& Math.floorMod(Geo.hash(kx, kz, sal + K_CAPITAL), 100) < PROB_CAPITAL_MONTANA;
		List<Candidata> candidatas = candidatas(kx, kz, k, montana);
		int ducadosReino = candidatas.size();
		// las mejores, mirando de cerca el agua alrededor de cada sitio posible: una capital entre islas o junto a
		// un lago se queda en la plaza, porque las calles sobre el agua no se ponen
		candidatas.sort((a, b) -> Double.compare(b.puntos(), a.puntos()));
		Candidata ganadora = null;
		Sitio sitio = null;
		double puntosMejor = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < Math.min(CANDIDATAS_CAPITAL, candidatas.size()); i++) {
			Candidata cand = candidatas.get(i);
			// el agua solo resta: una candidata que ya no llega a la mejor no puede ganar (ni las siguientes)
			if (cand.puntos() <= puntosMejor) {
				break;
			}
			boolean hayMontana = cand.sitios().stream().anyMatch(Sitio::montana);
			for (Sitio s : cand.sitios()) {
				if (montana && hayMontana && !s.montana()) {
					continue;
				}
				// se deja de contar agua en cuanto el sitio ya no puede ganar a la mejor
				int tope = puntosMejor == Double.NEGATIVE_INFINITY ? Integer.MAX_VALUE
						: (int) Math.floor((cand.puntos() - puntosMejor) / PESO_AGUA);
				double puntos = cand.puntos() - aguaAlrededor(s.x(), s.z(), tope) * PESO_AGUA;
				// solo donde el pueblo de la capital puede empezar (se mira al final: es lo más caro)
				if (puntos > puntosMejor && muestreador.capitalCabe(s.x(), s.z())) {
					puntosMejor = puntos;
					ganadora = cand;
					sitio = s;
				}
			}
		}
		if (ganadora == null) {
			// en ninguno de los mejores ducados cabe: el primer sitio de cualquier ducado del reino donde sí quepa
			for (Candidata cand : candidatas) {
				for (Sitio s : cand.sitios()) {
					if (muestreador.capitalCabe(s.x(), s.z())) {
						return capital(cand, s, montana, ducadosReino);
					}
				}
			}
			return null;
		}
		return capital(ganadora, sitio, montana, ducadosReino);
	}

	private static MapaTerreno.Capital capital(Candidata cand, Sitio s, boolean montana, int ducadosReino) {
		double riqueza = riqueza(cand.recursos(), ducadosReino);
		return new MapaTerreno.Capital(cand.cx(), cand.cz(), s.x(), s.z(), montana && s.montana(), nivel(riqueza), riqueza);
	}

	/** Los ducados con señor del reino cerca de su centro (k), con sus puntos sin contar el agua. */
	private List<Candidata> candidatas(int kx, int kz, int[] k, boolean montana) {
		int c0x = Geo.celdaDucado(k[0]);
		int c0z = Geo.celdaDucado(k[1]);
		List<Candidata> candidatas = new ArrayList<>();
		for (int cx = c0x - RADIO_CAPITAL; cx <= c0x + RADIO_CAPITAL; cx++) {
			for (int cz = c0z - RADIO_CAPITAL; cz <= c0z + RADIO_CAPITAL; cz++) {
				int[] c = Geo.centroDucado(cx, cz, sal);
				Geo.Reino r = Geo.reinoDeDucado(cx, cz, c[0], c[1], sal);
				if (r.kx() != kx || r.kz() != kz || r.salvaje()) {
					continue;
				}
				Candidata cand = candidata(cx, cz, c, k, montana);
				if (cand != null) {
					candidatas.add(cand);
				}
			}
		}
		return candidatas;
	}

	/** Puntos del ducado (cx, cz) con centro c, mirando 3×3 puntos a su alrededor; null si es casi todo mar. */
	private Candidata candidata(int cx, int cz, int[] c, int[] k, boolean montana) {
		double puntos = 0;
		int recursos = 0;
		int tierra = 0;
		int alta = Integer.MIN_VALUE;
		int baja = Integer.MAX_VALUE;
		List<Sitio> sitios = new ArrayList<>();
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				int x = c[0] + dx * PASO_CAPITAL;
				int z = c[1] + dz * PASO_CAPITAL;
				MapaTerreno.Casilla t = muestreador.muestra(x, z);
				if (t.oceano()) {
					continue;
				}
				tierra++;
				alta = Math.max(alta, t.altura());
				baja = Math.min(baja, t.altura());
				int rec = Recursos.de(t);
				recursos += Recursos.total(rec);
				puntos += Recursos.comida(rec) * 0.5;
				if (t.rio()) {
					puntos += 1;
				}
				if (t.montana()) {
					puntos += montana ? 2 : -1;
				}
				sitios.add(new Sitio(x, z, t.montana()));
			}
		}
		if (tierra < 5) {
			return null;
		}
		if (!montana) {
			puntos -= (alta - baja) / 12.0;
		}
		double dx = c[0] - k[0];
		double dz = c[1] - k[1];
		puntos += tierra * 0.5 - Math.sqrt(dx * dx + dz * dz) / 600.0;
		return new Candidata(cx, cz, puntos, sitios, recursos);
	}

	/**
	 * Riqueza de la capital: recursos medios por casilla de su ducado (0-10, lo normal 3-5) más el tamaño del
	 * reino cerca de su centro (ducados con señor, hasta 49). 0 pobre, 1 normal, 2 rica.
	 */
	static double riqueza(int recursos, int ducados) {
		return recursos / 9.0 + ducados / 8.0;
	}

	static int nivel(double riqueza) {
		return riqueza < RIQUEZA_NORMAL ? 0 : riqueza < RIQUEZA_RICA ? 1 : 2;
	}

	/** Cuántos de los 5×5 puntos alrededor de (x, z), hasta donde llega la capital, son agua (deja de contar pasado tope). */
	private int aguaAlrededor(int x, int z, int tope) {
		int agua = 0;
		for (int dz = -2; dz <= 2; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				if (muestreador.agua(x + dx * PASO_AGUA, z + dz * PASO_AGUA) && ++agua > tope) {
					return agua;
				}
			}
		}
		return agua;
	}
}
