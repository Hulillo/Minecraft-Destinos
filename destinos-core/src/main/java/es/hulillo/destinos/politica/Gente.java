package es.hulillo.destinos.politica;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * La gente de los reinos (v0.7 Gente): reyes con su familia, duques, generales y consejeros. Cada turno envejecen,
 * mueren, se casan, tienen hijos, heredan (según la ley de su cultura), dan golpes y se ganan un mote. Los reinos y
 * ducados los pone la política; aquí solo se sigue quién manda en ellos. Sin Minecraft: los nombres los da una
 * Fuente (en el juego, las tablas del datapack).
 */
public final class Gente {
	/** Lo que se pide fuera: la cultura y la casa real de cada reino, y los nombres de cada cultura. */
	public interface Fuente {
		/** "norte", "llanura"… o "" si aún no se sabe. */
		String cultura(long reino);

		/** El apellido de la casa real del reino, o "" si aún no se sabe. */
		String casa(long reino);

		List<String> nombres(String cultura, boolean mujer);

		List<String> casas(String cultura);

		/** Para las pruebas sin Minecraft: nombres inventados. */
		Fuente SIMPLE = new Fuente() {
			@Override
			public String cultura(long reino) {
				return "";
			}

			@Override
			public String casa(long reino) {
				return "";
			}

			@Override
			public List<String> nombres(String cultura, boolean mujer) {
				return mujer ? List.of("Elvira", "Urraca", "Leonor", "Jimena", "Teresa", "Inés")
						: List.of("Rodrigo", "Sancho", "Pelayo", "Nuño", "Alvar", "Martín");
			}

			@Override
			public List<String> casas(String cultura) {
				return List.of("Castelar", "Valdés", "Quiroga", "Velasco", "Sarmiento", "Altamira");
			}
		};
	}

	/** Cómo se hereda la corona en cada cultura. */
	public enum Ley {
		/** El hijo mayor; si no hay hijos varones, la hija mayor. */
		varon,
		/** El primogénito, sea hijo o hija. */
		absoluta,
		/** El más capaz de la sangre real. */
		valia,
		/** Los nobles eligen al más capaz, sea de la sangre o no. */
		electiva;

		public static Ley de(String cultura) {
			return switch (cultura) {
				case "llanura", "bambu", "cerezo" -> varon;
				case "costa", "islas" -> absoluta;
				case "norte", "estepa" -> electiva;
				default -> valia;
			};
		}
	}

	/** Un reinado, para el linaje del reino. hasta: año en que acabó, 0 si sigue. */
	public record Reinado(long reino, int persona, int desde, int hasta) {
	}

	/** Mayoría de edad. */
	public static final int ADULTO = 16;
	/** Por encima de tantas personas se olvidan los muertos que ya no nombra nadie. */
	private static final int MAX_PERSONAS = 3000;

	private final TreeMap<Integer, Persona> personas = new TreeMap<>();
	private int siguiente = 1;
	/** Reino → su rey (un rey puede serlo de dos reinos unidos por herencia). */
	private final TreeMap<Long, Integer> reyes = new TreeMap<>();
	/** Ducado → su duque (la capital no tiene: la gobierna el rey). */
	private final TreeMap<Long, Integer> duques = new TreeMap<>();
	private final TreeMap<Long, Integer> generales = new TreeMap<>();
	private final TreeMap<Long, Integer> consejeros = new TreeMap<>();
	private final List<Reinado> reinados = new ArrayList<>();
	/** Ducados cuyo duque ha cambiado (para que el juego cambie el título al entrar); se vacía fuera. */
	private final Set<Long> ducadosCambiados = new HashSet<>();

	// ------------------------------------------------------------------ consultas

	public Map<Integer, Persona> personas() {
		return personas;
	}

	public Persona persona(int id) {
		return personas.get(id);
	}

	public Map<Long, Integer> reyes() {
		return reyes;
	}

	public Map<Long, Integer> duques() {
		return duques;
	}

	public Map<Long, Integer> generales() {
		return generales;
	}

	public Map<Long, Integer> consejeros() {
		return consejeros;
	}

	public List<Reinado> reinados() {
		return reinados;
	}

	public Set<Long> ducadosCambiados() {
		return ducadosCambiados;
	}

	public int siguiente() {
		return siguiente;
	}

	public void fijaSiguiente(int n) {
		siguiente = Math.max(siguiente, n);
	}

	public Persona rey(long reino) {
		Integer id = reyes.get(reino);
		return id == null ? null : personas.get(id);
	}

	public Persona duque(long ducado) {
		Integer id = duques.get(ducado);
		return id == null ? null : personas.get(id);
	}

	public Persona general(long reino) {
		Integer id = generales.get(reino);
		return id == null ? null : personas.get(id);
	}

	public Persona consejero(long reino) {
		Integer id = consejeros.get(reino);
		return id == null ? null : personas.get(id);
	}

	public Persona conyuge(Persona p) {
		Persona c = p.conyuge == Persona.NADIE ? null : personas.get(p.conyuge);
		return c != null && c.vivo() ? c : null;
	}

	/** Hijos de cada persona (ids), para no recorrer a todo el mundo cada vez; null si hay que rehacerlo. */
	private Map<Integer, List<Integer>> indiceHijos;

