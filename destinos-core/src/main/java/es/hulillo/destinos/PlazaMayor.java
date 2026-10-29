package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * La plaza mayor de la capital, que es la del mercado: sin forma de cuadrícula. Su borde es una mancha irregular que
 * se ensancha donde llegan las calles; en medio la fuente con la campana y, sueltos por la plaza, los puestos mirando a
 * la fuente, dejando libres los pasos de las calles. Entre ellos algún árbol con bancos, farolas, barriles, cajas y
 * heno; las orillas, gastadas, con hierba y flores. Las casas de alrededor (las pone la capital) miran hacia ella.
 */
public final class PlazaMayor extends PiezaObra {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) PlazaMayor::new;
	/** Radio de base según la riqueza; el borde va y viene alrededor y se abre en las entradas. */
	private static final int[] BASE = {13, 16, 20};
	private static final double ONDA = 2.5;
	private static final double ENTRADA = 5;
	/** Lo que se abre la plaza a cada lado de una entrada (radianes). */
	private static final double ANCHO_ENTRADA = 0.35;
	private static final int[] PUESTOS = {6, 9, 14};
	private static final int ALTO = 10;

	private final int nivel;
	private final int semilla;
	/** Hacia dónde salen las calles, en décimas de grado (0 al este, como atan2 en x, z). */
	private final int[] entradas;

	PlazaMayor(int cx, int cz, int y0, int estilo, int madera, int color, int nivel, int semilla, int[] entradas) {
		super(TIPO, cx, cz, y0, 0, estilo, madera, color, maximo(nivel) + TALUD, ALTO);
		this.nivel = nivel;
		this.semilla = semilla;
		this.entradas = entradas;
	}

	private PlazaMayor(CompoundTag t) {
		super(TIPO, t);
		nivel = t.getIntOr("nivel", 1);
		semilla = t.getIntOr("semilla", 0);
		entradas = t.getIntArray("entradas").orElse(new int[0]);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "plaza_mayor"), TIPO);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		super.addAdditionalSaveData(ctx, t);
		t.putInt("nivel", nivel);
		t.putInt("semilla", semilla);
		t.putIntArray("entradas", entradas);
	}

	int nivel() {
		return nivel;
	}

	int semilla() {
		return semilla;
	}

	int[] entradas() {
		return entradas;
	}

	/** Lo más lejos que llega el borde del centro. */
	static int maximo(int nivel) {
		return BASE[Math.clamp(nivel, 0, BASE.length - 1)] + (int) Math.ceil(ONDA * 1.6 + ENTRADA) + 1;
	}

	static int base(int nivel) {
		return BASE[Math.clamp(nivel, 0, BASE.length - 1)];
	}

	/** El radio del borde hacia el ángulo a (radianes, como atan2(z, x)). */
	static double radio(int nivel, int semilla, int[] entradas, double a) {
		double f1 = Math.floorMod(semilla, 628) / 100.0;
		double f2 = Math.floorMod(semilla >> 10, 628) / 100.0;
		double r = base(nivel) + ONDA * Math.sin(3 * a + f1) + ONDA * 0.6 * Math.sin(5 * a + f2);
		double abre = 0;
		for (int e : entradas) {
			double d = Math.abs(Math.IEEEremainder(a - Math.toRadians(e / 10.0), 2 * Math.PI));
			abre = Math.max(abre, ENTRADA * Math.max(0, 1 - d / ANCHO_ENTRADA));
		}
		return r + abre;
	}

	double radio(double a) {
		return radio(nivel, semilla, entradas, a);
	}

	/** Si (x, z) del mundo cae dentro de la plaza (con margen bloques de más). */
	boolean dentro(int x, int z, double margen) {
		double dx = x - cx;
		double dz = z - cz;
		return Math.hypot(dx, dz) <= radio(Math.atan2(dz, dx)) + margen;
	}

	/** Si a está cerca de alguna entrada (para dejar libre el paso de las calles hasta la fuente). */
	private boolean enPaso(double a, double ancho) {
		for (int e : entradas) {
			if (Math.abs(Math.IEEEremainder(a - Math.toRadians(e / 10.0), 2 * Math.PI)) < ancho) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void construye(Obra o) {
		Paleta p = o.p;
		int m = maximo(nivel);
		// el suelo: empedrado, con un anillo de losas alrededor de la fuente y la orilla gastada
		for (int u = -m; u <= m; u++) {
			for (int v = -m; v <= m; v++) {
				double d = Math.hypot(u, v);
				double a = Math.atan2(v, u);
				double r = radio(a);
				if (d > r) {
					continue;
				}
				o.planta(u, v, u, v);
				boolean orilla = d > r - 2 && o.azar(u, 0, v, 10) < 4;
				if (orilla) {
					o.pon(u, 0, v, p.desierto ? Blocks.SAND : Blocks.GRASS_BLOCK);
					if (!enPaso(a, ANCHO_ENTRADA * 0.8) && o.azar(u, 1, v, 3) == 0) {
						o.parterre(u, 1, v);
					}
				} else if (d > 4.5 && d < 5.5) {
					o.pon(u, 0, v, p.zocalo);
				} else {
					o.pon(u, 0, v, p.adoquines[o.azar(u, 0, v, p.adoquines.length)]);
				}
			}
		}
		PlazaMercado.fuente(o, 0, 0);
		// los puestos, sueltos y mirando a la fuente, sin tapar los pasos de las calles
		List<int[]> ocupado = new ArrayList<>();
		ocupado.add(new int[] {0, 0, 6});
		// en el desierto, cuatro palmeras alrededor de la fuente, en las diagonales (si no cae ahí una calle)
		if (p.desierto) {
			for (int k = 0; k < 4; k++) {
				double a = Math.PI / 4 + k * Math.PI / 2;
				int u = (int) Math.round(Math.cos(a) * 8.5);
				int v = (int) Math.round(Math.sin(a) * 8.5);
				if (enPaso(a, 0.3)) {
					continue;
				}
				o.pon(u, 0, v, Blocks.SAND);
				o.palmera(u, 1, v, 6 + o.azar(u, 0, v, 3));
				ocupado.add(new int[] {u, v, 3});
			}
		}
		int quiero = PUESTOS[Math.clamp(nivel, 0, PUESTOS.length - 1)];
		int puestos = 0;
		for (int k = 0; k < 400 && puestos < quiero; k++) {
			double a = o.azar(k, 3, 7, 3600) / 3600.0 * 2 * Math.PI;
			double r0 = radio(a);
			double d = 8 + o.azar(k, 5, 11, 1000) / 1000.0 * Math.max(0, r0 - 12);
			int u = (int) Math.round(Math.cos(a) * d);
			int v = (int) Math.round(Math.sin(a) * d);
			if (enPaso(a, 0.22) || d > r0 - 4 || choca(ocupado, u, v, 4)) {
				continue;
			}
			Direction frente = Math.abs(u) >= Math.abs(v) ? (u > 0 ? Direction.WEST : Direction.EAST) : (v > 0 ? Direction.NORTH : Direction.SOUTH);
			int h = o.azar(k, 9, 2, 12);
			if (h == 0 && puestos > 2) {
				PlazaMercado.arbolConBancos(o, u, v);
			} else if (h == 1 && nivel > 0 && puestos > 2) {
				PlazaMercado.corral(o, u, v, frente, k);
			} else {
				o.incrusta(PuestoMercado.construye(o.azar(k, 1, 1, PuestoMercado.TIPOS), estilo, madera, color, Geo.hash(cx + u, cz + v, 7451)), u, v,
						PlazaMercado.giro(frente));
				puestos++;
			}
			ocupado.add(new int[] {u, v, 4});
		}
		// y cosas sueltas: barriles, cajas y heno junto a los puestos, y farolas
		int sueltas = 0;
		for (int k = 0; k < 300 && sueltas < 6 + 3 * nivel; k++) {
			double a = o.azar(k, 13, 5, 3600) / 3600.0 * 2 * Math.PI;
			double d = 6 + o.azar(k, 17, 3, 1000) / 1000.0 * Math.max(0, radio(a) - 8);
			int u = (int) Math.round(Math.cos(a) * d);
			int v = (int) Math.round(Math.sin(a) * d);
			if (enPaso(a, 0.15) || !o.vacio(u, 1, v) || choca(ocupado, u, v, 0)) {
				continue;
			}
			int h = o.azar(k, 19, 1, 5);
			o.pon(u, 1, v, h == 0 ? Blocks.HAY_BLOCK : h == 1 ? Blocks.BARREL : h == 2 ? Blocks.COMPOSTER : h == 3 ? Blocks.PUMPKIN : Blocks.BARREL);
			sueltas++;
		}
		int farolas = 0;
		for (int k = 0; k < 200 && farolas < 3 + nivel * 2; k++) {
			double a = o.azar(k, 23, 9, 3600) / 3600.0 * 2 * Math.PI;
			double d = radio(a) - 2;
			int u = (int) Math.round(Math.cos(a) * d);
			int v = (int) Math.round(Math.sin(a) * d);
			if (enPaso(a, 0.25) || choca(ocupado, u, v, 2) || !o.vacio(u, 1, v)) {
				continue;
			}
			o.farola(u, 1, v, Math.abs(u) >= Math.abs(v) ? (u > 0 ? Direction.WEST : Direction.EAST) : (v > 0 ? Direction.NORTH : Direction.SOUTH));
			ocupado.add(new int[] {u, v, 3});
			farolas++;
		}
	}

	private static boolean choca(List<int[]> ocupado, int u, int v, int r) {
		for (int[] q : ocupado) {
			if (Math.max(Math.abs(q[0] - u), Math.abs(q[1] - v)) <= q[2] + r) {
				return true;
			}
		}
		return false;
	}
}
