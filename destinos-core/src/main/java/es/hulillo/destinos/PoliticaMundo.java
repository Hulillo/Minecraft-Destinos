package es.hulillo.destinos;

import es.hulillo.destinos.politica.Grafo;
import es.hulillo.destinos.politica.Politica;
import es.hulillo.destinos.politica.Suceso;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * La política viva dentro de un mundo: va metiendo en el grafo las zonas que se calculan, juega un turno cada
 * cierto tiempo de juego, lo guarda en el storage destinos:politica y lo cuenta en el chat. Los registros de
 * reinos y ducados (nombres, colores) siguen siendo del datapack: se le piden con funciones de politica/.
 * Solo se usa desde el hilo del servidor.
 */
final class PoliticaMundo implements PaqueteTerritorio.Politica {
	private static final Identifier STORAGE = Identifier.fromNamespaceAndPath("destinos", "politica");
	/** Un turno por día de juego, al amanecer. El año del mundo es el de las estaciones (Estaciones). */
	static final int TICKS_TURNO = 24000;
	/** Anuncios como mucho por tanda (al forzar varios turnos); el resto queda en la crónica. */
	private static final int MAX_ANUNCIOS = 10;
	/** Sucesos de cada reino que salen en su ficha del mapa político. */
	private static final int CRONICA_FICHA = 4;

	private final Grafo grafo = new Grafo();
	private final Politica politica;
	private final NombresPolitica nombres;
	/** Capitales ya elegidas de los reinos conocidos (no se guardan: salen siempre igual del terreno). */
	private final Map<Long, MapaTerreno.Capital> capitales = new HashMap<>();
	/** Reinos sin sitio para capital (no se vuelve a mirar). */
	private final Set<Long> sinCapital = new HashSet<>();
	private long siguiente;
	private int ticksTurno;
	private int forzados;
	/** Quien pidió los turnos forzados, para contarle cómo ha ido. */
	private CommandSourceStack pidio;

	private PoliticaMundo(Politica politica, long siguiente, int ticksTurno) {
		this.politica = politica;
		this.nombres = new NombresPolitica(politica.sal());
		this.siguiente = siguiente;
		this.ticksTurno = ticksTurno;
	}

	/** Carga la política guardada de esta sal, o empieza una nueva. */
	static PoliticaMundo de(MinecraftServer server, int sal) {
		GuardadoPolitica.Cargado c = GuardadoPolitica.carga(server.getCommandStorage().get(STORAGE), sal);
		long ahora = server.overworld().getDayTime();
		if (c == null) {
			return new PoliticaMundo(new Politica(sal), proximo(ahora, TICKS_TURNO), TICKS_TURNO);
		}
		DestinosCore.LOG.info("Destinos Core: política cargada (año {}, {} ducados con otro dueño)",
				c.politica().anio(), c.politica().cambios().size());
		int ticks = c.ticksTurno() > 0 ? c.ticksTurno() : TICKS_TURNO;
		return new PoliticaMundo(c.politica(), Math.min(c.siguiente(), proximo(ahora, ticks)), ticks);
	}

	/**
	 * El amanecer en que toca el siguiente turno. Se mide con la hora del mundo (no con los ticks jugados) para que
	 * dormir también haga pasar los días, igual que las estaciones.
	 */
	private static long proximo(long ahora, int ticksTurno) {
		return (Math.floorDiv(ahora, TICKS_TURNO) + ticksTurno / TICKS_TURNO) * TICKS_TURNO;
	}

	int sal() {
		return politica.sal();
	}

	int turno() {
		return politica.turno();
	}

	/** El territorio con el dueño de hoy encima. */
	@Override
	public MapaTerreno.Resultado aplica(MapaTerreno.Resultado r) {
		return politica.aplica(r);
	}

	@Override
	public int turnoCambio(int dcx, int dcz) {
		return politica.turnoCambio(dcx, dcz);
	}

	/** Turno, guerras abiertas y capitales, para que los mapas marquen cambios recientes, frentes y coronas. */
	PaquetePolitica estadoMapas() {
		List<PaquetePolitica.Guerra> guerras = new ArrayList<>();
		for (Politica.Par p : politica.guerras().keySet()) {
			guerras.add(new PaquetePolitica.Guerra(Grafo.x(p.a()), Grafo.z(p.a()), Grafo.x(p.b()), Grafo.z(p.b())));
		}
		List<PaquetePolitica.Capital> coronas = new ArrayList<>();
		capitales.forEach((reino, c) -> coronas.add(new PaquetePolitica.Capital(c.x(), c.z(), Grafo.x(reino), Grafo.z(reino),
				politica.capitalPerdida(grafo, reino))));
		return new PaquetePolitica(politica.turno(), guerras, coronas);
	}

