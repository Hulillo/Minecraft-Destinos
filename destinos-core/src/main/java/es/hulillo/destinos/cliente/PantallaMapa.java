package es.hulillo.destinos.cliente;

import es.hulillo.destinos.Geo;
import es.hulillo.destinos.MapaTerreno;
import es.hulillo.destinos.PaqueteFichas;
import es.hulillo.destinos.PaquetePedirMapa;
import es.hulillo.destinos.PaquetePolitica;
import es.hulillo.destinos.PaqueteTerritorio;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * El mapa político a pantalla completa (tecla K): todos los ducados que conoce el cliente pintados con el color de
 * su reino, fronteras, frentes de guerra y capitales. Se arrastra para moverse y la rueda acerca y aleja. Al pasar
 * el ratón sale el ducado y su reino, y a la derecha la ficha del reino (el que tienes debajo, o el que has fijado
 * con un clic).
 */
public final class PantallaMapa extends Screen {
	private static final int PERGAMINO = 0xFFE3D3AE;
	private static final int NIEBLA = 0xFFCDBB92;
	private static final int MAR = 0xFF8FA9A1;
	private static final int SALVAJE = 0xFFA39D8C;
	private static final int ORO = 0xFFE8B83A;
	private static final int TINTA = 0xFF2B2015;
	private static final int TINTA_SUAVE = 0xFF6B5640;
	private static final int MARCO = 0xFF5A3D22;
	private static final int FONDO = 0xF0201A13;
	private static final int PANEL = 0xF02A231A;
	private static final int TEXTO = 0xFFEFE4C8;
	private static final int TEXTO_SUAVE = 0xFFB9AB8A;
	private static final int FRENTE = 0xFFC0281F;
	private static final int COSTA = 0xFF4A3D2A;
	private static final int FRONTERA = 0xFF2B2015;
	private static final int LINDE = 0x552B2015;
	private static final int CASILLA = MapaTerreno.CASILLA;
	private static final int NUCLEO = MapaTerreno.NUCLEO;
	/** Píxeles de pantalla por bloque: de 1 px por casilla a 32 px por casilla. */
	private static final double ESCALA_MIN = 1.0 / CASILLA;
	private static final double ESCALA_MAX = 0.5;
	/** Cada cuánto se piden más zonas al servidor mientras el mapa está abierto, y cada cuánto las fichas. */
	private static final int PIDE_ZONAS = 40;
	private static final int PIDE_FICHAS = 200;

	/** Lo que se queda de una vista a otra mientras dura la conexión. */
	private static double vistaX = Double.NaN;
	private static double vistaZ;
	private static double escala = 1.0 / 12;
	private static long fijado = Long.MIN_VALUE;

	private final KeyMapping tecla;
	/** Cómo se pinta cada zona (se rehace si llegan zonas nuevas, porque cambian las fronteras de los bordes). */
	private final Map<PaqueteTerritorio, Pintura> pinturas = new IdentityHashMap<>();
	private final Map<Long, double[]> centros = new HashMap<>();
	private int zonasPintadas = -1;
	private int turnoPintado = Integer.MIN_VALUE;
	private int ticks;
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private int panelX;
	private boolean arrastra;
	private double arrastrado;

	/** Una zona ya preparada: tiras de casillas del mismo color y trozos de frontera. */
	private record Pintura(int ox, int oz, int[] tiras, int[] bordes) {
	}

	/** Al desconectarse: la próxima vez el mapa se abre otra vez sobre el jugador. */
	static void olvida() {
		vistaX = Double.NaN;
		fijado = Long.MIN_VALUE;
	}

	public PantallaMapa(KeyMapping tecla) {
		super(Component.translatableWithFallback("destinos.mapa.titulo", "Mapa político"));
		this.tecla = tecla;
	}

