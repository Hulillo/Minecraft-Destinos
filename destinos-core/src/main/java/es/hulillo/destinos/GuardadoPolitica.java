package es.hulillo.destinos;

import es.hulillo.destinos.politica.Gente;
import es.hulillo.destinos.politica.Persona;
import es.hulillo.destinos.politica.Politica;
import es.hulillo.destinos.politica.Suceso;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * La política se guarda en el storage destinos:politica del mundo (se ve con /data get storage destinos:politica).
 * Si la sal guardada no es la del mundo actual, se empieza de cero.
 */
final class GuardadoPolitica {
	private GuardadoPolitica() {
	}

	/** Estado guardado: la política y cuándo toca el siguiente turno (hora del mundo, en ticks). */
	record Cargado(Politica politica, long siguiente, int ticksTurno) {
	}

	static CompoundTag guarda(Politica p, long siguiente, int ticksTurno) {
		CompoundTag t = new CompoundTag();
		t.putInt("sal", p.sal());
		t.putInt("turno", p.turno());
		t.putLong("protegido", p.reinoProtegido());
		t.putLong("siguiente", siguiente);
		t.putInt("ticks_turno", ticksTurno);
		ListTag reinos = new ListTag();
		p.reinos().forEach((id, r) -> {
			CompoundTag c = new CompoundTag();
			c.putLong("id", id);
			c.putInt("estabilidad", r.estabilidad);
			c.putLong("senor", r.senor);
			c.putInt("turnos_vasallo", r.turnosVasallo);
			c.putBoolean("caido", r.caido);
			reinos.add(c);
		});
		t.put("reinos", reinos);
		ListTag cambios = new ListTag();
		p.cambios().forEach((d, c) -> {
			CompoundTag x = new CompoundTag();
			x.putLong("ducado", d);
			x.putLong("dueno", c.dueno());
			x.putInt("lealtad", c.lealtad());
			x.putInt("anio", c.anio());
			x.putBoolean("noble", c.baseNoble());
			x.putInt("turno", c.turno());
			cambios.add(x);
		});
		t.put("cambios", cambios);
		t.put("relaciones", pares(p.relaciones(), "valor"));
		t.put("guerras", pares(p.guerras(), "turnos"));
		ListTag alianzas = new ListTag();
		for (Politica.Par par : p.alianzas()) {
			alianzas.add(par(par));
		}
		t.put("alianzas", alianzas);
		t.put("alianza_desde", pares(p.alianzaDesde(), "turno"));
		ListTag uniones = new ListTag();
		for (Politica.Par par : p.uniones()) {
			uniones.add(par(par));
		}
		t.put("uniones", uniones);
		ListTag cronica = new ListTag();
		for (Suceso s : p.cronica()) {
			CompoundTag c = new CompoundTag();
			c.putString("tipo", s.tipo().name());
			c.putInt("anio", s.anio());
			c.putLong("a", s.reinoA());
			c.putLong("b", s.reinoB());
			c.putLong("ducado", s.ducado());
			if (s.ayuda() != null) {
				c.putString("ayuda", s.ayuda());
			}
			if (s.personaA() != Persona.NADIE) {
				c.putInt("pa", s.personaA());
			}
			if (s.personaB() != Persona.NADIE) {
				c.putInt("pb", s.personaB());
			}
			cronica.add(c);
		}
		t.put("cronica", cronica);
		ListTag empujones = new ListTag();
		p.empujones().forEach((d, e) -> {
			CompoundTag c = new CompoundTag();
			c.putLong("ducado", d);
			c.putLong("reino", e.reino());
			c.putInt("campamentos", e.campamentos());
			c.putInt("turno", e.turno());
			c.putString("ayuda", e.ayuda());
			empujones.add(c);
		});
		t.put("empujones", empujones);
		ListTag embajadas = new ListTag();
		p.embajadas().forEach((par, ayuda) -> {
			CompoundTag c = par(par);
			c.putString("ayuda", ayuda);
			embajadas.add(c);
		});
		t.put("embajadas", embajadas);
		t.put("gente", guardaGente(p.gente()));
		return t;
	}

