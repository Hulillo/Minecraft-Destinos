package es.hulillo.destinos.cliente;

import com.mojang.blaze3d.platform.NativeImage;
import es.hulillo.destinos.DestinosCore;
import es.hulillo.destinos.Geo;
import es.hulillo.destinos.MapaTerreno;
import es.hulillo.destinos.PaqueteFichas;
import es.hulillo.destinos.PaquetePedirMapa;
import es.hulillo.destinos.PaquetePolitica;
import es.hulillo.destinos.PaqueteTerritorio;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * El mapa político a pantalla completa (tecla K): todos los ducados que conoce el cliente pintados con el color de
 * su reino, fronteras, frentes de guerra y capitales. Se arrastra para moverse y la rueda acerca y aleja. Al pasar
 * el ratón sale el ducado y su reino, y a la derecha la ficha del reino (el que tienes debajo, o el que has fijado
 * con un clic).
 */
public final class PantallaMapa extends Screen {
	private static final int NIEBLA = ModoMapa.NIEBLA;
	private static final int ORO = ModoMapa.ORO;
	private static final int TINTA = 0xFF2B2015;
	private static final int TINTA_SUAVE = 0xFF6B5640;
	private static final int MARCO = 0xFF5A3D22;
	private static final int FONDO = 0xF0201A13;
	private static final int PANEL = 0xF02A231A;
	private static final int TEXTO = 0xFFEFE4C8;
	private static final int TEXTO_SUAVE = 0xFFB9AB8A;
	private static final int FRENTE = ModoMapa.FRENTE;
	private static final int COSTA = 0xFF4A3D2A;
	private static final int FRONTERA = 0xFF2B2015;
	private static final int LINDE = 0x552B2015;
	private static final int CASILLA = MapaTerreno.CASILLA;
	private static final int NUCLEO = MapaTerreno.NUCLEO;
	/** Píxeles de pantalla por bloque: de 1 px cada 8 casillas (512 bloques) a 32 px por casilla. */
	private static final double ESCALA_MIN = 1.0 / (CASILLA * 8);
	private static final double ESCALA_MAX = 0.5;
	/** Cada cuánto se piden más zonas al servidor mientras el mapa está abierto, y cada cuánto las fichas. */
	private static final int PIDE_ZONAS = 40;
	private static final int PIDE_FICHAS = 200;
	/** Desde estos píxeles por casilla se pinta zona a zona con las lindes encima; por debajo, el lienzo de atlas. */
	private static final double CERCA = 4;
	/** La costa en el lienzo de lejos, algo más clara que la de cerca para que no pese tanto. */
	private static final int COSTA_LIENZO = 0xFF5C4E38;
	/** Ancho de la columna de modos, a la izquierda. */
	private static final int COLUMNA = 74;
	/** Cada cuánto, como mucho, se vuelven a buscar los accidentes del relieve (son caros). */
	private static final int REHACE_GEO = 400;

	/** Lo que se queda de una vista a otra mientras dura la conexión. */
	private static double vistaX = Double.NaN;
	private static double vistaZ;
	private static double escala = 1.0 / 12;
	private static long fijado = Long.MIN_VALUE;
	private static ModoMapa modo = ModoMapa.REINOS;
	private static String buscado = "";

	private final KeyMapping tecla;
	/** Cómo se pinta cada zona, por su clave (wx, wz); se rehace la zona que llega nueva y sus vecinas (por los bordes). */
	private final Map<Long, Pintura> pinturas = new HashMap<>();
	private final Map<Long, double[]> centros = new HashMap<>();
	/** El color de cada reino que sale en el mapa (para el cuadrito de la ficha). */
	private final Map<Long, Integer> coloresReino = new HashMap<>();
	private int zonasPintadas = -1;
	private int turnoPintado = Integer.MIN_VALUE;
	/** Si ha cambiado algo de lo que sale en el lienzo de lejos (y hay que rehacerlo). */
	private boolean lienzoViejo = true;
	/** El lienzo de lejos que se ve, y el que se está pintando en otro hilo. */
	private Identifier lienzo;
	private LienzoMapa.Hecho lienzoHecho;
	private CompletableFuture<Object[]> pintandoLienzo;
	private int cambiosPintados;
	private ModoMapa modoPintado;
	private final List<Boton> botones = new ArrayList<>();
	/** Lo que ya ocupan los rótulos en este frame, para que no se pisen (nombres de reino, accidentes, bichos). */
	private final List<int[]> ocupados = new ArrayList<>();
	private EditBox buscador;
	/** Lo que se encontró con la última búsqueda, en bloques, y por cuál va. */
	private final List<double[]> encontrados = new ArrayList<>();
	private int encontrado;
	private String buscadoAntes = null;
	private List<GeografiaMapa.Rotulo> rotulos = List.of();
	private int zonasGeo = -1;
	private String fichaClave;
	private final List<Trazo> fichaTrazos = new ArrayList<>();
	private List<Trazo> grabando;
	/** La leyenda del modo, ya traducida y medida. */
	private ModoMapa leyendaModo;
	private List<Object[]> leyendaMuestras = List.of();
	/** Los rótulos del relieve ya preparados para pintar: texto, ancho y color. */
	private List<Object[]> rotulosListos = List.of();
	/** Los rótulos del relieve se buscan en otro hilo; aquí el que está en marcha. */
	private CompletableFuture<List<GeografiaMapa.Rotulo>> buscandoGeo;
	/** Dónde van las estrellitas de los bichos raros (se rehace al llegar fichas). */
	private final List<int[]> raros = new ArrayList<>();
	private int rarosVersion = -1;
	/** El nombre de cada reino en mayúsculas, ya medido (se rehace al llegar fichas). */
	private final Map<Long, FormattedCharSequence> nombres = new HashMap<>();
	private final Map<Long, Integer> anchos = new HashMap<>();
	private int nombresVersion = -1;
	private List<FormattedCharSequence> pistaLineas;
	private int pistaAncho;
	private int pistaX;
	private int pistaZ;
	private ModoMapa pistaModo;
	private int pistaTurno;
	private PaqueteTerritorio.Ducado pistaDucado;
	private int tickGeo = -REHACE_GEO;
	private int ticks;
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private int panelX;
	private boolean arrastra;
	private double arrastrado;

	/** Una zona ya preparada: trozos de frontera, lo que aporta al centro de cada reino y los datos de sus casillas. */
	private static final class Pintura {
		final PaqueteTerritorio zona;
		final int ox;
		final int oz;
		final int[] bordes;
		final Map<Long, double[]> centros;
		/** Color, dueño y marcas de cada casilla, para el lienzo de lejos (que se pinta en otro hilo). */
		final LienzoMapa.Datos datos;
		/** Tramos de río (i, j, i2, j2) y cumbres (i, j, altura, 0), para de cerca. */
		final int[] rios;
		final int[] cumbres;
		/** La zona hecha imagen, un píxel por casilla (para de cerca; se hace la primera vez que se pinta). */
		Identifier imagen;

		Pintura(PaqueteTerritorio zona, int[] bordes, Map<Long, double[]> centros, LienzoMapa.Datos datos, int[][] detalles) {
			this.rios = detalles[0];
			this.cumbres = detalles[1];
			this.zona = zona;
			this.ox = zona.wx() * NUCLEO;
			this.oz = zona.wz() * NUCLEO;
			this.bordes = bordes;
			this.centros = centros;
			this.datos = datos;
		}

		/** Suelta las imágenes de la tarjeta gráfica. */
		void suelta() {
			TextureManager t = Minecraft.getInstance().getTextureManager();
			if (imagen != null) {
				t.release(imagen);
				imagen = null;
			}
		}

		int ox() {
			return ox;
		}

		int oz() {
			return oz;
		}

