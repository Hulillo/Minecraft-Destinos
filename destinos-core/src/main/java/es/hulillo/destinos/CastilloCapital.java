package es.hulillo.destinos;

import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * El castillo de una capital rica (muralla de nivel 2). Una muralla almenada con torres cuadradas en las esquinas,
 * rematadas con un piso de madera en voladizo y chapitel, y una barbacana con rastrillo y hastial en la puerta. Al
 * fondo del patio, el palacio: planta baja de piedra con arcos y dos pisos de entramado en voladizo, tejado a dos
 * aguas con buhardillas, torrecillas en las esquinas de delante y la torre del homenaje, con escalera de caracol,
 * balcón y aguja. Dentro: salón del trono, cocina y comedor abajo; sala del consejo y alcobas en el primer piso;
 * la cámara del señor, el tesoro y la capilla en el segundo; y catacumbas debajo. En el patio, una fuente con
 * parterres, el establo, el pozo y farolas. Los materiales van con la cultura (Paleta) y los estandartes con el
 * color del reino.
 */
public final class CastilloCapital extends PiezaObra {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) CastilloCapital::new;
	/** Muralla: los anillos MURO - 1 y MURO; por fuera, zócalo y contrafuertes. */
	private static final int MURO = 18;
	private static final int ALTO_MURO = 8;
	/** Torres de las esquinas: centro en (±MURO, ±MURO), medio lado TORRE. */
	private static final int TORRE = 3;
	/** El palacio: de -PALACIO a PALACIO a lo ancho y de 0 a FONDO; pisos en 6 y 11 y desván en 16. */
	private static final int PALACIO = 12;
	private static final int FONDO = 16;
	private static final int[] PISOS = {0, 6, 11, 16};
	/** La torre del homenaje: centro en (0, HV), dentro del palacio, al fondo. */
	private static final int HV = 13;
	private static final int BALCON = 26;
	/** La barbacana: de -BARBACANA a BARBACANA, de FRENTE a MURO - 3 (por dentro). */
	private static final int BARBACANA = 5;
	private static final int FRENTE = -(MURO + 3);
	/** Medio lado de la caja: las torres de las esquinas con su tejado. */
	static final int MEDIO = MURO + TORRE + 2;
	private static final int ALTO = 46;

	CastilloCapital(int cx, int cz, int y0, int rot, int estilo, int color) {
		super(TIPO, cx, cz, y0, rot, estilo, color, MEDIO, ALTO);
	}

	private CastilloCapital(CompoundTag t) {
		super(TIPO, t);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "castillo"), TIPO);
	}

	/** El giro para que la puerta mire hacia (dx, dz), el lado de la plaza visto desde el castillo. */
	static int giroHacia(int dx, int dz) {
		if (Math.abs(dz) >= Math.abs(dx)) {
			return dz < 0 ? 0 : 2;
		}
		return dx > 0 ? 1 : 3;
	}

	/** Dónde está, para el registro: centro, suelo y giro. */
	String donde() {
		return cx + ", " + y0 + ", " + cz + " (giro " + rot + ")";
	}

	@Override
	protected void construye(Obra o) {
		o.planta(-MURO - 1, -MURO - 1, MURO + 1, MURO + 1);
		o.planta(-BARBACANA, FRENTE, BARBACANA, -MURO);
		patio(o);
		muralla(o);
		for (int su = -1; su <= 1; su += 2) {
			for (int sv = -1; sv <= 1; sv += 2) {
				torre(o, su, sv);
			}
		}
		barbacana(o);
		palacio(o);
		catacumbas(o);
		homenaje(o);
	}

	// ---- muralla, torres y barbacana ----

	private void muralla(Obra o) {
		Paleta p = o.p;
		for (int u = -MURO - 1; u <= MURO + 1; u++) {
			for (int v = -MURO - 1; v <= MURO + 1; v++) {
				int anillo = Math.max(Math.abs(u), Math.abs(v));
				if (anillo < MURO - 1) {
					continue;
				}
				int largo = Math.abs(u) >= Math.abs(v) ? v : u;
				o.pon(u, 0, v, p.zocalo);
				if (anillo <= MURO) {
					o.sillares(u, 1, v, u, ALTO_MURO, v, false);
					// saeteras en la cara de fuera, entre contrafuerte y contrafuerte
					if (anillo == MURO && Math.floorMod(largo, 6) == 3) {
						o.pon(u, 5, v, Blocks.AIR);
						o.pon(u, 6, v, Blocks.AIR);
					}
					if (anillo == MURO && Math.floorMod(u + v, 2) == 0) {
						o.pon(u, ALTO_MURO + 1, v, p.piedra(o, u, ALTO_MURO + 1, v));
					}
					if (anillo == MURO - 1) {
						o.pon(u, ALTO_MURO, v, p.moldura);
					}
				} else if (Math.floorMod(largo, 6) == 0) {
					o.sillares(u, 1, v, u, ALTO_MURO - 1, v, false);
				} else {
					o.pon(u, 1, v, p.zocalo);
				}
			}
		}
		// escalera al adarve, pegada a la muralla de delante por dentro
		o.escalera(BARBACANA + 2, 1, -(MURO - 2), Direction.EAST, ALTO_MURO, p.piedraEscalon, p.zocalo);
	}

	/** Torre cuadrada de esquina: maciza hasta el adarve, sala encima, piso de madera en voladizo y chapitel. */
	private void torre(Obra o, int su, int sv) {
		Paleta p = o.p;
		int cu = su * MURO;
		int cv = sv * MURO;
		o.sillares(cu - TORRE, 1, cv - TORRE, cu + TORRE, 12, cv + TORRE, false);
		o.caja(cu - TORRE + 1, ALTO_MURO, cv - TORRE + 1, cu + TORRE - 1, ALTO_MURO, cv + TORRE - 1, p.tablas);
		o.aire(cu - TORRE + 1, ALTO_MURO + 1, cv - TORRE + 1, cu + TORRE - 1, 11, cv + TORRE - 1);
		o.caja(cu - TORRE + 1, 12, cv - TORRE + 1, cu + TORRE - 1, 12, cv + TORRE - 1, p.tablas);
		o.pon(cu, 11, cv, Obra.farolColgado());
		o.paredes(cu - TORRE, 12, cv - TORRE, cu + TORRE, 12, cv + TORRE, p.moldura);
		// por donde llega el adarve de cada muralla se entra en la torre
		for (int a = MURO - 1; a <= MURO; a++) {
			o.aire(cu - su * TORRE, ALTO_MURO + 1, sv * a, cu - su * TORRE, ALTO_MURO + 2, sv * a);
			o.aire(su * a, ALTO_MURO + 1, cv - sv * TORRE, su * a, ALTO_MURO + 2, cv - sv * TORRE);
		}
		o.pon(cu + su * TORRE, 10, cv, Blocks.GLASS_PANE);
		o.pon(cu, 10, cv + sv * TORRE, Blocks.GLASS_PANE);
		if (p.desierto) {
			o.paredes(cu - TORRE, 13, cv - TORRE, cu + TORRE, 13, cv + TORRE, p.moldura);
			for (int d = -TORRE; d <= TORRE; d += 2) {
				o.pon(cu + d, 14, cv - TORRE, p.moldura);
				o.pon(cu + d, 14, cv + TORRE, p.moldura);
				o.pon(cu - TORRE, 14, cv + d, p.moldura);
				o.pon(cu + TORRE, 14, cv + d, p.moldura);
			}
			o.chapitel(cu, cv, 13, TORRE - 1);
			return;
		}
		// el piso de madera, un bloque más ancho, sobre ménsulas
		int h = TORRE + 1;
		for (int du = -h; du <= h; du++) {
			for (int dv = -h; dv <= h; dv++) {
				if (Math.max(Math.abs(du), Math.abs(dv)) != h) {
					continue;
				}
				o.pon(cu + du, 12, cv + dv, p.tablas);
				if (Math.abs(du) != Math.abs(dv)) {
					Direction adentro = Math.abs(du) == h ? (du > 0 ? Direction.WEST : Direction.EAST) : (dv > 0 ? Direction.NORTH : Direction.SOUTH);
					o.pon(cu + du, 11, cv + dv, Obra.escalon(p.maderaEscalon, adentro, true));
				}
				boolean poste = Math.abs(du) == Math.abs(dv) || du == 0 || dv == 0;
				for (int y = 13; y <= 15; y++) {
					boolean ventana = y == 14 && (Math.abs(du) == 2 || Math.abs(dv) == 2) && !poste;
					o.pon(cu + du, y, cv + dv, poste ? Obra.tronco(p.tronco, Direction.Axis.Y) : ventana ? Blocks.GLASS_PANE.defaultBlockState() : p.enlucido);
				}
			}
		}
		o.aire(cu - TORRE, 13, cv - TORRE, cu + TORRE, 15, cv + TORRE);
		o.caja(cu - h, 16, cv - h, cu + h, 16, cv + h, p.tablas);
		o.chapitel(cu, cv, 16, h + 1);
	}

	/** La barbacana: paso abovedado con rastrillo, sala encima para la guardia y tejado con hastial y rosetón. */
	private void barbacana(Obra o) {
		Paleta p = o.p;
		int dentro = -(MURO - 3);
		o.sillares(-BARBACANA, 1, FRENTE, BARBACANA, 13, dentro, false);
		o.paredes(-BARBACANA, ALTO_MURO, FRENTE, BARBACANA, ALTO_MURO, dentro, p.moldura);
		o.caja(-BARBACANA + 1, ALTO_MURO, FRENTE + 1, BARBACANA - 1, ALTO_MURO, dentro - 1, p.tablas);
		o.aire(-BARBACANA + 1, ALTO_MURO + 1, FRENTE + 1, BARBACANA - 1, 12, dentro - 1);
		// el paso: tres de ancho, con arco y el rastrillo a medio bajar
		o.aire(-1, 1, FRENTE, 1, 4, dentro);
		for (int v = FRENTE; v <= dentro; v++) {
			o.pon(-1, 4, v, Obra.escalon(p.piedraEscalon, Direction.WEST, true));
			o.pon(1, 4, v, Obra.escalon(p.piedraEscalon, Direction.EAST, true));
			o.pon(-1, 0, v, p.adoquin(o, -1, v));
			o.pon(0, 0, v, p.adoquin(o, 0, v));
			o.pon(1, 0, v, p.adoquin(o, 1, v));
		}
		o.caja(-1, 3, FRENTE + 1, 1, 3, FRENTE + 1, Blocks.IRON_BARS);
		o.pon(0, 4, FRENTE + 1, Blocks.IRON_BARS);
		// del adarve se pasa a la sala de la guardia
		for (int a = MURO - 1; a <= MURO; a++) {
			o.aire(-BARBACANA, ALTO_MURO + 1, -a, -BARBACANA, ALTO_MURO + 2, -a);
			o.aire(BARBACANA, ALTO_MURO + 1, -a, BARBACANA, ALTO_MURO + 2, -a);
		}
		o.pon(0, 12, FRENTE + 3, Obra.farolColgado());
		o.pon(-BARBACANA + 1, ALTO_MURO + 1, FRENTE + 1, Blocks.BARREL);
		o.pon(-BARBACANA + 1, ALTO_MURO + 1, FRENTE + 2, Obra.mira(Blocks.CHEST, Direction.EAST));
		o.pon(BARBACANA - 1, ALTO_MURO + 1, FRENTE + 1, Blocks.FLETCHING_TABLE);
		o.mesa(-1, FRENTE + 3, 1, FRENTE + 3, ALTO_MURO + 1, true);
		// ventanas y estandartes de la fachada; pilares con farol a los lados de la entrada
		for (int s = -1; s <= 1; s += 2) {
			o.pon(2 * s, 10, FRENTE, Blocks.GLASS_PANE);
			o.pon(2 * s, 11, FRENTE, Blocks.GLASS_PANE);
			o.estandarte(4 * s, 11, FRENTE - 1, Direction.NORTH);
			o.pon(3 * s, 1, FRENTE - 1, p.muroBajo);
			o.pon(3 * s, 2, FRENTE - 1, p.muroBajo);
			o.pon(3 * s, 3, FRENTE - 1, Blocks.LANTERN);
		}
		if (p.desierto) {
			for (int u = -BARBACANA; u <= BARBACANA; u++) {
				for (int v = FRENTE; v <= dentro; v++) {
					boolean borde = Math.abs(u) == BARBACANA || v == FRENTE || v == dentro;
					if (borde && Math.floorMod(u + v, 2) == 0) {
						o.pon(u, 14, v, p.moldura);
					}
				}
			}
			o.cupula(0, (FRENTE + dentro) / 2, 14, 2);
			return;
		}
		o.dosAguas(-BARBACANA - 1, FRENTE - 1, BARBACANA + 1, dentro + 1, 14, true, p.piedra(o, 0, 14, FRENTE));
		// rosetón en el hastial de delante
		int[][] roseton = {{0, 15}, {-1, 16}, {0, 16}, {1, 16}, {0, 17}};
		for (int[] r : roseton) {
			o.pon(r[0], r[1], FRENTE, Blocks.GLASS_PANE);
		}
	}

	// ---- el patio ----

	private void patio(Obra o) {
		Paleta p = o.p;
		int ua = -(MURO - 2), ub = MURO - 2, va = -(MURO - 2), vb = -1;
		for (int u = ua; u <= ub; u++) {
			for (int v = va; v <= vb; v++) {
				o.pon(u, 0, v, p.adoquin(o, u, v));
			}
		}
		fuente(o, 0, -9);
		if (p.desierto) {
			// alrededor de la fuente, un mosaico de azulejos
			Block[] azulejos = {Blocks.BLUE_GLAZED_TERRACOTTA, Blocks.ORANGE_GLAZED_TERRACOTTA, Blocks.CYAN_GLAZED_TERRACOTTA};
			for (int du = -7; du <= 7; du++) {
				for (int dv = -7; dv <= 7; dv++) {
					if (!octogono(du, dv, 5)) {
						Direction d = Direction.from2DDataValue(Math.floorMod(du + dv, 4));
						o.pon(du, 0, -9 + dv, Obra.mira(azulejos[Math.floorMod(Math.abs(du) + Math.abs(dv), 3)], d));
					}
				}
			}
		}
		// parterres a los lados de la fuente, con un arbolito en los de delante
		for (int s = -1; s <= 1; s += 2) {
			for (int u = 6; u <= 9; u++) {
				for (int v = -15; v <= -12; v++) {
					o.parterre(s * u, 1, v);
				}
				for (int v = -6; v <= -3; v++) {
					o.parterre(s * u, 1, v);
				}
			}
			o.arbol(s * 7, 1, -13, 4);
			o.farola(3 * s, 1, -15, s > 0 ? Direction.WEST : Direction.EAST);
			o.farola(3 * s, 1, -3, s > 0 ? Direction.WEST : Direction.EAST);
		}
		o.arbol(-14, 1, -13, 5);
		establo(o);
		pozo(o, -12, -4);
	}

	private static boolean octogono(int du, int dv, int r) {
		return Math.max(Math.abs(du), Math.abs(dv)) <= r && Math.abs(du) + Math.abs(dv) <= r + r / 2;
	}

	/** Fuente: pilón octogonal con escalones alrededor y un caño en medio que rebosa. */
	private void fuente(Obra o, int cu, int cv) {
		Paleta p = o.p;
		for (int du = -5; du <= 5; du++) {
			for (int dv = -5; dv <= 5; dv++) {
				int u = cu + du;
				int v = cv + dv;
				if (octogono(du, dv, 3)) {
					o.pon(u, 0, v, p.losa);
					o.pon(u, 1, v, Blocks.WATER);
				} else if (octogono(du, dv, 4)) {
					o.pon(u, 0, v, p.zocalo);
					o.pon(u, 1, v, p.moldura);
				} else if (octogono(du, dv, 5)) {
					Direction adentro = Math.abs(du) >= Math.abs(dv) ? (du > 0 ? Direction.WEST : Direction.EAST)
							: (dv > 0 ? Direction.NORTH : Direction.SOUTH);
					o.pon(u, 1, v, Obra.escalon(p.piedraEscalon, adentro, false));
				}
			}
		}
		o.pon(cu, 1, cv, p.moldura);
		o.pon(cu, 2, cv, p.muroBajo);
		o.pon(cu, 3, cv, p.moldura);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			o.pon(cu + d.getStepX(), 3, cv + d.getStepZ(), Obra.escalon(p.piedraEscalon, d.getOpposite(), true));
		}
		o.pon(cu, 4, cv, Blocks.WATER);
	}

	/** El establo, pegado a la muralla del este: tres cuadras con heno y bebedero, bajo un tejado a dos aguas. */
	private void establo(Obra o) {
		Paleta p = o.p;
		int u0 = 11, u1 = MURO - 2, v0 = -13, v1 = -3;
		for (int u = u0 + 1; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				o.pon(u, 0, v, o.azar(u, 0, v, 3) == 0 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
			}
		}
		for (int v : new int[] {v0, -8, v1}) {
			o.caja(u0, 1, v, u0, 3, v, Obra.tronco(p.tronco, Direction.Axis.Y));
		}
		o.viga(u0, v0, u0, v1, 4);
		o.viga(u0, v0, u1, v0, 4);
		o.viga(u0, v1, u1, v1, 4);
		o.dosAguas(u0 - 1, v0 - 1, u1, v1 + 1, 5, true, p.tablas);
		// las cuadras: separadores de valla, heno al fondo y un bebedero
		for (int v : new int[] {-10, -6}) {
			o.caja(u0 + 2, 1, v, u1, 1, v, p.valla);
		}
		for (int v : new int[] {-12, -8, -4}) {
			o.pon(u1, 1, v, Blocks.HAY_BLOCK);
			o.pon(u1, 1, v + 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
			o.pon(u0, 3, v, Obra.farolColgado());
		}
		o.pon(u0 - 1, 1, v0, Blocks.HAY_BLOCK);
		o.pon(u0 - 1, 2, v0, Blocks.HAY_BLOCK);
		o.pon(u0 - 2, 1, v0, Blocks.BARREL);
		o.pon(u0 - 1, 1, v1, Blocks.COMPOSTER);
	}

	/** Pozo de piedra con su tejadillo de madera sobre dos postes y un farol colgando. */
	private void pozo(Obra o, int cu, int cv) {
		Paleta p = o.p;
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				if (du == 0 && dv == 0) {
					o.pon(cu, 0, cv, Blocks.WATER);
					o.pon(cu, -1, cv, Blocks.WATER);
					o.pon(cu, -2, cv, Blocks.WATER);
					o.pon(cu, -3, cv, p.zocalo);
				} else {
					o.caja(cu + du, -3, cv + dv, cu + du, 0, cv + dv, p.zocalo);
					o.pon(cu + du, 1, cv + dv, p.piedra(o, cu + du, 1, cv + dv));
				}
			}
		}
		for (int s = -1; s <= 1; s += 2) {
			o.pon(cu + s, 2, cv, p.valla);
			o.pon(cu + s, 3, cv, p.valla);
		}
		for (int du = -2; du <= 2; du++) {
			o.pon(cu + du, 4, cv - 1, Obra.escalon(p.maderaEscalon, Direction.SOUTH, false));
			o.pon(cu + du, 4, cv + 1, Obra.escalon(p.maderaEscalon, Direction.NORTH, false));
			o.pon(cu + du, 4, cv, Obra.tronco(p.tronco, Direction.Axis.X));
			o.pon(cu + du, 5, cv, Obra.losa(p.maderaLosa, false));
		}
		o.pon(cu, 3, cv, Obra.farolColgado());
	}

	// ---- el palacio ----

	private void palacio(Obra o) {
		Paleta p = o.p;
		int a = PALACIO;
		// planta baja de piedra
		o.sillares(-a, 1, 0, a, PISOS[1] - 1, FONDO, true);
		o.caja(-a, 0, 0, a, 0, FONDO, p.zocalo);
		o.caja(-a + 1, 0, 1, a - 1, 0, FONDO - 1, p.losa);
		o.aire(-a + 1, 1, 1, a - 1, PISOS[1] - 1, FONDO - 1);
		// los pisos de arriba, en voladizo por delante
		for (int f = 1; f < PISOS.length; f++) {
			o.caja(-a + 1, PISOS[f], 0, a - 1, PISOS[f], FONDO - 1, p.tablas);
		}
		if (p.desierto) {
			o.sillares(-a, PISOS[1], 0, a, PISOS[3], FONDO, true);
			o.aire(-a + 1, PISOS[1] + 1, 1, a - 1, PISOS[2] - 1, FONDO - 1);
			o.aire(-a + 1, PISOS[2] + 1, 1, a - 1, PISOS[3] - 1, FONDO - 1);
			o.paredes(-a, PISOS[1], 0, a, PISOS[1], FONDO, p.moldura);
			o.paredes(-a, PISOS[3], 0, a, PISOS[3], FONDO, p.moldura);
			ventanasArco(o, 0);
		} else {
			entramado(o, -a, -1, a, FONDO);
			for (int u = -a; u <= a; u++) {
				o.pon(u, PISOS[1] - 1, -1, Obra.escalon(p.maderaEscalon, Direction.SOUTH, true));
				o.pon(u, PISOS[1], 0, p.tablas);
			}
		}
		// la fachada de abajo: entrada en arco, ventanas, estandartes y faroles bajo el voladizo
		o.aire(-1, 1, 0, 1, 3, 0);
		o.pon(-1, 3, 0, Obra.escalon(p.piedraEscalon, Direction.WEST, true));
		o.pon(1, 3, 0, Obra.escalon(p.piedraEscalon, Direction.EAST, true));
		o.pon(0, 4, 0, p.moldura);
		for (int s = -1; s <= 1; s += 2) {
			for (int u : new int[] {4, 7, 10}) {
				ventana(o, s * u, 2, 0);
			}
			for (int v : new int[] {3, 6, 11, 14}) {
				ventana(o, s * a, 2, v);
			}
			for (int u : new int[] {6, 9}) {
				ventana(o, s * u, 2, FONDO);
			}
			o.estandarte(2 * s, 4, -1, Direction.NORTH);
			o.pon(6 * s, 4, -1, Obra.farolColgado());
			o.pon(10 * s, 4, -1, Obra.farolColgado());
		}
		salonDelTrono(o);
		cocina(o);
		comedor(o);
		primerPiso(o);
		segundoPiso(o);
		tejado(o);
	}

	private static void ventana(Obra o, int u, int ly, int v) {
		o.pon(u, ly, v, Blocks.GLASS_PANE);
		o.pon(u, ly + 1, v, Blocks.GLASS_PANE);
	}

	/** Los pisos de arriba de entramado: postes cada tres, vigas en cada piso, enlucido y ventanas en cada vano. */
	private void entramado(Obra o, int ua, int va, int ub, int vb) {
		Paleta p = o.p;
		for (int u = ua; u <= ub; u++) {
			for (int v = va; v <= vb; v++) {
				boolean bordeU = u == ua || u == ub;
				boolean bordeV = v == va || v == vb;
				if (!bordeU && !bordeV) {
					continue;
				}
				int largo = bordeV ? u - ua : v - va;
				boolean poste = bordeU && bordeV || largo % 3 == 0;
				Direction.Axis eje = bordeV ? Direction.Axis.X : Direction.Axis.Z;
				for (int y = PISOS[1]; y <= PISOS[3]; y++) {
					BlockState b;
					if (poste) {
						b = Obra.tronco(p.tronco, Direction.Axis.Y);
					} else if (y == PISOS[1] || y == PISOS[2] || y == PISOS[3]) {
						b = Obra.tronco(p.tronco, eje);
					} else if (y == PISOS[1] + 2 || y == PISOS[1] + 3 || y == PISOS[2] + 2 || y == PISOS[2] + 3) {
						b = Blocks.GLASS_PANE.defaultBlockState();
					} else {
						b = p.enlucido;
					}
					o.pon(u, y, v, b);
				}
			}
		}
	}

	/** En el desierto, ventanas de arco en los pisos de arriba. */
	private void ventanasArco(Obra o, int v0) {
		for (int u = -PALACIO + 2; u <= PALACIO - 2; u += 3) {
			for (int f = 1; f <= 2; f++) {
				ventana(o, u, PISOS[f] + 2, v0);
				ventana(o, u, PISOS[f] + 2, FONDO);
			}
		}
		for (int v = 3; v <= FONDO - 3; v += 3) {
			for (int f = 1; f <= 2; f++) {
				ventana(o, -PALACIO, PISOS[f] + 2, v);
				ventana(o, PALACIO, PISOS[f] + 2, v);
			}
		}
	}

	/** Planta baja, delante: el salón del trono, con estrado, columnas, vigas, lámparas y dos chimeneas. */
	private void salonDelTrono(Obra o) {
		Paleta p = o.p;
		// tabique con las alas de detrás (cocina y comedor), con una puerta a cada una
		for (int s = -1; s <= 1; s += 2) {
			o.sillares(s * 4, 1, 9, s * (PALACIO - 1), PISOS[1] - 1, 9, false);
			o.aire(s * 7, 1, 9, s * 7, 2, 9);
		}
		for (int v : new int[] {2, 5, 8}) {
			o.viga(-PALACIO + 1, v, PALACIO - 1, v, 5);
		}
		for (int s = -1; s <= 1; s += 2) {
			o.pilar(s * 5, 3, 1, 4);
			o.pilar(s * 5, 7, 1, 4);
			o.pon(s * 3, 4, 2, Obra.farolColgado());
			o.pon(s * 3, 4, 5, Obra.farolColgado());
			o.pon(s * 9, 4, 5, Obra.farolColgado());
			o.chimenea(s * (PALACIO - 1), 1, 5, s > 0 ? Direction.WEST : Direction.EAST, PISOS[1]);
		}
		// alfombra hasta el estrado
		o.caja(-1, 1, 1, 1, 1, 6, Blocks.RED_CARPET);
		// el estrado con el trono, el respaldo dorado y los estandartes del reino detrás
		o.caja(-3, 1, 7, 3, 1, 9, p.moldura);
		o.caja(-3, 2, 7, 3, 2, 9, o.tinte("carpet", Blocks.RED_CARPET));
		o.silla(0, 2, 8, Direction.SOUTH);
		o.pon(0, 2, 9, Blocks.GOLD_BLOCK);
		o.pon(0, 3, 9, Blocks.GOLD_BLOCK);
		for (int s = -1; s <= 1; s += 2) {
			o.pon(s, 2, 8, Obra.escalon(p.maderaEscalon, s > 0 ? Direction.EAST : Direction.WEST, false));
			o.estandarte(2 * s, 4, 9, Direction.NORTH);
			o.pon(3 * s, 2, 9, Blocks.LANTERN);
		}
	}

	/** Ala oeste de abajo: la cocina, con su hogar, la escalera a las catacumbas, mesa de trabajo y despensa. */
	private void cocina(Obra o) {
		Paleta p = o.p;
		int u0 = -PALACIO + 1;
		o.chimenea(-8, 1, FONDO - 1, Direction.NORTH, PISOS[1]);
		o.pon(-6, 1, FONDO - 1, Obra.mira(Blocks.SMOKER, Direction.NORTH));
		o.pon(-5, 1, FONDO - 1, Obra.mira(Blocks.FURNACE, Direction.NORTH));
		o.pon(-4, 1, FONDO - 1, Blocks.BARREL);
		o.pon(-4, 1, FONDO - 3, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		o.pon(-4, 1, 10, Blocks.HAY_BLOCK);
		o.pon(-5, 1, 10, Blocks.BARREL);
		o.pon(-5, 2, 10, Blocks.PUMPKIN);
		o.mesa(-8, 12, -6, 12, 1, false);
		o.pon(-7, 5, 12, Obra.farolColgado());
		// la bajada a las catacumbas, con barandilla
		o.escalera(u0, -5, FONDO - 1, Direction.NORTH, 6, p.piedraEscalon, p.zocalo);
		for (int v = 11; v <= FONDO - 1; v++) {
			o.pon(u0 + 1, 1, v, p.valla);
		}
	}

	/** Ala este de abajo: el comedor, con la mesa larga, chimenea, aparador y lámpara. */
	private void comedor(Obra o) {
		o.mesa(6, 12, 10, 13, 1, true);
		o.chimenea(PALACIO - 1, 1, 12, Direction.WEST, PISOS[1]);
		o.pon(8, 5, 12, Obra.farolColgado());
		o.pon(8, 5, 13, Obra.farolColgado());
		o.pon(5, 1, FONDO - 1, Blocks.BARREL);
		o.pon(6, 1, FONDO - 1, Blocks.BARREL);
		o.pon(5, 2, FONDO - 1, Blocks.FLOWER_POT);
		o.pon(6, 2, FONDO - 1, Obra.velas(3));
		o.pon(PALACIO - 1, 1, 10, Blocks.CHISELED_BOOKSHELF);
		o.pon(PALACIO - 1, 1, 15, Blocks.BOOKSHELF);
	}

	/** Primer piso: la sala del consejo delante (mesa larga, biblioteca, mapas) y dos alcobas en las alas. */
	private void primerPiso(Obra o) {
		Paleta p = o.p;
		int y = PISOS[1] + 1;
		tabique(o, PISOS[1]);
		o.caja(-4, y, 2, 4, y, 6, o.tinte("carpet", Blocks.RED_CARPET));
		o.mesa(-3, 4, 3, 4, y, true);
		o.silla(-4, y, 4, Direction.WEST);
		o.silla(4, y, 4, Direction.EAST);
		for (int v = 1; v <= 7; v++) {
			Block estante = v % 3 == 0 ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF;
			o.caja(-PALACIO + 1, y, v, -PALACIO + 1, y + 2, v, estante);
		}
		o.pon(-PALACIO + 2, y, 4, Obra.mira(Blocks.LECTERN, Direction.EAST));
		o.pon(PALACIO - 1, y, 2, Blocks.CARTOGRAPHY_TABLE);
		o.pon(PALACIO - 1, y, 3, Blocks.BARREL);
		o.pon(PALACIO - 1, y + 1, 3, Blocks.FLOWER_POT);
		for (int u : new int[] {-7, 0, 7}) {
			o.pon(u, PISOS[2] - 1, 4, Obra.farolColgado());
		}
		// alcoba de la corte (oeste): dos camas, cofre y armario
		o.cama(-PALACIO + 1, y, 11, Direction.WEST, Blocks.WHITE_BED);
		o.cama(-PALACIO + 1, y, 13, Direction.WEST, Blocks.WHITE_BED);
		o.pon(-PALACIO + 1, y, 12, Blocks.BARREL);
		o.pon(-PALACIO + 1, y + 1, 12, Obra.velas(2));
		o.pon(-PALACIO + 1, y, FONDO - 1, Obra.mira(Blocks.CHEST, Direction.EAST));
		o.caja(-6, y, FONDO - 1, -5, y + 1, FONDO - 1, Blocks.BARREL);
		o.caja(-9, y, 11, -8, y, 14, Blocks.BROWN_CARPET);
		o.pon(-8, PISOS[2] - 1, 12, Obra.farolColgado());
		// alcoba de invitados (este): cama del color del reino, escritorio y estantería
		o.cama(PALACIO - 1, y, 12, Direction.EAST, o.tinte("bed", Blocks.RED_BED));
		o.pon(PALACIO - 1, y, 11, Blocks.BARREL);
		o.pon(PALACIO - 1, y + 1, 11, Blocks.LANTERN);
		o.mesa(7, FONDO - 1, 8, FONDO - 1, y, false);
		o.silla(7, y, FONDO - 2, Direction.NORTH);
		o.caja(4, y, FONDO - 1, 5, y + 1, FONDO - 1, Blocks.BOOKSHELF);
		o.pon(8, PISOS[2] - 1, 12, Obra.farolColgado());
	}

	/** Segundo piso: la cámara del señor delante (cama con dosel, chimenea), el tesoro y la capilla en las alas. */
	private void segundoPiso(Obra o) {
		Paleta p = o.p;
		int y = PISOS[2] + 1;
		tabique(o, PISOS[2]);
		// cama con dosel del color del reino
		o.cama(-PALACIO + 1, y, 3, Direction.WEST, o.tinte("bed", Blocks.RED_BED));
		o.cama(-PALACIO + 1, y, 4, Direction.WEST, o.tinte("bed", Blocks.RED_BED));
		for (int u : new int[] {-PALACIO + 1, -PALACIO + 3}) {
			for (int v : new int[] {2, 5}) {
				o.caja(u, y, v, u, y + 2, v, p.valla);
			}
		}
		o.caja(-PALACIO + 1, y + 3, 2, -PALACIO + 3, y + 3, 5, o.tinte("wool", Blocks.RED_WOOL));
		o.caja(-8, y, 2, -6, y, 5, o.tinte("carpet", Blocks.RED_CARPET));
		o.pon(-PALACIO + 1, y, 1, Obra.mira(Blocks.CHEST, Direction.EAST));
		o.pon(-PALACIO + 1, y, 6, Blocks.BARREL);
		o.pon(-PALACIO + 1, y + 1, 6, Obra.velas(3));
		o.chimenea(PALACIO - 1, y, 4, Direction.WEST, PISOS[3]);
		o.silla(PALACIO - 3, y, 3, Direction.WEST);
		o.silla(PALACIO - 3, y, 5, Direction.WEST);
		o.mesa(4, 7, 6, 7, y, false);
		o.silla(5, y, 6, Direction.NORTH);
		o.pon(0, PISOS[3] - 1, 4, Obra.farolColgado());
		o.pon(-6, PISOS[3] - 1, 4, Obra.farolColgado());
		// el tesoro (oeste): cofres, oro y vasijas
		for (int v = 10; v <= FONDO - 1; v += 2) {
			o.pon(-PALACIO + 1, y, v, Obra.mira(Blocks.CHEST, Direction.EAST));
		}
		o.caja(-6, y, FONDO - 1, -5, y, FONDO - 1, Blocks.GOLD_BLOCK);
		o.pon(-6, y + 1, FONDO - 1, Blocks.GOLD_BLOCK);
		o.pon(-4, y, FONDO - 1, Obra.mira(Blocks.DECORATED_POT, Direction.NORTH));
		o.pon(-8, y, 10, Obra.mira(Blocks.DECORATED_POT, Direction.SOUTH));
		o.pon(-8, PISOS[3] - 1, 13, Obra.farolColgado());
		// la capilla (este): altar con velas, atril y bancos mirando al altar
		o.caja(PALACIO - 1, y, 12, PALACIO - 1, y, 13, p.moldura);
		o.pon(PALACIO - 1, y + 1, 12, Obra.velas(4));
		o.pon(PALACIO - 1, y + 1, 13, Obra.velas(3));
		o.estandarte(PALACIO - 1, y + 2, 11, Direction.WEST);
		o.pon(PALACIO - 2, y, 12, Obra.mira(Blocks.LECTERN, Direction.WEST));
		for (int u : new int[] {6, 8}) {
			for (int v = 11; v <= 14; v++) {
				o.silla(u, y, v, Direction.WEST);
			}
		}
		o.pon(8, PISOS[3] - 1, 12, Obra.farolColgado());
	}

	/** El tabique entre la parte de delante y las alas en un piso, con una puerta a cada ala. */
	private void tabique(Obra o, int suelo) {
		Paleta p = o.p;
		for (int s = -1; s <= 1; s += 2) {
			o.caja(s * 4, suelo + 1, 9, s * (PALACIO - 1), suelo + 4, 9, p.tablas);
			o.aire(s * 7, suelo + 1, 9, s * 7, suelo + 2, 9);
		}
	}

	/** El tejado del palacio: a dos aguas con buhardillas delante (en el desierto, azotea almenada). */
	private void tejado(Obra o) {
		Paleta p = o.p;
		if (p.desierto) {
			for (int u = -PALACIO; u <= PALACIO; u++) {
				for (int v = 0; v <= FONDO; v++) {
					if ((Math.abs(u) == PALACIO || v == 0 || v == FONDO) && Math.floorMod(u + v, 2) == 0) {
						o.pon(u, PISOS[3] + 1, v, p.moldura);
					}
				}
			}
			for (int s = -1; s <= 1; s += 2) {
				o.cupula(s * 7, 6, PISOS[3] + 1, 2);
			}
			return;
		}
		o.dosAguas(-PALACIO - 1, -2, PALACIO + 1, FONDO + 1, PISOS[3], false, p.enlucido);
		for (int s = -1; s <= 1; s += 2) {
			int du = 7 * s;
			o.aire(du - 2, PISOS[3], -1, du + 2, PISOS[3] + 7, 4);
			for (int u = du - 2; u <= du + 2; u++) {
				for (int y = PISOS[3]; y <= PISOS[3] + 2; y++) {
					boolean poste = Math.abs(u - du) == 2;
					boolean ventana = y == PISOS[3] + 1 && !poste;
					o.pon(u, y, -1, poste ? Obra.tronco(p.tronco, Direction.Axis.Y) : ventana ? Blocks.GLASS_PANE.defaultBlockState() : p.enlucido);
				}
			}
			for (int v = 0; v <= 4; v++) {
				o.caja(du - 2, PISOS[3], v, du - 2, PISOS[3] + 3, v, p.enlucido);
				o.caja(du + 2, PISOS[3], v, du + 2, PISOS[3] + 3, v, p.enlucido);
			}
			o.dosAguas(du - 3, -2, du + 3, 5, PISOS[3] + 3, true, p.enlucido);
		}
		// la chimenea de fuera de las dos chimeneas del salón, con humo
		for (int s = -1; s <= 1; s += 2) {
			o.sillares(s * (PALACIO + 1), PISOS[1], 5, s * (PALACIO + 1), PISOS[3] + 9, 5, false);
			o.pon(s * (PALACIO + 1), PISOS[3] + 10, 5, Blocks.CAMPFIRE);
		}
	}

	// ---- catacumbas ----

	private void catacumbas(Obra o) {
		Paleta p = o.p;
		int a = PALACIO;
		o.sillares(-a, -6, 0, a, -1, FONDO, true);
		o.caja(-a + 1, -6, 1, a - 1, -6, FONDO - 1, p.zocalo);
		for (int u = -a + 1; u <= a - 1; u++) {
			for (int v = 1; v <= FONDO - 1; v++) {
				for (int y = -5; y <= -1; y++) {
					if (o.en(u, y, v) == null) {
						o.pon(u, y, v, Blocks.AIR);
					}
				}
			}
		}
		for (int u : new int[] {-8, -4, 4, 8}) {
			for (int v : new int[] {4, 8, 12}) {
				o.sillares(u, -5, v, u, -1, v, false);
			}
		}
		// sarcófagos, con velas encima
		for (int s = -1; s <= 1; s += 2) {
			for (int v : new int[] {2, 6, 10}) {
				o.caja(s * 6, -5, v, s * 6, -5, v + 1, p.moldura);
				o.caja(s * 6, -4, v, s * 6, -4, v + 1, Obra.losa(p.piedraLosa, false));
			}
			o.pon(s * 6, -3, 6, Obra.velas(1 + (s > 0 ? 2 : 0)));
		}
		// calaveras en nichos a lo largo de las paredes
		for (int v = 2; v <= FONDO - 2; v += 2) {
			o.pon(a - 1, -4, v, Obra.mira(Blocks.SKELETON_WALL_SKULL, Direction.WEST));
			if (v < 10) {
				o.pon(-a + 1, -4, v, Obra.mira(Blocks.SKELETON_WALL_SKULL, Direction.EAST));
			}
		}
		for (int u = -a + 3; u <= a - 3; u += 3) {
			o.pon(u, -4, 1, Obra.mira(Blocks.SKELETON_WALL_SKULL, Direction.SOUTH));
		}
		// el altar del fondo y la luz de almas
		o.pon(0, -5, FONDO - 1, p.moldura);
		o.pon(0, -4, FONDO - 1, Obra.velas(4));
		o.estandarte(0, -2, FONDO - 1, Direction.NORTH);
		for (int v : new int[] {3, 7, 11}) {
			o.pon(0, -1, v, Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
		}
		o.pon(a - 1, -1, 1, Blocks.COBWEB);
		o.pon(-a + 1, -1, 1, Blocks.COBWEB);
		o.pon(a - 1, -1, FONDO - 1, Blocks.COBWEB);
		o.pon(-6, -5, 14, Obra.mira(Blocks.DECORATED_POT, Direction.NORTH));
	}

	// ---- la torre del homenaje ----

	/** Las 16 casillas de la escalera de caracol, en el sentido de las agujas del reloj desde delante a la izquierda. */
	private static final int[][] CARACOL = {{-2, 11}, {-1, 11}, {0, 11}, {1, 11}, {2, 11}, {2, 12}, {2, 13}, {2, 14}, {2, 15},
			{1, 15}, {0, 15}, {-1, 15}, {-2, 15}, {-2, 14}, {-2, 13}, {-2, 12}};
	/** Desfase de la escalera: así los rellanos de cada piso caen donde hay salida al palacio. */
	private static final int DESFASE = 8;

	private void homenaje(Obra o) {
		Paleta p = o.p;
		int t = TORRE;
		int arriba = 31;
		o.sillares(-t, 1, HV - t, t, arriba, HV + t, true);
		o.aire(-t + 1, 1, HV - t + 1, t - 1, BALCON - 1, HV + t - 1);
		o.sillares(-1, 1, HV - 1, 1, BALCON - 1, HV + 1, false);
		// la escalera de caracol, con zanca maciza en la primera vuelta
		for (int s = 1; s <= BALCON; s++) {
			int[] c = CARACOL[(s - 1 + DESFASE) % 16];
			int[] sig = CARACOL[(s + DESFASE) % 16];
			int du = Integer.signum(sig[0] - c[0]);
			Direction sube = du > 0 ? Direction.EAST : du < 0 ? Direction.WEST : sig[1] > c[1] ? Direction.SOUTH : Direction.NORTH;
			boolean rellano = s == PISOS[1] || s == PISOS[2] || s == BALCON;
			o.pon(c[0], s, c[1], rellano ? p.tablas : Obra.escalon(p.piedraEscalon, sube, false));
			if (s > 1 && s <= 16) {
				o.sillares(c[0], 1, c[1], c[0], s - 1, c[1], false);
			}
			if (rellano) {
				salida(o, c, s);
			}
		}
		salida(o, CARACOL[(DESFASE - 1) % 16], 0);
		// ventanas a lo largo de la escalera
		for (int y = 4; y <= 22; y += 6) {
			o.pon(t, y, HV, Blocks.GLASS_PANE);
			o.pon(-t, y, HV, Blocks.GLASS_PANE);
			o.pon(0, y, HV + t, Blocks.GLASS_PANE);
		}
		// la sala de arriba, con su suelo (sin tapar los últimos peldaños)
		for (int i = 0; i < CARACOL.length; i++) {
			// el último peldaño de esta casilla: si llega a menos de dos del suelo, ahí se deja el hueco
			int ultimo = 0;
			for (int s = 1; s < BALCON; s++) {
				if ((s - 1 + DESFASE) % 16 == i) {
					ultimo = s;
				}
			}
			if (ultimo < BALCON - 2) {
				o.pon(CARACOL[i][0], BALCON, CARACOL[i][1], p.tablas);
			}
		}
		o.caja(-1, BALCON, HV - 1, 1, BALCON, HV + 1, p.tablas);
		o.aire(-t + 1, BALCON + 1, HV - t + 1, t - 1, arriba, HV + t - 1);
		o.pon(0, arriba, HV, Obra.farolColgado());
		o.pon(1, BALCON + 1, HV + 1, Blocks.BARREL);
		o.pon(-1, BALCON + 1, HV + 2, Obra.mira(Blocks.CHEST, Direction.NORTH));
		// el balcón alrededor, sobre ménsulas y con barandilla
		int b = t + 1;
		for (int du = -b; du <= b; du++) {
			for (int dv = -b; dv <= b; dv++) {
				if (Math.max(Math.abs(du), Math.abs(dv)) != b) {
					continue;
				}
				o.pon(du, BALCON, HV + dv, p.moldura);
				o.pon(du, BALCON + 1, HV + dv, p.valla);
				if (Math.abs(du) != Math.abs(dv)) {
					Direction adentro = Math.abs(du) == b ? (du > 0 ? Direction.WEST : Direction.EAST) : (dv > 0 ? Direction.NORTH : Direction.SOUTH);
					o.pon(du, BALCON - 1, HV + dv, Obra.escalon(p.piedraEscalon, adentro, true));
				}
			}
		}
		for (int d = -1; d <= 1; d += 2) {
			ventana(o, 2 * d, BALCON + 2, HV - t);
			ventana(o, t, BALCON + 2, HV + 2 * d);
			ventana(o, -t, BALCON + 2, HV + 2 * d);
			ventana(o, 2 * d, BALCON + 2, HV + t);
			o.estandarte(2 * d, arriba - 1, HV - t - 1, Direction.NORTH);
		}
		// cornisa y chapitel
		o.paredes(-b, arriba + 1, HV - b, b, arriba + 1, HV + b, p.moldura);
		o.caja(-t, arriba + 1, HV - t, t, arriba + 1, HV + t, p.tablas);
		o.chapitel(0, HV, arriba + 2, b);
		// torrecillas en las esquinas de delante del palacio
		for (int s = -1; s <= 1; s += 2) {
			int cu = s * PALACIO;
			o.sillares(cu - 1, 1, -2, cu + 1, 20, 0, false);
			o.pon(cu + s, 8, -1, Blocks.GLASS_PANE);
			o.pon(cu, 13, -2, Blocks.GLASS_PANE);
			o.caja(cu - 2, 21, -3, cu + 2, 21, 1, p.moldura);
			o.chapitel(cu, -1, 22, 2);
		}
	}

	/** Puerta desde el rellano de la escalera (casilla c, a la altura s) hacia fuera de la torre. */
	private void salida(Obra o, int[] c, int s) {
		int u = Math.abs(c[0]) == 2 ? c[0] + Integer.signum(c[0]) : c[0];
		int v = Math.abs(c[0]) == 2 ? c[1] : c[1] == HV - 2 ? HV - TORRE : HV + TORRE;
		o.aire(u, s + 1, v, u, s + 2, v);
	}
}