	private static CompoundTag guardaGente(Gente g) {
		CompoundTag t = new CompoundTag();
		t.putInt("siguiente", g.siguiente());
		ListTag personas = new ListTag();
		for (Persona x : g.personas().values()) {
			CompoundTag c = new CompoundTag();
			c.putInt("id", x.id);
			c.putString("nombre", x.nombre);
			c.putString("casa", x.casa);
			c.putString("cultura", x.cultura);
			c.putBoolean("mujer", x.mujer);
			c.putInt("nace", x.nace);
			c.putString("rol", x.rol.name());
			c.putLong("reino", x.reino);
			c.putLong("ducado", x.ducado);
			c.putInt("padre", x.padre);
			c.putInt("madre", x.madre);
			c.putInt("conyuge", x.conyuge);
			c.putString("r1", x.rasgo1.name());
			c.putString("r2", x.rasgo2.name());
			c.putInt("guerra", x.guerra);
			c.putInt("diplomacia", x.diplomacia);
			c.putInt("intriga", x.intriga);
			c.putInt("fama", x.fama);
			c.putInt("lealtad", x.lealtad);
			c.putInt("ordinal", x.ordinal);
			if (x.mote != null) {
				c.putString("mote", x.mote);
			}
			c.putInt("muere", x.muere);
			if (x.causa != null) {
				c.putString("causa", x.causa.name());
			}
			personas.add(c);
		}
		t.put("personas", personas);
		t.put("reyes", cargos(g.reyes()));
		t.put("duques", cargos(g.duques()));
		t.put("generales", cargos(g.generales()));
		t.put("consejeros", cargos(g.consejeros()));
		ListTag reinados = new ListTag();
		for (Gente.Reinado r : g.reinados()) {
			CompoundTag c = new CompoundTag();
			c.putLong("reino", r.reino());
			c.putInt("p", r.persona());
			c.putInt("desde", r.desde());
			c.putInt("hasta", r.hasta());
			reinados.add(c);
		}
		t.put("reinados", reinados);
		return t;
	}

	private static ListTag cargos(Map<Long, Integer> mapa) {
		ListTag l = new ListTag();
		mapa.forEach((k, id) -> {
			CompoundTag c = new CompoundTag();
			c.putLong("k", k);
			c.putInt("p", id);
			l.add(c);
		});
		return l;
	}

	private static void leeCargos(ListTag l, Map<Long, Integer> mapa) {
		for (int i = 0; i < l.size(); i++) {
			CompoundTag c = l.getCompoundOrEmpty(i);
			mapa.put(c.getLongOr("k", 0), c.getIntOr("p", Persona.NADIE));
		}
	}

	private static <E extends Enum<E>> E enumeracion(Class<E> tipo, String nombre, E porDefecto) {
		try {
			return Enum.valueOf(tipo, nombre);
		} catch (IllegalArgumentException e) {
			return porDefecto;
		}
	}

	private static void cargaGente(CompoundTag t, Gente g) {
		ListTag personas = t.getListOrEmpty("personas");
		for (int i = 0; i < personas.size(); i++) {
			CompoundTag c = personas.getCompoundOrEmpty(i);
			Persona x = new Persona(c.getIntOr("id", 0), c.getStringOr("nombre", "?"), c.getStringOr("casa", "?"),
					c.getStringOr("cultura", "llanura"), c.getBooleanOr("mujer", false), c.getIntOr("nace", 0),
					enumeracion(Persona.Rol.class, c.getStringOr("rol", ""), Persona.Rol.sin_cargo), c.getLongOr("reino", Politica.NADIE),
					enumeracion(Persona.Rasgo.class, c.getStringOr("r1", ""), Persona.Rasgo.justo),
					enumeracion(Persona.Rasgo.class, c.getStringOr("r2", ""), Persona.Rasgo.astuto), c.getIntOr("guerra", 5),
					c.getIntOr("diplomacia", 5), c.getIntOr("intriga", 5));
			x.ducado = c.getLongOr("ducado", Politica.NADIE);
			x.padre = c.getIntOr("padre", Persona.NADIE);
			x.madre = c.getIntOr("madre", Persona.NADIE);
			x.conyuge = c.getIntOr("conyuge", Persona.NADIE);
			x.fama = c.getIntOr("fama", 0);
			x.lealtad = c.getIntOr("lealtad", 60);
			x.ordinal = c.getIntOr("ordinal", 0);
			x.mote = c.getString("mote").orElse(null);
			x.muere = c.getIntOr("muere", 0);
			x.causa = c.getString("causa").map(n -> enumeracion(Persona.Muerte.class, n, Persona.Muerte.enfermedad)).orElse(null);
			g.restaura(x);
		}
		g.fijaSiguiente(t.getIntOr("siguiente", 1));
		leeCargos(t.getListOrEmpty("reyes"), g.reyes());
		leeCargos(t.getListOrEmpty("duques"), g.duques());
		leeCargos(t.getListOrEmpty("generales"), g.generales());
		leeCargos(t.getListOrEmpty("consejeros"), g.consejeros());
		ListTag reinados = t.getListOrEmpty("reinados");
		for (int i = 0; i < reinados.size(); i++) {
			CompoundTag c = reinados.getCompoundOrEmpty(i);
			g.reinados().add(new Gente.Reinado(c.getLongOr("reino", 0), c.getIntOr("p", Persona.NADIE), c.getIntOr("desde", 1),
					c.getIntOr("hasta", 0)));
		}
	}

