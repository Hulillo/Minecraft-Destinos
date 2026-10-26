import es.hulillo.destinos.Geo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Compara Geo.java con los casos de herramientas/casos.py. Uso: java PruebaGeo casos.txt */
public final class PruebaGeo {
	public static void main(String[] args) throws Exception {
		List<String> lineas = Files.readAllLines(Path.of(args[0]));
		for (String l : lineas) {
			String[] t = l.trim().split(" ");
			int[] v = new int[t.length];
			for (int i = 0; i < t.length; i++) {
				v[i] = Integer.parseInt(t[i]);
			}
			int[] g = Geo.territorioGeometrico(v[1], v[2], v[0]);
			for (int i = 0; i < 5; i++) {
				if (g[i] != v[3 + i] || Geo.colorReino(g[2], g[3], v[0]) != v[8]
						|| Geo.rasgoReino(g[2], g[3], v[0]) != v[9]) {
					System.err.println("DISTINTO en sal=" + v[0] + " x=" + v[1] + " z=" + v[2]);
					System.exit(1);
				}
			}
		}
		System.out.println("OK: Geo.java y el simulador coinciden en " + lineas.size() + " puntos");
	}
}
