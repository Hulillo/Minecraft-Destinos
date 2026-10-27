package es.hulillo.destinos.politica;

import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

/**
 * Un HashMap por id de ducado o de reino (x en los 32 bits altos, z en los bajos). Long.hashCode de esos ids es
 * x ^ z, que se repite muchísimo en una rejilla (todos los de la misma diagonal chocan), así que la clave se
 * mezcla antes con una multiplicación por un impar: es biyectiva, y el reparto queda bueno. No se recorre.
 */
final class PorId<V> {
	private final HashMap<Long, V> mapa = new HashMap<>();

	private static long mezcla(long id) {
		return id * 0x9E3779B97F4A7C15L;
	}

	V get(long id) {
		return mapa.get(mezcla(id));
	}

	V getOrDefault(long id, V porDefecto) {
		return mapa.getOrDefault(mezcla(id), porDefecto);
	}

	boolean containsKey(long id) {
		return mapa.containsKey(mezcla(id));
	}

	V put(long id, V v) {
		return mapa.put(mezcla(id), v);
	}

	V putIfAbsent(long id, V v) {
		return mapa.putIfAbsent(mezcla(id), v);
	}

	V computeIfAbsent(long id, LongFunction<V> nuevo) {
		return mapa.computeIfAbsent(mezcla(id), k -> nuevo.apply(id));
	}

	V merge(long id, V v, BiFunction<? super V, ? super V, ? extends V> junta) {
		return mapa.merge(mezcla(id), v, junta);
	}

	void clear() {
		mapa.clear();
	}
}
