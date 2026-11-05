package es.hulillo.destinos;

/**
 * v0.7.1 La corte: el aspecto de una persona en un número (lo que viaja al cliente) y su skin de 64×64 pintada a
 * partir de él. Siempre la misma skin para el mismo número. Pura, sin Minecraft: el servidor arma el número
 * (CorteCapital) y el cliente pinta los píxeles (cliente.RenderPersona); la CI prueba que no cambia.
 *
 * Bits: piel 0-2, pelo 3-5, peinado 6-7, ropa (DyeColor) 8-11, cargo 12-14, mujer 15, canas 16, barba 17, niño 18,
 * semilla 19-26.
 */
public final class AspectoPersona {
	private AspectoPersona() {
	}

	/** Los cargos que cambian la ropa. */
	public static final int REY = 0;
	public static final int CONSORTE = 1;
	public static final int HIJO = 2;
	public static final int CONSEJERO = 3;
	public static final int GENERAL = 4;
	public static final int NOBLE = 5;

	/** Tonos de piel, de más claro a más oscuro. */
	private static final int[] PIELES = {0xF3DCCB, 0xEBC7AA, 0xE0B48E, 0xCF9C74, 0xB98458, 0x9C6A44, 0x7E5233, 0x5E3B24};
	/** Colores de pelo: negro, castaño oscuro, castaño, caoba, rubio oscuro, rubio, pelirrojo, rubio ceniza. */
	private static final int[] PELOS = {0x1E1813, 0x3B2A1E, 0x5A3E28, 0x6E3A22, 0x8C6A3E, 0xC9A35E, 0x9A4422, 0xD8C69A};
	/** El color de cada DyeColor (en su orden: blanco, naranja, magenta… negro), algo apagado para la ropa. */
	private static final int[] TINTES = {0xE4E4DE, 0xD87A2A, 0xA8479E, 0x5E8FC0, 0xD8B640, 0x6FA03A, 0xD48AA2, 0x4E4E4E, 0x9A9A94, 0x3A7E84,
		0x6A3C94, 0x34488C, 0x6A4A30, 0x4E6A2A, 0x963030, 0x222226};

	public static int arma(int piel, int pelo, int peinado, int ropa, int cargo, boolean mujer, boolean canas, boolean barba, boolean nino,
			int semilla) {
		return (piel & 7) | (pelo & 7) << 3 | (peinado & 3) << 6 | (ropa & 15) << 8 | (cargo & 7) << 12 | (mujer ? 1 << 15 : 0)
				| (canas ? 1 << 16 : 0) | (barba ? 1 << 17 : 0) | (nino ? 1 << 18 : 0) | (semilla & 255) << 19;
	}

	public static boolean nino(int a) {
		return (a >> 18 & 1) != 0;
	}

	public static boolean mujer(int a) {
		return (a >> 15 & 1) != 0;
	}

	/** El tono de piel de una cultura (con un poco de variedad por persona: v de 0 a 2). */
	public static int piel(String cultura, int v) {
		int base = switch (cultura) {
			case "norte" -> 0;
			case "llanura", "cerezo", "bambu" -> 1;
			case "costa", "estepa" -> 2;
			case "andina", "islas" -> 3;
			case "selva" -> 4;
			case "desierto" -> 5;
			case "sabana" -> 6;
			default -> 2;
		};
		return Math.max(0, Math.min(7, base + v - 1));
	}

	/** Un pelo típico de la cultura (r de 0 a 99). */
	public static int peloDe(String cultura, int r) {
		return switch (cultura) {
			case "norte" -> r < 35 ? 5 : r < 55 ? 7 : r < 70 ? 6 : r < 90 ? 4 : 2;
			case "llanura", "costa" -> r < 30 ? 2 : r < 55 ? 1 : r < 70 ? 4 : r < 80 ? 3 : r < 90 ? 0 : 6;
			case "estepa" -> r < 40 ? 1 : r < 70 ? 0 : r < 90 ? 2 : 4;
			default -> r < 60 ? 0 : r < 90 ? 1 : 2;
		};
	}

	private static final int ANCHO = 64;

