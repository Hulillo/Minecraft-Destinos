package es.hulillo.destinos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;

/**
 * Los seres que trae una obra (Obra.ser): caballos en las cuadras, el herrero, un gato, un preso y armaduras en sus
 * soportes. Se ponen al generar el chunk en el que caen, como los guardianes de los monumentos, y no desaparecen.
 */
final class SeresObra {
	static final String CABALLO = "caballo";
	static final String BURRO = "burro";
	static final String HERRERO = "herrero";
	static final String GATO = "gato";
	static final String PRESO = "preso";
	/** Armaduras en su soporte: de guardia (hierro con espada), de malla, de oro y de cuero (los peleles). */
	static final String GUARDIA = "guardia";
	static final String MALLA = "malla";
	static final String ORO = "oro";
	static final String PELELE = "pelele";

	private SeresObra() {
	}

	/** Pone el ser s en pos; rot es el giro de la obra (de 90 en 90 grados, en el sentido de las agujas del reloj). */
	static void pon(WorldGenLevel level, Obra.Ser s, BlockPos pos, int rot) {
		ServerLevel sl = level.getLevel();
		EntityType<?> tipo = switch (s.tipo()) {
			case CABALLO -> EntityType.HORSE;
			case BURRO -> EntityType.DONKEY;
			case HERRERO -> EntityType.VILLAGER;
			case GATO -> EntityType.CAT;
			case PRESO -> EntityType.ZOMBIE_VILLAGER;
			default -> EntityType.ARMOR_STAND;
		};
		Entity e = tipo.create(sl, EntitySpawnReason.STRUCTURE);
		if (e == null) {
			return;
		}
		float giro = yaw(s.mira()) + 90 * rot;
		e.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, giro, 0);
		if (e instanceof Mob m) {
			m.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
			m.setPersistenceRequired();
			m.setYHeadRot(giro);
			m.setYBodyRot(giro);
		}
		// el herrero coge solo el oficio de armero con el alto horno de la fragua, que tiene al lado
		if (e instanceof Mob m && PRESO.equals(s.tipo())) {
			m.setBaby(false);
		}
		if (e instanceof ArmorStand a) {
			viste(a, s.tipo());
		}
		level.addFreshEntityWithPassengers(e);
	}

	private static void viste(ArmorStand a, String tipo) {
		switch (tipo) {
			case MALLA -> {
				a.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
				a.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
				a.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
				a.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.CHAINMAIL_BOOTS));
			}
			case ORO -> {
				a.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
				a.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
				a.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
				a.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
			}
			case PELELE -> {
				a.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
				a.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
				a.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
			}
			default -> {
				a.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				a.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				a.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
				a.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
				a.setShowArms(true);
				a.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			}
		}
	}

	/** El giro (yaw) de Minecraft hacia d: 0 al sur, 90 al oeste, 180 al norte y 270 al este. */
	private static float yaw(Direction d) {
		return switch (d) {
			case SOUTH -> 0;
			case WEST -> 90;
			case NORTH -> 180;
			default -> 270;
		};
	}
}
