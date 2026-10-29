package es.hulillo.destinos;

import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Una puerta de la muralla donde la cruza una calle principal: un pasaje en arco con el rastrillo alzado entre dos
 * torres, con una terraza almenada encima. Las torres son huecas, con puerta hacia la ciudad, escala de mano, un piso a
 * la altura del adarve con salida a la muralla y otro en la azotea, que da a la terraza. De piedra en las capitales
 * normales y ricas; de madera, como la empalizada, en las pobres.
 *
 * En local la puerta mira al norte (v negativa), hacia fuera de la ciudad; el pasaje va a lo largo de v.
 */
public final class PuertaCapital extends PiezaObra {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) PuertaCapital::new;
	/** Medio ancho (de torre a torre) y medio fondo. */
	static final int MEDIO = 7;
	private static final int FONDO = 4;
	/** Hasta dónde, delante y detrás, la muralla no se pone (el paso y las puertas de las torres quedan libres). */
	static final int LIBRE = FONDO + 5;
	/** El pasaje: medio ancho y alto libre. */
	private static final int PASO = 2;
	private static final int ALTO_PASO = 5;

	private final int nivel;

	PuertaCapital(int cx, int cz, int y0, int rot, int estilo, int madera, int color, int nivel) {
		super(TIPO, cx, cz, y0, rot, estilo, madera, color, MEDIO + TALUD, MurallaCapital.alto(nivel) + 12);
		this.nivel = nivel;
	}

	private PuertaCapital(CompoundTag t) {
		super(TIPO, t);
		nivel = t.getIntOr("nivel", 1);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "puerta"), TIPO);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		super.addAdditionalSaveData(ctx, t);
		t.putInt("nivel", nivel);
	}

	/** El bloque de los muros: piedra de la cultura o, en las pobres, tablas con troncos en las esquinas. */
	private BlockState muro(Obra o, int u, int ly, int v, boolean esquina) {
		if (nivel == 0) {
			return esquina ? Obra.tronco(o.p.tronco, Direction.Axis.Y) : o.p.tablas;
		}
		return o.p.piedra(o, u, ly, v);
	}

	@Override
	protected void construye(Obra o) {
		Paleta p = o.p;
		int adarve = MurallaCapital.alto(nivel);
		int alto = adarve + 3;
		int torre = alto + 3;
		o.planta(-MEDIO, -FONDO, MEDIO, FONDO);
		// el suelo, empedrado
		for (int u = -MEDIO; u <= MEDIO; u++) {
			for (int v = -FONDO; v <= FONDO; v++) {
				o.pon(u, 0, v, p.adoquines[o.azar(u, 0, v, p.adoquines.length)]);
			}
		}
		// el cuerpo de en medio, sobre el pasaje: macizo de lado a lado, con la terraza encima
		for (int u = -PASO - 1; u <= PASO + 1; u++) {
			for (int v = -FONDO; v <= FONDO; v++) {
				for (int y = 1; y <= alto; y++) {
					boolean pasaje = Math.abs(u) <= PASO && y <= ALTO_PASO;
					o.pon(u, y, v, pasaje ? Blocks.AIR.defaultBlockState() : muro(o, u, y, v, false));
				}
				if (Math.abs(v) == FONDO && (u + v) % 2 == 0) {
					o.pon(u, alto + 1, v, muro(o, u, alto + 1, v, false));
				}
			}
		}
		// el arco: las esquinas de arriba redondeadas por delante y por detrás, y el rastrillo alzado
		for (int s = -1; s <= 1; s += 2) {
			for (int v : new int[] {-FONDO, FONDO}) {
				o.pon(s * PASO, ALTO_PASO, v, Obra.escalon(nivel == 0 ? p.maderaEscalon : p.piedraEscalon,
						s < 0 ? Direction.WEST : Direction.EAST, true));
			}
		}
		for (int u = -PASO; u <= PASO; u++) {
			o.pon(u, ALTO_PASO, -FONDO + 1, Blocks.IRON_BARS);
		}
		// las dos torres
		for (int s = -1; s <= 1; s += 2) {
			int u0 = s * (PASO + 2);
			int u1 = s * MEDIO;
			int ua = Math.min(u0, u1);
			int ub = Math.max(u0, u1);
			for (int u = ua; u <= ub; u++) {
				for (int v = -FONDO; v <= FONDO; v++) {
					boolean borde = u == ua || u == ub || Math.abs(v) == FONDO;
					boolean esquina = (u == ua || u == ub) && Math.abs(v) == FONDO;
					for (int y = 1; y <= torre; y++) {
						BlockState b;
						if (borde) {
							b = muro(o, u, y, v, esquina);
						} else if (y == adarve || y == alto || y == torre) {
							b = p.tablas;
						} else {
							b = Blocks.AIR.defaultBlockState();
						}
						o.pon(u, y, v, b);
					}
					if (borde && (u + v) % 2 == 0) {
						o.pon(u, torre + 1, v, muro(o, u, torre + 1, v, esquina));
					}
				}
			}
			// la escala de mano, contra el muro de fuera de la torre, y los huecos
			int ue = s * (MEDIO - 1);
			for (int y = 1; y <= torre; y++) {
				o.pon(ue, y, -FONDO + 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, s < 0 ? Direction.EAST : Direction.WEST));
			}
			// puerta a la ciudad, salida al adarve por fuera y a la terraza por dentro
			o.puerta(s * (PASO + 4), 1, FONDO, Direction.SOUTH);
			o.aire(u1, adarve + 1, 0, u1, adarve + 2, 1);
			o.aire(u0, alto + 1, 0, u0, alto + 2, 0);
			// el estandarte del reino en la cara de fuera
			o.estandarte(s * (PASO + 4), torre - 1, -FONDO - 1, Direction.NORTH);
		}
		o.estandarte(0, alto - 1, -FONDO - 1, Direction.NORTH);
		o.alumbra(-MEDIO, -FONDO, MEDIO, FONDO, 0, torre, 4);
	}
}
