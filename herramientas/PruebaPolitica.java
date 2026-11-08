import es.hulillo.destinos.AspectoPersona;
import es.hulillo.destinos.Geo;
import es.hulillo.destinos.RutinaCorte;
import es.hulillo.destinos.politica.Grafo;
import es.hulillo.destinos.politica.Politica;
import es.hulillo.destinos.politica.Suceso;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Simula 25 años de política viva sobre una rejilla de ducados con los reinos del mapa base y comprueba que
 * es reproducible, que el reino protegido aguanta y que el mapa cambia sin volverse un caos.
 * Uso: java PruebaPolitica
 */
public final class PruebaPolitica {
	private static final int LADO = 36;
	private static final int TURNOS = 25 * Politica.TURNOS_POR_ANIO;

	public static void main(String[] args) {
		for (int sal : new int[] {184467221, 99, 1999999999, -123456}) {
			String a = simula(sal, true);
			String b = simula(sal, false);
			if (!a.equals(b)) {
				falla("con la misma sal " + sal + " dos simulaciones dan resultados distintos");
			}
		}
		for (int sal : new int[] {184467221, 99, 1999999999, -123456}) {
			empujon(sal);
			embajada(sal);
		}
		gente();
		for (int sal : new int[] {184467221, 99, 1999999999, -123456}) {
			asesinato(sal);
		}
		corte();
		System.out.println("OK: política reproducible, reino protegido a salvo y mapa vivo sin caos");
	}

	private static Grafo grafo(int sal) {
		Grafo g = new Grafo();
		for (int cx = 0; cx < LADO; cx++) {
			for (int cz = 0; cz < LADO; cz++) {
				Geo.Reino r = Geo.reinoDeDucado(cx, cz, sal);
				long id = Grafo.id(cx, cz);
				g.agrega(new Grafo.Ducado(id, cx, cz, Grafo.id(r.kx(), r.kz()), r.salvaje()),
						300 + Math.floorMod(Geo.hash(cx, cz, sal + 3), 400));
				g.recursos(id, Math.floorMod(Geo.hash(cx, cz, sal + 5), 1200), Math.floorMod(Geo.hash(cx, cz, sal + 6), 900),
						Math.floorMod(Geo.hash(cx, cz, sal + 7), 900), Math.floorMod(Geo.hash(cx, cz, sal + 8), 4));
			}
		}
		// capital: el ducado con señor más cerca del centro de su reino
		java.util.Map<Long, long[]> mejor = new java.util.HashMap<>();
		for (Grafo.Ducado d : g.ducados().values()) {
			if (d.salvajeBase()) {
				continue;
			}
			int[] k = Geo.centroReino(Grafo.x(d.reinoBase()), Grafo.z(d.reinoBase()), sal);
			int[] c = Geo.centroDucado(d.dcx(), d.dcz(), sal);
			long dist = (long) (c[0] - k[0]) * (c[0] - k[0]) + (long) (c[1] - k[1]) * (c[1] - k[1]);
			long[] m = mejor.get(d.reinoBase());
			if (m == null || dist < m[1]) {
				mejor.put(d.reinoBase(), new long[] {d.id(), dist});
			}
		}
		mejor.forEach((reino, m) -> g.fijaCapital(reino, m[0]));
		for (int cx = 0; cx < LADO; cx++) {
			for (int cz = 0; cz < LADO; cz++) {
				int dif = Math.floorMod(Geo.hash(cx, cz, sal + 11), 10);
				if (cx + 1 < LADO) {
					g.une(Grafo.id(cx, cz), Grafo.id(cx + 1, cz), 20, dif);
				}
				if (cz + 1 < LADO) {
					g.une(Grafo.id(cx, cz), Grafo.id(cx, cz + 1), 20, 10 - dif);
				}
			}
		}
		return g;
	}

