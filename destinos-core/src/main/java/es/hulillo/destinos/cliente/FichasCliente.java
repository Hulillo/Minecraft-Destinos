package es.hulillo.destinos.cliente;

import es.hulillo.destinos.Geo;
import es.hulillo.destinos.PaqueteFichas;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;

/** Las fichas del mapa político que ha mandado el servidor: reinos y nombres de ducados. Solo desde el hilo del cliente. */
public final class FichasCliente {
	private static final Map<Long, PaqueteFichas.Reino> REINOS = new HashMap<>();
	private static final Map<Long, Component> DUCADOS = new HashMap<>();
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
		for (PaqueteFichas.Nombre d : p.ducados()) {
			DUCADOS.put(Geo.clave(d.dcx(), d.dcz()), d.nombre());
		}
		anio = p.anio();
		turno = p.turno();
		vivos = p.reinos().size();
	}

	static void borra() {
		REINOS.clear();
		DUCADOS.clear();
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
		return DUCADOS.get(Geo.clave(dcx, dcz));
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
