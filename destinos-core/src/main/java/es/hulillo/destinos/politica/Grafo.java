package es.hulillo.destinos.politica;

import es.hulillo.destinos.MapaTerreno;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Los ducados que ya se conocen (los de las zonas calculadas) y quién toca a quién por tierra.
 * Crece según se exploran zonas nuevas; la política solo mueve fichas dentro de él.
 */
public final class Grafo {
	/** Un ducado del mapa base: su reino de origen y si nació como tierra salvaje. */
	public record Ducado(long id, int dcx, int dcz, long reinoBase, boolean salvajeBase) {
	}

	/** Frontera con un vecino: casillas que comparten y qué parte cruza ríos o montañas (0..1). */
	public record Lado(int casillas, double dificultad) {
	}

	private final Map<Long, Ducado> ducados = new TreeMap<>();
	private final Map<Long, Integer> casillas = new HashMap<>();
	/** comida, mineral, madera y pueblos de cada ducado (lo que se ha visto de él). */
	private final Map<Long, int[]> recursos = new HashMap<>();
	/** Capital de cada reino (por su ducado) y al revés. */
	private final Map<Long, Long> capitales = new HashMap<>();
	private final Map<Long, Long> capitalDe = new HashMap<>();
	private final Map<Long, Map<Long, int[]>> vecinos = new HashMap<>();
	private final Set<Long> ventanas = new HashSet<>();
	/** En qué ventanas sale cada ducado (para reenviar a los mapas solo las que cambian). */
	private final Map<Long, Set<Long>> ventanasDe = new HashMap<>();

	public static long id(int x, int z) {
		return MapaTerreno.clave(x, z);
	}

	public static int x(long id) {
		return (int) (id >> 32);
	}

	public static int z(long id) {
		return (int) id;
	}

	/** Si la ventana ya está metida (para no calcular su vecindario otra vez). */
	public boolean conoce(long ventana) {
		return ventanas.contains(ventana);
	}

	/** Añade lo que se ve en una ventana. Cada ventana cuenta una sola vez. */
	public boolean agrega(long ventana, MapaTerreno.Vecindario v) {
		if (!ventanas.add(ventana)) {
			return false;
		}
		for (MapaTerreno.DucadoVisto d : v.ducados()) {
			long id = id(d.dcx(), d.dcz());
			ducados.putIfAbsent(id, new Ducado(id, d.dcx(), d.dcz(), id(d.kx(), d.kz()), d.salvaje()));
			casillas.merge(id, d.casillas(), Integer::sum);
			int[] r = recursos.computeIfAbsent(id, k -> new int[4]);
			r[0] += d.comida();
			r[1] += d.mineral();
			r[2] += d.madera();
			r[3] += d.pueblos();
			ventanasDe.computeIfAbsent(id, k -> new HashSet<>()).add(ventana);
		}
		for (MapaTerreno.Frontera f : v.fronteras()) {
			long a = id(f.dcx1(), f.dcz1());
			long b = id(f.dcx2(), f.dcz2());
			suma(a, b, f);
			suma(b, a, f);
		}
		return true;
	}

	private void suma(long a, long b, MapaTerreno.Frontera f) {
		int[] lado = vecinos.computeIfAbsent(a, k -> new TreeMap<>()).computeIfAbsent(b, k -> new int[2]);
		lado[0] += f.casillas();
		lado[1] += f.dificiles();
	}

	/** Añade un ducado a mano (para las pruebas). */
	public void agrega(Ducado d, int tamano) {
		ducados.put(d.id(), d);
		casillas.merge(d.id(), tamano, Integer::sum);
	}

	/** Pone a mano lo que da un ducado (para las pruebas). */
	public void recursos(long id, int comida, int mineral, int madera, int pueblos) {
		recursos.put(id, new int[] {comida, mineral, madera, pueblos});
	}

	/** comida, mineral, madera y pueblos del ducado. No se debe modificar. */
	public int[] recursos(long id) {
		return recursos.getOrDefault(id, VACIO);
	}

	private static final int[] VACIO = new int[4];

	/** Apunta la capital de un reino (solo la primera vez: no cambia aunque se conquiste). */
	public boolean fijaCapital(long reino, long ducado) {
		if (capitales.putIfAbsent(reino, ducado) != null) {
			return false;
		}
		capitalDe.put(ducado, reino);
		return true;
	}

	public boolean tieneCapital(long reino) {
		return capitales.containsKey(reino);
	}

	/** Ducado de la capital del reino, o null si no se sabe. */
	public Long capital(long reino) {
		return capitales.get(reino);
	}

	/** Reino del que es capital el ducado, o null. */
	public Long capitalDe(long ducado) {
		return capitalDe.get(ducado);
	}

	/** Une dos ducados a mano (para las pruebas). */
	public void une(long a, long b, int casillasFrontera, int dificiles) {
		MapaTerreno.Frontera f = new MapaTerreno.Frontera(x(a), z(a), x(b), z(b), casillasFrontera, dificiles);
		suma(a, b, f);
		suma(b, a, f);
	}

	public Map<Long, Ducado> ducados() {
		return ducados;
	}

	public Ducado ducado(long id) {
		return ducados.get(id);
	}

	/** Ventanas donde sale el ducado. */
	public Set<Long> ventanasDe(long id) {
		return ventanasDe.getOrDefault(id, Set.of());
	}

	public int tamano(long id) {
		return casillas.getOrDefault(id, 0);
	}

	/** Vecinos conocidos de un ducado, en orden fijo. */
	public Map<Long, Lado> vecinos(long id) {
		Map<Long, Lado> res = new TreeMap<>();
		Map<Long, int[]> v = vecinos.get(id);
		if (v != null) {
			v.forEach((b, lado) -> {
				if (ducados.containsKey(b)) {
					res.put(b, new Lado(lado[0], lado[0] == 0 ? 0 : (double) lado[1] / lado[0]));
				}
			});
		}
		return res;
	}
}
