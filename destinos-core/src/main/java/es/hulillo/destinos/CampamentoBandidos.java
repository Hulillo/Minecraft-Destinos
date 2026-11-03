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
		BlockPos c = seco(level, x, z);
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
		DyeColor tinte = DyeColor.byId(AdornoCapital.tinte(rgb));
		BlockState lana = bloque(tinte, "wool", Blocks.RED_WOOL).defaultBlockState();
		BlockPos c = seco(level, x, z);
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

	/** El primer hueco encima del suelo (sin contar hojas). */
	private static BlockPos suelo(ServerLevel level, int x, int z) {
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
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
			BlockPos p = suelo(level, px, pz);
			if (level.getBlockState(p.below()).getFluidState().isEmpty() && libre(level, p)) {
				return p;
			}
		}
		return suelo(level, x, z);
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
