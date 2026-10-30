package es.hulillo.destinos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Un tramo de una calle de la capital: el trozo recto entre dos puntos de su curva, pintado como con una brocha
 * redonda de radio bloques alrededor de la línea (así las diagonales tienen el mismo ancho que las rectas y los
 * tramos se empalman sin huecos en las curvas). La altura va de y0 a y1 a lo largo del tramo, ya suavizada a lo largo
 * de toda la calle: donde el suelo queda un poco por encima se rebaja y donde queda un poco por debajo se rellena,
 * con un escalón de cuneta a los lados; si la diferencia es grande, la calle sigue el suelo. Solo se toca el suelo
 * natural, nunca las casas. Sobre el agua cruza por un puente de piedra medio bloque por encima, con pretil a los
 * lados y pilares hasta el fondo; en el pantano, por una pasarela de mangle sobre pilotes.
 *
 * No cuenta para el allanado del terreno (que con cajas finas a la altura de cada tramo dejaba la calle en
 * terrazas rectangulares): el allanado lo hace ella misma, columna a columna.
 */
public final class CalleCapital extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) CalleCapital::new;
	/** Cada cuántos bloques el puente lleva pilares hasta el fondo. */
	private static final int PILARES = 5;
	/** Lo que se rebaja o se rellena como mucho para que la calle vaya suave; con más, sigue el suelo. */
	private static final int MAX_CORTE = 3;
	private static final int MAX_RELLENO = 2;
	/** Sin altura guardada (piezas de antes): la calle sigue el suelo. */
	private static final int SIN_ALTURA = Integer.MIN_VALUE;

	private final int x0;
	private final int z0;
	private final int y0;
	private final int x1;
	private final int z1;
	private final int y1;
	/** Medio ancho de la calle, en décimas de bloque. */
	private final int radio;
	private final boolean puente;
	/** Lo que se puede rebajar o rellenar como mucho (0: lo de siempre); las rampas, como la subida al castillo, más. */
	private final int maximo;
	/** En el pantano: sobre el agua, pasarela de mangle sobre pilotes (también en rondas y callejones). */
	private boolean madera;

	CalleCapital(int x0, int z0, int y0, int x1, int z1, int y1, double radio, boolean puente) {
		this(x0, z0, y0, x1, z1, y1, radio, puente, 0);
	}

	CalleCapital(int x0, int z0, int y0, int x1, int z1, int y1, double radio, boolean puente, int maximo) {
		super(TIPO, 0, caja(x0, z0, y0, x1, z1, y1, radio, maximo));
		this.maximo = maximo;
		this.x0 = x0;
		this.z0 = z0;
		this.y0 = y0;
		this.x1 = x1;
		this.z1 = z1;
		this.y1 = y1;
		this.radio = (int) Math.round(radio * 10);
		this.puente = puente;
	}

	int x0() {
		return x0;
	}

	int z0() {
		return z0;
	}

	int x1() {
		return x1;
	}

	int z1() {
		return z1;
	}

	private static BoundingBox caja(int x0, int z0, int y0, int x1, int z1, int y1, double radio, int maximo) {
		int m = (int) Math.ceil(radio) + 2;
		int h = Math.max(MAX_CORTE, maximo);
		return new BoundingBox(Math.min(x0, x1) - m, Math.min(y0, y1) - h - 1, Math.min(z0, z1) - m, Math.max(x0, x1) + m,
				Math.max(y0, y1) + h + 2, Math.max(z0, z1) + m);
	}

	private CalleCapital(CompoundTag t) {
		super(TIPO, t);
		x0 = t.getIntOr("x0", 0);
		z0 = t.getIntOr("z0", 0);
		x1 = t.getIntOr("x1", 0);
		z1 = t.getIntOr("z1", 0);
		y0 = t.getIntOr("y0", SIN_ALTURA);
		y1 = t.getIntOr("y1", SIN_ALTURA);
		radio = t.getIntOr("radio", 10);
		puente = t.getBooleanOr("puente", false);
		maximo = t.getIntOr("max", 0);
		madera = t.getBooleanOr("madera", false);
	}

	/** La vuelve pasarela de madera sobre el agua (las capitales del pantano). */
	CalleCapital deMadera() {
		madera = true;
		return this;
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "calle"), TIPO);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		t.putInt("x0", x0);
		t.putInt("z0", z0);
		t.putInt("x1", x1);
		t.putInt("z1", z1);
		t.putInt("y0", y0);
		t.putInt("y1", y1);
		t.putInt("radio", radio);
		t.putBoolean("puente", puente);
		t.putInt("max", maximo);
		t.putBoolean("madera", madera);
	}

	@Override
	public boolean isCloseToChunk(ChunkPos chunk, int distancia) {
		return y0 == SIN_ALTURA && super.isCloseToChunk(chunk, distancia);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar,
			BoundingBox caja, ChunkPos chunk, BlockPos pivote) {
		BoundingBox mia = getBoundingBox();
		int ax = Math.max(mia.minX(), caja.minX());
		int bx = Math.min(mia.maxX(), caja.maxX());
		int az = Math.max(mia.minZ(), caja.minZ());
		int bz = Math.min(mia.maxZ(), caja.maxZ());
		double r = radio / 10.0;
		double dx = x1 - x0;
		double dz = z1 - z0;
		double l2 = dx * dx + dz * dz;
		// el pretil, unido a lo largo si el tramo va casi recto por un eje; en diagonal, postes sueltos
		Boolean aLoLargoDeZ = Math.abs(dz) >= 2 * Math.abs(dx) ? Boolean.TRUE : Math.abs(dx) >= 2 * Math.abs(dz) ? Boolean.FALSE : null;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = ax; x <= bx; x++) {
			for (int z = az; z <= bz; z++) {
				double t = l2 == 0 ? 0 : Math.clamp(((x - x0) * dx + (z - z0) * dz) / l2, 0, 1);
				double d = Math.hypot(x0 + t * dx - x, z0 + t * dz - z);
				if (d > r + 1.5) {
					continue;
				}
				boolean calle = d <= r + 0.3;
				SueloNatural.tala(level, caja, x, z);
				int y = y0 == SIN_ALTURA ? SIN_ALTURA : (int) Math.round(y0 + t * (y1 - y0));
				BlockState s = SueloNatural.superficie(level, pos, x, z);
				if (s.is(Blocks.WATER)) {
					if ((puente || madera) && d <= r + 1.3) {
						puente(level, pos, !calle, Math.floorMod(x + z, madera ? 3 : PILARES) == 0, aLoLargoDeZ, madera);
					}
				} else if (y != SIN_ALTURA && Math.abs(pos.getY() - y) > corte() + 1) {
					// un acantilado o un barranco que el trazado no vio: ahí no se pinta calle
					continue;
				} else if (calle) {
					s = allana(level, pos, s, y, 0, corte(), relleno());
					pon(level, pos, s);
				} else if (y != SIN_ALTURA) {
					// la cuneta: un escalón entre la calle y el suelo de al lado, para que no quede un tajo
					allana(level, pos, s, y, 1, corte(), relleno());
				}
			}
		}
	}

	/**
	 * Lleva el suelo de pos (cuyo bloque de arriba es s) a la altura y (más o menos holgura): rebaja lo natural que
	 * sobra o rellena con tierra lo que falta, si es poco; si no, o si hay algo que no es natural, lo deja. Devuelve el
	 * bloque de arriba, con pos en él.
	 */
	private int corte() {
		return Math.max(MAX_CORTE, maximo);
	}

	private int relleno() {
		return Math.max(MAX_RELLENO, maximo);
	}

	private static BlockState allana(WorldGenLevel level, BlockPos.MutableBlockPos pos, BlockState s, int y, int holgura, int maxCorte,
			int maxRelleno) {
		if (y == SIN_ALTURA) {
			return s;
		}
		int h = pos.getY();
		if (h > y + holgura && h - y <= maxCorte) {
			int x = pos.getX();
			int z = pos.getZ();
			for (int k = y + holgura + 1; k <= h; k++) {
				pos.set(x, k, z);
				BlockState b = level.getBlockState(pos);
				if (!SueloNatural.natural(b) && !b.canBeReplaced() && !b.is(BlockTags.LEAVES) && !b.is(BlockTags.LOGS)) {
					pos.set(x, h, z);
					return s;
				}
			}
			// y lo de encima de lo que se quita (las plantas ya no están; los troncos de un árbol sí se quedan)
			for (int k = h; k > y + holgura; k--) {
				pos.set(x, k, z);
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
			pos.set(x, y + holgura, z);
			BlockState abajo = level.getBlockState(pos);
			// lo que queda arriba es lo de debajo de la hierba: vuelve a ser hierba en la cuneta
			if (holgura > 0 && abajo.is(Blocks.DIRT) && s.is(Blocks.GRASS_BLOCK)) {
				level.setBlock(pos, s, 2);
				return s;
			}
			return abajo;
		}
		if (h < y - holgura && y - h <= maxRelleno) {
			int x = pos.getX();
			int z = pos.getZ();
			for (int k = h + 1; k <= y - holgura; k++) {
				pos.set(x, k, z);
				if (!level.getBlockState(pos).canBeReplaced()) {
					pos.set(x, k - 1, z);
					return level.getBlockState(pos);
				}
			}
			BlockState relleno = SueloNatural.natural(s) ? s : Blocks.DIRT.defaultBlockState();
			for (int k = h; k <= y - holgura; k++) {
				pos.set(x, k, z);
				level.setBlock(pos, k == y - holgura ? relleno : Blocks.DIRT.defaultBlockState(), 2);
			}
			return relleno;
		}
		return s;
	}

	/**
	 * Una casilla del puente sobre el agua de pos: losa medio bloque por encima (se sube desde la orilla sin saltar),
	 * pretil unido a lo largo (aLoLargoDeZ) y, a trechos, el pilar hasta el fondo.
	 */
	private static void puente(WorldGenLevel level, BlockPos.MutableBlockPos pos, boolean pretil, boolean pilar, Boolean aLoLargoDeZ,
			boolean madera) {
		Block suelo = madera ? Blocks.MANGROVE_SLAB : Blocks.STONE_BRICK_SLAB;
		Block baranda = madera ? Blocks.MANGROVE_FENCE : Blocks.STONE_BRICK_WALL;
		int agua = pos.getY();
		pos.setY(agua + 1);
		BlockState losa = level.getBlockState(pos);
		if (!pretil) {
			level.setBlock(pos, suelo.defaultBlockState(), 2);
			// si el pretil de otro tramo cayó aquí (en una curva), fuera: esto es calle
			pos.setY(agua + 2);
			if (level.getBlockState(pos).is(baranda)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
			return;
		}
		// el pretil no se pone encima de la calle de otro tramo
		if (losa.is(suelo)) {
			return;
		}
		level.setBlock(pos, suelo.defaultBlockState(), 2);
		pos.setY(agua + 2);
		BlockState muro = baranda.defaultBlockState();
		if (aLoLargoDeZ != null) {
			if (madera) {
				muro = aLoLargoDeZ ? muro.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true)
						: muro.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true);
			} else {
				muro = aLoLargoDeZ ? muro.setValue(BlockStateProperties.NORTH_WALL, WallSide.LOW).setValue(BlockStateProperties.SOUTH_WALL, WallSide.LOW)
						: muro.setValue(BlockStateProperties.EAST_WALL, WallSide.LOW).setValue(BlockStateProperties.WEST_WALL, WallSide.LOW);
			}
		}
		level.setBlock(pos, muro, 2);
		if (pilar) {
			for (int y = agua; y > level.getMinY(); y--) {
				pos.setY(y);
				BlockState b = level.getBlockState(pos);
				if (y != agua && !b.is(Blocks.WATER) && !b.canBeReplaced()) {
					break;
				}
				level.setBlock(pos, (madera ? Blocks.MANGROVE_LOG : Blocks.STONE_BRICKS).defaultBlockState(), 2);
			}
		}
	}

	private static void pon(WorldGenLevel level, BlockPos.MutableBlockPos pos, BlockState s) {
		BlockState nuevo = null;
		if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL)
				|| s.is(Blocks.MYCELIUM) || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.SNOW_BLOCK)) {
			nuevo = Blocks.DIRT_PATH.defaultBlockState();
		} else if (s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)) {
			nuevo = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
		} else if (s.is(Blocks.GRAVEL) || s.is(Blocks.MUD) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.TERRACOTTA)
				|| s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.CALCITE)) {
			nuevo = Blocks.COBBLESTONE.defaultBlockState();
		}
		// el resto (casas, caminos ya puestos, hielo…) se deja como está
		if (nuevo != null) {
			level.setBlock(pos, nuevo, 2);
		}
	}
}