	/** Hijos de la persona (vivos y muertos), del mayor al pequeño. */
	public List<Persona> hijos(Persona p) {
		if (indiceHijos == null) {
			indiceHijos = new java.util.HashMap<>();
			for (Persona h : personas.values()) {
				for (int padre : new int[] {h.padre, h.madre}) {
					if (padre != Persona.NADIE) {
						indiceHijos.computeIfAbsent(padre, k -> new ArrayList<>()).add(h.id);
					}
				}
			}
		}
		List<Persona> res = new ArrayList<>();
		for (int id : indiceHijos.getOrDefault(p.id, List.of())) {
			Persona h = personas.get(id);
			if (h != null) {
				res.add(h);
			}
		}
		res.sort(Comparator.comparingInt((Persona h) -> h.nace).thenComparingInt(h -> h.id));
		return res;
	}

	private void padres(Persona h, int padre, int madre) {
		h.padre = padre;
		h.madre = madre;
		indiceHijos = null;
	}

	/** Los reinados del reino, del primero al último. */
	public List<Reinado> linaje(long reino) {
		List<Reinado> res = new ArrayList<>();
		for (Reinado r : reinados) {
			if (r.reino() == reino) {
				res.add(r);
			}
		}
		return res;
	}

	/** Quién heredaría hoy la corona del reino según su ley, o null si nadie (crisis sucesoria). */
	public Persona heredero(Politica p, long reino) {
		Persona rey = rey(reino);
		return rey == null ? null : sucesor(p, reino, rey);
	}

	// ------------------------------------------------------------------ lo que la gente pesa en la política

	/** Cuánto más (o menos) declara guerras el reino por el carácter de su rey. */
	public double belicosidad(long reino) {
		Persona r = rey(reino);
		if (r == null) {
			return 1;
		}
		double m = 1;
		for (Persona.Rasgo x : new Persona.Rasgo[] {r.rasgo1, r.rasgo2}) {
			m *= switch (x) {
				case ambicioso -> 1.6;
				case cruel -> 1.3;
				case valiente -> 1.2;
				case piadoso -> 0.6;
				case justo, humilde -> 0.8;
				case cobarde -> 0.5;
				default -> 1;
			};
		}
		return m;
	}

	/** Lo que el carácter del rey empuja hacia la paz en cada turno de guerra (puede ser negativo). */
	public double pacifismo(long reino) {
		Persona r = rey(reino);
		if (r == null) {
			return 0;
		}
		double m = 0;
		for (Persona.Rasgo x : new Persona.Rasgo[] {r.rasgo1, r.rasgo2}) {
			m += switch (x) {
				case piadoso, cobarde -> 0.05;
				case justo, humilde -> 0.03;
				case ambicioso, cruel -> -0.03;
				default -> 0;
			};
		}
		return m;
	}

	// ------------------------------------------------------------------ el turno

	/**
	 * Un turno de la gente, tras el de la política: ducadosDe son los reinos vivos con sus ducados de hoy y
	 * esteTurno, lo que ha pasado en la política este turno (conquistas, anexiones…).
	 */
	void turno(Politica p, Grafo grafo, Map<Long, List<Long>> ducadosDe, Fuente f, SplittableRandom rnd, List<Suceso> esteTurno,
			Consumer<Suceso> anota) {
		int anio = p.anio();
		reinosCaidos(p, grafo, ducadosDe, esteTurno, anio);
		ducados(p, grafo, rnd, anota, anio);
		nacimientos(p, f, rnd, anota, anio);
		bodas(p, ducadosDe, f, rnd, anota, anio);
		muertes(p, rnd, anota, anio);
		// tras todas las muertes del turno (también las de parto), para que ningún reino se quede sin rey
		sucesiones(p, grafo, ducadosDe, f, rnd, anota, anio);
		cargos(p, grafo, ducadosDe, f, rnd, anio);
		golpes(p, grafo, ducadosDe, rnd, anota, anio);
		lealtades(p, rnd);
		fama(p, esteTurno, anota, anio);
		olvida(p);
	}

	/** Reinos que ya no tienen tierras: su rey se va al exilio (o, si se los anexiona otro, queda de duque). */
	private void reinosCaidos(Politica p, Grafo grafo, Map<Long, List<Long>> ducadosDe, List<Suceso> esteTurno, int anio) {
		for (long reino : new ArrayList<>(reyes.keySet())) {
			if (ducadosDe.containsKey(reino)) {
				continue;
			}
			Persona rey = rey(reino);
			reyes.remove(reino);
			cierraReinado(reino, anio);
			for (TreeMap<Long, Integer> cargo : List.of(generales, consejeros)) {
				Integer id = cargo.remove(reino);
				if (id != null && personas.get(id) != null) {
					personas.get(id).rol = Persona.Rol.sin_cargo;
				}
			}
			if (rey == null || !rey.vivo() || reyes.containsValue(rey.id)) {
				continue;
			}
			rey.rol = Persona.Rol.sin_cargo;
			// anexionado por su señor: el rey se queda con su antigua capital, de duque del nuevo reino
			for (Suceso s : esteTurno) {
				if (s.tipo() == Suceso.Tipo.anexion && s.reinoB() == reino) {
					Long capital = grafo.capital(reino);
					Grafo.Ducado d = capital == null ? null : grafo.ducado(capital);
					if (d != null && p.dueno(d) == s.reinoA()) {
						ponDuque(capital, rey, s.reinoA());
						rey.lealtad = 45;
					}
				}
			}
		}
	}

