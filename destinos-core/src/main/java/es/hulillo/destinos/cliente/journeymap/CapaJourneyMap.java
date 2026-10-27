package es.hulillo.destinos.cliente.journeymap;

import es.hulillo.destinos.DestinosCore;
import es.hulillo.destinos.MapaTerreno;
import es.hulillo.destinos.PaquetePolitica;
import es.hulillo.destinos.PaqueteTerritorio;
import es.hulillo.destinos.cliente.Colores;
import es.hulillo.destinos.cliente.TerritoriosCliente;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygonWithHoles;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.util.PolygonHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.level.Level;

/**
 * Cada ducado es un polígono de JourneyMap relleno con el color de su reino y borde fino. Encima, cada reino
 * lleva su contorno sin relleno con un borde gordo y oscuro, para que se vea bien dónde cambia de reino.
 * Un ducado o reino puede caer en varias zonas, así que su área se va juntando según llegan y su polígono se rehace.
 */
final class CapaJourneyMap implements TerritoriosCliente.Oyente {
	private static final String GRUPO = "Destinos: reinos";
	private static final float OPACIDAD_RELLENO = 0.22F;
	private static final float OPACIDAD_AGUAS = 0.11F;
	private static final float OPACIDAD_BORDE = 0.6F;
	private static final float GROSOR_BORDE = 1.0F;
	private static final float OPACIDAD_REINO = 0.95F;
	private static final float GROSOR_REINO = 3.5F;
	private static final float OPACIDAD_ORO = 0.35F;
	/** Altura a la que se dejan los polígonos (JourneyMap la usa al ver cuevas por capas). */
	private static final int Y = 64;

	private final IClientAPI api;
	private final Map<PaqueteTerritorio.Ducado, Area> areas = new HashMap<>();
	private final Map<PaqueteTerritorio.Ducado, List<PolygonOverlay>> capas = new HashMap<>();
	/**
	 * Polígonos por rehacer. Al entrar o tras una conquista llegan varias zonas seguidas y un mismo reino sale en
	 * todas: se rehace una sola vez al final del tick, no una por zona.
	 */
	private final Set<PaqueteTerritorio.Ducado> pendientes = new LinkedHashSet<>();
	/** Frentes de guerra: una línea roja sobre la frontera con una X en cada tramo, como una trinchera. */
	private final List<PolygonOverlay> frentes = new ArrayList<>();
	private boolean frentesSucios;
	/** Coronas de las capitales: solo cambian con el estado político. */
	private final List<PolygonOverlay> coronas = new ArrayList<>();
	private boolean coronasSucias;

	CapaJourneyMap(IClientAPI api) {
		this.api = api;
		ClientTickEvents.END_CLIENT_TICK.register(cliente -> rehaz());
	}

	@Override
	public void zonaNueva(PaqueteTerritorio zona, PaqueteTerritorio anterior) {
		Set<PaqueteTerritorio.Ducado> tocados = new HashSet<>();
		if (anterior != null) {
			// la zona ha cambiado (conquistas): se quita lo que aportaba antes
			conReinos(trozos(anterior)).forEach((ducado, trozo) -> {
				Area a = areas.get(ducado);
				if (a != null) {
					a.subtract(trozo);
					tocados.add(ducado);
				}
			});
		}
		conReinos(trozos(zona)).forEach((ducado, trozo) -> {
			areas.computeIfAbsent(ducado, k -> new Area()).add(trozo);
			tocados.add(ducado);
		});
		pendientes.addAll(tocados);
		// sin guerras (ni frentes viejos que quitar) explorar no obliga a rehacer los frentes
		frentesSucios |= TerritoriosCliente.hayGuerras() || !frentes.isEmpty();
	}

