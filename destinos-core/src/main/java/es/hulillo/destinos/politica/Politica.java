package es.hulillo.destinos.politica;

import es.hulillo.destinos.Geo;
import es.hulillo.destinos.MapaTerreno;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Política viva: quién manda ahora en cada ducado y cómo se llevan los reinos.
 *
 * El mapa base (MapaTerreno) dice de qué reino nació cada ducado. Encima, esta clase guarda solo lo que ha
 * cambiado (dueño nuevo de un ducado) y el estado de cada reino. Cada turno, con tiradas deterministas de la
 * sal y el número de turno, se mueven las relaciones y pueden pasar guerras, conquistas, alianzas,
 * vasallajes, anexiones, crisis y expansiones. Pocas cosas grandes por año para que el mapa no sea un caos.
 *
 * Los reinos y ducados se identifican por su celda (Grafo.id). No depende de Minecraft: se prueba con
 * herramientas/PruebaPolitica.java.
 */
public final class Politica {
	public static final long NADIE = Long.MIN_VALUE;
	/** Dueño de un ducado que ha quedado sin señor. */
	public static final long SALVAJE = Long.MIN_VALUE + 1;
	public static final int TURNOS_POR_ANIO = 8;
	/** Durante estos turnos el reino protegido (donde se empieza a jugar) no pierde tierras. */
	public static final int TURNOS_PROTECCION = 10;
	private static final int MAX_CRONICA = 30;

	// Rasgos del datapack (destinos.rasgo.N)
	private static final int MILITAR = 0;
	private static final int MERCANTIL = 1;
	private static final int DEVOTO = 2;
	private static final int DECADENTE = 3;

	/** Estado de un reino que ya ha salido en el mapa conocido. */
	public static final class Reino {
		public int estabilidad = 60;
		/** Reino del que es vasallo, o NADIE. */
		public long senor = NADIE;
		public int turnosVasallo;
		public boolean caido;
	}

	/**
	 * Un ducado que ya no es de su reino de origen. baseNoble: nació con señor (entonces ahora es "del Reino de
	 * X" por conquista o anexión); si no, era tierra salvaje reclamada.
	 */
	public record Cambio(long dueno, int lealtad, int anio, boolean baseNoble) {
	}

	/** Par de reinos sin orden (a < b). */
	public record Par(long a, long b) implements Comparable<Par> {
		public static Par de(long x, long y) {
			return x < y ? new Par(x, y) : new Par(y, x);
		}

		public long otro(long r) {
			return r == a ? b : a;
		}

		@Override
		public int compareTo(Par o) {
			int c = Long.compare(a, o.a);
			return c != 0 ? c : Long.compare(b, o.b);
		}
	}

	private final int sal;
	private int turno;
	private long reinoProtegido = NADIE;
	private final Map<Long, Reino> reinos = new TreeMap<>();
	private final Map<Long, Cambio> cambios = new TreeMap<>();
	private final Map<Par, Integer> relaciones = new TreeMap<>();
	/** Guerras abiertas y cuántos turnos llevan. */
	private final Map<Par, Integer> guerras = new TreeMap<>();
	private final TreeSet<Par> alianzas = new TreeSet<>();
	private final Deque<Suceso> cronica = new ArrayDeque<>();

	public Politica(int sal) {
		this.sal = sal;
	}

	public int sal() {
		return sal;
	}

	public int turno() {
		return turno;
	}

	public int anio() {
		return 1 + turno / TURNOS_POR_ANIO;
	}

	// ------------------------------------------------------------------ consultas

	/** Dueño actual de un ducado: un reino, o SALVAJE. */
	public long dueno(Grafo.Ducado d) {
		Cambio c = cambios.get(d.id());
		if (c != null) {
			return c.dueno();
		}
		return d.salvajeBase() ? SALVAJE : d.reinoBase();
	}

