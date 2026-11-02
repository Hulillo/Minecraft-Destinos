package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
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
