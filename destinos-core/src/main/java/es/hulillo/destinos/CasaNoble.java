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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Una casa noble de la capital, mirando a la plaza mayor: dos plantas, la de abajo de piedra y la de arriba de
 * entramado de madera, con tejado a dos aguas (en el desierto, de arenisca y azotea). Dentro, estanterías,
 * alfombra, faroles y una escalera de mano entre las plantas. Como el castillo, a una altura fija y con
 * cimientos hasta el suelo de verdad.
 *
 * Coordenadas locales: (u, v) desde el centro, con la puerta en v negativa; rot gira lo local al mundo igual que en
 * el castillo (0: la puerta mira al norte).
 */
public final class CasaNoble extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) CasaNoble::new;
	/** Media anchura (u) y medio fondo (v) de la casa. */
	static final int ANCHO = 4;
	static final int FONDO = 5;
	/** Suelo de la planta de arriba, arranque del tejado y su cumbrera. */
	private static final int PISO = 5;
	private static final int TEJADO = 9;
	private static final int ALTO = TEJADO + ANCHO;
	private static final int CIMIENTO = 16;

	private final int cx;
	private final int cz;
	private final int y0;
	private final int rot;
	private final int estilo;

	CasaNoble(int cx, int cz, int y0, int rot, int estilo) {
		super(TIPO, 0, new BoundingBox(cx - FONDO, y0, cz - FONDO, cx + FONDO, y0 + ALTO, cz + FONDO));
		this.cx = cx;
		this.cz = cz;
		this.y0 = y0;
		this.rot = rot;
		this.estilo = estilo;
	}

	private CasaNoble(CompoundTag t) {
		super(TIPO, t);
		cx = t.getIntOr("cx", 0);
		cz = t.getIntOr("cz", 0);
		y0 = t.getIntOr("y0", 0);
		rot = t.getIntOr("rot", 0);
		estilo = t.getIntOr("estilo", AdornoCapital.TEMPLADO);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "casa_noble"), TIPO);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		t.putInt("cx", cx);
		t.putInt("cz", cz);
		t.putInt("y0", y0);
		t.putInt("rot", rot);
		t.putInt("estilo", estilo);
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
				if (Math.abs(u) <= ANCHO && Math.abs(v) <= FONDO) {
					columna(level, pos, x, z, u, v);
				}
			}
		}
	}

	private void columna(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int u, int v) {
		pos.set(x, y0 - 1, z);
		for (int i = 0; i < CIMIENTO; i++) {
			BlockState s = level.getBlockState(pos);
			if (!s.isAir() && !s.canBeReplaced()) {
				break;
			}
			level.setBlock(pos, base(), 2);
			pos.move(0, -1, 0);
		}
		for (int ly = 0; ly <= ALTO; ly++) {
			BlockState b = bloque(u, v, ly);
			if (b != null) {
				level.setBlock(new BlockPos(x, y0 + ly, z), b, 2);
			}
		}
	}

	/** El bloque en (u, v, ly); null para no tocar (nunca pasa dentro de la casa). */
	private BlockState bloque(int u, int v, int ly) {
		boolean paredU = Math.abs(u) == ANCHO;
		boolean paredV = Math.abs(v) == FONDO;
		boolean pared = paredU || paredV;
		boolean esquina = paredU && paredV;
		if (ly == 0) {
			return base();
		}
		if (estilo == AdornoCapital.DESIERTO) {
			if (ly > TEJADO + 1) {
				return aire();
			}
			if (ly == TEJADO) {
				return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
			}
			if (ly == TEJADO + 1) {
				return pared ? Blocks.CUT_SANDSTONE.defaultBlockState() : aire();
			}
		} else if (ly >= TEJADO) {
			return tejado(u, v, ly);
		}
		if (pared) {
			// la puerta, mirando a la plaza
			if (u == 0 && v == -FONDO && (ly == 1 || ly == 2)) {
				return puerta(ly == 1);
			}
			if (ventana(u, v, ly)) {
				return Blocks.GLASS_PANE.defaultBlockState();
			}
			if (ly < PISO) {
				return piedra();
			}
			if (ly == PISO) {
				return viga();
			}
			// arriba, entramado: vigas en las esquinas y en medio de cada pared, lo demás encalado
			return esquina || u == 0 || v == 0 ? viga() : enlucido();
		}
		// dentro: escalera de mano pegada a la pared del fondo, que atraviesa el suelo de arriba
		if (u == ANCHO - 1 && v == FONDO - 1 && ly <= PISO) {
			return escalera();
		}
		if (ly == PISO) {
			return madera();
		}
		if (ly == 1 && v == FONDO - 1 && u >= -ANCHO + 1 && u <= -1) {
			return Blocks.BOOKSHELF.defaultBlockState();
		}
		if (ly == 1 && Math.abs(u) == ANCHO - 1 && v == -FONDO + 1) {
			return Blocks.LANTERN.defaultBlockState();
		}
		if (ly == PISO + 1) {
			if (Math.abs(u) == ANCHO - 1 && v == -FONDO + 1) {
				return Blocks.LANTERN.defaultBlockState();
			}
			if (Math.abs(u) <= 1 && Math.abs(v) <= 2) {
				return Blocks.RED_CARPET.defaultBlockState();
			}
		}
		return aire();
	}

	/** Ventanas: dos por planta en cada lado largo y a los lados de la puerta, a media altura. */
	private static boolean ventana(int u, int v, int ly) {
		if (ly != 2 && ly != PISO + 2) {
			return false;
		}
		if (Math.abs(u) == ANCHO && Math.abs(v) == 2) {
			return true;
		}
		return Math.abs(v) == FONDO && Math.abs(u) == 2;
	}

	/** Tejado a dos aguas a lo largo de v: cada fila sube un bloque hacia la cumbrera (u = 0). */
	private BlockState tejado(int u, int v, int ly) {
		int k = ly - TEJADO;
		int borde = ANCHO - k;
		if (k > ANCHO || Math.abs(u) > borde) {
			return aire();
		}
		if (Math.abs(u) == borde) {
			if (borde == 0) {
				return madera();
			}
			// el lado alto del peldaño hacia la cumbrera
			return peldano().defaultBlockState().setValue(StairBlock.FACING, mundo(u > 0 ? Direction.WEST : Direction.EAST));
		}
		// los hastiales de los extremos, de madera; dentro, el desván vacío
		return Math.abs(v) == FONDO ? madera() : aire();
	}

	private BlockState puerta(boolean abajo) {
		return puertaBloque().defaultBlockState().setValue(DoorBlock.FACING, mundo(Direction.NORTH))
				.setValue(DoorBlock.HALF, abajo ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
	}

	private BlockState escalera() {
		return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, mundo(Direction.NORTH));
	}

	/** Del lado local (NORTH = hacia la puerta) al del mundo, con el giro de la casa. */
	private Direction mundo(Direction local) {
		Direction d = local;
		for (int i = 0; i < rot; i++) {
			d = d.getClockWise();
		}
		return d;
	}

	private Block peldano() {
		return switch (estilo) {
			case AdornoCapital.TAIGA, AdornoCapital.NIEVE -> Blocks.SPRUCE_STAIRS;
			case AdornoCapital.SABANA -> Blocks.ACACIA_STAIRS;
			default -> Blocks.DARK_OAK_STAIRS;
		};
	}

	private Block puertaBloque() {
		return switch (estilo) {
			case AdornoCapital.TAIGA, AdornoCapital.NIEVE -> Blocks.SPRUCE_DOOR;
			case AdornoCapital.SABANA, AdornoCapital.DESIERTO -> Blocks.ACACIA_DOOR;
			default -> Blocks.DARK_OAK_DOOR;
		};
	}

	private BlockState piedra() {
		return switch (estilo) {
			case AdornoCapital.DESIERTO -> Blocks.CUT_SANDSTONE.defaultBlockState();
			case AdornoCapital.SABANA -> Blocks.MUD_BRICKS.defaultBlockState();
			default -> Blocks.STONE_BRICKS.defaultBlockState();
		};
	}

	private BlockState base() {
		return estilo == AdornoCapital.DESIERTO ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
	}

	private BlockState viga() {
		return switch (estilo) {
			case AdornoCapital.TAIGA, AdornoCapital.NIEVE -> Blocks.SPRUCE_LOG.defaultBlockState();
			case AdornoCapital.SABANA, AdornoCapital.DESIERTO -> Blocks.STRIPPED_ACACIA_LOG.defaultBlockState();
			default -> Blocks.DARK_OAK_LOG.defaultBlockState();
		};
	}

	private BlockState enlucido() {
		return switch (estilo) {
			case AdornoCapital.DESIERTO -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
			case AdornoCapital.SABANA -> Blocks.WHITE_TERRACOTTA.defaultBlockState();
			default -> Blocks.WHITE_CONCRETE.defaultBlockState();
		};
	}

	private BlockState madera() {
		return switch (estilo) {
			case AdornoCapital.TAIGA, AdornoCapital.NIEVE -> Blocks.SPRUCE_PLANKS.defaultBlockState();
			case AdornoCapital.SABANA, AdornoCapital.DESIERTO -> Blocks.ACACIA_PLANKS.defaultBlockState();
			default -> Blocks.DARK_OAK_PLANKS.defaultBlockState();
		};
	}

	private static BlockState aire() {
		return Blocks.AIR.defaultBlockState();
	}
}
