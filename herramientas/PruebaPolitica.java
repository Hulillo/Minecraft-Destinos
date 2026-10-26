import es.hulillo.destinos.Geo;
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
		System.out.println("OK: política reproducible, reino protegido a salvo y mapa vivo sin caos");
	}

	private static Grafo grafo(int sal) {
		Grafo g = new Grafo();
		for (int cx = 0; cx < LADO; cx++) {
			for (int cz = 0; cz < LADO; cz++) {
				int[] c = Geo.centro(cx, cz, Geo.S_DUCADO, sal);
				Geo.Reino r = Geo.reinoDeDucado(cx, cz, c[0], c[1], sal);
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
			int[] k = Geo.centro(Grafo.x(d.reinoBase()), Grafo.z(d.reinoBase()), Geo.S_REINO, sal + Geo.K_REINO);
			int[] c = Geo.centro(d.dcx(), d.dcz(), Geo.S_DUCADO, sal);
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
		return String.join("\n", registro) + p.cambios();
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
		java.util.Set<Long> vivos = new java.util.HashSet<>();
		for (Grafo.Ducado d : g.ducados().values()) {
			long r = p.dueno(d);
			if (r != Politica.SALVAJE) {
				vivos.add(r);
			}
		}
		return vivos.size();
	}

	private static void falla(String motivo) {
		System.err.println("FALLO: " + motivo);
		System.exit(1);
	}
}