	/** La skin de la persona: 64×64 píxeles ARGB (formato de jugador, brazos anchos). */
	public static int[] pixeles(int a) {
		int[] px = new int[ANCHO * ANCHO];
		int piel = 0xFF000000 | PIELES[a & 7];
		boolean canas = (a >> 16 & 1) != 0;
		int pelo = 0xFF000000 | (canas ? 0xB4B0AA : PELOS[a >> 3 & 7]);
		int peinado = a >> 6 & 3;
		int ropa = 0xFF000000 | TINTES[a >> 8 & 15];
		int cargo = a >> 12 & 7;
		boolean mujer = mujer(a);
		boolean barba = (a >> 17 & 1) != 0;
		int semilla = a >> 19 & 255;
		int calzon = cargo == CONSEJERO ? oscuro(ropa, 0.55) : 0xFF3A2E24;
		int bota = 0xFF2A1E14;
		// la cabeza: piel, con el pelo por arriba, detrás y a los lados
		caja(px, 0, 0, 8, 8, 8, piel);
		cara(px, a, piel, pelo, barba && !mujer && !nino(a));
		pelo(px, pelo, mujer || peinado >= 2, peinado);
		// el cuerpo y los brazos: la ropa; las manos, de piel
		int tunica = cargo == GENERAL ? 0xFF8A8C90 : ropa;
		caja(px, 16, 16, 8, 12, 4, tunica);
		caja(px, 40, 16, 4, 12, 4, tunica);
		caja(px, 32, 48, 4, 12, 4, tunica);
		manos(px, 40, 16, piel);
		manos(px, 32, 48, piel);
		// las piernas: calzas y botas (con falda larga, todo del color de la ropa)
		int pierna = mujer || cargo == CONSEJERO ? oscuro(ropa, 0.85) : calzon;
		caja(px, 0, 16, 4, 12, 4, pierna);
		caja(px, 16, 48, 4, 12, 4, pierna);
		botas(px, 0, 16, bota);
		botas(px, 16, 48, bota);
		// el cinturón y el cuello
		franja(px, 16, 16, 8, 4, 8, 0xFF4A3220);
		cuello(px, ropa);
		switch (cargo) {
			case REY -> {
				corona(px, 0xFFE8C040, 0xFFB02828, true);
				capa(px, oscuro(ropa, 0.7));
			}
			case CONSORTE -> corona(px, 0xFFE8C040, 0xFF3A6AD0, false);
			case HIJO -> diadema(px, 0xFFC8C8D0);
			case CONSEJERO -> tunicaLarga(px, oscuro(ropa, 0.6));
			case GENERAL -> {
				malla(px, semilla);
				sobreveste(px, ropa);
			}
			default -> {
			}
		}
		return px;
	}

	// ------------------------------------------------------------------ las piezas

	/** Pinta las seis caras de una caja del modelo (ancho w, alto h, fondo d) que empieza en (ox, oy) de la textura. */
	private static void caja(int[] px, int ox, int oy, int w, int h, int d, int color) {
		rect(px, ox + d, oy, w, d, aclara(color));
		rect(px, ox + d + w, oy, w, d, oscuro(color, 0.8));
		rect(px, ox, oy + d, d, h, oscuro(color, 0.9));
		rect(px, ox + d, oy + d, w, h, color);
		rect(px, ox + d + w, oy + d, d, h, oscuro(color, 0.9));
		rect(px, ox + d + w + d, oy + d, w, h, oscuro(color, 0.85));
	}

	private static void rect(int[] px, int x, int y, int w, int h, int color) {
		for (int j = y; j < y + h; j++) {
			for (int i = x; i < x + w; i++) {
				px[j * ANCHO + i] = color;
			}
		}
	}

	private static void pon(int[] px, int x, int y, int color) {
		px[y * ANCHO + x] = color;
	}

	/** Ojos, cejas y boca en la cara (8..15, 8..15), y la barba. */
	private static void cara(int[] px, int a, int piel, int pelo, boolean barba) {
		int ojo = (a >> 19 & 3) == 0 ? 0xFF3A6A9A : (a >> 19 & 3) == 1 ? 0xFF4A7A3A : 0xFF4A3020;
		for (int s : new int[] {9, 13}) {
			pon(px, s, 12, 0xFFF4F4F0);
			pon(px, s + 1, 12, ojo);
			pon(px, s, 11, oscuro(pelo, 0.9));
			pon(px, s + 1, 11, oscuro(pelo, 0.9));
		}
		pon(px, 11, 13, oscuro(piel, 0.85));
		pon(px, 12, 13, oscuro(piel, 0.85));
		pon(px, 11, 14, oscuro(piel, 0.6));
		pon(px, 12, 14, oscuro(piel, 0.6));
		if (barba) {
			for (int x = 8; x < 16; x++) {
				pon(px, x, 15, pelo);
				if (x < 10 || x > 13) {
					pon(px, x, 14, pelo);
				}
			}
			pon(px, 10, 13, pelo);
			pon(px, 13, 13, pelo);
			// la barba también por los lados de la cara
			for (int y = 13; y < 16; y++) {
				pon(px, 0 + 7, y, pelo);
				pon(px, 16, y, pelo);
			}
		}
	}