	/** Los ducados que han cambiado de dueño: el duque viejo cae o huye; el nuevo lo pone el siguiente paso. */
	private void ducados(Politica p, Grafo grafo, SplittableRandom rnd, Consumer<Suceso> anota, int anio) {
		for (Map.Entry<Long, Integer> en : new ArrayList<>(duques.entrySet())) {
			long ducado = en.getKey();
			Persona d = personas.get(en.getValue());
			Grafo.Ducado g = grafo.ducado(ducado);
			if (d == null || !d.vivo() || g == null) {
				quitaDuque(ducado);
				continue;
			}
			long dueno = p.dueno(g);
			Long capital = dueno == Politica.SALVAJE ? null : grafo.capital(dueno);
			if (d.reino == dueno && (capital == null || capital != ducado)) {
				continue;
			}
			quitaDuque(ducado);
			Politica.Cambio c = p.cambios().get(ducado);
			boolean guerra = c != null && c.turno() == p.turno() && c.lealtad() == Politica.LEALTAD_GUERRA;
			if (guerra && rnd.nextDouble() < 0.5) {
				mata(d, Persona.Muerte.guerra, anio);
				anota.accept(new Suceso(Suceso.Tipo.caido, anio, d.reino, dueno, ducado, null, d.id, Persona.NADIE));
			} else if (dueno != Politica.SALVAJE && !guerra && (capital == null || capital != ducado)) {
				// pasa a otro reino sin guerra (anexión): el duque jura al nuevo rey
				ponDuque(ducado, d, dueno);
				d.lealtad = 40;
			}
		}
	}

	/** Muertes de viejo, de enfermedad y en la guerra. */
	private void muertes(Politica p, SplittableRandom rnd, Consumer<Suceso> anota, int anio) {
		for (Persona x : new ArrayList<>(personas.values())) {
			if (!x.vivo()) {
				continue;
			}
			int edad = x.edad(anio);
			double prob = edad < 1 ? 0.002 : edad < 40 ? 0.0004 : edad < 55 ? 0.001 : edad < 65 ? 0.003 : edad < 75 ? 0.007 : 0.02;
			prob *= 96.0 / Math.max(8, p.turnosPorAnio());
			boolean enGuerra = (x.rol == Persona.Rol.general || x.rol == Persona.Rol.rey || x.rol == Persona.Rol.duque)
					&& p.enGuerra(x.reino);
			double guerra = !enGuerra ? 0 : x.rol == Persona.Rol.general ? 0.012 : x.rol == Persona.Rol.rey && x.tiene(Persona.Rasgo.valiente) ? 0.004
					: x.rol == Persona.Rol.duque ? 0.001 : 0;
			double t = rnd.nextDouble();
			if (t < guerra) {
				mata(x, Persona.Muerte.guerra, anio);
				anota.accept(new Suceso(Suceso.Tipo.caido, anio, x.reino, Politica.NADIE, x.rol == Persona.Rol.duque ? x.ducado : Politica.NADIE,
						null, x.id, Persona.NADIE));
			} else if (t < guerra + prob) {
				mata(x, edad >= 65 ? Persona.Muerte.vejez : Persona.Muerte.enfermedad, anio);
				if (x.rol == Persona.Rol.rey) {
					anota.accept(new Suceso(Suceso.Tipo.muerte, anio, x.reino, Politica.NADIE, Politica.NADIE, null, x.id, Persona.NADIE));
				}
			}
		}
	}

	/** Reinos sin rey vivo: hereda quien diga su ley, o hay crisis; los reinos nuevos estrenan familia real. */
	private void sucesiones(Politica p, Grafo grafo, Map<Long, List<Long>> ducadosDe, Fuente f, SplittableRandom rnd,
			Consumer<Suceso> anota, int anio) {
		for (long reino : ducadosDe.keySet()) {
			Persona rey = rey(reino);
			if (rey == null && !haReinado(reino)) {
				familiaReal(reino, f, rnd, anio);
				continue;
			}
			if (rey != null && rey.vivo()) {
				continue;
			}
			Persona nuevo = rey == null ? null : sucesor(p, reino, rey);
			if (nuevo != null) {
				Long otro = reinoDe(nuevo);
				corona(nuevo, reino, anio);
				if (otro != null && otro != reino) {
					// ya era rey de otro reino: los dos quedan unidos bajo la misma corona
					p.unePorHerencia(reino, otro);
					anota.accept(new Suceso(Suceso.Tipo.herencia, anio, reino, otro, Politica.NADIE, null, nuevo.id,
							rey == null ? Persona.NADIE : rey.id));
				} else {
					anota.accept(new Suceso(Suceso.Tipo.sucesion, anio, reino, Politica.NADIE, Politica.NADIE, null, nuevo.id,
							rey == null ? Persona.NADIE : rey.id));
				}
				continue;
			}
			// sin heredero: el noble más capaz del reino se hace con la corona, y el reino se tambalea
			Persona noble = null;
			for (Persona d : nobles(reino)) {
				if (noble == null || d.valia() > noble.valia()) {
					noble = d;
				}
			}
			if (noble == null) {
				noble = nueva(f, reino, cultura(f, reino), rnd.nextDouble() < 0.3, 25 + rnd.nextInt(30), Persona.Rol.sin_cargo,
						casaNueva(f, cultura(f, reino), rnd), rnd, anio);
			}
			liberaCargo(noble);
			corona(noble, reino, anio);
			Politica.Reino e = p.reinos().get(reino);
			if (e != null) {
				e.estabilidad = Math.max(0, e.estabilidad - 20);
			}
			anota.accept(new Suceso(Suceso.Tipo.crisis, anio, reino, Politica.NADIE, Politica.NADIE, null, noble.id,
					rey == null ? Persona.NADIE : rey.id));
		}
	}

