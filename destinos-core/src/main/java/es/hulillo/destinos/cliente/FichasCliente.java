package es.hulillo.destinos.cliente;

import es.hulillo.destinos.Geo;
import es.hulillo.destinos.PaqueteFichas;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;

/** Las fichas del mapa político que ha mandado el servidor: reinos y nombres de ducados. Solo desde el hilo del cliente. */
public final class FichasCliente {
	private static final Map<Long, PaqueteFichas.Reino> REINOS = new HashMap<>();
	private static final Map<Long, PaqueteFichas.Nombre> DUCADOS = new HashMap<>();
	/** Cuántos ducados conocidos tiene cada animal (por su nombre traducido), para ver cuáles son raros. */
	private static final Map<String, Integer> ANIMALES = new HashMap<>();
	private static int anio;
	private static int turno = -1;
	private static int vivos;

	private FichasCliente() {
	}

	static void recibe(PaqueteFichas p) {
		REINOS.clear();
		DUCADOS.clear();
		for (PaqueteFichas.Reino r : p.reinos()) {
			REINOS.put(Geo.clave(r.kx(), r.kz()), r);
		}
		ANIMALES.clear();
		for (PaqueteFichas.Nombre d : p.ducados()) {
			DUCADOS.put(Geo.clave(d.dcx(), d.dcz()), d);
			for (Component a : d.animales()) {
				ANIMALES.merge(a.getString(), 1, Integer::sum);
			}
		}
		anio = p.anio();
		turno = p.turno();
		vivos = p.reinos().size();
	}

	static void borra() {
		REINOS.clear();
		DUCADOS.clear();
		ANIMALES.clear();
		turno = -1;
		vivos = 0;
	}

	/** Si ya han llegado fichas en esta conexión. */
	static boolean hay() {
		return turno >= 0;
	}

	static PaqueteFichas.Reino reino(int kx, int kz) {
		return REINOS.get(Geo.clave(kx, kz));
	}

	/** Nombre de un reino ("Reino de X" con su color) o null si no hay ficha. */
	static Component nombreReino(int kx, int kz) {
		PaqueteFichas.Reino r = reino(kx, kz);
		return r == null ? null : r.nombre();
	}

	static Component ducado(int dcx, int dcz) {
		PaqueteFichas.Nombre d = DUCADOS.get(Geo.clave(dcx, dcz));
		return d == null ? null : d.nombre();
	}

	/** Ficha de un ducado (nombre, peligro, fauna) o null. */
	static PaqueteFichas.Nombre fichaDucado(int dcx, int dcz) {
		return DUCADOS.get(Geo.clave(dcx, dcz));
	}

	/** Peligro del ducado de 0 a 3, o -1 si no se sabe. */
	static int peligro(int dcx, int dcz) {
		PaqueteFichas.Nombre d = DUCADOS.get(Geo.clave(dcx, dcz));
		return d == null ? -1 : d.peligro();
	}

	/** Si el animal sale en pocos ducados de los conocidos (como mucho uno de cada diez, y al menos con 10 conocidos). */
	static boolean raro(Component animal) {
		int n = ANIMALES.getOrDefault(animal.getString(), 0);
		return DUCADOS.size() >= 10 && n * 10 <= DUCADOS.size();
	}

	/** Si el ducado tiene algún animal raro. */
	static boolean conRaros(int dcx, int dcz) {
		PaqueteFichas.Nombre d = DUCADOS.get(Geo.clave(dcx, dcz));
		if (d == null) {
			return false;
		}
		for (Component a : d.animales()) {
			if (raro(a)) {
				return true;
			}
		}
		return false;
	}

	static Iterable<PaqueteFichas.Nombre> ducados() {
		return DUCADOS.values();
	}

	static Iterable<PaqueteFichas.Reino> reinos() {
		return REINOS.values();
	}

	static int anio() {
		return anio;
	}

	static int turno() {
		return turno;
	}

	static int vivos() {
		return vivos;
	}
}