	private static String simula(int sal, boolean informa) {
		Grafo g = grafo(sal);
		Politica p = new Politica(sal);
		long protegido = p.dueno(g.ducado(Grafo.id(LADO / 2, LADO / 2)));
		if (protegido == Politica.SALVAJE) {
			protegido = g.ducado(Grafo.id(LADO / 2, LADO / 2)).reinoBase();
		}
		p.protege(protegido);
		int ducadosProtegido = cuenta(p, g, protegido);
		int reinosAntes = reinosVivos(p, g);
		Map<Suceso.Tipo, Integer> cuenta = new EnumMap<>(Suceso.Tipo.class);
		List<String> registro = new ArrayList<>();
		int maxPorTurno = 0;
		for (int t = 1; t <= TURNOS; t++) {
			int[] esteTurno = {0};
			p.turno(g, s -> {
				cuenta.merge(s.tipo(), 1, Integer::sum);
				registro.add(s.toString());
				esteTurno[0]++;
			});
			maxPorTurno = Math.max(maxPorTurno, esteTurno[0]);
			if (t <= Politica.TURNOS_PROTECCION && cuenta(p, g, protegido) < ducadosProtegido) {
				falla("sal " + sal + ": el reino protegido pierde tierras en el turno " + t);
			}
			for (Map.Entry<Long, Politica.Cambio> c : p.cambios().entrySet()) {
				if (g.ducado(c.getKey()) == null) {
					falla("sal " + sal + ": cambio en un ducado que no existe");
				}
			}
		}
		int reinosDespues = reinosVivos(p, g);
		if (informa) {
			System.out.println("sal " + sal + ": reinos " + reinosAntes + " → " + reinosDespues + " en 25 años · " + cuenta
					+ " · máx. " + maxPorTurno + " sucesos en un turno");
		}
		if (cuenta.getOrDefault(Suceso.Tipo.guerra, 0) == 0 || cuenta.getOrDefault(Suceso.Tipo.conquista, 0) == 0) {
			falla("sal " + sal + ": en 25 años no ha habido ninguna guerra o conquista");
		}
		if (reinosDespues * 2 < reinosAntes) {
			falla("sal " + sal + ": han desaparecido más de la mitad de los reinos (caos)");
		}
		StringBuilder gente = new StringBuilder();
		p.gente().todas().forEach(x -> gente.append(x).append('\n'));
		return String.join("\n", registro) + p.cambios() + gente + p.gente().reyes();
	}

	/**
	 * v0.7 Gente: en 40 años cada reino vivo tiene siempre un rey vivo, los reyes mueren y heredan según su ley,
	 * hay bodas, y los muertos no siguen reinando ni gobernando ducados.
	 */
	private static void gente() {
		Map<Suceso.Tipo, Integer> total = new EnumMap<>(Suceso.Tipo.class);
		for (int sal : new int[] {184467221, 99, 1999999999, -123456}) {
			Grafo g = grafo(sal);
			Politica p = new Politica(sal);
			List<Suceso> todos = new ArrayList<>();
			for (int t = 1; t <= 40 * Politica.TURNOS_POR_ANIO; t++) {
				p.turno(g, s -> {
					total.merge(s.tipo(), 1, Integer::sum);
					todos.add(s);
				});
				for (long reino : p.reinosVivos(g)) {
					es.hulillo.destinos.politica.Persona rey = p.gente().rey(reino);
					if (rey == null || !rey.vivo() || rey.rol != es.hulillo.destinos.politica.Persona.Rol.rey) {
						falla("sal " + sal + ", turno " + t + ": el reino " + reino + " se ha quedado sin rey vivo (" + rey + ")");
					}
				}
				p.gente().duques().forEach((d, id) -> {
					es.hulillo.destinos.politica.Persona x = p.gente().persona(id);
					if (x == null || !x.vivo() || x.ducado != d || p.dueno(g.ducado(d)) != x.reino) {
						falla("sal " + sal + ": el ducado " + d + " tiene un duque que no vale (" + x + ")");
					}
				});
			}
			// el árbol de cada rey: él en el centro, sin repetidos, sus hijos debajo y sus padres encima
			for (long reino : p.reinosVivos(g)) {
				es.hulillo.destinos.politica.Persona rey = p.gente().rey(reino);
				List<es.hulillo.destinos.politica.Gente.Rama> arbol = p.gente().arbol(rey.id);
				java.util.Set<Integer> vistos = new java.util.HashSet<>();
				boolean esta = false;
				for (es.hulillo.destinos.politica.Gente.Rama r : arbol) {
					es.hulillo.destinos.politica.Persona x = r.persona();
					if (!vistos.add(x.id)) {
						falla("sal " + sal + ": el árbol de " + rey + " repite a " + x);
					}
					esta |= x.id == rey.id && r.generacion() == 0;
					if (r.generacion() == 1 && x.padre != rey.id && x.madre != rey.id) {
						falla("sal " + sal + ": en el árbol de " + rey + " sale como hijo " + x);
					}
					if (r.generacion() == -1 && x.id != rey.padre && x.id != rey.madre) {
						falla("sal " + sal + ": en el árbol de " + rey + " sale como padre " + x);
					}
				}
				if (!esta || arbol.size() > es.hulillo.destinos.politica.Gente.MAX_ARBOL) {
					falla("sal " + sal + ": el árbol de " + rey + " no vale (" + arbol.size() + " personas)");
				}
			}
			for (Suceso s : p.cronica()) {
				if (s.personaA() != es.hulillo.destinos.politica.Persona.NADIE && p.gente().persona(s.personaA()) == null) {
					falla("sal " + sal + ": la crónica nombra a alguien olvidado (" + s + ")");
				}
			}
			System.out.println("sal " + sal + ": " + p.gente().personas().size() + " personas, " + p.gente().reinados().size()
					+ " reinados en 40 años");
		}
		System.out.println("gente: " + total);
		for (Suceso.Tipo t : new Suceso.Tipo[] {Suceso.Tipo.muerte, Suceso.Tipo.sucesion, Suceso.Tipo.boda, Suceso.Tipo.nacimiento}) {
			if (total.getOrDefault(t, 0) == 0) {
				falla("en 40 años no ha habido ningún suceso de " + t);
			}
		}
	}