	@Override
	protected void init() {
		Minecraft mc = Minecraft.getInstance();
		if (Double.isNaN(vistaX) && mc.player != null) {
			vistaX = mc.player.getX();
			vistaZ = mc.player.getZ();
		}
		int ancho = Math.min(170, Math.max(120, width / 4));
		x0 = 8;
		y0 = 30;
		panelX = width - ancho - 8;
		x1 = panelX - 6;
		y1 = height - 22;
		addRenderableWidget(Button.builder(Component.translatableWithFallback("destinos.mapa.cerrar", "Cerrar"), b -> onClose())
				.bounds(width - 8 - 56, 6, 56, 18).build());
		addRenderableWidget(Button.builder(textoCapa(), b -> {
			TerritoriosCliente.alterna();
			b.setMessage(textoCapa());
		}).bounds(width - 8 - 56 - 4 - 150, 6, 150, 18).build());
		ClientPlayNetworking.send(new PaquetePedirMapa(true));
	}

	private static Component textoCapa() {
		return Component.translatableWithFallback(TerritoriosCliente.visible() ? "destinos.mapa.capa_si" : "destinos.mapa.capa_no",
				TerritoriosCliente.visible() ? "Colores en tus mapas: sí" : "Colores en tus mapas: no");
	}

	@Override
	public void tick() {
		ticks++;
		if (ticks % PIDE_ZONAS == 0) {
			ClientPlayNetworking.send(new PaquetePedirMapa(ticks % PIDE_FICHAS == 0));
		}
	}

	// ------------------------------------------------------------------ coordenadas

	private double cx() {
		return (x0 + x1) / 2.0;
	}

	private double cy() {
		return (y0 + y1) / 2.0;
	}

	private int pantallaX(double bloque) {
		return (int) Math.floor(cx() + (bloque - vistaX) * escala);
	}

	private int pantallaY(double bloque) {
		return (int) Math.floor(cy() + (bloque - vistaZ) * escala);
	}

	private double bloqueX(double px) {
		return vistaX + (px - cx()) / escala;
	}

	private double bloqueZ(double py) {
		return vistaZ + (py - cy()) / escala;
	}

	private boolean enMapa(double mx, double my) {
		return mx >= x0 && mx < x1 && my >= y0 && my < y1;
	}

	private PaqueteTerritorio.Ducado bajo(double mx, double my) {
		if (!enMapa(mx, my)) {
			return null;
		}
		return TerritoriosCliente.ducado((int) Math.floor(bloqueX(mx) / CASILLA), (int) Math.floor(bloqueZ(my) / CASILLA));
	}

