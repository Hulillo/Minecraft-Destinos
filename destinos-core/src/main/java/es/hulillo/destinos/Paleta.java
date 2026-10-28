package es.hulillo.destinos;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Los materiales de cada cultura (AdornoCapital.TEMPLADO…) para los edificios hechos a mano (Obra). La piedra la
 * pone la cultura; la madera del entramado y del tejado, la madera de la capital (MADERAS), que sale del bioma.
 */
final class Paleta {
	/** Las maderas: madera del entramado y de la carpintería, del tejado y del remate (aleros, cumbrera, bordes). */
	private record Madera(String entramado, String tejado, String remate) {
	}

	private static final Madera[] MADERAS = {
			new Madera("dark_oak", "spruce", "dark_oak"),
			new Madera("spruce", "dark_oak", "spruce"),
			new Madera("dark_oak", "birch", "dark_oak"),
			new Madera("jungle", "mangrove", "dark_oak"),
			new Madera("dark_oak", "cherry", "dark_oak"),
			new Madera("mangrove", "dark_oak", "mangrove"),
			new Madera("acacia", "acacia", "dark_oak"),
			new Madera("dark_oak", "dark_oak", "spruce"),
			new Madera("spruce", "deepslate_tile", "spruce"),
			new Madera("oak", "spruce", "spruce")};
	static final int OSCURA = 0;
	static final int ABETO = 1;
	static final int ABEDUL = 2;
	static final int JUNGLA = 3;
	static final int CEREZO = 4;
	static final int MANGLE = 5;
	static final int ACACIA = 6;
	static final int BOSQUE_OSCURO = 7;
	static final int NIEVE = 8;
	static final int ROBLE = 9;

	final boolean desierto;
	/** Tierras frías: pinos en vez de robles. */
	final boolean pino;
	/** Sillares: la piedra de los muros, en varias piezas para que no quede lisa. */
	final Block[] piedras;
	final BlockState zocalo;
	final BlockState moldura;
	final Block piedraEscalon;
	final Block piedraLosa;
	final BlockState muroBajo;
	/** Ladrillo de chimeneas y hogares. */
	final BlockState ladrillo;
	final Block ladrilloEscalon;
	final Block tronco;
	final Block troncoPelado;
	final BlockState tablas;
	final Block maderaEscalon;
	final Block maderaLosa;
	final BlockState valla;
	final Block trampilla;
	final Block puerta;
	/** El relleno blanco del entramado. */
	final BlockState enlucido;
	final BlockState tejadoLleno;
	final Block tejadoEscalon;
	final Block tejadoLosa;
	/** El remate de los tejados: aleros, bordes, cumbrera y aristas, en otra madera para que destaquen. */
	final Block remateEscalon;
	final Block remateLosa;
	final Block remateTronco;
	final Block troncoArbol;
	final Block hojas;
	final Block[] flores;
	/** Suelo de piedra pulida de los salones. */
	final BlockState losa;
	/** El empedrado del patio, en varias piezas. */
	final Block[] adoquines;