	/** Null si no hay nada guardado para esta sal. */
	static Cargado carga(CompoundTag t, int sal) {
		if (t.getIntOr("sal", sal + 1) != sal || !t.contains("turno")) {
			return null;
		}
		Politica p = new Politica(sal);
		p.restaura(t.getIntOr("turno", 0), t.getLongOr("protegido", Politica.NADIE));
		ListTag reinos = t.getListOrEmpty("reinos");
		for (int i = 0; i < reinos.size(); i++) {
			CompoundTag c = reinos.getCompoundOrEmpty(i);
			Politica.Reino r = new Politica.Reino();
			r.estabilidad = c.getIntOr("estabilidad", 60);
			r.senor = c.getLongOr("senor", Politica.NADIE);
			r.turnosVasallo = c.getIntOr("turnos_vasallo", 0);
			r.caido = c.getBooleanOr("caido", false);
			p.reinos().put(c.getLongOr("id", 0), r);
		}
		ListTag cambios = t.getListOrEmpty("cambios");
		for (int i = 0; i < cambios.size(); i++) {
			CompoundTag c = cambios.getCompoundOrEmpty(i);
			p.cambios().put(c.getLongOr("ducado", 0), new Politica.Cambio(c.getLongOr("dueno", Politica.SALVAJE),
					c.getIntOr("lealtad", 50), c.getIntOr("anio", 1), c.getBooleanOr("noble", false), c.getIntOr("turno", -1)));
		}
		leePares(t.getListOrEmpty("relaciones"), "valor", p.relaciones());
		leePares(t.getListOrEmpty("guerras"), "turnos", p.guerras());
		ListTag alianzas = t.getListOrEmpty("alianzas");
		for (int i = 0; i < alianzas.size(); i++) {
			CompoundTag c = alianzas.getCompoundOrEmpty(i);
			p.alianzas().add(Politica.Par.de(c.getLongOr("a", 0), c.getLongOr("b", 0)));
		}
		leePares(t.getListOrEmpty("alianza_desde"), "turno", p.alianzaDesde());
		ListTag uniones = t.getListOrEmpty("uniones");
		for (int i = 0; i < uniones.size(); i++) {
			CompoundTag c = uniones.getCompoundOrEmpty(i);
			p.uniones().add(Politica.Par.de(c.getLongOr("a", 0), c.getLongOr("b", 0)));
		}
		ListTag cronica = t.getListOrEmpty("cronica");
		for (int i = 0; i < cronica.size(); i++) {
			CompoundTag c = cronica.getCompoundOrEmpty(i);
			try {
				p.cronica().addLast(new Suceso(Suceso.Tipo.valueOf(c.getStringOr("tipo", "")), c.getIntOr("anio", 1),
						c.getLongOr("a", Politica.NADIE), c.getLongOr("b", Politica.NADIE), c.getLongOr("ducado", Politica.NADIE),
						c.getString("ayuda").orElse(null), c.getIntOr("pa", Persona.NADIE), c.getIntOr("pb", Persona.NADIE)));
			} catch (IllegalArgumentException e) {
				// suceso de otra versión: se salta
			}
		}
		ListTag empujones = t.getListOrEmpty("empujones");
		for (int i = 0; i < empujones.size(); i++) {
			CompoundTag c = empujones.getCompoundOrEmpty(i);
			p.empujones().put(c.getLongOr("ducado", 0), new Politica.Empujon(c.getLongOr("reino", Politica.NADIE),
					c.getIntOr("campamentos", 1), c.getIntOr("turno", 0), c.getStringOr("ayuda", "?")));
		}
		ListTag embajadas = t.getListOrEmpty("embajadas");
		for (int i = 0; i < embajadas.size(); i++) {
			CompoundTag c = embajadas.getCompoundOrEmpty(i);
			p.embajadas().put(Politica.Par.de(c.getLongOr("a", 0), c.getLongOr("b", 0)), c.getStringOr("ayuda", "?"));
		}
		cargaGente(t.getCompoundOrEmpty("gente"), p.gente());
		return new Cargado(p, t.getLongOr("siguiente", 0), t.getIntOr("ticks_turno", 0));
	}

	private static CompoundTag par(Politica.Par par) {
		CompoundTag c = new CompoundTag();
		c.putLong("a", par.a());
		c.putLong("b", par.b());
		return c;
	}

	private static ListTag pares(Map<Politica.Par, Integer> mapa, String campo) {
		ListTag l = new ListTag();
		mapa.forEach((par, v) -> {
			CompoundTag c = par(par);
			c.putInt(campo, v);
			l.add(c);
		});
		return l;
	}

	private static void leePares(ListTag l, String campo, Map<Politica.Par, Integer> mapa) {
		for (int i = 0; i < l.size(); i++) {
			CompoundTag c = l.getCompoundOrEmpty(i);
			mapa.put(Politica.Par.de(c.getLongOr("a", 0), c.getLongOr("b", 0)), c.getIntOr(campo, 0));
		}
	}
}
