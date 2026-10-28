package es.hulillo.destinos;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Los puestos del mercado de la plaza mayor, hechos en local con Obra (el mostrador mira a -v, hacia el centro de la
 * plaza) para que AdornoCapital los ponga girados. Caben en u -2..2, v -2..1 y suben hasta ly 5.
 */
final class PuestoMercado {
	static final int TIPOS = 4;
	static final int ALTO = 6;

	private PuestoMercado() {
	}

	static Obra construye(int tipo, int estilo, int color, int semilla) {
		Obra o = new Obra(estilo, color, ALTO, semilla);
		switch (Math.floorMod(tipo, TIPOS)) {
			case 0 -> toldo(o);
			case 1 -> verduras(o);
			case 2 -> flores(o);
			default -> carro(o);
		}
		o.conecta();
		return o;
	}

	private static void postes(Obra o, int vDelante, int altoDelante, int vDetras, int altoDetras) {
		for (int u : new int[] {-2, 2}) {
			o.caja(u, 1, vDelante, u, altoDelante, vDelante, o.p.valla);
			o.caja(u, 1, vDetras, u, altoDetras, vDetras, o.p.valla);
		}
	}

	/** De telas: toldo a rayas del color del reino y blancas que cae por delante, mostrador y cajas detrás. */
	private static void toldo(Obra o) {
		Paleta p = o.p;
		Block raya = o.tinte("wool", Blocks.RED_WOOL);
		postes(o, -1, 3, 1, 3);
		for (int u = -2; u <= 2; u++) {
			BlockState tela = (Math.floorMod(u, 2) == 0 ? raya : Blocks.WHITE_WOOL).defaultBlockState();
			o.caja(u, 4, -1, u, 4, 1, tela);
			o.pon(u, 3, -2, tela);
		}
		// el mostrador: barriles a los lados y una tabla en medio con lo que se vende encima
		o.pon(-1, 1, -1, Blocks.BARREL);
		o.pon(0, 1, -1, Obra.losa(p.maderaLosa, true));
		o.pon(1, 1, -1, Blocks.BARREL);
		Block[][] generos = {
				{Blocks.CAKE, Blocks.FLOWER_POT, Blocks.DECORATED_POT},
				{Blocks.DECORATED_POT, Blocks.CANDLE, Blocks.DECORATED_POT},
				{Blocks.HONEY_BLOCK, Blocks.CAKE, Blocks.POTTED_RED_MUSHROOM}};
		Block[] g = generos[o.azar(0, 2, -1, generos.length)];
		for (int u = -1; u <= 1; u++) {
			o.pon(u, 2, -1, u == 0 && g[1] == Blocks.CANDLE ? Obra.velas(3) : g[u + 1].defaultBlockState());
		}
		// detrás, la trastienda: cajas, un saco de heno y un farol colgado del toldo
		o.pon(-1, 1, 1, Blocks.BARREL);
		o.pon(-1, 2, 1, Blocks.BARREL);
		o.pon(0, 1, 1, Obra.mira(Blocks.CHEST, Direction.NORTH));
		o.pon(1, 1, 1, Blocks.HAY_BLOCK);
		o.pon(0, 3, 0, Obra.farolColgado());
	}

	/** De fruta y verdura: tejadillo de madera a un agua, melones, calabazas y cajas también por delante. */
	private static void verduras(Obra o) {
		Paleta p = o.p;
		postes(o, -1, 2, 1, 4);
		for (int u = -2; u <= 2; u++) {
			o.pon(u, 5, 1, Obra.escalon(p.maderaEscalon, Direction.SOUTH, false));
			o.pon(u, 4, 0, Obra.escalon(p.maderaEscalon, Direction.SOUTH, false));
			o.pon(u, 3, -1, Obra.escalon(p.maderaEscalon, Direction.SOUTH, false));
			o.pon(u, 3, -2, Obra.losa(p.maderaLosa, false));
		}
		// el mostrador, lleno de género
		o.pon(-1, 1, -1, Blocks.PUMPKIN);
		o.pon(0, 1, -1, Blocks.BARREL);
		o.pon(0, 2, -1, Blocks.MELON);
		o.pon(1, 1, -1, Blocks.MELON);
		// cajas en el suelo delante y a los lados
		o.pon(-2, 1, -2, Blocks.HAY_BLOCK);
		o.pon(2, 1, -2, Blocks.COMPOSTER);
		o.pon(1, 1, -2, Obra.trampilla(p.trampilla, Direction.NORTH, false, false));
		// detrás: más cajas apiladas y sacos
		o.pon(-1, 1, 1, Blocks.BARREL);
		o.pon(0, 1, 1, Blocks.HAY_BLOCK);
		o.pon(0, 2, 1, Blocks.PUMPKIN);
		o.pon(1, 1, 1, Blocks.BARREL);
		o.pon(1, 2, 1, Blocks.BARREL);
		o.pon(0, 3, 0, Obra.farolColgado());
	}

