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
 * La caja abarca toda la capital pero está arriba del todo del mundo: las piezas que no son de pueblo cuentan como
 * cajas rígidas para allanar el terreno, y tan lejos del suelo no allanan nada.
 */
public final class AdornoCapital extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) AdornoCapital::new;
	static final int TEMPLADO = 0;
	static final int TAIGA = 1;
	static final int NIEVE = 2;
	static final int SABANA = 3;
	static final int DESIERTO = 4;
	/** Radio de la plaza mayor alrededor del centro de la capital. */
	private static final int PLAZA = 12;
	/** Las farolas: como mucho una por celda de LADO×LADO, y solo en su parte de en medio (así quedan separadas). */
	private static final int LADO = 8;
	private static final int K_PIEDRA = 4417;
	private static final int K_FAROLA = 9133;

	private final int px;
	private final int pz;
	private final int estilo;
	/** Color del reino (id de DyeColor), o -1 si no se sabe. */
	private final int color;

	AdornoCapital(int px, int pz, int estilo, int color, BoundingBox capital, int techo) {
		super(TIPO, 0, new BoundingBox(capital.minX(), techo, capital.minZ(), capital.maxX(), techo, capital.maxZ()));
		this.px = px;
		this.pz = pz;
		this.estilo = estilo;
		this.color = color;
	}

	private AdornoCapital(CompoundTag t) {
		super(TIPO, t);
		px = t.getIntOr("px", 0);
		pz = t.getIntOr("pz", 0);
		estilo = t.getIntOr("estilo", TEMPLADO);
		color = t.getIntOr("color", -1);
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
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		t.putInt("px", px);
		t.putInt("pz", pz);
		t.putInt("estilo", estilo);
		t.putInt("color", color);
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
		if (caja.isInside(px, caja.minY(), pz)) {
			int llenos = 0;
			for (int y = 100; y < 319; y++) {
				if (!level.getBlockState(pos.set(px, y, pz)).isAir()) {
					llenos++;
				}
			}
			DestinosCore.LOG.info("Destinos Core: adorno en {}, {}: superficie {} · fondo {} · en 318 {} · en 317 {} · bloques entre 100 y 318: {}",
					px, pz, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, px, pz), level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, px, pz),
					level.getBlockState(pos.set(px, 318, pz)), level.getBlockState(pos.set(px, 317, pz)), llenos);
		}
		// 1. empedrar: calles (los caminos de tierra) y la plaza (el suelo natural cerca del centro)
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				empiedra(level, pos, x, z);
			}
		}
		// 2. farolas a los lados de las calles
		for (int cx = Math.floorDiv(x0, LADO); cx <= Math.floorDiv(x1, LADO); cx++) {
			for (int cz = Math.floorDiv(z0, LADO); cz <= Math.floorDiv(z1, LADO); cz++) {
				farola(level, caja, pos, cx, cz);
			}
		}
		// 3. la plaza mayor: estandartes en los cuatro puntos cardinales y puestos de mercado en las diagonales
		for (Direction d : Direction.Plane.HORIZONTAL) {
			estandartes(level, caja, pos, px + d.getStepX() * (PLAZA - 1), pz + d.getStepZ() * (PLAZA - 1));
		}
		int diagonal = (int) Math.round((PLAZA - 4) / Math.sqrt(2));
		for (int[] d : List.of(new int[] {1, 1}, new int[] {1, -1}, new int[] {-1, 1}, new int[] {-1, -1})) {
			puesto(level, caja, pos, px + d[0] * diagonal, pz + d[1] * diagonal, Geo.hash(px + d[0], pz + d[1], K_PIEDRA));
		}
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
		int ox = Math.floorMod(h, 4) + 2;
		int oz = Math.floorMod(h >> 4, 4) + 2;
		for (int i = 0; i < 16; i++) {
			int x = cx * LADO + 2 + (ox + i) % 4;
			int z = cz * LADO + 2 + (oz + i / 4) % 4;
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

	/** Puesto de mercado de 3×2 con toldo a rayas del color del reino, si cabe en plaza libre y llana. */
	private void puesto(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos, int x, int z, int h) {
		if (!dentro(caja, x - 1, z - 1, x + 1, z)) {
			return;
		}
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 0; oz++) {
				int sy = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x + ox, z + oz) - 1;
				if (sy != y || !empedrado(level.getBlockState(pos.set(x + ox, y, z + oz))) || !libre(level, pos, x + ox, y + 1, z + oz, 4)) {
					return;
				}
			}
		}
		BlockState poste = (estilo == SABANA ? Blocks.ACACIA_FENCE : estilo == TAIGA || estilo == NIEVE ? Blocks.SPRUCE_FENCE
				: estilo == DESIERTO ? Blocks.BIRCH_FENCE : Blocks.OAK_FENCE).defaultBlockState();
		Block raya = color >= 0 ? bloque("wool") : Blocks.RED_WOOL;
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 0; oz++) {
				if (ox != 0) {
					level.setBlock(pos.set(x + ox, y + 1, z + oz), poste, 2);
					level.setBlock(pos.set(x + ox, y + 2, z + oz), poste, 2);
				}
				level.setBlock(pos.set(x + ox, y + 3, z + oz), (ox == 0 ? Blocks.WHITE_WOOL : raya).defaultBlockState(), 2);
			}
		}
		// el mostrador: un barril y algo de lo que se vende
		Block[] mercancia = {Blocks.HAY_BLOCK, Blocks.MELON, Blocks.PUMPKIN, Blocks.COMPOSTER, Blocks.SMOKER, Blocks.FLETCHING_TABLE};
		level.setBlock(new BlockPos(x, y + 1, z), Blocks.BARREL.defaultBlockState(), 2);
		DestinosCore.LOG.info("Destinos Core: puesto de mercado en {}, {}, {}", x, y, z);
		level.setBlock(pos.set(x, y + 1, z - 1), mercancia[Math.floorMod(h, mercancia.length)].defaultBlockState(), 2);
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
