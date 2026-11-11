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
	/** Las columnas cuyo suelo lo ha fijado una calle, una obra o la muralla: ahí no va pretil. */
	private final boolean[] ocupada;
	private final boolean arena;

	private TerrazaCapital(int x0, int z0, int lado, short[] alto, boolean arena, BoundingBox caja) {
		super(TIPO, 0, caja);
		this.x0 = x0;
		this.z0 = z0;
		this.lado = lado;
		this.alto = alto;
		this.ocupada = new boolean[alto.length];
		this.arena = arena;
	}

	private TerrazaCapital(CompoundTag t) {
		super(TIPO, t);
		x0 = 0;
		z0 = 0;
		lado = 0;
		alto = new short[0];
		ocupada = new boolean[0];
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
		if (calle > 0 && quien != null) {
			// dos calles distintas que pisan la misma columna a alturas que no casan: se apunta, para el registro
			int otra = quien[i * lado + j];
			if (otra > 0 && otra != calle && Math.abs(y - alto[i * lado + j]) >= 3) {
				choques++;
				if (ejemplos.length() < 400 && choques % 8 == 1) {
					ejemplos.append(' ').append(x).append(',').append(z).append(" (").append(alto[i * lado + j]).append(" de ").append(nombres.get(otra - 1))
							.append(", ").append(y).append(" de ").append(nombres.get(calle - 1)).append(')');
				}
			}
			quien[i * lado + j] = calle;
		}
		alto[i * lado + j] = Math.abs(y - alto[i * lado + j]) > MAX_OBRA ? (short) SIN : (short) y;
		ocupada[i * lado + j] = true;
	}

	/** Para el registro: qué calle ha fijado cada columna, y cuántas veces dos calles no casan en la misma. */
	private int[] quien;
	private int calle;
	private int choques;
	private final StringBuilder ejemplos = new StringBuilder();
	private final java.util.List<String> nombres = new java.util.ArrayList<>();

	/** Cuántas columnas pisan dos calles a alturas que no casan (3 bloques o más), con algunos sitios de ejemplo. */
	String choques() {
		return choques + " columnas con dos calles a distinta altura" + (choques > 0 ? ":" + ejemplos : "");
	}

	/** Como fija, para la muralla: si su pie queda lejos del bancal (un arco sobre una hondonada, un cortado), la columna se queda como estaba. */
	void acerca(int x, int z, int y) {
		int i = x - x0;
		int j = z - z0;
		if (i >= 0 && j >= 0 && i < lado && j < lado && alto[i * lado + j] != SIN && Math.abs(y - alto[i * lado + j]) <= MAX_OBRA) {
			alto[i * lado + j] = (short) y;
			ocupada[i * lado + j] = true;
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
			ocupada[i * lado + j] = true;
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
		// de la última a la primera: las principales se trazaron antes y son las que mandan donde otra las toca
		quien = new int[alto.length];
		for (int k = calles.size() - 1; k >= 0; k--) {
			calle = k + 1;
			while (nombres.size() <= k) {
				nombres.add("");
			}
			nombres.set(k, calles.get(k).apodo());
			calles.get(k).encaja(this);
		}
		calle = 0;
		quien = null;
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
		// también en diagonal: en las esquinas quedaba la tierra del relleno a la vista
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
			int v = bancal(x + d[0], z + d[1]);
			if (v != SIN) {
				bajo = Math.min(bajo, v);
			}
		}
		return t - bajo >= SALTO_MURO ? bajo : SIN;
	}

	/** El salto desde el que el borde del bancal lleva pretil, para que nadie se caiga. */
	private static final int SALTO_PRETIL = 3;

	/** Si el borde de (x, z) lleva pretil: un bancal sin calle ni obra encima, con una caída de SALTO_PRETIL o más a un lado (no en diagonal). */
	boolean pretil(int x, int z) {
		int i = x - x0;
		int j = z - z0;
		if (i < 0 || j < 0 || i >= lado || j >= lado || alto[i * lado + j] == SIN || ocupada[i * lado + j]) {
			return false;
		}
		int t = alto[i * lado + j];
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			int v = bancal(x + d[0], z + d[1]);
			if (v != SIN && t - v >= SALTO_PRETIL) {
				return true;
			}
		}
		return false;
	}

	/** De qué es el muro de contención de un trozo de bancal: 0 mampostería, 1 sillería, 2 tapial, 3 talud de tierra. Cambia por tramos y de un bancal a otro. */
	private int estilo(int x, int z, int t) {
		return Math.floorMod(Geo.hash(Math.floorDiv(x + z, 14), Math.floorDiv(x - z, 14), t), 4);
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

	/**
	 * Al acabar el chunk: tapa los manantiales que Minecraft abre en las paredes de piedra o tierra con una sola cara al
	 * aire (los cortes de los bancales y de las calles están llenos de sitios así, y el agua bajaba por las calles).
	 * Un manantial es una fuente de agua o lava con terreno justo encima y aire a un lado; una charca o un río no.
	 */
	void sinManantiales(WorldGenLevel level, BoundingBox caja) {
		BoundingBox mia = getBoundingBox();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = Math.max(mia.minX(), caja.minX()); x <= Math.min(mia.maxX(), caja.maxX()); x++) {
			for (int z = Math.max(mia.minZ(), caja.minZ()); z <= Math.min(mia.maxZ(), caja.maxZ()); z++) {
				int t = bancal(x, z);
				if (t == SIN) {
					continue;
				}
				for (int y = t - 6; y <= t + MAXIMO; y++) {
					if (!level.getFluidState(pos.set(x, y, z)).isSource()) {
						continue;
					}
					BlockState encima = level.getBlockState(pos.set(x, y + 1, z));
					if (!SueloNatural.natural(encima)) {
						continue;
					}
					boolean aire = level.getBlockState(pos.set(x + 1, y, z)).isAir() || level.getBlockState(pos.set(x - 1, y, z)).isAir()
							|| level.getBlockState(pos.set(x, y, z + 1)).isAir() || level.getBlockState(pos.set(x, y, z - 1)).isAir();
					if (aire) {
						level.setBlock(pos.set(x, y, z), encima.is(Blocks.GRASS_BLOCK) ? Blocks.DIRT.defaultBlockState() : encima, 2);
					}
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
		// ni se abre la orilla de una charca o un arroyo: el agua se iría bancal abajo
		if (h > t && SueloNatural.aguaAlLado(level, pos, x, z, t + 1, h + 1)) {
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
		BlockState arriba = s.is(Blocks.GRAVEL) ? relleno : s;
		level.setBlock(pos.set(x, t, z), arriba, 2);
		int bajo = muro(x, z);
		if (bajo != SIN) {
			// el muro de contención, de la altura del bancal de abajo a la de este; arriba se queda la hierba (o lo que
			// hubiera): el bancal se ve verde por encima y el muro solo de frente
			int estilo = t - bajo <= 2 ? 3 : estilo(x, z, t);
			for (int y = Math.min(bajo, h) + 1; y < t; y++) {
				level.setBlock(pos.set(x, y, z), cara(estilo, x, y, z, y - bajo), 2);
			}
			if (pretil(x, z) && level.getBlockState(pos.set(x, t + 1, z)).canBeReplaced()) {
				level.setBlock(pos, pretil(estilo, x, z, t), 2);
			}
		}
	}

	/** El bloque de la cara del muro: según el estilo del tramo, con musgo y piezas distintas a trozos; lo de más abajo, más basto. */
	private BlockState cara(int estilo, int x, int y, int z, int altura) {
		int azar = Math.floorMod(Geo.hash(x, z, y), 12);
		if (arena) {
			return (estilo == 2 ? (azar < 4 ? Blocks.MUD_BRICKS : Blocks.PACKED_MUD)
					: azar < 3 ? Blocks.CUT_SANDSTONE : azar == 3 ? Blocks.SMOOTH_SANDSTONE : Blocks.SANDSTONE).defaultBlockState();
		}
		return (switch (estilo) {
			case 0 -> azar < 3 ? Blocks.MOSSY_COBBLESTONE : azar == 3 ? Blocks.ANDESITE : azar == 4 ? Blocks.MOSS_BLOCK : Blocks.COBBLESTONE;
			case 1 -> azar < 2 ? Blocks.MOSSY_STONE_BRICKS : azar == 2 ? Blocks.CRACKED_STONE_BRICKS : altura <= 1 && azar < 6 ? Blocks.COBBLESTONE
					: Blocks.STONE_BRICKS;
			case 2 -> altura <= 1 ? (azar < 5 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE) : azar < 4 ? Blocks.MUD_BRICKS : Blocks.PACKED_MUD;
			default -> azar < 4 ? Blocks.COARSE_DIRT : azar < 6 ? Blocks.ROOTED_DIRT : azar == 6 ? Blocks.MOSS_BLOCK : azar == 7 ? Blocks.MOSSY_COBBLESTONE
					: Blocks.DIRT;
		}).defaultBlockState();
	}

	/** El pretil del borde: murete del material del muro (valla de madera en el talud de tierra, con algún seto), unido a los de al lado. */
	private BlockState pretil(int estilo, int x, int z, int t) {
		boolean[] une = new boolean[4];
		net.minecraft.core.Direction[] lados = {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH,
				net.minecraft.core.Direction.WEST};
		for (int k = 0; k < 4; k++) {
			int qx = x + lados[k].getStepX();
			int qz = z + lados[k].getStepZ();
			une[k] = pretil(qx, qz) && bancal(qx, qz) == t;
		}
		if (estilo == 3 && !arena) {
			if (Math.floorMod(Geo.hash(x, z, t), 5) == 0) {
				return Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
			}
			return Blocks.SPRUCE_FENCE.defaultBlockState()
					.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH, une[0])
					.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST, une[1])
					.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH, une[2])
					.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST, une[3]);
		}
		net.minecraft.world.level.block.Block muro = arena ? Blocks.SANDSTONE_WALL
				: estilo == 1 ? Blocks.STONE_BRICK_WALL : estilo == 2 ? Blocks.MUD_BRICK_WALL
						: Math.floorMod(Geo.hash(x, z, t), 3) == 0 ? Blocks.MOSSY_COBBLESTONE_WALL : Blocks.COBBLESTONE_WALL;
		net.minecraft.world.level.block.state.properties.WallSide si = net.minecraft.world.level.block.state.properties.WallSide.LOW;
		net.minecraft.world.level.block.state.properties.WallSide no = net.minecraft.world.level.block.state.properties.WallSide.NONE;
		boolean recto = une[0] && une[2] && !une[1] && !une[3] || une[1] && une[3] && !une[0] && !une[2];
		return muro.defaultBlockState()
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH_WALL, une[0] ? si : no)
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST_WALL, une[1] ? si : no)
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH_WALL, une[2] ? si : no)
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST_WALL, une[3] ? si : no)
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.UP, !recto);
	}
}
