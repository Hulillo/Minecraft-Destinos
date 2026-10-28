package es.hulillo.destinos;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Una pieza de la capital hecha a mano con Obra (el castillo, las casas nobles): se construye en local una vez y se
 * pone por chunks, girada, a una altura fija y con cimientos hasta el suelo de verdad.
 *
 * Coordenadas locales: (u, v) desde el centro, con la puerta en v negativa; rot gira lo local al mundo de 90 en 90
 * grados en el sentido de las agujas del reloj (0: la puerta mira al norte).
 */
abstract class PiezaObra extends StructurePiece {
	/** Hasta dónde se rellena el cimiento por debajo, buscando suelo firme. */
	private static final int CIMIENTO = 16;

	protected final int cx;
	protected final int cz;
	protected final int y0;
	protected final int rot;
	protected final int estilo;
	/** La madera de la capital (Paleta.madera), o -1 en las guardadas antes: la de la cultura. */
	protected final int madera;
	/** Color del reino (id de DyeColor), o -1 si no se sabe. */
	protected final int color;
	private Obra obra;

	PiezaObra(StructurePieceType tipo, int cx, int cz, int y0, int rot, int estilo, int madera, int color, int medio, int alto) {
		super(tipo, 0, new BoundingBox(cx - medio, y0, cz - medio, cx + medio, y0 + alto, cz + medio));
		this.cx = cx;
		this.cz = cz;
		this.y0 = y0;
		this.rot = rot;
		this.estilo = estilo;
		this.madera = madera;
		this.color = color;
	}

	PiezaObra(StructurePieceType tipo, CompoundTag t) {
		super(tipo, t);
		cx = t.getIntOr("cx", 0);
		cz = t.getIntOr("cz", 0);
		y0 = t.getIntOr("y0", 0);
		rot = t.getIntOr("rot", 0);
		// los castillos guardados antes de los estilos solo sabían si eran del desierto
		estilo = t.getIntOr("estilo", t.getIntOr("desierto", 0) != 0 ? AdornoCapital.DESIERTO : AdornoCapital.TEMPLADO);
		madera = t.getIntOr("madera", -1);
		color = t.getIntOr("color", -1);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		t.putInt("cx", cx);
		t.putInt("cz", cz);
		t.putInt("y0", y0);
		t.putInt("rot", rot);
		t.putInt("estilo", estilo);
		t.putInt("madera", madera);
		t.putInt("color", color);
	}

	/** Lo que se construye, en local, sobre o. */
	protected abstract void construye(Obra o);

	/** La altura que ocupa por encima del suelo (la de la caja). */
	protected int alto() {
		return getBoundingBox().getYSpan() - 1;
	}

	private synchronized Obra obra() {
		if (obra == null) {
			Obra o = new Obra(estilo, madera, color, alto(), Geo.hash(cx, cz, 7919));
			construye(o);
			o.conecta();
			obra = o;
		}
		return obra;
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar,
			BoundingBox caja, ChunkPos chunk, BlockPos pivote) {
		Obra o = obra();
		Rotation giro = switch (rot) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
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
				Obra.Columna c = o.columna(u, v);
				if (c != null) {
					columna(level, pos, x, z, c, o.p, giro);
				}
			}
		}
		// y los seres que caen en este chunk
		for (Obra.Ser s : o.seres()) {
			int x = cx + switch (rot) {
				case 1 -> -s.v();
				case 2 -> -s.u();
				case 3 -> s.v();
				default -> s.u();
			};
			int z = cz + switch (rot) {
				case 1 -> s.u();
				case 2 -> -s.v();
				case 3 -> -s.u();
				default -> s.v();
			};
			if (x >= caja.minX() && x <= caja.maxX() && z >= caja.minZ() && z <= caja.maxZ()) {
				SeresObra.pon(level, s, new BlockPos(x, y0 + s.ly(), z), rot);
			}
		}
	}

	private void columna(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, Obra.Columna c, Paleta p, Rotation giro) {
		int fondo = c.fondo();
		if (c.planta) {
			// cimiento: hacia abajo hasta tocar suelo firme, para que no cuelgue en las cuestas
			pos.set(x, y0 + Math.min(fondo, 0) - 1, z);
			for (int i = 0; i < CIMIENTO; i++) {
				BlockState s = level.getBlockState(pos);
				if (!s.isAir() && !s.canBeReplaced()) {
					break;
				}
				level.setBlock(pos, p.zocalo, 2);
				pos.move(0, -1, 0);
			}
		}
		int alto = alto();
		for (int ly = Math.min(fondo, 0); ly <= alto; ly++) {
			BlockState b = c.get(ly);
			pos.set(x, y0 + ly, z);
			if (b == null) {
				if (!c.planta) {
					continue;
				}
				if (ly == 0) {
					// el suelo natural del patio: el que hay, o hierba (arena) si ahí no queda nada firme
					BlockState s = level.getBlockState(pos);
					if (s.isAir() || s.canBeReplaced()) {
						level.setBlock(pos, (p.desierto ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState(), 2);
					}
					continue;
				}
				if (ly < 0) {
					continue;
				}
				if (level.getBlockState(pos).isAir()) {
					continue;
				}
				b = Blocks.AIR.defaultBlockState();
			}
			level.setBlock(pos.immutable(), b.rotate(giro), 2);
		}
	}
}
