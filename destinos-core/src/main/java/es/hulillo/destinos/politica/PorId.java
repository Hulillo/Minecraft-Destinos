package es.hulillo.destinos.politica;

import es.hulillo.destinos.Geo;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

/**
 * Un HashMap por id de ducado o de reino (Geo.clave). Long.hashCode de esos ids es x ^ z, que se repite
 * muchísimo en una rejilla (todos los de la misma diagonal chocan), así que la clave se mezcla antes
 * (Geo.mezcla). No se recorre.
 */
final class PorId<V> {
	private final HashMap<Long, V> mapa = new HashMap<>();

	V get(long id) {
		return mapa.get(Geo.mezcla(id));
	}

	V getOrDefault(long id, V porDefecto) {
		return mapa.getOrDefault(Geo.mezcla(id), porDefecto);
	}

	boolean containsKey(long id) {
		return mapa.containsKey(Geo.mezcla(id));
	}

	V put(long id, V v) {
		return mapa.put(Geo.mezcla(id), v);
	}

	V putIfAbsent(long id, V v) {
		return mapa.putIfAbsent(Geo.mezcla(id), v);
	}

	V computeIfAbsent(long id, LongFunction<V> nuevo) {
		return mapa.computeIfAbsent(Geo.mezcla(id), k -> nuevo.apply(id));
	}

	V merge(long id, V v, BiFunction<? super V, ? super V, ? extends V> junta) {
		return mapa.merge(Geo.mezcla(id), v, junta);
	}

	void clear() {
		mapa.clear();
	}
}