	/**
	 * Mete en el grafo las zonas recién calculadas, apunta las capitales que ya se han elegido y fija el reino
	 * protegido. Devuelve si hay capitales nuevas (para mandarlas a los mapas).
	 */
	boolean alimenta(MinecraftServer server, MapaTerreno mapa) {
		for (long clave : mapa.ventanasListas()) {
			if (grafo.conoce(clave)) {
				continue;
			}
			MapaTerreno.Vecindario v = mapa.vecindarioSiListo(clave);
			if (v != null) {
				grafo.agrega(clave, v);
			}
		}
		boolean nuevas = false;
		Set<Long> mirados = new HashSet<>();
		for (Grafo.Ducado d : grafo.ducados().values()) {
			long reino = d.reinoBase();
			if (d.salvajeBase() || !mirados.add(reino) || capitales.containsKey(reino) || sinCapital.contains(reino)) {
				continue;
			}
			java.util.Optional<MapaTerreno.Capital> c = mapa.capitalSiLista(Grafo.x(reino), Grafo.z(reino));
			if (c == null) {
				continue;
			}
			if (c.isEmpty()) {
				sinCapital.add(reino);
				continue;
			}
			capitales.put(reino, c.get());
			grafo.fijaCapital(reino, Grafo.id(c.get().dcx(), c.get().dcz()));
			nuevas = true;
		}
		if (politica.reinoProtegido() == Politica.NADIE) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.level().dimension() != Level.OVERWORLD) {
					continue;
				}
				MapaTerreno.Resultado r = mapa.consultaSiLista(p.getBlockX(), p.getBlockZ());
				MapaTerreno.Resultado a = r == null ? null : aplica(r);
				if (a != null && !a.oceano() && !a.salvaje()) {
					politica.protege(Grafo.id(a.kx(), a.kz()));
					guarda(server);
					break;
				}
			}
		}
		return nuevas;
	}

	/** Juega los turnos que tocan. Devuelve las ventanas con ducados que han cambiado de dueño (a reenviar a los mapas). */
	Set<Long> tick(MinecraftServer server) {
		if (grafo.ducados().isEmpty()) {
			return Set.of();
		}
		long ahora = server.overworld().getDayTime();
		int turnos = forzados;
		forzados = 0;
		if (siguiente - ahora > ticksTurno) {
			// alguien ha echado el reloj atrás con /time set
			siguiente = proximo(ahora, ticksTurno);
		}
		if (ahora >= siguiente) {
			turnos++;
			siguiente = proximo(ahora, ticksTurno);
		}
		return turnos > 0 ? juega(server, turnos) : Set.of();
	}

	/** Pide n turnos ya (se juegan en el siguiente tick, fuera del comando, para que el datapack responda). */
	void fuerza(int n, CommandSourceStack fuente) {
		forzados += n;
		pidio = fuente;
	}

	void ritmo(MinecraftServer server, int dias) {
		ticksTurno = dias * TICKS_TURNO;
		siguiente = proximo(server.overworld().getDayTime(), ticksTurno);
		guarda(server);
	}

	private Set<Long> juega(MinecraftServer server, int turnos) {
		Map<Long, Politica.Cambio> antes = new HashMap<>(politica.cambios());
		List<Suceso> sucesos = new ArrayList<>();
		politica.fijaAnio(Estaciones.anio(server.overworld()));
		for (int i = 0; i < turnos; i++) {
			politica.turno(grafo, sucesos::add);
		}
		if (pidio != null) {
			resumen(pidio, turnos, sucesos.size());
			pidio = null;
		}
		Set<Long> cambiados = new HashSet<>(antes.keySet());
		cambiados.addAll(politica.cambios().keySet());
		cambiados.removeIf(d -> Objects.equals(antes.get(d), politica.cambios().get(d)));
		Set<Long> ventanas = new HashSet<>();
		for (long d : cambiados) {
			actualizaDucado(server, d);
			ventanas.addAll(grafo.ventanasDe(d));
		}
		anuncia(server, sucesos);
		guarda(server);
		return ventanas;
	}

	/** Tras forzar turnos: cuántos sucesos ha habido y, si ninguno, por qué. */
	private void resumen(CommandSourceStack fuente, int turnos, int sucesos) {
		int reinos = reinosVivos();
		fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.resumen",
				"Han pasado %s turnos: %s sucesos · %s ducados y %s reinos conocidos", turnos, sucesos,
				grafo.ducados().size(), reinos).withStyle(ChatFormatting.GOLD), false);
		if (sucesos == 0 && reinos < 3) {
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.pocos",
					"Aún se conocen pocos reinos alrededor, así que no tienen con quién pelearse ni aliarse. El mundo conocido crece solo con el tiempo y al explorar.")
					.withStyle(ChatFormatting.GRAY), false);
		}
	}

	void guarda(MinecraftServer server) {
		server.getCommandStorage().set(STORAGE, GuardadoPolitica.guarda(politica, siguiente, ticksTurno));
	}

	// ------------------------------------------------------------------ datapack

	/** Dueño de hoy de un ducado conocido. */
	private NombresPolitica.Dueno duenoActual(long ducado) {
		Grafo.Ducado d = grafo.ducado(ducado);
		long dueno = d == null ? Politica.SALVAJE : politica.dueno(d);
		if (dueno == Politica.SALVAJE) {
			long base = d == null ? ducado : d.reinoBase();
			return new NombresPolitica.Dueno(Grafo.x(base), Grafo.z(base), true);
		}
		return new NombresPolitica.Dueno(Grafo.x(dueno), Grafo.z(dueno), false);
	}

	/** El registro del ducado sigue al nuevo dueño (o se borra para rehacerse al entrar). */
	private void actualizaDucado(MinecraftServer server, long ducado) {
		NombresPolitica.Dueno o = duenoActual(ducado);
		Grafo.Ducado d = grafo.ducado(ducado);
		boolean conquistado = d != null && !d.salvajeBase() && !o.salvaje() && o.reino() != d.reinoBase();
		nombres.cambiaDucado(server, ducado, o, conquistado);
	}

	private void aseguraDucado(MinecraftServer server, long ducado) {
		nombres.aseguraDucado(server, ducado, duenoActual(ducado));
	}

	Component reino(MinecraftServer server, long reino) {
		return nombres.reino(server, reino);
	}

	// ------------------------------------------------------------------ mapa político

	/**
	 * Las fichas del mapa político para un jugador: cada reino vivo con su poder y relaciones, y el nombre de cada
	 * ducado conocido. Pide los registros que falten al datapack (una vez por partida cada uno). p puede ser null
	 * (prueba de la CI): entonces la reputación sale 0.
	 */
	PaqueteFichas fichas(MinecraftServer server, ServerPlayer p, EncargosMundo encargos) {
		record Fila(long reino, Politica.Resumen resumen) {
		}
		List<Fila> filas = new ArrayList<>();
		for (long r : politica.reinosVivos(grafo)) {
			filas.add(new Fila(r, politica.resumen(grafo, r)));
		}
		filas.sort((a, b) -> Integer.compare(b.resumen().poder(), a.resumen().poder()));
		List<Suceso> todos = new ArrayList<>(politica.cronica());
		List<PaqueteFichas.Reino> reinos = new ArrayList<>(filas.size());
		for (int i = 0; i < filas.size(); i++) {
			long id = filas.get(i).reino();
			Politica.Resumen res = filas.get(i).resumen();
			Politica.Reino e = politica.reinos().get(id);
			Component capital = Component.empty();
			boolean perdida = false;
			MapaTerreno.Capital c = capitales.get(id);
			if (c != null) {
				long ducado = Grafo.id(c.dcx(), c.dcz());
				capital = nombreDucado(server, ducado);
				perdida = politica.capitalPerdida(grafo, id);
			}
			List<int[]> guerras = new ArrayList<>();
			List<int[]> aliados = new ArrayList<>();
			for (Politica.Par par : politica.guerras().keySet()) {
				if (par.tiene(id)) {
					guerras.add(celda(par.otro(id)));
				}
			}
			for (Politica.Par par : politica.alianzas()) {
				if (par.tiene(id)) {
					aliados.add(celda(par.otro(id)));
				}
			}
			// lo último que ha pasado con el reino, lo más reciente primero
			List<Component> cronica = new ArrayList<>();
			for (int k = todos.size() - 1; k >= 0 && cronica.size() < CRONICA_FICHA; k--) {
				Suceso s = todos.get(k);
				if (s.reinoA() == id || s.reinoB() == id) {
					cronica.add(nombres.texto(server, s));
				}
			}
			int[] senor = e != null && e.senor != Politica.NADIE ? celda(e.senor) : null;
			int fondo = AdornoCapital.tinte(Geo.colorReino(Grafo.x(id), Grafo.z(id), sal()));
			int[] union = null;
			Component nombreUnion = Component.empty();
			Politica.Par u = politica.unionDe(id);
			if (u != null) {
				long otro = u.otro(id);
				boolean primero = res.poder() >= politica.resumen(grafo, otro).poder();
				union = celda(otro);
				nombreUnion = nombres.nombreUnion(server, primero ? id : otro, primero ? otro : id);
			}
			reinos.add(new PaqueteFichas.Reino(Grafo.x(id), Grafo.z(id), nombreReino(server, id), res.poder(),
					NombresPolitica.redondea(res.habitantes()), res.ducados(), e == null ? 60 : e.estabilidad, i + 1, capital, perdida,
					senor, guerras, aliados, p == null ? 0 : encargos.rep(p, id), nombres.cultura(server, id), cronica, fondo,
					Estandarte.de(Grafo.x(id), Grafo.z(id), sal(), fondo), union, nombreUnion));
		}
		Map<Long, Integer> peligro = peligros(server);
		FaunaDucados fauna = FaunaDucados.de(server.overworld(), sal());
		List<PaqueteFichas.Nombre> ducados = new ArrayList<>(grafo.ducados().size());
		for (long id : grafo.ducados().keySet()) {
			FaunaDucados.Fauna f = fauna.de(Grafo.x(id), Grafo.z(id));
			ducados.add(new PaqueteFichas.Nombre(Grafo.x(id), Grafo.z(id), nombreDucado(server, id), Math.min(3, peligro.getOrDefault(id, 0)),
					f.animales(), f.peligros()));
		}
		return new PaqueteFichas(Estaciones.anio(server.overworld()), politica.turno(), reinos, ducados);
	}

	/**
	 * Lo peligroso que es cada ducado conocido (de 0 a 3, para el modo Seguridad): tierra salvaje (bandidos) +1, reino
	 * en guerra +1, frente de una guerra +2, reino en crisis +1 y una mazmorra despierta dentro +1.
	 */
	private Map<Long, Integer> peligros(MinecraftServer server) {
		Map<Long, Integer> res = new HashMap<>();
		Set<Long> enGuerra = new HashSet<>();
		for (Politica.Par par : politica.guerras().keySet()) {
			enGuerra.add(par.a());
			enGuerra.add(par.b());
			for (long frente : new long[] {politica.frente(grafo, par.a(), par.b()), politica.frente(grafo, par.b(), par.a())}) {
				if (frente != Politica.NADIE) {
					res.merge(frente, 2, Integer::sum);
				}
			}
		}
		for (Grafo.Ducado d : grafo.ducados().values()) {
			long dueno = politica.dueno(d);
			int n = 0;
			if (dueno == Politica.SALVAJE) {
				n++;
			} else {
				if (enGuerra.contains(dueno)) {
					n++;
				}
				Politica.Reino e = politica.reinos().get(dueno);
				if (e != null && e.estabilidad < 35) {
					n++;
				}
			}
			if (n > 0) {
				res.merge(d.id(), n, Integer::sum);
			}
		}
		MapaTerreno m = DestinosCore.mapaActual();
		if (m != null) {
			for (int[] xz : MazmorrasMundo.de(server).despiertasXZ()) {
				MapaTerreno.Resultado r = m.consultaSiLista(xz[0], xz[1]);
				if (r != null) {
					res.merge(Grafo.id(r.dcx(), r.dcz()), 1, Integer::sum);
				}
			}
		}
		return res;
	}

	private static int[] celda(long id) {
		return new int[] {Grafo.x(id), Grafo.z(id)};
	}

	// ------------------------------------------------------------------ para los encargos

	Grafo grafo() {
		return grafo;
	}

	/** Guerras abiertas en las que está el reino: el otro bando de cada una. */
	List<Long> enemigos(long reino) {
		List<Long> l = new ArrayList<>();
		for (Politica.Par p : politica.guerras().keySet()) {
			if (p.tiene(reino)) {
				l.add(p.otro(reino));
			}
		}
		return l;
	}

	/** Cuántas guerras abiertas hay y en cuántas se encuentra frente para los encargos de guerra (prueba de CI). */
	int[] frentes() {
		int n = 0;
		int con = 0;
		for (Politica.Par p : politica.guerras().keySet()) {
			n++;
			if (politica.frente(grafo, p.a(), p.b()) != Politica.NADIE) {
				con++;
			}
		}
		return new int[] {n, con};
	}

	/** El ducado del enemigo por el que ataca el reino (Politica.frente), o Politica.NADIE. */
	long frente(long reino, long enemigo) {
		return politica.frente(grafo, reino, enemigo);
	}

	/** Un jugador ha arrasado un campamento del enemigo en el ducado: cuántos lleva el reino ahí (0 si ya no hay guerra). */
	int empuja(MinecraftServer server, long ducado, long reino, String quien) {
		int n = politica.empuja(grafo, ducado, reino, quien);
		if (n > 0) {
			guarda(server);
		}
		return n;
	}

	/** Dueño de hoy de un ducado conocido: un reino, o Politica.SALVAJE (también si no se conoce). */
	long duenoDe(long ducado) {
		Grafo.Ducado d = grafo.ducado(ducado);
		return d == null ? Politica.SALVAJE : politica.dueno(d);
	}

	/** Nombre del ducado, pidiendo antes su registro al datapack. */
	Component nombreDucado(MinecraftServer server, long ducado) {
		aseguraDucado(server, ducado);
		return nombres.ducado(server, ducado);
	}

	/** "Reino de X", pidiendo antes su registro al datapack. */
	Component nombreReino(MinecraftServer server, long reino) {
		nombres.aseguraReino(server, reino);
		return nombres.reino(server, reino);
	}

	/** La capital del reino si se conoce y sigue siendo suya; si no, null. */
	MapaTerreno.Capital capitalViva(long reino) {
		MapaTerreno.Capital c = capitales.get(reino);
		return c == null || politica.capitalPerdida(grafo, reino) ? null : c;
	}

	/** Reinos que tocan las tierras del reino. */
	java.util.TreeSet<Long> vecinos(long reino) {
		return politica.vecinosDe(grafo, reino);
	}

	Politica.Carta carta(long a, long b) {
		return politica.carta(a, b);
	}

	/** Un jugador ha entregado una carta del reino a al b (Politica.embajada). */
	Politica.Carta embajada(MinecraftServer server, long a, long b, String quien) {
		Politica.Carta c = politica.embajada(a, b, quien);
		if (c != Politica.Carta.nada) {
			guarda(server);
		}
		return c;
	}

	boolean enGuerra(long a, long b) {
		return politica.guerras().containsKey(Politica.Par.de(a, b));
	}

	/**
	 * La capital conocida a menos de r bloques de (x, z) y quién manda hoy en ella: {dueño, x, z}, o null. El dueño
	 * puede ser Politica.SALVAJE si ha quedado sin señor.
	 */
	long[] capitalCerca(int x, int z, int r) {
		for (MapaTerreno.Capital c : capitales.values()) {
			long dx = c.x() - x;
			long dz = c.z() - z;
			if (dx * dx + dz * dz < (long) r * r) {
				return new long[] {duenoDe(Grafo.id(c.dcx(), c.dcz())), c.x(), c.z()};
			}
		}
		return null;
	}

	/** Si (x, z) está a menos de r bloques de alguna capital conocida. */
	boolean cercaDeCapital(int x, int z, int r) {
		for (MapaTerreno.Capital c : capitales.values()) {
			long dx = c.x() - x;
			long dz = c.z() - z;
			if (dx * dx + dz * dz < (long) r * r) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Lo que dice el cartel de una capital del ducado (dcx, dcz): el nombre del ducado y el reino que manda hoy en él
	 * (null si es tierra salvaje). null del todo si el ducado aún no se conoce.
	 */
	Component[] cartel(MinecraftServer server, int dcx, int dcz) {
		long ducado = Grafo.id(dcx, dcz);
		if (grafo.ducado(ducado) == null) {
			return null;
		}
		aseguraDucado(server, ducado);
		NombresPolitica.Dueno o = duenoActual(ducado);
		if (!o.salvaje()) {
			nombres.aseguraReino(server, o.reino());
		}
		return new Component[] {nombres.ducado(server, ducado), o.salvaje() ? null : nombres.reino(server, o.reino())};
	}

	private void anuncia(MinecraftServer server, List<Suceso> sucesos) {
		if (sucesos.isEmpty()) {
			return;
		}
		// los registros se piden antes de escribir nada, para que todos los nombres estén ya
		for (Suceso s : sucesos) {
			if (s.reinoA() != Politica.NADIE) {
				nombres.aseguraReino(server, s.reinoA());
			}
			if (s.reinoB() != Politica.NADIE) {
				nombres.aseguraReino(server, s.reinoB());
			}
			if (s.ducado() != Politica.NADIE) {
				aseguraDucado(server, s.ducado());
			}
		}
		boolean grave = false;
		for (int i = 0; i < sucesos.size() && i < MAX_ANUNCIOS; i++) {
			Suceso s = sucesos.get(i);
			Component t = nombres.texto(server, s);
			server.sendSystemMessage(t);
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				p.sendSystemMessage(Component.empty().append(t).append(nombres.donde(p, s)));
			}
			grave |= !NombresPolitica.menor(s.tipo()) && s.tipo() != Suceso.Tipo.paz;
		}
		if (sucesos.size() > MAX_ANUNCIOS) {
			server.getPlayerList().broadcastSystemMessage(Component.translatableWithFallback("destinos.politica.mas",
					"… y %s sucesos más (/destinoscore cronica)", sucesos.size() - MAX_ANUNCIOS)
					.withStyle(ChatFormatting.DARK_GRAY), false);
		}
		String sonido = grave ? "minecraft:item.goat_horn.sound.2 master @s ~ ~ ~ 0.5 1" : "minecraft:block.bell.use master @s ~ ~ ~ 0.4 1.2";
		nombres.funcion(server, 0, 0, "execute as @a at @s run playsound " + sonido);
	}

	// ------------------------------------------------------------------ comandos

	void estado(CommandSourceStack fuente, MapaTerreno mapa) {
		MinecraftServer server = fuente.getServer();
		long faltan = Math.max(0, siguiente - server.overworld().getDayTime());
		fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.estado",
				"Año %s · turno %s · siguiente turno en %s min · %s ducados y %s reinos conocidos",
				Estaciones.anio(server.overworld()), politica.turno(), (faltan + 1199) / 1200, grafo.ducados().size(), reinosVivos())
				.withStyle(ChatFormatting.GOLD), false);
		ServerPlayer p = fuente.getPlayer();
		if (p == null || p.level().dimension() != Level.OVERWORLD) {
			return;
		}
		MapaTerreno.Resultado r = mapa.consultaSiLista(p.getBlockX(), p.getBlockZ());
		if (r == null) {
			return;
		}
		r = aplica(r);
		if (r.salvaje() || r.oceano()) {
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.salvaje", "Aquí no manda nadie.")
					.withStyle(ChatFormatting.GRAY), false);
			return;
		}
		long id = Grafo.id(r.kx(), r.kz());
		Politica.Reino e = politica.reinos().get(id);
		int estabilidad = e == null ? 60 : e.estabilidad;
		fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.reino", "%s · estabilidad %s/100",
				reino(server, id), estabilidad), false);
		Politica.Resumen res = politica.resumen(grafo, id);
		fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.poder",
				"Poder %s · unos %s habitantes · %s ducados · comida %s, mineral %s, madera %s",
				res.poder(), NombresPolitica.redondea(res.habitantes()), res.ducados(), res.comida(), res.mineral(), res.madera())
				.withStyle(ChatFormatting.YELLOW), false);
		Component capital = capital(server, id);
		if (capital != null) {
			fuente.sendSuccess(() -> capital, false);
		}
		Politica.Par union = politica.unionDe(id);
		if (union != null) {
			long otro = union.otro(id);
			boolean primero = res.poder() >= politica.resumen(grafo, otro).poder();
			Component nombre = nombres.nombreUnion(server, primero ? id : otro, primero ? otro : id);
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.en_union", "❖ Forma parte de la %s",
					nombre).withStyle(ChatFormatting.LIGHT_PURPLE), false);
		}
		if (e != null && e.senor != Politica.NADIE) {
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.vasallo", "Vasallo del %s",
					reino(server, e.senor)).withStyle(ChatFormatting.GRAY), false);
		}
		List<Component> guerras = new ArrayList<>();
		List<Component> aliados = new ArrayList<>();
		politica.guerras().keySet().stream().filter(par -> par.tiene(id))
				.forEach(par -> guerras.add(reino(server, par.otro(id))));
		politica.alianzas().stream().filter(par -> par.tiene(id))
				.forEach(par -> aliados.add(reino(server, par.otro(id))));
		if (!guerras.isEmpty()) {
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.guerras", "⚔ En guerra con: %s",
					NombresPolitica.lista(guerras)).withStyle(ChatFormatting.RED), false);
		}
		if (!aliados.isEmpty()) {
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.aliados", "✦ Aliados: %s",
					NombresPolitica.lista(aliados)).withStyle(ChatFormatting.AQUA), false);
		}
		List<Component> relaciones = new ArrayList<>();
		for (long v : politica.vecinosDe(grafo, id)) {
			relaciones.add(Component.translatableWithFallback("destinos.politica.relacion", "%s (%s)",
					reino(server, v), politica.relacion(id, v)));
		}
		if (!relaciones.isEmpty()) {
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.relaciones",
					"Vecinos (relación de -100 a 100): %s", NombresPolitica.lista(relaciones)).withStyle(ChatFormatting.GRAY), false);
		}
	}

	/** "♛ Capital: X (x, z)", o en manos de quién está si se ha perdido; null si aún no se sabe. */
	private Component capital(MinecraftServer server, long reino) {
		MapaTerreno.Capital c = capitales.get(reino);
		if (c == null) {
			return null;
		}
		long ducado = Grafo.id(c.dcx(), c.dcz());
		aseguraDucado(server, ducado);
		MutableComponent sitio = Component.empty().append(nombres.ducado(server, ducado))
				.append(Component.literal(" (" + c.x() + ", " + c.z() + ")").withStyle(ChatFormatting.DARK_GRAY))
				.withStyle(st -> NombresPolitica.enMapa(st, c.x(), c.z()));
		Component nombre = c.montana()
				? Component.translatableWithFallback("destinos.politica.capital_montana", "%s (en las montañas)", sitio)
				: sitio;
		Grafo.Ducado d = grafo.ducado(ducado);
		long dueno = d == null ? reino : politica.dueno(d);
		if (dueno != reino && dueno != Politica.SALVAJE) {
			return Component.translatableWithFallback("destinos.politica.capital_perdida", "♛ Capital: %s, en manos del %s",
					nombre, reino(server, dueno)).withStyle(ChatFormatting.RED);
		}
		return Component.translatableWithFallback("destinos.politica.capital_info", "♛ Capital: %s", nombre)
				.withStyle(ChatFormatting.GOLD);
	}

	/** Los reinos conocidos más poderosos. */
	void ranking(CommandSourceStack fuente) {
		MinecraftServer server = fuente.getServer();
		record Fila(long reino, Politica.Resumen resumen) {
		}
		List<Fila> filas = new ArrayList<>();
		for (long r : politica.reinosVivos(grafo)) {
			filas.add(new Fila(r, politica.resumen(grafo, r)));
		}
		filas.sort((a, b) -> Integer.compare(b.resumen().poder(), a.resumen().poder()));
		fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.ranking",
				"♛ Reinos más poderosos que se conocen").withStyle(ChatFormatting.GOLD), false);
		for (int i = 0; i < filas.size() && i < 10; i++) {
			Fila f = filas.get(i);
			nombres.aseguraReino(server, f.reino());
			int puesto = i + 1;
			Component linea = Component.translatableWithFallback("destinos.politica.ranking_linea",
					"%s. %s · poder %s · unos %s habitantes · %s ducados", puesto, reino(server, f.reino()), f.resumen().poder(),
					NombresPolitica.redondea(f.resumen().habitantes()), f.resumen().ducados());
			fuente.sendSuccess(() -> linea, false);
		}
	}

	void cronica(CommandSourceStack fuente) {
		List<Suceso> todos = new ArrayList<>(politica.cronica());
		if (todos.isEmpty()) {
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.cronica_vacia",
					"La crónica está vacía: aún no ha pasado nada.").withStyle(ChatFormatting.GRAY), false);
			return;
		}
		fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.cronica", "📜 Crónica del mundo")
				.withStyle(ChatFormatting.GOLD), false);
		for (Suceso s : todos.subList(Math.max(0, todos.size() - 12), todos.size())) {
			Component t = nombres.texto(fuente.getServer(), s);
			fuente.sendSuccess(() -> t, false);
		}
	}

	private int reinosVivos() {
		return politica.reinosVivos(grafo).size();
	}
}