	/** El territorio del mapa base con el dueño actual encima (lo que ven títulos y mapas). */
	public MapaTerreno.Resultado aplica(MapaTerreno.Resultado r) {
		Cambio c = cambios.get(Grafo.id(r.dcx(), r.dcz()));
		if (c == null) {
			return r;
		}
		if (c.dueno() == SALVAJE) {
			return new MapaTerreno.Resultado(r.dcx(), r.dcz(), r.kx(), r.kz(), true, r.oceano());
		}
		// un ducado salvaje reclamado deja de serlo entero; en uno noble, sus islas sueltas siguen sin señor
		boolean salvaje = r.salvaje() && c.baseNoble();
		return new MapaTerreno.Resultado(r.dcx(), r.dcz(), Grafo.x(c.dueno()), Grafo.z(c.dueno()), salvaje, r.oceano());
	}

	public Map<Long, Reino> reinos() {
		return reinos;
	}

	public Map<Long, Cambio> cambios() {
		return cambios;
	}

	public int relacion(long a, long b) {
		return relaciones.getOrDefault(Par.de(a, b), relacionInicial(a, b));
	}

	public Map<Par, Integer> relaciones() {
		return relaciones;
	}

	public Map<Par, Integer> guerras() {
		return guerras;
	}

	public TreeSet<Par> alianzas() {
		return alianzas;
	}

	public Deque<Suceso> cronica() {
		return cronica;
	}

	/** Reinos que hoy tocan por tierra al reino dado. */
	public TreeSet<Long> vecinosDe(Grafo grafo, long reino) {
		TreeSet<Long> res = new TreeSet<>();
		for (Grafo.Ducado d : grafo.ducados().values()) {
			if (dueno(d) != reino) {
				continue;
			}
			for (long v : grafo.vecinos(d.id()).keySet()) {
				long o = dueno(grafo.ducado(v));
				if (o != SALVAJE && o != reino) {
					res.add(o);
				}
			}
		}
		return res;
	}

	public long reinoProtegido() {
		return reinoProtegido;
	}

	/** El reino donde se empieza: no pierde tierras en los primeros turnos. Solo se fija una vez. */
	public void protege(long reino) {
		if (reinoProtegido == NADIE && reino != SALVAJE) {
			reinoProtegido = reino;
		}
	}

	// ------------------------------------------------------------------ para guardar y cargar

	public void restaura(int turno, long protegido) {
		this.turno = turno;
		this.reinoProtegido = protegido;
	}

	// ------------------------------------------------------------------ el turno

	/** Juega un turno sobre el mapa conocido y avisa de cada suceso. */
	public void turno(Grafo grafo, Consumer<Suceso> aviso) {
		turno++;
		SplittableRandom rnd = new SplittableRandom(((long) sal << 20) ^ turno * 0x9E3779B97F4A7C15L);
		Mapa mapa = new Mapa(grafo);
		for (long r : mapa.reinos()) {
			reinos.computeIfAbsent(r, k -> new Reino()).caido = false;
		}
		List<Suceso> sucesos = new ArrayList<>();
		Consumer<Suceso> anota = s -> {
			sucesos.add(s);
			cronica.addLast(s);
			while (cronica.size() > MAX_CRONICA) {
				cronica.removeFirst();
			}
		};
		// cada reino protagoniza como mucho un suceso grande por turno
		TreeSet<Long> ocupados = new TreeSet<>();

		relaciones(mapa, rnd);
		estabilidad(mapa);
		paces(mapa, rnd, anota, ocupados);
		batallas(mapa, rnd, anota, ocupados);
		declaraciones(mapa, rnd, anota, ocupados);
		alianzas(mapa, rnd, anota);
		vasallajes(mapa, rnd, anota, ocupados);
		crisis(mapa, rnd, anota, ocupados);
		expansiones(mapa, rnd, anota, ocupados);
		limpia(new Mapa(grafo));
		sucesos.forEach(aviso);
	}