	/**
	 * Encargos de guerra: con Politica.CAMPAMENTOS_CONQUISTA campamentos arrasados en el ducado del frente, el reino
	 * que los pidió lo toma en el siguiente turno, y la crónica dice quién ayudó.
	 */
	private static void empujon(int sal) {
		Grafo g = grafo(sal);
		Politica p = new Politica(sal);
		for (int t = 1; t <= TURNOS; t++) {
			p.turno(g, s -> {
			});
			if (t <= Politica.TURNOS_PROTECCION || p.guerras().isEmpty()) {
				continue;
			}
			Politica.Par par = p.guerras().keySet().iterator().next();
			long ducado = p.frente(g, par.a(), par.b());
			if (ducado == Politica.NADIE) {
				continue;
			}
			int n = 0;
			for (int i = 0; i < Politica.CAMPAMENTOS_CONQUISTA; i++) {
				n = p.empuja(g, ducado, par.a(), i == 1 ? "Pepe" : "hulio");
			}
			if (n != Politica.CAMPAMENTOS_CONQUISTA) {
				falla("sal " + sal + ": los campamentos arrasados no se suman (" + n + ")");
			}
			List<Suceso> sucesos = new ArrayList<>();
			p.turno(g, sucesos::add);
			boolean ok = sucesos.stream().anyMatch(s -> (s.tipo() == Suceso.Tipo.conquista || s.tipo() == Suceso.Tipo.capital)
					&& s.ducado() == ducado && s.reinoA() == par.a() && "hulio, Pepe".equals(s.ayuda()));
			if (!ok || p.dueno(g.ducado(ducado)) != par.a()) {
				falla("sal " + sal + ": con " + n + " campamentos arrasados el ducado no cae: " + sucesos);
			}
			System.out.println("sal " + sal + ": empujón OK en el turno " + (t + 1) + " (" + sucesos.size() + " sucesos)");
			return;
		}
		falla("sal " + sal + ": no ha habido ninguna guerra con frente para probar los campamentos");
	}

