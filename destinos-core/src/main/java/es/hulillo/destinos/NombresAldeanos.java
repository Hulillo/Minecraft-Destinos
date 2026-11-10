package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;

/**
 * Los aldeanos de Humanoid Villagers con nombre de la cultura de su ducado (Kwame en la sabana, Astrid en el norte…)
 * en vez de los de su lista. Su datapack saca el maniquí (con la etiqueta humvil.human.needs_config) encima del
 * aldeano, que ya tiene echado el sexo, y justo después le copia el nombre al aldeano: aquí, al aparecer el
 * maniquí, se le cambia el nombre, y el aldeano se lleva el nuestro. Sin tocar su mod: si no está, nunca aparece
 * esa etiqueta. Los nombres, de las tablas del datapack (destinos:tablas cultura.m / cultura.f), sin repetir los de
 * los vecinos de alrededor mientras queden.
 */
final class NombresAldeanos {
	private static final String NUEVO = "humvil.human.needs_config";
	private static final String HUMANO = "humvil.human";
	private static final String TRANSFORMADO = "humvil.human.transformed";
	private static final String SEXO = "humvil.transform.gender";
	private static final Identifier TABLAS = Identifier.fromNamespaceAndPath("destinos", "tablas");
	/** Hasta dónde se miran los nombres de los vecinos para no repetirlos. */
	private static final double VECINOS = 64;

	private NombresAldeanos() {
	}

	static void alCargar(Entity e, ServerLevel level) {
		if (!e.getTags().contains(NUEVO) || level.dimension() != Level.OVERWORLD) {
			return;
		}
		MinecraftServer server = level.getServer();
		Boolean mujer = mujer(server, level, e);
		if (mujer == null) {
			return;
		}
		List<String> nombres = nombres(server, cultura(server, level, e.getBlockX(), e.getBlockZ()), mujer);
		if (nombres.isEmpty()) {
			return;
		}
		e.setCustomName(Component.literal(elige(level, e, nombres)));
	}

	/** El sexo que echó Humanoid Villagers al aldeano de debajo (0 hombre, 1 mujer), o null si no se encuentra. */
	private static Boolean mujer(MinecraftServer server, ServerLevel level, Entity maniqui) {
		Objective o = server.getScoreboard().getObjective(SEXO);
		if (o == null) {
			return null;
		}
		for (Entity x : level.getEntities(maniqui, maniqui.getBoundingBox().inflate(0.5),
				x -> !x.getTags().contains(TRANSFORMADO) && !x.getTags().contains(HUMANO) && !x.getTags().contains(NUEVO))) {
			ReadOnlyScoreInfo s = server.getScoreboard().getPlayerScoreInfo(x, o);
			if (s != null) {
				return s.value() == 1;
			}
		}
		return null;
	}

	/** La cultura del ducado donde está, o la de la tierra de ese sitio si aún no se sabe de qué ducado es. */
	private static String cultura(MinecraftServer server, ServerLevel level, int x, int z) {
		MapaTerreno m = DestinosCore.mapa(server);
		if (m == null) {
			return "llanura";
		}
		CulturaDucados c = CulturaDucados.de(level, m.sal());
		MapaTerreno.Resultado r = m.consultaSiLista(x, z);
		return r != null ? c.de(r.dcx(), r.dcz()) : c.enSitio(x, z);
	}

	private static List<String> nombres(MinecraftServer server, String cultura, boolean mujer) {
		ListTag l = server.getCommandStorage().get(TABLAS).getCompoundOrEmpty(cultura).getListOrEmpty(mujer ? "f" : "m");
		List<String> res = new ArrayList<>(l.size());
		for (int i = 0; i < l.size(); i++) {
			String v = l.getStringOr(i, "");
			if (!v.isEmpty()) {
				res.add(v);
			}
		}
		return res;
	}

	/** Uno al azar que no lleve ya ningún vecino; si están todos cogidos, uno cualquiera. */
	private static String elige(ServerLevel level, Entity maniqui, List<String> nombres) {
		Set<String> usados = new HashSet<>();
		for (Entity x : level.getEntities(maniqui, maniqui.getBoundingBox().inflate(VECINOS), x -> x.getTags().contains(HUMANO))) {
			Component n = x.getCustomName();
			if (n != null) {
				usados.add(n.getString());
			}
		}
		List<String> libres = new ArrayList<>(nombres);
		libres.removeAll(usados);
		List<String> de = libres.isEmpty() ? nombres : libres;
		return de.get(level.getRandom().nextInt(de.size()));
	}
}