	/** Foto del reparto actual: qué ducados tiene cada reino y qué reinos se tocan. */
	private final class Mapa {
		final Grafo grafo;
		final Map<Long, List<Long>> ducadosDe = new TreeMap<>();
		final Map<Long, Integer> poder = new HashMap<>();
		final TreeSet<Par> vecinos = new TreeSet<>();

		Mapa(Grafo grafo) {
			this.grafo = grafo;
			for (Grafo.Ducado d : grafo.ducados().values()) {
				long r = dueno(d);
				if (r == SALVAJE) {
					continue;
				}
				ducadosDe.computeIfAbsent(r, k -> new ArrayList<>()).add(d.id());
				poder.merge(r, Math.max(1, grafo.tamano(d.id())), Integer::sum);
			}
			for (Grafo.Ducado d : grafo.ducados().values()) {
				long r = dueno(d);
				if (r == SALVAJE) {
					continue;
				}
				for (long v : grafo.vecinos(d.id()).keySet()) {
					long o = dueno(grafo.ducado(v));
					if (o != SALVAJE && o != r) {
						vecinos.add(Par.de(r, o));
					}
				}
			}
		}

		Iterable<Long> reinos() {
			return ducadosDe.keySet();
		}

		int poder(long r) {
			return poder.getOrDefault(r, 0);
		}

		boolean vivo(long r) {
			return ducadosDe.containsKey(r);
		}
	}

	private int relacionInicial(long a, long b) {
		return Math.floorMod(Geo.hash(Grafo.x(a) ^ Grafo.x(b), Grafo.z(a) ^ Grafo.z(b), sal + 7), 61) - 30;
	}

	private int rasgo(long reino) {
		return Geo.rasgoReino(Grafo.x(reino), Grafo.z(reino), sal);
	}

	private void cambiaRelacion(long a, long b, int delta) {
		Par p = Par.de(a, b);
		int r = relaciones.getOrDefault(p, relacionInicial(a, b)) + delta;
		relaciones.put(p, Math.max(-100, Math.min(100, r)));
	}

	private void relaciones(Mapa mapa, SplittableRandom rnd) {
		for (Par p : mapa.vecinos) {
			int delta = rnd.nextInt(-4, 5);
			int ra = rasgo(p.a);
			int rb = rasgo(p.b);
			if (ra == MILITAR || rb == MILITAR) {
				delta -= 2;
			}
			if (ra == MERCANTIL && rb == MERCANTIL) {
				delta += 4;
			}
			if ((ra == DEVOTO && rb == DECADENTE) || (ra == DECADENTE && rb == DEVOTO)) {
				delta -= 3;
			}
			if (alianzas.contains(p)) {
				delta += 3;
			}
			if (guerras.containsKey(p)) {
				delta -= 5;
			}
			if (vasallo(p.a, p.b) || vasallo(p.b, p.a)) {
				delta += 4;
			}
			cambiaRelacion(p.a, p.b, delta);
		}
	}

	private boolean vasallo(long v, long senor) {
		Reino r = reinos.get(v);
		return r != null && r.senor == senor;
	}

	private void estabilidad(Mapa mapa) {
		for (long r : mapa.reinos()) {
			Reino e = reinos.get(r);
			e.estabilidad += Integer.compare(60, e.estabilidad) * 3;
			for (Par g : guerras.keySet()) {
				if (g.a == r || g.b == r) {
					e.estabilidad -= 2;
				}
			}
			e.estabilidad = Math.max(0, Math.min(100, e.estabilidad));
		}
	}

	private boolean protegido(long r) {
		return r == reinoProtegido && turno <= TURNOS_PROTECCION;
	}