	private boolean haReinado(long reino) {
		for (Reinado r : reinados) {
			if (r.reino() == reino) {
				return true;
			}
		}
		return false;
	}

	/** El reino del que la persona es hoy rey, o null. */
	private Long reinoDe(Persona x) {
		for (Map.Entry<Long, Integer> en : reyes.entrySet()) {
			if (en.getValue() == x.id) {
				return en.getKey();
			}
		}
		return null;
	}

	/**
	 * Quién hereda al rey en el reino según la ley de la cultura: primero sus hijos y nietos, luego sus hermanos y
	 * sobrinos. Con la ley electiva, también los nobles del reino. Un rey de otro reino solo hereda si así se pueden
	 * unir los dos (ninguno está ya en una unión ni en guerra con el otro).
	 */
	Persona sucesor(Politica p, long reino, Persona rey) {
		Ley ley = Ley.de(rey.cultura);
		int anio = p.anio();
		List<List<Persona>> grupos = new ArrayList<>();
		List<Persona> hijos = vivos(hijos(rey));
		List<Persona> nietos = new ArrayList<>();
		for (Persona h : hijos(rey)) {
			nietos.addAll(vivos(hijos(h)));
		}
		List<Persona> hermanos = new ArrayList<>();
		List<Persona> sobrinos = new ArrayList<>();
		Persona padre = rey.padre == Persona.NADIE ? null : personas.get(rey.padre);
		if (padre != null) {
			for (Persona h : hijos(padre)) {
				if (h.id != rey.id) {
					if (h.vivo()) {
						hermanos.add(h);
					}
					sobrinos.addAll(vivos(hijos(h)));
				}
			}
		}
		grupos.add(hijos);
		grupos.add(nietos);
		grupos.add(hermanos);
		grupos.add(sobrinos);
		List<Persona> sangre = new ArrayList<>();
		for (List<Persona> g : grupos) {
			g.removeIf(x -> !puedeHeredar(p, reino, x));
			sangre.addAll(g);
		}
		if (ley == Ley.electiva) {
			Persona mejor = null;
			int valor = Integer.MIN_VALUE;
			List<Persona> todos = new ArrayList<>(sangre);
			todos.addAll(nobles(reino));
			for (Persona x : todos) {
				if (x.edad(anio) < ADULTO || !puedeHeredar(p, reino, x)) {
					continue;
				}
				int v = x.valia() + (sangre.contains(x) ? 3 : 0);
				if (v > valor) {
					valor = v;
					mejor = x;
				}
			}
			if (mejor != null) {
				return mejor;
			}
		}
		if (ley == Ley.valia) {
			for (List<Persona> g : grupos) {
				Persona mejor = null;
				for (Persona x : g) {
					if (x.edad(anio) >= ADULTO && (mejor == null || x.valia() > mejor.valia())) {
						mejor = x;
					}
				}
				if (mejor != null) {
					return mejor;
				}
			}
		}
		for (List<Persona> g : grupos) {
			Persona mejor = null;
			for (Persona x : g) {
				if (mejor == null || (ley == Ley.varon && x.mujer != mejor.mujer ? !x.mujer : x.nace < mejor.nace)) {
					mejor = x;
				}
			}
			if (mejor != null) {
				return mejor;
			}
		}
		return null;
	}

	private boolean puedeHeredar(Politica p, long reino, Persona x) {
		if (!x.vivo()) {
			return false;
		}
		Long otro = reinoDe(x);
		if (otro == null || otro == reino) {
			return true;
		}
		return p.unionDe(reino) == null && p.unionDe(otro) == null && !p.guerras().containsKey(Politica.Par.de(reino, otro))
				&& !reinoDeOtroMas(x, reino, otro);
	}

	/** Si la persona ya es rey de algún reino más aparte de otro (no se juntan tres coronas). */
	private boolean reinoDeOtroMas(Persona x, long reino, long otro) {
		for (Map.Entry<Long, Integer> en : reyes.entrySet()) {
			if (en.getValue() == x.id && en.getKey() != otro && en.getKey() != reino) {
				return true;
			}
		}
		return false;
	}

	private static List<Persona> vivos(List<Persona> l) {
		l.removeIf(x -> !x.vivo());
		return l;
	}

	/** Duques, general y consejero vivos del reino. */
	private List<Persona> nobles(long reino) {
		List<Persona> res = new ArrayList<>();
		for (Integer id : duques.values()) {
			Persona d = personas.get(id);
			if (d != null && d.vivo() && d.reino == reino) {
				res.add(d);
			}
		}
		for (Persona x : new Persona[] {general(reino), consejero(reino)}) {
			if (x != null && x.vivo()) {
				res.add(x);
			}
		}
		return res;
	}

	/** La persona pasa a reinar en el reino: deja su cargo, y su cónyuge pasa a consorte. */
	private void corona(Persona x, long reino, int anio) {
		cierraReinado(reino, anio);
		liberaCargo(x);
		int mismos = 0;
		for (Reinado r : reinados) {
			Persona antes = personas.get(r.persona());
			if (r.reino() == reino && antes != null && antes.nombre.equals(x.nombre) && r.persona() != x.id) {
				mismos = Math.max(mismos, antes.ordinal);
			}
		}
		if (x.ordinal == 0) {
			x.ordinal = mismos + 1;
		}
		x.rol = Persona.Rol.rey;
		if (reinoDe(x) == null) {
			x.reino = reino;
		}
		reyes.put(reino, x.id);
		reinados.add(new Reinado(reino, x.id, anio, 0));
		Persona c = conyuge(x);
		if (c != null && reinoDe(c) == null) {
			liberaCargo(c);
			c.rol = Persona.Rol.consorte;
			c.reino = x.reino;
		}
	}