		int[] bordes() {
			return bordes;
		}
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
		x0 = 8 + COLUMNA + 6;
		y0 = 30;
		panelX = width - ancho - 8;
		x1 = panelX - 6;
		y1 = height - 22;
		// botones propios, con pinta de pergamino como el resto del mapa (no los grises de Minecraft)
		botones.clear();
		Component cerrar = Component.translatableWithFallback("destinos.mapa.cerrar", "Cerrar").copy().append(" ✕");
		int wc = font.width(cerrar) + 14;
		botones.add(new Boton(width - 8 - wc, 7, wc, 16, () -> cerrar, b -> onClose(), false));
		int wcapa = Math.max(font.width(textoCapa()), font.width(textoCapaOtro())) + 14;
		botones.add(new Boton(width - 8 - wc - 4 - wcapa, 7, wcapa, 16, PantallaMapa::textoCapa, b -> TerritoriosCliente.alterna(), false));
		for (ModoMapa m : ModoMapa.values()) {
			Component t = Component.literal(ICONOS[m.ordinal()] + " ").append(m.nombre());
			botones.add(new Boton(8, y0 + m.ordinal() * 19, COLUMNA, 17, () -> t, b -> cambiaModo(m), true, m));
		}
		int libre = width - 8 - wc - 4 - wcapa - 6;
		int anchoBusca = Math.min(140, Math.max(70, libre - 260));
		buscador = new EditBox(font, libre - anchoBusca + 4, 9, anchoBusca - 8, 12,
				Component.translatableWithFallback("destinos.mapa.buscar", "Buscar reino o ducado"));
		buscador.setBordered(false);
		buscador.setTextColor(TEXTO);
		buscador.setHint(Component.translatableWithFallback("destinos.mapa.buscar", "Buscar reino o ducado"));
		buscador.setMaxLength(40);
		buscador.setValue(buscado);
		buscador.setResponder(t -> buscado = t);
		addRenderableWidget(buscador);
		ClientPlayNetworking.send(new PaquetePedirMapa(true));
	}

	private void cambiaModo(ModoMapa m) {
		modo = m;
	}

	/** Un botón del mapa: caja de pergamino oscuro, dorada si es el modo puesto, más clara al pasar el ratón. */
	private record Boton(int x, int y, int w, int h, java.util.function.Supplier<Component> texto,
			java.util.function.Consumer<Boton> accion, boolean izquierda, ModoMapa modo) {
		Boton(int x, int y, int w, int h, java.util.function.Supplier<Component> texto, java.util.function.Consumer<Boton> accion,
				boolean izquierda) {
			this(x, y, w, h, texto, accion, izquierda, null);
		}

		boolean dentro(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	/** Los iconos de los modos, en el orden de ModoMapa. */
	private static final String[] ICONOS = {"♛", "✦", "⚔", "◆", "✿", "☠", "❀", "▲"};

	private void pintaBotones(GuiGraphics g, int mx, int my) {
		for (Boton b : botones) {
			boolean puesto = b.modo() != null && b.modo() == modo;
			boolean encima = b.dentro(mx, my);
			int fondo = puesto ? 0xFF6B4A1E : encima ? 0xFF4A3B28 : 0xFF2E251A;
			g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), puesto ? ORO : encima ? 0xFF9C7A45 : MARCO);
			g.fill(b.x() + 1, b.y() + 1, b.x() + b.w() - 1, b.y() + b.h() - 1, fondo);
			if (puesto) {
				// la pestaña del modo puesto: una raya dorada a la izquierda
				g.fill(b.x() + 1, b.y() + 1, b.x() + 3, b.y() + b.h() - 1, ORO);
			}
			Component t = b.texto().get();
			int color = puesto ? 0xFFFFE9A8 : encima ? TEXTO : TEXTO_SUAVE;
			int tx = b.izquierda() ? b.x() + 7 : b.x() + (b.w() - font.width(t)) / 2;
			g.drawString(font, t, tx, b.y() + (b.h() - 8) / 2, color, puesto);
		}
		if (buscador != null) {
			int bx = buscador.getX() - 4;
			int by = buscador.getY() - 3;
			boolean foco = buscador.isFocused();
			g.fill(bx, by, bx + buscador.getWidth() + 8, by + 16, foco ? ORO : MARCO);
			g.fill(bx + 1, by + 1, bx + buscador.getWidth() + 7, by + 15, 0xFF1C1610);
			if (!foco && buscado.isEmpty()) {
				g.drawString(font, "⌕", bx + buscador.getWidth() - 2, by + 4, TEXTO_SUAVE, false);
			}
		}
	}

