package es.hulillo.destinos.cliente;

import es.hulillo.destinos.PaqueteFichas;
import es.hulillo.destinos.PaqueteTerritorio;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * Los modos del mapa político (teclas 1 a 8): cómo se colorea cada casilla y qué leyenda lleva cada uno. Todos
 * los colores van en ARGB.
 */
enum ModoMapa {
	REINOS("reinos", "Reinos"),
	DIPLOMACIA("diplomacia", "Diplomacia"),
	GUERRAS("guerras", "Guerras"),
	PODER("poder", "Poder"),
	CULTURA("cultura", "Cultura"),
	SEGURIDAD("seguridad", "Seguridad"),
	FAUNA("fauna", "Fauna"),
	RELIEVE("relieve", "Relieve");

	static final int PERGAMINO = 0xFFE3D3AE;
	static final int NIEBLA = 0xFFCDBB92;
	static final int MAR = 0xFF8FA9A1;
	static final int SALVAJE = 0xFFA39D8C;
	static final int ORO = 0xFFE8B83A;
	static final int FRENTE = 0xFFC0281F;
	static final int ALIANZA = 0xFF4FB548;
	static final int VASALLAJE = 0xFFF1C84B;
	private static final int DEBIL = 0xFFF2E6C4;
	private static final int FUERTE = 0xFF5A2F14;
	private static final int SIN_DATOS = 0xFFCFC6B2;
	static final int[] PELIGRO = {0xFF6F9C5A, 0xFFE2C75E, 0xFFD9803A, 0xFF9C2A22};
	static final Map<String, Integer> CULTURAS = Map.ofEntries(
			Map.entry("llanura", 0xFF7D9A55), Map.entry("norte", 0xFF7F9FC0), Map.entry("costa", 0xFF4E9AB0),
			Map.entry("desierto", 0xFFE0B866), Map.entry("sabana", 0xFFB5763C), Map.entry("selva", 0xFF2F7D3A),
			Map.entry("andina", 0xFF9C6B9E), Map.entry("cerezo", 0xFFE79AB8), Map.entry("bambu", 0xFF8DBE5A),
			Map.entry("estepa", 0xFFC9B27A), Map.entry("islas", 0xFF3FB5A5));
	/** Color de cada familia de bioma (PaqueteTerritorio.familia), para el modo Fauna. */
	private static final int[] HABITAT = {
		0xFFA8B884, // otra
		MAR, // océano
		0xFF6FA0C8, // río
		0xFFE8F0F2, // nieve
		0xFFD07A3E, // badlands
		0xFFE6D08C, // desierto
		0xFF2E7A34, // jungla
		0xFFB3A44E, // sabana
		0xFF4F6E52, // pantano
		0xFF3F6B5A, // taiga
		0xFF9A9A92, // montaña
		0xFF4E8A3E, // bosque
		0xFF9CC46A, // llanura
	};

	final String clave;
	final String respaldo;

	ModoMapa(String clave, String respaldo) {
		this.clave = clave;
		this.respaldo = respaldo;
	}

	Component nombre() {
		return Component.translatableWithFallback("destinos.mapa.modo." + clave, respaldo);
	}

	static int mezcla(int a, int b, double t) {
		int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int v = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int z = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return 0xFF000000 | r << 16 | v << 8 | z;
	}

	/** Si en este modo se ven los frentes de guerra en rojo. */
	boolean frentes() {
		return this == REINOS || this == GUERRAS || this == DIPLOMACIA;
	}

	/** Si los colores de este modo salen de las fichas (y hay que repintar cuando llegan ducados nuevos). */
	boolean usaFichas() {
		return this == PODER || this == CULTURA || this == SEGURIDAD;
	}

	/** Si en este modo las fronteras entre reinos van marcadas fuerte (si no, como una linde de ducado). */
	boolean fronteras() {
		return this != RELIEVE && this != FAUNA;
	}

	/** Color de la casilla (i, j) de la zona en este modo. */
	int color(PaqueteTerritorio z, int i, int j) {
		PaqueteTerritorio.Ducado d = z.ducado(i, j);
		if (this == RELIEVE) {
			return relieve(z, i, j);
		}
		if (d.deNadie()) {
			return MAR;
		}
		int base = d.salvaje() ? SALVAJE : mezcla(0xFF000000 | d.color(), PERGAMINO, 0.22);
		if (d.oceano()) {
			return d.salvaje() || this != REINOS ? MAR : mezcla(MAR, 0xFF000000 | d.color(), 0.3);
		}
		switch (this) {
			case REINOS:
				return TerritoriosCliente.reciente(d) ? mezcla(base, ORO, 0.45) : base;
			case DIPLOMACIA:
				return mezcla(base, PERGAMINO, 0.55);
			case GUERRAS:
				return TerritoriosCliente.enGuerra(d) ? base : mezcla(base, PERGAMINO, 0.75);
			case PODER: {
				PaqueteFichas.Reino r = d.salvaje() ? null : FichasCliente.reino(d.kx(), d.kz());
				if (r == null) {
					return d.salvaje() ? SALVAJE : SIN_DATOS;
				}
				int n = Math.max(2, FichasCliente.vivos());
				return mezcla(DEBIL, FUERTE, 1 - (r.puesto() - 1) / (double) (n - 1));
			}
			case CULTURA: {
				PaqueteFichas.Reino r = d.salvaje() ? null : FichasCliente.reino(d.kx(), d.kz());
				return r == null ? (d.salvaje() ? SALVAJE : SIN_DATOS) : CULTURAS.getOrDefault(r.cultura(), SIN_DATOS);
			}
			case SEGURIDAD: {
				int p = FichasCliente.peligro(d.dcx(), d.dcz());
				return p < 0 ? SIN_DATOS : PELIGRO[Math.min(3, p)];
			}
			case FAUNA:
				return HABITAT[Math.min(HABITAT.length - 1, z.familia(i, j))];
			default:
				return base;
		}
	}

