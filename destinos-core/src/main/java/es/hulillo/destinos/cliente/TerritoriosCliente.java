package es.hulillo.destinos.cliente;

import es.hulillo.destinos.MapaTerreno;
import es.hulillo.destinos.PaqueteTerritorio;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lo que el cliente sabe del mapa político: las zonas que le ha mandado el servidor (cerca de donde ha estado
 * el jugador en esta conexión). Solo se usa desde el hilo del cliente. Las casillas son de 64×64 bloques.
 */
public final class TerritoriosCliente {
	/** Quien pinta los territorios en un mapa y quiere enterarse de los cambios. */
	public interface Oyente {
		void zonaNueva(PaqueteTerritorio zona);

		void borrado();
	}

	public static final int SIN_ALTURA = Integer.MIN_VALUE;
	private static final Map<Long, PaqueteTerritorio> ZONAS = new HashMap<>();
	private static final List<Oyente> OYENTES = new CopyOnWriteArrayList<>();
	private static int sal;
	/** La última zona consultada: al pintar se piden muchas casillas seguidas de la misma. */
	private static PaqueteTerritorio ultima;

	private TerritoriosCliente() {
	}

	public static void escucha(Oyente oyente) {
		OYENTES.add(oyente);
		ZONAS.values().forEach(oyente::zonaNueva);
	}

	static void recibe(PaqueteTerritorio zona) {
		if (!ZONAS.isEmpty() && zona.sal() != sal) {
			borra();
		}
		sal = zona.sal();
		ZONAS.put(MapaTerreno.clave(zona.wx(), zona.wz()), zona);
		OYENTES.forEach(o -> o.zonaNueva(zona));
	}

	static void borra() {
		ZONAS.clear();
		ultima = null;
		OYENTES.forEach(Oyente::borrado);
	}

	public static boolean vacio() {
		return ZONAS.isEmpty();
	}

	/** Ducado de la casilla (tx, tz), o null si el servidor aún no la ha mandado. */
	public static PaqueteTerritorio.Ducado ducado(int tx, int tz) {
		PaqueteTerritorio z = zona(tx, tz);
		return z == null ? null : z.ducado(tx - z.wx() * MapaTerreno.NUCLEO, tz - z.wz() * MapaTerreno.NUCLEO);
	}

	/** Altura aproximada del suelo en la casilla (tx, tz), o SIN_ALTURA. */
	public static int altura(int tx, int tz) {
		PaqueteTerritorio z = zona(tx, tz);
		return z == null ? SIN_ALTURA : z.altura(tx - z.wx() * MapaTerreno.NUCLEO, tz - z.wz() * MapaTerreno.NUCLEO);
	}

	private static PaqueteTerritorio zona(int tx, int tz) {
		int wx = Math.floorDiv(tx, MapaTerreno.NUCLEO);
		int wz = Math.floorDiv(tz, MapaTerreno.NUCLEO);
		PaqueteTerritorio z = ultima;
		if (z == null || z.wx() != wx || z.wz() != wz) {
			z = ZONAS.get(MapaTerreno.clave(wx, wz));
			if (z != null) {
				ultima = z;
			}
		}
		return z;
	}
}