	private void cierraReinado(long reino, int anio) {
		for (int i = reinados.size() - 1; i >= 0; i--) {
			Reinado r = reinados.get(i);
			if (r.reino() == reino && r.hasta() == 0) {
				reinados.set(i, new Reinado(reino, r.persona(), r.desde(), Math.max(r.desde(), anio)));
			}
		}
	}

	/** Quita a la persona de su ducado, generalato o consejo. */
	private void liberaCargo(Persona x) {
		if (x.ducado != Politica.NADIE) {
			quitaDuque(x.ducado);
		}
		generales.values().removeIf(id -> id == x.id);
		consejeros.values().removeIf(id -> id == x.id);
	}

	private void ponDuque(long ducado, Persona x, long reino) {
		liberaCargo(x);
		Integer viejo = duques.put(ducado, x.id);
		if (viejo != null && viejo != x.id && personas.get(viejo) != null) {
			personas.get(viejo).ducado = Politica.NADIE;
		}
		x.ducado = ducado;
		x.reino = reino;
		x.rol = Persona.Rol.duque;
		ducadosCambiados.add(ducado);
	}

	private void quitaDuque(long ducado) {
		Integer id = duques.remove(ducado);
		ducadosCambiados.add(ducado);
		Persona x = id == null ? null : personas.get(id);
		if (x != null) {
			x.ducado = Politica.NADIE;
			if (x.rol == Persona.Rol.duque) {
				x.rol = Persona.Rol.sin_cargo;
			}
		}
	}

	private void mata(Persona x, Persona.Muerte causa, int anio) {
		x.muere = Math.max(Math.max(1, anio), x.nace + 1);
		x.causa = causa;
		if (x.ducado != Politica.NADIE) {
			long d = x.ducado;
			quitaDuque(d);
			x.rol = Persona.Rol.duque;
		}
		generales.values().removeIf(id -> id == x.id);
		consejeros.values().removeIf(id -> id == x.id);
	}

	/**
	 * Una persona mata a otra (un jugador, v0.7.1): si era rey, el siguiente turno se elige sucesor. Devuelve el
	 * suceso para la crónica, o null si ya estaba muerta.
	 */
	public Suceso asesina(Politica p, int id, String quien) {
		Persona x = personas.get(id);
		if (x == null || !x.vivo()) {
			return null;
		}
		mata(x, Persona.Muerte.asesinato, p.anio());
		return new Suceso(Suceso.Tipo.asesinato, p.anio(), x.reino, Politica.NADIE, Politica.NADIE, quien, x.id, Persona.NADIE);
	}

	/** Cada reino vivo con su general y su consejero, y cada ducado (menos la capital) con su duque. */
	private void cargos(Politica p, Grafo grafo, Map<Long, List<Long>> ducadosDe, Fuente f, SplittableRandom rnd, int anio) {
		for (Map.Entry<Long, List<Long>> en : ducadosDe.entrySet()) {
			long reino = en.getKey();
			String cultura = cultura(f, reino);
			if (general(reino) == null) {
				Persona g = nueva(f, reino, cultura, rnd.nextDouble() < 0.2, 28 + rnd.nextInt(28), Persona.Rol.general,
						casaNueva(f, cultura, rnd), rnd, anio, 6);
				generales.put(reino, g.id);
			}
			if (consejero(reino) == null) {
				Persona c = nueva(f, reino, cultura, rnd.nextDouble() < 0.4, 35 + rnd.nextInt(30), Persona.Rol.consejero,
						casaNueva(f, cultura, rnd), rnd, anio, 0);
				consejeros.put(reino, c.id);
			}
			Long capital = grafo.capital(reino);
			for (long ducado : en.getValue()) {
				if ((capital != null && capital == ducado) || duque(ducado) != null) {
					continue;
				}
				// un ducado ganado en la guerra se lo lleva el general, si no tiene ya uno; si no, un noble nuevo
				Politica.Cambio c = p.cambios().get(ducado);
				Persona g = general(reino);
				if (c != null && c.turno() == p.turno() && c.lealtad() == Politica.LEALTAD_GUERRA && g != null
						&& rnd.nextDouble() < 0.3) {
					ponDuque(ducado, g, reino);
					g.lealtad = Math.min(100, g.lealtad + 15);
					continue;
				}
				Persona d = nueva(f, reino, cultura, rnd.nextDouble() < 0.3, 20 + rnd.nextInt(45), Persona.Rol.duque,
						casaNueva(f, cultura, rnd), rnd, anio);
				ponDuque(ducado, d, reino);
			}
		}
	}