	/**
	 * Turno o guerras nuevas: cambian los dorados (recientes) y los frentes. Solo los ducados que alguna vez cambiaron
	 * de dueño pueden ganar o perder el dorado; el resto (casi todos) no se vuelve a pintar.
	 */
	@Override
	public void politica() {
		areas.keySet().stream().filter(d -> d.cambio() >= 0).forEach(pendientes::add);
		frentesSucios = true;
		coronasSucias = true;
	}

	@Override
	public void visibilidad(boolean visible) {
		if (visible) {
			pendientes.addAll(areas.keySet());
			frentesSucios = true;
			coronasSucias = true;
		} else {
			quitaTodo();
		}
	}

	private void rehaz() {
		// apagado: las áreas se siguen juntando, pero no se pinta nada hasta que se encienda
		if (!TerritoriosCliente.visible() || (pendientes.isEmpty() && !frentesSucios && !coronasSucias)) {
			return;
		}
		// los reinos al final, para que su borde quede por encima de los ducados
		pendientes.stream().filter(d -> !esReino(d)).forEach(this::redibuja);
		pendientes.stream().filter(CapaJourneyMap::esReino).forEach(this::redibuja);
		pendientes.clear();
		if (frentesSucios) {
			frentesSucios = false;
			redibujaFrentes();
		}
		if (coronasSucias) {
			coronasSucias = false;
			redibujaCoronas();
		}
	}

	/** Forma de la corona en bloques alrededor de la capital (z hacia el sur): base y tres puntas. */
	private static final double[] CORONA = {-64, 32, -64, -36, -32, 0, 0, -48, 32, 0, 64, -36, 64, 32};

	/** Una corona dorada sobre cada capital (gris si hoy la tiene otro reino), por encima de todo lo demás. */
	private void redibujaCoronas() {
		coronas.forEach(api::remove);
		coronas.clear();
		for (PaquetePolitica.Capital c : TerritoriosCliente.capitales()) {
			Path2D forma = new Path2D.Double();
			forma.moveTo(c.x() + CORONA[0], c.z() + CORONA[1]);
			for (int k = 2; k < CORONA.length; k += 2) {
				forma.lineTo(c.x() + CORONA[k], c.z() + CORONA[k + 1]);
			}
			forma.closePath();
			ShapeProperties estilo = new ShapeProperties()
					.setFillColor(c.perdida() ? Colores.GRIS : Colores.ORO)
					.setFillOpacity(0.95F)
					.setStrokeColor(0x000000)
					.setStrokeOpacity(1.0F)
					.setStrokeWidth(2.0F);
			muestra(new Area(forma), estilo, coronas, "la corona de una capital");
		}
	}

	/**
	 * Recorre las fronteras este y sur de cada casilla recibida y, donde los dos reinos están en guerra, pone un
	 * tramo de línea y una X. Todo va en un solo trazado: Area lo junta de una vez (sin sumar tramo a tramo).
	 */
	private void redibujaFrentes() {
		frentes.forEach(api::remove);
		frentes.clear();
		if (!TerritoriosCliente.hayGuerras()) {
			return;
		}
		int n = MapaTerreno.NUCLEO;
		int c = MapaTerreno.CASILLA;
		Path2D trazado = new Path2D.Double(Path2D.WIND_NON_ZERO);
		boolean hay = false;
		for (PaqueteTerritorio zona : TerritoriosCliente.zonasRecibidas()) {
			for (int j = 0; j < n; j++) {
				for (int i = 0; i < n; i++) {
					PaqueteTerritorio.Ducado d = zona.ducado(i, j);
					if (d == null || !TerritoriosCliente.enGuerra(d)) {
						continue;
					}
					int tx = zona.wx() * n + i;
					int tz = zona.wz() * n + j;
					if (TerritoriosCliente.enGuerra(d, TerritoriosCliente.ducado(tx + 1, tz))) {
						tramo(trazado, (tx + 1) * c, tz * c + c / 2.0, true);
						hay = true;
					}
					if (TerritoriosCliente.enGuerra(d, TerritoriosCliente.ducado(tx, tz + 1))) {
						tramo(trazado, tx * c + c / 2.0, (tz + 1) * c, false);
						hay = true;
					}
				}
			}
		}
		if (!hay) {
			return;
		}
		ShapeProperties estilo = new ShapeProperties()
				.setFillColor(Colores.ROJO)
				.setFillOpacity(0.85F)
				.setStrokeColor(0x000000)
				.setStrokeOpacity(0.9F)
				.setStrokeWidth(2.0F);
		muestra(new Area(trazado), estilo, frentes, "un frente de guerra");
	}

