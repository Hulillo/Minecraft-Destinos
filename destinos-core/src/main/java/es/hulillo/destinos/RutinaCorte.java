package es.hulillo.destinos;

/**
 * v0.7.1 La corte: dónde está cada uno de la corte a cada hora del día, y en qué punto del castillo (o de la plaza)
 * cae cada sitio. Pura, sin Minecraft: la prueba la CI.
 */
public final class RutinaCorte {
	private RutinaCorte() {
	}

	/** Los de la corte que tienen cuerpo en la capital. */
	public enum Cargo {
		rey, consorte, hijo, consejero, general
	}

	/** Las horas del día de la rutina. */
	public enum Franja {
		manana, comida, tarde, noche;

		/** La franja de una hora del mundo (0 = las 6:00, 6000 = mediodía, 12000 = las 18:00). */
		public static Franja de(long tiempo) {
			long t = Math.floorMod(tiempo, 24000L);
			return t < 6000 ? manana : t < 7000 ? comida : t < 12000 ? tarde : noche;
		}
	}

	/** Los sitios de la rutina: salas del castillo, o la plaza y la casa noble en las capitales sin castillo. */
	public enum Sitio {
		trono, junto_trono, patio_honor, consejo, patio_armas, comedor, capilla, alcoba_rey, alcoba_reina, alcoba_hijo, biblioteca,
		cuartel, plaza, casa
	}

	/** Dónde está cada cargo a cada hora en una capital con castillo. */
	public static Sitio sitio(Cargo c, Franja f) {
		return switch (f) {
			case manana -> switch (c) {
				case rey -> Sitio.trono;
				case consorte -> Sitio.junto_trono;
				case hijo -> Sitio.patio_honor;
				case consejero -> Sitio.consejo;
				case general -> Sitio.patio_armas;
			};
			case comida -> Sitio.comedor;
			case tarde -> switch (c) {
				case rey, consejero -> Sitio.consejo;
				case consorte -> Sitio.capilla;
				case hijo, general -> Sitio.patio_armas;
			};
			case noche -> switch (c) {
				case rey -> Sitio.alcoba_rey;
				case consorte -> Sitio.alcoba_reina;
				case hijo -> Sitio.alcoba_hijo;
				case consejero -> Sitio.biblioteca;
				case general -> Sitio.cuartel;
			};
		};
	}

	/** En una capital sin castillo: de día en la plaza; de noche en la casa noble, o en la plaza si no hay ninguna. */
	public static Sitio sinCastillo(Franja f, boolean hayCasa) {
		return f == Franja.noche && hayCasa ? Sitio.casa : Sitio.plaza;
	}

	/**
	 * Los puntos (u, y, v) de cada sitio, en coordenadas de su pieza (CastilloCapital, PlazaMayor o CasaNoble): y es
	 * la altura de los pies sobre su suelo. Varios por sitio, para repartir a los que coinciden en la misma sala.
	 */
	private static final int[][][] PUNTOS = new int[Sitio.values().length][][];

	static {
		pon(Sitio.trono, 0, 3, 23);
		pon(Sitio.junto_trono, 2, 3, 23, -2, 3, 23);
		pon(Sitio.patio_honor, -8, 1, -2, 8, 1, -2, -8, 1, -9, 8, 1, -9);
		pon(Sitio.consejo, 16, 7, 18, 19, 7, 18, 17, 7, 21, 20, 7, 21);
		pon(Sitio.patio_armas, -10, 1, -20, 10, 1, -20, 0, 1, -18, -6, 1, -16, 6, 1, -16);
		pon(Sitio.comedor, 16, 1, 18, 18, 1, 18, 20, 1, 18, 16, 1, 21, 18, 1, 21, 20, 1, 21);
		pon(Sitio.capilla, -18, 1, 20, -20, 1, 22);
		pon(Sitio.alcoba_rey, 1, 13, 21);
		pon(Sitio.alcoba_reina, 17, 13, 22);
		pon(Sitio.alcoba_hijo, 21, 7, -8, -21, 7, -8, 21, 7, -1, -21, 7, -1);
		pon(Sitio.biblioteca, -17, 7, 19);
		pon(Sitio.cuartel, -22, 1, -2);
		// la plaza: alrededor de la fuente de la campana; la casa noble: su salón de abajo
		pon(Sitio.plaza, 5, 1, 0, -5, 1, 0, 0, 1, 5, 0, 1, -5, 4, 1, 4, -4, 1, -4);
		pon(Sitio.casa, 0, 1, 2, 2, 1, 2, -2, 1, 2, 0, 1, 0, 2, 1, 0, -2, 1, 0);
	}

	private static void pon(Sitio s, int... uyv) {
		int[][] p = new int[uyv.length / 3][];
		for (int i = 0; i < p.length; i++) {
			p[i] = new int[] {uyv[3 * i], uyv[3 * i + 1], uyv[3 * i + 2]};
		}
		PUNTOS[s.ordinal()] = p;
	}

	/** El punto del sitio para el i-ésimo que está en él (se repiten si son más que puntos). */
	public static int[] punto(Sitio s, int i) {
		int[][] p = PUNTOS[s.ordinal()];
		return p[Math.floorMod(i, p.length)];
	}

	/** Cuántos caben en el sitio sin repetir punto. */
	public static int plazas(Sitio s) {
		return PUNTOS[s.ordinal()].length;
	}

	/** Si el sitio es del castillo (y no de la plaza o la casa noble). */
	public static boolean delCastillo(Sitio s) {
		return s != Sitio.plaza && s != Sitio.casa;
	}
}