	private void paces(Mapa mapa, SplittableRandom rnd, Consumer<Suceso> anota, TreeSet<Long> ocupados) {
		for (Par p : new ArrayList<>(guerras.keySet())) {
			int turnos = guerras.get(p) + 1;
			guerras.put(p, turnos);
			double prob = 0.10 + 0.05 * turnos;
			if (reinos.get(p.a).estabilidad < 30 || reinos.get(p.b).estabilidad < 30) {
				prob += 0.15;
			}
			if (rnd.nextDouble() < prob) {
				guerras.remove(p);
				// tras la paz, unos años de tregua antes de que la relación vuelva a agriarse
				relaciones.put(p, 15);
				ocupados.add(p.a);
				ocupados.add(p.b);
				anota.accept(new Suceso(Suceso.Tipo.paz, anio(), p.a, p.b, NADIE));
			}
		}
	}

	/** Poder de un reino en guerra contando a sus aliados (que no sean del bando contrario). */
	private int fuerza(Mapa mapa, long r, long enemigo) {
		int f = mapa.poder(r);
		for (Par a : alianzas) {
			if (a.a == r || a.b == r) {
				long otro = a.otro(r);
				if (otro != enemigo) {
					f += mapa.poder(otro) / 2;
				}
			}
		}
		return f;
	}

	private void batallas(Mapa mapa, SplittableRandom rnd, Consumer<Suceso> anota, TreeSet<Long> ocupados) {
		for (Par p : new ArrayList<>(guerras.keySet())) {
			if (!mapa.vivo(p.a) || !mapa.vivo(p.b)) {
				continue;
			}
			int fa = fuerza(mapa, p.a, p.b);
			int fb = fuerza(mapa, p.b, p.a);
			// gana el turno uno de los dos, más probable el más fuerte; el otro puede perder un ducado
			boolean ganaA = rnd.nextDouble() * (fa + fb) < fa;
			long gana = ganaA ? p.a : p.b;
			long pierde = ganaA ? p.b : p.a;
			if (protegido(pierde)) {
				continue;
			}
			Long objetivo = null;
			double mejor = Double.MAX_VALUE;
			for (long d : mapa.ducadosDe.get(pierde)) {
				for (Map.Entry<Long, Grafo.Lado> v : mapa.grafo.vecinos(d).entrySet()) {
					if (dueno(mapa.grafo.ducado(v.getKey())) == gana && v.getValue().dificultad() < mejor) {
						mejor = v.getValue().dificultad();
						objetivo = d;
					}
				}
			}
			if (objetivo == null) {
				continue;
			}
			// cruzar ríos y cordilleras cuesta: la conquista falla más
			if (rnd.nextDouble() < 0.3 * (1 - 0.6 * mejor)) {
				conquista(objetivo, gana, mapa, true);
				reinos.get(pierde).estabilidad -= 6;
				reinos.get(gana).estabilidad += 2;
				ocupados.add(gana);
				ocupados.add(pierde);
				anota.accept(new Suceso(Suceso.Tipo.conquista, anio(), gana, pierde, objetivo));
				if (mapa.ducadosDe.get(pierde).isEmpty()) {
					cae(pierde, mapa);
					anota.accept(new Suceso(Suceso.Tipo.cae, anio(), pierde, gana, NADIE));
				}
			}
		}
	}

	/** Pasa un ducado a otro dueño y actualiza la foto del turno. */
	private void conquista(long ducado, long nuevo, Mapa mapa, boolean guerra) {
		Grafo.Ducado d = mapa.grafo.ducado(ducado);
		long viejo = dueno(d);
		if (viejo != SALVAJE) {
			mapa.ducadosDe.get(viejo).remove(ducado);
			mapa.poder.merge(viejo, -Math.max(1, mapa.grafo.tamano(ducado)), Integer::sum);
		}
		if (nuevo != SALVAJE) {
			mapa.ducadosDe.computeIfAbsent(nuevo, k -> new ArrayList<>()).add(ducado);
			mapa.poder.merge(nuevo, Math.max(1, mapa.grafo.tamano(ducado)), Integer::sum);
		}
		long base = d.salvajeBase() ? SALVAJE : d.reinoBase();
		if (nuevo == base) {
			cambios.remove(ducado);
		} else {
			cambios.put(ducado, new Cambio(nuevo, guerra ? 20 : 60, anio(), !d.salvajeBase() && nuevo != SALVAJE));
		}
	}