	/** El pelo: arriba, el flequillo, los lados y detrás; largo hasta los hombros si largo. */
	private static void pelo(int[] px, int pelo, boolean largo, int peinado) {
		rect(px, 8, 0, 8, 8, pelo);
		int flequillo = peinado == 1 ? 2 : 1;
		rect(px, 8, 8, 8, flequillo, pelo);
		int lado = largo ? 8 : 3;
		rect(px, 0, 8, 8, lado, oscuro(pelo, 0.9));
		rect(px, 16, 8, 8, lado, oscuro(pelo, 0.9));
		rect(px, 24, 8, 8, largo ? 8 : 6, oscuro(pelo, 0.85));
		if (largo) {
			// la melena cae por los lados de la cara
			for (int y = 8; y < 16; y++) {
				pon(px, 8, y, pelo);
				pon(px, 15, y, pelo);
			}
			// y por la espalda, en la capa del pelo (sombrero), un poco más abajo
			rect(px, 56, 8, 8, 8, oscuro(pelo, 0.85));
		}
	}

	private static void manos(int[] px, int ox, int oy, int piel) {
		// las dos últimas filas de cada cara del brazo
		rect(px, ox, oy + 4 + 10, 16, 2, piel);
		rect(px, ox + 4 + 4, oy, 4, 4, piel);
	}

	private static void botas(int[] px, int ox, int oy, int bota) {
		rect(px, ox, oy + 4 + 9, 16, 3, bota);
		rect(px, ox + 4 + 4, oy, 4, 4, bota);
	}

	/** Una franja de alto h que da la vuelta a la caja (ancho w, fondo d) a la altura fila. */
	private static void franja(int[] px, int ox, int oy, int w, int d, int fila, int color) {
		rect(px, ox, oy + d + fila, 2 * (w + d), 1, color);
	}

	private static void cuello(int[] px, int ropa) {
		rect(px, 22, 20, 4, 1, aclara(ropa));
	}

	/** Corona (o tiara): una fila de oro alrededor de la cabeza en la capa del pelo, con picos y piedras. */
	private static void corona(int[] px, int oro, int piedra, boolean picos) {
		int ox = 32;
		for (int x = ox; x < ox + 32; x++) {
			pon(px, x, 9, oro);
			if (picos && x % 2 == 0) {
				pon(px, x, 8, oro);
			}
		}
		pon(px, 43, 9, piedra);
		pon(px, 44, 9, piedra);
		if (picos) {
			rect(px, 40, 0, 8, 8, 0);
			for (int x = 40; x < 48; x++) {
				pon(px, x, 0, oro);
				pon(px, x, 7, oro);
			}
		}
	}

	private static void diadema(int[] px, int plata) {
		for (int x = 32; x < 64; x++) {
			pon(px, x, 9, plata);
		}
	}

	/** Capa por la espalda (capa de la chaqueta), con el cuello de armiño. */
	private static void capa(int[] px, int color) {
		rect(px, 32, 36, 8, 12, color);
		rect(px, 28, 36, 4, 12, oscuro(color, 0.9));
		rect(px, 16, 36, 4, 12, oscuro(color, 0.9));
		for (int x = 16; x < 40; x++) {
			pon(px, x, 36, x % 3 == 0 ? 0xFF1A1A1A : 0xFFF0F0EA);
		}
	}

	/** Túnica larga hasta los pies (capa de chaqueta y de las perneras). */
	private static void tunicaLarga(int[] px, int color) {
		rect(px, 16, 36, 24, 12, color);
		rect(px, 0, 36, 16, 12, color);
		rect(px, 0, 52, 16, 12, color);
		rect(px, 20, 36, 8, 1, aclara(color));
	}

	/** Cota de malla: el gris del cuerpo y de los brazos (sin las manos), punteado. */
	private static void malla(int[] px, int semilla) {
		punteado(px, 16, 20, 24, 12, semilla);
		punteado(px, 40, 20, 16, 10, semilla);
		punteado(px, 32, 52, 16, 10, semilla);
	}

	private static void punteado(int[] px, int x0, int y0, int w, int h, int semilla) {
		for (int y = y0; y < y0 + h; y++) {
			for (int x = x0; x < x0 + w; x++) {
				if (((x + y + semilla) & 1) == 0) {
					px[y * ANCHO + x] = 0xFF6E7074;
				}
			}
		}
	}

	/** Sobreveste del color del reino encima de la malla, por delante y por detrás. */
	private static void sobreveste(int[] px, int ropa) {
		rect(px, 21, 36, 6, 12, ropa);
		rect(px, 33, 36, 6, 12, oscuro(ropa, 0.85));
	}

	static int oscuro(int c, double f) {
		int r = (int) ((c >> 16 & 255) * f);
		int g = (int) ((c >> 8 & 255) * f);
		int b = (int) ((c & 255) * f);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private static int aclara(int c) {
		int r = Math.min(255, (c >> 16 & 255) + 18);
		int g = Math.min(255, (c >> 8 & 255) + 18);
		int b = Math.min(255, (c & 255) + 18);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
