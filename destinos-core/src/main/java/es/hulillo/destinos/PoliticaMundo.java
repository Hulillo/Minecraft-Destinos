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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * La política viva dentro de un mundo: va metiendo en el grafo las zonas que se calculan, juega un turno cada
 * cierto tiempo de juego, lo guarda en el storage destinos:politica y lo cuenta en el chat. Los registros de
 * reinos y ducados (nombres, colores) siguen siendo del datapack: se le piden con funciones de politica/.
 * Solo se usa desde el hilo del servidor.
 */
final class PoliticaMundo implements PaqueteTerritorio.Politica {
	private static final Identifier STORAGE = Identifier.fromNamespaceAndPath("destinos", "politica");
	private static final Identifier MUNDO = Identifier.fromNamespaceAndPath("destinos", "mundo");
	/** Un turno por día de juego, al amanecer. El año del mundo es el de las estaciones (Estaciones). */
	static final int TICKS_TURNO = 24000;
	/** Anuncios como mucho por tanda (al forzar varios turnos); el resto queda en la crónica. */
	private static final int MAX_ANUNCIOS = 10;

	private final Grafo grafo = new Grafo();
	private final Politica politica;
	private long siguiente;
	private int ticksTurno;
	private int forzados;
	/** Quien pidió los turnos forzados, para contarle cómo ha ido. */
	private CommandSourceStack pidio;