	/** Encargos de embajada: una carta entregada trae la paz (en guerra) o la alianza en el siguiente turno. */
	private static void embajada(int sal) {
		Grafo g = grafo(sal);
		Politica p = new Politica(sal);
		boolean paz = false;
		boolean alianza = false;
		for (int t = 1; t <= TURNOS && !(paz && alianza); t++) {
			p.turno(g, s -> {
			});
			Politica.Par par = null;
			Politica.Carta esperada = null;
			if (!paz && !p.guerras().isEmpty()) {
				par = p.guerras().keySet().iterator().next();
				esperada = Politica.Carta.paz;
			} else if (!alianza) {
				for (long a : p.reinosVivos(g)) {
					for (long b : p.vecinosDe(g, a)) {
						if (p.carta(a, b) == Politica.Carta.alianza) {
							par = Politica.Par.de(a, b);
							break;
						}
					}
					if (par != null) {
						break;
					}
				}
				esperada = Politica.Carta.alianza;
			}
			if (par == null) {
				continue;
			}
			if (p.embajada(par.a(), par.b(), "hulio") != esperada) {
				falla("sal " + sal + ": la carta no trae " + esperada);
			}
			Politica.Par x = par;
			Suceso.Tipo tipo = esperada == Politica.Carta.paz ? Suceso.Tipo.paz : Suceso.Tipo.alianza;
			List<Suceso> sucesos = new ArrayList<>();
			p.turno(g, sucesos::add);
			boolean ok = sucesos.stream().anyMatch(s -> s.tipo() == tipo && "hulio".equals(s.ayuda())
					&& Politica.Par.de(s.reinoA(), s.reinoB()).equals(x));
			if (!ok) {
				falla("sal " + sal + ": la carta entregada no ha traído " + tipo + ": " + sucesos);
			}
			paz |= tipo == Suceso.Tipo.paz;
			alianza |= tipo == Suceso.Tipo.alianza;
		}
		if (!paz || !alianza) {
			falla("sal " + sal + ": no se han podido probar las cartas de paz y alianza");
		}
		System.out.println("sal " + sal + ": embajadas OK (paz y alianza)");
	}

	/**
	 * v0.7.1 La corte: si un jugador mata al rey, en la misma llamada hay rey nuevo vivo (sucesión, herencia o crisis)
	 * y todo queda en la crónica; matar a un muerto no hace nada.
	 */
	private static void asesinato(int sal) {
		Grafo g = grafo(sal);
		Politica p = new Politica(sal);
		for (int t = 1; t <= 10 * Politica.TURNOS_POR_ANIO; t++) {
			p.turno(g, s -> {
			});
		}
		int muertos = 0;
		for (long reino : p.reinosVivos(g)) {
			if (muertos == 4) {
				break;
			}
			es.hulillo.destinos.politica.Persona rey = p.gente().rey(reino);
			List<Suceso> sucesos = p.asesinato(rey.id, "hulio");
			es.hulillo.destinos.politica.Persona nuevo = p.gente().rey(reino);
			if (sucesos.isEmpty() || sucesos.get(0).tipo() != Suceso.Tipo.asesinato || !"hulio".equals(sucesos.get(0).ayuda())) {
				falla("sal " + sal + ": matar al rey " + rey + " no deja el asesinato: " + sucesos);
			}
			if (rey.vivo() || nuevo == null || !nuevo.vivo() || nuevo.id == rey.id) {
				falla("sal " + sal + ": tras matar a " + rey + " el reino " + reino + " no tiene rey nuevo vivo (" + nuevo + ")");
			}
			boolean sube = sucesos.stream().anyMatch(s -> s.tipo() == Suceso.Tipo.sucesion || s.tipo() == Suceso.Tipo.herencia
					|| s.tipo() == Suceso.Tipo.crisis);
			if (!sube || !p.cronica().containsAll(sucesos)) {
				falla("sal " + sal + ": la sucesión tras el asesinato no sale o no está en la crónica: " + sucesos);
			}
			if (!p.asesinato(rey.id, "hulio").isEmpty()) {
				falla("sal " + sal + ": se puede matar dos veces a " + rey);
			}
			muertos++;
		}
		// y el reino sigue funcionando: unos turnos más sin reinos sin rey
		for (int t = 1; t <= Politica.TURNOS_POR_ANIO; t++) {
			p.turno(g, s -> {
			});
			for (long reino : p.reinosVivos(g)) {
				es.hulillo.destinos.politica.Persona rey = p.gente().rey(reino);
				if (rey == null || !rey.vivo()) {
					falla("sal " + sal + ": tras los asesinatos el reino " + reino + " se queda sin rey");
				}
			}
		}
		if (muertos == 0) {
			falla("sal " + sal + ": no había reyes que matar");
		}
		System.out.println("sal " + sal + ": " + muertos + " reyes asesinados, todos con sucesor en el acto");
	}