	/** Un tramo de frente de una casilla centrado en (x, z): la línea y su X encima. */
	private static void tramo(Path2D p, double x, double z, boolean vertical) {
		double medio = MapaTerreno.CASILLA / 2.0;
		double g = 3;
		if (vertical) {
			cuadro(p, x - g, z - medio, x + g, z - medio, x + g, z + medio, x - g, z + medio);
		} else {
			cuadro(p, x - medio, z - g, x + medio, z - g, x + medio, z + g, x - medio, z + g);
		}
		double r = 14;
		double t = 4;
		cuadro(p, x - r, z - r + t, x - r + t, z - r, x + r, z + r - t, x + r - t, z + r);
		cuadro(p, x + r - t, z - r, x + r, z - r + t, x - r + t, z + r, x - r, z + r - t);
	}

	/** Cuadrilátero siempre en el mismo sentido de giro, para que los solapes se junten y no se resten. */
	private static void cuadro(Path2D p, double... xz) {
		double area = 0;
		for (int k = 0; k < 8; k += 2) {
			int s = (k + 2) % 8;
			area += xz[k] * xz[s + 1] - xz[s] * xz[k + 1];
		}
		if (area < 0) {
			double[] r = new double[8];
			for (int k = 0; k < 4; k++) {
				r[2 * k] = xz[2 * (3 - k)];
				r[2 * k + 1] = xz[2 * (3 - k) + 1];
			}
			xz = r;
		}
		p.moveTo(xz[0], xz[1]);
		for (int k = 2; k < 8; k += 2) {
			p.lineTo(xz[k], xz[k + 1]);
		}
		p.closePath();
	}

	/**
	 * Añade a los trozos de ducado el de cada reino entero (tierra y aguas). El reino se guarda como un Ducado
	 * de mentira en la celda (MIN_VALUE, MIN_VALUE). Las tierras salvajes no son un reino: no llevan contorno.
	 */
	private static Map<PaqueteTerritorio.Ducado, Area> conReinos(Map<PaqueteTerritorio.Ducado, Area> trozos) {
		Map<PaqueteTerritorio.Ducado, Area> res = new HashMap<>(trozos);
		trozos.forEach((d, trozo) -> {
			if (!d.salvaje()) {
				PaqueteTerritorio.Ducado reino = new PaqueteTerritorio.Ducado(Integer.MIN_VALUE, Integer.MIN_VALUE,
						d.kx(), d.kz(), false, false, false, d.color(), -1);
				res.computeIfAbsent(reino, k -> new Area()).add(trozo);
			}
		});
		return res;
	}

	private static boolean esReino(PaqueteTerritorio.Ducado d) {
		return d.dcx() == Integer.MIN_VALUE && d.dcz() == Integer.MIN_VALUE;
	}

	@Override
	public void borrado() {
		quitaTodo();
		areas.clear();
	}