	private Paleta(int estilo, int madera) {
		desierto = estilo == AdornoCapital.DESIERTO;
		pino = estilo == AdornoCapital.TAIGA || estilo == AdornoCapital.NIEVE;
		boolean sabana = estilo == AdornoCapital.SABANA;
		if (desierto) {
			piedras = new Block[] {Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE};
			zocalo = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
			moldura = Blocks.CHISELED_SANDSTONE.defaultBlockState();
			piedraEscalon = Blocks.SANDSTONE_STAIRS;
			piedraLosa = Blocks.SANDSTONE_SLAB;
			muroBajo = Blocks.SANDSTONE_WALL.defaultBlockState();
			ladrillo = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
			ladrilloEscalon = Blocks.SMOOTH_SANDSTONE_STAIRS;
		} else if (sabana) {
			piedras = new Block[] {Blocks.MUD_BRICKS, Blocks.MUD_BRICKS, Blocks.MUD_BRICKS, Blocks.PACKED_MUD};
			zocalo = Blocks.PACKED_MUD.defaultBlockState();
			moldura = Blocks.TERRACOTTA.defaultBlockState();
			piedraEscalon = Blocks.MUD_BRICK_STAIRS;
			piedraLosa = Blocks.MUD_BRICK_SLAB;
			muroBajo = Blocks.MUD_BRICK_WALL.defaultBlockState();
			ladrillo = Blocks.BRICKS.defaultBlockState();
			ladrilloEscalon = Blocks.BRICK_STAIRS;
		} else {
			piedras = pino
					? new Block[] {Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.MOSSY_STONE_BRICKS, Blocks.ANDESITE}
					: new Block[] {Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
							Blocks.MOSSY_STONE_BRICKS, Blocks.COBBLESTONE};
			zocalo = Blocks.COBBLESTONE.defaultBlockState();
			moldura = (estilo == AdornoCapital.NIEVE ? Blocks.POLISHED_DIORITE : Blocks.POLISHED_ANDESITE).defaultBlockState();
			piedraEscalon = Blocks.STONE_BRICK_STAIRS;
			piedraLosa = Blocks.STONE_BRICK_SLAB;
			muroBajo = Blocks.COBBLESTONE_WALL.defaultBlockState();
			ladrillo = Blocks.STONE_BRICKS.defaultBlockState();
			ladrilloEscalon = Blocks.STONE_BRICK_STAIRS;
		}
		Madera m = MADERAS[madera];
		String e = m.entramado();
		tronco = bloque(e + "_log");
		troncoPelado = bloque("stripped_" + e + "_log");
		tablas = bloque(e + "_planks").defaultBlockState();
		maderaEscalon = bloque(e + "_stairs");
		maderaLosa = bloque(e + "_slab");
		valla = bloque(e + "_fence").defaultBlockState();
		trampilla = bloque(e + "_trapdoor");
		puerta = bloque(e + "_door");
		enlucido = (desierto ? Blocks.SMOOTH_SANDSTONE : estilo == AdornoCapital.NIEVE ? Blocks.CALCITE : Blocks.WHITE_TERRACOTTA)
				.defaultBlockState();
		String t = desierto ? "smooth_sandstone" : m.tejado();
		tejadoLleno = bloque(t.equals("deepslate_tile") ? "deepslate_tiles" : t.equals("smooth_sandstone") ? t : t + "_planks")
				.defaultBlockState();
		tejadoEscalon = bloque(t + "_stairs");
		tejadoLosa = bloque(t + "_slab");
		String r = desierto ? "smooth_sandstone" : m.remate();
		remateEscalon = bloque(r + "_stairs");
		remateLosa = bloque(r + "_slab");
		remateTronco = desierto ? Blocks.CUT_SANDSTONE : bloque(r + "_log");
		losa = (desierto || sabana ? Blocks.SMOOTH_SANDSTONE : Blocks.SMOOTH_STONE).defaultBlockState();
		// los árboles del patio, los del sitio
		switch (madera) {
			case ABEDUL -> {
				troncoArbol = Blocks.BIRCH_LOG;
				hojas = Blocks.BIRCH_LEAVES;
			}
			case JUNGLA -> {
				troncoArbol = Blocks.JUNGLE_LOG;
				hojas = Blocks.JUNGLE_LEAVES;
			}
			case CEREZO -> {
				troncoArbol = Blocks.CHERRY_LOG;
				hojas = Blocks.CHERRY_LEAVES;
			}
			case BOSQUE_OSCURO -> {
				troncoArbol = Blocks.DARK_OAK_LOG;
				hojas = Blocks.DARK_OAK_LEAVES;
			}
			default -> {
				troncoArbol = desierto ? Blocks.JUNGLE_LOG : pino ? Blocks.SPRUCE_LOG : sabana ? Blocks.ACACIA_LOG : Blocks.OAK_LOG;
				hojas = desierto ? Blocks.JUNGLE_LEAVES : pino ? Blocks.SPRUCE_LEAVES : sabana ? Blocks.ACACIA_LEAVES : Blocks.OAK_LEAVES;
			}
		}
		flores = desierto ? new Block[] {Blocks.DEAD_BUSH, Blocks.ORANGE_TULIP, Blocks.RED_TULIP}
				: estilo == AdornoCapital.NIEVE ? new Block[] {Blocks.LILY_OF_THE_VALLEY, Blocks.CORNFLOWER, Blocks.WHITE_TULIP, Blocks.FERN}
				: new Block[] {Blocks.POPPY, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.PINK_TULIP,
						Blocks.RED_TULIP, Blocks.LILY_OF_THE_VALLEY, Blocks.SHORT_GRASS};
		adoquines = desierto ? new Block[] {Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE}
				: sabana ? new Block[] {Blocks.PACKED_MUD, Blocks.PACKED_MUD, Blocks.COARSE_DIRT, Blocks.MUD_BRICKS, Blocks.DIRT_PATH}
				: new Block[] {Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.STONE, Blocks.ANDESITE, Blocks.STONE_BRICKS, Blocks.GRAVEL,
						Blocks.DIRT_PATH, Blocks.MOSSY_COBBLESTONE};
	}

