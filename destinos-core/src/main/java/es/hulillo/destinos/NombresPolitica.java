package es.hulillo.destinos;

import es.hulillo.destinos.politica.Grafo;
import es.hulillo.destinos.politica.Politica;
import es.hulillo.destinos.politica.Suceso;
import java.util.List;
import java.util.Map;
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
			Map.entry(Suceso.Tipo.fin_union, "✧ Año %1$s · Se rompe la %4$s: el %2$s y el %3$s vuelven a ir cada uno por su lado."));

	/** Quién manda hoy en un ducado: el reino (kx, kz), o el reino de origen si es tierra salvaje. */
	record Dueno(int kx, int kz, boolean salvaje) {
		long reino() {
			return Grafo.id(kx, kz);
		}
	}

	private final int sal;

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
		int kx = Grafo.x(reino);
		int kz = Grafo.z(reino);
		int[] c = Geo.centroReino(kx, kz, sal);
		funcion(server, c[0], c[1], "function destinos:politica/asegurar_reino {kx:" + kx + ",kz:" + kz + "}");
	}

	/** Que exista el registro del ducado, con su dueño de hoy. */
	void aseguraDucado(MinecraftServer server, long ducado, Dueno dueno) {
		llamaDucado(server, ducado, dueno, "asegurar_ducado", "");
	}

	/** El registro del ducado sigue al nuevo dueño. conquistado: lo tiene otro reino con señor que el de origen. */
	void cambiaDucado(MinecraftServer server, long ducado, Dueno dueno, boolean conquistado) {
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
				.withStyle(st -> st.withHoverEvent(new HoverEvent.ShowText(coordenadas(c[0], c[1]))));
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
		return res.withStyle(st -> st.withHoverEvent(new HoverEvent.ShowText(coordenadas(c[0], c[1]))));
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

	/** Alianzas, rupturas y expansiones: se escriben en gris y no suenan a cuerno. */
	static boolean menor(Suceso.Tipo t) {
		return t == Suceso.Tipo.alianza || t == Suceso.Tipo.ruptura || t == Suceso.Tipo.expansion;
	}

	/** El texto de un suceso: "⚔ Año 3 · El Reino de A declara la guerra al Reino de B." */
	Component texto(MinecraftServer server, Suceso s) {
		Component a = s.reinoA() == Politica.NADIE ? Component.empty() : reino(server, s.reinoA());
		Component b = s.reinoB() == Politica.NADIE ? Component.empty() : reino(server, s.reinoB());
		Component d = s.tipo() == Suceso.Tipo.union || s.tipo() == Suceso.Tipo.fin_union
				? nombreUnion(server, s.reinoA(), s.reinoB())
				: s.ducado() == Politica.NADIE ? Component.empty() : ducado(server, s.ducado());
		MutableComponent t = Component.translatableWithFallback("destinos.politica." + s.tipo().name(),
				RESPALDO.get(s.tipo()), s.anio(), a, b, d);
		return t.withStyle(menor(s.tipo()) ? ChatFormatting.GRAY : ChatFormatting.WHITE);
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