	private void cae(long reino, Mapa mapa) {
		Reino r = reinos.get(reino);
		r.caido = true;
		r.senor = NADIE;
		guerras.keySet().removeIf(p -> p.a == reino || p.b == reino);
		alianzas.removeIf(p -> p.a == reino || p.b == reino);
		for (Reino otro : reinos.values()) {
			if (otro.senor == reino) {
				otro.senor = NADIE;
			}
		}
		mapa.ducadosDe.remove(reino);
	}

	private boolean enGuerra(long r) {
		for (Par p : guerras.keySet()) {
			if (p.a == r || p.b == r) {
				return true;
			}
		}
		return false;
	}

	private void declaraciones(Mapa mapa, SplittableRandom rnd, Consumer<Suceso> anota, TreeSet<Long> ocupados) {
		// pocas guerras nuevas a la vez, para que se puedan seguir
		int maximo = Math.max(1, mapa.ducadosDe.size() / 40);
		for (Par p : mapa.vecinos) {
			if (maximo == 0) {
				break;
			}
			if (guerras.containsKey(p) || alianzas.contains(p) || vasallo(p.a, p.b) || vasallo(p.b, p.a)
					|| ocupados.contains(p.a) || ocupados.contains(p.b) || enGuerra(p.a) || enGuerra(p.b)) {
				continue;
			}
			if (relacion(p.a, p.b) > -60) {
				continue;
			}
			// declara el más fuerte, o el militar
			long ataca = mapa.poder(p.a) >= mapa.poder(p.b) ? p.a : p.b;
			if (rasgo(p.otro(ataca)) == MILITAR && rasgo(ataca) != MILITAR) {
				ataca = p.otro(ataca);
			}
			double prob = rasgo(ataca) == MILITAR ? 0.12 : 0.06;
			if (rnd.nextDouble() < prob) {
				maximo--;
				guerras.put(p, 0);
				ocupados.add(p.a);
				ocupados.add(p.b);
				anota.accept(new Suceso(Suceso.Tipo.guerra, anio(), ataca, p.otro(ataca), NADIE));
				// los aliados del atacado se enfadan con el atacante
				for (Par a : alianzas) {
					long defiende = p.otro(ataca);
					if (a.a == defiende || a.b == defiende) {
						cambiaRelacion(a.otro(defiende), ataca, -15);
					}
				}
			}
		}
	}

	private void alianzas(Mapa mapa, SplittableRandom rnd, Consumer<Suceso> anota) {
		for (Par p : new ArrayList<>(alianzas)) {
			if (relacion(p.a, p.b) < 20 || guerras.containsKey(p)) {
				alianzas.remove(p);
				anota.accept(new Suceso(Suceso.Tipo.ruptura, anio(), p.a, p.b, NADIE));
			}
		}
		for (Par p : mapa.vecinos) {
			if (!alianzas.contains(p) && !guerras.containsKey(p) && relacion(p.a, p.b) >= 60 && rnd.nextDouble() < 0.3) {
				alianzas.add(p);
				anota.accept(new Suceso(Suceso.Tipo.alianza, anio(), p.a, p.b, NADIE));
			}
		}
	}

