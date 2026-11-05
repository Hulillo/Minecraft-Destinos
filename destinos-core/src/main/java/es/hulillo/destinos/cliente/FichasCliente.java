package es.hulillo.destinos.cliente;

import es.hulillo.destinos.Geo;
import es.hulillo.destinos.PaqueteFichas;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
	/** Los ducados con algún bicho raro, calculado al llegar las fichas (el mapa los mira en cada frame). */
	private static final Set<Long> CON_RAROS = new HashSet<>();
	/** Sube cada vez que llegan fichas, y "cambios" solo si hay ducados nuevos o ha pasado un turno. */
	private static int version;
	private static int cambios;
	private static int ducadosAntes = -1;

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
		if (p.turno() != turno || p.ducados().size() != ducadosAntes) {
			cambios++;
		}
		ducadosAntes = p.ducados().size();
		anio = p.anio();
		turno = p.turno();
		vivos = p.reinos().size();
		CON_RAROS.clear();
		for (PaqueteFichas.Nombre d : p.ducados()) {
			for (Component a : d.animales()) {
				if (raro(a)) {
					CON_RAROS.add(Geo.clave(d.dcx(), d.dcz()));
					break;
				}
			}
		}
		version++;
	}

	/** Sube cada vez que llegan fichas (para rehacer lo que se guarda a partir de ellas). */
	static int version() {
		return version;
	}

	/** Sube solo si hay ducados nuevos o un turno nuevo (para repintar los modos que dependen de las fichas). */
	static int cambios() {
		return cambios;
	}

	static void borra() {
		REINOS.clear();
		DUCADOS.clear();
		ANIMALES.clear();
		CON_RAROS.clear();
		ducadosAntes = -1;
		version++;
		cambios++;
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
		return CON_RAROS.contains(Geo.clave(dcx, dcz));
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