	/** Área de cada ducado (tierra y aguas territoriales) dentro de la zona, juntando las casillas seguidas de cada fila. */
	private static Map<PaqueteTerritorio.Ducado, Area> trozos(PaqueteTerritorio zona) {
		// los rectángulos de cada ducado se juntan en un trazado y el Area se hace una sola vez al final: sumar
		// Areas de una en una cuesta cada vez más (y esto va en el hilo del juego)
		Map<PaqueteTerritorio.Ducado, Path2D.Double> trazados = new HashMap<>();
		int n = MapaTerreno.NUCLEO;
		int c = MapaTerreno.CASILLA;
		for (int j = 0; j < n; j++) {
			int i = 0;
			while (i < n) {
				PaqueteTerritorio.Ducado d = zona.ducado(i, j);
				int fin = i + 1;
				while (fin < n && zona.ducado(fin, j) == d) {
					fin++;
				}
				if (!d.deNadie()) {
					int x = (zona.wx() * n + i) * c;
					int z = (zona.wz() * n + j) * c;
					trazados.computeIfAbsent(d, k -> new Path2D.Double()).append(new Rectangle(x, z, (fin - i) * c, c), false);
				}
				i = fin;
			}
		}
		Map<PaqueteTerritorio.Ducado, Area> res = new HashMap<>();
		trazados.forEach((d, trazado) -> res.put(d, new Area(trazado)));
		return res;
	}

	private void redibuja(PaqueteTerritorio.Ducado ducado) {
		List<PolygonOverlay> viejas = capas.remove(ducado);
		if (viejas != null) {
			viejas.forEach(api::remove);
		}
		ShapeProperties estilo;
		if (esReino(ducado)) {
			// el frente de guerra va aparte (redibujaFrentes): el contorno del reino sigue con su color
			estilo = new ShapeProperties()
					.setFillColor(ducado.color())
					.setFillOpacity(0)
					.setStrokeColor(Colores.oscuro(Colores.oscuro(ducado.color())))
					.setStrokeOpacity(OPACIDAD_REINO)
					.setStrokeWidth(GROSOR_REINO);
		} else if (TerritoriosCliente.reciente(ducado)) {
			// acaba de cambiar de dueño: relleno y borde dorados
			estilo = new ShapeProperties()
					.setFillColor(Colores.ORO)
					.setFillOpacity(OPACIDAD_ORO)
					.setStrokeColor(Colores.ORO)
					.setStrokeOpacity(OPACIDAD_REINO)
					.setStrokeWidth(GROSOR_REINO - 1);
		} else {
			estilo = new ShapeProperties()
					.setFillColor(ducado.color())
					.setFillOpacity(ducado.oceano() ? OPACIDAD_AGUAS : OPACIDAD_RELLENO)
					.setStrokeColor(Colores.oscuro(ducado.color()))
					.setStrokeOpacity(OPACIDAD_BORDE)
					.setStrokeWidth(GROSOR_BORDE);
		}
		Area area = areas.get(ducado);
		if (area == null || area.isEmpty()) {
			areas.remove(ducado);
			return;
		}
		List<PolygonOverlay> nuevas = new ArrayList<>();
		muestra(area, estilo, nuevas, "un ducado");
		capas.put(ducado, nuevas);
	}

	/**
	 * Pone en JourneyMap los polígonos del área con ese estilo y los apunta en puestas (para quitarlos luego). que:
	 * qué se estaba pintando, para el aviso si JourneyMap no lo acepta.
	 */
	private void muestra(Area area, ShapeProperties estilo, List<PolygonOverlay> puestas, String que) {
		for (MapPolygonWithHoles poligono : PolygonHelper.createPolygonFromArea(area, Y)) {
			PolygonOverlay capa = new PolygonOverlay(DestinosCore.MOD_ID, Level.OVERWORLD, estilo, poligono);
			capa.setOverlayGroupName(GRUPO);
			try {
				api.show(capa);
				puestas.add(capa);
			} catch (Exception e) {
				DestinosCore.LOG.warn("Destinos Core: JourneyMap no ha aceptado {}", que, e);
			}
		}
	}

	/** Quita del mapa todo lo pintado (las áreas se quedan). */
	private void quitaTodo() {
		api.removeAll(DestinosCore.MOD_ID);
		capas.clear();
		frentes.clear();
		coronas.clear();
		pendientes.clear();
	}
}
