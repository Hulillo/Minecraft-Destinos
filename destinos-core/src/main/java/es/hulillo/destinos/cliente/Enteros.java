package es.hulillo.destinos.cliente;

import java.util.Arrays;

/** Una lista de int que crece sola, sin cajas de Integer (para las tiras del mapa y las colas de búsqueda). */
final class Enteros {
	private int[] v = new int[64];
	private int n;

	void add(int a) {
		if (n == v.length) {
			v = Arrays.copyOf(v, n * 2);
		}
		v[n++] = a;
	}

	void add(int a, int b, int c, int d) {
		add(a);
		add(b);
		add(c);
		add(d);
	}

	int get(int i) {
		return v[i];
	}

	int size() {
		return n;
	}

	int[] toArray() {
		return Arrays.copyOf(v, n);
	}
}
