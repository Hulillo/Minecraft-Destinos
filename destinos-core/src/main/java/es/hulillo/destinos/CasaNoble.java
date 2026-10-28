package es.hulillo.destinos;

import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Una casa noble de la capital, mirando a la plaza mayor: planta baja de piedra con el salón (chimenea, sillones,
 * estantería) y la cocina, y encima un piso de entramado en voladizo con dos dormitorios, unidos por una escalera de
 * verdad, y bajo el tejado un desván forrado de madera al que se sube por una escala. Tejado a dos aguas con el
 * hastial hacia la plaza y chimenea con humo (en el desierto, todo de arenisca y con azotea almenada).
 */
public final class CasaNoble extends PiezaObra {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) CasaNoble::new;
	/** Medio lado de la caja (la casa con el alero, el voladizo y la chimenea). */
	static final int FONDO = 9;
	/** Media anchura (u) y medio fondo (v) de la planta baja. */
	private static final int ANCHO = 6;
	private static final int LARGO = 7;
	/** Suelo del piso de arriba y arranque del tejado. */
	private static final int PISO = 6;
	private static final int TEJADO = 11;
	private static final int ALTO = 20;

	CasaNoble(int cx, int cz, int y0, int rot, int estilo, int madera, int color) {
		super(TIPO, cx, cz, y0, rot, estilo, madera, color, FONDO + TALUD, ALTO);
	}

	private CasaNoble(CompoundTag t) {
		super(TIPO, t);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "casa_noble"), TIPO);
	}

	@Override
	protected void construye(Obra o) {
		Paleta p = o.p;
		int a = ANCHO;
		int l = LARGO;
		o.planta(-a, -l, a, l);
		o.planta(-a - 1, -3, -a - 1, -3);
		// planta baja de piedra, con suelo de tablas
		o.caja(-a, 0, -l, a, 0, l, p.zocalo);
		o.caja(-a + 1, 0, -l + 1, a - 1, 0, l - 1, p.tablas);
		o.sillares(-a, 1, -l, a, PISO - 1, l, true);
		o.aire(-a + 1, 1, -l + 1, a - 1, PISO - 1, l - 1);
		for (int u : new int[] {-a, a}) {
			for (int v : new int[] {-l, l}) {
				o.caja(u, 1, v, u, PISO - 1, v, p.moldura);
			}
		}
		o.caja(-a + 1, PISO, -l + 1, a - 1, PISO, l - 1, p.tablas);
		// el piso de arriba, un bloque en voladizo hacia la plaza
		if (p.desierto) {
			o.sillares(-a, PISO, -l - 1, a, TEJADO - 1, l, true);
			o.paredes(-a, PISO, -l - 1, a, PISO, l, p.moldura);
		} else {
			entramado(o, -a, -l - 1, a, l);
		}
		o.caja(-a + 1, PISO, -l, a - 1, PISO, -l, p.tablas);
		o.aire(-a + 1, PISO + 1, -l, a - 1, TEJADO - 1, l - 1);
		for (int u = -a; u <= a; u++) {
			o.pon(u, PISO - 1, -l - 1, Obra.escalon(p.desierto ? p.piedraEscalon : p.maderaEscalon, Direction.SOUTH, true));
		}
		// fachada: puerta, ventanas, estandartes y faroles bajo el voladizo
		o.puerta(0, 1, -l, Direction.NORTH);
		o.pon(0, 3, -l, p.moldura);
		for (int s = -1; s <= 1; s += 2) {
			ventana(o, 3 * s, 2, -l);
			ventana(o, 2 * s, 2, l);
			o.estandarte(2 * s, 3, -l - 1, Direction.NORTH);
			o.pon(4 * s, 3, -l - 1, Obra.farolColgado());
		}
		ventana(o, -a, 2, -5);
		ventana(o, -a, 2, 3);
		ventana(o, a, 2, -1);
		ventana(o, a, 2, 3);
		salon(o);
		cocina(o);
		dormitorios(o);
		tejado(o);
		if (!p.desierto) {
			desvan(o);
		}
	}

	private static void ventana(Obra o, int u, int ly, int v) {
		o.pon(u, ly, v, Blocks.GLASS_PANE);
		o.pon(u, ly + 1, v, Blocks.GLASS_PANE);
	}

	/** El piso de arriba de entramado: postes, vigas abajo y arriba, enlucido y ventanas en los vanos. */
	private void entramado(Obra o, int ua, int va, int ub, int vb) {
		Paleta p = o.p;
		for (int u = ua; u <= ub; u++) {
			for (int v = va; v <= vb; v++) {
				boolean bordeU = u == ua || u == ub;
				boolean bordeV = v == va || v == vb;
				if (!bordeU && !bordeV) {
					continue;
				}
				boolean poste = bordeU && bordeV || (bordeV ? Math.abs(u) == 3 || u == 0 : (v - va) % 3 == 0);
				Direction.Axis eje = bordeV ? Direction.Axis.X : Direction.Axis.Z;
				for (int y = PISO; y < TEJADO; y++) {
					BlockState b;
					if (poste) {
						b = Obra.tronco(p.tronco, Direction.Axis.Y);
					} else if (y == PISO || y == TEJADO - 1) {
						b = Obra.tronco(p.tronco, eje);
					} else if (y == PISO + 2 || y == PISO + 3) {
						b = Blocks.GLASS_PANE.defaultBlockState();
					} else {
						b = p.enlucido;
					}
					o.pon(u, y, v, b);
				}
			}
		}
	}

	/** Planta baja, delante: el salón, con chimenea, dos sillones, mesita, alfombra y estantería. */
	private void salon(Obra o) {
		Paleta p = o.p;
		o.caja(-3, 1, -5, -1, 1, -1, o.tinte("carpet", Blocks.RED_CARPET));
		o.chimenea(-ANCHO + 1, 1, -3, Direction.EAST, PISO);
		o.silla(-1, 1, -4, Direction.EAST);
		o.silla(-1, 1, -2, Direction.EAST);
		o.mesa(-2, -3, -2, -3, 1, false);
		o.caja(ANCHO - 1, 1, -5, ANCHO - 1, 2, -3, Blocks.BOOKSHELF);
		o.pon(ANCHO - 1, 3, -4, Blocks.LANTERN);
		o.pon(0, PISO - 1, -3, Obra.farolColgado());
		// tabique con la cocina, con un arco ancho en medio; la escalera sube pegada a la pared del este, cerrada del
		// lado de la cocina por un tabique entero (sin rendijas entre peldaños y techo)
		o.caja(-ANCHO + 1, 1, 1, ANCHO - 2, PISO - 1, 1, p.tablas);
		o.aire(-1, 1, 1, 1, 3, 1);
		o.pon(-1, 3, 1, Obra.escalon(p.maderaEscalon, Direction.WEST, true));
		o.pon(1, 3, 1, Obra.escalon(p.maderaEscalon, Direction.EAST, true));
		o.escalera(ANCHO - 1, 1, -1, Direction.SOUTH, PISO, p.maderaEscalon, p.tablas);
		o.caja(ANCHO - 2, 1, 2, ANCHO - 2, PISO - 1, PISO - 2, p.tablas);
	}

	/** Planta baja, detrás: la cocina, con fogones, caldero, despensa y mesa. */
	private void cocina(Obra o) {
		int v = LARGO - 1;
		o.pon(-4, 1, v, Obra.mira(Blocks.SMOKER, Direction.NORTH));
		o.pon(-3, 1, v, Obra.mira(Blocks.FURNACE, Direction.NORTH));
		o.pon(-2, 1, v, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		o.pon(-1, 1, v, Blocks.BARREL);
		o.pon(0, 1, v, Blocks.CRAFTING_TABLE);
		o.pon(1, 1, v, Blocks.BARREL);
		o.pon(1, 2, v, Blocks.FLOWER_POT);
		o.pon(-4, 1, 2, Blocks.HAY_BLOCK);
		o.pon(-4, 2, 2, Blocks.PUMPKIN);
		o.mesa(-2, 3, -1, 3, 1, false);
		o.silla(-2, 1, 2, Direction.NORTH);
		o.silla(-1, 1, 2, Direction.NORTH);
		o.pon(-1, PISO - 1, 3, Obra.farolColgado());
	}

	/** El piso de arriba: el dormitorio de los señores delante y el de los hijos detrás, con la escalera. */
	private void dormitorios(Obra o) {
		Paleta p = o.p;
		int y = PISO + 1;
		o.caja(-ANCHO + 1, y, -1, ANCHO - 1, TEJADO - 1, -1, p.tablas);
		o.aire(2, y, -1, 2, y + 1, -1);
		// delante: cama de matrimonio del color del reino, mesillas, armario y escritorio junto a la ventana
		o.caja(-2, y, -5, 1, y, -3, o.tinte("carpet", Blocks.RED_CARPET));
		o.cama(-4, y, -4, Direction.WEST, o.tinte("bed", Blocks.RED_BED));
		o.cama(-4, y, -3, Direction.WEST, o.tinte("bed", Blocks.RED_BED));
		o.pon(-4, y, -5, Blocks.BARREL);
		o.pon(-4, y + 1, -5, Obra.velas(2));
		o.pon(-4, y, -2, Blocks.BARREL);
		o.pon(-4, y + 1, -2, Blocks.LANTERN);
		o.caja(4, y, -6, 4, y + 1, -5, Blocks.BARREL);
		o.pon(4, y, -3, Obra.mira(Blocks.CHEST, Direction.WEST));
		o.mesa(1, -6, 2, -6, y, false);
		o.silla(1, y, -5, Direction.SOUTH);
		o.pon(0, TEJADO - 1, -4, Obra.farolColgado());
		// detrás: dos camas, cofre y la barandilla del hueco de la escalera
		o.caja(-4, y, 1, 0, y, 3, Blocks.BROWN_CARPET);
		o.cama(-3, y, 5, Direction.SOUTH, Blocks.WHITE_BED);
		o.cama(-1, y, 5, Direction.SOUTH, Blocks.WHITE_BED);
		o.pon(-2, y, 5, Blocks.BARREL);
		o.pon(-2, y + 1, 5, Obra.velas(3));
		o.pon(-4, y, 5, Obra.mira(Blocks.CHEST, Direction.EAST));
		for (int v = 1; v <= PISO - 3; v++) {
			o.pon(ANCHO - 2, y, v, p.valla);
		}
		o.pon(-2, TEJADO - 1, 2, Obra.farolColgado());
	}

	/** El desván: se sube por una escala desde el cuarto de detrás; trastos, la cama del criado y faroles en la viga. */
	private void desvan(Obra o) {
		int y = TEJADO + 1;
		BlockState escala = Obra.mira(Blocks.LADDER, Direction.NORTH);
		// pegada al poste de en medio de la pared de atrás, no delante de una ventana
		o.caja(0, PISO + 1, LARGO - 1, 0, TEJADO, LARGO - 1, escala);
		o.pon(-2, y, -5, Blocks.BARREL);
		o.pon(-2, y + 1, -5, Blocks.BARREL);
		o.pon(-1, y, -5, Blocks.BARREL);
		o.pon(1, y, -5, Obra.mira(Blocks.CHEST, Direction.SOUTH));
		o.pon(2, y, -4, Blocks.HAY_BLOCK);
		o.pon(-2, y, -2, Obra.mira(Blocks.DECORATED_POT, Direction.EAST));
		o.cama(0, y, 3, Direction.SOUTH, Blocks.WHITE_BED);
		o.pon(-1, y, 3, Blocks.BARREL);
		o.pon(-1, y + 1, 3, Obra.velas(1));
		o.caja(-1, y, -1, 1, y, 1, Blocks.BROWN_CARPET);
		o.pon(0, TEJADO + 4, -3, Obra.farolColgado());
		o.pon(0, TEJADO + 4, 2, Obra.farolColgado());
		o.pon(-1, TEJADO + 3, -6, Blocks.COBWEB);
		o.pon(1, TEJADO + 3, 5, Blocks.COBWEB);
	}

	/** Tejado a dos aguas con el hastial hacia la plaza y la chimenea por fuera (en el desierto, azotea). */
	private void tejado(Obra o) {
		Paleta p = o.p;
		int a = ANCHO;
		int l = LARGO;
		if (p.desierto) {
			o.caja(-a, TEJADO, -l - 1, a, TEJADO, l, p.tejadoLleno);
			for (int u = -a; u <= a; u++) {
				for (int v = -l - 1; v <= l; v++) {
					if ((Math.abs(u) == a || v == -l - 1 || v == l) && Math.floorMod(u + v, 2) == 0) {
						o.pon(u, TEJADO + 1, v, p.moldura);
					}
				}
			}
		} else {
			o.caja(-a + 1, TEJADO, -l, a - 1, TEJADO, l - 1, p.tablas);
			o.dosAguas(-a - 1, -l - 2, a + 1, l + 1, TEJADO, true, p.enlucido);
		}
		o.sillares(-a - 1, 0, -3, -a - 1, TEJADO + 3, -3, false);
		o.pon(-a - 1, TEJADO + 4, -3, Blocks.CAMPFIRE);
	}
}
