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

	/** Oro, plata, armiño y los cueros de cinturones y botas. */
	private static final int ORO = 0xFFE2B33C;
	private static final int ORO_OSCURO = 0xFFA87A1E;
	private static final int PLATA = 0xFFC9CDD4;
	private static final int ACERO = 0xFF8E939B;
	private static final int ARMINO = 0xFFF2EFE6;
	private static final int CUERO = 0xFF5A3A22;
	private static final int BOTA = 0xFF3A2616;
	private static final int[] GEMAS = {0xFFC0263A, 0xFF2E5FD0, 0xFF2E9A4E, 0xFF8A3AB8};

	/** La skin de la persona: 64×64 píxeles ARGB (formato de jugador, brazos anchos). */
	public static int[] pixeles(int a) {
		int[] px = new int[ANCHO * ANCHO];
		Rasgos r = new Rasgos(a);
		cabeza(px, r);
		pelo(px, r);
		ropa(px, r);
		piernas(px, r);
		switch (r.cargo) {
			case REY -> rey(px, r);
			case CONSORTE -> consorte(px, r);
			case HIJO -> hijo(px, r);
			case CONSEJERO -> consejero(px, r);
			case GENERAL -> general(px, r);
			default -> noble(px, r);
		}
		return px;
	}

	/** Lo que sale del número, ya en colores. */
	private static final class Rasgos {
		final int piel;
		final int pelo;
		final int peinado;
		final int ropa;
		/** Segundo color de la ropa: ribetes, botones, bordados. */
		final int orla;
		final int cargo;
		final boolean mujer;
		final boolean canas;
		final boolean barba;
		final boolean nino;
		final int semilla;
		final int ojos;
		/** Falda o túnica hasta los pies (mujeres y consejeros). */
		final boolean larga;

		Rasgos(int a) {
			piel = 0xFF000000 | PIELES[a & 7];
			canas = (a >> 16 & 1) != 0;
			pelo = 0xFF000000 | (canas ? 0xBDB8B0 : PELOS[a >> 3 & 7]);
			peinado = a >> 6 & 3;
			ropa = 0xFF000000 | TINTES[a >> 8 & 15];
			cargo = a >> 12 & 7;
			mujer = mujer(a);
			nino = nino(a);
			barba = (a >> 17 & 1) != 0 && !mujer && !nino;
			semilla = a >> 19 & 255;
			ojos = switch (semilla & 3) {
				case 0 -> 0xFF3E6E9E;
				case 1 -> 0xFF4E7A3A;
				case 2 -> 0xFF6A4424;
				default -> 0xFF3A2A1E;
			};
			larga = mujer || cargo == CONSEJERO;
			boolean real = cargo == REY || cargo == CONSORTE || cargo == HIJO;
			orla = real ? ORO : switch (semilla >> 2 & 3) {
				case 0 -> aclara(aclara(ropa));
				case 1 -> 0xFFE8E0CC;
				case 2 -> oscuro(ropa, 0.55);
				default -> 0xFFB08A3E;
			};
		}
	}

	// ------------------------------------------------------------------ la cabeza

	private static void cabeza(int[] px, Rasgos r) {
		caja(px, 0, 0, 8, 8, 8, r.piel, r.semilla, 0.03);
		// la cara (8..15, 8..15): bordes algo en sombra, mejillas, cejas, ojos, nariz y boca
		for (int y = 8; y < 16; y++) {
			pon(px, 8, y, oscuro(get(px, 8, y), 0.93));
			pon(px, 15, y, oscuro(get(px, 15, y), 0.93));
		}
		int ceja = oscuro(r.canas ? 0xFF9A958E : r.pelo, 0.85);
		pon(px, 9, 11, ceja);
		pon(px, 10, 11, ceja);
		pon(px, 13, 11, ceja);
		pon(px, 14, 11, ceja);
		// ojos: blanco por fuera, el iris hacia dentro, con un brillo
		pon(px, 9, 12, 0xFFF2F0EA);
		pon(px, 10, 12, r.ojos);
		pon(px, 13, 12, r.ojos);
		pon(px, 14, 12, 0xFFF2F0EA);
		pon(px, 11, 12, oscuro(r.piel, 0.96));
		pon(px, 12, 12, oscuro(r.piel, 0.96));
		// nariz: el puente claro y la sombra de debajo
		pon(px, 11, 13, aclara(r.piel));
		pon(px, 12, 13, oscuro(r.piel, 0.86));
		pon(px, 11, 14, oscuro(r.piel, 0.9));
		pon(px, 12, 14, oscuro(r.piel, 0.9));
		// boca
		int labio = mezcla(oscuro(r.piel, 0.7), 0xFFA04848, r.mujer ? 0.45 : 0.2);
		pon(px, 11, 15, labio);
		pon(px, 12, 15, labio);
		pon(px, 10, 15, oscuro(r.piel, 0.92));
		pon(px, 13, 15, oscuro(r.piel, 0.92));
		if (r.mujer || r.nino) {
			int rubor = mezcla(r.piel, 0xFFE07A7A, 0.3);
			pon(px, 9, 14, rubor);
			pon(px, 14, 14, rubor);
		}
		if (r.canas) {
			// arrugas junto a los ojos
			pon(px, 8, 12, oscuro(r.piel, 0.82));
			pon(px, 15, 12, oscuro(r.piel, 0.82));
		}
		// orejas en los lados
		for (int x : new int[] {3, 4}) {
			pon(px, x, 12, oscuro(r.piel, 0.86));
			pon(px, 16 + 7 - x, 12, oscuro(r.piel, 0.86));
		}
		pon(px, 4, 13, oscuro(r.piel, 0.78));
		pon(px, 19, 13, oscuro(r.piel, 0.78));
		if (r.barba) {
			barba(px, r);
		}
	}

	/** Cuatro barbas: cerrada, perilla con bigote, bigote solo o de varios días. */
	private static void barba(int[] px, Rasgos r) {
		int b = r.canas ? 0xFFCFCAC2 : oscuro(r.pelo, 0.95);
		switch (r.semilla >> 4 & 3) {
			case 0 -> {
				for (int x = 8; x < 16; x++) {
					pon(px, x, 15, veta(b, x, 15, r.semilla));
					if (x <= 9 || x >= 14) {
						pon(px, x, 14, veta(b, x, 14, r.semilla));
						pon(px, x, 13, veta(b, x, 13, r.semilla));
					}
				}
				pon(px, 10, 14, b);
				pon(px, 13, 14, b);
				pon(px, 11, 15, oscuro(b, 0.6));
				pon(px, 12, 15, oscuro(b, 0.6));
				// y por los lados de la cara y bajo la barbilla
				for (int y = 13; y < 16; y++) {
					pon(px, 7, y, b);
					pon(px, 6, y, oscuro(b, 0.9));
					pon(px, 16, y, b);
					pon(px, 17, y, oscuro(b, 0.9));
				}
				for (int x = 16; x < 24; x++) {
					pon(px, x, 0 + 7, b);
				}
			}
			case 1 -> {
				for (int x = 10; x < 14; x++) {
					pon(px, x, 14, b);
				}
				pon(px, 11, 15, b);
				pon(px, 12, 15, b);
			}
			case 2 -> {
				for (int x = 9; x < 15; x++) {
					pon(px, x, 14, b);
				}
				pon(px, 9, 15, b);
				pon(px, 14, 15, b);
			}
			default -> {
				for (int x = 8; x < 16; x++) {
					for (int y = 14; y < 16; y++) {
						if (((x * 3 + y + r.semilla) & 1) == 0) {
							pon(px, x, y, mezcla(get(px, x, y), b, 0.45));
						}
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ el pelo

	/** Cortos con o sin flequillo, raya al lado y melena; ellas melena, trenza, moño o media melena. */
	private static void pelo(int[] px, Rasgos r) {
		int p = r.pelo;
		// arriba de la cabeza y la nuca
		for (int x = 8; x < 16; x++) {
			for (int y = 0; y < 8; y++) {
				pon(px, x, y, veta(p, x, y, r.semilla));
			}
		}
		boolean largo = r.mujer ? r.peinado != 3 : r.peinado == 3;
		int lado = largo ? 8 : r.peinado == 0 ? 2 : 3;
		for (int y = 8; y < 8 + lado; y++) {
			for (int x = 0; x < 8; x++) {
				pon(px, x, y, veta(oscuro(p, 0.92), x, y, r.semilla));
				pon(px, 16 + x, y, veta(oscuro(p, 0.92), x, y, r.semilla));
			}
		}
		for (int y = 8; y < 16; y++) {
			if (y < 8 + (largo ? 8 : 5)) {
				for (int x = 24; x < 32; x++) {
					pon(px, x, y, veta(oscuro(p, 0.86), x, y, r.semilla));
				}
			}
		}
		// patillas junto a la oreja (sin tapar la oreja)
		if (!largo) {
			pon(px, 6, 8 + lado, oscuro(p, 0.92));
			pon(px, 17, 8 + lado, oscuro(p, 0.92));
		}
		// el flequillo sobre la frente
		for (int x = 8; x < 16; x++) {
			pon(px, x, 8, veta(p, x, 8, r.semilla));
		}
		if (r.peinado == 1 || (r.mujer && r.peinado == 3)) {
			for (int x = 8; x < 16; x++) {
				if (x != 11 && x != 12) {
					pon(px, x, 9, veta(p, x, 9, r.semilla));
				}
			}
		} else if (r.peinado == 2) {
			for (int x = 8; x < 12; x++) {
				pon(px, x, 9, veta(p, x, 9, r.semilla));
			}
			pon(px, 8, 10, p);
		}
		if (largo) {
			// la melena enmarca la cara y abulta por encima (capa del sombrero, sin tapar la cara)
			for (int y = 9; y < 16; y++) {
				pon(px, 8, y, veta(p, 8, y, r.semilla));
				pon(px, 15, y, veta(p, 15, y, r.semilla));
			}
			for (int y = 8; y < 16; y++) {
				for (int x = 32; x < 40; x++) {
					if (x > 34 || y < 12) {
						pon(px, x, y, veta(oscuro(p, 0.9), x, y, r.semilla + 1));
					}
				}
				for (int x = 48; x < 64; x++) {
					if (x < 56 ? x < 53 || y < 12 : true) {
						pon(px, x, y, veta(oscuro(p, x < 56 ? 0.9 : 0.84), x, y, r.semilla + 1));
					}
				}
			}
			// y cae por la espalda
			int cae = r.mujer ? 4 : 2;
			for (int y = 36; y < 36 + cae; y++) {
				for (int x = 32; x < 40; x++) {
					pon(px, x, y, veta(oscuro(p, 0.84), x, y, r.semilla));
				}
			}
		}
		if (r.mujer && r.peinado == 1) {
			// trenza por la espalda, del pelo a la cintura
			for (int y = 36; y < 46; y++) {
				int c = (y & 1) == 0 ? p : oscuro(p, 0.8);
				pon(px, 35, y, c);
				pon(px, 36, y, (y & 1) == 0 ? oscuro(p, 0.8) : p);
			}
			pon(px, 35, 46, r.orla);
			pon(px, 36, 46, r.orla);
		} else if (r.mujer && r.peinado == 2) {
			// moño: un bulto arriba y atrás
			for (int x = 42; x < 46; x++) {
				for (int y = 3; y < 7; y++) {
					pon(px, x, y, veta(p, x, y, r.semilla));
				}
			}
			for (int x = 58; x < 62; x++) {
				pon(px, x, 8, oscuro(p, 0.85));
				pon(px, x, 9, oscuro(p, 0.85));
			}
		}
	}

	// ------------------------------------------------------------------ la ropa

	/** Jubón o vestido: tela con sombras, cuello en pico, botones, cinturón con hebilla, ribetes y puños. */
	private static void ropa(int[] px, Rasgos r) {
		int tela = r.cargo == GENERAL ? 0xFF6E7076 : r.ropa;
		caja(px, 16, 16, 8, 12, 4, tela, r.semilla, 0.05);
		caja(px, 40, 16, 4, 12, 4, tela, r.semilla + 3, 0.05);
		caja(px, 32, 48, 4, 12, 4, tela, r.semilla + 5, 0.05);
		// cuello en pico con su ribete
		pon(px, 23, 20, r.piel);
		pon(px, 24, 20, r.piel);
		pon(px, 23, 21, oscuro(r.piel, 0.85));
		pon(px, 24, 21, oscuro(r.piel, 0.85));
		pon(px, 22, 20, r.orla);
		pon(px, 25, 20, r.orla);
		pon(px, 22, 21, r.orla);
		pon(px, 25, 21, r.orla);
		for (int x = 20; x < 28; x++) {
			if (x < 22 || x > 25) {
				pon(px, x, 20, oscuro(tela, 0.85));
			}
		}
		// la abotonadura del pecho
		for (int y = 22; y < 27; y++) {
			pon(px, 23, y, oscuro(tela, 0.8));
			pon(px, 24, y, aclara(tela));
			if ((y & 1) == 0) {
				pon(px, 23, y, r.orla);
			}
		}
		// cinturón de cuero con hebilla, alrededor del cuerpo
		for (int x = 16; x < 40; x++) {
			pon(px, x, 27, oscuro(CUERO, (x & 1) == 0 ? 1 : 0.88));
		}
		pon(px, 23, 27, ORO);
		pon(px, 24, 27, ORO_OSCURO);
		pon(px, 25, 28, oscuro(CUERO, 0.8));
		// el faldón por debajo, con ribete al borde
		for (int x = 16; x < 40; x++) {
			pon(px, x, 31, r.orla == ORO ? ORO_OSCURO : oscuro(r.orla, 0.9));
			pon(px, x, 28, oscuro(get(px, x, 28), 0.9));
		}
		// hombros con luz y puños con ribete; las manos, de piel
		for (int[] brazo : new int[][] {{40, 16}, {32, 48}}) {
			int ox = brazo[0];
			int oy = brazo[1];
			for (int x = ox; x < ox + 16; x++) {
				pon(px, x, oy + 4, aclara(get(px, x, oy + 4)));
				pon(px, x, oy + 4 + 9, r.orla);
				pon(px, x, oy + 4 + 10, r.piel);
				pon(px, x, oy + 4 + 11, oscuro(r.piel, 0.9));
			}
			rect(px, ox + 8, oy, 4, 4, r.piel);
			// pliegue del codo
			pon(px, ox + 5, oy + 10, oscuro(get(px, ox + 5, oy + 10), 0.85));
			pon(px, ox + 6, oy + 10, oscuro(get(px, ox + 6, oy + 10), 0.85));
		}
	}

	/** Calzas con rodilla y costura y botas con caña doblada, cordones y suela; o falda larga hasta los pies. */
	private static void piernas(int[] px, Rasgos r) {
		int calza = r.larga ? oscuro(r.ropa, r.cargo == CONSEJERO ? 0.7 : 0.88)
				: switch (r.semilla >> 6 & 3) {
					case 0 -> 0xFF3E3226;
					case 1 -> 0xFF2E3442;
					case 2 -> oscuro(r.ropa, 0.6);
					default -> 0xFF4A3A2A;
				};
		caja(px, 0, 16, 4, 12, 4, calza, r.semilla + 7, 0.05);
		caja(px, 16, 48, 4, 12, 4, calza, r.semilla + 9, 0.05);
		for (int[] pierna : new int[][] {{0, 16}, {16, 48}}) {
			int ox = pierna[0];
			int oy = pierna[1];
			if (r.larga) {
				// pliegues de la falda y el bajo con ribete
				for (int y = oy + 4; y < oy + 16; y++) {
					for (int x = ox; x < ox + 16; x += 3) {
						pon(px, x, y, oscuro(get(px, x, y), 0.88));
					}
				}
				for (int x = ox; x < ox + 16; x++) {
					pon(px, x, oy + 4 + 10, r.orla);
					pon(px, x, oy + 4 + 11, oscuro(BOTA, 1.2));
				}
				rect(px, ox + 8, oy, 4, 4, BOTA);
				continue;
			}
			for (int x = ox; x < ox + 16; x++) {
				pon(px, x, oy + 4 + 5, oscuro(get(px, x, oy + 4 + 5), 0.88));
				// botas: caña doblada, cuero y suela
				pon(px, x, oy + 4 + 7, aclara(aclara(BOTA)));
				pon(px, x, oy + 4 + 8, BOTA);
				pon(px, x, oy + 4 + 9, BOTA);
				pon(px, x, oy + 4 + 10, BOTA);
				pon(px, x, oy + 4 + 11, 0xFF1E140C);
			}
			// cordones por delante
			pon(px, ox + 5, oy + 4 + 8, aclara(BOTA));
			pon(px, ox + 6, oy + 4 + 9, aclara(BOTA));
			pon(px, ox + 5, oy + 4 + 10, aclara(BOTA));
			// costura por fuera
			for (int y = oy + 4; y < oy + 4 + 7; y++) {
				pon(px, ox + 1, y, oscuro(get(px, ox + 1, y), 0.85));
			}
			rect(px, ox + 8, oy, 4, 4, 0xFF1E140C);
		}
	}

	// ------------------------------------------------------------------ cada cargo

	private static void rey(int[] px, Rasgos r) {
		corona(px, r, true);
		// manto con pliegues por la espalda y los lados, cuello de armiño con sus colas negras
		int manto = oscuro(r.ropa, 0.72);
		for (int y = 36; y < 48; y++) {
			for (int x = 32; x < 40; x++) {
				pon(px, x, y, veta(((x & 1) == 0 ? manto : oscuro(manto, 0.85)), x, y, r.semilla));
			}
			for (int x : new int[] {16, 17, 18, 19, 28, 29, 30, 31}) {
				pon(px, x, y, veta(oscuro(manto, 0.9), x, y, r.semilla));
			}
		}
		for (int x = 32; x < 40; x++) {
			pon(px, x, 47, ORO_OSCURO);
		}
		for (int x = 16; x < 40; x++) {
			pon(px, x, 36, (x + 1) % 4 == 0 ? 0xFF1A1A1A : ARMINO);
			pon(px, x, 37, x % 4 == 2 ? 0xFF1A1A1A : ARMINO);
		}
		rect(px, 20, 32, 8, 4, ARMINO);
		bordado(px, r, ORO);
		cadena(px, ORO, GEMAS[r.semilla & 3]);
		// hombreras de armiño en las mangas
		for (int[] m : new int[][] {{40, 32}, {48, 48}}) {
			for (int x = m[0] + 4; x < m[0] + 8; x++) {
				pon(px, x, m[1], ARMINO);
			}
			for (int x = m[0]; x < m[0] + 16; x++) {
				pon(px, x, m[1] + 4, x % 3 == 0 ? 0xFF1A1A1A : ARMINO);
			}
		}
	}

	private static void consorte(int[] px, Rasgos r) {
		corona(px, r, false);
		bordado(px, r, ORO);
		// collar de perlas
		for (int x = 21; x < 27; x++) {
			if ((x & 1) == 1) {
				pon(px, x, 22, 0xFFF4F0E8);
			}
		}
		pon(px, 23, 23, GEMAS[r.semilla & 3]);
		// corpiño con lazada
		for (int y = 23; y < 27; y++) {
			pon(px, 22 + (y & 1), y, ORO);
			pon(px, 25 - (y & 1), y, ORO);
		}
	}

	private static void hijo(int[] px, Rasgos r) {
		// diadema de plata con una piedra
		for (int x = 32; x < 64; x++) {
			pon(px, x, 9, (x & 3) == 0 ? oscuro(PLATA, 0.8) : PLATA);
		}
		pon(px, 44, 9, GEMAS[(r.semilla + 1) & 3]);
		// banda cruzada sobre el pecho
		for (int i = 0; i < 8; i++) {
			pon(px, 20 + i, 21 + i, r.orla);
			if (i < 7) {
				pon(px, 21 + i, 21 + i, oscuro(r.orla, 0.8));
			}
			pon(px, 39 - i, 21 + i, r.orla);
		}
	}

	private static void consejero(int[] px, Rasgos r) {
		// túnica larga encima (capa de la chaqueta y de las perneras), con capucha caída a la espalda
		int toga = oscuro(r.ropa, 0.6);
		for (int[] z : new int[][] {{16, 36, 24, 12}, {0, 36, 16, 12}, {0, 52, 16, 12}}) {
			for (int y = z[1]; y < z[1] + z[3]; y++) {
				for (int x = z[0]; x < z[0] + z[2]; x++) {
					boolean delante = (x >= 20 && x < 28 && z[1] == 36 && z[0] == 16);
					if (delante && x >= 22 && x < 26 && y < 46) {
						continue;
					}
					pon(px, x, y, veta(x % 3 == 0 ? oscuro(toga, 0.86) : toga, x, y, r.semilla));
				}
			}
		}
		for (int x = 32; x < 40; x++) {
			pon(px, x, 36, oscuro(toga, 0.75));
			pon(px, x, 37, oscuro(toga, 0.75));
			pon(px, x, 38, oscuro(toga, 0.8));
		}
		for (int y = 36; y < 48; y++) {
			pon(px, 21, y, r.orla);
			pon(px, 26, y, r.orla);
		}
		// mangas anchas
		for (int[] m : new int[][] {{40, 32}, {48, 48}}) {
			for (int x = m[0]; x < m[0] + 16; x++) {
				for (int y = m[1] + 4 + 6; y < m[1] + 4 + 10; y++) {
					pon(px, x, y, veta(toga, x, y, r.semilla));
				}
				pon(px, x, m[1] + 4 + 9, r.orla);
			}
		}
		cadena(px, ORO, ORO_OSCURO);
	}

	private static void general(int[] px, Rasgos r) {
		// cota de malla en el cuerpo y los brazos (sin las manos)
		malla(px, 16, 20, 24, 8);
		malla(px, 40, 20, 16, 9);
		malla(px, 32, 52, 16, 9);
		// sobreveste del color del reino, con la cruz del ribete, por delante y por detrás
		for (int[] cara : new int[][] {{20, 36}, {32, 36}}) {
			for (int y = cara[1]; y < cara[1] + 12; y++) {
				for (int x = cara[0] + 1; x < cara[0] + 7; x++) {
					pon(px, x, y, veta(r.ropa, x, y, r.semilla));
				}
			}
			for (int y = cara[1] + 1; y < cara[1] + 9; y++) {
				pon(px, cara[0] + 3, y, r.orla);
				pon(px, cara[0] + 4, y, r.orla);
			}
			for (int x = cara[0] + 2; x < cara[0] + 6; x++) {
				pon(px, x, cara[1] + 3, r.orla);
			}
			for (int x = cara[0] + 1; x < cara[0] + 7; x++) {
				pon(px, x, cara[1] + 7, CUERO);
			}
		}
		// hombreras y brazales de acero
		for (int[] m : new int[][] {{40, 32}, {48, 48}}) {
			rect(px, m[0] + 4, m[1], 4, 4, ACERO);
			for (int x = m[0]; x < m[0] + 16; x++) {
				pon(px, x, m[1] + 4, aclara(ACERO));
				pon(px, x, m[1] + 5, ACERO);
				pon(px, x, m[1] + 6, oscuro(ACERO, 0.8));
				pon(px, x, m[1] + 4 + 7, ACERO);
				pon(px, x, m[1] + 4 + 8, oscuro(ACERO, 0.85));
			}
		}
		// grebas sobre las botas
		for (int[] g : new int[][] {{0, 32}, {0, 48}}) {
			for (int x = g[0]; x < g[0] + 16; x++) {
				for (int y = g[1] + 4 + 6; y < g[1] + 4 + 9; y++) {
					pon(px, x, y, y == g[1] + 4 + 6 ? aclara(ACERO) : ACERO);
				}
			}
		}
	}

	private static void noble(int[] px, Rasgos r) {
		bordado(px, r, r.orla);
	}

	/** Corona (con picos y piedras de colores, que se ve también desde arriba) o tiara. */
	private static void corona(int[] px, Rasgos r, boolean rey) {
		if (rey) {
			for (int x = 32; x < 64; x++) {
				pon(px, x, 9, (x & 3) == 0 ? GEMAS[(x / 4 + r.semilla) & 3] : ORO);
				pon(px, x, 10, ORO_OSCURO);
				if ((x & 1) == 0) {
					pon(px, x, 8, ORO);
				}
			}
			for (int i = 0; i < 8; i++) {
				pon(px, 40 + i, 0, ORO);
				pon(px, 40 + i, 7, ORO);
				pon(px, 40, i, ORO);
				pon(px, 47, i, ORO);
			}
			pon(px, 40, 0, GEMAS[0]);
			pon(px, 47, 7, GEMAS[1]);
			return;
		}
		for (int x = 32; x < 64; x++) {
			pon(px, x, 9, ORO);
		}
		pon(px, 43, 8, ORO);
		pon(px, 44, 8, ORO);
		pon(px, 43, 9, GEMAS[(r.semilla + 2) & 3]);
		pon(px, 44, 9, GEMAS[(r.semilla + 2) & 3]);
	}

	/** Bordado de puntos en el pecho y en los bajos de las mangas. */
	private static void bordado(int[] px, Rasgos r, int hilo) {
		for (int x = 20; x < 28; x++) {
			if (((x + r.semilla) & 1) == 0 && x != 23 && x != 24) {
				pon(px, x, 23, hilo);
			}
		}
		for (int x = 32; x < 40; x++) {
			if ((x & 1) == 0) {
				pon(px, x, 22, hilo);
			}
		}
	}

	/** Cadena en pico sobre el pecho con su medallón. */
	private static void cadena(int[] px, int metal, int piedra) {
		for (int i = 0; i < 3; i++) {
			pon(px, 21 + i, 21 + i, metal);
			pon(px, 26 - i, 21 + i, metal);
		}
		pon(px, 23, 24, metal);
		pon(px, 24, 24, metal);
		pon(px, 23, 25, piedra);
		pon(px, 24, 25, oscuro(piedra, 0.8));
	}

	/** Anillas de la malla: filas desplazadas de puntos claros y oscuros. */
	private static void malla(int[] px, int x0, int y0, int w, int h) {
		for (int y = y0; y < y0 + h; y++) {
			for (int x = x0; x < x0 + w; x++) {
				int k = (x + (y & 1)) & 1;
				pon(px, x, y, k == 0 ? 0xFF7C8088 : 0xFF54585E);
			}
		}
	}

	// ------------------------------------------------------------------ pinceles

	/**
	 * Pinta las seis caras de una caja del modelo (ancho w, alto h, fondo d) que empieza en (ox, oy) de la textura:
	 * arriba más luz, abajo y detrás más sombra, un degradado de arriba abajo y algo de grano (ruido).
	 */
	private static void caja(int[] px, int ox, int oy, int w, int h, int d, int color, int semilla, double grano) {
		cara(px, ox + d, oy, w, d, aclara(color), semilla, grano, false);
		cara(px, ox + d + w, oy, w, d, oscuro(color, 0.72), semilla, grano, false);
		cara(px, ox, oy + d, d, h, oscuro(color, 0.9), semilla, grano, true);
		cara(px, ox + d, oy + d, w, h, color, semilla, grano, true);
		cara(px, ox + d + w, oy + d, d, h, oscuro(color, 0.9), semilla, grano, true);
		cara(px, ox + d + w + d, oy + d, w, h, oscuro(color, 0.82), semilla, grano, true);
	}

	private static void cara(int[] px, int x0, int y0, int w, int h, int color, int semilla, double grano, boolean degradado) {
		for (int y = y0; y < y0 + h; y++) {
			double f = degradado ? 1.05 - 0.13 * (y - y0) / Math.max(1, h - 1) : 1;
			for (int x = x0; x < x0 + w; x++) {
				double n = 1 + grano * ruido(x, y, semilla);
				px[y * ANCHO + x] = escala(color, f * n);
			}
		}
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

	private static int get(int[] px, int x, int y) {
		return px[y * ANCHO + x];
	}

	/** Un pelo o una tela con vetas: cada píxel un poco más claro u oscuro. */
	private static int veta(int c, int x, int y, int semilla) {
		return escala(c, 1 + 0.07 * ruido(x, y >> 1, semilla));
	}

	/** Ruido fijo de -1 a 1 para cada píxel. */
	private static double ruido(int x, int y, int semilla) {
		int h = x * 374761393 + y * 668265263 + semilla * 1274126177;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return ((h & 1023) / 511.5) - 1;
	}

	private static int escala(int c, double f) {
		int r = (int) Math.min(255, Math.max(0, (c >> 16 & 255) * f));
		int g = (int) Math.min(255, Math.max(0, (c >> 8 & 255) * f));
		int b = (int) Math.min(255, Math.max(0, (c & 255) * f));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	static int oscuro(int c, double f) {
		return escala(c, f);
	}

	private static int aclara(int c) {
		int r = Math.min(255, (c >> 16 & 255) + 22);
		int g = Math.min(255, (c >> 8 & 255) + 22);
		int b = Math.min(255, (c & 255) + 22);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private static int mezcla(int a, int b, double t) {
		int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
		int g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}
}
