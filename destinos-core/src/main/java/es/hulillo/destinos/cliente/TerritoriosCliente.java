package es.hulillo.destinos.cliente;

import es.hulillo.destinos.DestinosCore;
import es.hulillo.destinos.MapaTerreno;
import es.hulillo.destinos.PaquetePolitica;
import es.hulillo.destinos.PaqueteTerritorio;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lo que el cliente sabe del mapa político: las zonas que le ha mandado el servidor (cerca de donde ha estado
 * el jugador en esta conexión). Solo se usa desde el hilo del cliente. Las casillas son de 64×64 bloques.
 */
public final class TerritoriosCliente {
	/** Quien pinta los territorios en un mapa y quiere enterarse de los cambios. */
	public interface Oyente {
		/** anterior: lo que había antes en esa zona (el servidor la reenvía si cambia de dueño), o null. */
		void zonaNueva(PaqueteTerritorio zona, PaqueteTerritorio anterior);

		void borrado();

		/** Ha llegado un estado político nuevo (turno, guerras). */
		default void politica() {
		}

		/** Se ha encendido o apagado el mapa político. */
		default void visibilidad(boolean visible) {
		}
	}

	public static final int SIN_ALTURA = Integer.MIN_VALUE;
	private static final Map<Long, PaqueteTerritorio> ZONAS = new HashMap<>();
	private static final List<Oyente> OYENTES = new CopyOnWriteArrayList<>();
	private static int sal;
	/** Qué hizo el último mapa al pintar, para /destinosmapa. Lo rellenan las capas de cada mapa. */
	public static volatile String ultimoPintado = "ningún mapa ha intentado pintar todavía";
	/** La última zona consultada: al pintar se piden muchas casillas seguidas de la misma. */
	private static PaqueteTerritorio ultima;
	/** Turno político de ahora y guerras abiertas (pares de reinos), del último PaquetePolitica. */
	private static int turno;
	private static final Set<Long> GUERRAS = new HashSet<>();
	/** Reinos que están en alguna guerra. */
	private static final Set<Long> EN_GUERRA = new HashSet<>();
	/** Capitales de los reinos conocidos, para poner su corona en los mapas. */
	private static List<PaquetePolitica.Capital> capitales = List.of();
	/** Si se pintan los reinos en los mapas (la tecla del mapa político lo cambia). */
	private static boolean visible = Ajustes.leeVisible();

	private TerritoriosCliente() {
	}

	public static void escucha(Oyente oyente) {
		OYENTES.add(oyente);
		ZONAS.values().forEach(z -> oyente.zonaNueva(z, null));
	}

	static void recibe(PaqueteTerritorio zona) {
		if (!ZONAS.isEmpty() && zona.sal() != sal) {
			borra();
		}
		if (ZONAS.isEmpty()) {
			DestinosCore.LOG.info("Destinos Core: llegan los territorios del servidor para pintarlos en el mapa");
		}
		sal = zona.sal();
		PaqueteTerritorio anterior = ZONAS.put(MapaTerreno.clave(zona.wx(), zona.wz()), zona);
		ultima = null;
		OYENTES.forEach(o -> o.zonaNueva(zona, anterior));
	}

	static void politica(PaquetePolitica p) {
		turno = p.turno();
		capitales = List.copyOf(p.capitales());
		GUERRAS.clear();
		EN_GUERRA.clear();
		for (PaquetePolitica.Guerra g : p.guerras()) {
			GUERRAS.add(par(g.akx(), g.akz(), g.bkx(), g.bkz()));
			EN_GUERRA.add(MapaTerreno.clave(g.akx(), g.akz()));
			EN_GUERRA.add(MapaTerreno.clave(g.bkx(), g.bkz()));
		}
		OYENTES.forEach(Oyente::politica);
	}

	/** Clave de un par de reinos sin orden. */
	private static long par(int akx, int akz, int bkx, int bkz) {
		long a = MapaTerreno.clave(akx, akz);
		long b = MapaTerreno.clave(bkx, bkz);
		long lo = Math.min(a, b);
		long hi = Math.max(a, b);
		return lo * 0x9E3779B97F4A7C15L ^ hi;
	}

	/** Si el ducado cambió de dueño hace poco (se marca en dorado). */
	public static boolean reciente(PaqueteTerritorio.Ducado d) {
		return d.cambio() >= 0 && turno - d.cambio() < PaquetePolitica.TURNOS_RECIENTE;
	}

	public static List<PaquetePolitica.Capital> capitales() {
		return capitales;
	}

	/** Si hay alguna guerra abierta: sin guerras no hace falta buscar frentes. */
	public static boolean hayGuerras() {
		return !GUERRAS.isEmpty();
	}

	/** Si los reinos de estos dos ducados están en guerra entre sí. */
	public static boolean enGuerra(PaqueteTerritorio.Ducado a, PaqueteTerritorio.Ducado b) {
		if (GUERRAS.isEmpty() || b == null || a.salvaje() || b.salvaje() || a.mismoReino(b)) {
			return false;
		}
		return GUERRAS.contains(par(a.kx(), a.kz(), b.kx(), b.kz()));
	}

	/** Si el reino del ducado está en alguna guerra. */
	public static boolean enGuerra(PaqueteTerritorio.Ducado d) {
		return !d.salvaje() && EN_GUERRA.contains(MapaTerreno.clave(d.kx(), d.kz()));
	}

	static void borra() {
		ZONAS.clear();
		GUERRAS.clear();
		EN_GUERRA.clear();
		capitales = List.of();
		turno = 0;
		ultima = null;
		OYENTES.forEach(Oyente::borrado);
	}

	public static boolean visible() {
		return visible;
	}

	/** Enciende o apaga el mapa político en todos los mapas y lo recuerda para la próxima vez. */
	public static void alterna() {
		visible = !visible;
		Ajustes.guardaVisible(visible);
		OYENTES.forEach(o -> o.visibilidad(visible));
	}

	/** Todas las zonas recibidas (para recorrer los frentes de guerra). */
	public static Collection<PaqueteTerritorio> zonasRecibidas() {
		return ZONAS.values();
	}

	public static int zonas() {
		return ZONAS.size();
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