	/** Busca reinos y ducados cuyo nombre lleve el texto; cada Intro salta al siguiente que haya. */
	private void busca() {
		String q = buscado.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return;
		}
		if (!q.equals(buscadoAntes)) {
			buscadoAntes = q;
			encontrados.clear();
			encontrado = -1;
			for (PaqueteFichas.Reino r : FichasCliente.reinos()) {
				if (r.nombre().getString().toLowerCase(Locale.ROOT).contains(q)) {
					double[] p = sitioReino(r.kx(), r.kz());
					if (p != null) {
						encontrados.add(new double[] {p[0], p[1], 1, r.kx(), r.kz()});
					}
				}
			}
			int sal = TerritoriosCliente.sal();
			for (PaqueteFichas.Nombre d : FichasCliente.ducados()) {
				if (d.nombre().getString().toLowerCase(Locale.ROOT).contains(q)) {
					int[] c = Geo.centroDucado(d.dcx(), d.dcz(), sal);
					encontrados.add(new double[] {c[0], c[1], 0, 0, 0});
				}
			}
		}
		if (encontrados.isEmpty()) {
			return;
		}
		encontrado = (encontrado + 1) % encontrados.size();
		double[] e = encontrados.get(encontrado);
		vistaX = e[0];
		vistaZ = e[1];
		escala = Math.max(escala, 1.0 / 24);
		if (e[2] == 1) {
			fijado = Geo.clave((int) e[3], (int) e[4]);
		}
	}

	/** Dónde está un reino en el mapa (en bloques): su capital, o el centro de sus tierras conocidas. */
	private double[] sitioReino(int kx, int kz) {
		double[] mejor = null;
		for (PaquetePolitica.Capital c : TerritoriosCliente.capitales()) {
			if (c.kx() == kx && c.kz() == kz) {
				mejor = new double[] {c.x(), c.z()};
				if (!c.perdida()) {
					return mejor;
				}
			}
		}
		if (mejor != null) {
			return mejor;
		}
		double[] s = centros.get(Geo.clave(kx, kz));
		return s == null || s[2] == 0 ? null : new double[] {s[0] / s[2] * CASILLA, s[1] / s[2] * CASILLA};
	}

	private static Component textoCapaOtro() {
		return Component.translatableWithFallback(!TerritoriosCliente.visible() ? "destinos.mapa.capa_si" : "destinos.mapa.capa_no",
				!TerritoriosCliente.visible() ? "Colores en tus mapas: sí" : "Colores en tus mapas: no");
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
		if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			for (Boton b : botones) {
				if (b.dentro(e.x(), e.y())) {
					setFocused(null);
					b.accion().accept(b);
					Minecraft.getInstance().getSoundManager().play(
							net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1));
					return true;
				}
			}
		}
		if (super.mouseClicked(e, doble)) {
			return true;
		}
		if (e.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && enMapa(e.x(), e.y())) {
			setFocused(null);
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
		if (buscador != null && buscador.isFocused()) {
			if (e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) {
				busca();
				return true;
			}
			// mientras se escribe, las letras y los números son del buscador (Escape sí cierra)
			return super.keyPressed(e);
		}
		if (e.key() >= GLFW.GLFW_KEY_1 && e.key() < GLFW.GLFW_KEY_1 + ModoMapa.values().length) {
			cambiaModo(ModoMapa.values()[e.key() - GLFW.GLFW_KEY_1]);
			return true;
		}
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
			Component anio = Component.translatableWithFallback("destinos.mapa.anio", "Año %s · turno %s · %s reinos conocidos",
					FichasCliente.anio(), FichasCliente.turno(), FichasCliente.vivos());
			int ax = 16 + font.width(title) + 12;
			if (buscador == null || ax + font.width(anio) < buscador.getX() - 6) {
				g.drawString(font, anio, ax, 11, TEXTO_SUAVE, false);
			}
		}
		g.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, MARCO);
		g.enableScissor(x0, y0, x1, y1);
		g.fill(x0, y0, x1, y1, NIEBLA);
		prepara();
		pintaZonas(g);
		if (modo == ModoMapa.DIPLOMACIA) {
			pintaDiplomacia(g);
		}
		if (modo == ModoMapa.FAUNA) {
			pintaRaros(g);
		}
		pintaCapitales(g);
		// los rótulos, de más a menos importantes, sin pisarse: reinos, accidentes y bichos
		ocupados.clear();
		if (modo == ModoMapa.RELIEVE) {
			pintaGeografia(g, true);
		} else {
			pintaNombres(g);
			// de lejos, como en un atlas, también los mares y las sierras grandes (los ríos solo en Relieve)
			if (escala * CASILLA < CERCA) {
				pintaGeografia(g, false);
			}
			if (modo == ModoMapa.FAUNA) {
				pintaBichos(g);
			}
		}
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
		g.drawString(font, Component.translatableWithFallback("destinos.mapa.modos", "Modos"), 10, y0 - 11, TEXTO_SUAVE, false);
		pintaBotones(g, mx, my);
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

	/**
	 * Pone al día las pinturas. Si cambia el turno o el modo se rehace todo; si no, solo las zonas que han llegado (o
	 * se han renovado) y sus vecinas, que es donde cambian las fronteras de los bordes.
	 */
	private void prepara() {
		int turno = FichasCliente.turno();
		int cambios = modo.usaFichas() ? FichasCliente.cambios() : 0;
		Collection<PaqueteTerritorio> zonas = TerritoriosCliente.zonasRecibidas();
		boolean todo = turno != turnoPintado || cambios != cambiosPintados || modo != modoPintado || pinturas.size() > zonas.size();
		turnoPintado = turno;
		cambiosPintados = cambios;
		modoPintado = modo;
		if (todo) {
			pinturas.values().forEach(Pintura::suelta);
			pinturas.clear();
		}
		Set<Long> rehacer = null;
		for (PaqueteTerritorio z : zonas) {
			Pintura p = pinturas.get(Geo.clave(z.wx(), z.wz()));
			if (p == null || p.zona != z) {
				if (rehacer == null) {
					rehacer = new HashSet<>();
				}
				rehacer.add(Geo.clave(z.wx(), z.wz()));
				if (!todo) {
					for (int dz = -1; dz <= 1; dz++) {
						for (int dx = -1; dx <= 1; dx++) {
							if (pinturas.containsKey(Geo.clave(z.wx() + dx, z.wz() + dz))) {
								rehacer.add(Geo.clave(z.wx() + dx, z.wz() + dz));
							}
						}
					}
				}
			}
		}
		if (rehacer == null) {
			return;
		}
		Map<Long, PaqueteTerritorio> porClave = new HashMap<>();
		for (PaqueteTerritorio z : zonas) {
			porClave.put(Geo.clave(z.wx(), z.wz()), z);
		}
		for (long k : rehacer) {
			PaqueteTerritorio z = porClave.get(k);
			if (z != null) {
				Pintura vieja = pinturas.put(k, pinta(z));
				if (vieja != null) {
					vieja.suelta();
				}
			}
		}
		zonasPintadas = pinturas.size();
		// el lienzo de lejos solo se rehace si lo que ha cambiado cae dentro de él
		LienzoMapa.Hecho h = lienzoHecho;
		if (todo || h == null) {
			lienzoViejo = true;
		} else {
			double lado = (double) NUCLEO * CASILLA;
			for (long k : rehacer) {
				double zx = Geo.claveX(k) * lado;
				double zz = Geo.claveZ(k) * lado;
				if (zx + lado > h.bx0() && zx < h.bx0() + h.ancho() * h.bloquesPorPixel() && zz + lado > h.bz0()
						&& zz < h.bz0() + h.alto() * h.bloquesPorPixel()) {
					lienzoViejo = true;
					break;
				}
			}
		}
		centros.clear();
		for (Pintura p : pinturas.values()) {
			for (Map.Entry<Long, double[]> e : p.centros.entrySet()) {
				double[] a = centros.computeIfAbsent(e.getKey(), x -> new double[] {0, 0, 0, Double.MAX_VALUE, -Double.MAX_VALUE});
				a[3] = Math.min(a[3], e.getValue()[3]);
				a[4] = Math.max(a[4], e.getValue()[4]);
				a[0] += e.getValue()[0];
				a[1] += e.getValue()[1];
				a[2] += e.getValue()[2];
			}
		}
	}

	/** Tipo de linde entre dos casillas vecinas: 0 nada, 1 ducado, 2 costa, 3 reino, 4 frente de guerra. */
	private static int linde(PaqueteTerritorio.Ducado a, PaqueteTerritorio.Ducado b) {
		if (a == null || b == null || (a.deNadie() && b.deNadie())) {
			return 0;
		}
		if (modo.frentes() && TerritoriosCliente.enGuerra(a, b)) {
			return 4;
		}
		if (a.oceano() != b.oceano()) {
			return 2;
		}
		if (!a.mismoReino(b)) {
			return a.oceano() ? 1 : modo.fronteras() ? 3 : 1;
		}
		return a.mismoDucado(b) || a.oceano() ? 0 : 1;
	}

	private Pintura pinta(PaqueteTerritorio z) {
		int ox = z.wx() * NUCLEO;
		int oz = z.wz() * NUCLEO;
		Enteros bordes = new Enteros();
		Map<Long, double[]> centros = new HashMap<>();
		int[] colores = new int[NUCLEO * NUCLEO];
		long[] dueno = new long[NUCLEO * NUCLEO];
		byte[] marcas = new byte[NUCLEO * NUCLEO];
		for (int j = 0; j < NUCLEO; j++) {
			for (int i = 0; i < NUCLEO; i++) {
				PaqueteTerritorio.Ducado d = z.ducado(i, j);
				int c = i + j * NUCLEO;
				colores[c] = modo.color(z, i, j);
				// las tierras salvajes son todas de nadie: entre ellas no hay frontera
				dueno[c] = d.salvaje() ? Long.MIN_VALUE : Geo.clave(d.kx(), d.kz());
				marcas[c] = (byte) ((d.oceano() ? LienzoMapa.Datos.OCEANO : 0) | (d.salvaje() ? LienzoMapa.Datos.SALVAJE : 0));
				if (!d.salvaje() && !d.oceano()) {
					coloresReino.put(Geo.clave(d.kx(), d.kz()), d.color());
					double[] s = centros.computeIfAbsent(Geo.clave(d.kx(), d.kz()), k -> new double[] {0, 0, 0, Double.MAX_VALUE, -Double.MAX_VALUE});
					// de dónde a dónde va el reino de oeste a este (para que el nombre no sea más ancho que sus tierras)
					s[3] = Math.min(s[3], ox + i);
					s[4] = Math.max(s[4], ox + i + 1);
					s[0] += ox + i + 0.5;
					s[1] += oz + j + 0.5;
					s[2]++;
				}
				// la linde de la izquierda y la de arriba (las otras las pone la casilla de al lado)
				PaqueteTerritorio.Ducado izq = i > 0 ? z.ducado(i - 1, j) : TerritoriosCliente.ducado(ox + i - 1, oz + j);
				PaqueteTerritorio.Ducado arr = j > 0 ? z.ducado(i, j - 1) : TerritoriosCliente.ducado(ox + i, oz + j - 1);
				int a = linde(d, izq);
				if (a > 0) {
					bordes.add(0, i, j, a);
				}
				int b = linde(d, arr);
				if (b > 0) {
					bordes.add(1, i, j, b);
				}
				// los detalles de cerca: curvas de nivel en Relieve y límites de hábitat en Fauna
				if (modo == ModoMapa.RELIEVE && !d.oceano()) {
					int nivel = Math.floorDiv(z.altura(i, j), CURVA);
					if (nivel != nivel(z, ox + i - 1, oz + j, i - 1, j)) {
						bordes.add(0, i, j, CURVA_NIVEL);
					}
					if (nivel != nivel(z, ox + i, oz + j - 1, i, j - 1)) {
						bordes.add(1, i, j, CURVA_NIVEL);
					}
				} else if (modo == ModoMapa.FAUNA && !d.oceano()) {
					int f = z.familia(i, j);
					if (i > 0 && z.familia(i - 1, j) != f && !z.ducado(i - 1, j).oceano()) {
						bordes.add(0, i, j, HABITAT);
					}
					if (j > 0 && z.familia(i, j - 1) != f && !z.ducado(i, j - 1).oceano()) {
						bordes.add(1, i, j, HABITAT);
					}
				}
			}
		}
		return new Pintura(z, bordes.toArray(), centros, new LienzoMapa.Datos(colores, dueno, marcas), detalles(z));
	}

	/** El escalón de altura de una casilla (para las curvas de nivel), o el mismo que el de al lado si no se sabe. */
	private static int nivel(PaqueteTerritorio z, int tx, int tz, int i, int j) {
		if (i >= 0 && j >= 0) {
			return z.ducado(i, j).oceano() ? Integer.MIN_VALUE : Math.floorDiv(z.altura(i, j), CURVA);
		}
		PaqueteTerritorio.Ducado d = TerritoriosCliente.ducado(tx, tz);
		int h = TerritoriosCliente.altura(tx, tz);
		if (d == null || h == TerritoriosCliente.SIN_ALTURA) {
			return Math.floorDiv(z.altura(Math.max(0, i), Math.max(0, j)), CURVA);
		}
		return d.oceano() ? Integer.MIN_VALUE : Math.floorDiv(h, CURVA);
	}

	/**
	 * Lo que se dibuja encima de la zona de cerca: los tramos de río (de centro a centro de casillas de río vecinas,
	 * para que se vean como un río y no como cuadros) y las cumbres (la casilla más alta de su alrededor).
	 */
	private static int[][] detalles(PaqueteTerritorio z) {
		Enteros rios = new Enteros();
		Enteros cumbres = new Enteros();
		for (int j = 0; j < NUCLEO; j++) {
			for (int i = 0; i < NUCLEO; i++) {
				if (z.ducado(i, j).oceano()) {
					continue;
				}
				if (z.rio(i, j)) {
					// hacia la derecha, abajo y las dos diagonales de abajo (las otras las pone la casilla vecina)
					for (int[] d : new int[][] {{1, 0}, {0, 1}, {1, 1}, {-1, 1}}) {
						int ni = i + d[0];
						int nj = j + d[1];
						if (ni >= 0 && ni < NUCLEO && nj < NUCLEO && (z.rio(ni, nj) || z.ducado(ni, nj).oceano())) {
							// en diagonal solo si no hay ya un camino recto, para no hacer triángulos
							if (d[0] != 0 && d[1] != 0 && (z.rio(i + d[0], j) || z.rio(i, j + d[1]))) {
								continue;
							}
							rios.add(i, j, ni, nj);
						}
					}
				}
				int h = z.altura(i, j);
				if (h >= CUMBRE_MIN && esCumbre(z, i, j, h)) {
					cumbres.add(i, j, h, 0);
				}
			}
		}
		return new int[][] {rios.toArray(), cumbres.toArray()};
	}

	/** Si la casilla es la más alta en RADIO_CUMBRE casillas a la redonda (dentro de la zona). */
	private static boolean esCumbre(PaqueteTerritorio z, int i, int j, int h) {
		for (int dj = -RADIO_CUMBRE; dj <= RADIO_CUMBRE; dj++) {
			for (int di = -RADIO_CUMBRE; di <= RADIO_CUMBRE; di++) {
				int a = i + di;
				int b = j + dj;
				if ((di != 0 || dj != 0) && a >= 0 && b >= 0 && a < NUCLEO && b < NUCLEO) {
					int o = z.altura(a, b);
					// en empate gana la primera, para que no salgan dos cumbres juntas
					if (o > h || (o == h && (dj < 0 || (dj == 0 && di < 0)))) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * De cerca (4 px por casilla o más), cada zona es una imagen de 48×48 (un píxel por casilla) que la tarjeta gráfica
	 * estira, y encima las lindes como rayas. De lejos, un lienzo de atlas (LienzoMapa): una sola imagen de todo lo que
	 * se ve, a la resolución de verdad de la pantalla y con las fronteras en rayas finas, que se pinta en otro hilo y
	 * solo se rehace al cambiar de zoom, al salirse de ella o al llegar zonas nuevas. Así cuesta un dibujo por zona de
	 * cerca y uno solo de lejos.
	 */
	private void pintaZonas(GuiGraphics g) {
		double s = escala * CASILLA;
		if (s < CERCA) {
			preparaLienzo();
			if (lienzo != null) {
				pintaLienzo(g);
				return;
			}
		}
		// hacer imágenes cuesta (al abrir o al cambiar de modo hay que hacerlas todas): unas pocas por frame, y las
		// zonas que aún no la tienen se quedan en niebla un instante
		int presupuesto = IMAGENES_POR_FRAME;
		for (Pintura p : pinturas.values()) {
			if (!visible(p)) {
				continue;
			}
			if (p.imagen == null) {
				if (presupuesto-- <= 0) {
					continue;
				}
				p.imagen = imagen(p);
			}
			int xa = pantallaX((double) p.ox() * CASILLA);
			int ya = pantallaY((double) p.oz() * CASILLA);
			int xb = pantallaX((double) (p.ox() + NUCLEO) * CASILLA);
			int yb = pantallaY((double) (p.oz() + NUCLEO) * CASILLA);
			g.blit(RenderPipelines.GUI_TEXTURED, p.imagen, xa, ya, 0, 0, xb - xa, yb - ya, NUCLEO, NUCLEO, NUCLEO, NUCLEO);
			if (s >= CERCA) {
				pintaBordes(g, p, s);
				if (modo == ModoMapa.RELIEVE) {
					pintaDetalles(g, p, s);
				}
			}
		}
	}

	/** Ríos como rayas de centro a centro de casilla y cumbres con su altura (en Relieve, de cerca). */
	private void pintaDetalles(GuiGraphics g, Pintura p, double s) {
		int[] r = p.rios;
		int grosor = s >= 16 ? 3 : 2;
		for (int k = 0; k < r.length; k += 4) {
			double ax = pantallaX((p.ox() + r[k] + 0.5) * CASILLA);
			double ay = pantallaY((p.oz() + r[k + 1] + 0.5) * CASILLA);
			double bx = pantallaX((p.ox() + r[k + 2] + 0.5) * CASILLA);
			double by = pantallaY((p.oz() + r[k + 3] + 0.5) * CASILLA);
			if (Math.max(ax, bx) < x0 || Math.min(ax, bx) > x1 || Math.max(ay, by) < y0 || Math.min(ay, by) > y1) {
				continue;
			}
			if (ax == bx || ay == by) {
				// recto: un solo rectángulo
				g.fill((int) Math.min(ax, bx) - grosor / 2, (int) Math.min(ay, by) - grosor / 2, (int) Math.max(ax, bx) - grosor / 2 + grosor,
						(int) Math.max(ay, by) - grosor / 2 + grosor, RIO);
			} else {
				int pasos = (int) Math.ceil(Math.abs(bx - ax) / 1.5);
				for (int q = 0; q <= pasos; q++) {
					int x = (int) Math.round(ax + (bx - ax) * q / pasos);
					int y = (int) Math.round(ay + (by - ay) * q / pasos);
					g.fill(x - grosor / 2, y - grosor / 2, x - grosor / 2 + grosor, y - grosor / 2 + grosor, RIO);
				}
			}
		}
		if (s < 8) {
			return;
		}
		int[] c = p.cumbres;
		for (int k = 0; k < c.length; k += 4) {
			int x = pantallaX((p.ox() + c[k] + 0.5) * CASILLA);
			int y = pantallaY((p.oz() + c[k + 1] + 0.5) * CASILLA);
			if (x < x0 || x > x1 || y < y0 || y > y1) {
				continue;
			}
			String t = s >= 12 ? "▲ " + c[k + 2] : "▲";
			int w = font.width(t);
			if (libre(x - w / 2 - 1, y - 5, x + w / 2 + 1, y + 5)) {
				g.drawString(font, t, x - w / 2, y - 4, 0xFF3A2A1A, false);
			}
		}
	}

	/** Cuántas imágenes de zona se hacen como mucho en un frame. */
	private static final int IMAGENES_POR_FRAME = 40;
	/** Tipos de raya de cerca además de las lindes: curva de nivel (Relieve) y límite de hábitat (Fauna). */
	private static final int CURVA_NIVEL = 5;
	private static final int HABITAT = 6;
	/** Cada cuántos bloques de altura va una curva de nivel. */
	private static final int CURVA = 24;
	/** Altura mínima para marcar una cumbre, y en cuántas casillas a la redonda ha de ser la más alta. */
	private static final int CUMBRE_MIN = 110;
	private static final int RADIO_CUMBRE = 4;
	private static final int RIO = 0xFF3F7FB8;
	/** Casillas que ha de tener una sierra para salir con nombre fuera del modo Relieve. */
	private static final int SIERRA_ATLAS = 40;
	/** El lienzo cubre lo que se ve y esto más por cada lado (en fracción del ancho), para poder moverse sin rehacerlo. */
	private static final double MARGEN_LIENZO = 0.25;

	private static int imagenes;

	/** La imagen de cerca de una zona: un píxel por casilla. */
	private static Identifier imagen(Pintura p) {
		NativeImage img = new NativeImage(NUCLEO, NUCLEO, false);
		int[] c = p.datos.colores();
		for (int j = 0; j < NUCLEO; j++) {
			for (int i = 0; i < NUCLEO; i++) {
				img.setPixel(i, j, c[i + j * NUCLEO]);
			}
		}
		return sube(img);
	}

	/** Sube una imagen a la tarjeta gráfica con un nombre nuevo. */
	private static Identifier sube(NativeImage img) {
		Identifier id = Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "mapa/img_" + (imagenes++));
		DynamicTexture tex = new DynamicTexture(id::toString, img);
		tex.upload();
		Minecraft.getInstance().getTextureManager().register(id, tex);
		return id;
	}

	/** Píxeles de verdad por píxel de interfaz que usa el lienzo (como mucho 2, que más no se nota y cuesta). */
	private static int resolucion() {
		return (int) Math.max(1, Math.min(2, Math.round(Minecraft.getInstance().getWindow().getGuiScale())));
	}

	/** Si hace falta, pide un lienzo nuevo; y si ha acabado uno, lo sube y suelta el viejo. */
	private void preparaLienzo() {
		if (pintandoLienzo != null && pintandoLienzo.isDone()) {
			Object[] r = pintandoLienzo.getNow(null);
			pintandoLienzo = null;
			if (r != null) {
				soltarLienzo();
				lienzo = sube((NativeImage) r[1]);
				lienzoHecho = (LienzoMapa.Hecho) r[0];
			}
		}
		if (pintandoLienzo != null) {
			return;
		}
		int res = resolucion();
		double bpp = 1 / (escala * res);
		double vx0 = bloqueX(x0);
		double vz0 = bloqueZ(y0);
		double vx1 = bloqueX(x1);
		double vz1 = bloqueZ(y1);
		LienzoMapa.Hecho h = lienzoHecho;
		boolean vale = h != null && !lienzoViejo && Math.abs(h.bloquesPorPixel() - bpp) <= bpp * 1e-6
				&& vx0 >= h.bx0() && vz0 >= h.bz0() && vx1 <= h.bx0() + h.ancho() * h.bloquesPorPixel()
				&& vz1 <= h.bz0() + h.alto() * h.bloquesPorPixel();
		if (vale) {
			return;
		}
		// lo que se ve y un margen alrededor, con el origen en un múltiplo de píxel para que no tiemble al rehacerlo
		double mx = (vx1 - vx0) * MARGEN_LIENZO;
		double mz = (vz1 - vz0) * MARGEN_LIENZO;
		double bx0 = Math.floor((vx0 - mx) / bpp) * bpp;
		double bz0 = Math.floor((vz0 - mz) / bpp) * bpp;
		int ancho = (int) Math.ceil((vx1 - vx0 + 2 * mx) / bpp) + 1;
		int alto = (int) Math.ceil((vz1 - vz0 + 2 * mz) / bpp) + 1;
		Map<Long, LienzoMapa.Datos> zonas = new HashMap<>();
		for (Map.Entry<Long, Pintura> e : pinturas.entrySet()) {
			zonas.put(e.getKey(), e.getValue().datos);
		}
		Set<Long> guerras = new HashSet<>();
		if (modo.frentes()) {
			for (PaqueteFichas.Reino r : FichasCliente.reinos()) {
				for (int[] o : r.guerras()) {
					guerras.add(LienzoMapa.par(Geo.clave(r.kx(), r.kz()), Geo.clave(o[0], o[1])));
				}
			}
		}
		boolean fronteras = modo.fronteras();
		lienzoViejo = false;
		pintandoLienzo = CompletableFuture.supplyAsync(() -> {
			LienzoMapa.Hecho hecho = LienzoMapa.pinta(zonas, guerras, bx0, bz0, bpp, ancho, alto, res, fronteras, COSTA_LIENZO, FRONTERA,
					FRENTE);
			// la imagen también se rellena aquí, que son millones de píxeles: al hilo del cliente solo le toca subirla
			NativeImage img = new NativeImage(ancho, alto, true);
			int[] px = hecho.pixeles();
			for (int y = 0; y < alto; y++) {
				for (int x = 0; x < ancho; x++) {
					img.setPixel(x, y, px[x + y * ancho]);
				}
			}
			return new Object[] {hecho, img};
		}).exceptionally(e -> {
			DestinosCore.LOG.warn("Destinos Core: no se pudo pintar el lienzo del mapa", e);
			return null;
		});
	}

	/** El lienzo en su sitio (con traslación en coma flotante para que se mueva suave al arrastrar). */
	private void pintaLienzo(GuiGraphics g) {
		LienzoMapa.Hecho h = lienzoHecho;
		double ex = (h.bx0() - vistaX) * escala + cx();
		double ez = (h.bz0() - vistaZ) * escala + cy();
		// si ha cambiado el zoom y el nuevo aún no está, se estira el viejo
		float w = (float) (h.ancho() * h.bloquesPorPixel() * escala);
		float hh = (float) (h.alto() * h.bloquesPorPixel() * escala);
		g.pose().pushMatrix();
		g.pose().translate((float) ex, (float) ez);
		g.pose().scale(w / h.ancho(), hh / h.alto());
		g.blit(RenderPipelines.GUI_TEXTURED, lienzo, 0, 0, 0, 0, h.ancho(), h.alto(), h.ancho(), h.alto(), h.ancho(), h.alto());
		g.pose().popMatrix();
	}

	private void soltarLienzo() {
		if (lienzo != null) {
			Minecraft.getInstance().getTextureManager().release(lienzo);
			lienzo = null;
			lienzoHecho = null;
		}
	}

	/** Las lindes de la zona como rayas encima de la imagen (solo de cerca). */
	private void pintaBordes(GuiGraphics g, Pintura p, double s) {
		int[] b = p.bordes();
		for (int k = 0; k < b.length; k += 4) {
			int tipo = b[k + 3];
			if (tipo == 1 && s < 6) {
				continue;
			}
			if (tipo >= CURVA_NIVEL && s < 6) {
				continue;
			}
			int grosor = tipo >= 3 && tipo <= 4 && s >= 12 ? 2 : 1;
			if (tipo == 4) {
				grosor++;
			}
			int color = switch (tipo) {
				case 1 -> LINDE;
				case 2 -> COSTA;
				case 4 -> FRENTE;
				case CURVA_NIVEL -> 0x664A3520;
				case HABITAT -> 0x55203018;
				default -> FRONTERA;
			};
			int i = p.ox() + b[k + 1];
			int j = p.oz() + b[k + 2];
			int xa = pantallaX((double) i * CASILLA);
			int ya = pantallaY((double) j * CASILLA);
			if (ya > y1 || xa > x1) {
				continue;
			}
			if (b[k] == 0) {
				int yb = pantallaY((double) (j + 1) * CASILLA);
				if (xa < x0 - 2 || yb < y0) {
					continue;
				}
				g.fill(xa - grosor / 2, ya, xa - grosor / 2 + grosor, Math.max(yb, ya + 1), color);
			} else {
				int xb = pantallaX((double) (i + 1) * CASILLA);
				if (xb < x0 || ya < y0 - 2) {
					continue;
				}
				g.fill(xa, ya - grosor / 2, Math.max(xb, xa + 1), ya - grosor / 2 + grosor, color);
			}
		}
	}

	@Override
	public void removed() {
		pinturas.values().forEach(Pintura::suelta);
		pinturas.clear();
		soltarLienzo();
		if (pintandoLienzo != null) {
			// si acaba después de cerrar, que no se quede la imagen colgando
			pintandoLienzo.thenAccept(r -> {
				if (r != null) {
					Minecraft.getInstance().execute(((NativeImage) r[1])::close);
				}
			});
			pintandoLienzo = null;
		}
		super.removed();
	}

	/** Si la zona cae, aunque sea en parte, dentro del mapa. */
	private boolean visible(Pintura p) {
		return pantallaX((double) (p.ox() + NUCLEO) * CASILLA) >= x0 && pantallaX((double) p.ox() * CASILLA) <= x1
				&& pantallaY((double) (p.oz() + NUCLEO) * CASILLA) >= y0 && pantallaY((double) p.oz() * CASILLA) <= y1;
	}

	/** Líneas curvas entre capitales: verde las alianzas, rojo las guerras y oro del vasallo a su señor. */
	private void pintaDiplomacia(GuiGraphics g) {
		for (PaqueteFichas.Reino r : FichasCliente.reinos()) {
			double[] a = sitioReino(r.kx(), r.kz());
			if (a == null) {
				continue;
			}
			long ka = Geo.clave(r.kx(), r.kz());
			for (int[] o : r.aliados()) {
				if (Geo.clave(o[0], o[1]) > ka) {
					curva(g, a, sitioReino(o[0], o[1]), ModoMapa.ALIANZA);
				}
			}
			for (int[] o : r.guerras()) {
				if (Geo.clave(o[0], o[1]) > ka) {
					curva(g, a, sitioReino(o[0], o[1]), FRENTE);
				}
			}
			if (r.senor() != null) {
				curva(g, a, sitioReino(r.senor()[0], r.senor()[1]), ModoMapa.VASALLAJE);
			}
		}
	}

	/** Una curva suave de a a b (en bloques), combada hacia un lado para que no se tapen las de ida y vuelta. */
	private void curva(GuiGraphics g, double[] a, double[] b, int color) {
		if (b == null) {
			return;
		}
		double ax = pantallaX(a[0]);
		double ay = pantallaY(a[1]);
		double bx = pantallaX(b[0]);
		double by = pantallaY(b[1]);
		double dx = bx - ax;
		double dy = by - ay;
		double largo = Math.hypot(dx, dy);
		if (largo < 4 || Math.max(ax, bx) < x0 - 20 || Math.min(ax, bx) > x1 + 20 || Math.max(ay, by) < y0 - 20 || Math.min(ay, by) > y1 + 20) {
			return;
		}
		double qx = (ax + bx) / 2 - dy * 0.18;
		double qy = (ay + by) / 2 + dx * 0.18;
		int pasos = (int) Math.min(300, largo / 2.5) + 1;
		for (int k = 0; k <= pasos; k++) {
			double t = k / (double) pasos;
			double u = 1 - t;
			int x = (int) Math.round(u * u * ax + 2 * u * t * qx + t * t * bx);
			int y = (int) Math.round(u * u * ay + 2 * u * t * qy + t * t * by);
			if (x >= x0 - 2 && x <= x1 + 2 && y >= y0 - 2 && y <= y1 + 2) {
				g.fill(x - 1, y - 1, x + 2, y + 2, color);
			}
		}
	}

	/** En el modo Fauna, una estrellita en los ducados con algún bicho que sale en pocos sitios. */
	private void pintaRaros(GuiGraphics g) {
		if (escala * CASILLA < 1.5) {
			return;
		}
		if (rarosVersion != FichasCliente.version()) {
			rarosVersion = FichasCliente.version();
			raros.clear();
			int sal = TerritoriosCliente.sal();
			for (PaqueteFichas.Nombre d : FichasCliente.ducados()) {
				if (FichasCliente.conRaros(d.dcx(), d.dcz())) {
					raros.add(Geo.centroDucado(d.dcx(), d.dcz(), sal));
				}
			}
		}
		Component estrella = Component.literal("✦");
		for (int[] c : raros) {
			int x = pantallaX(c[0]);
			int y = pantallaY(c[1]);
			if (x < x0 || x > x1 || y < y0 || y > y1) {
				continue;
			}
			g.drawString(font, estrella, x - font.width(estrella) / 2, y - 4, ORO, true);
		}
	}

	/** En el modo Relieve, los nombres de sierras, ríos y mares (sin pisarse; primero los más grandes). */
	private void pintaGeografia(GuiGraphics g, boolean todo) {
		if (buscandoGeo != null && buscandoGeo.isDone()) {
			rotulos = buscandoGeo.getNow(rotulos);
			buscandoGeo = null;
			// estilo, ancho y color de cada rótulo, una vez y no en cada frame
			List<Object[]> l = new ArrayList<>(rotulos.size());
			for (GeografiaMapa.Rotulo r : rotulos) {
				Component t = r.tipo() != GeografiaMapa.Tipo.SIERRA ? r.texto().copy().withStyle(ChatFormatting.ITALIC) : r.texto();
				int color = switch (r.tipo()) {
					case SIERRA -> 0xFF4A2E17;
					case RIO -> 0xFF1E4A78;
					default -> 0xFF173B5C;
				};
				l.add(new Object[] {r, t.getVisualOrderText(), font.width(t), color});
			}
			rotulosListos = l;
		}
		int n = TerritoriosCliente.zonas();
		if (buscandoGeo == null && n != zonasGeo && ticks - tickGeo >= REHACE_GEO) {
			zonasGeo = n;
			tickGeo = ticks;
			// las zonas no cambian una vez recibidas: se copia la lista y se busca en otro hilo para no dar tirones
			List<PaqueteTerritorio> copia = new ArrayList<>(TerritoriosCliente.zonasRecibidas());
			int sal = TerritoriosCliente.sal();
			buscandoGeo = CompletableFuture.supplyAsync(() -> {
				List<GeografiaMapa.Rotulo> l = GeografiaMapa.calcula(copia, sal);
				l.sort((a, b) -> Integer.compare(b.casillas(), a.casillas()));
				return l;
			}).exceptionally(e -> List.of());
		}
		double s = escala * CASILLA;
		for (Object[] o : rotulosListos) {
			GeografiaMapa.Rotulo r = (GeografiaMapa.Rotulo) o[0];
			FormattedCharSequence t = (FormattedCharSequence) o[1];
			int w = (Integer) o[2];
			int color = (Integer) o[3];
			if (!todo && (r.tipo() == GeografiaMapa.Tipo.RIO || (r.tipo() == GeografiaMapa.Tipo.SIERRA && r.casillas() < SIERRA_ATLAS))) {
				continue;
			}
			// que el accidente se vea más grande que su nombre: los ríos por lo largos, lo demás por el área
			// y que el nombre no sea más ancho que el propio accidente en pantalla (un mar lejano no lleva un letrero enorme)
			double largo = Math.max(r.ancho(), r.alto()) * s;
			boolean cabe = r.tipo() == GeografiaMapa.Tipo.RIO ? r.casillas() * s >= w * 0.7 && largo >= w * 0.8
					: r.casillas() * s * s >= w * 9 * 2.5 && r.ancho() * s >= w * 1.1 && r.alto() * s >= 14;
			if (!cabe) {
				continue;
			}
			int x = pantallaX(r.x()) - w / 2;
			int y = pantallaY(r.z()) - 4;
			if (x + w < x0 || x > x1 || y + 9 < y0 || y > y1) {
				continue;
			}
			if (!libre(x - 2, y - 1, x + w + 2, y + 10)) {
				continue;
			}
			g.drawString(font, t, x, y, color, false);
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
			// solo las de tierras ya exploradas: si no, quedan flotando en la niebla
			if (TerritoriosCliente.zona(Math.floorDiv(c.x(), CASILLA), Math.floorDiv(c.z(), CASILLA)) == null) {
				continue;
			}
			g.drawString(font, corona, x - font.width(corona) / 2, y - 4, c.perdida() ? 0xFF8A8577 : ORO, true);
		}
	}

	/** El nombre de cada reino en el centro de sus tierras conocidas, si cabe. */
	private void pintaNombres(GuiGraphics g) {
		if (nombresVersion != FichasCliente.version()) {
			nombresVersion = FichasCliente.version();
			nombres.clear();
			anchos.clear();
			for (PaqueteFichas.Reino r : FichasCliente.reinos()) {
				Component t = Component.literal(r.nombre().getString().toUpperCase(Locale.ROOT));
				nombres.put(Geo.clave(r.kx(), r.kz()), t.getVisualOrderText());
				anchos.put(Geo.clave(r.kx(), r.kz()), font.width(t));
			}
		}
		double s = escala * CASILLA;
		// los reinos grandes primero, que son los que se quedan el sitio si dos nombres chocan
		List<Map.Entry<Long, double[]>> orden = new ArrayList<>(centros.entrySet());
		orden.sort((a, b) -> Double.compare(b.getValue()[2], a.getValue()[2]));
		for (Map.Entry<Long, double[]> e : orden) {
			double[] c = e.getValue();
			FormattedCharSequence sinColor = nombres.get(e.getKey());
			if (sinColor == null || c[2] == 0) {
				continue;
			}
			int w = anchos.get(e.getKey());
			// que las tierras del reino ocupen al menos el doble del nombre; y si sobra sitio, el nombre más grande, como
			// en los atlas (hasta el triple)
			double area = c[2] * s * s;
			if (area < w * 9 * 2.0) {
				continue;
			}
			float x = (float) (cx() + (c[0] / c[2] * CASILLA - vistaX) * escala);
			float y = (float) (cy() + (c[1] / c[2] * CASILLA - vistaZ) * escala) + 6;
			// el tamaño más grande que quepa en sus tierras y no pise otro nombre; si ni a tamaño normal cabe, no sale
			float f = 0;
			for (float t : new float[] {3, 2, 1.5f, 1}) {
				if ((t == 1 || area >= w * t * 9 * t * 3.0) && w * t <= (c[4] - c[3]) * s * 1.1
						&& libre((int) (x - w * t / 2) - 2, (int) y - 2, (int) (x + w * t / 2) + 2, (int) (y + 9 * t) + 2)) {
					f = t;
					break;
				}
			}
			if (f == 0) {
				continue;
			}
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(f, f);
			// un halo de pergamino alrededor para que se lea encima de fronteras y colores
			int ox = -w / 2;
			for (int[] d : HALO) {
				g.drawString(font, sinColor, ox + d[0], d[1], HALO_COLOR, false);
			}
			g.drawString(font, sinColor, ox, 0, TINTA, false);
			g.pose().popMatrix();
		}
	}

	private static final int[][] HALO = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** Si el rectángulo está libre de rótulos; si lo está, lo aparta para el que lo pide. */
	private boolean libre(int xa, int ya, int xb, int yb) {
		for (int[] o : ocupados) {
			if (xa < o[2] && xb > o[0] && ya < o[3] && yb > o[1]) {
				return false;
			}
		}
		ocupados.add(new int[] {xa, ya, xb, yb});
		return true;
	}

	/** El nombre del bicho más típico de cada ducado, de cerca y en el modo Fauna (los raros en dorado). */
	private void pintaBichos(GuiGraphics g) {
		if (escala * CASILLA < 6) {
			return;
		}
		if (bichosVersion != FichasCliente.version()) {
			bichosVersion = FichasCliente.version();
			bichos.clear();
			int sal = TerritoriosCliente.sal();
			for (PaqueteFichas.Nombre d : FichasCliente.ducados()) {
				if (d.animales().isEmpty()) {
					continue;
				}
				// el raro si lo hay, que es lo interesante; si no, el que más sale
				Component a = d.animales().get(0);
				for (Component o : d.animales()) {
					if (FichasCliente.raro(o)) {
						a = Component.literal("✦ ").append(o);
						break;
					}
				}
				int[] c = Geo.centroDucado(d.dcx(), d.dcz(), sal);
				bichos.add(new Object[] {c, a.getVisualOrderText(), font.width(a), a.getString().startsWith("✦")});
			}
		}
		for (Object[] b : bichos) {
			int[] c = (int[]) b[0];
			int w = (Integer) b[2];
			int x = pantallaX(c[0]) - w / 2;
			int y = pantallaY(c[1]) + 6;
			if (x + w < x0 || x > x1 || y + 9 < y0 || y > y1 || !libre(x - 2, y - 1, x + w + 2, y + 10)) {
				continue;
			}
			FormattedCharSequence t = (FormattedCharSequence) b[1];
			for (int[] d : HALO) {
				g.drawString(font, t, x + d[0], y + d[1], HALO_COLOR, false);
			}
			g.drawString(font, t, x, y, (Boolean) b[3] ? 0xFF8A5A00 : 0xFF24401C, false);
		}
	}

	private final List<Object[]> bichos = new ArrayList<>();
	private int bichosVersion = -1;
	private static final int HALO_COLOR = 0xB0E3D3AE;

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
		if (leyendaModo != modo) {
			leyendaModo = modo;
			leyendaMuestras = new ArrayList<>();
			for (Object[] m : modo.leyenda()) {
				Component c = (Component) m[1];
				leyendaMuestras.add(new Object[] {m[0], c.getVisualOrderText(), font.width(c)});
			}
		}
		for (Object[] m : leyendaMuestras) {
			x = muestra(g, x, y, (Integer) m[0], (FormattedCharSequence) m[1], (Integer) m[2]);
		}
		Component ayuda = Component.translatableWithFallback("destinos.mapa.ayuda", "Arrastra · rueda: zoom · 1-8: modos · espacio: a ti · clic: fijar");
		if (x + font.width(ayuda) < x1) {
			g.drawString(font, ayuda, x1 - font.width(ayuda), y, TEXTO_SUAVE, false);
		}
	}

	private int muestra(GuiGraphics g, int x, int y, int color, FormattedCharSequence texto, int ancho) {
		g.fill(x, y, x + 8, y + 8, 0xFF000000);
		g.fill(x + 1, y + 1, x + 7, y + 7, color);
		g.drawString(font, texto, x + 11, y, TEXTO, false);
		return x + 11 + ancho + 10;
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
		Integer colorReino = coloresReino.get(Geo.clave(kx, kz));
		g.fill(tx + 1, y + 1, tx + 9, y + 13, colorReino == null ? 0xFF888888 : 0xFF000000 | colorReino);
		// el texto de la ficha (traducir y partir en líneas) solo se rehace si cambia el reino, las fichas o el tamaño
		String clave = kx + "," + kz + "," + (fijado != Long.MIN_VALUE) + "," + tx + "," + w + "," + y1 + "," + FichasCliente.version();
		if (clave.equals(fichaClave)) {
			for (Trazo t : fichaTrazos) {
				g.drawString(font, t.texto(), t.x(), t.y(), t.color(), false);
			}
			return;
		}
		fichaClave = clave;
		fichaTrazos.clear();
		grabando = fichaTrazos;
		fichaReino(g, r, tx, y, w);
		grabando = null;
	}

	/** Lo escrito en la ficha de un reino (se graba en fichaTrazos para los frames siguientes). */
	private void fichaReino(GuiGraphics g, PaqueteFichas.Reino r, int tx, int y, int w) {
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
		if (!r.cultura().isEmpty()) {
			y = linea(g, Component.translatableWithFallback("destinos.mapa.cultura", "Cultura: %s",
					Component.translatableWithFallback("destinos.cultura." + r.cultura(), r.cultura())), tx, y, w,
					ModoMapa.CULTURAS.getOrDefault(r.cultura(), TEXTO) | 0xFF000000);
		}
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
		y = linea(g, Component.translatableWithFallback(clave, respaldo, (rep > 0 ? "+" : "") + rep), tx, y, w,
				rep >= 20 ? 0xFF9FD08A : rep <= -20 ? 0xFFFF7A6A : TEXTO);
		if (!r.cronica().isEmpty() && y + 24 < y1) {
			y += 6;
			y = linea(g, Component.translatableWithFallback("destinos.mapa.cronica", "Crónica").withStyle(ChatFormatting.BOLD), tx, y, w, ORO)
					+ 2;
			for (Component c : r.cronica()) {
				if (y + 10 > y1 - 4) {
					break;
				}
				y = linea(g, Component.literal("· ").append(c), tx, y, w, TEXTO_SUAVE) + 2;
			}
		}
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
			if (grabando != null) {
				grabando.add(new Trazo(l, x, y, color));
			}
			y += 10;
		}
		return y;
	}

	/** Un trozo de texto ya partido y puesto en su sitio, para repintar la ficha sin rehacerla. */
	private record Trazo(FormattedCharSequence texto, int x, int y, int color) {
	}

	/** El cartelito junto al ratón: ducado, reino y si está en guerra. */
	private void pista(GuiGraphics g, PaqueteTerritorio.Ducado d, int mx, int my) {
		int bx = (int) Math.floor(bloqueX(mx));
		int bz = (int) Math.floor(bloqueZ(my));
		int tx = Math.floorDiv(bx, CASILLA);
		int tz = Math.floorDiv(bz, CASILLA);
		// las líneas solo se rehacen al cambiar de casilla, de modo o de turno (traducir y medir cada frame cuesta)
		if (pistaLineas == null || tx != pistaX || tz != pistaZ || modo != pistaModo || FichasCliente.turno() != pistaTurno
				|| d != pistaDucado) {
			pistaX = tx;
			pistaZ = tz;
			pistaModo = modo;
			pistaTurno = FichasCliente.turno();
			pistaDucado = d;
			List<Component> l = lineasPista(d, tx, tz);
			pistaLineas = new ArrayList<>();
			pistaAncho = 0;
			for (Component c : l) {
				pistaLineas.add(c.getVisualOrderText());
				pistaAncho = Math.max(pistaAncho, font.width(c));
			}
		}
		FormattedCharSequence coords = Component.literal(bx + ", " + bz).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText();
		int w = Math.max(pistaAncho, font.width(coords));
		int h = (pistaLineas.size() + 1) * 10 + 6;
		int x = mx + 12;
		int y = my - 4;
		if (x + w + 8 > x1) {
			x = mx - 12 - w - 8;
		}
		// que no se salga de la pantalla por ningún lado
		x = Math.max(2, Math.min(width - w - 10, x));
		y = Math.max(2, Math.min(height - h - 2, Math.max(y0, Math.min(y1 - h, y))));
		g.fill(x - 1, y - 1, x + w + 9, y + h + 1, 0xFF8A6D3F);
		g.fill(x, y, x + w + 8, y + h, 0xF0120F0B);
		int yy = y + 4;
		for (FormattedCharSequence c : pistaLineas) {
			g.drawString(font, c, x + 4, yy, TEXTO, false);
			yy += 10;
		}
		g.drawString(font, coords, x + 4, yy, TEXTO, false);
	}

	/** Lo que dice la pista de la casilla (tx, tz), sin las coordenadas. */
	private static List<Component> lineasPista(PaqueteTerritorio.Ducado d, int tx, int tz) {
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
		extras(lineas, d, tx, tz);
		return lineas;
	}

	/** Lo que añade a la pista cada modo: el peligro, los bichos o la altura. */
	private static void extras(List<Component> lineas, PaqueteTerritorio.Ducado d, int tx, int tz) {
		switch (modo) {
			case SEGURIDAD -> {
				int p = d.oceano() ? -1 : FichasCliente.peligro(d.dcx(), d.dcz());
				if (p >= 0) {
					String[] claves = {"seguro", "tranquilo", "peligroso", "muy_peligroso"};
					String[] respaldos = {"Seguro", "Tranquilo", "Peligroso", "Muy peligroso"};
					int q = Math.min(3, p);
					lineas.add(Component.translatableWithFallback("destinos.mapa.pista_peligro", "Peligro: %s",
							Component.translatableWithFallback("destinos.mapa.leyenda." + claves[q], respaldos[q]))
							.withColor(ModoMapa.PELIGRO[q] & 0xFFFFFF));
				}
			}
			case FAUNA -> {
				PaqueteFichas.Nombre f = d.oceano() ? null : FichasCliente.fichaDucado(d.dcx(), d.dcz());
				if (f != null && !f.animales().isEmpty()) {
					MutableComponent a = Component.empty();
					for (int k = 0; k < f.animales().size(); k++) {
						Component c = f.animales().get(k);
						if (k > 0) {
							a.append(", ");
						}
						a.append(FichasCliente.raro(c) ? Component.literal("✦ ").append(c).withStyle(ChatFormatting.GOLD) : c);
					}
					lineas.add(Component.translatableWithFallback("destinos.mapa.pista_animales", "Animales: %s", a).withStyle(ChatFormatting.GREEN));
				}
				if (f != null && !f.peligros().isEmpty()) {
					MutableComponent m = Component.empty();
					for (int k = 0; k < f.peligros().size(); k++) {
						if (k > 0) {
							m.append(", ");
						}
						m.append(f.peligros().get(k));
					}
					lineas.add(Component.translatableWithFallback("destinos.mapa.pista_bichos", "Ojo con: %s", m).withStyle(ChatFormatting.RED));
				}
			}
			case RELIEVE -> {
				int h = TerritoriosCliente.altura(tx, tz);
				if (h != TerritoriosCliente.SIN_ALTURA) {
					lineas.add(Component.translatableWithFallback(d.oceano() ? "destinos.mapa.pista_fondo" : "destinos.mapa.pista_altura",
							d.oceano() ? "Fondo a y=%s" : "Altura: y=%s", h).withStyle(ChatFormatting.AQUA));
				}
				PaqueteTerritorio z = TerritoriosCliente.zona(tx, tz);
				if (z != null && !d.oceano() && z.rio(tx - z.wx() * NUCLEO, tz - z.wz() * NUCLEO)) {
					lineas.add(Component.translatableWithFallback("destinos.mapa.pista_rio", "Pasa un río").withStyle(ChatFormatting.BLUE));
				}
			}
			default -> {
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