	/** Tintes de altura de atlas, con sombra del noroeste y los ríos en azul. */
	private static int relieve(PaqueteTerritorio z, int i, int j) {
		PaqueteTerritorio.Ducado d = z.ducado(i, j);
		int h = z.altura(i, j);
		if (d.oceano()) {
			// cuanto más hondo, más oscuro
			double t = Math.max(0, Math.min(1, (62 - h) / 40.0));
			return mezcla(0xFF9CC3D0, 0xFF2F5F80, t);
		}
		if (z.rio(i, j)) {
			return 0xFF5E9CC9;
		}
		int c;
		if (h < 80) {
			c = mezcla(0xFF7FA65A, 0xFFA9C06E, (h - 62) / 18.0);
		} else if (h < 120) {
			c = mezcla(0xFFA9C06E, 0xFFD9CF8A, (h - 80) / 40.0);
		} else if (h < 180) {
			c = mezcla(0xFFD9CF8A, 0xFFB48A5A, (h - 120) / 60.0);
		} else if (h < 260) {
			c = mezcla(0xFFB48A5A, 0xFF8C7A70, (h - 180) / 80.0);
		} else {
			c = mezcla(0xFF8C7A70, 0xFFF4F4F4, Math.min(1, (h - 260) / 120.0));
		}
		// sombreado: la casilla del noroeste más alta la oscurece, más baja la aclara
		int hn = i > 0 && j > 0 ? z.altura(i - 1, j - 1) : h;
		double s = Math.max(-1, Math.min(1, (h - hn) / 24.0));
		return s >= 0 ? mezcla(c, 0xFFFFFFFF, s * 0.25) : mezcla(c, 0xFF000000, -s * 0.3);
	}

	/** La leyenda del modo: pares color, texto. */
	List<Object[]> leyenda() {
		List<Object[]> l = new ArrayList<>();
		switch (this) {
			case REINOS -> {
				l.add(new Object[] {ORO, t("capital", "♛ Capital")});
				l.add(new Object[] {FRENTE, t("frente", "Frente de guerra")});
				l.add(new Object[] {SALVAJE, t("salvaje", "Tierras salvajes")});
				l.add(new Object[] {mezcla(PERGAMINO, ORO, 0.6), t("reciente", "Cambió de dueño")});
				l.add(new Object[] {NIEBLA, t("niebla", "Sin explorar")});
			}
			case DIPLOMACIA -> {
				l.add(new Object[] {ALIANZA, t("alianza", "Alianza")});
				l.add(new Object[] {FRENTE, t("guerra", "Guerra")});
				l.add(new Object[] {VASALLAJE, t("vasallaje", "Vasallaje")});
			}
			case GUERRAS -> {
				l.add(new Object[] {0xFFB5523A, t("en_guerra", "Reinos en guerra")});
				l.add(new Object[] {mezcla(0xFFB5523A, PERGAMINO, 0.75), t("en_paz", "En paz")});
				l.add(new Object[] {FRENTE, t("frente", "Frente de guerra")});
			}
			case PODER -> {
				l.add(new Object[] {DEBIL, t("debil", "Débil")});
				l.add(new Object[] {mezcla(DEBIL, FUERTE, 0.5), t("medio", "Medio")});
				l.add(new Object[] {FUERTE, t("fuerte", "El más fuerte")});
			}
			case CULTURA -> {
				for (String c : new String[] {"llanura", "norte", "costa", "desierto", "sabana", "selva", "andina", "cerezo", "bambu", "estepa",
					"islas"}) {
					l.add(new Object[] {CULTURAS.get(c), Component.translatableWithFallback("destinos.cultura." + c, c)});
				}
			}
			case SEGURIDAD -> {
				l.add(new Object[] {PELIGRO[0], t("seguro", "Seguro")});
				l.add(new Object[] {PELIGRO[1], t("tranquilo", "Tranquilo")});
				l.add(new Object[] {PELIGRO[2], t("peligroso", "Peligroso")});
				l.add(new Object[] {PELIGRO[3], t("muy_peligroso", "Muy peligroso")});
			}
			case FAUNA -> {
				l.add(new Object[] {HABITAT[11], t("bosque", "Bosque")});
				l.add(new Object[] {HABITAT[12], t("llanura", "Llanura")});
				l.add(new Object[] {HABITAT[3], t("nieve", "Nieve")});
				l.add(new Object[] {HABITAT[5], t("desierto", "Desierto")});
				l.add(new Object[] {HABITAT[6], t("jungla", "Jungla")});
				l.add(new Object[] {HABITAT[8], t("pantano", "Pantano")});
				l.add(new Object[] {ORO, t("raros", "✦ Bichos raros")});
			}
			case RELIEVE -> {
				l.add(new Object[] {0xFF7FA65A, t("bajo", "Llano")});
				l.add(new Object[] {0xFFD9CF8A, t("colinas", "Colinas")});
				l.add(new Object[] {0xFFB48A5A, t("monte", "Monte")});
				l.add(new Object[] {0xFFF4F4F4, t("cumbres", "Cumbres")});
				l.add(new Object[] {0xFF5E9CC9, t("rio", "Río")});
			}
		}
		return l;
	}

	private static Component t(String clave, String respaldo) {
		return Component.translatableWithFallback("destinos.mapa.leyenda." + clave, respaldo);
	}
}
