package es.hulillo.destinos;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Los materiales de cada cultura (AdornoCapital.TEMPLADO…) para los edificios hechos a mano (Obra). */
final class Paleta {
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
	final Block troncoArbol;
	final Block hojas;
	final Block[] flores;
	/** Suelo de piedra pulida de los salones. */
	final BlockState losa;
	/** El empedrado del patio, en varias piezas. */
	final Block[] adoquines;

	private Paleta(int estilo) {
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
		if (pino) {
			tronco = Blocks.SPRUCE_LOG;
			troncoPelado = Blocks.STRIPPED_SPRUCE_LOG;
			tablas = Blocks.SPRUCE_PLANKS.defaultBlockState();
			maderaEscalon = Blocks.SPRUCE_STAIRS;
			maderaLosa = Blocks.SPRUCE_SLAB;
			valla = Blocks.SPRUCE_FENCE.defaultBlockState();
			trampilla = Blocks.SPRUCE_TRAPDOOR;
			puerta = Blocks.SPRUCE_DOOR;
		} else if (sabana || desierto) {
			tronco = Blocks.ACACIA_LOG;
			troncoPelado = Blocks.STRIPPED_ACACIA_LOG;
			tablas = Blocks.ACACIA_PLANKS.defaultBlockState();
			maderaEscalon = Blocks.ACACIA_STAIRS;
			maderaLosa = Blocks.ACACIA_SLAB;
			valla = Blocks.ACACIA_FENCE.defaultBlockState();
			trampilla = Blocks.ACACIA_TRAPDOOR;
			puerta = Blocks.ACACIA_DOOR;
		} else {
			tronco = Blocks.DARK_OAK_LOG;
			troncoPelado = Blocks.STRIPPED_DARK_OAK_LOG;
			tablas = Blocks.DARK_OAK_PLANKS.defaultBlockState();
			maderaEscalon = Blocks.DARK_OAK_STAIRS;
			maderaLosa = Blocks.DARK_OAK_SLAB;
			valla = Blocks.DARK_OAK_FENCE.defaultBlockState();
			trampilla = Blocks.DARK_OAK_TRAPDOOR;
			puerta = Blocks.DARK_OAK_DOOR;
		}
		enlucido = (desierto ? Blocks.SMOOTH_SANDSTONE : sabana ? Blocks.WHITE_TERRACOTTA
				: estilo == AdornoCapital.NIEVE ? Blocks.CALCITE : Blocks.WHITE_TERRACOTTA).defaultBlockState();
		switch (estilo) {
			case AdornoCapital.DESIERTO -> {
				tejadoLleno = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
				tejadoEscalon = Blocks.SMOOTH_SANDSTONE_STAIRS;
			}
			case AdornoCapital.SABANA -> {
				tejadoLleno = Blocks.ACACIA_PLANKS.defaultBlockState();
				tejadoEscalon = Blocks.ACACIA_STAIRS;
			}
			case AdornoCapital.NIEVE -> {
				tejadoLleno = Blocks.DEEPSLATE_TILES.defaultBlockState();
				tejadoEscalon = Blocks.DEEPSLATE_TILE_STAIRS;
			}
			case AdornoCapital.TAIGA -> {
				tejadoLleno = Blocks.DARK_OAK_PLANKS.defaultBlockState();
				tejadoEscalon = Blocks.DARK_OAK_STAIRS;
			}
			default -> {
				tejadoLleno = Blocks.SPRUCE_PLANKS.defaultBlockState();
				tejadoEscalon = Blocks.SPRUCE_STAIRS;
			}
		}
		losa = (desierto || sabana ? Blocks.SMOOTH_SANDSTONE : Blocks.SMOOTH_STONE).defaultBlockState();
		troncoArbol = desierto ? Blocks.JUNGLE_LOG : pino ? Blocks.SPRUCE_LOG : sabana ? Blocks.ACACIA_LOG : Blocks.OAK_LOG;
		hojas = desierto ? Blocks.JUNGLE_LEAVES : pino ? Blocks.SPRUCE_LEAVES : sabana ? Blocks.ACACIA_LEAVES : Blocks.OAK_LEAVES;
		flores = desierto ? new Block[] {Blocks.DEAD_BUSH, Blocks.ORANGE_TULIP, Blocks.RED_TULIP}
				: estilo == AdornoCapital.NIEVE ? new Block[] {Blocks.LILY_OF_THE_VALLEY, Blocks.CORNFLOWER, Blocks.WHITE_TULIP, Blocks.FERN}
				: new Block[] {Blocks.POPPY, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.PINK_TULIP,
						Blocks.RED_TULIP, Blocks.LILY_OF_THE_VALLEY, Blocks.SHORT_GRASS};
		adoquines = desierto ? new Block[] {Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE}
				: sabana ? new Block[] {Blocks.PACKED_MUD, Blocks.PACKED_MUD, Blocks.COARSE_DIRT, Blocks.MUD_BRICKS, Blocks.DIRT_PATH}
				: new Block[] {Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.STONE, Blocks.ANDESITE, Blocks.STONE_BRICKS, Blocks.GRAVEL,
						Blocks.DIRT_PATH, Blocks.MOSSY_COBBLESTONE};
	}

	private static final Paleta[] TODAS = new Paleta[5];

	static synchronized Paleta de(int estilo) {
		int e = Math.floorMod(estilo, TODAS.length);
		if (TODAS[e] == null) {
			TODAS[e] = new Paleta(e);
		}
		return TODAS[e];
	}

	/** El sillar de ese sitio (varía, pero siempre el mismo en el mismo sitio). */
	BlockState piedra(Obra o, int u, int ly, int v) {
		return piedras[o.azar(u, ly, v, piedras.length)].defaultBlockState();
	}

	BlockState adoquin(Obra o, int u, int v) {
		return adoquines[o.azar(u, 0, v, adoquines.length)].defaultBlockState();
	}
}