	// ------------------------------------------------------------------ ratón y teclas

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doble) {
		if (super.mouseClicked(e, doble)) {
			return true;
		}
		if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && enMapa(e.x(), e.y())) {
			arrastra = true;
			arrastrado = 0;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (arrastra) {
			vistaX -= dx / escala;
			vistaZ -= dy / escala;
			arrastrado += Math.abs(dx) + Math.abs(dy);
			return true;
		}
		return super.mouseDragged(e, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (arrastra && e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			arrastra = false;
			if (arrastrado < 3) {
				// un clic sin arrastrar: fija la ficha del reino de ahí (o la suelta en el mar o en tierra de nadie)
				PaqueteTerritorio.Ducado d = bajo(e.x(), e.y());
				fijado = d == null || d.salvaje() || d.deNadie() ? Long.MIN_VALUE : Geo.clave(d.kx(), d.kz());
			}
			return true;
		}
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (!enMapa(mx, my) || sy == 0) {
			return false;
		}
		// el bloque que hay bajo el ratón se queda ahí al acercar o alejar
		double bx = bloqueX(mx);
		double bz = bloqueZ(my);
		escala = Math.max(ESCALA_MIN, Math.min(ESCALA_MAX, escala * Math.pow(1.25, sy)));
		vistaX = bx - (mx - cx()) / escala;
		vistaZ = bz - (my - cy()) / escala;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (tecla.matches(e)) {
			onClose();
			return true;
		}
		if (e.key() == GLFW.GLFW_KEY_SPACE && minecraft != null && minecraft.player != null) {
			vistaX = minecraft.player.getX();
			vistaZ = minecraft.player.getZ();
			return true;
		}
		return super.keyPressed(e);
	}

	// ------------------------------------------------------------------ pintar

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		g.fill(0, 0, width, height, FONDO);
		g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), 10, 11, ORO, true);
		if (FichasCliente.hay()) {
			g.drawString(font, Component.translatableWithFallback("destinos.mapa.anio", "Año %s · turno %s · %s reinos conocidos",
					FichasCliente.anio(), FichasCliente.turno(), FichasCliente.vivos()), 16 + font.width(title) + 12, 11, TEXTO_SUAVE, false);
		}
		g.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, MARCO);
		g.enableScissor(x0, y0, x1, y1);
		g.fill(x0, y0, x1, y1, NIEBLA);
		prepara();
		pintaZonas(g);
		pintaCapitales(g);
		pintaNombres(g);
		pintaJugador(g);
		g.disableScissor();
		if (TerritoriosCliente.vacio()) {
			Component c = Component.translatableWithFallback("destinos.mapa.vacio",
					"Aún no hay mapa: el servidor está calculando los territorios de alrededor…");
			g.drawString(font, c, (int) cx() - font.width(c) / 2, (int) cy(), TINTA, false);
		}
		leyenda(g);
		PaqueteTerritorio.Ducado d = bajo(mx, my);
		ficha(g, d);
		super.render(g, mx, my, pt);
		if (d != null && !arrastra) {
			g.nextStratum();
			pista(g, d, mx, my);
		}
	}

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
		// el fondo lo pone render (oscuro y sin desenfoque, para que el mapa se lea bien)
	}

	/** Rehace las pinturas si han llegado zonas o ha pasado un turno (cambian colores y fronteras). */
	private void prepara() {
		int n = TerritoriosCliente.zonas();
		int turno = FichasCliente.turno();
		if (n == zonasPintadas && turno == turnoPintado && pinturas.size() == n) {
			return;
		}
		zonasPintadas = n;
		turnoPintado = turno;
		pinturas.clear();
		centros.clear();
		for (PaqueteTerritorio z : TerritoriosCliente.zonasRecibidas()) {
			pinturas.put(z, pinta(z));
		}
	}

	private static int mezcla(int a, int b, double t) {
		int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int v = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int z = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return 0xFF000000 | r << 16 | v << 8 | z;
	}

	private static int color(PaqueteTerritorio.Ducado d) {
		int c;
		if (d.deNadie()) {
			return MAR;
		} else if (d.oceano()) {
			c = d.salvaje() ? MAR : mezcla(MAR, 0xFF000000 | d.color(), 0.3);
		} else if (d.salvaje()) {
			c = SALVAJE;
		} else {
			c = mezcla(0xFF000000 | d.color(), PERGAMINO, 0.22);
		}
		return TerritoriosCliente.reciente(d) ? mezcla(c, ORO, 0.45) : c;
	}

	/** Tipo de linde entre dos casillas vecinas: 0 nada, 1 ducado, 2 costa, 3 reino, 4 frente de guerra. */
	private static int linde(PaqueteTerritorio.Ducado a, PaqueteTerritorio.Ducado b) {
		if (a == null || b == null || (a.deNadie() && b.deNadie())) {
			return 0;
		}
		if (TerritoriosCliente.enGuerra(a, b)) {
			return 4;
		}
		if (a.oceano() != b.oceano()) {
			return 2;
		}
		if (!a.mismoReino(b)) {
			return a.oceano() ? 1 : 3;
		}
		return a.mismoDucado(b) || a.oceano() ? 0 : 1;
	}

	private Pintura pinta(PaqueteTerritorio z) {
		int ox = z.wx() * NUCLEO;
		int oz = z.wz() * NUCLEO;
		List<Integer> tiras = new ArrayList<>();
		List<Integer> bordes = new ArrayList<>();
		for (int j = 0; j < NUCLEO; j++) {
			int desde = 0;
			int c = color(z.ducado(0, j));
			for (int i = 1; i <= NUCLEO; i++) {
				int ci = i < NUCLEO ? color(z.ducado(i, j)) : 0;
				if (i == NUCLEO || ci != c) {
					tiras.add(j);
					tiras.add(desde);
					tiras.add(i);
					tiras.add(c);
					desde = i;
					c = ci;
				}
			}
			for (int i = 0; i < NUCLEO; i++) {
				PaqueteTerritorio.Ducado d = z.ducado(i, j);
				if (!d.salvaje() && !d.oceano()) {
					double[] s = centros.computeIfAbsent(Geo.clave(d.kx(), d.kz()), k -> new double[3]);
					s[0] += ox + i + 0.5;
					s[1] += oz + j + 0.5;
					s[2]++;
				}
				// la linde de la izquierda y la de arriba (las otras las pone la casilla de al lado)
				PaqueteTerritorio.Ducado izq = i > 0 ? z.ducado(i - 1, j) : TerritoriosCliente.ducado(ox + i - 1, oz + j);
				PaqueteTerritorio.Ducado arr = j > 0 ? z.ducado(i, j - 1) : TerritoriosCliente.ducado(ox + i, oz + j - 1);
				int a = linde(d, izq);
				if (a > 0) {
					bordes.add(0);
					bordes.add(i);
					bordes.add(j);
					bordes.add(a);
				}
				int b = linde(d, arr);
				if (b > 0) {
					bordes.add(1);
					bordes.add(i);
					bordes.add(j);
					bordes.add(b);
				}
			}
		}
		return new Pintura(ox, oz, tiras.stream().mapToInt(Integer::intValue).toArray(), bordes.stream().mapToInt(Integer::intValue).toArray());
	}

	private void pintaZonas(GuiGraphics g) {
		double s = escala * CASILLA;
		for (Pintura p : pinturas.values()) {
			int zx0 = pantallaX((double) p.ox() * CASILLA);
			int zy0 = pantallaY((double) p.oz() * CASILLA);
			int zx1 = pantallaX((double) (p.ox() + NUCLEO) * CASILLA);
			int zy1 = pantallaY((double) (p.oz() + NUCLEO) * CASILLA);
			if (zx1 < x0 || zx0 > x1 || zy1 < y0 || zy0 > y1) {
				continue;
			}
			int[] t = p.tiras();
			for (int k = 0; k < t.length; k += 4) {
				int ya = pantallaY((double) (p.oz() + t[k]) * CASILLA);
				int yb = pantallaY((double) (p.oz() + t[k] + 1) * CASILLA);
				int xa = pantallaX((double) (p.ox() + t[k + 1]) * CASILLA);
				int xb = pantallaX((double) (p.ox() + t[k + 2]) * CASILLA);
				if (xb > xa && yb > ya) {
					g.fill(xa, ya, xb, yb, t[k + 3]);
				}
			}
			int[] b = p.bordes();
			for (int k = 0; k < b.length; k += 4) {
				int tipo = b[k + 3];
				if (tipo == 1 && s < 6) {
					continue;
				}
				int grosor = tipo >= 3 && s >= 12 ? 2 : 1;
				if (tipo == 4) {
					grosor++;
				}
				int color = switch (tipo) {
					case 1 -> LINDE;
					case 2 -> COSTA;
					case 4 -> FRENTE;
					default -> FRONTERA;
				};
				int i = p.ox() + b[k + 1];
				int j = p.oz() + b[k + 2];
				int xa = pantallaX((double) i * CASILLA);
				int ya = pantallaY((double) j * CASILLA);
				if (b[k] == 0) {
					int yb = pantallaY((double) (j + 1) * CASILLA);
					g.fill(xa - grosor / 2, ya, xa - grosor / 2 + grosor, Math.max(yb, ya + 1), color);
				} else {
					int xb = pantallaX((double) (i + 1) * CASILLA);
					g.fill(xa, ya - grosor / 2, Math.max(xb, xa + 1), ya - grosor / 2 + grosor, color);
				}
			}
		}
	}

	private void pintaCapitales(GuiGraphics g) {
		Component corona = Component.literal("♛");
		for (PaquetePolitica.Capital c : TerritoriosCliente.capitales()) {
			int x = pantallaX(c.x());
			int y = pantallaY(c.z());
			if (x < x0 - 8 || x > x1 + 8 || y < y0 - 8 || y > y1 + 8) {
				continue;
			}
			g.drawString(font, corona, x - font.width(corona) / 2, y - 4, c.perdida() ? 0xFF8A8577 : ORO, true);
		}
	}

	/** El nombre de cada reino en el centro de sus tierras conocidas, si cabe. */
	private void pintaNombres(GuiGraphics g) {
		double s = escala * CASILLA;
		for (Map.Entry<Long, double[]> e : centros.entrySet()) {
			double[] c = e.getValue();
			Component nombre = FichasCliente.nombreReino(Geo.claveX(e.getKey()), Geo.claveZ(e.getKey()));
			if (nombre == null || c[2] == 0) {
				continue;
			}
			MutableComponent sinColor = Component.literal(nombre.getString().toUpperCase());
			int w = font.width(sinColor);
			// que las tierras del reino ocupen al menos el doble del nombre
			if (c[2] * s * s < w * 9 * 2.0) {
				continue;
			}
			int x = pantallaX(c[0] / c[2] * CASILLA) - w / 2;
			int y = pantallaY(c[1] / c[2] * CASILLA) + 6;
			g.drawString(font, sinColor, x, y, TINTA, false);
		}
	}

	private void pintaJugador(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		int x = pantallaX(mc.player.getX());
		int y = pantallaY(mc.player.getZ());
		g.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000);
		g.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFFFFF);
		// hacia dónde mira, con un puntito delante
		double a = Math.toRadians(mc.player.getYRot());
		int fx = x + (int) Math.round(-Math.sin(a) * 6);
		int fy = y + (int) Math.round(Math.cos(a) * 6);
		g.fill(fx - 1, fy - 1, fx + 2, fy + 2, 0xFFFFFFFF);
	}

	private void leyenda(GuiGraphics g) {
		int x = x0;
		int y = y1 + 7;
		x = muestra(g, x, y, ORO, Component.translatableWithFallback("destinos.mapa.leyenda.capital", "♛ Capital"));
		x = muestra(g, x, y, FRENTE, Component.translatableWithFallback("destinos.mapa.leyenda.frente", "Frente de guerra"));
		x = muestra(g, x, y, SALVAJE, Component.translatableWithFallback("destinos.mapa.leyenda.salvaje", "Tierras salvajes"));
		x = muestra(g, x, y, mezcla(PERGAMINO, ORO, 0.6), Component.translatableWithFallback("destinos.mapa.leyenda.reciente",
				"Cambió de dueño"));
		x = muestra(g, x, y, NIEBLA, Component.translatableWithFallback("destinos.mapa.leyenda.niebla", "Sin explorar"));
		Component ayuda = Component.translatableWithFallback("destinos.mapa.ayuda", "Arrastra · rueda: zoom · espacio: a ti · clic: fijar ficha");
		if (x + font.width(ayuda) < x1) {
			g.drawString(font, ayuda, x1 - font.width(ayuda), y, TEXTO_SUAVE, false);
		}
	}

	private int muestra(GuiGraphics g, int x, int y, int color, Component texto) {
		g.fill(x, y, x + 8, y + 8, 0xFF000000);
		g.fill(x + 1, y + 1, x + 7, y + 7, color);
		g.drawString(font, texto, x + 11, y, TEXTO, false);
		return x + 11 + font.width(texto) + 10;
	}

	// ------------------------------------------------------------------ ficha y pista

	/** El reino de la ficha: el fijado con clic, el de debajo del ratón o el tuyo. */
	private PaqueteFichas.Reino reinoFicha(PaqueteTerritorio.Ducado bajo) {
		if (fijado != Long.MIN_VALUE) {
			PaqueteFichas.Reino r = FichasCliente.reino(Geo.claveX(fijado), Geo.claveZ(fijado));
			if (r != null) {
				return r;
			}
		}
		PaqueteTerritorio.Ducado d = bajo;
		Minecraft mc = Minecraft.getInstance();
		if (d == null && mc.player != null) {
			d = TerritoriosCliente.ducado(Math.floorDiv(mc.player.getBlockX(), CASILLA), Math.floorDiv(mc.player.getBlockZ(), CASILLA));
		}
		return d == null || d.salvaje() || d.deNadie() ? null : FichasCliente.reino(d.kx(), d.kz());
	}

	private void ficha(GuiGraphics g, PaqueteTerritorio.Ducado bajo) {
		int x = panelX;
		int ancho = width - 8 - panelX;
		g.fill(x - 2, y0 - 3, x + ancho + 2, y1 + 3, MARCO);
		g.fill(x, y0, x + ancho, y1, PANEL);
		int tx = x + 7;
		int w = ancho - 14;
		int y = y0 + 7;
		if (!FichasCliente.hay()) {
			g.drawString(font, Component.translatableWithFallback("destinos.mapa.cargando", "Cargando fichas…"), tx, y, TEXTO_SUAVE, false);
			return;
		}
		PaqueteFichas.Reino r = reinoFicha(bajo);
		if (r == null) {
			for (FormattedCharSequence l : font.split(Component.translatableWithFallback("destinos.mapa.sin_ficha",
					"Pasa el ratón por un reino para ver su ficha, o haz clic para dejarla fija."), w)) {
				g.drawString(font, l, tx, y, TEXTO_SUAVE, false);
				y += 10;
			}
			return;
		}
		int kx = r.kx();
		int kz = r.kz();
		g.fill(tx, y, tx + 10, y + 14, 0xFF000000);
		PaqueteTerritorio.Ducado algun = null;
		for (PaqueteTerritorio z : TerritoriosCliente.zonasRecibidas()) {
			for (PaqueteTerritorio.Ducado d : z.paleta()) {
				if (!d.salvaje() && d.kx() == kx && d.kz() == kz) {
					algun = d;
					break;
				}
			}
			if (algun != null) {
				break;
			}
		}
		g.fill(tx + 1, y + 1, tx + 9, y + 13, algun == null ? 0xFF888888 : 0xFF000000 | algun.color());
		y = linea(g, r.nombre().copy().withStyle(ChatFormatting.BOLD), tx + 14, y + 3, w - 14, TEXTO) + 6;
		if (fijado != Long.MIN_VALUE) {
			y = linea(g, Component.translatableWithFallback("destinos.mapa.fijada", "(ficha fija: clic fuera para soltarla)"), tx, y, w,
					TEXTO_SUAVE) + 2;
		}
		y = linea(g, Component.translatableWithFallback("destinos.mapa.poder", "Poder %s · puesto %s de %s", r.poder(), r.puesto(),
				FichasCliente.vivos()), tx, y, w, ORO);
		y = linea(g, Component.translatableWithFallback("destinos.mapa.gente", "Unos %s habitantes · %s ducados", r.habitantes(),
				r.ducados()), tx, y, w, TEXTO);
		y = linea(g, Component.translatableWithFallback("destinos.mapa.estabilidad", "Estabilidad %s/100", r.estabilidad()), tx, y, w,
				r.estabilidad() < 35 ? 0xFFFF7A6A : TEXTO);
		if (!r.capital().getString().isEmpty()) {
			y = linea(g, Component.translatableWithFallback(r.capitalPerdida() ? "destinos.mapa.capital_perdida" : "destinos.mapa.capital",
					r.capitalPerdida() ? "♛ Capital perdida: %s" : "♛ Capital: %s", r.capital()), tx, y, w,
					r.capitalPerdida() ? 0xFFFF7A6A : TEXTO);
		}
		if (r.senor() != null) {
			y = linea(g, Component.translatableWithFallback("destinos.mapa.vasallo", "Vasallo del %s", nombre(r.senor())), tx, y, w,
					TEXTO_SUAVE);
		}
		y += 4;
		if (!r.guerras().isEmpty()) {
			y = linea(g, Component.translatableWithFallback("destinos.mapa.guerras", "⚔ En guerra con %s", lista(r.guerras())), tx, y, w,
					0xFFFF7A6A);
		}
		if (!r.aliados().isEmpty()) {
			y = linea(g, Component.translatableWithFallback("destinos.mapa.aliados", "✦ Aliado del %s", lista(r.aliados())), tx, y, w,
					0xFF7FD6E0);
		}
		if (r.guerras().isEmpty() && r.aliados().isEmpty()) {
			y = linea(g, Component.translatableWithFallback("destinos.mapa.en_paz", "En paz y sin alianzas"), tx, y, w, TEXTO_SUAVE);
		}
		y += 4;
		int rep = r.rep();
		String clave = rep >= 20 ? "destinos.mapa.rep_buena" : rep <= -20 ? "destinos.mapa.rep_mala" : "destinos.mapa.rep";
		String respaldo = rep >= 20 ? "Tu reputación: %s (te aprecian)" : rep <= -20 ? "Tu reputación: %s (sus guardias van a por ti)"
				: "Tu reputación: %s";
		linea(g, Component.translatableWithFallback(clave, respaldo, (rep > 0 ? "+" : "") + rep), tx, y, w,
				rep >= 20 ? 0xFF9FD08A : rep <= -20 ? 0xFFFF7A6A : TEXTO);
	}

	private static Component nombre(int[] celda) {
		Component n = FichasCliente.nombreReino(celda[0], celda[1]);
		return n != null ? n : Component.translatableWithFallback("destinos.politica.lejano", "reino lejano");
	}

	private static Component lista(List<int[]> celdas) {
		MutableComponent res = Component.empty();
		for (int i = 0; i < celdas.size(); i++) {
			if (i > 0) {
				res.append(", ");
			}
			res.append(nombre(celdas.get(i)));
		}
		return res;
	}

	private int linea(GuiGraphics g, Component c, int x, int y, int w, int color) {
		for (FormattedCharSequence l : font.split(c, w)) {
			g.drawString(font, l, x, y, color, false);
			y += 10;
		}
		return y;
	}

	/** El cartelito junto al ratón: ducado, reino y si está en guerra. */
	private void pista(GuiGraphics g, PaqueteTerritorio.Ducado d, int mx, int my) {
		List<Component> lineas = new ArrayList<>();
		if (d.oceano()) {
			lineas.add(d.deNadie()
					? Component.translatableWithFallback("destinos.mapa.mar", "Mar abierto")
					: Component.translatableWithFallback("destinos.mapa.aguas", "Aguas del %s", nombre(new int[] {d.kx(), d.kz()})));
		} else {
			Component duc = FichasCliente.ducado(d.dcx(), d.dcz());
			lineas.add(duc != null ? duc.copy().withStyle(ChatFormatting.BOLD)
					: Component.translatableWithFallback("destinos.mapa.ducado_desconocido", "Ducado sin nombre todavía"));
			if (d.salvaje()) {
				lineas.add(Component.translatableWithFallback("destinos.mapa.salvaje", "Tierras salvajes: aquí no manda nadie")
						.withStyle(ChatFormatting.GRAY));
			} else {
				lineas.add(nombre(new int[] {d.kx(), d.kz()}));
				PaqueteFichas.Reino r = FichasCliente.reino(d.kx(), d.kz());
				if (r != null) {
					lineas.add(Component.translatableWithFallback("destinos.mapa.pista_poder", "Poder %s · puesto %s", r.poder(), r.puesto())
							.withStyle(ChatFormatting.GOLD));
					if (!r.guerras().isEmpty()) {
						lineas.add(Component.translatableWithFallback("destinos.mapa.guerras", "⚔ En guerra con %s", lista(r.guerras()))
								.withStyle(ChatFormatting.RED));
					}
				}
				if (TerritoriosCliente.reciente(d)) {
					lineas.add(Component.translatableWithFallback("destinos.mapa.pista_reciente", "Acaba de cambiar de dueño")
							.withStyle(ChatFormatting.YELLOW));
				}
			}
		}
		int bx = (int) Math.floor(bloqueX(mx));
		int bz = (int) Math.floor(bloqueZ(my));
		lineas.add(Component.literal(bx + ", " + bz).withStyle(ChatFormatting.DARK_GRAY));
		int w = 0;
		for (Component c : lineas) {
			w = Math.max(w, font.width(c));
		}
		int h = lineas.size() * 10 + 6;
		int x = mx + 12;
		int y = my - 4;
		if (x + w + 8 > x1) {
			x = mx - 12 - w - 8;
		}
		y = Math.max(y0, Math.min(y1 - h, y));
		g.fill(x - 1, y - 1, x + w + 9, y + h + 1, 0xFF8A6D3F);
		g.fill(x, y, x + w + 8, y + h, 0xF0120F0B);
		int yy = y + 4;
		for (Component c : lineas) {
			g.drawString(font, c, x + 4, yy, TEXTO, false);
			yy += 10;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
