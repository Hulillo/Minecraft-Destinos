package es.hulillo.destinos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Lo que da vida a un pueblo propio (no a las capitales ni a las ciudades, que van empedradas): rincones junto a las
 * calles, con lo que se ve en un pueblo de su oficio (heno y calabazas en la aldea, leña en el aserradero, mineral en
 * la mina, barriles y fardos de algas en el puerto, dianas en el puesto de vigía), algún árbol y algún banco, y flores
 * y hierba sueltas por los prados de entre las casas. Todo sale de la posición (el mismo sitio da siempre lo mismo) y
 * mira solo el chunk que se está poniendo: da igual el orden en que se generen.
 */
final class RinconesPueblo {
	/** Como mucho un rincón por celda de CELDA×CELDA. */
	private static final int CELDA = 13;
	/** Lo que se aparta un rincón del borde del chunk (mira la calle a dos bloques y su sitio de 3×3). */
	private static final int MARGEN = 3;
	/** De cada cien columnas de prado, cuántas llevan una flor o hierba. */
	private static final int FLORES = 7;
	private static final int K_RINCON = 7121;
	private static final int K_FLOR = 3301;

	private enum Rincon {
		HENO, CALABAZAS, LENA, COLMENA, ARBOL, BANCO, MINERAL, BARRILES, ALGAS, DIANA
	}

	private static final Rincon[] ALDEA = {Rincon.HENO, Rincon.CALABAZAS, Rincon.COLMENA, Rincon.ARBOL, Rincon.ARBOL, Rincon.BANCO, Rincon.LENA};
	private static final Rincon[] MINA = {Rincon.MINERAL, Rincon.MINERAL, Rincon.LENA, Rincon.BANCO, Rincon.HENO, Rincon.ARBOL};
	private static final Rincon[] ASERRADERO = {Rincon.LENA, Rincon.LENA, Rincon.LENA, Rincon.ARBOL, Rincon.ARBOL, Rincon.BANCO, Rincon.HENO};
	private static final Rincon[] PUERTO = {Rincon.BARRILES, Rincon.ALGAS, Rincon.ALGAS, Rincon.BANCO, Rincon.LENA, Rincon.ARBOL};
	private static final Rincon[] PUESTO = {Rincon.DIANA, Rincon.DIANA, Rincon.HENO, Rincon.LENA};

	private final AdornoCapital adorno;
	private final int estilo;
	private final Rincon[] tipos;

	RinconesPueblo(AdornoCapital adorno, int estilo, MapaTerreno.TipoPueblo oficio) {
		this.adorno = adorno;
		this.estilo = estilo;
		this.tipos = switch (oficio) {
			case mina -> MINA;
			case aserradero -> ASERRADERO;
			case puerto -> PUERTO;
			case puesto -> PUESTO;
			default -> ALDEA;
		};
	}

	/** Si el pueblo de este oficio lleva rincones y calles de tierra (la ciudad no: es como una capital pequeña). */
	static boolean rural(int oficio) {
		return oficio >= 0 && oficio != MapaTerreno.TipoPueblo.ciudad.ordinal();
	}

