package es.hulillo.destinos;

import es.hulillo.destinos.politica.Gente;
import es.hulillo.destinos.politica.Grafo;
import es.hulillo.destinos.politica.Persona;
import es.hulillo.destinos.politica.Politica;
import es.hulillo.destinos.politica.Suceso;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * Los nombres de reinos y ducados y los textos de la política. Los registros (nombre, casa, color) son del
 * datapack, en el storage destinos:mundo: se le piden con sus funciones de politica/ antes de escribir nada.
 * Solo se usa desde el hilo del servidor.
 */
final class NombresPolitica {
	private static final Identifier MUNDO = Identifier.fromNamespaceAndPath("destinos", "mundo");
	private static final String[] RUMBOS = {"e", "se", "s", "so", "o", "no", "n", "ne"};
	private static final String[] UNIONES = {"Corona de %s y %s", "Unión de %s y %s", "Liga de %s y %s",
		"Mancomunidad de %s y %s"};

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
			Map.entry(Suceso.Tipo.expansion, "⚜ Año %1$s · El %2$s reclama %4$s, antes tierra salvaje."),
			Map.entry(Suceso.Tipo.capital, "♛ Año %1$s · ¡El %2$s toma %4$s, la capital del %3$s!"),
			Map.entry(Suceso.Tipo.union, "❖ Año %1$s · El %2$s y el %3$s se unen: nace la %4$s."),
			Map.entry(Suceso.Tipo.fin_union, "✧ Año %1$s · Se rompe la %4$s: el %2$s y el %3$s vuelven a ir cada uno por su lado."),
			Map.entry(Suceso.Tipo.muerte, "✝ Año %1$s · Muere %5$s del %2$s (%7$s)."),
			Map.entry(Suceso.Tipo.sucesion, "♛ Año %1$s · Sube al trono del %2$s %5$s."),
			Map.entry(Suceso.Tipo.crisis, "⚠ Año %1$s · Sin heredero, el %2$s se tambalea: %5$s se hace con la corona."),
			Map.entry(Suceso.Tipo.boda, "💍 Año %1$s · Boda de %5$s del %2$s y %6$s del %3$s: la alianza queda sellada."),
			Map.entry(Suceso.Tipo.golpe, "⚔ Año %1$s · Golpe en el %2$s: %5$s derroca a %6$s."),
			Map.entry(Suceso.Tipo.caido, "✝ Año %1$s · Cae en la guerra %5$s, del %2$s."),
			Map.entry(Suceso.Tipo.herencia, "❖ Año %1$s · Una sola corona para el %2$s y el %3$s: la hereda %5$s."),
			Map.entry(Suceso.Tipo.renombre, "★ Año %1$s · Nace una leyenda en el %2$s: %5$s."),
			Map.entry(Suceso.Tipo.nacimiento, "✦ Año %1$s · Nace %5$s en la familia real del %2$s."),
			Map.entry(Suceso.Tipo.asesinato, "☠ Año %1$s · Asesinato en el %2$s: %8$s mata a %5$s."));
	/** Los mismos sucesos cuando han ayudado jugadores (%5$s: quiénes). */
	private static final Map<Suceso.Tipo, String> RESPALDO_AYUDA = Map.of(
			Suceso.Tipo.conquista, "⚑ Año %1$s · Con la ayuda de %5$s, el %2$s toma %4$s al %3$s.",
			Suceso.Tipo.capital, "♛ Año %1$s · ¡Con la ayuda de %5$s, el %2$s toma %4$s, la capital del %3$s!",
			Suceso.Tipo.paz, "☮ Año %1$s · Gracias a la embajada de %5$s, el %2$s y el %3$s firman la paz.",
			Suceso.Tipo.alianza, "✦ Año %1$s · Gracias a la embajada de %5$s, el %2$s y el %3$s sellan una alianza.");

	/** Quién manda hoy en un ducado: el reino (kx, kz), o el reino de origen si es tierra salvaje. */
	record Dueno(int kx, int kz, boolean salvaje) {
		long reino() {
			return Grafo.id(kx, kz);
		}
	}

	private final int sal;
	/**
	 * Lo que ya se ha pedido al datapack en esta partida, para no volver a llamar a sus funciones (cada llamada es un
	 * comando con macro, y los tablones y el HUD piden nombres a menudo). Las claves se mezclan (Geo.mezcla) por las colisiones de Long.hashCode.
	 */
	private final Set<Long> reinosPedidos = new HashSet<>();
	private final Map<Long, Dueno> ducadosPedidos = new HashMap<>();

	NombresPolitica(int sal) {
		this.sal = sal;
	}

	// ------------------------------------------------------------------ registros del datapack

	/** Ejecuta una función del datapack en (x, z), sin que escriba nada en el chat. */
	void funcion(MinecraftServer server, int x, int z, String comando) {
		CommandSourceStack fuente = server.createCommandSourceStack()
				.withLevel(server.overworld())
				.withPosition(new Vec3(x + 0.5, 64, z + 0.5))
				.withSuppressedOutput();
		server.getCommands().performPrefixedCommand(fuente, comando);
	}

	/** Que exista el registro del reino (nombre, casa, color), generado en el centro del reino. */
	void aseguraReino(MinecraftServer server, long reino) {
		if (!reinosPedidos.add(Geo.mezcla(reino))) {
			return;
		}
		int kx = Grafo.x(reino);
		int kz = Grafo.z(reino);
		int[] c = Geo.centroReino(kx, kz, sal);
		funcion(server, c[0], c[1], "function destinos:politica/asegurar_reino {kx:" + kx + ",kz:" + kz + "}");
	}

	/** Que exista el registro del ducado, con su dueño de hoy. */
	void aseguraDucado(MinecraftServer server, long ducado, Dueno dueno) {
		if (dueno.equals(ducadosPedidos.put(Geo.mezcla(ducado), dueno))) {
			return;
		}
		llamaDucado(server, ducado, dueno, "asegurar_ducado", "");
	}

	/** El registro del ducado sigue al nuevo dueño. conquistado: lo tiene otro reino con señor que el de origen. */
	void cambiaDucado(MinecraftServer server, long ducado, Dueno dueno, boolean conquistado) {
		ducadosPedidos.put(Geo.mezcla(ducado), dueno);
		llamaDucado(server, ducado, dueno, "cambio", ",c:" + (conquistado ? 1 : 0));
	}

	private void llamaDucado(MinecraftServer server, long ducado, Dueno o, String funcion, String extra) {
		if (!o.salvaje()) {
			aseguraReino(server, o.reino());
		}
		int[] c = Geo.centroDucado(Grafo.x(ducado), Grafo.z(ducado), sal);
		funcion(server, c[0], c[1], "function destinos:politica/" + funcion + " {dx:" + Grafo.x(ducado) + ",dz:" + Grafo.z(ducado)
				+ ",kx:" + o.kx() + ",kz:" + o.kz() + ",w:" + (o.salvaje() ? 1 : 0) + extra + "}");
	}

	private static CompoundTag registros(MinecraftServer server, String tipo) {
		return server.getCommandStorage().get(MUNDO).getCompoundOrEmpty(tipo);
	}

	private static CompoundTag registroReino(MinecraftServer server, long reino) {
		return registros(server, "reinos").getCompoundOrEmpty("k" + Grafo.x(reino) + "_" + Grafo.z(reino));
	}

	// ------------------------------------------------------------------ nombres

	/** "Reino de X" con su color, o "reino lejano" si el datapack aún no lo conoce. */
	Component reino(MinecraftServer server, long reino) {
		CompoundTag r = registroReino(server, reino);
		if (!r.contains("nombre")) {
			return lejano();
		}
		int[] c = Geo.centroReino(Grafo.x(reino), Grafo.z(reino), sal);
		return Component.translatableWithFallback("destinos.reino", "Reino de %s", r.getStringOr("nombre", "?"))
				.withColor(color(r))
				.withStyle(st -> enMapa(st, c[0], c[1]));
	}

	/**
	 * Que el texto lleve las coordenadas al pasar el ratón y abra el mapa político en ese sitio al hacer clic (con
	 * /destinosver, que no pide permisos).
	 */
	static net.minecraft.network.chat.Style enMapa(net.minecraft.network.chat.Style st, int x, int z) {
		Component pista = Component.empty().append(coordenadas(x, z)).append("\n")
				.append(Component.translatableWithFallback("destinos.politica.ver_mapa", "Clic: verlo en el mapa")
						.withStyle(net.minecraft.ChatFormatting.GRAY));
		return st.withHoverEvent(new HoverEvent.ShowText(pista))
				.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/destinosver " + x + " " + z));
	}

	/** La cultura del reino ("norte", "desierto"…, como en culturas.py), o "" si el datapack aún no lo conoce. */
	String cultura(MinecraftServer server, long reino) {
		return registroReino(server, reino).getStringOr("cultura", "");
	}

	/** Nombre a secas de un reino ("Valmora"), con su color. */
	private Component nombre(MinecraftServer server, long reino) {
		CompoundTag r = registroReino(server, reino);
		if (!r.contains("nombre")) {
			return lejano();
		}
		return Component.literal(r.getStringOr("nombre", "?")).withColor(color(r));
	}

	private static Component lejano() {
		return Component.translatableWithFallback("destinos.politica.lejano", "reino lejano");
	}

	Component ducado(MinecraftServer server, long ducado) {
		CompoundTag d = registros(server, "ducados").getCompoundOrEmpty("d" + Grafo.x(ducado) + "_" + Grafo.z(ducado));
		String nombre = d.getStringOr("nombre", "?");
		int[] c = Geo.centroDucado(Grafo.x(ducado), Grafo.z(ducado), sal);
		MutableComponent res = d.getBooleanOr("salvaje", false)
				? Component.translatableWithFallback("destinos.salvaje." + d.getIntOr("geo", 0), nombre, nombre).withColor(0x9e9e9e)
				: Component.translatableWithFallback("destinos.tipo." + d.getStringOr("tipo", "ducado"), nombre, nombre)
						.withColor(color(d));
		return res.withStyle(st -> enMapa(st, c[0], c[1]));
	}

	/** "Corona de A y B": el tipo de unión sale del par, así que siempre se llama igual. */
	Component nombreUnion(MinecraftServer server, long primero, long segundo) {
		long x = primero ^ segundo;
		int i = Math.floorMod(Geo.hash((int) x, (int) (x >>> 32), sal + 13), UNIONES.length);
		return Component.translatableWithFallback("destinos.union." + i, UNIONES[i], nombre(server, primero), nombre(server, segundo));
	}

	/** "Centro: X, Z", para verlo al pasar el ratón por un nombre del chat. */
	static Component coordenadas(int x, int z) {
		return Component.translatableWithFallback("destinos.politica.coordenadas", "Centro: %s, %s", x, z);
	}

	/** El color de un registro del datapack ("#rrggbb" o un nombre de color de Minecraft); blanco si no se entiende. */
	private static int color(CompoundTag registro) {
		String c = registro.getStringOr("color", "white");
		if (c.startsWith("#")) {
			try {
				return Integer.parseInt(c.substring(1), 16);
			} catch (NumberFormatException e) {
				return 0xffffff;
			}
		}
		ChatFormatting f = ChatFormatting.getByName(c);
		return f != null && f.getColor() != null ? f.getColor() : 0xffffff;
	}

	// ------------------------------------------------------------------ sucesos

	/** Alianzas, rupturas, expansiones, bodas y nacimientos: se escriben en gris y no suenan a cuerno. */
	static boolean menor(Suceso.Tipo t) {
		return t == Suceso.Tipo.alianza || t == Suceso.Tipo.ruptura || t == Suceso.Tipo.expansion || t == Suceso.Tipo.boda
				|| t == Suceso.Tipo.nacimiento || t == Suceso.Tipo.caido;
	}

	// ------------------------------------------------------------------ gente (v0.7)

	/** Listas de nombres de cada cultura ("norte/m", "norte/casas"…, del storage destinos:tablas), para no leerlas cada vez. */
	private final Map<String, List<String>> tablas = new HashMap<>();

	/** Los nombres de la gente salen de las tablas del datapack y de los registros de los reinos. */
	Gente.Fuente fuente(MinecraftServer server) {
		return new Gente.Fuente() {
			@Override
			public String cultura(long reino) {
				return NombresPolitica.this.cultura(server, reino);
			}

			@Override
			public String casa(long reino) {
				return registroReino(server, reino).getStringOr("casa", "");
			}

			@Override
			public List<String> nombres(String cultura, boolean mujer) {
				return lista(server, cultura, mujer ? "f" : "m");
			}

			@Override
			public List<String> casas(String cultura) {
				return lista(server, cultura, "casas");
			}
		};
	}

	private List<String> lista(MinecraftServer server, String cultura, String campo) {
		String clave = cultura + "/" + campo;
		List<String> res = tablas.get(clave);
		if (res != null) {
			return res;
		}
		net.minecraft.nbt.ListTag l = server.getCommandStorage().get(Identifier.fromNamespaceAndPath("destinos", "tablas"))
				.getCompoundOrEmpty(cultura).getListOrEmpty(campo);
		List<String> leida = new java.util.ArrayList<>(l.size());
		for (int i = 0; i < l.size(); i++) {
			String v = l.getStringOr(i, "");
			if (!v.isEmpty()) {
				leida.add(v);
			}
		}
		// vacía si el datapack aún no la ha cargado: entonces no se guarda, para volver a mirar
		if (!leida.isEmpty()) {
			tablas.put(clave, List.copyOf(leida));
		}
		return leida;
	}

	/** Quién gobierna hoy un ducado: su duque, o el rey en la capital. */
	record Senor(long ducado, Persona persona, boolean rey) {
	}

	/**
	 * Los señores vivos de los ducados (la gente) pasan a los registros del datapack, para que el título al entrar
	 * los nombre. Solo los ducados con registro y con señor; el storage se lee y se escribe una vez para todos.
	 */
	void fijaSenores(MinecraftServer server, List<Senor> senores) {
		if (senores.isEmpty()) {
			return;
		}
		CompoundTag mundo = server.getCommandStorage().get(MUNDO);
		CompoundTag ducados = mundo.getCompoundOrEmpty("ducados");
		boolean cambia = false;
		for (Senor s : senores) {
			String clave = "d" + Grafo.x(s.ducado()) + "_" + Grafo.z(s.ducado());
			if (!ducados.contains(clave)) {
				continue;
			}
			CompoundTag d = ducados.getCompoundOrEmpty(clave);
			Persona x = s.persona();
			// en la capital manda el rey: el título lo dice con destinos.titulo.<tipo>.rey_m / rey_f
			String senor = s.rey() ? x.nombreCorto() : x.nombre;
			String g = (s.rey() ? "rey_" : "") + (x.mujer ? "f" : "m");
			if (d.getBooleanOr("salvaje", false) || (senor.equals(d.getStringOr("senor", "")) && g.equals(d.getStringOr("g", "")))) {
				continue;
			}
			d.putString("senor", senor);
			d.putString("g", g);
			ducados.put(clave, d);
			cambia = true;
		}
		if (cambia) {
			mundo.put("ducados", ducados);
			server.getCommandStorage().set(MUNDO, mundo);
		}
	}

	/** "Aldric II el Bravo" (con su mote si lo tiene), con la casa, la edad y el carácter al pasar el ratón. */
	static MutableComponent persona(Persona x, int anio) {
		Component pista = pista(x, anio);
		return nombre(x).withStyle(st -> st.withHoverEvent(new HoverEvent.ShowText(pista)).withColor(0xE8D9A8));
	}

	/** "Aldric II el Bravo": el nombre con su número y su mote, sin más. */
	static MutableComponent nombre(Persona x) {
		MutableComponent nombre = Component.literal(x.nombreCorto());
		return x.mote == null ? nombre
				: Component.translatableWithFallback("destinos.gente.nombre_mote", "%s %s", nombre,
						Component.translatableWithFallback("destinos.gente.mote." + x.mote + "." + (x.mujer ? "f" : "m"), x.mote));
	}

	/** Lo que sale al pasar el ratón por alguien: casa, edad y carácter (o de qué murió). */
	static Component pista(Persona x, int anio) {
		return x.vivo()
				? Component.translatableWithFallback("destinos.gente.pista", "Casa %s · %s años · %s y %s\nGuerra %s · Diplomacia %s · Intriga %s",
						x.casa, x.edad(anio), rasgo(x, x.rasgo1), rasgo(x, x.rasgo2), x.guerra, x.diplomacia, x.intriga)
				: Component.translatableWithFallback("destinos.gente.pista_muerto", "Casa %s · murió con %s años, %s", x.casa, x.edad(anio),
						causa(x));
	}

	/** "el rey Aldric II", "la duquesa Elvira"… con el cargo de hoy (o el último que tuvo). */
	static MutableComponent conTitulo(Persona x, int anio) {
		return Component.translatableWithFallback("destinos.gente.titulo." + x.rol.name() + "." + (x.mujer ? "f" : "m"), "%s", persona(x, anio));
	}

	static Component rasgo(Persona x, Persona.Rasgo r) {
		return Component.translatableWithFallback("destinos.gente.rasgo." + r.name() + "." + (x.mujer ? "f" : "m"), r.name());
	}

	static Component causa(Persona x) {
		String c = x.causa == null ? "enfermedad" : x.causa.name();
		return Component.translatableWithFallback("destinos.gente.causa." + c, c);
	}

	/** El texto de un suceso: "⚔ Año 3 · El Reino de A declara la guerra al Reino de B." */
	Component texto(MinecraftServer server, Suceso s) {
		Component a = s.reinoA() == Politica.NADIE ? Component.empty() : reino(server, s.reinoA());
		Component b = s.reinoB() == Politica.NADIE ? Component.empty() : reino(server, s.reinoB());
		Component d = s.tipo() == Suceso.Tipo.union || s.tipo() == Suceso.Tipo.fin_union
				? nombreUnion(server, s.reinoA(), s.reinoB())
				: s.ducado() == Politica.NADIE ? Component.empty() : ducado(server, s.ducado());
		if (s.personaA() != Persona.NADIE) {
			return textoGente(server, s, a, b, d);
		}
		String ayuda = s.ayuda() != null ? RESPALDO_AYUDA.get(s.tipo()) : null;
		MutableComponent t = ayuda != null
				// sucesos en los que han ayudado jugadores (campamentos arrasados, cartas entregadas): sale quién
				? Component.translatableWithFallback("destinos.politica." + s.tipo().name() + "_ayuda", ayuda, s.anio(), a, b, d,
						Component.literal(s.ayuda()).withStyle(ChatFormatting.GOLD))
				: Component.translatableWithFallback("destinos.politica." + s.tipo().name(), RESPALDO.get(s.tipo()), s.anio(), a, b, d);
		return t.withStyle(menor(s.tipo()) ? ChatFormatting.GRAY : ChatFormatting.WHITE);
	}

	/** La política del mundo, para pintar la gente de la crónica; null hasta que se fija. */
	private Politica politica;

	void fijaPolitica(Politica politica) {
		this.politica = politica;
	}

	/** Un suceso con personas: %5$s y %6$s son la gente con su título, %7$s la causa de la muerte y %8$s quién (jugadores). */
	private Component textoGente(MinecraftServer server, Suceso s, Component a, Component b, Component d) {
		Gente gente = politica == null ? null : politica.gente();
		int anio = politica == null ? s.anio() : politica.anio();
		Persona pa = gente == null ? null : gente.persona(s.personaA());
		Persona pb = gente == null || s.personaB() == Persona.NADIE ? null : gente.persona(s.personaB());
		Component x = pa == null ? Component.literal("?") : conTitulo(pa, anio);
		Component y = pb == null ? Component.empty() : conTitulo(pb, anio);
		Component causa = pa == null ? Component.empty() : causa(pa);
		Component quien = Component.literal(s.ayuda() == null ? "?" : s.ayuda()).withStyle(ChatFormatting.GOLD);
		String clave = s.tipo().name();
		String respaldo = RESPALDO.get(s.tipo());
		if (s.tipo() == Suceso.Tipo.boda && s.reinoB() == Politica.NADIE) {
			clave = "boda_noble";
			respaldo = "💍 Año %1$s · Boda en el %2$s: %5$s se casa con %6$s.";
		} else if (s.tipo() == Suceso.Tipo.caido && s.ducado() != Politica.NADIE) {
			clave = "caido_ducado";
			respaldo = "✝ Año %1$s · Cae %5$s, del %2$s, luchando por %4$s.";
		}
		return Component.translatableWithFallback("destinos.politica." + clave, respaldo, s.anio(), a, b, d, x, y, causa, quien)
				.withStyle(menor(s.tipo()) ? ChatFormatting.GRAY : ChatFormatting.WHITE);
	}

	/** " (a 1800 bloques al NE)" desde el jugador hasta donde pasa el suceso; vacío si no está en el mundo normal. */
	Component donde(ServerPlayer p, Suceso s) {
		if (p.level().dimension() != Level.OVERWORLD || (s.ducado() == Politica.NADIE && s.reinoA() == Politica.NADIE)) {
			return Component.empty();
		}
		int[] c = s.ducado() != Politica.NADIE ? Geo.centroDucado(Grafo.x(s.ducado()), Grafo.z(s.ducado()), sal)
				: Geo.centroReino(Grafo.x(s.reinoA()), Grafo.z(s.reinoA()), sal);
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

	// ------------------------------------------------------------------ ayudas

	/** "A, B, C". */
	static Component lista(List<Component> partes) {
		MutableComponent res = Component.empty();
		for (int i = 0; i < partes.size(); i++) {
			if (i > 0) {
				res.append(", ");
			}
			res.append(partes.get(i));
		}
		return res;
	}

	/** Habitantes redondeados a centenas (son una estimación). */
	static int redondea(int n) {
		return n < 100 ? n : Math.round(n / 100f) * 100;
	}
}
