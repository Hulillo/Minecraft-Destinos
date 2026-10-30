package es.hulillo.destinos;

import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Un huerto de las afueras de la capital, fuera de la muralla: surcos de un cultivo (el del clima) a los dos lados de
 * una acequia, vallado, con la cancela hacia la ciudad, un espantapájaros en una esquina y el compostador, los
 * barriles y un farol junto a la entrada.
 */
public final class HuertoCapital extends PiezaObra {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) HuertoCapital::new;
	/** Medio lado del huerto, valla incluida. */
	static final int MEDIO = 5;
	private static final int ALTO = 5;

	HuertoCapital(int cx, int cz, int y0, int rot, int estilo, int madera, int color) {
		super(TIPO, cx, cz, y0, rot, estilo, madera, color, MEDIO + TALUD, ALTO);
	}

	private HuertoCapital(CompoundTag t) {
		super(TIPO, t);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "huerto"), TIPO);
	}

	/** El cultivo del huerto, según el clima y la suerte: trigo, zanahorias, patatas o remolachas. */
	private BlockState cultivo(Obra o, int u, int v) {
		int k = o.azar(0, 7, 0, 4);
		if (estilo == AdornoCapital.NIEVE || estilo == AdornoCapital.TAIGA) {
			k = k < 2 ? 2 : 3;
		} else if (estilo == AdornoCapital.DESIERTO || estilo == AdornoCapital.SABANA) {
			k = k < 3 ? 0 : 1;
		}
		Block b = switch (k) {
			case 1 -> Blocks.CARROTS;
			case 2 -> Blocks.POTATOES;
			case 3 -> Blocks.BEETROOTS;
			default -> Blocks.WHEAT;
		};
		BlockState s = b.defaultBlockState();
		return b == Blocks.BEETROOTS ? s.setValue(BlockStateProperties.AGE_3, 1 + o.azar(u, 1, v, 3))
				: s.setValue(BlockStateProperties.AGE_7, 3 + o.azar(u, 1, v, 5));
	}

	@Override
	protected void construye(Obra o) {
		Paleta p = o.p;
		int m = MEDIO;
		o.planta(-m, -m, m, m);
		for (int u = -m; u <= m; u++) {
			for (int v = -m; v <= m; v++) {
				boolean valla = Math.max(Math.abs(u), Math.abs(v)) == m;
				if (valla) {
					o.pon(u, 0, v, p.desierto ? Blocks.SAND : Blocks.GRASS_BLOCK);
					o.pon(u, 1, v, p.valla);
				} else if (u == 0) {
					// la acequia por en medio, con una pasarela de losa en la entrada
					o.pon(u, 0, v, Blocks.WATER);
					o.pon(u, -1, v, Blocks.DIRT);
				} else if (Math.abs(v) == m - 1 && v < 0) {
					// el camino de dentro, junto a la cancela
					o.pon(u, 0, v, Blocks.DIRT_PATH);
				} else {
					o.pon(u, 0, v, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
					o.pon(u, 1, v, cultivo(o, u, v));
				}
			}
		}
		// la cancela hacia la ciudad (la puerta de la obra, en v negativa) y el paso sobre la acequia
		o.pon(-2, 1, -m, o.portillo(Direction.NORTH));
		o.pon(0, 0, -m + 1, Obra.losa(p.maderaLosa, true));
		// el espantapájaros en la esquina del fondo
		int eu = m - 2;
		int ev = m - 2;
		o.pon(eu, 0, ev, Blocks.COARSE_DIRT);
		o.pon(eu, 1, ev, p.valla);
		o.pon(eu, 2, ev, Blocks.HAY_BLOCK);
		o.pon(eu, 3, ev, Obra.mira(Blocks.CARVED_PUMPKIN, Direction.NORTH));
		o.pon(eu - 1, 2, ev, Obra.trampilla(p.trampilla, Direction.WEST, false, true));
		o.pon(eu + 1, 2, ev, Obra.trampilla(p.trampilla, Direction.EAST, false, true));
		// junto a la entrada: compostador, barriles y un farol en la valla
		o.pon(-m + 1, 1, -m + 1, Blocks.COMPOSTER);
		o.pon(m - 1, 1, -m + 1, Blocks.BARREL);
		o.pon(m - 2, 1, -m + 1, Blocks.HAY_BLOCK);
		o.pon(-3, 2, -m, Blocks.LANTERN);
	}
}