	private void vasallajes(Mapa mapa, SplittableRandom rnd, Consumer<Suceso> anota, TreeSet<Long> ocupados) {
		// vasallos: se independizan si se llevan mal, o acaban anexionados si son leales
		for (long v : new ArrayList<>(mapa.ducadosDe.keySet())) {
			Reino r = reinos.get(v);
			if (r.senor == NADIE) {
				continue;
			}
			if (!mapa.vivo(r.senor)) {
				r.senor = NADIE;
				continue;
			}
			r.turnosVasallo++;
			int rel = relacion(v, r.senor);
			if (rel < 0) {
				long senor = r.senor;
				r.senor = NADIE;
				anota.accept(new Suceso(Suceso.Tipo.independencia, anio(), v, senor, NADIE));
			} else if (r.turnosVasallo >= 6 && rel >= 70 && !protegido(v) && !ocupados.contains(v)
					&& rnd.nextDouble() < 0.2) {
				long senor = r.senor;
				for (long d : new ArrayList<>(mapa.ducadosDe.get(v))) {
					conquista(d, senor, mapa, false);
				}
				cae(v, mapa);
				ocupados.add(v);
				ocupados.add(senor);
				anota.accept(new Suceso(Suceso.Tipo.anexion, anio(), senor, v, NADIE));
			}
		}
		// reinos pequeños junto a uno grande y amistoso
		for (Par p : mapa.vecinos) {
			long chico = mapa.poder(p.a) <= mapa.poder(p.b) ? p.a : p.b;
			long grande = p.otro(chico);
			Reino c = reinos.get(chico);
			if (c == null || c.senor != NADIE || reinos.get(grande).senor != NADIE || guerras.containsKey(p)
					|| ocupados.contains(chico) || chico == reinoProtegido) {
				continue;
			}
			if (mapa.poder(chico) * 3 < mapa.poder(grande) && relacion(chico, grande) >= 40 && rnd.nextDouble() < 0.15) {
				c.senor = grande;
				c.turnosVasallo = 0;
				ocupados.add(chico);
				anota.accept(new Suceso(Suceso.Tipo.vasallaje, anio(), chico, grande, NADIE));
			}
		}
	}

	private void crisis(Mapa mapa, SplittableRandom rnd, Consumer<Suceso> anota, TreeSet<Long> ocupados) {
		for (long r : new ArrayList<>(mapa.ducadosDe.keySet())) {
			Reino e = reinos.get(r);
			if (e.estabilidad > 10 || protegido(r) || ocupados.contains(r) || rnd.nextDouble() >= 0.2) {
				continue;
			}
			for (long d : new ArrayList<>(mapa.ducadosDe.get(r))) {
				conquista(d, SALVAJE, mapa, false);
			}
			cae(r, mapa);
			ocupados.add(r);
			anota.accept(new Suceso(Suceso.Tipo.disolucion, anio(), r, NADIE, NADIE));
		}
	}

	private void expansiones(Mapa mapa, SplittableRandom rnd, Consumer<Suceso> anota, TreeSet<Long> ocupados) {
		for (long r : new ArrayList<>(mapa.ducadosDe.keySet())) {
			Reino e = reinos.get(r);
			if (e.estabilidad < 55 || enGuerra(r) || ocupados.contains(r) || rnd.nextDouble() >= 0.15) {
				continue;
			}
			Long objetivo = null;
			double mejor = Double.MAX_VALUE;
			for (long d : mapa.ducadosDe.get(r)) {
				for (Map.Entry<Long, Grafo.Lado> v : mapa.grafo.vecinos(d).entrySet()) {
					if (dueno(mapa.grafo.ducado(v.getKey())) == SALVAJE && v.getValue().dificultad() < mejor) {
						mejor = v.getValue().dificultad();
						objetivo = v.getKey();
					}
				}
			}
			if (objetivo != null) {
				conquista(objetivo, r, mapa, false);
				ocupados.add(r);
				anota.accept(new Suceso(Suceso.Tipo.expansion, anio(), r, NADIE, objetivo));
			}
		}
	}

	/** Quita guerras y alianzas de reinos que ya no tienen tierras. */
	private void limpia(Mapa mapa) {
		guerras.keySet().removeIf(p -> !mapa.vivo(p.a) || !mapa.vivo(p.b));
		alianzas.removeIf(p -> !mapa.vivo(p.a) || !mapa.vivo(p.b));
	}
}
