package es.hulillo.destinos;

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
		ListTag cronica = new ListTag();
		for (Suceso s : p.cronica()) {
			CompoundTag c = new CompoundTag();
			c.putString("tipo", s.tipo().name());
			c.putInt("anio", s.anio());
			c.putLong("a", s.reinoA());
			c.putLong("b", s.reinoB());
			c.putLong("ducado", s.ducado());
			cronica.add(c);
		}
		t.put("cronica", cronica);
		return t;
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
					c.getIntOr("lealtad", 50), c.getIntOr("anio", 1), c.getBooleanOr("noble", false)));
		}
		leePares(t.getListOrEmpty("relaciones"), "valor", p.relaciones());
		leePares(t.getListOrEmpty("guerras"), "turnos", p.guerras());
		ListTag alianzas = t.getListOrEmpty("alianzas");
		for (int i = 0; i < alianzas.size(); i++) {
			CompoundTag c = alianzas.getCompoundOrEmpty(i);
			p.alianzas().add(Politica.Par.de(c.getLongOr("a", 0), c.getLongOr("b", 0)));
		}
		ListTag cronica = t.getListOrEmpty("cronica");
		for (int i = 0; i < cronica.size(); i++) {
			CompoundTag c = cronica.getCompoundOrEmpty(i);
			try {
				p.cronica().addLast(new Suceso(Suceso.Tipo.valueOf(c.getStringOr("tipo", "")), c.getIntOr("anio", 1),
						c.getLongOr("a", Politica.NADIE), c.getLongOr("b", Politica.NADIE), c.getLongOr("ducado", Politica.NADIE)));
			} catch (IllegalArgumentException e) {
				// suceso de otra versión: se salta
			}
		}
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