	/** De flores: techo de hojas de azalea en flor, macetas en el mostrador y tiestos grandes a los lados. */
	private static void flores(Obra o) {
		Paleta p = o.p;
		postes(o, -1, 3, 1, 3);
		Block hoja = p.desierto ? p.hojas : Blocks.AZALEA_LEAVES;
		Block flor = p.desierto ? p.hojas : Blocks.FLOWERING_AZALEA_LEAVES;
		for (int u = -2; u <= 2; u++) {
			for (int v = -1; v <= 1; v++) {
				o.pon(u, 4, v, Obra.hojas(o.azar(u, 4, v, 3) == 0 ? hoja : flor));
			}
			if (o.azar(u, 3, -2, 3) != 0) {
				o.pon(u, 3, -2, Obra.hojas(flor));
			}
		}
		// mostrador de tablas con macetas
		Block[] macetas = p.desierto
				? new Block[] {Blocks.POTTED_CACTUS, Blocks.POTTED_DEAD_BUSH, Blocks.POTTED_ORANGE_TULIP, Blocks.POTTED_RED_TULIP}
				: new Block[] {Blocks.POTTED_POPPY, Blocks.POTTED_DANDELION, Blocks.POTTED_BLUE_ORCHID, Blocks.POTTED_ALLIUM,
						Blocks.POTTED_PINK_TULIP, Blocks.POTTED_CORNFLOWER, Blocks.POTTED_FLOWERING_AZALEA};
		for (int u = -1; u <= 1; u++) {
			o.pon(u, 1, -1, Obra.losa(p.maderaLosa, true));
			o.pon(u, 2, -1, macetas[o.azar(u, 2, -1, macetas.length)]);
		}
		// a los lados, tiestos grandes; detrás, una estantería de cajas con más flores
		o.pon(-2, 1, -2, Blocks.DECORATED_POT);
		o.pon(2, 1, -2, Blocks.DECORATED_POT);
		for (int u = -1; u <= 1; u++) {
			o.pon(u, 1, 1, Blocks.BARREL);
			o.pon(u, 2, 1, macetas[o.azar(u, 2, 1, macetas.length)]);
		}
		o.pon(0, 3, 0, Obra.farolColgado());
	}

	/** Un carro con ruedas, tablas a los lados, la carga y un palo con su farol. */
	private static void carro(Obra o) {
		Paleta p = o.p;
		for (int u = -1; u <= 1; u++) {
			o.pon(u, 1, 0, Obra.losa(p.maderaLosa, true));
			// las tablas de los lados, por encima de las ruedas
			o.pon(u, 2, -1, Obra.trampilla(p.trampilla, Direction.NORTH, false, true));
			o.pon(u, 2, 1, Obra.trampilla(p.trampilla, Direction.SOUTH, false, true));
		}
		for (int u : new int[] {-1, 1}) {
			o.pon(u, 1, -1, Obra.trampilla(Blocks.SPRUCE_TRAPDOOR, Direction.NORTH, false, true));
			o.pon(u, 1, 1, Obra.trampilla(Blocks.SPRUCE_TRAPDOOR, Direction.SOUTH, false, true));
		}
		// la carga
		Block[][] cargas = {
				{Blocks.HAY_BLOCK, Blocks.HAY_BLOCK, Blocks.BARREL},
				{Blocks.PUMPKIN, Blocks.BARREL, Blocks.MELON},
				{Blocks.BARREL, Blocks.HAY_BLOCK, Blocks.PUMPKIN}};
		Block[] c = cargas[o.azar(0, 2, 0, cargas.length)];
		for (int u = -1; u <= 1; u++) {
			o.pon(u, 2, 0, c[u + 1]);
		}
		if (c[0] == Blocks.HAY_BLOCK) {
			o.pon(-1, 3, 0, Blocks.HAY_BLOCK);
		}
		// las varas para tirar del carro y, detrás, el palo del farol
		o.pon(2, 1, 0, Obra.escalon(p.maderaEscalon, Direction.WEST, false));
		o.caja(-2, 1, 0, -2, 3, 0, p.valla);
		o.pon(-2, 4, 0, Blocks.LANTERN);
		// un par de sacos y una caja en el suelo, junto al carro
		o.pon(1, 1, -2, Blocks.BARREL);
		o.pon(-1, 1, -2, Blocks.HAY_BLOCK);
	}
}