	/** Los reyes y sus herederos casados tienen hijos de vez en cuando. */
	private void nacimientos(Politica p, Fuente f, SplittableRandom rnd, Consumer<Suceso> anota, int anio) {
		double escala = 96.0 / Math.max(8, p.turnosPorAnio());
		for (Persona x : new ArrayList<>(personas.values())) {
			if (!x.vivo() || !x.mujer || (x.rol != Persona.Rol.rey && x.rol != Persona.Rol.consorte && x.rol != Persona.Rol.familia)) {
				continue;
			}
			Persona c = conyuge(x);
			int edad = x.edad(anio);
			if (c == null || edad < 17 || edad > 42 || rnd.nextDouble() >= 0.004 * escala) {
				continue;
			}
			// el niño es de la casa del que lleva la sangre del reino (el rey, o el padre si ella no reina)
			Persona sangre = x.rol == Persona.Rol.rey ? x : c.rol == Persona.Rol.rey ? c : esDeLaSangre(x) ? x : c;
			Persona h = nueva(f, x.reino, sangre.cultura, rnd.nextBoolean(), 0, Persona.Rol.familia, sangre.casa, rnd, anio);
			padres(h, c.id, x.id);
			if (rnd.nextDouble() < 0.01 * escala) {
				mata(x, Persona.Muerte.parto, anio);
			}
			if (x.rol == Persona.Rol.rey || c.rol == Persona.Rol.rey) {
				anota.accept(new Suceso(Suceso.Tipo.nacimiento, anio, x.reino, Politica.NADIE, Politica.NADIE, null, h.id, Persona.NADIE));
			}
		}
	}

	/** Si la persona es hija de un rey (lleva la sangre de su casa). */
	private boolean esDeLaSangre(Persona x) {
		Persona padre = x.padre == Persona.NADIE ? null : personas.get(x.padre);
		Persona madre = x.madre == Persona.NADIE ? null : personas.get(x.madre);
		return (padre != null && padre.ordinal > 0) || (madre != null && madre.ordinal > 0);
	}

	/**
	 * Las alianzas se sellan casando a dos de las familias reales; los reyes y herederos solteros acaban casándose
	 * con alguien de su nobleza.
	 */
	private void bodas(Politica p, Map<Long, List<Long>> ducadosDe, Fuente f, SplittableRandom rnd, Consumer<Suceso> anota, int anio) {
		double escala = 96.0 / Math.max(8, p.turnosPorAnio());
		for (Politica.Par par : new ArrayList<>(p.alianzas())) {
			if (rnd.nextDouble() >= 0.02 * escala) {
				continue;
			}
			// la tirada va antes de buscar novios: mirar las familias cuesta
			Persona a = soltero(par.a(), null, anio);
			Persona b = a == null ? null : soltero(par.b(), !a.mujer, anio);
			if (a == null || b == null) {
				continue;
			}
			casa(a, b);
			// se va a la corte del otro el que no reina (o, si los dos son herederos, el del reino más pequeño)
			Persona seVa = b.rol == Persona.Rol.rey ? a : a.rol == Persona.Rol.rey ? b
					: ducadosDe.getOrDefault(par.a(), List.of()).size() >= ducadosDe.getOrDefault(par.b(), List.of()).size() ? b : a;
			Persona queda = seVa == a ? b : a;
			if (seVa.rol != Persona.Rol.rey) {
				seVa.reino = queda.reino;
				if (queda.rol == Persona.Rol.rey) {
					seVa.rol = Persona.Rol.consorte;
				}
			}
			p.mejora(par.a(), par.b(), 10);
			anota.accept(new Suceso(Suceso.Tipo.boda, anio, par.a(), par.b(), Politica.NADIE, null, a.id, b.id));
		}
		for (long reino : ducadosDe.keySet()) {
			if (rnd.nextDouble() >= 0.012 * escala) {
				continue;
			}
			Persona rey = rey(reino);
			Persona h = rey != null && conyuge(rey) == null ? null : heredero(p, reino);
			for (Persona x : new Persona[] {rey, h}) {
				if (x == null || !x.vivo() || conyuge(x) != null || x.edad(anio) < 20 || x.edad(anio) > 60) {
					continue;
				}
				Persona c = nueva(f, reino, x.cultura, !x.mujer, Math.max(ADULTO, x.edad(anio) - 8 + rnd.nextInt(10)),
						x.rol == Persona.Rol.rey ? Persona.Rol.consorte : Persona.Rol.familia, casaNueva(f, x.cultura, rnd), rnd, anio);
				casa(x, c);
				if (x.rol == Persona.Rol.rey) {
					anota.accept(new Suceso(Suceso.Tipo.boda, anio, reino, Politica.NADIE, Politica.NADIE, null, x.id, c.id));
				}
			}
		}
	}

	/** El rey o un hijo/hermano suyo, soltero y en edad de casarse (de sexo mujer, o cualquiera si null). */
	private Persona soltero(long reino, Boolean mujer, int anio) {
		Persona rey = rey(reino);
		if (rey == null) {
			return null;
		}
		List<Persona> candidatos = new ArrayList<>();
		candidatos.add(rey);
		candidatos.addAll(hijos(rey));
		for (Persona x : candidatos) {
			if (x.vivo() && conyuge(x) == null && x.edad(anio) >= ADULTO && x.edad(anio) <= 45 && (mujer == null || x.mujer == mujer)) {
				return x;
			}
		}
		return null;
	}

	private void casa(Persona a, Persona b) {
		a.conyuge = b.id;
		b.conyuge = a.id;
	}

