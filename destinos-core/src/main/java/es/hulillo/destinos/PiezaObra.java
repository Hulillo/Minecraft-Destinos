package es.hulillo.destinos;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.tags.BlockTags;
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
 * pone por chunks, girada, a una altura fija y con cimientos hasta el suelo de verdad. Alrededor, hasta el borde de la
 * caja, el terreno se ataluza: se rellena o se rebaja en bancal de un bloque por bloque de distancia, para que la obra
 * no quede ni colgando sobre un pilar ni metida en un hoyo.
 *
 * Coordenadas locales: (u, v) desde el centro, con la puerta en v negativa; rot gira lo local al mundo de 90 en 90
 * grados en el sentido de las agujas del reloj (0: la puerta mira al norte).
 */
abstract class PiezaObra extends StructurePiece {
	/** Margen de la caja alrededor de las obras pequeñas, para el bancal. */
	static final int TALUD = 4;
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
	/** Distancia (en planta, en local) de cada columna de la caja a la obra con suelo; se calcula con la obra. */
	private int[][] distancia;

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
			distancia = distancias(o);
			obra = o;
		}
		return obra;
	}

	/** Medio lado de la caja, en planta. */
	private int medio() {
		return (getBoundingBox().getXSpan() - 1) / 2;
	}

	/** La distancia de cada columna (u, v) de la caja a la columna con suelo más cercana (0 en la obra). */
	private int[][] distancias(Obra o) {
		int m = medio();
		int n = 2 * m + 1;
		int[][] d = new int[n][n];
		java.util.ArrayDeque<int[]> cola = new java.util.ArrayDeque<>();
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				Obra.Columna c = o.columna(i - m, j - m);
				if (c != null && c.planta) {
					d[i][j] = 0;
					cola.add(new int[] {i, j});
				} else {
					d[i][j] = Integer.MAX_VALUE;
				}
			}
		}
		while (!cola.isEmpty()) {
			int[] a = cola.poll();
			for (int di = -1; di <= 1; di++) {
				for (int dj = -1; dj <= 1; dj++) {
					int i = a[0] + di;
					int j = a[1] + dj;
					if (i >= 0 && j >= 0 && i < n && j < n && d[i][j] > d[a[0]][a[1]] + 1) {
						d[i][j] = d[a[0]][a[1]] + 1;
						cola.add(new int[] {i, j});
					}
				}
			}
		}
		return d;
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
				if (c == null || !c.planta) {
					int m = medio();
					if (Math.abs(u) <= m && Math.abs(v) <= m) {
						talud(level, pos, x, z, distancia[u + m][v + m], o.p);
					}
				}
				if (c != null) {
					columna(level, azar, pos, x, z, u, v, c, o.p, giro);
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

	/**
	 * El bancal alrededor de la obra, a d columnas de ella: el suelo queda entre y0 - (d - 1) y y0 + (d - 1). Más bajo
	 * se rellena de tierra con hierba (arena en el desierto); más alto se rebaja (solo el terreno: ni troncos, ni
	 * hojas, ni lo que hayan puesto otras piezas).
	 */
	private void talud(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int d, Paleta p) {
		if (d == Integer.MAX_VALUE || d < 1) {
			return;
		}
		int bajo = y0 - (d - 1);
		int alto = y0 + (d - 1);
		// el suelo de verdad: el primer bloque de terreno bajando desde bien arriba
		int suelo = Integer.MIN_VALUE;
		for (int y = alto + 24; y >= bajo - 24; y--) {
			pos.set(x, y, z);
			if (terreno(level.getBlockState(pos))) {
				suelo = y;
				break;
			}
		}
		if (suelo == Integer.MIN_VALUE) {
			return;
		}
		BlockState cesped = (p.desierto ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState();
		BlockState relleno = (p.desierto ? Blocks.SANDSTONE : Blocks.DIRT).defaultBlockState();
		if (suelo < bajo) {
			for (int y = suelo + 1; y <= bajo; y++) {
				pos.set(x, y, z);
				BlockState s = level.getBlockState(pos);
				if (s.isAir() || s.canBeReplaced()) {
					level.setBlock(pos, y == bajo ? cesped : relleno, 2);
				}
			}
			if (suelo + 1 <= bajo - 1) {
				pos.set(x, suelo, z);
				if (level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
					level.setBlock(pos, relleno, 2);
				}
			}
		} else if (suelo > alto) {
			for (int y = suelo + 1; y > alto; y--) {
				pos.set(x, y, z);
				BlockState s = level.getBlockState(pos);
				if (terreno(s) || !s.isAir() && s.canBeReplaced() && s.getFluidState().isEmpty()) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				}
			}
			pos.set(x, alto, z);
			if (terreno(level.getBlockState(pos))) {
				level.setBlock(pos, cesped, 2);
			}
		}
	}

	/** Si el bloque es terreno natural (tierra, piedra, arena, grava, terracota, nieve), no un tronco ni una obra. */
	private static boolean terreno(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND) || s.is(BlockTags.TERRACOTTA)
				|| s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
				|| s.is(Blocks.CALCITE) || s.is(Blocks.TUFF) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.COARSE_DIRT);
	}

	private void columna(WorldGenLevel level, RandomSource azar, BlockPos.MutableBlockPos pos, int x, int z, int u, int v, Obra.Columna c, Paleta p,
			Rotation giro) {
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
			botin(level, azar, pos.immutable(), b, u, ly, v);
		}
	}

	/**
	 * El botín de los cofres y barriles de la obra, de las tablas de Minecraft (se llenan al abrirlos), según el cuarto
	 * en que están: se mira lo que hay alrededor (yunques y fraguas, fogones, camas, libros, rejas, dianas, calderos de
	 * alquimia...). Y de vez en cuando, en cualquier sitio, un cofre de tesoro.
	 */
	private void botin(WorldGenLevel level, RandomSource azar, BlockPos pos, BlockState b, int u, int ly, int v) {
		boolean cofre = b.is(Blocks.CHEST);
		if (!cofre && !b.is(Blocks.BARREL)) {
			return;
		}
		int h = Math.floorMod(Geo.hash(pos.getX(), pos.getZ(), pos.getY() * 31 + 6029), 1000);
		ResourceKey<LootTable> tabla;
		if (cofre && h < RAROS_POR_MIL) {
			tabla = TESOROS.get(h % TESOROS.size());
		} else {
			tabla = deCuarto(cuarto(obra(), u, ly, v), cofre, h);
		}
		RandomizableContainer.setBlockEntityLootTable(level, azar, pos, tabla);
	}

	/** Cofres de tesoro: uno de cada tantos (por mil), en cualquier cuarto. */
	private static final int RAROS_POR_MIL = 90;
	private static final List<ResourceKey<LootTable>> TESOROS = List.of(BuiltInLootTables.WOODLAND_MANSION, BuiltInLootTables.BURIED_TREASURE,
			BuiltInLootTables.DESERT_PYRAMID, BuiltInLootTables.SHIPWRECK_TREASURE, BuiltInLootTables.JUNGLE_TEMPLE);

	private enum Cuarto { HERRERIA, COCINA, DORMITORIO, BIBLIOTECA, CALABOZO, ARQUEROS, CAPILLA, PESCA, ESTABLO, OTRO }

	/** Qué cuarto es, por lo que hay a menos de 4 bloques en el mismo piso. */
	private static Cuarto cuarto(Obra o, int u, int ly, int v) {
		int[] n = new int[Cuarto.values().length];
		for (int du = -4; du <= 4; du++) {
			for (int dv = -4; dv <= 4; dv++) {
				for (int dy = -1; dy <= 2; dy++) {
					BlockState s = o.en(u + du, ly + dy, v + dv);
					if (s == null || s.isAir()) {
						continue;
					}
					Cuarto c = s.is(Blocks.ANVIL) || s.is(Blocks.GRINDSTONE) || s.is(Blocks.SMITHING_TABLE) || s.is(Blocks.BLAST_FURNACE) ? Cuarto.HERRERIA
							: s.is(Blocks.SMOKER) || s.is(Blocks.FURNACE) || s.is(Blocks.WATER_CAULDRON) || s.is(Blocks.CAKE) ? Cuarto.COCINA
							: s.is(BlockTags.BEDS) ? Cuarto.DORMITORIO
							: s.is(Blocks.BOOKSHELF) || s.is(Blocks.LECTERN) || s.is(Blocks.CHISELED_BOOKSHELF) ? Cuarto.BIBLIOTECA
							: s.is(Blocks.IRON_BARS) || s.is(Blocks.IRON_DOOR) || s.is(Blocks.SKELETON_SKULL) ? Cuarto.CALABOZO
							: s.is(Blocks.TARGET) ? Cuarto.ARQUEROS
							: s.is(BlockTags.CANDLES) || s.is(Blocks.BREWING_STAND) ? Cuarto.CAPILLA
							: s.is(Blocks.PACKED_ICE) || s.is(Blocks.DRIED_KELP_BLOCK) ? Cuarto.PESCA
							: s.is(Blocks.HAY_BLOCK) ? Cuarto.ESTABLO
							: Cuarto.OTRO;
					n[c.ordinal()]++;
				}
			}
		}
		Cuarto mejor = Cuarto.OTRO;
		for (Cuarto c : Cuarto.values()) {
			if (c != Cuarto.OTRO && n[c.ordinal()] > 0 && (mejor == Cuarto.OTRO || n[c.ordinal()] > n[mejor.ordinal()])) {
				mejor = c;
			}
		}
		return mejor;
	}

	private static ResourceKey<LootTable> deCuarto(Cuarto c, boolean cofre, int h) {
		List<ResourceKey<LootTable>> t = switch (c) {
			case HERRERIA -> List.of(BuiltInLootTables.VILLAGE_WEAPONSMITH, BuiltInLootTables.VILLAGE_ARMORER, BuiltInLootTables.VILLAGE_TOOLSMITH);
			case COCINA -> cofre ? List.of(BuiltInLootTables.VILLAGE_PLAINS_HOUSE, BuiltInLootTables.VILLAGE_TAIGA_HOUSE)
					: List.of(BuiltInLootTables.VILLAGE_BUTCHER, BuiltInLootTables.VILLAGE_PLAINS_HOUSE);
			case DORMITORIO -> List.of(BuiltInLootTables.VILLAGE_PLAINS_HOUSE, BuiltInLootTables.VILLAGE_TAIGA_HOUSE,
					BuiltInLootTables.VILLAGE_SAVANNA_HOUSE, BuiltInLootTables.VILLAGE_SNOWY_HOUSE);
			// los libros buenos son raros: una de cada tres, la biblioteca de la fortaleza
			case BIBLIOTECA -> h % 3 == 0 ? List.of(BuiltInLootTables.STRONGHOLD_LIBRARY) : List.of(BuiltInLootTables.VILLAGE_CARTOGRAPHER);
			case CALABOZO -> List.of(BuiltInLootTables.SIMPLE_DUNGEON, BuiltInLootTables.PILLAGER_OUTPOST);
			case ARQUEROS -> List.of(BuiltInLootTables.VILLAGE_FLETCHER, BuiltInLootTables.PILLAGER_OUTPOST);
			case CAPILLA -> List.of(BuiltInLootTables.VILLAGE_TEMPLE);
			case PESCA -> List.of(BuiltInLootTables.VILLAGE_FISHER);
			case ESTABLO -> List.of(BuiltInLootTables.VILLAGE_TANNERY, BuiltInLootTables.VILLAGE_SHEPHERD);
			default -> cofre ? List.of(BuiltInLootTables.VILLAGE_PLAINS_HOUSE, BuiltInLootTables.STRONGHOLD_CORRIDOR, BuiltInLootTables.VILLAGE_MASON)
					: List.of(BuiltInLootTables.VILLAGE_PLAINS_HOUSE, BuiltInLootTables.VILLAGE_TAIGA_HOUSE);
		};
		return t.get(h % t.size());
	}
}
