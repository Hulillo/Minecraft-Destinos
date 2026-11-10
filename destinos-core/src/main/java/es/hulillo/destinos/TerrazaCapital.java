package es.hulillo.destinos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Las terrazas de una capital de montaña: donde el sitio es una ladera, el suelo se deja en bancales (cada uno
 * ESCALON bloques por encima del de debajo, con el borde siguiendo las curvas de nivel) sujetos por muros de
 * contención de piedra. Es lo primero que se pone en cada chunk: lo demás (casas, calles, muralla) se ha trazado ya
 * sobre los bancales y los encuentra hechos.
 *
 * Todo viene del plano: la altura de cada columna está decidida antes de poner nada (TerrenoCapital.bancal) y aquí
 * solo se rebaja lo que sobra, se rellena lo que falta y se levanta el muro donde el bancal de al lado queda más bajo.
 * El plano se rehace por semilla cuando hace falta, así que la pieza no se guarda: leída de un guardado, no hace nada.
 */
public final class TerrazaCapital extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) TerrazaCapital::new;
	/** Lo que se rebaja o se rellena como mucho en una columna; con más (un cortado, un pico), se deja como está. */
	private static final int MAXIMO = 12;
	/** El salto con el bancal de al lado desde el que hay muro de contención. */
	private static final int SALTO_MURO = 2;
	private static final int SIN = Short.MIN_VALUE;

	private final int x0;
	private final int z0;
	private final int lado;
	/** La altura del bancal de cada columna del cuadrado (SIN donde no se toca). */
	private final short[] alto;
	private final boolean arena;

	private TerrazaCapital(int x0, int z0, int lado, short[] alto, boolean arena, BoundingBox caja) {
		super(TIPO, 0, caja);
		this.x0 = x0;
		this.z0 = z0;
		this.lado = lado;
		this.alto = alto;
		this.arena = arena;
	}

	private TerrazaCapital(CompoundTag t) {
		super(TIPO, t);
		x0 = 0;
		z0 = 0;
		lado = 0;
		alto = new short[0];
		arena = false;
	}

	/** Las terrazas del terreno dado (ya aterrazado), o null si no hay ninguna columna que tocar. */
	static TerrazaCapital de(TerrenoCapital terreno, int cx, int cz, boolean arena) {
		int r = terreno.radioTerrazas();
		int lado = 2 * r + 1;
		short[] alto = new short[lado * lado];
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int i = 0; i < lado; i++) {
			for (int j = 0; j < lado; j++) {
				int h = terreno.bancal(cx - r + i, cz - r + j);
				alto[i * lado + j] = h == Integer.MIN_VALUE ? SIN : (short) h;
				if (h != Integer.MIN_VALUE) {
					min = Math.min(min, h);
					max = Math.max(max, h);
				}
			}
		}
		if (min > max) {
			return null;
		}
		return new TerrazaCapital(cx - r, cz - r, lado, alto, arena,
				new BoundingBox(cx - r, min - MAXIMO, cz - r, cx + r, max + MAXIMO, cz + r));
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "terraza"), TIPO);
	}

	/** Lo que una obra puede apartarse de su bancal; con más, su sitio se deja como esté (ya lo amolda el relleno de Minecraft). */
	private static final int MAX_OBRA = TerrenoCapital.ESCALON + 3;

	/**
	 * El suelo de (x, z) queda a la altura y: lo pide una calle o una obra que pasa por ahí (su suelo manda sobre el
	 * del bancal). Si se aparta demasiado del bancal, la columna se deja sin tocar.
	 */
	void fija(int x, int z, int y) {
		int i = x - x0;
		int j = z - z0;
		if (i < 0 || j < 0 || i >= lado || j >= lado || alto[i * lado + j] == SIN) {
			return;
		}
		alto[i * lado + j] = Math.abs(y - alto[i * lado + j]) > MAX_OBRA ? (short) SIN : (short) y;
	}

	/** Como fija, para la muralla: si su pie queda lejos del bancal (un arco sobre una hondonada, un cortado), la columna se queda como estaba. */
	void acerca(int x, int z, int y) {
		int i = x - x0;
		int j = z - z0;
		if (i >= 0 && j >= 0 && i < lado && j < lado && alto[i * lado + j] != SIN && Math.abs(y - alto[i * lado + j]) <= TerrenoCapital.ESCALON) {
			alto[i * lado + j] = (short) y;
		}
	}

	/** El suelo bajo la muralla y en los dos bloques de fuera queda a su pie: ni medio enterrada en el bancal de arriba ni en el aire sobre el de abajo. */
	void encaja(MurallaCapital muro) {
		muro.recorre(muro.getBoundingBox(), (x, z, pie, arriba, portillo, andado, nx, nz, fuera) -> {
			acerca(x, z, pie);
			if (fuera) {
				for (int k = 1; k <= 2; k++) {
					acerca(x + (int) Math.round(nx * k), z + (int) Math.round(nz * k), pie);
				}
			}
		});
	}

	/** La columna (x, z) no se toca (un puente, un muelle). */
	void suelta(int x, int z) {
		int i = x - x0;
		int j = z - z0;
		if (i >= 0 && j >= 0 && i < lado && j < lado) {
			alto[i * lado + j] = (short) SIN;
		}
	}

	/**
	 * Encaja los bancales con lo trazado: bajo cada calle, el suelo de la calle (una rampa entre bancales queda entre
	 * muros de contención); bajo cada obra y un bloque alrededor, el suelo de la obra. Así lo que el relleno de
	 * Minecraft amoldó para ellas no se deshace, y el salto con el bancal de al lado lo sujeta un muro.
	 */
	void encaja(java.util.List<CalleCapital> calles, java.util.List<? extends StructurePiece> obras) {
		for (StructurePiece o : obras) {
			BoundingBox b = o.getBoundingBox();
			if (b.getXSpan() <= 2 && b.getZSpan() <= 2) {
				continue;
			}
			// las obras propias pisan en y0 y su caja ya trae el borde; las casas de plantilla, en su suelo, con un bloque alrededor
			boolean obra = o instanceof PiezaObra;
			int y = obra ? b.minY() : b.minY() - 1 + (o instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece p ? p.getGroundLevelDelta() : 0);
			int m = obra ? 0 : 1;
			for (int x = b.minX() - m; x <= b.maxX() + m; x++) {
				for (int z = b.minZ() - m; z <= b.maxZ() + m; z++) {
					fija(x, z, y);
				}
			}
		}
		// las calles, lo último: donde pasan pegadas a una casa o entran en el borde de una plaza, manda su suelo (y el
		// caminito de cada puerta llega hasta la calle)
		for (CalleCapital c : calles) {
			c.encaja(this);
		}
	}

	/** La altura del bancal en (x, z), o SIN. */
	int bancal(int x, int z) {
		int i = x - x0;
		int j = z - z0;
		return i < 0 || j < 0 || i >= lado || j >= lado ? SIN : alto[i * lado + j];
	}

	static boolean sin(int bancal) {
		return bancal == SIN;
	}

	/** Si en (x, z) hay muro de contención: el bancal de algún lado queda SALTO_MURO o más por debajo. Devuelve hasta dónde baja, o SIN. */
	int muro(int x, int z) {
		int t = bancal(x, z);
		if (t == SIN) {
			return SIN;
		}
		int bajo = t;
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			int v = bancal(x + d[0], z + d[1]);
			if (v != SIN) {
				bajo = Math.min(bajo, v);
			}
		}
		return t - bajo >= SALTO_MURO ? bajo : SIN;
	}

	/** El terreno no se amolda a esta pieza con el relleno de Minecraft: lo hace ella, columna a columna. */
	@Override
	public boolean isCloseToChunk(ChunkPos chunk, int distancia) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar, BoundingBox caja,
			ChunkPos chunk, BlockPos pivote) {
		BoundingBox mia = getBoundingBox();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = Math.max(mia.minX(), caja.minX()); x <= Math.min(mia.maxX(), caja.maxX()); x++) {
			for (int z = Math.max(mia.minZ(), caja.minZ()); z <= Math.min(mia.maxZ(), caja.maxZ()); z++) {
				int t = bancal(x, z);
				if (t != SIN) {
					columna(level, caja, pos, x, z, t);
				}
			}
		}
	}

	private void columna(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos, int x, int z, int t) {
		SueloNatural.tala(level, caja, x, z);
		BlockState s = SueloNatural.superficie(level, pos, x, z);
		int h = pos.getY();
		// el agua no se toca, ni lo que no es terreno, ni un cortado o un pico que se sale de lo que se puede mover
		if (!s.getFluidState().isEmpty() || !SueloNatural.natural(s) || Math.abs(h - t) > MAXIMO) {
			return;
		}
		// la capa de arriba, como la que había (hierba, arena, nieve, piedra…); debajo, tierra (o arenisca: la arena se caería)
		BlockState relleno = (SueloNatural.arena(s) ? Blocks.SANDSTONE : s.is(Blocks.STONE) || s.is(Blocks.GRAVEL) ? Blocks.STONE : Blocks.DIRT)
				.defaultBlockState();
		for (int y = h; y > t; y--) {
			level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
		}
		for (int y = h; y < t; y++) {
			level.setBlock(pos.set(x, y, z), relleno, 2);
		}
		level.setBlock(pos.set(x, t, z), s.is(Blocks.GRAVEL) ? relleno : s, 2);
		int bajo = muro(x, z);
		if (bajo != SIN) {
			// el muro de contención: de piedra (con musgo a trozos) de la altura del bancal de abajo a la de este
			for (int y = Math.min(bajo, h) + 1; y <= t; y++) {
				level.setBlock(pos.set(x, y, z), piedra(x, y, z), 2);
			}
		}
	}

	private BlockState piedra(int x, int y, int z) {
		if (arena) {
			return (Math.floorMod(Geo.hash(x, z, y), 4) == 0 ? Blocks.CUT_SANDSTONE : Blocks.SANDSTONE).defaultBlockState();
		}
		int azar = Math.floorMod(Geo.hash(x, z, y), 6);
		return (azar == 0 ? Blocks.MOSSY_COBBLESTONE : azar == 1 ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE).defaultBlockState();
	}
}