	/** En un reino revuelto, un duque o general ambicioso y desleal puede hacerse con la corona. */
	private void golpes(Politica p, Grafo grafo, Map<Long, List<Long>> ducadosDe, SplittableRandom rnd, Consumer<Suceso> anota, int anio) {
		for (long reino : ducadosDe.keySet()) {
			Politica.Reino e = p.reinos().get(reino);
			Persona rey = rey(reino);
			if (e == null || rey == null || !rey.vivo() || e.estabilidad >= 35 || p.protegidoHoy(reino)) {
				continue;
			}
			Persona golpista = null;
			for (Persona x : nobles(reino)) {
				if (x.edad(anio) >= ADULTO && x.lealtad < 35 && (x.tiene(Persona.Rasgo.ambicioso) || x.intriga >= 7)
						&& (golpista == null || x.intriga + x.guerra > golpista.intriga + golpista.guerra)) {
					golpista = x;
				}
			}
			if (golpista == null || rnd.nextDouble() >= 0.03 * 96.0 / Math.max(8, p.turnosPorAnio()) / 4) {
				continue;
			}
			if (rnd.nextDouble() < 0.5) {
				mata(rey, Persona.Muerte.golpe, anio);
			}
			corona(golpista, reino, anio);
			// si aún reina en otro reino (unión), sigue siendo rey allí; si no, se queda sin cargo con su consorte
			if (rey.vivo() && reinoDe(rey) == null) {
				rey.rol = Persona.Rol.sin_cargo;
				Persona c = conyuge(rey);
				if (c != null && c.rol == Persona.Rol.consorte) {
					c.rol = Persona.Rol.sin_cargo;
				}
			}
			e.estabilidad = Math.max(0, e.estabilidad - 10);
			for (Persona x : nobles(reino)) {
				x.lealtad = Math.max(x.lealtad, 45);
			}
			anota.accept(new Suceso(Suceso.Tipo.golpe, anio, reino, Politica.NADIE, Politica.NADIE, null, golpista.id, rey.id));
		}
	}

	/** La lealtad de los nobles va hacia lo que se merece su rey (justo y generoso suben, cruel y codicioso bajan). */
	private void lealtades(Politica p, SplittableRandom rnd) {
		// los nobles de cada reino de una pasada (mirarlos reino a reino recorre todos los duques cada vez)
		Map<Long, List<Persona>> nobles = new java.util.HashMap<>();
		for (Integer id : duques.values()) {
			Persona d = personas.get(id);
			if (d != null && d.vivo()) {
				nobles.computeIfAbsent(d.reino, k -> new ArrayList<>()).add(d);
			}
		}
		for (TreeMap<Long, Integer> cargo : List.of(generales, consejeros)) {
			cargo.forEach((reino, id) -> {
				Persona x = personas.get(id);
				if (x != null && x.vivo()) {
					nobles.computeIfAbsent(reino, k -> new ArrayList<>()).add(x);
				}
			});
		}
		for (Map.Entry<Long, Integer> en : reyes.entrySet()) {
			Persona rey = personas.get(en.getValue());
			Politica.Reino e = p.reinos().get(en.getKey());
			if (rey == null || e == null) {
				continue;
			}
			int base = 55 + (e.estabilidad - 50) / 3;
			for (Persona.Rasgo x : new Persona.Rasgo[] {rey.rasgo1, rey.rasgo2}) {
				base += switch (x) {
					case justo, generoso -> 15;
					case cruel, codicioso -> -15;
					case valiente -> 5;
					case cobarde -> -5;
					default -> 0;
				};
			}
			for (Persona x : nobles.getOrDefault(en.getKey(), List.of())) {
				int objetivo = base - (x.tiene(Persona.Rasgo.ambicioso) ? 15 : 0) + (x.tiene(Persona.Rasgo.humilde) ? 10 : 0);
				x.lealtad = Math.max(0, Math.min(100, x.lealtad + Integer.signum(objetivo - x.lealtad) + rnd.nextInt(-1, 2)));
			}
		}
	}

	/** Las victorias dan fama al general y al rey; con mucha, un mote. */
	private void fama(Politica p, List<Suceso> esteTurno, Consumer<Suceso> anota, int anio) {
		for (Suceso s : esteTurno) {
			int puntos = s.tipo() == Suceso.Tipo.capital ? 25 : s.tipo() == Suceso.Tipo.conquista ? 8 : s.tipo() == Suceso.Tipo.cae ? 0
					: s.tipo() == Suceso.Tipo.expansion ? 3 : 0;
			if (puntos == 0) {
				continue;
			}
			Persona g = general(s.reinoA());
			if (g != null) {
				g.fama += puntos;
			}
			Persona rey = rey(s.reinoA());
			if (rey != null) {
				rey.fama += puntos / 2;
			}
		}
		for (Persona x : personas.values()) {
			if (!x.vivo() || x.mote != null || x.fama < 50 || (x.rol != Persona.Rol.rey && x.rol != Persona.Rol.general)) {
				continue;
			}
			x.mote = x.rol == Persona.Rol.general ? (x.tiene(Persona.Rasgo.cruel) ? "terrible" : "conquistador") : switch (x.rasgo1) {
				case justo, cruel, piadoso, generoso, astuto -> x.rasgo1.name();
				case valiente -> "bravo";
				default -> "grande";
			};
			anota.accept(new Suceso(Suceso.Tipo.renombre, anio, x.reino, Politica.NADIE, Politica.NADIE, null, x.id, Persona.NADIE));
		}
	}

	/** Se olvidan los muertos que ya no nombra nadie (ni la crónica, ni el linaje, ni sus descendientes). */
	private void olvida(Politica p) {
		if (personas.size() <= MAX_PERSONAS) {
			return;
		}
		Set<Integer> guarda = new HashSet<>();
		for (Reinado r : reinados) {
			guarda.add(r.persona());
		}
		for (Suceso s : p.cronica()) {
			guarda.add(s.personaA());
			guarda.add(s.personaB());
		}
		for (Persona x : personas.values()) {
			if (x.vivo()) {
				guarda.add(x.id);
				guarda.add(x.padre);
				guarda.add(x.madre);
				guarda.add(x.conyuge);
			}
		}
		personas.keySet().removeIf(id -> !guarda.contains(id));
		indiceHijos = null;
	}

