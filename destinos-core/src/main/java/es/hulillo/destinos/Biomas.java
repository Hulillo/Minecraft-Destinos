package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Los biomas que se han visto al leer el terreno, numerados por orden de llegada (para guardar el de cada casilla en
 * pocos bits). Solo vive en memoria: los números no se guardan, se mandan como nombre ("minecraft:taiga").
 */
public final class Biomas {
	/** Cabe en los 9 bits de Casilla.empaqueta (el 0 es "no se sabe"). */
	public static final int MAXIMO = 510;
	private static final Map<String, Integer> NUMEROS = new HashMap<>();
	private static final List<String> NOMBRES = new ArrayList<>();

	private Biomas() {
	}

	/** Número del bioma, o -1 si ya hay demasiados. */
	public static synchronized int numero(String nombre) {
		Integer n = NUMEROS.get(nombre);
		if (n != null) {
			return n;
		}
		if (NOMBRES.size() >= MAXIMO) {
			return -1;
		}
		NUMEROS.put(nombre, NOMBRES.size());
		NOMBRES.add(nombre);
		return NOMBRES.size() - 1;
	}

	/** Nombre del bioma número n, o "" si no se sabe. */
	public static synchronized String nombre(int n) {
		return n >= 0 && n < NOMBRES.size() ? NOMBRES.get(n) : "";
	}
}
