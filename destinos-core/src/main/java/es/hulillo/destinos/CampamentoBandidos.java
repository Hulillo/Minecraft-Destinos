package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * El campamento de un encargo de caza: una hoguera, dos tiendas de lana (una con el cofre del botín) y unos cuantos
 * bandidos (saqueadores y vindicadores con nombre). Se monta cuando el jugador se acerca, en el primer trozo de tierra
 * seca cerca del sitio del encargo, y los bandidos no desaparecen solos.
 */
final class CampamentoBandidos {
	/** Dónde ha quedado el campamento y quiénes son sus bandidos. */
	record Montado(int x, int z, List<UUID> bandidos) {
	}

	private CampamentoBandidos() {
	}

	static Montado monta(ServerLevel level, int x, int z, int estrellas, int id) {
		BlockPos c = allana(level, seco(level, x, z));
		try {
			return montaEn(level, c, estrellas);
		} finally {
			llano = null;
		}
	}

	private static Montado montaEn(ServerLevel level, BlockPos c, int estrellas) {
		level.setBlock(c, Blocks.CAMPFIRE.defaultBlockState(), 3);
		BlockPos tienda1 = suelo(level, c.getX() + 4, c.getZ() - 1);
		BlockPos tienda2 = suelo(level, c.getX() - 4, c.getZ() + 1);
		tienda(level, tienda1, Blocks.BROWN_WOOL.defaultBlockState());
		tienda(level, tienda2, Blocks.WHITE_WOOL.defaultBlockState());
		if (libre(level, tienda1)) {
			level.setBlock(tienda1, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
			RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), tienda1, BuiltInLootTables.PILLAGER_OUTPOST);
		}
		pon(level, suelo(level, c.getX() + 1, c.getZ() + 2), Blocks.HAY_BLOCK.defaultBlockState());
		pon(level, suelo(level, c.getX() - 2, c.getZ() - 2), Blocks.BARREL.defaultBlockState());
		int n = 2 + estrellas;
		List<UUID> bandidos = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			BlockPos p = suelo(level, c.getX() + (int) Math.round(Math.cos(a) * 3), c.getZ() + (int) Math.round(Math.sin(a) * 3));
			Entity e = bandido(level, p, i, estrellas, i == 0 && estrellas >= 3);
			if (e != null) {
				bandidos.add(e.getUUID());
			}
		}
		return new Montado(c.getX(), c.getZ(), bandidos);
	}

	/**
	 * El campamento de un encargo de guerra: el del ejército enemigo en el frente. Tiendas de lana del color de su
	 * reino, un estandarte junto a la hoguera, armas apoyadas, soldados con armadura de cuero teñida de su color y un
	 * capitán con el estandarte en la cabeza (como los de las incursiones).
	 */
	static Montado montaGuerra(ServerLevel level, int x, int z, int estrellas, int rgb, Component reino) {
		BlockPos c = allana(level, seco(level, x, z));
		try {
			return montaGuerraEn(level, c, estrellas, rgb, reino);
		} finally {
			llano = null;
		}
	}

	private static Montado montaGuerraEn(ServerLevel level, BlockPos c, int estrellas, int rgb, Component reino) {
		DyeColor tinte = DyeColor.byId(AdornoCapital.tinte(rgb));
		BlockState lana = bloque(tinte, "wool", Blocks.RED_WOOL).defaultBlockState();
		level.setBlock(c, Blocks.CAMPFIRE.defaultBlockState(), 3);
		BlockPos capitania = suelo(level, c.getX() + 5, c.getZ());
		tienda(level, capitania, lana);
		tienda(level, suelo(level, c.getX() - 5, c.getZ() + 1), lana);
		tienda(level, suelo(level, c.getX() + 1, c.getZ() - 6), Blocks.WHITE_WOOL.defaultBlockState());
		if (libre(level, capitania)) {
			level.setBlock(capitania, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
			RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), capitania, BuiltInLootTables.PILLAGER_OUTPOST);
		}
		// el estandarte del reino junto a la hoguera y otro a la entrada de la tienda del capitán
		pon(level, suelo(level, c.getX() + 2, c.getZ() + 2), bloque(tinte, "banner", Blocks.RED_BANNER).defaultBlockState()
				.setValue(BlockStateProperties.ROTATION_16, 4));
		pon(level, suelo(level, c.getX() + 3, c.getZ() - 2), bloque(tinte, "banner", Blocks.RED_BANNER).defaultBlockState()
				.setValue(BlockStateProperties.ROTATION_16, 4));
		pon(level, suelo(level, c.getX() - 2, c.getZ() - 3), Blocks.FLETCHING_TABLE.defaultBlockState());
		pon(level, suelo(level, c.getX() - 3, c.getZ() - 3), Blocks.GRINDSTONE.defaultBlockState()
				.setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
		pon(level, suelo(level, c.getX() - 2, c.getZ() + 3), Blocks.BARREL.defaultBlockState());
		pon(level, suelo(level, c.getX() - 1, c.getZ() + 3), Blocks.HAY_BLOCK.defaultBlockState());
		int n = 3 + estrellas;
		List<UUID> soldados = new ArrayList<>();
		Entity capitan = soldado(level, suelo(level, c.getX() + 3, c.getZ()), 0, estrellas, true, rgb, tinte, reino);
		if (capitan != null) {
			soldados.add(capitan.getUUID());
		}
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			BlockPos p = suelo(level, c.getX() + (int) Math.round(Math.cos(a) * 3), c.getZ() + (int) Math.round(Math.sin(a) * 3));
			Entity e = soldado(level, p, i, estrellas, false, rgb, tinte, reino);
			if (e != null) {
				soldados.add(e.getUUID());
			}
		}
		return new Montado(c.getX(), c.getZ(), soldados);
	}

	private static Entity soldado(ServerLevel level, BlockPos p, int i, int estrellas, boolean capitan, int rgb, DyeColor tinte,
			Component reino) {
		Mob m = (capitan || i % 2 == 0 ? EntityType.VINDICATOR : EntityType.PILLAGER).create(level, EntitySpawnReason.EVENT);
		if (m == null) {
			return null;
		}
		m.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
		m.finalizeSpawn(level, level.getCurrentDifficultyAt(p), EntitySpawnReason.EVENT, null);
		m.setPersistenceRequired();
		m.addTag(EncargosMundo.BANDIDO);
		m.setCustomName(capitan ? Component.translatableWithFallback("destinos.encargo.capitan", "Capitán del %s", reino)
				: Component.translatableWithFallback("destinos.encargo.soldado", "Soldado del %s", reino));
		if (capitan) {
			// el capitán, con el estandarte en la cabeza, peto de hierro y más vida
			Item estandarte = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(tinte.getName() + "_banner"));
			m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(estandarte));
			m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(estrellas >= 4 ? Items.DIAMOND_CHESTPLATE : Items.IRON_CHESTPLATE));
			m.setItemSlot(EquipmentSlot.LEGS, cuero(Items.LEATHER_LEGGINGS, rgb));
			m.setItemSlot(EquipmentSlot.FEET, cuero(Items.LEATHER_BOOTS, rgb));
			m.setDropChance(EquipmentSlot.HEAD, 1);
			var vida = m.getAttribute(Attributes.MAX_HEALTH);
			if (vida != null) {
				vida.setBaseValue(30 + 8 * estrellas);
				m.setHealth(m.getMaxHealth());
			}
		} else {
			m.setItemSlot(EquipmentSlot.HEAD, estrellas >= 4 ? new ItemStack(Items.IRON_HELMET) : cuero(Items.LEATHER_HELMET, rgb));
			m.setItemSlot(EquipmentSlot.CHEST, estrellas >= 5 ? new ItemStack(Items.CHAINMAIL_CHESTPLATE) : cuero(Items.LEATHER_CHESTPLATE, rgb));
			m.setItemSlot(EquipmentSlot.LEGS, cuero(Items.LEATHER_LEGGINGS, rgb));
			m.setItemSlot(EquipmentSlot.FEET, cuero(Items.LEATHER_BOOTS, rgb));
		}
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			m.setDropChance(slot, 0.08f);
		}
		level.addFreshEntityWithPassengers(m);
		return m;
	}

	/** Una pieza de cuero teñida del color del reino. */
	private static ItemStack cuero(Item pieza, int rgb) {
		ItemStack s = new ItemStack(pieza);
		s.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		return s;
	}

	private static Block bloque(DyeColor tinte, String tipo, Block sinColor) {
		Block b = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(tinte.getName() + "_" + tipo));
		return b == Blocks.AIR ? sinColor : b;
	}

	private static Entity bandido(ServerLevel level, BlockPos p, int i, int estrellas, boolean jefe) {
		Mob m = (i % 2 == 0 ? EntityType.VINDICATOR : EntityType.PILLAGER).create(level, EntitySpawnReason.EVENT);
		if (m == null) {
			return null;
		}
		m.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
		m.finalizeSpawn(level, level.getCurrentDifficultyAt(p), EntitySpawnReason.EVENT, null);
		m.setPersistenceRequired();
		m.addTag(EncargosMundo.BANDIDO);
		m.setCustomName(jefe ? Component.translatableWithFallback("destinos.encargo.jefe", "Jefe de los bandidos")
				: Component.translatableWithFallback("destinos.encargo.bandido", "Bandido"));
		if (estrellas >= 3) {
			m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(jefe ? Items.IRON_HELMET : Items.LEATHER_HELMET));
		}
		if (estrellas >= 4 || jefe) {
			m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(jefe ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
		}
		level.addFreshEntityWithPassengers(m);
		return m;
	}

	/** Una tienda a dos aguas a lo largo de z: paredes de lana a los lados y la cumbrera arriba. */
	private static void tienda(ServerLevel level, BlockPos p, BlockState lana) {
		for (int dz = -1; dz <= 1; dz++) {
			pon(level, p.offset(-1, 0, dz), lana);
			pon(level, p.offset(1, 0, dz), lana);
			pon(level, p.offset(0, 1, dz), lana);
			BlockPos dentro = p.offset(0, 0, dz);
			if (!level.getBlockState(dentro).isAir() && level.getBlockState(dentro).canBeReplaced()) {
				level.setBlock(dentro, Blocks.AIR.defaultBlockState(), 3);
			}
		}
	}

	private static void pon(ServerLevel level, BlockPos p, BlockState b) {
		if (libre(level, p)) {
			level.setBlock(p, b, 3);
		}
	}

	private static boolean libre(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return s.canBeReplaced() && s.getFluidState().isEmpty();
	}

	/** El llano del campamento que se está montando (su centro a la altura del suelo), o null. */
	private static BlockPos llano;
	/** Radio del llano del campamento y anchura del talud que lo une al terreno de alrededor. */
	private static final int LLANO = 8;
	private static final int TALUD = 4;
	private static final int MAX_RELLENO = 8;

	/** El primer hueco encima del suelo: en el llano del campamento, su altura; fuera, el suelo de verdad. */
	private static BlockPos suelo(ServerLevel level, int x, int z) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return new BlockPos(x, llano != null ? llano.getY() : level.getSeaLevel(), z);
		}
		if (llano != null && (long) (x - llano.getX()) * (x - llano.getX()) + (long) (z - llano.getZ()) * (z - llano.getZ()) <= (long) LLANO * LLANO) {
			return new BlockPos(x, llano.getY(), z);
		}
		return new BlockPos(x, tierra(level, x, z), z);
	}

	/** La altura del primer hueco sobre la tierra o la roca de (x, z), sin contar árboles, hojas ni plantas. */
	private static int tierra(ServerLevel level, int x, int z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
		while (p.getY() > level.getMinY()) {
			BlockState s = level.getBlockState(p);
			if (!s.isAir() && !encima(s)) {
				break;
			}
			p.move(0, -1, 0);
		}
		return p.getY() + 1;
	}

	/** Lo que hay encima del suelo y no es suelo: árboles, hojas, plantas, nieve, setas gigantes. */
	private static boolean encima(BlockState s) {
		return s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.canBeReplaced() && s.getFluidState().isEmpty()
				|| s.is(Blocks.VINE) || s.is(Blocks.BAMBOO) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.MOSS_CARPET) || s.is(Blocks.SNOW)
				|| s.is(Blocks.BEE_NEST) || s.is(Blocks.COCOA) || s.is(Blocks.MANGROVE_ROOTS) || s.is(Blocks.BROWN_MUSHROOM_BLOCK)
				|| s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.CACTUS) || s.is(Blocks.SUGAR_CANE)
				|| SueloNatural.deTerreno(s);
	}

	/**
	 * Allana un redondel alrededor de c a la altura más repetida del suelo de verdad: tala lo que haya encima (árboles
	 * incluidos), rebaja lo que sobresale y rellena los huecos, y lo une al terreno de alrededor con un talud. Devuelve
	 * el centro a la altura del llano, que queda como el suelo del campamento.
	 */
	private static BlockPos allana(ServerLevel level, BlockPos c) {
		List<Integer> alturas = new ArrayList<>();
		for (int dx = -LLANO; dx <= LLANO; dx += 2) {
			for (int dz = -LLANO; dz <= LLANO; dz += 2) {
				if (dx * dx + dz * dz <= LLANO * LLANO && level.hasChunk((c.getX() + dx) >> 4, (c.getZ() + dz) >> 4)) {
					alturas.add(tierra(level, c.getX() + dx, c.getZ() + dz));
				}
			}
		}
		if (alturas.isEmpty()) {
			return c;
		}
		java.util.Collections.sort(alturas);
		int y = alturas.get(alturas.size() / 2);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int lejos = LLANO + TALUD;
		for (int dx = -lejos; dx <= lejos; dx++) {
			for (int dz = -lejos; dz <= lejos; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > lejos) {
					continue;
				}
				int x = c.getX() + dx;
				int z = c.getZ() + dz;
				if (!level.hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				int holgura = d <= LLANO ? 0 : (int) Math.ceil(d - LLANO);
				// por encima del llano (con el talud, un bloque más por cada bloque de distancia): fuera tierra y árboles
				int corte = y + holgura;
				for (int yy = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z); yy >= corte; yy--) {
					BlockState s = level.getBlockState(p.set(x, yy, z));
					if (!s.isAir() && s.getFluidState().isEmpty() && (encima(s) || SueloNatural.natural(s) || s.is(BlockTags.BASE_STONE_OVERWORLD)
							|| s.is(BlockTags.DIRT) || s.is(BlockTags.SAND))) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
					}
				}
				// por debajo: tierra hasta el llano (o hasta el talud), con hierba arriba
				int relleno = y - holgura;
				for (int yy = relleno - 1; yy >= relleno - MAX_RELLENO; yy--) {
					BlockState s = level.getBlockState(p.set(x, yy, z));
					if (!s.isAir() && !(encima(s) && s.getFluidState().isEmpty())) {
						break;
					}
					level.setBlock(p, (yy == relleno - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 2);
				}
			}
		}
		llano = new BlockPos(c.getX(), y, c.getZ());
		return llano;
	}

	/** Un sitio en tierra seca cerca de (x, z), en espiral; si no hay ninguno cargado, (x, z) tal cual. */
	private static BlockPos seco(ServerLevel level, int x, int z) {
		for (int i = 0; i < 20; i++) {
			double a = i * 2.4;
			int r = i * 2;
			int px = x + (int) (Math.cos(a) * r);
			int pz = z + (int) (Math.sin(a) * r);
			if (!level.isLoaded(new BlockPos(px, 64, pz))) {
				continue;
			}
			BlockPos p = new BlockPos(px, tierra(level, px, pz), pz);
			if (level.getBlockState(p.below()).getFluidState().isEmpty()) {
				return p;
			}
		}
		return new BlockPos(x, tierra(level, x, z), z);
	}

	/**
	 * Si con las entidades de alrededor del campamento ya cargadas no queda ninguno de sus bandidos (se los ha llevado
	 * otra cosa, o el mundo está en pacífico). Entonces el encargo se da por cumplido.
	 */
	static boolean desaparecidos(ServerLevel level, Encargo e) {
		int cx = e.x >> 4;
		int cz = e.z >> 4;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!level.areEntitiesLoaded(ChunkPos.asLong(cx + dx, cz + dz))) {
					return false;
				}
			}
		}
		for (UUID u : e.bandidos) {
			Entity b = level.getEntity(u);
			if (b != null && b.isAlive()) {
				return false;
			}
		}
		e.bandidos.clear();
		return true;
	}
}
