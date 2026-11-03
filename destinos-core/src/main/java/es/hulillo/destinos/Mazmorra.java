package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Una estructura del mundo (de vanilla o de mods) que puede despertarse: entonces, al entrar, salen oleadas de bichos
 * y al final un jefe; al matarlo aparece un cofre y queda limpia unos días. Se apuntan al cargar sus chunks.
 */
final class Mazmorra {
	/** De qué va la mazmorra (sale del nombre de la estructura): qué bichos salen, qué jefe y qué botín. */
	enum Tema {
		illager(l(EntityType.VINDICATOR, EntityType.PILLAGER), EntityType.VINDICATOR, BuiltInLootTables.WOODLAND_MANSION,
				"mansion", "illager", "outpost", "fort", "keep", "castle", "manor", "hideout", "bastion", "barrack", "citadel", "bandit",
					"raider", "pillager", "watchtower"),
		ahogados(l(EntityType.DROWNED), EntityType.DROWNED, BuiltInLootTables.UNDERWATER_RUIN_BIG,
				"ocean", "drown", "sunken", "underwater", "shipwreck", "reef", "lighthouse", "monument", "atlantis"),
		desierto(l(EntityType.HUSK, EntityType.SKELETON), EntityType.HUSK, BuiltInLootTables.DESERT_PYRAMID,
				"desert", "pyramid", "sand", "dune", "badlands", "mesa", "ziggurat", "oasis"),
		jungla(l(EntityType.SPIDER, EntityType.SKELETON, EntityType.CAVE_SPIDER), EntityType.SKELETON,
				BuiltInLootTables.JUNGLE_TEMPLE, "jungle", "temple"),
		hielo(l(EntityType.STRAY, EntityType.ZOMBIE), EntityType.STRAY, BuiltInLootTables.IGLOO_CHEST,
				"ice", "frost", "snow", "igloo", "frozen", "glacier"),
		pantano(l(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.WITCH), EntityType.WITCH, BuiltInLootTables.SIMPLE_DUNGEON,
				"swamp", "witch", "bog", "marsh", "mangrove"),
		sculk(l(EntityType.SKELETON, EntityType.STRAY), EntityType.STRAY, BuiltInLootTables.ANCIENT_CITY,
				"ancient", "sculk", "deep_dark", "echo"),
		mina(l(EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.ZOMBIE), EntityType.SPIDER,
				BuiltInLootTables.ABANDONED_MINESHAFT, "mineshaft", "mine", "quarry", "cave"),
		cripta(l(EntityType.ZOMBIE, EntityType.SKELETON), EntityType.WITHER_SKELETON, BuiltInLootTables.SIMPLE_DUNGEON,
				"crypt", "catacomb", "tomb", "grave", "necro", "undead", "ossuary", "mausoleum", "dungeon", "lair", "ruin",
				"tower", "stronghold", "labyrinth", "maze", "vault", "trial", "sanctum", "shrine", "barrow", "chapel", "abbey",
					"monastery", "ruins", "altar", "spire", "dungeon");

		final List<EntityType<? extends Mob>> bichos;
		final EntityType<? extends Mob> jefe;
		final ResourceKey<LootTable> botin;
		final String[] palabras;

		Tema(List<EntityType<? extends Mob>> bichos, EntityType<? extends Mob> jefe, ResourceKey<LootTable> botin, String... palabras) {
			this.bichos = bichos;
			this.jefe = jefe;
			this.botin = botin;
			this.palabras = palabras;
		}

		@SafeVarargs
		private static List<EntityType<? extends Mob>> l(EntityType<? extends Mob>... t) {
			return List.of(t);
		}

		/** El tema de una estructura por su nombre, o null si no parece una mazmorra. */
		static Tema de(String ruta) {
			for (Tema t : values()) {
				for (String p : t.palabras) {
					if (ruta.contains(p)) {
						return t;
					}
				}
			}
			return null;
		}
	}

	enum Estado {
		dormida, despierta, limpia
	}

