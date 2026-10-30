package es.hulillo.destinos;

import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Una placita de barrio, a modo de parque, junto a una calle: césped con un seto bajo alrededor, dos caminos
 * empedrados en cruz que la atraviesan y en medio un árbol grande con bancos, un pozo o una fuentecilla. Flores en
 * los cuartos y una farola. En el desierto, en vez de la fuentecilla, un oasis con palmeras.
 */
public final class PlazuelaCapital extends PiezaObra {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) PlazuelaCapital::new;
	/** Medio lado de la placita (sin el talud). */
	static final int MEDIO = 5;
	private static final int ALTO = 9;
	private static final int ARBOL = 0;
	private static final int POZO = 1;
	private static final int FUENTE = 2;

	PlazuelaCapital(int cx, int cz, int y0, int estilo, int madera, int color) {
		super(TIPO, cx, cz, y0, 0, estilo, madera, color, MEDIO + TALUD, ALTO);
	}

	private PlazuelaCapital(CompoundTag t) {
		super(TIPO, t);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "plazuela"), TIPO);
	}

	@Override
	protected void construye(Obra o) {
		Paleta p = o.p;
		int m = MEDIO;
		o.planta(-m, -m, m, m);
		BlockState seto = Obra.hojas(p.hojas);
		for (int u = -m; u <= m; u++) {
			for (int v = -m; v <= m; v++) {
				boolean camino = Math.abs(u) <= 1 || Math.abs(v) <= 1;
				if (camino) {
					o.pon(u, 0, v, p.adoquines[o.azar(u, 0, v, p.adoquines.length)]);
				} else {
					o.pon(u, 0, v, p.desierto ? Blocks.SAND : Blocks.GRASS_BLOCK);
					if (Math.max(Math.abs(u), Math.abs(v)) == m) {
						o.pon(u, 1, v, seto);
					} else if (o.azar(u, 1, v, 4) == 0) {
						o.parterre(u, 1, v);
					}
				}
			}
		}
		int centro = o.azar(0, 0, 0, 3);
		if (p.desierto && centro != POZO) {
			oasis(o);
		} else if (centro == ARBOL) {
			// el árbol en su alcorque, con un banco en cada esquina de la cruz
			for (int du = -1; du <= 1; du++) {
				for (int dv = -1; dv <= 1; dv++) {
					o.pon(du, 0, dv, du == 0 && dv == 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : p.zocalo);
				}
			}
			o.arbol(0, 1, 0, 6);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				o.silla(2 * (d.getStepX() + d.getClockWise().getStepX()), 1, 2 * (d.getStepZ() + d.getClockWise().getStepZ()), d);
			}
		} else if (centro == POZO) {
			// el pozo: brocal de piedra, agua honda, dos postes y un tejadillo con el cubo colgando
			for (int du = -1; du <= 1; du++) {
				for (int dv = -1; dv <= 1; dv++) {
					if (du == 0 && dv == 0) {
						o.caja(0, -3, 0, 0, 0, 0, Blocks.WATER);
					} else {
						o.pon(du, 1, dv, p.muroBajo);
					}
				}
			}
			o.caja(-1, 2, 0, -1, 3, 0, p.valla);
			o.caja(1, 2, 0, 1, 3, 0, p.valla);
			for (int du = -1; du <= 1; du++) {
				for (int dv = -1; dv <= 1; dv++) {
					o.pon(du, 4, dv, Obra.losa(p.maderaLosa, false));
				}
			}
			o.pon(0, 3, 0, p.valla);
			o.pon(0, 2, 0, Obra.farolColgado());
		} else {
			// la fuentecilla: pilón con bordillo de peldaños y una columnilla con maceta en medio
			for (int du = -2; du <= 2; du++) {
				for (int dv = -2; dv <= 2; dv++) {
					int a = Math.max(Math.abs(du), Math.abs(dv));
					o.pon(du, 0, dv, p.zocalo);
					if (a == 2) {
						if (Math.abs(du) == 2 && Math.abs(dv) == 2) {
							o.pon(du, 1, dv, p.moldura);
						} else {
							Direction fuera = Math.abs(du) == 2 ? (du > 0 ? Direction.EAST : Direction.WEST) : (dv > 0 ? Direction.SOUTH : Direction.NORTH);
							o.pon(du, 1, dv, Obra.escalon(p.piedraEscalon, fuera.getOpposite(), false));
						}
					} else if (a == 1) {
						o.pon(du, 1, dv, Blocks.WATER);
					}
				}
			}
			o.pon(0, 1, 0, p.moldura);
			o.pon(0, 2, 0, p.muroBajo);
			o.pon(0, 3, 0, Blocks.POTTED_RED_TULIP);
		}
		o.farola(m - 2, 1, m - 2, Direction.NORTH);
	}

	/**
	 * El oasis de las placitas del desierto: una charca redonda con nenúfares y su orilla de arena con cañas, y tres
	 * palmeras alrededor.
	 */
	private static void oasis(Obra o) {
		for (int du = -3; du <= 3; du++) {
			for (int dv = -3; dv <= 3; dv++) {
				double d = Math.hypot(du, dv);
				if (d <= 2.3) {
					o.caja(du, -1, dv, du, 0, dv, Blocks.WATER);
					o.pon(du, -2, dv, Blocks.SAND);
					if (o.azar(du, 1, dv, 5) == 0) {
						o.pon(du, 1, dv, Blocks.LILY_PAD);
					}
				} else if (d <= 3.2) {
					o.pon(du, 0, dv, Blocks.SAND);
					o.pon(du, 1, dv, Blocks.AIR);
					if (o.azar(du, 1, dv, 3) == 0) {
						o.caja(du, 1, dv, du, 1 + o.azar(du, 2, dv, 2), dv, Blocks.SUGAR_CANE);
					}
				}
			}
		}
		o.palmera(-3, 1, -3, 6);
		o.palmera(3, 1, -3, 5);
		o.palmera(-3, 1, 3, 7);
	}
}
