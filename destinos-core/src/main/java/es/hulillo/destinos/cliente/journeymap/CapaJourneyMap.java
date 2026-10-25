package es.hulillo.destinos.cliente.journeymap;

import es.hulillo.destinos.DestinosCore;
import es.hulillo.destinos.MapaTerreno;
import es.hulillo.destinos.PaqueteTerritorio;
import es.hulillo.destinos.cliente.TerritoriosCliente;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygonWithHoles;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.util.PolygonHelper;
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
	/** Altura a la que se dejan los polígonos (JourneyMap la usa al ver cuevas por capas). */
	private static final int Y = 64;

	private final IClientAPI api;
	private final Map<PaqueteTerritorio.Ducado, Area> areas = new HashMap<>();
	private final Map<PaqueteTerritorio.Ducado, List<PolygonOverlay>> capas = new HashMap<>();

	CapaJourneyMap(IClientAPI api) {
		this.api = api;
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
		// los reinos al final, para que su borde quede por encima de los ducados
		tocados.stream().filter(d -> !esReino(d)).forEach(this::redibuja);
		tocados.stream().filter(CapaJourneyMap::esReino).forEach(this::redibuja);
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
						d.kx(), d.kz(), false, false, false, d.color());
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
		api.removeAll(DestinosCore.MOD_ID);
		areas.clear();
		capas.clear();
	}

	/** Área de cada ducado (tierra y aguas territoriales) dentro de la zona, juntando las casillas seguidas de cada fila. */
	private static Map<PaqueteTerritorio.Ducado, Area> trozos(PaqueteTerritorio zona) {
		Map<PaqueteTerritorio.Ducado, Area> res = new HashMap<>();
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
					res.computeIfAbsent(d, k -> new Area()).add(new Area(new Rectangle(x, z, (fin - i) * c, c)));
				}
				i = fin;
			}
		}
		return res;
	}

	private void redibuja(PaqueteTerritorio.Ducado ducado) {
		List<PolygonOverlay> viejas = capas.remove(ducado);
		if (viejas != null) {
			viejas.forEach(api::remove);
		}
		ShapeProperties estilo = esReino(ducado)
				? new ShapeProperties()
						.setFillColor(ducado.color())
						.setFillOpacity(0)
						.setStrokeColor(oscuro(oscuro(ducado.color())))
						.setStrokeOpacity(OPACIDAD_REINO)
						.setStrokeWidth(GROSOR_REINO)
				: new ShapeProperties()
						.setFillColor(ducado.color())
						.setFillOpacity(ducado.oceano() ? OPACIDAD_AGUAS : OPACIDAD_RELLENO)
						.setStrokeColor(oscuro(ducado.color()))
						.setStrokeOpacity(OPACIDAD_BORDE)
						.setStrokeWidth(GROSOR_BORDE);
		List<PolygonOverlay> nuevas = new ArrayList<>();
		if (areas.get(ducado).isEmpty()) {
			areas.remove(ducado);
			return;
		}
		for (MapPolygonWithHoles poligono : PolygonHelper.createPolygonFromArea(areas.get(ducado), Y)) {
			PolygonOverlay capa = new PolygonOverlay(DestinosCore.MOD_ID, Level.OVERWORLD, estilo, poligono);
			capa.setOverlayGroupName(GRUPO);
			try {
				api.show(capa);
				nuevas.add(capa);
			} catch (Exception e) {
				DestinosCore.LOG.warn("Destinos Core: JourneyMap no ha aceptado un ducado", e);
			}
		}
		capas.put(ducado, nuevas);
	}

	static int oscuro(int rgb) {
		int r = (rgb >> 16 & 0xff) * 3 / 5;
		int g = (rgb >> 8 & 0xff) * 3 / 5;
		int b = (rgb & 0xff) * 3 / 5;
		return r << 16 | g << 8 | b;
	}
}