	/** v0.7.1 La corte: la rutina cubre todo el día y todos los cargos, y la skin sale entera y siempre igual. */
	private static void corte() {
		for (long t = 0; t < 24000; t += 250) {
			RutinaCorte.Franja f = RutinaCorte.Franja.de(t);
			if (RutinaCorte.Franja.de(t + 24000 * 7) != f || RutinaCorte.Franja.de(t - 24000) != f) {
				falla("la franja de la hora " + t + " cambia con el día");
			}
			for (RutinaCorte.Cargo c : RutinaCorte.Cargo.values()) {
				RutinaCorte.Sitio s = RutinaCorte.sitio(c, f);
				if (!RutinaCorte.delCastillo(s) || RutinaCorte.plazas(s) < 1 || RutinaCorte.punto(s, 7).length != 3) {
					falla("el sitio de " + c + " a las " + t + " no vale: " + s);
				}
			}
			if (RutinaCorte.delCastillo(RutinaCorte.sinCastillo(f, true)) || RutinaCorte.delCastillo(RutinaCorte.sinCastillo(f, false))) {
				falla("sin castillo, la corte acaba en el castillo");
			}
		}
		if (RutinaCorte.Franja.de(1000) != RutinaCorte.Franja.manana || RutinaCorte.Franja.de(6500) != RutinaCorte.Franja.comida
				|| RutinaCorte.Franja.de(9000) != RutinaCorte.Franja.tarde || RutinaCorte.Franja.de(18000) != RutinaCorte.Franja.noche) {
			falla("las franjas del día no caen donde deben");
		}
		if (RutinaCorte.sitio(RutinaCorte.Cargo.rey, RutinaCorte.Franja.manana) != RutinaCorte.Sitio.trono) {
			falla("por la mañana el rey no está en el trono");
		}
		// la comida junta a los cinco: tiene que haber sitio para todos
		if (RutinaCorte.plazas(RutinaCorte.Sitio.comedor) < RutinaCorte.Cargo.values().length) {
			falla("en el comedor no caben todos");
		}
		java.util.Set<Integer> distintas = new java.util.HashSet<>();
		java.util.SplittableRandom r = new java.util.SplittableRandom(71);
		for (int cargo = 0; cargo <= AspectoPersona.NOBLE; cargo++) {
			for (int i = 0; i < 40; i++) {
				int a = AspectoPersona.arma(r.nextInt(8), r.nextInt(8), r.nextInt(4), r.nextInt(16), cargo, r.nextBoolean(), r.nextInt(6) == 0,
						r.nextBoolean(), r.nextInt(5) == 0, r.nextInt(256));
				int[] px = AspectoPersona.pixeles(a);
				if (!java.util.Arrays.equals(px, AspectoPersona.pixeles(a))) {
					falla("la skin " + a + " no sale siempre igual");
				}
				// la base de la cabeza, el cuerpo, los brazos y las piernas no puede tener agujeros
				for (int[] caja : new int[][] {{0, 0, 8, 8, 8}, {16, 16, 8, 12, 4}, {40, 16, 4, 12, 4}, {32, 48, 4, 12, 4}, {0, 16, 4, 12, 4},
						{16, 48, 4, 12, 4}}) {
					int ox = caja[0], oy = caja[1], w = caja[2], h = caja[3], d = caja[4];
					for (int y = oy + d; y < oy + d + h; y++) {
						for (int x = ox; x < ox + 2 * (w + d); x++) {
							if ((px[y * 64 + x] >>> 24) != 0xFF) {
								falla("la skin " + a + " tiene un agujero en " + x + "," + y);
							}
						}
					}
				}
				distintas.add(java.util.Arrays.hashCode(px));
			}
		}
		if (distintas.size() < 200) {
			falla("las skins se parecen demasiado: " + distintas.size() + " distintas de 240");
		}
		System.out.println("corte: rutina completa y " + distintas.size() + " skins distintas");
	}

	private static int cuenta(Politica p, Grafo g, long reino) {
		int n = 0;
		for (Grafo.Ducado d : g.ducados().values()) {
			if (p.dueno(d) == reino) {
				n++;
			}
		}
		return n;
	}

	private static int reinosVivos(Politica p, Grafo g) {
		return p.reinosVivos(g).size();
	}

	private static void falla(String motivo) {
		System.err.println("FALLO: " + motivo);
		System.exit(1);
	}
}
