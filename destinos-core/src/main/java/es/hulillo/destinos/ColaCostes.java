package es.hulillo.destinos;

import java.util.Arrays;

/**
 * Cola de prioridad del Dijkstra de MapaTerreno sin un objeto por entrada (eran decenas de miles por zona, basura
 * para el recolector): montículo en arrays, ordenado por coste, luego semilla y luego casilla.
 */
final class ColaCostes {
	private double[] coste;
	private int[] casilla;
	private int[] semilla;
	private int n;

	ColaCostes(int capacidad) {
		coste = new double[capacidad];
		casilla = new int[capacidad];
		semilla = new int[capacidad];
	}

	boolean vacia() {
		return n == 0;
	}

	/** Coste, casilla y semilla de la primera (la de menor coste). */
	double coste() {
		return coste[0];
	}

	int casilla() {
		return casilla[0];
	}

	int semilla() {
		return semilla[0];
	}

	void mete(double c, int k, int s) {
		if (n == coste.length) {
			coste = Arrays.copyOf(coste, n * 2);
			casilla = Arrays.copyOf(casilla, n * 2);
			semilla = Arrays.copyOf(semilla, n * 2);
		}
		coste[n] = c;
		casilla[n] = k;
		semilla[n] = s;
		int i = n++;
		while (i > 0 && antes(i, (i - 1) / 2)) {
			cambia(i, (i - 1) / 2);
			i = (i - 1) / 2;
		}
	}

	/** Quita la primera. */
	void saca() {
		cambia(0, --n);
		int i = 0;
		while (true) {
			int a = 2 * i + 1;
			int b = a + 1;
			int m = i;
			if (a < n && antes(a, m)) {
				m = a;
			}
			if (b < n && antes(b, m)) {
				m = b;
			}
			if (m == i) {
				return;
			}
			cambia(i, m);
			i = m;
		}
	}

	private boolean antes(int a, int b) {
		int c = Double.compare(coste[a], coste[b]);
		if (c != 0) {
			return c < 0;
		}
		if (semilla[a] != semilla[b]) {
			return semilla[a] < semilla[b];
		}
		return casilla[a] < casilla[b];
	}

	private void cambia(int a, int b) {
		double c = coste[a];
		coste[a] = coste[b];
		coste[b] = c;
		int t = casilla[a];
		casilla[a] = casilla[b];
		casilla[b] = t;
		t = semilla[a];
		semilla[a] = semilla[b];
		semilla[b] = t;
	}
}