	// ------------------------------------------------------------------ gente nueva

	private static String cultura(Fuente f, long reino) {
		String c = f.cultura(reino);
		return c == null || c.isEmpty() ? "llanura" : c;
	}

	private static String casaNueva(Fuente f, String cultura, SplittableRandom rnd) {
		List<String> l = f.casas(cultura);
		return l.isEmpty() ? "?" : l.get(rnd.nextInt(l.size()));
	}

	private Persona nueva(Fuente f, long reino, String cultura, boolean mujer, int edad, Persona.Rol rol, String casa,
			SplittableRandom rnd, int anio) {
		return nueva(f, reino, cultura, mujer, edad, rol, casa, rnd, anio, 0);
	}

	/** Una persona nueva con nombre de su cultura y carácter al azar. minGuerra: nota de guerra mínima (generales). */
	private Persona nueva(Fuente f, long reino, String cultura, boolean mujer, int edad, Persona.Rol rol, String casa,
			SplittableRandom rnd, int anio, int minGuerra) {
		List<String> nombres = f.nombres(cultura, mujer);
		String nombre = nombres.isEmpty() ? "?" : nombres.get(rnd.nextInt(nombres.size()));
		Persona.Rasgo[] rasgos = Persona.Rasgo.values();
		Persona.Rasgo r1 = rasgos[rnd.nextInt(rasgos.length)];
		Persona.Rasgo r2;
		do {
			r2 = rasgos[rnd.nextInt(rasgos.length)];
		} while (r2 == r1 || r2.opuesto(r1));
		Persona x = new Persona(siguiente++, nombre, casa, cultura, mujer, anio - edad, rol, reino, r1, r2,
				Math.max(minGuerra, nota(rnd)), nota(rnd), nota(rnd));
		x.lealtad = 50 + rnd.nextInt(30);
		personas.put(x.id, x);
		return x;
	}

	private static int nota(SplittableRandom rnd) {
		return Math.min(10, rnd.nextInt(1, 7) + rnd.nextInt(0, 5));
	}

	/**
	 * La familia real de un reino que sale por primera vez: el rey (con su padre, ya muerto, en el linaje), su
	 * consorte y sus hijos.
	 */
	private void familiaReal(long reino, Fuente f, SplittableRandom rnd, int anio) {
		String cultura = cultura(f, reino);
		String casa = f.casa(reino);
		if (casa == null || casa.isEmpty()) {
			casa = casaNueva(f, cultura, rnd);
		}
		boolean reina = rnd.nextDouble() < (Ley.de(cultura) == Ley.varon ? 0.15 : 0.4);
		int edad = 22 + rnd.nextInt(40);
		// el padre, que reinó antes y ya ha muerto
		Persona padre = nueva(f, reino, cultura, false, edad + 20 + rnd.nextInt(15), Persona.Rol.rey, casa, rnd, anio);
		int murio = anio - 1 - rnd.nextInt(Math.max(1, Math.min(15, edad - 5)));
		padre.ordinal = 1;
		padre.muere = Math.max(padre.nace + 30, murio);
		padre.causa = padre.edad(anio) >= 65 ? Persona.Muerte.vejez : Persona.Muerte.enfermedad;
		reinados.add(new Reinado(reino, padre.id, padre.muere - 10 - rnd.nextInt(25), padre.muere));
		Persona rey = nueva(f, reino, cultura, reina, edad, Persona.Rol.sin_cargo, casa, rnd, anio);
		padres(rey, padre.id, Persona.NADIE);
		if (rnd.nextDouble() < 0.6) {
			// un hermano o hermana
			Persona h = nueva(f, reino, cultura, rnd.nextBoolean(), Math.max(1, edad - 3 + rnd.nextInt(10)), Persona.Rol.familia, casa, rnd, anio);
			padres(h, padre.id, Persona.NADIE);
		}
		corona(rey, reino, anio);
		reinados.set(reinados.size() - 1, new Reinado(reino, rey.id, padre.muere, 0));
		if (edad >= 20 && rnd.nextDouble() < 0.85) {
			Persona c = nueva(f, reino, cultura, !reina, Math.max(ADULTO, edad - 6 + rnd.nextInt(12)), Persona.Rol.consorte,
					casaNueva(f, cultura, rnd), rnd, anio);
			casa(rey, c);
			int maxEdad = Math.min(edad, c.edad(anio)) - 18;
			int hijos = maxEdad <= 0 ? 0 : rnd.nextInt(0, 5);
			for (int i = 0; i < hijos; i++) {
				Persona h = nueva(f, reino, cultura, rnd.nextBoolean(), rnd.nextInt(maxEdad + 1), Persona.Rol.familia, casa, rnd, anio);
				padres(h, reina ? c.id : rey.id, reina ? rey.id : c.id);
			}
		}
	}

	/** Para guardar y cargar: mete a una persona tal cual. */
	public void restaura(Persona x) {
		personas.put(x.id, x);
		indiceHijos = null;
		siguiente = Math.max(siguiente, x.id + 1);
	}

	/** Para las pruebas: todas las personas en orden. */
	public Collection<Persona> todas() {
		return personas.values();
	}
}