	/** Chunk de inicio de la estructura (ChunkPos.toLong): la identifica. */
	final long clave;
	final String estructura;
	final Tema tema;
	final int minX, minY, minZ, maxX, maxY, maxZ;
	final int estrellas;
	Estado estado = Estado.dormida;
	/** Hasta qué hora del mundo sigue despierta (o limpia, antes de poder volver a despertarse). */
	long hasta;
	/** Si se ha despertado sola de noche (sale con calavera en el mapa a los jugadores de cerca). */
	boolean nocturna;
	/** 0 = sin empezar, 1..OLEADAS = oleada en curso, OLEADAS + 1 = el jefe. */
	int oleada;
	/** Bichos vivos de la oleada (o el jefe) y cuántos salieron, para la barra. */
	final List<UUID> vivos = new ArrayList<>();
	int salieron;

	Mazmorra(long clave, String estructura, Tema tema, BoundingBox caja, int estrellas) {
		this(clave, estructura, tema, caja.minX(), caja.minY(), caja.minZ(), caja.maxX(), caja.maxY(), caja.maxZ(), estrellas);
	}

	private Mazmorra(long clave, String estructura, Tema tema, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int estrellas) {
		this.clave = clave;
		this.estructura = estructura;
		this.tema = tema;
		this.minX = minX;
		this.minY = minY;
		this.minZ = minZ;
		this.maxX = maxX;
		this.maxY = maxY;
		this.maxZ = maxZ;
		this.estrellas = estrellas;
	}

	int x() {
		return (minX + maxX) / 2;
	}

	int z() {
		return (minZ + maxZ) / 2;
	}

	/** Si p está dentro de la caja de la estructura ampliada en m bloques. */
	boolean dentro(BlockPos p, int m) {
		return p.getX() >= minX - m && p.getX() <= maxX + m && p.getY() >= minY - m && p.getY() <= maxY + m
				&& p.getZ() >= minZ - m && p.getZ() <= maxZ + m;
	}

	boolean dentro(double x, double y, double z, int m) {
		return x >= minX - m && x < maxX + 1 + m && y >= minY - m && y < maxY + 1 + m && z >= minZ - m && z < maxZ + 1 + m;
	}

	CompoundTag guarda() {
		CompoundTag t = new CompoundTag();
		t.putLong("clave", clave);
		t.putString("estructura", estructura);
		t.putString("tema", tema.name());
		t.putIntArray("caja", new int[] {minX, minY, minZ, maxX, maxY, maxZ});
		t.putInt("estrellas", estrellas);
		t.putString("estado", estado.name());
		t.putLong("hasta", hasta);
		t.putBoolean("nocturna", nocturna);
		t.putInt("oleada", oleada);
		t.putInt("salieron", salieron);
		ListTag l = new ListTag();
		for (UUID u : vivos) {
			l.add(StringTag.valueOf(u.toString()));
		}
		t.put("vivos", l);
		return t;
	}

	/** Null si no se entiende (de otra versión). */
	static Mazmorra carga(CompoundTag t) {
		int[] c = t.getIntArray("caja").orElse(new int[0]);
		if (c.length != 6) {
			return null;
		}
		Tema tema;
		Estado estado;
		try {
			tema = Tema.valueOf(t.getStringOr("tema", ""));
			estado = Estado.valueOf(t.getStringOr("estado", "dormida"));
		} catch (IllegalArgumentException e) {
			return null;
		}
		Mazmorra m = new Mazmorra(t.getLongOr("clave", 0), t.getStringOr("estructura", "?"), tema, c[0], c[1], c[2], c[3], c[4], c[5],
				t.getIntOr("estrellas", 1));
		m.estado = estado;
		m.hasta = t.getLongOr("hasta", 0);
		m.nocturna = t.getBooleanOr("nocturna", false);
		m.oleada = t.getIntOr("oleada", 0);
		m.salieron = t.getIntOr("salieron", 0);
		ListTag l = t.getListOrEmpty("vivos");
		for (int i = 0; i < l.size(); i++) {
			try {
				m.vivos.add(UUID.fromString(l.getStringOr(i, "")));
			} catch (IllegalArgumentException e) {
				// se salta
			}
		}
		return m;
	}
}