	/** Pone los rincones y las flores de la parte x0..x1 × z0..z1 (dentro de caja, el chunk que se está poniendo). */
	void pon(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos, int x0, int z0, int x1, int z1) {
		for (int cx = Math.floorDiv(x0, CELDA); cx <= Math.floorDiv(x1, CELDA); cx++) {
			for (int cz = Math.floorDiv(z0, CELDA); cz <= Math.floorDiv(z1, CELDA); cz++) {
				rincon(level, caja, pos, cx, cz);
			}
		}
		if (estilo == AdornoCapital.DESIERTO) {
			return;
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				flor(level, pos, x, z);
			}
		}
	}

	/** Un rincón en la celda (cx, cz): en un prado llano de 3×3 que dé a una calle por un lado, apartado un bloque. */
	private void rincon(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos, int cx, int cz) {
		int h = Geo.hash(cx, cz, K_RINCON);
		// no todas las celdas: así quedan sueltos y no en fila
		if (Math.floorMod(h, 5) == 0) {
			return;
		}
		Rincon que = tipos[Math.floorMod(h >> 3, tipos.length)];
		int alto = que == Rincon.ARBOL ? 6 : 3;
		int ox = Math.floorMod(h >> 8, CELDA);
		int oz = Math.floorMod(h >> 14, CELDA);
		for (int i = 0; i < 40; i++) {
			int x = cx * CELDA + (ox + i * 5) % CELDA;
			int z = cz * CELDA + (oz + i * 3 + i / CELDA) % CELDA;
			if (x - MARGEN < caja.minX() || x + MARGEN > caja.maxX() || z - MARGEN < caja.minZ() || z + MARGEN > caja.maxZ()
					|| adorno.enPlaza(x, z, 4)) {
				continue;
			}
			int y = prado(level, pos, x, z);
			if (y == Integer.MIN_VALUE || !llano(level, pos, x, z, y, alto)) {
				continue;
			}
			// la calle a dos bloques por un solo lado, y nada de calle pegado
			Direction calle = null;
			int calles = 0;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (adorno.deCalle(level, pos, x + d.getStepX(), z + d.getStepZ(), y)) {
					calles = 9;
					break;
				}
				if (adorno.deCalle(level, pos, x + 2 * d.getStepX(), z + 2 * d.getStepZ(), y)) {
					calles++;
					calle = d;
				}
			}
			if (calles != 1) {
				continue;
			}
			despeja(level, pos, x, z, y);
			pon(level, pos, que, x, y + 1, z, calle, h);
			return;
		}
	}

	/** La altura del prado en (x, z) (la tierra de debajo de la hierba o la nieve), o MIN_VALUE si no es prado. */
	private static int prado(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		int y = SueloNatural.cima(level, x, z);
		BlockState s = level.getBlockState(pos.set(x, y, z));
		for (int i = 0; i < 2 && s.canBeReplaced() && s.getFluidState().isEmpty(); i++) {
			s = level.getBlockState(pos.set(x, --y, z));
		}
		return SueloNatural.natural(s) && s.getFluidState().isEmpty() ? y : Integer.MIN_VALUE;
	}

	/** Si el 3×3 alrededor de (x, z) es prado a la altura y, con alto bloques libres encima. */
	private boolean llano(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int y, int alto) {
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				if (prado(level, pos, x + ox, z + oz) != y || !AdornoCapital.libre(level, pos, x + ox, y + 1, z + oz, alto)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Quita la hierba y las flores del 3×3 (si no, la mitad de arriba de la hierba alta se quedaba flotando). */
	private static void despeja(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int y) {
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				for (int dy = 1; dy <= 2; dy++) {
					if (!level.getBlockState(pos.set(x + ox, y + dy, z + oz)).isAir()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	private void pon(WorldGenLevel level, BlockPos.MutableBlockPos pos, Rincon que, int x, int y, int z, Direction calle, int h) {
		// lo largo del rincón va a lo largo de la calle
		Direction a = calle.getClockWise();
		int ax = a.getStepX();
		int az = a.getStepZ();
		switch (que) {
			case HENO -> {
				// tres balas de heno y una encima
				for (int k = -1; k <= 1; k++) {
					level.setBlock(pos.set(x + k * ax, y, z + k * az), eje(Blocks.HAY_BLOCK, k == 0 ? Direction.UP : a), 2);
				}
				level.setBlock(pos.set(x, y + 1, z), Blocks.HAY_BLOCK.defaultBlockState(), 2);
			}
			case CALABAZAS -> {
				Block fruto = estilo == AdornoCapital.DESIERTO || estilo == AdornoCapital.SABANA ? Blocks.MELON : Blocks.PUMPKIN;
				level.setBlock(pos.set(x - ax, y, z - az), fruto.defaultBlockState(), 2);
				level.setBlock(pos.set(x, y, z), Blocks.HAY_BLOCK.defaultBlockState(), 2);
				level.setBlock(pos.set(x + ax, y, z + az), (Math.floorMod(h, 2) == 0 ? Blocks.MELON : fruto).defaultBlockState(), 2);
				level.setBlock(pos.set(x, y + 1, z), Blocks.CARVED_PUMPKIN.defaultBlockState()
						.setValue(net.minecraft.world.level.block.CarvedPumpkinBlock.FACING, calle), 2);
			}
			case LENA -> {
				// una pila de troncos tumbados a lo largo de la calle, más baja por los lados
				Block tronco = tronco();
				for (int k = -1; k <= 1; k++) {
					for (int f = 0; f <= 1; f++) {
						int px = x + k * ax - f * calle.getStepX();
						int pz = z + k * az - f * calle.getStepZ();
						level.setBlock(pos.set(px, y, pz), eje(tronco, a), 2);
						if (k == 0 || f == 1 && Math.floorMod(h + k, 2) == 0) {
							level.setBlock(pos.set(px, y + 1, pz), eje(tronco, a), 2);
						}
					}
				}
			}
			case COLMENA -> {
				level.setBlock(pos.set(x, y, z), eje(tronco(), Direction.UP), 2);
				level.setBlock(pos.set(x, y + 1, z), Blocks.BEEHIVE.defaultBlockState()
						.setValue(net.minecraft.world.level.block.BeehiveBlock.FACING, calle), 2);
				Block[] flores = {Blocks.DANDELION, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY};
				for (int k = -1; k <= 1; k += 2) {
					level.setBlock(pos.set(x + k * ax, y, z + k * az), flores[Math.floorMod(h + k, flores.length)].defaultBlockState(), 2);
				}
			}
			case ARBOL -> adorno.arbolito(level, pos, x, y, z, h);
			case BANCO -> {
				// un banco de dos mirando a la calle, con un farol en un poste al lado
				for (int k = 0; k <= 1; k++) {
					level.setBlock(pos.set(x + k * ax, y, z + k * az), adorno.asiento().defaultBlockState().setValue(StairBlock.FACING, calle.getOpposite()), 2);
				}
				level.setBlock(pos.set(x - ax, y, z - az), adorno.valla().defaultBlockState(), 2);
				level.setBlock(pos.set(x - ax, y + 1, z - az), Blocks.LANTERN.defaultBlockState(), 2);
			}
			case MINERAL -> {
				// el mineral sacado del monte, amontonado
				BlockState[] monton = {Blocks.COBBLESTONE.defaultBlockState(), Blocks.COAL_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(),
						Blocks.GRAVEL.defaultBlockState(), Blocks.COPPER_ORE.defaultBlockState()};
				for (int k = -1; k <= 1; k++) {
					level.setBlock(pos.set(x + k * ax, y, z + k * az), monton[Math.floorMod(h + 3 * k, monton.length)], 2);
				}
				level.setBlock(pos.set(x, y + 1, z), Blocks.COBBLESTONE_SLAB.defaultBlockState(), 2);
			}
			case BARRILES -> {
				// en el puerto los barriles son del pescador, que es lo que hay
				level.setBlock(pos.set(x - ax, y, z - az), Blocks.BARREL.defaultBlockState(), 2);
				level.setBlock(pos.set(x, y, z), Blocks.BARREL.defaultBlockState(), 2);
				level.setBlock(pos.set(x, y + 1, z), Blocks.LANTERN.defaultBlockState(), 2);
				level.setBlock(pos.set(x + ax, y, z + az), Blocks.DRIED_KELP_BLOCK.defaultBlockState(), 2);
			}
			case ALGAS -> {
				// fardos de algas secándose
				for (int k = -1; k <= 1; k++) {
					level.setBlock(pos.set(x + k * ax, y, z + k * az), Blocks.DRIED_KELP_BLOCK.defaultBlockState(), 2);
				}
				level.setBlock(pos.set(x + (Math.floorMod(h, 2) == 0 ? ax : -ax), y + 1, z + (Math.floorMod(h, 2) == 0 ? az : -az)),
						Blocks.DRIED_KELP_BLOCK.defaultBlockState(), 2);
			}
			case DIANA -> {
				// un muñeco de prácticas: poste de valla, bala de heno y diana encima
				level.setBlock(pos.set(x, y, z), adorno.valla().defaultBlockState(), 2);
				level.setBlock(pos.set(x, y + 1, z), Blocks.HAY_BLOCK.defaultBlockState(), 2);
				level.setBlock(pos.set(x, y + 2, z), Blocks.TARGET.defaultBlockState(), 2);
			}
		}
	}

	/** Una flor o hierba suelta en el prado de (x, z), una de cada FLORES por ciento. */
	private void flor(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		int h = Math.floorMod(Geo.hash(x, z, K_FLOR), 100);
		if (h >= FLORES) {
			return;
		}
		int y = SueloNatural.cima(level, x, z);
		if (!level.getBlockState(pos.set(x, y, z)).is(Blocks.GRASS_BLOCK) || !level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
			return;
		}
		Block[] flores = estilo == AdornoCapital.TAIGA || estilo == AdornoCapital.NIEVE
				? new Block[] {Blocks.FERN, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.LILY_OF_THE_VALLEY, Blocks.CORNFLOWER, Blocks.FERN}
				: estilo == AdornoCapital.SABANA ? new Block[] {Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.DANDELION, Blocks.ORANGE_TULIP, Blocks.POPPY}
				: new Block[] {Blocks.POPPY, Blocks.DANDELION, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.SHORT_GRASS,
						Blocks.SHORT_GRASS};
		level.setBlock(pos.set(x, y + 1, z), flores[h % flores.length].defaultBlockState(), 2);
	}

	private Block tronco() {
		return switch (estilo) {
			case AdornoCapital.SABANA -> Blocks.ACACIA_LOG;
			case AdornoCapital.TAIGA, AdornoCapital.NIEVE -> Blocks.SPRUCE_LOG;
			case AdornoCapital.DESIERTO -> Blocks.JUNGLE_LOG;
			default -> Blocks.OAK_LOG;
		};
	}

	private static BlockState eje(Block b, Direction d) {
		return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, d.getAxis());
	}
}
