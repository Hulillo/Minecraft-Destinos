package es.hulillo.destinos;

/**
 * Lo que da una casilla de terreno: comida (0-4, llanuras y ríos), mineral (0-3, montañas y badlands) y madera
 * (0-3, bosques). Va en un byte: comida en los bits 0-2, mineral en 3-4 y madera en 5-6.
 */
final class Recursos {
	// Familias de MuestreadorMundo.FAMILIAS (+1): 1 océano, 2 río, 3 nieve, 4 badlands, 5 desierto, 6 selva,
	// 7 sabana, 8 pantano, 9 taiga, 10 montaña, 11 bosque, 12 llanura; 0 otra cosa
	private static final int[] COMIDA = {2, 0, 3, 0, 0, 0, 2, 2, 2, 1, 0, 2, 3};
	private static final int[] MADERA = {1, 0, 0, 0, 0, 0, 3, 1, 2, 3, 0, 3, 0};

	private Recursos() {
	}

	static byte de(MapaTerreno.Casilla c) {
		if (c.oceano()) {
			return 0;
		}
		int f = c.familia() >= 0 && c.familia() < COMIDA.length ? c.familia() : 0;
		int comida = COMIDA[f] + (c.rio() ? 2 : 0);
		int mineral = f == 4 ? 3 : f == 3 || f == 5 ? 1 : 0;
		if (c.montana()) {
			comida--;
			mineral = 3;
		}
		comida = Math.max(0, Math.min(4, comida));
		return (byte) (comida | mineral << 3 | MADERA[f] << 5);
	}

	static int comida(int recurso) {
		return recurso & 7;
	}

	static int mineral(int recurso) {
		return recurso >> 3 & 3;
	}

	static int madera(int recurso) {
		return recurso >> 5 & 3;
	}

	static int total(int recurso) {
		return comida(recurso) + mineral(recurso) + madera(recurso);
	}
}
