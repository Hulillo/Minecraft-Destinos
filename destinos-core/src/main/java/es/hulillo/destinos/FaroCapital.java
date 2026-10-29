package es.hulillo.destinos;

import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * El faro de las capitales de costa, junto al muelle que da a mar abierto: torre redonda a franjas blancas y del color
 * del reino, con puerta, escala por dentro y saeteras; arriba un mirador con baranda alrededor de la linterna (luz de
 * mar tras cristales) y un tejadillo cónico con su farol.
 */
public final class FaroCapital extends PiezaObra {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) FaroCapital::new;
	/** Radio de la torre y del mirador. */
	static final int RADIO = 3;
	private static final int MIRADOR = RADIO + 1;
	/** Alto de la torre hasta el suelo del mirador. */
	private static final int ALTO_TORRE = 16;
	private static final int FRANJA = 4;

	FaroCapital(int cx, int cz, int y0, int rot, int estilo, int madera, int color) {
		super(TIPO, cx, cz, y0, rot, estilo, madera, color, MIRADOR + TALUD, ALTO_TORRE + 12);
	}

	private FaroCapital(CompoundTag t) {
		super(TIPO, t);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "faro"), TIPO);
	}

	@Override
	protected void construye(Obra o) {
		Paleta p = o.p;
		int h = ALTO_TORRE;
		Block franja = o.tinte("terracotta", Blocks.RED_TERRACOTTA);
		for (int u = -MIRADOR; u <= MIRADOR; u++) {
			for (int v = -MIRADOR; v <= MIRADOR; v++) {
				double d = Math.hypot(u, v);
				if (d <= RADIO + 0.3) {
					o.planta(u, v, u, v);
					o.pon(u, 0, v, p.zocalo);
					for (int y = 1; y <= h; y++) {
						if (d > RADIO - 0.7) {
							o.pon(u, y, v, (y - 1) / FRANJA % 2 == 0 ? p.enlucido : franja.defaultBlockState());
						} else {
							o.pon(u, y, v, Blocks.AIR);
						}
					}
				}
				// el mirador, que vuela un bloque, con su baranda
				if (d <= MIRADOR + 0.3) {
					o.pon(u, h + 1, v, p.moldura);
					if (d > MIRADOR - 0.7) {
						o.pon(u, h + 2, v, p.muroBajo);
					}
				}
			}
		}
		// la puerta, la escala (pegada al muro del fondo, hasta salir al mirador) y las saeteras
		o.puerta(0, 1, -RADIO, Direction.NORTH);
		for (int y = 1; y <= h + 1; y++) {
			o.pon(0, y, RADIO - 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		}
		for (int y = 6; y < h; y += 5) {
			o.pon(RADIO, y, 0, Blocks.GLASS_PANE);
			o.pon(-RADIO, y, 0, Blocks.GLASS_PANE);
			o.pon(0, y + 2, -RADIO, Blocks.GLASS_PANE);
		}
		// abajo, el almacén del farero: barriles de aceite, un cofre y un farol
		o.pon(-1, 1, 1, Blocks.BARREL);
		o.pon(1, 1, 1, Blocks.BARREL);
		o.pon(-2, 1, 0, Blocks.CHEST);
		o.pon(2, 1, 0, Blocks.LANTERN);
		o.pon(0, h - 1, 0, Obra.farolColgado());
		o.pon(0, h, 0, p.valla);
		// la linterna: cuatro postes en las esquinas, cristal entre ellos y la luz en medio (por el lado de la escala,
		// un hueco para entrar)
		for (int u = -2; u <= 2; u++) {
			for (int v = -2; v <= 2; v++) {
				int a = Math.max(Math.abs(u), Math.abs(v));
				for (int y = h + 2; y <= h + 4; y++) {
					if (Math.abs(u) == 2 && Math.abs(v) == 2) {
						o.pon(u, y, v, p.muroBajo);
					} else if (a == 2) {
						o.pon(u, y, v, u == 0 && v == 2 && y < h + 4 ? Blocks.AIR.defaultBlockState() : Blocks.GLASS.defaultBlockState());
					} else if (u == 0 && v == 0) {
						o.pon(u, y, v, y < h + 4 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.GLOWSTONE.defaultBlockState());
					}
				}
			}
		}
		o.pon(0, h + 2, 1, Blocks.AIR);
		// el tejadillo cónico y el farol en la punta
		for (int k = 0; k <= 2; k++) {
			int r = 3 - k;
			for (int u = -r; u <= r; u++) {
				for (int v = -r; v <= r; v++) {
					if (Math.hypot(u, v) <= r + 0.3) {
						o.pon(u, h + 5 + k, v, k == 0 && Math.hypot(u, v) > r - 0.7 ? p.moldura : p.tejadoLleno);
					}
				}
			}
		}
		o.pon(0, h + 8, 0, p.muroBajo);
		o.pon(0, h + 9, 0, Blocks.LANTERN);
		o.estandarte(0, h + 1, -MIRADOR - 1, Direction.NORTH);
	}
}