	private static Block bloque(String nombre) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(nombre));
	}

	/**
	 * La madera de una capital: la del bioma si tiene una propia (abedules, jungla, cerezos, manglares, bosque
	 * oscuro) y si no la de la cultura; en las templadas, roble oscuro o roble según el sitio.
	 */
	static int madera(int estilo, String bioma, int azar) {
		if (estilo == AdornoCapital.DESIERTO || estilo == AdornoCapital.SABANA) {
			return ACACIA;
		}
		if (estilo == AdornoCapital.NIEVE) {
			return NIEVE;
		}
		if (bioma.contains("cherry")) {
			return CEREZO;
		}
		if (bioma.contains("jungle") || bioma.contains("bamboo")) {
			return JUNGLA;
		}
		if (bioma.contains("mangrove") || bioma.contains("swamp")) {
			return MANGLE;
		}
		if (bioma.contains("dark_forest")) {
			return BOSQUE_OSCURO;
		}
		if (bioma.contains("birch")) {
			return ABEDUL;
		}
		if (estilo == AdornoCapital.TAIGA) {
			return ABETO;
		}
		return Math.floorMod(azar, 2) == 0 ? OSCURA : ROBLE;
	}

	/** La de antes de que hubiera maderas: la que tocaba a cada cultura. */
	static int maderaDe(int estilo) {
		return switch (estilo) {
			case AdornoCapital.TAIGA -> ABETO;
			case AdornoCapital.NIEVE -> NIEVE;
			case AdornoCapital.SABANA, AdornoCapital.DESIERTO -> ACACIA;
			default -> OSCURA;
		};
	}

	private static final Paleta[][] TODAS = new Paleta[5][MADERAS.length];

	/** La paleta de esa cultura con esa madera (o la de la cultura si madera < 0). */
	static synchronized Paleta de(int estilo, int madera) {
		int e = Math.floorMod(estilo, TODAS.length);
		int m = madera < 0 || madera >= MADERAS.length ? maderaDe(e) : madera;
		if (TODAS[e][m] == null) {
			TODAS[e][m] = new Paleta(e, m);
		}
		return TODAS[e][m];
	}

	/** El sillar de ese sitio (varía, pero siempre el mismo en el mismo sitio). */
	BlockState piedra(Obra o, int u, int ly, int v) {
		return piedras[o.azar(u, ly, v, piedras.length)].defaultBlockState();
	}

	BlockState adoquin(Obra o, int u, int v) {
		return adoquines[o.azar(u, 0, v, adoquines.length)].defaultBlockState();
	}
}