	private PoliticaMundo(Politica politica, long siguiente, int ticksTurno) {
		this.politica = politica;
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

	/** Turno y guerras abiertas, para que los mapas marquen cambios recientes y frentes. */
	PaquetePolitica estadoMapas() {
		List<PaquetePolitica.Guerra> guerras = new ArrayList<>();
		for (Politica.Par p : politica.guerras().keySet()) {
			guerras.add(new PaquetePolitica.Guerra(Grafo.x(p.a()), Grafo.z(p.a()), Grafo.x(p.b()), Grafo.z(p.b())));
		}
		return new PaquetePolitica(politica.turno(), guerras);
	}

	/** Mete en el grafo las zonas recién calculadas y fija el reino protegido. */
	void alimenta(MinecraftServer server, MapaTerreno mapa) {
		for (long clave : mapa.ventanasListas()) {
			if (grafo.conoce(clave)) {
				continue;
			}
			MapaTerreno.Vecindario v = mapa.vecindarioSiListo(clave);
			if (v != null) {
				grafo.agrega(clave, v);
			}
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

	private void funcion(MinecraftServer server, int x, int z, String comando) {
		CommandSourceStack fuente = server.createCommandSourceStack()
				.withLevel(server.overworld())
				.withPosition(new Vec3(x + 0.5, 64, z + 0.5))
				.withSuppressedOutput();
		server.getCommands().performPrefixedCommand(fuente, comando);
	}

	/** Que exista el registro del reino (nombre, casa, color), generado en el centro del reino. */
	private void aseguraReino(MinecraftServer server, long reino) {
		int kx = Grafo.x(reino);
		int kz = Grafo.z(reino);
		int[] c = Geo.centro(kx, kz, Geo.S_REINO, politica.sal() + Geo.K_REINO);
		funcion(server, c[0], c[1], "function destinos:politica/asegurar_reino {kx:" + kx + ",kz:" + kz + "}");
	}

	/** Dueño de hoy de un ducado conocido: {kx, kz, salvaje}. */
	private int[] duenoActual(long ducado) {
		Grafo.Ducado d = grafo.ducado(ducado);
		long dueno = d == null ? Politica.SALVAJE : politica.dueno(d);
		if (dueno == Politica.SALVAJE) {
			long base = d == null ? ducado : d.reinoBase();
			return new int[] {Grafo.x(base), Grafo.z(base), 1};
		}
		return new int[] {Grafo.x(dueno), Grafo.z(dueno), 0};
	}

	/** El registro del ducado sigue al nuevo dueño (o se borra para rehacerse al entrar). */
	private void actualizaDucado(MinecraftServer server, long ducado) {
		int[] o = duenoActual(ducado);
		if (o[2] == 0) {
			aseguraReino(server, Grafo.id(o[0], o[1]));
		}
		Grafo.Ducado d = grafo.ducado(ducado);
		boolean conquistado = d != null && !d.salvajeBase() && o[2] == 0 && Grafo.id(o[0], o[1]) != d.reinoBase();
		int[] c = Geo.centro(Grafo.x(ducado), Grafo.z(ducado), Geo.S_DUCADO, politica.sal());
		funcion(server, c[0], c[1], "function destinos:politica/cambio {dx:" + Grafo.x(ducado) + ",dz:" + Grafo.z(ducado)
				+ ",kx:" + o[0] + ",kz:" + o[1] + ",w:" + o[2] + ",c:" + (conquistado ? 1 : 0) + "}");
	}

	private void aseguraDucado(MinecraftServer server, long ducado) {
		int[] o = duenoActual(ducado);
		if (o[2] == 0) {
			aseguraReino(server, Grafo.id(o[0], o[1]));
		}
		int[] c = Geo.centro(Grafo.x(ducado), Grafo.z(ducado), Geo.S_DUCADO, politica.sal());
		funcion(server, c[0], c[1], "function destinos:politica/asegurar_ducado {dx:" + Grafo.x(ducado) + ",dz:"
				+ Grafo.z(ducado) + ",kx:" + o[0] + ",kz:" + o[1] + ",w:" + o[2] + "}");
	}

	// ------------------------------------------------------------------ textos

	private static CompoundTag registros(MinecraftServer server, String tipo) {
		return server.getCommandStorage().get(MUNDO).getCompoundOrEmpty(tipo);
	}

	private static int color(String c, int porDefecto) {
		if (c.startsWith("#")) {
			try {
				return Integer.parseInt(c.substring(1), 16);
			} catch (NumberFormatException e) {
				return porDefecto;
			}
		}
		ChatFormatting f = ChatFormatting.getByName(c);
		return f != null && f.getColor() != null ? f.getColor() : porDefecto;
	}

	/** "Reino de X" con su color, o "un reino lejano" si el datapack aún no lo conoce. */
	/** "X, Z" del centro de la celda, para verlo al pasar el ratón por un nombre del chat. */
	private static Component coordenadas(int[] c) {
		return Component.translatableWithFallback("destinos.politica.coordenadas", "Centro: %s, %s", c[0], c[1]);
	}

	private int[] centroReino(long reino) {
		return Geo.centro(Grafo.x(reino), Grafo.z(reino), Geo.S_REINO, politica.sal() + Geo.K_REINO);
	}

	private int[] centroDucado(long ducado) {
		return Geo.centro(Grafo.x(ducado), Grafo.z(ducado), Geo.S_DUCADO, politica.sal());
	}

	Component reino(MinecraftServer server, long reino) {
		CompoundTag r = registros(server, "reinos").getCompoundOrEmpty("k" + Grafo.x(reino) + "_" + Grafo.z(reino));
		if (!r.contains("nombre")) {
			return Component.translatableWithFallback("destinos.politica.lejano", "reino lejano");
		}
		String nombre = r.getStringOr("nombre", "?");
		Component pista = coordenadas(centroReino(reino));
		return Component.translatableWithFallback("destinos.reino", "Reino de %s", nombre)
				.withColor(color(r.getStringOr("color", "white"), 0xffffff))
				.withStyle(st -> st.withHoverEvent(new HoverEvent.ShowText(pista)));
	}

	Component ducado(MinecraftServer server, long ducado) {
		CompoundTag d = registros(server, "ducados").getCompoundOrEmpty("d" + Grafo.x(ducado) + "_" + Grafo.z(ducado));
		String nombre = d.getStringOr("nombre", "?");
		Component pista = coordenadas(centroDucado(ducado));
		MutableComponent res = d.getBooleanOr("salvaje", false)
				? Component.translatableWithFallback("destinos.salvaje." + d.getIntOr("geo", 0), nombre, nombre).withColor(0x9e9e9e)
				: Component.translatableWithFallback("destinos.tipo." + d.getStringOr("tipo", "ducado"), nombre, nombre)
						.withColor(color(d.getStringOr("color", "white"), 0xffffff));
		return res.withStyle(st -> st.withHoverEvent(new HoverEvent.ShowText(pista)));
	}

	private static final String[] RUMBOS = {"e", "se", "s", "so", "o", "no", "n", "ne"};

	/** " (a 1800 bloques al NE)" desde el jugador hasta donde pasa el suceso; vacío si no está en el mundo normal. */
	private Component donde(ServerPlayer p, Suceso s) {
		if (p.level().dimension() != Level.OVERWORLD || (s.ducado() == Politica.NADIE && s.reinoA() == Politica.NADIE)) {
			return Component.empty();
		}
		int[] c = s.ducado() != Politica.NADIE ? centroDucado(s.ducado()) : centroReino(s.reinoA());
		double dx = c[0] - p.getX();
		double dz = c[1] - p.getZ();
		int distancia = (int) Math.round(Math.sqrt(dx * dx + dz * dz) / 100) * 100;
		if (distancia < 200) {
			return Component.translatableWithFallback("destinos.politica.aqui", " (aquí mismo)").withStyle(ChatFormatting.DARK_GRAY);
		}
		// el norte es -z en Minecraft; atan2 con z hacia abajo da el rumbo en sentido horario desde el este
		int rumbo = Math.floorMod((int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4)), 8);
		return Component.translatableWithFallback("destinos.politica.donde", " (a %s bloques al %s)", distancia,
				Component.translatableWithFallback("destinos.rumbo." + RUMBOS[rumbo], RUMBOS[rumbo].toUpperCase()))
				.withStyle(ChatFormatting.DARK_GRAY);
	}

	/** Textos en español por si el cliente no tiene el idioma (los de verdad están en los lang de build.py). */
	private static final Map<Suceso.Tipo, String> RESPALDO = Map.ofEntries(
			Map.entry(Suceso.Tipo.guerra, "⚔ Año %1$s · El %2$s declara la guerra al %3$s."),
			Map.entry(Suceso.Tipo.paz, "☮ Año %1$s · El %2$s y el %3$s firman la paz."),
			Map.entry(Suceso.Tipo.conquista, "⚑ Año %1$s · %4$s pasa al %2$s tras la guerra con el %3$s."),
			Map.entry(Suceso.Tipo.cae, "☠ Año %1$s · El %2$s ha caído: el %3$s se queda con sus últimas tierras."),
			Map.entry(Suceso.Tipo.alianza, "✦ Año %1$s · El %2$s y el %3$s sellan una alianza."),
			Map.entry(Suceso.Tipo.ruptura, "✧ Año %1$s · Se rompe la alianza entre el %2$s y el %3$s."),
			Map.entry(Suceso.Tipo.vasallaje, "♛ Año %1$s · El %2$s jura vasallaje al %3$s."),
			Map.entry(Suceso.Tipo.independencia, "⚐ Año %1$s · El %2$s rompe el vasallaje y se declara libre del %3$s."),
			Map.entry(Suceso.Tipo.anexion, "♛ Año %1$s · El %2$s se anexiona a su vasallo, el %3$s."),
			Map.entry(Suceso.Tipo.disolucion, "☠ Año %1$s · El %2$s se hunde en el caos: sus tierras quedan sin señor."),
			Map.entry(Suceso.Tipo.expansion, "⚜ Año %1$s · El %2$s reclama %4$s, antes tierra salvaje."));

	private static boolean menor(Suceso.Tipo t) {
		return t == Suceso.Tipo.alianza || t == Suceso.Tipo.ruptura || t == Suceso.Tipo.expansion;
	}

	/** El texto de un suceso: "⚔ Año 3 · El Reino de A declara la guerra al Reino de B." */
	Component texto(MinecraftServer server, Suceso s) {
		Component a = s.reinoA() == Politica.NADIE ? Component.empty() : reino(server, s.reinoA());
		Component b = s.reinoB() == Politica.NADIE ? Component.empty() : reino(server, s.reinoB());
		Component d = s.ducado() == Politica.NADIE ? Component.empty() : ducado(server, s.ducado());
		MutableComponent t = Component.translatableWithFallback("destinos.politica." + s.tipo().name(),
				RESPALDO.get(s.tipo()), s.anio(), a, b, d);
		return t.withStyle(menor(s.tipo()) ? ChatFormatting.GRAY : ChatFormatting.WHITE);
	}

	private void anuncia(MinecraftServer server, List<Suceso> sucesos) {
		if (sucesos.isEmpty()) {
			return;
		}
		// los registros se piden antes de escribir nada, para que todos los nombres estén ya
		for (Suceso s : sucesos) {
			if (s.reinoA() != Politica.NADIE) {
				aseguraReino(server, s.reinoA());
			}
			if (s.reinoB() != Politica.NADIE) {
				aseguraReino(server, s.reinoB());
			}
			if (s.ducado() != Politica.NADIE) {
				aseguraDucado(server, s.ducado());
			}
		}
		boolean grave = false;
		for (int i = 0; i < sucesos.size() && i < MAX_ANUNCIOS; i++) {
			Suceso s = sucesos.get(i);
			Component t = texto(server, s);
			server.sendSystemMessage(t);
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				p.sendSystemMessage(Component.empty().append(t).append(donde(p, s)));
			}
			grave |= !menor(s.tipo()) && s.tipo() != Suceso.Tipo.paz;
		}
		if (sucesos.size() > MAX_ANUNCIOS) {
			server.getPlayerList().broadcastSystemMessage(Component.translatableWithFallback("destinos.politica.mas",
					"… y %s sucesos más (/destinoscore cronica)", sucesos.size() - MAX_ANUNCIOS)
					.withStyle(ChatFormatting.DARK_GRAY), false);
		}
		String sonido = grave ? "minecraft:item.goat_horn.sound.2 master @s ~ ~ ~ 0.5 1" : "minecraft:block.bell.use master @s ~ ~ ~ 0.4 1.2";
		funcion(server, 0, 0, "execute as @a at @s run playsound " + sonido);
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
		if (e != null && e.senor != Politica.NADIE) {
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.vasallo", "Vasallo del %s",
					reino(server, e.senor)).withStyle(ChatFormatting.GRAY), false);
		}
		List<Component> guerras = new ArrayList<>();
		List<Component> aliados = new ArrayList<>();
		politica.guerras().keySet().stream().filter(par -> par.a() == id || par.b() == id)
				.forEach(par -> guerras.add(reino(server, par.otro(id))));
		politica.alianzas().stream().filter(par -> par.a() == id || par.b() == id)
				.forEach(par -> aliados.add(reino(server, par.otro(id))));
		if (!guerras.isEmpty()) {
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.guerras", "⚔ En guerra con: %s",
					lista(guerras)).withStyle(ChatFormatting.RED), false);
		}
		if (!aliados.isEmpty()) {
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.aliados", "✦ Aliados: %s",
					lista(aliados)).withStyle(ChatFormatting.AQUA), false);
		}
		List<Component> relaciones = new ArrayList<>();
		for (long v : politica.vecinosDe(grafo, id)) {
			relaciones.add(Component.translatableWithFallback("destinos.politica.relacion", "%s (%s)",
					reino(server, v), politica.relacion(id, v)));
		}
		if (!relaciones.isEmpty()) {
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.politica.relaciones",
					"Vecinos (relación de -100 a 100): %s", lista(relaciones)).withStyle(ChatFormatting.GRAY), false);
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
			Component t = texto(fuente.getServer(), s);
			fuente.sendSuccess(() -> t, false);
		}
	}

	private int reinosVivos() {
		Set<Long> vivos = new HashSet<>();
		for (Grafo.Ducado d : grafo.ducados().values()) {
			long r = politica.dueno(d);
			if (r != Politica.SALVAJE) {
				vivos.add(r);
			}
		}
		return vivos.size();
	}

	private static Component lista(List<Component> partes) {
		MutableComponent res = Component.empty();
		for (int i = 0; i < partes.size(); i++) {
			if (i > 0) {
				res.append(", ");
			}
			res.append(partes.get(i));
		}
		return res;
	}
}
