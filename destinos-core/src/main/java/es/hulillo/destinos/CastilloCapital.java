package es.hulillo.destinos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * El castillo de una capital rica (muralla de nivel 2): un patio cuadrado con muralla almenada, una torre redonda
 * en cada esquina y la torre del homenaje en medio, con dos pisos y azotea. La puerta mira a la plaza. Todo a una
 * altura fija (el suelo del centro), con cimientos hasta el suelo de verdad. En el desierto, de arenisca.
 *
 * Coordenadas locales: (u, v) desde el centro, con la puerta en v negativa; rot gira lo local al mundo de 90 en
 * 90 grados en el sentido de las agujas del reloj (0: la puerta mira al norte).
 */
public final class CastilloCapital extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) CastilloCapital::new;
	/** Media muralla: el patio va de -MURO a MURO, con la muralla en los dos anillos de fuera. */
	private static final int MURO = 11;
	private static final int ALTO_MURO = 8;
	/** Torres de las esquinas: centro en (±ESQUINA, ±ESQUINA). */
	private static final int ESQUINA = 10;
	private static final double RADIO_TORRE = 3.5;
	private static final int ALTO_TORRE = 12;
	/** Torre del homenaje: de -HOMENAJE a HOMENAJE, pisos en PISOS y azotea arriba del todo. */
	private static final int HOMENAJE = 4;
	private static final int ALTO_HOMENAJE = 15;
	private static final int[] PISOS = {5, 10};
	/** Medio lado de la caja: la muralla más lo que sobresalen las torres. */
	static final int MEDIO = ESQUINA + (int) Math.ceil(RADIO_TORRE);
	/** Hasta dónde se rellena el cimiento por debajo, buscando suelo firme. */
	private static final int CIMIENTO = 16;

	private final int cx;
	private final int cz;
	private final int y0;
	private final int rot;
	private final boolean desierto;

	CastilloCapital(int cx, int cz, int y0, int rot, boolean desierto) {
		super(TIPO, 0, new BoundingBox(cx - MEDIO, y0, cz - MEDIO, cx + MEDIO, y0 + ALTO_HOMENAJE + 2, cz + MEDIO));
		this.cx = cx;
		this.cz = cz;
		this.y0 = y0;
		this.rot = rot;
		this.desierto = desierto;
	}

	private CastilloCapital(CompoundTag t) {
		super(TIPO, t);
		cx = t.getIntOr("cx", 0);
		cz = t.getIntOr("cz", 0);
		y0 = t.getIntOr("y0", 0);
		rot = t.getIntOr("rot", 0);
		desierto = t.getIntOr("desierto", 0) != 0;
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "castillo"), TIPO);
	}

	/** El giro para que la puerta mire hacia (dx, dz), el lado de la plaza visto desde el castillo. */
	static int giroHacia(int dx, int dz) {
		if (Math.abs(dz) >= Math.abs(dx)) {
			return dz < 0 ? 0 : 2;
		}
		return dx > 0 ? 1 : 3;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		t.putInt("cx", cx);
		t.putInt("cz", cz);
		t.putInt("y0", y0);
		t.putInt("rot", rot);
		t.putInt("desierto", desierto ? 1 : 0);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar,
			BoundingBox caja, ChunkPos chunk, BlockPos pivote) {
		BoundingBox mia = getBoundingBox();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = Math.max(mia.minX(), caja.minX()); x <= Math.min(mia.maxX(), caja.maxX()); x++) {
			for (int z = Math.max(mia.minZ(), caja.minZ()); z <= Math.min(mia.maxZ(), caja.maxZ()); z++) {
				int dx = x - cx;
				int dz = z - cz;
				// del mundo a lo local: el giro al revés
				int u = switch (rot) {
					case 1 -> dz;
					case 2 -> -dx;
					case 3 -> -dz;
					default -> dx;
				};
				int v = switch (rot) {
					case 1 -> -dx;
					case 2 -> -dz;
					case 3 -> dx;
					default -> dz;
				};
				if (dentro(u, v)) {
					columna(level, pos, x, z, u, v);
				}
			}
		}
	}

	/** Si la columna local (u, v) es del castillo: el cuadrado de la muralla o una torre. */
	private static boolean dentro(int u, int v) {
		return Math.max(Math.abs(u), Math.abs(v)) <= MURO || enTorre(u, v) >= 0;
	}

	/** Distancia al centro de la torre de esa esquina, o -1 si la columna no es de ninguna torre. */
	private static double enTorre(int u, int v) {
		double d = Math.hypot(Math.abs(u) - ESQUINA, Math.abs(v) - ESQUINA);
		return d <= RADIO_TORRE ? d : -1;
	}

	private void columna(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int u, int v) {
		// cimiento: hacia abajo hasta tocar suelo firme, para que no cuelgue en las cuestas
		pos.set(x, y0 - 1, z);
		for (int i = 0; i < CIMIENTO; i++) {
			BlockState s = level.getBlockState(pos);
			if (!s.isAir() && !s.canBeReplaced()) {
				break;
			}
			level.setBlock(pos, base(), 2);
			pos.move(0, -1, 0);
		}
		int alto = ALTO_HOMENAJE + 1;
		for (int ly = 0; ly <= alto; ly++) {
			pos.set(x, y0 + ly, z);
			BlockState b = bloque(u, v, ly, x, z);
			if (b == null) {
				// el suelo del patio: el que hay, o tierra si ahí no queda nada firme
				BlockState s = level.getBlockState(pos);
				if (s.isAir() || s.canBeReplaced()) {
					level.setBlock(pos, desierto ? Blocks.SAND.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(), 2);
				}
			} else {
				level.setBlock(pos, b, 2);
			}
		}
	}

	/** El bloque en (u, v, ly), o null para dejar lo que hay (el suelo del patio si es tierra). */
	private BlockState bloque(int u, int v, int ly, int x, int z) {
		int anillo = Math.max(Math.abs(u), Math.abs(v));
		double torre = enTorre(u, v);
		boolean almena = (x + z) % 2 == 0;
		if (torre >= 0) {
			boolean pared = torre > RADIO_TORRE - 1;
			if (ly == 0) {
				return base();
			}
			// por donde llega el adarve de la muralla se entra en la torre
			if (pared && ly >= ALTO_MURO + 1 && ly <= ALTO_MURO + 2 && anillo >= MURO - 1 && anillo <= MURO
					&& Math.min(Math.abs(u), Math.abs(v)) < ESQUINA - 1) {
				return aire();
			}
			if (pared) {
				return ly <= ALTO_TORRE ? muro(x, z, ly) : ly == ALTO_TORRE + 1 && almena ? muro(x, z, ly) : aire();
			}
			// maciza hasta el adarve (sin huecos oscuros abajo), un piso a esa altura y el tejado
			if (ly < ALTO_MURO) {
				return muro(x, z, ly);
			}
			return ly == ALTO_MURO || ly == ALTO_TORRE ? madera() : ly == ALTO_MURO + 1 && torre < 0.8 ? farol() : aire();
		}
		if (anillo >= MURO - 1) {
			if (ly == 0) {
				return base();
			}
			// la puerta, de tres de ancho, hacia la plaza
			if (v < 0 && Math.abs(u) <= 1 && ly <= 4) {
				return aire();
			}
			if (ly <= ALTO_MURO) {
				return muro(x, z, ly);
			}
			return ly == ALTO_MURO + 1 && anillo == MURO && almena ? muro(x, z, ly) : aire();
		}
		if (Math.max(Math.abs(u), Math.abs(v)) <= HOMENAJE) {
			return homenaje(u, v, ly, x, z);
		}
		// el patio: camino de la puerta al homenaje, escalera para subir al adarve junto a la puerta y faroles
		if (ly == 0) {
			if (Math.abs(u) <= 1 && v < 0) {
				return desierto ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
			}
			return null;
		}
		if (u == 3 && v == -(MURO - 2) && ly <= ALTO_MURO) {
			return escalera(Direction.SOUTH);
		}
		if (ly == 1 && Math.abs(u) == 2 && v == -(HOMENAJE + 2)) {
			return farol();
		}
		return aire();
	}

	private BlockState homenaje(int u, int v, int ly, int x, int z) {
		int anillo = Math.max(Math.abs(u), Math.abs(v));
		boolean hueco = u == 0 && v == HOMENAJE - 1;
		if (ly == 0) {
			return base();
		}
		if (anillo == HOMENAJE) {
			if (ly > ALTO_HOMENAJE) {
				return (x + z) % 2 == 0 ? muro(x, z, ly) : aire();
			}
			// puerta hacia la de la muralla y ventanas en medio de cada lado
			if (u == 0 && v < 0 && ly <= 2) {
				return aire();
			}
			// (la pared del fondo sin ventana: ahí va pegada la escalera)
			boolean ventana = v == 0 || u == 0 && v < 0;
			if (ventana && (ly == PISOS[0] + 2 || ly == PISOS[1] + 2 || ly == PISOS[0] - 2 && v == 0)) {
				return Blocks.GLASS.defaultBlockState();
			}
			return muro(x, z, ly);
		}
		if (ly > ALTO_HOMENAJE) {
			return aire();
		}
		// la escalera de mano, pegada a la pared del fondo, atraviesa los pisos y la azotea
		if (hueco) {
			return escalera(Direction.NORTH);
		}
		if (ly == ALTO_HOMENAJE) {
			return muro(x, z, ly);
		}
		if (ly == PISOS[0] || ly == PISOS[1]) {
			return madera();
		}
		if (Math.abs(u) == HOMENAJE - 1 && v == -(HOMENAJE - 1) && (ly == 1 || ly == PISOS[0] + 1 || ly == PISOS[1] + 1)) {
			return farol();
		}
		return aire();
	}

	/** Una escalera de mano que mira hacia lado (en local), es decir, pegada a la pared del lado contrario. */
	private BlockState escalera(Direction lado) {
		Direction mundo = lado;
		for (int i = 0; i < rot; i++) {
			mundo = mundo.getClockWise();
		}
		return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, mundo);
	}

	/** Piedra del muro, con algún ladrillo musgoso o agrietado (siempre el mismo en cada sitio). */
	private BlockState muro(int x, int z, int y) {
		if (desierto) {
			return Blocks.CUT_SANDSTONE.defaultBlockState();
		}
		return switch (Math.floorMod(Geo.hash(x, z, y0 + y), 9)) {
			case 0 -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
			case 1 -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
			default -> Blocks.STONE_BRICKS.defaultBlockState();
		};
	}

	private BlockState base() {
		return desierto ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
	}

	private BlockState madera() {
		return desierto ? Blocks.ACACIA_PLANKS.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
	}

	private static BlockState farol() {
		return Blocks.LANTERN.defaultBlockState();
	}

	private static BlockState aire() {
		return Blocks.AIR.defaultBlockState();
	}
}
