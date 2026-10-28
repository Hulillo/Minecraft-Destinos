package es.hulillo.destinos;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Lo que hace bonita a una capital, puesto al final sobre lo que ya han dejado sus casas y calles: los caminos de
 * tierra se empiedran, alrededor de la fuente o el pozo del centro se hace una plaza mayor enlosada con pilares de
 * estandartes del color del reino y puestos de mercado, y las calles se alumbran con farolas. Solo toca el suelo
 * natural y los caminos, nunca las casas. Cada cultura con sus piedras: arenisca en el desierto, adobe en la sabana.
 *
 * La caja abarca toda la capital, así que no debe contar para adaptar el terreno: las piezas que no son de pueblo
 * cuentan como cajas rígidas y el terreno se rellena bajo ellas (con la caja arriba del mundo salía una losa de
 * tierra flotando sobre toda la capital). Por eso dice que no está cerca de ningún chunk, que es lo único que mira
 * el allanado; para ponerse se mira su caja.
 */
public final class AdornoCapital extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) AdornoCapital::new;
	static final int TEMPLADO = 0;
	static final int TAIGA = 1;
	static final int NIEVE = 2;
	static final int SABANA = 3;
	static final int DESIERTO = 4;
	/** Radio de la plaza mayor alrededor del centro de la capital. */
	static final int PLAZA = 20;
	/** Hasta dónde llegan, pasado el borde de la plaza mayor, las rampas de las avenidas. */
	private static final int RAMPA = 14;
	/** Distancia del centro a los puestos de mercado. */
	private static final int ANILLO_MERCADO = 12;
	/** Distancia del centro a los bancos de la plaza (entre el pozo o la fuente del centro y los puestos). */
	private static final int ANILLO_BANCOS = 8;
	/** Distancia del centro a las jardineras, detrás de cada puesto. */
	private static final int ANILLO_JARDIN = 17;
	/** Las farolas: como mucho una por celda de LADO×LADO, y solo en su parte de en medio (así quedan separadas). */
	private static final int LADO = 16;
	private static final int K_PIEDRA = 4417;
	private static final int K_FAROLA = 9133;

	private final int px;
	private final int pz;
	private final int estilo;
	/** Color del reino (id de DyeColor), o -1 si no se sabe. */
	private final int color;
	/** Si lleva el anillo de puestos en la plaza (solo si la capital no tiene plaza del mercado). */
	private final boolean puestos;
	/** Altura de la plaza mayor (la mediana de su suelo), o MIN_VALUE en las guardadas antes: sin allanar. */
	private final int py;

	AdornoCapital(int px, int py, int pz, int estilo, int color, BoundingBox capital, int techo, boolean puestos) {
		super(TIPO, 0, new BoundingBox(capital.minX(), techo, capital.minZ(), capital.maxX(), techo, capital.maxZ()));
		this.px = px;
		this.pz = pz;
		this.estilo = estilo;
		this.color = color;
		this.puestos = puestos;
		this.py = py;
	}

	private AdornoCapital(CompoundTag t) {
		super(TIPO, t);
		px = t.getIntOr("px", 0);
		pz = t.getIntOr("pz", 0);
		estilo = t.getIntOr("estilo", TEMPLADO);
		color = t.getIntOr("color", -1);
		puestos = t.getBooleanOr("puestos", true);
		py = t.getIntOr("py", Integer.MIN_VALUE);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "adorno"), TIPO);
	}

	/** El estilo según el pueblo copiado (su nombre) y, si es de otro mod sin pistas, lo caluroso del bioma. */
	static int estilo(String pueblo, float temperatura) {
		if (pueblo.contains("desert")) {
			return DESIERTO;
		}
		if (pueblo.contains("savanna")) {
			return SABANA;
		}
		if (pueblo.contains("snow")) {
			return NIEVE;
		}
		if (pueblo.contains("taiga")) {
			return TAIGA;
		}
		return temperatura > 1.5F ? DESIERTO : temperatura < 0.15F ? NIEVE : TEMPLADO;
	}

	/** El tinte que más se parece al color (RGB) del reino. */
	static int tinte(int rgb) {
		DyeColor mejor = DyeColor.WHITE;
		long dist = Long.MAX_VALUE;
		for (DyeColor d : DyeColor.values()) {
			int c = d.getTextureDiffuseColor();
			long dr = ((c >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
			long dg = ((c >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
			long db = (c & 0xFF) - (rgb & 0xFF);
			long n = dr * dr + dg * dg + db * db;
			if (n < dist) {
				dist = n;
				mejor = d;
			}
		}
		return mejor.getId();
	}

	@Override
	public boolean isCloseToChunk(ChunkPos chunk, int distancia) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		t.putInt("px", px);
		t.putInt("pz", pz);
		t.putInt("estilo", estilo);
		t.putInt("color", color);
		t.putBoolean("puestos", puestos);
		t.putInt("py", py);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar,
			BoundingBox caja, ChunkPos chunk, BlockPos pivote) {
		BoundingBox mia = getBoundingBox();
		int x0 = Math.max(mia.minX(), caja.minX());
		int x1 = Math.min(mia.maxX(), caja.maxX());
		int z0 = Math.max(mia.minZ(), caja.minZ());
		int z1 = Math.min(mia.maxZ(), caja.maxZ());
		if (x0 > x1 || z0 > z1) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// 1. empedrar: calles (los caminos de tierra) y la plaza (el suelo natural cerca del centro)
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				allana(level, pos, x, z);
				empiedra(level, pos, x, z);
			}
		}
		// 2. farolas a los lados de las calles
		for (int cx = Math.floorDiv(x0, LADO); cx <= Math.floorDiv(x1, LADO); cx++) {
			for (int cz = Math.floorDiv(z0, LADO); cz <= Math.floorDiv(z1, LADO); cz++) {
				farola(level, caja, pos, cx, cz);
			}
		}
		// 3. la plaza mayor: estandartes en las cuatro diagonales del borde (las calles entran por los lados) y un
		// anillo de ocho puestos de mercado alrededor del pozo o la fuente del centro
		int diagonal = (int) Math.round((PLAZA - 3) / Math.sqrt(2));
		for (int[] d : List.of(new int[] {1, 1}, new int[] {1, -1}, new int[] {-1, 1}, new int[] {-1, -1})) {
			estandartes(level, caja, pos, px + d[0] * diagonal, pz + d[1] * diagonal);
		}
		for (int i = 0; i < 8; i++) {
			double a = Math.PI / 8 + i * Math.PI / 4;
			int x = px + (int) Math.round(Math.cos(a) * ANILLO_MERCADO);
			int z = pz + (int) Math.round(Math.sin(a) * ANILLO_MERCADO);
			if (puestos) {
				puesto(level, caja, pos, x, z, Geo.hash(x, z, K_PIEDRA));
			}
			// y por fuera de cada puesto, hacia el borde, una jardinera con flores y un árbol
			int jx = px + (int) Math.round(Math.cos(a) * ANILLO_JARDIN);
			int jz = pz + (int) Math.round(Math.sin(a) * ANILLO_JARDIN);
			jardinera(level, caja, pos, jx, jz, Geo.hash(jx, jz, K_PIEDRA));
		}
		// 4. un sitio para reunirse: bancos mirando al centro entre los puestos y, si el mercado está en su plaza
		// propia, en el anillo de los puestos corros de bancos con mesa a la sombra de un farol
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			Direction fuera = haciaFuera(a);
			int x = px + (int) Math.round(Math.cos(a) * ANILLO_BANCOS);
			int z = pz + (int) Math.round(Math.sin(a) * ANILLO_BANCOS);
			banco(level, caja, pos, x, z, fuera);
			if (!puestos) {
				double b = Math.PI / 8 + i * Math.PI / 4;
				int mx = px + (int) Math.round(Math.cos(b) * ANILLO_MERCADO);
				int mz = pz + (int) Math.round(Math.sin(b) * ANILLO_MERCADO);
				corro(level, caja, pos, mx, mz, haciaFuera(b));
			}
		}
	}

	/** La dirección de los cuatro ejes más parecida a la del ángulo a (hacia fuera de la plaza). */
	private static Direction haciaFuera(double a) {
		double c = Math.cos(a);
		double d = Math.sin(a);
		return Math.abs(c) >= Math.abs(d) ? (c > 0 ? Direction.EAST : Direction.WEST) : (d > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/** El peldaño de madera de los bancos, según la cultura. */
	private Block asiento() {
		return switch (estilo) {
			case DESIERTO -> Blocks.SMOOTH_SANDSTONE_STAIRS;
			case SABANA -> Blocks.ACACIA_STAIRS;
			case TAIGA, NIEVE -> Blocks.SPRUCE_STAIRS;
			default -> Blocks.DARK_OAK_STAIRS;
		};
	}

	/** Si las columnas de x0..x1 × z0..z1 son plaza empedrada a la misma altura y y libres por encima; su altura o MIN_VALUE. */
	private int llano(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos, int x0, int z0, int x1, int z1, int alto) {
		if (!dentro(caja, Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1))) {
			return Integer.MIN_VALUE;
		}
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x0, z0) - 1;
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				int sy = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
				if (sy != y || !empedrado(level.getBlockState(pos.set(x, y, z))) || !libre(level, pos, x, y + 1, z, alto)) {
					return Integer.MIN_VALUE;
				}
			}
		}
		return y;
	}

	/** Un banco de tres con brazos, de espaldas hacia fuera (mirando al centro), centrado en (x, z). */
	private void banco(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos, int x, int z, Direction fuera) {
		Direction lado = fuera.getClockWise();
		int y = llano(level, caja, pos, x - 2 * lado.getStepX(), z - 2 * lado.getStepZ(), x + 2 * lado.getStepX(), z + 2 * lado.getStepZ(), 2);
		if (y == Integer.MIN_VALUE) {
			return;
		}
		BlockState peldano = asiento().defaultBlockState().setValue(StairBlock.FACING, fuera);
		for (int k = -1; k <= 1; k++) {
			level.setBlock(pos.set(x + k * lado.getStepX(), y + 1, z + k * lado.getStepZ()), peldano, 2);
		}
		Block trampilla = estilo == DESIERTO ? Blocks.BIRCH_TRAPDOOR : estilo == SABANA ? Blocks.ACACIA_TRAPDOOR
				: estilo == TAIGA || estilo == NIEVE ? Blocks.SPRUCE_TRAPDOOR : Blocks.DARK_OAK_TRAPDOOR;
		for (int k : new int[] {-2, 2}) {
			Direction hacia = k < 0 ? lado.getOpposite() : lado;
			level.setBlock(pos.set(x + k * lado.getStepX(), y + 1, z + k * lado.getStepZ()), trampilla.defaultBlockState()
					.setValue(TrapDoorBlock.FACING, hacia).setValue(TrapDoorBlock.OPEN, true), 2);
		}
	}

	/** Un corro: mesa con mantel y farol, dos bancos enfrentados a los lados y un arbolito detrás, hacia fuera. */
	private void corro(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos, int x, int z, Direction fuera) {
		Direction lado = fuera.getClockWise();
		int y = llano(level, caja, pos, x - 2 * lado.getStepX() - fuera.getStepX(), z - 2 * lado.getStepZ() - fuera.getStepZ(),
				x + 2 * lado.getStepX() + fuera.getStepX(), z + 2 * lado.getStepZ() + fuera.getStepZ(), 3);
		if (y == Integer.MIN_VALUE) {
			return;
		}
		Block valla = estilo == SABANA ? Blocks.ACACIA_FENCE : estilo == TAIGA || estilo == NIEVE ? Blocks.SPRUCE_FENCE
				: estilo == DESIERTO ? Blocks.BIRCH_FENCE : Blocks.DARK_OAK_FENCE;
		// la mesa: dos patas con tablero de alfombra del color del reino y un farol encima
		for (int k = 0; k <= 1; k++) {
			int mx = x + k * fuera.getStepX();
			int mz = z + k * fuera.getStepZ();
			level.setBlock(pos.set(mx, y + 1, mz), valla.defaultBlockState(), 2);
			level.setBlock(pos.set(mx, y + 2, mz), (color >= 0 ? alfombra(color) : Blocks.WHITE_CARPET).defaultBlockState(), 2);
		}
		level.setBlock(pos.set(x, y + 3, z), Blocks.LANTERN.defaultBlockState(), 2);
		// los bancos, a los dos lados, mirando a la mesa
		for (int s = -1; s <= 1; s += 2) {
			Direction respaldo = s < 0 ? lado.getOpposite() : lado;
			for (int k = 0; k <= 1; k++) {
				level.setBlock(pos.set(x + s * lado.getStepX() + k * fuera.getStepX(), y + 1, z + s * lado.getStepZ() + k * fuera.getStepZ()),
						asiento().defaultBlockState().setValue(StairBlock.FACING, respaldo), 2);
			}
		}
	}

	private static Block alfombra(int color) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(DyeColor.byId(color).getName() + "_carpet"));
	}

	/** Jardinera de 3×3 en la plaza: césped con flores alrededor y un arbolito en medio, si cabe en plaza llana. */
	private void jardinera(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos, int x, int z, int h) {
		if (!dentro(caja, x - 2, z - 2, x + 2, z + 2)) {
			return;
		}
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				int sy = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x + ox, z + oz) - 1;
				if (sy != y || !empedrado(level.getBlockState(pos.set(x + ox, y, z + oz))) || !libre(level, pos, x + ox, y + 1, z + oz, 6)) {
					return;
				}
			}
		}
		Block[] flores = estilo == DESIERTO ? new Block[] {Blocks.DEAD_BUSH}
				: new Block[] {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET};
		Block borde = estilo == DESIERTO ? Blocks.CUT_SANDSTONE_SLAB : estilo == SABANA ? Blocks.MUD_BRICK_SLAB : Blocks.STONE_BRICK_SLAB;
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				level.setBlock(pos.set(x + ox, y, z + oz), (estilo == DESIERTO ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState(), 2);
				if (ox != 0 || oz != 0) {
					level.setBlock(pos.set(x + ox, y + 1, z + oz),
							flores[Math.floorMod(h + ox * 3 + oz * 7, flores.length)].defaultBlockState(), 2);
				}
			}
		}
		// el bordillo alrededor
		for (int i = -2; i <= 2; i++) {
			for (int[] b : new int[][] {{i, -2}, {i, 2}, {-2, i}, {2, i}}) {
				int bx = x + b[0];
				int bz = z + b[1];
				int by = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, bx, bz) - 1;
				if (by == y && empedrado(level.getBlockState(pos.set(bx, by, bz))) && libre(level, pos, bx, by + 1, bz, 1)) {
					level.setBlock(pos.set(bx, by + 1, bz), borde.defaultBlockState(), 2);
				}
			}
		}
		arbolito(level, pos, x, y + 1, z, h);
	}

	/** Un árbol pequeño del estilo de la capital: tronco de 4 y copa redonda (hojas que no se caen). */
	private void arbolito(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int h) {
		if (estilo == DESIERTO) {
			for (int i = 0; i < 3; i++) {
				level.setBlock(pos.set(x, y + i, z), Blocks.CACTUS.defaultBlockState(), 2);
			}
			return;
		}
		Block tronco = estilo == SABANA ? Blocks.ACACIA_LOG : estilo == TAIGA || estilo == NIEVE ? Blocks.SPRUCE_LOG
				: Math.floorMod(h, 3) == 0 ? Blocks.CHERRY_LOG : Blocks.OAK_LOG;
		Block hojas = estilo == SABANA ? Blocks.ACACIA_LEAVES : estilo == TAIGA || estilo == NIEVE ? Blocks.SPRUCE_LEAVES
				: tronco == Blocks.CHERRY_LOG ? Blocks.CHERRY_LEAVES : Blocks.OAK_LEAVES;
		BlockState hoja = hojas.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int dy = 2; dy <= 5; dy++) {
			int r = dy == 5 ? 1 : 2;
			for (int ox = -r; ox <= r; ox++) {
				for (int oz = -r; oz <= r; oz++) {
					if (Math.abs(ox) + Math.abs(oz) <= r + (dy == 3 || dy == 4 ? 1 : 0) && level.getBlockState(pos.set(x + ox, y + dy, z + oz)).isAir()) {
						level.setBlock(pos, hoja, 2);
					}
				}
			}
		}
		for (int i = 0; i < 4; i++) {
			level.setBlock(pos.set(x, y + i, z), tronco.defaultBlockState(), 2);
		}
	}

	/**
	 * La plaza mayor llana, a la altura py: el terreno de dentro se rellena o se rebaja (solo el natural, nunca lo
	 * construido). Justo fuera del borde, si el terreno queda más alto o más bajo, un murete de contención de la piedra
	 * de las calles (con pretil donde la plaza queda en alto), salvo donde entran las avenidas: ahí una rampa de un
	 * bloque por bloque que sube o baja hasta el terreno.
	 */
	private void allana(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		if (py == Integer.MIN_VALUE) {
			return;
		}
		int dx = x - px;
		int dz = z - pz;
		double r = Math.sqrt((double) dx * dx + (double) dz * dz);
		boolean avenida = Math.abs(dx) <= 2 || Math.abs(dz) <= 2;
		if (r > PLAZA + (avenida ? RAMPA : 1.5)) {
			return;
		}
		int h = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
		BlockState arriba = level.getBlockState(pos.set(x, h, z));
		if (!arriba.getFluidState().isEmpty()) {
			return;
		}
		// lo de encima del suelo (hierba, flores) no cuenta
		while (arriba.canBeReplaced() && h > py - 32) {
			h--;
			arriba = level.getBlockState(pos.set(x, h, z));
		}
		boolean calle = arriba.is(Blocks.DIRT_PATH) || empedrado(arriba);
		if (!SueloNatural.natural(arriba) && !calle) {
			return;
		}
		if (r > PLAZA && avenida) {
			// la rampa de la avenida, de tierra pisada (luego se empiedra como calle)
			int m = (int) Math.ceil(r - PLAZA);
			mueve(level, pos, x, z, h, Math.clamp(h, py - m, py + m), Blocks.DIRT_PATH.defaultBlockState());
			return;
		}
		if (r > PLAZA) {
			if (Math.abs(h - py) > 1) {
				for (int y = Math.min(h, py) + 1; y <= Math.max(h, py); y++) {
					level.setBlock(pos.set(x, y, z), calle(x, z + y), 2);
				}
				// la plaza en alto: pretil para no caerse
				if (h < py && level.getBlockState(pos.set(x, py + 1, z)).isAir()) {
					level.setBlock(pos, pretil(), 2);
				}
			}
			return;
		}
		mueve(level, pos, x, z, h, py, Blocks.GRASS_BLOCK.defaultBlockState());
	}

	/** Lleva el suelo de la columna de h a objetivo: rellena de tierra o rebaja el terreno, y deja cima encima. */
	private void mueve(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int h, int objetivo, BlockState cima) {
		if (h < objetivo) {
			for (int y = h + 1; y <= objetivo; y++) {
				level.setBlock(pos.set(x, y, z), y == objetivo ? cima : Blocks.DIRT.defaultBlockState(), 2);
			}
		} else if (h > objetivo) {
			for (int y = h + 3; y > objetivo; y--) {
				BlockState b = level.getBlockState(pos.set(x, y, z));
				if (SueloNatural.natural(b) || b.is(Blocks.DIRT_PATH) || empedrado(b) || !b.isAir() && b.canBeReplaced() && b.getFluidState().isEmpty()) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				}
			}
			level.setBlock(pos.set(x, objetivo, z), cima, 2);
		}
	}

	private BlockState pretil() {
		return switch (estilo) {
			case DESIERTO -> Blocks.SANDSTONE_WALL.defaultBlockState();
			case SABANA -> Blocks.MUD_BRICK_WALL.defaultBlockState();
			case TAIGA, NIEVE -> Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState();
			default -> Blocks.COBBLESTONE_WALL.defaultBlockState();
		};
	}

	private void empiedra(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		int dx = x - px;
		int dz = z - pz;
		int r2 = dx * dx + dz * dz;
		pos.set(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
		BlockState s = level.getBlockState(pos);
		if (s.is(Blocks.DIRT_PATH)) {
			level.setBlock(pos, r2 <= PLAZA * PLAZA ? plaza(dx, dz) : calle(x, z), 2);
			return;
		}
		if (r2 > PLAZA * PLAZA) {
			return;
		}
		s = SueloNatural.superficie(level, pos, x, z);
		if (SueloNatural.natural(s) || s.is(Blocks.DIRT_PATH)) {
			level.setBlock(pos, plaza(dx, dz), 2);
		}
	}

	/** Losa de la calle en (x, z): una mezcla de piedras según la cultura, siempre la misma en cada sitio. */
	private BlockState calle(int x, int z) {
		int h = Math.floorMod(Geo.hash(x, z, K_PIEDRA), 100);
		Block b = switch (estilo) {
			case DESIERTO -> h < 45 ? Blocks.SMOOTH_SANDSTONE : h < 75 ? Blocks.CUT_SANDSTONE : Blocks.SANDSTONE;
			case SABANA -> h < 40 ? Blocks.PACKED_MUD : h < 75 ? Blocks.MUD_BRICKS : h < 90 ? Blocks.TERRACOTTA : Blocks.COBBLESTONE;
			case TAIGA -> h < 35 ? Blocks.COBBLESTONE : h < 60 ? Blocks.MOSSY_COBBLESTONE : h < 85 ? Blocks.STONE_BRICKS
					: Blocks.MOSSY_STONE_BRICKS;
			case NIEVE -> h < 40 ? Blocks.STONE_BRICKS : h < 70 ? Blocks.COBBLESTONE : h < 85 ? Blocks.POLISHED_ANDESITE
					: Blocks.CRACKED_STONE_BRICKS;
			default -> h < 40 ? Blocks.COBBLESTONE : h < 70 ? Blocks.STONE_BRICKS : h < 85 ? Blocks.ANDESITE
					: h < 95 ? Blocks.MOSSY_COBBLESTONE : Blocks.POLISHED_ANDESITE;
		};
		return b.defaultBlockState();
	}

	/** Losa de la plaza: anillos alrededor del centro, con un borde marcado. */
	private BlockState plaza(int dx, int dz) {
		int anillo = (int) Math.sqrt(dx * dx + dz * dz);
		boolean marca = anillo == PLAZA || anillo % 4 == 0;
		Block b = switch (estilo) {
			case DESIERTO -> marca ? Blocks.CUT_RED_SANDSTONE : Blocks.SMOOTH_SANDSTONE;
			case SABANA -> marca ? Blocks.MUD_BRICKS : Blocks.PACKED_MUD;
			case NIEVE -> marca ? Blocks.POLISHED_DIORITE : Blocks.STONE_BRICKS;
			case TAIGA -> marca ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS;
			default -> marca ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS;
		};
		return b.defaultBlockState();
	}

	private boolean empedrado(BlockState s) {
		return s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.STONE_BRICKS)
				|| s.is(Blocks.MOSSY_STONE_BRICKS) || s.is(Blocks.CRACKED_STONE_BRICKS) || s.is(Blocks.ANDESITE)
				|| s.is(Blocks.POLISHED_ANDESITE) || s.is(Blocks.POLISHED_DIORITE) || s.is(Blocks.SMOOTH_SANDSTONE)
				|| s.is(Blocks.CUT_SANDSTONE) || s.is(Blocks.SANDSTONE) || s.is(Blocks.CUT_RED_SANDSTONE)
				|| s.is(Blocks.PACKED_MUD) || s.is(Blocks.MUD_BRICKS) || s.is(Blocks.TERRACOTTA);
	}

	/**
	 * Una farola en la celda (cx, cz), si en su parte de en medio hay un trozo de calle con suelo natural al lado:
	 * la farola va en ese suelo, pegada a la calle.
	 */
	private void farola(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos, int cx, int cz) {
		int h = Geo.hash(cx, cz, K_FAROLA);
		int ox = Math.floorMod(h, 8);
		int oz = Math.floorMod(h >> 4, 8);
		for (int i = 0; i < 64; i++) {
			int x = cx * LADO + 4 + (ox + i) % 8;
			int z = cz * LADO + 4 + (oz + i / 8) % 8;
			if (!caja.isInside(x, caja.minY(), z) || cerca(x, z, PLAZA)) {
				continue;
			}
			BlockState suelo = SueloNatural.superficie(level, pos, x, z);
			int y = pos.getY();
			if (!SueloNatural.natural(suelo) || !libre(level, pos, x, y + 1, z, 4)) {
				continue;
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				int sx = x + d.getStepX();
				int sz = z + d.getStepZ();
				int sy = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, sx, sz) - 1;
				if (Math.abs(sy - y) <= 1 && empedrado(level.getBlockState(pos.set(sx, sy, sz)))) {
					ponFarola(level, pos, x, y, z);
					return;
				}
			}
		}
	}

	private void ponFarola(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
		BlockState base = switch (estilo) {
			case DESIERTO -> Blocks.SANDSTONE_WALL.defaultBlockState();
			case SABANA -> Blocks.MUD_BRICK_WALL.defaultBlockState();
			case TAIGA, NIEVE -> Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState();
			default -> Blocks.COBBLESTONE_WALL.defaultBlockState();
		};
		BlockState palo = switch (estilo) {
			case DESIERTO -> Blocks.SANDSTONE_WALL.defaultBlockState();
			case SABANA -> Blocks.ACACIA_FENCE.defaultBlockState();
			case TAIGA, NIEVE -> Blocks.SPRUCE_FENCE.defaultBlockState();
			default -> Blocks.DARK_OAK_FENCE.defaultBlockState();
		};
		level.setBlock(pos.set(x, y + 1, z), base, 2);
		level.setBlock(pos.set(x, y + 2, z), palo, 2);
		level.setBlock(pos.set(x, y + 3, z), palo, 2);
		level.setBlock(pos.set(x, y + 4, z), Blocks.LANTERN.defaultBlockState(), 2);
	}

	/** Pilar de piedra con un estandarte del reino en cada cara, si el sitio es plaza libre. */
	private void estandartes(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos, int x, int z) {
		if (color < 0 || !dentro(caja, x - 1, z - 1, x + 1, z + 1)) {
			return;
		}
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
		if (!empedrado(level.getBlockState(pos.set(x, y, z)))) {
			return;
		}
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				if (!libre(level, pos, x + ox, y + 1, z + oz, 5)) {
					return;
				}
			}
		}
		Block piedra = estilo == DESIERTO ? Blocks.CUT_SANDSTONE : estilo == SABANA ? Blocks.MUD_BRICKS : Blocks.STONE_BRICKS;
		Block remate = estilo == DESIERTO ? Blocks.CHISELED_SANDSTONE : estilo == SABANA ? Blocks.MUD_BRICKS
				: Blocks.CHISELED_STONE_BRICKS;
		for (int i = 1; i <= 3; i++) {
			level.setBlock(pos.set(x, y + i, z), piedra.defaultBlockState(), 2);
		}
		level.setBlock(pos.set(x, y + 4, z), remate.defaultBlockState(), 2);
		level.setBlock(pos.set(x, y + 5, z), Blocks.LANTERN.defaultBlockState(), 2);
		Block banner = bloque("wall_banner");
		for (Direction d : Direction.Plane.HORIZONTAL) {
			level.setBlock(new BlockPos(x + d.getStepX(), y + 4, z + d.getStepZ()),
					banner.defaultBlockState().setValue(WallBannerBlock.FACING, d), 2);
		}
		DestinosCore.LOG.info("Destinos Core: plaza mayor de la capital en {}, {}: estandartes en {}, {}, {} ({})", px, pz, x, y, z,
				level.getBlockState(pos.set(x + 1, y + 4, z)));
	}

	/** Sitios de alrededor que se prueban, por orden, si el puesto no cabe donde le toca. */
	private static final int[][] PRUEBAS_PUESTO = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2},
			{2, 2}, {-2, -2}, {2, -2}, {-2, 2}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}};

	/**
	 * Un puesto de mercado (de toldo, de verduras, de flores o un carro; ver PuestoMercado) con el mostrador hacia el
	 * centro de la plaza. Si donde le toca no cabe (una casa, un pozo, un desnivel), prueba un poco más allá; vale
	 * suelo empedrado o natural con un escalón de un bloque como mucho, que se enlosa al ponerlo. Se arrima un poco
	 * para caber entero en este chunk: así lo pone uno solo y nunca queda a medias.
	 */
	private void puesto(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos, int x, int z, int h) {
		if (!dentro(caja, x, z, x, z)) {
			return;
		}
		Obra o = PuestoMercado.construye(h, estilo, color, h);
		for (int[] d : PRUEBAS_PUESTO) {
			int cx = Math.clamp(x + d[0], caja.minX() + 2, caja.maxX() - 2);
			int cz = Math.clamp(z + d[1], caja.minZ() + 2, caja.maxZ() - 2);
			if (ponPuesto(level, pos, o, cx, cz, h)) {
				return;
			}
		}
	}

	private boolean ponPuesto(WorldGenLevel level, BlockPos.MutableBlockPos pos, Obra o, int x, int z, int h) {
		int hx = px - x;
		int hz = pz - z;
		int rot = Math.abs(hx) >= Math.abs(hz) ? (hx > 0 ? 1 : 3) : (hz > 0 ? 2 : 0);
		Rotation giro = switch (rot) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
		for (int fase = 0; fase < 2; fase++) {
			for (int u = -2; u <= 2; u++) {
				for (int v = -2; v <= 1; v++) {
					Obra.Columna c = o.columna(u, v);
					if (c == null) {
						continue;
					}
					// de lo local al mundo
					int wx = x + switch (rot) {
						case 1 -> -v;
						case 2 -> -u;
						case 3 -> v;
						default -> u;
					};
					int wz = z + switch (rot) {
						case 1 -> u;
						case 2 -> -v;
						case 3 -> -u;
						default -> v;
					};
					int sy = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, wx, wz) - 1;
					if (fase == 0) {
						BlockState suelo = level.getBlockState(pos.set(wx, sy, wz));
						boolean firme = empedrado(suelo) || SueloNatural.natural(suelo) || suelo.is(Blocks.DIRT_PATH);
						if (sy < y - 1 || sy > y || !firme || !libre(level, pos, wx, y + 1, wz, PuestoMercado.ALTO - 1)) {
							return false;
						}
						continue;
					}
					// el suelo, enlosado y a nivel
					if (!empedrado(level.getBlockState(pos.set(wx, y, wz)))) {
						level.setBlock(pos.immutable(), plaza(wx - px, wz - pz), 2);
					}
					for (int ly = 1; ly < PuestoMercado.ALTO; ly++) {
						BlockState b = c.get(ly);
						if (b != null) {
							level.setBlock(new BlockPos(wx, y + ly, wz), b.rotate(giro), 2);
						}
					}
				}
			}
		}
		DestinosCore.LOG.info("Destinos Core: puesto de mercado en {}, {}, {} (tipo {})", x, y, z, Math.floorMod(h, PuestoMercado.TIPOS));
		return true;
	}

	/** El bloque del color del reino de ese tipo (wool, wall_banner…). */
	private Block bloque(String tipo) {
		DyeColor d = DyeColor.byId(color);
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(d.getName() + "_" + tipo));
	}

	private boolean cerca(int x, int z, int r) {
		long dx = x - px;
		long dz = z - pz;
		return dx * dx + dz * dz <= (long) r * r;
	}

	private static boolean dentro(BoundingBox caja, int x0, int z0, int x1, int z1) {
		return x0 >= caja.minX() && x1 <= caja.maxX() && z0 >= caja.minZ() && z1 <= caja.maxZ();
	}

	/** Si los alto bloques desde (x, y, z) hacia arriba están vacíos (aire o plantas). */
	private static boolean libre(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int alto) {
		for (int i = 0; i < alto; i++) {
			BlockState s = level.getBlockState(pos.set(x, y + i, z));
			if (!s.isAir() && !(s.canBeReplaced() && s.getFluidState().isEmpty())) {
				return false;
			}
		}
		return true;
	}
}
