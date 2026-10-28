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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * El castillo de una capital rica (muralla de nivel 2). Muralla almenada con adarve de dos de ancho, cuatro torres
 * en las esquinas (con puerta al patio, escalera de ida y vuelta hasta el adarve y una sala arriba bajo el chapitel)
 * y una barbacana con rastrillo y sala de guardia abierta hasta la cumbrera. Delante, un patio grande: la fuente en
 * medio del camino de losas, canteros con árboles, bancos, el pozo, el campo de tiro y la herrería. Al fondo, el
 * palacio: planta baja de piedra con porche, dos pisos de entramado en voladizo y el desván con buhardillas. En
 * medio del palacio sube la torre del homenaje, que lleva la escalera de todos los pisos (tramos de tres de ancho
 * con descansillos) hasta el balcón y la sala de arriba. Dentro: salón del trono, cocina y comedor abajo; sala del
 * consejo y alcobas en el primer piso; la cámara del señor, el tesoro y la capilla en el segundo; y el desván de
 * los criados. A los lados del palacio, el establo (este) y la entrada a las mazmorras (oeste), que bajan a las
 * celdas y siguen hasta las catacumbas de debajo del palacio. Los materiales van con la cultura y la madera de la
 * capital (Paleta), y los estandartes con el color del reino.
 */
public final class CastilloCapital extends PiezaObra {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) CastilloCapital::new;
	/** Muralla: los anillos MURO - 2 a MURO (el de fuera con almenas); por fuera, zócalo y contrafuertes. */
	private static final int MURO = 24;
	private static final int ALTO_MURO = 10;
	/** Por dentro de la muralla, el patio llega hasta aquí. */
	private static final int PATIO = MURO - 3;
	/** Torres de las esquinas: centro en (±MURO, ±MURO), medio lado TORRE; pisos cada 5. */
	private static final int TORRE = 4;
	/** El palacio: de -PALACIO a PALACIO a lo ancho y de DELANTE a DETRAS; pisos en 6 y 11 y desván en 16. */
	private static final int PALACIO = 13;
	private static final int DELANTE = 4;
	private static final int DETRAS = MURO - 3;
	private static final int[] PISOS = {0, 6, 11, 16};
	/** La torre del homenaje: de -HU a HU y de HV0 a DETRAS, con pisos de escalera en PISOS y en 21 y 26. */
	private static final int HU = 4;
	private static final int HV0 = 13;
	private static final int HV = (HV0 + DETRAS) / 2;
	private static final int BALCON = 26;
	private static final int CIMA = 31;
	/** La fuente del patio. */
	private static final int FV = -9;
	/** La barbacana: de -BARBACANA a BARBACANA, de FRENTE a DENTRO. */
	private static final int BARBACANA = 6;
	private static final int FRENTE = -(MURO + 3);
	private static final int DENTRO = -PATIO;
	/** Medio lado de la caja: las torres de las esquinas con el alero de su chapitel. */
	static final int MEDIO = MURO + TORRE + 2;
	private static final int ALTO = 46;

	CastilloCapital(int cx, int cz, int y0, int rot, int estilo, int madera, int color) {
		super(TIPO, cx, cz, y0, rot, estilo, madera, color, MEDIO, ALTO);
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
		o.planta(-BARBACANA, FRENTE, BARBACANA, -MURO - 1);
		for (int su = -1; su <= 1; su += 2) {
			for (int sv = -1; sv <= 1; sv += 2) {
				o.planta(su * MURO - TORRE, sv * MURO - TORRE, su * MURO + TORRE, sv * MURO + TORRE);
			}
		}
		patio(o);
		ladoOeste(o);
		establo(o);
		muralla(o);
		for (int su = -1; su <= 1; su += 2) {
			for (int sv = -1; sv <= 1; sv += 2) {
				torre(o, su, sv);
			}
		}
		barbacana(o);
		palacio(o);
		catacumbas(o);
		mazmorras(o);
	}

	private static void ventana(Obra o, int u, int ly, int v) {
		o.pon(u, ly, v, Blocks.GLASS_PANE);
		o.pon(u, ly + 1, v, Blocks.GLASS_PANE);
	}

	private static boolean octogono(int du, int dv, int r) {
		return Math.max(Math.abs(du), Math.abs(dv)) <= r && Math.abs(du) + Math.abs(dv) <= r + r / 2;
	}

	/** Chimenea por fuera de la pared, de piedra, hasta alto, con humo. */
	private static void chimeneaFuera(Obra o, int u, int v, int alto) {
		o.sillares(u, 1, v, u, alto, v, false);
		o.pon(u, alto + 1, v, Blocks.CAMPFIRE);
	}

	// ---- muralla, torres y barbacana ----

	private void muralla(Obra o) {
		Paleta p = o.p;
		for (int u = -MURO - 1; u <= MURO + 1; u++) {
			for (int v = -MURO - 1; v <= MURO + 1; v++) {
				int anillo = Math.max(Math.abs(u), Math.abs(v));
				if (anillo < MURO - 2) {
					continue;
				}
				int largo = Math.abs(u) >= Math.abs(v) ? v : u;
				o.pon(u, 0, v, p.zocalo);
				if (anillo <= MURO) {
					o.sillares(u, 1, v, u, ALTO_MURO, v, false);
					if (anillo == MURO) {
						// saeteras entre contrafuerte y contrafuerte, y almenas
						if (Math.floorMod(largo, 6) == 3) {
							o.pon(u, 5, v, Blocks.AIR);
							o.pon(u, 6, v, Blocks.AIR);
						}
						if (Math.floorMod(u + v, 2) == 0) {
							o.pon(u, ALTO_MURO + 1, v, p.piedra(o, u, ALTO_MURO + 1, v));
						}
						o.pon(u, ALTO_MURO - 1, v, p.moldura);
					}
					if (anillo == MURO - 2) {
						o.pon(u, ALTO_MURO, v, p.moldura);
					}
				} else if (Math.floorMod(largo, 6) == 0) {
					o.sillares(u, 1, v, u, ALTO_MURO - 3, v, false);
					Direction afuera = Math.abs(u) > Math.abs(v) ? (u > 0 ? Direction.EAST : Direction.WEST) : (v > 0 ? Direction.SOUTH : Direction.NORTH);
					o.pon(u, ALTO_MURO - 2, v, Obra.escalon(p.piedraEscalon, afuera.getOpposite(), false));
				} else {
					o.pon(u, 1, v, p.zocalo);
				}
			}
		}
	}

	/**
	 * Torre de esquina: puerta al patio, tres tramos de escalera de tres de ancho (uno por piso, de ida y vuelta a
	 * los lados de un muro de en medio), salida al adarve en el piso 10, sala arriba y chapitel.
	 */
	private void torre(Obra o, int su, int sv) {
		Paleta p = o.p;
		int cu = su * MURO;
		int cv = sv * MURO;
		int t = TORRE;
		int r = t - 1;
		// la banda de los tramos que suben hacia el sur (los de los pisos 0 y 10) es la de dentro del castillo
		int sa = -su;
		o.caja(cu - t, 0, cv - t, cu + t, 0, cv + t, p.zocalo);
		o.sillares(cu - t, 1, cv - t, cu + t, 20, cv + t, true);
		o.aire(cu - r, 1, cv - r, cu + r, 19, cv + r);
		o.caja(cu - r, 0, cv - r, cu + r, 0, cv + r, p.tablas);
		o.paredes(cu - t, ALTO_MURO, cv - t, cu + t, ALTO_MURO, cv + t, p.moldura);
		for (int piso = 5; piso <= 15; piso += 5) {
			o.caja(cu - r, piso, cv - r, cu + r, piso, cv + r, p.tablas);
		}
		for (int i = 1; i <= 3; i++) {
			int a = cu + sa * i;
			int b = cu - sa * i;
			// huecos de los tramos que llegan a cada piso
			o.aire(a, 5, cv - 2, a, 5, cv + 1);
			o.aire(b, 10, cv - 1, b, 10, cv + 2);
			o.aire(a, 15, cv - 2, a, 15, cv + 1);
			o.escalera(a, 1, cv - 2, Direction.SOUTH, 5, p.piedraEscalon, p.zocalo);
			o.escalera(b, 6, cv + 2, Direction.NORTH, 5, p.piedraEscalon, p.zocalo);
			o.escalera(a, 11, cv - 2, Direction.SOUTH, 5, p.piedraEscalon, p.zocalo);
			// barandillas al borde de los huecos
			o.pon(a, 6, cv - 3, p.valla);
			o.pon(b, 11, cv + 3, p.valla);
			o.pon(a, 16, cv - 3, p.valla);
		}
		o.sillares(cu, 1, cv - 2, cu, 15, cv + 2, false);
		for (int dv = -2; dv <= 1; dv++) {
			o.pon(cu, 16, cv + dv, p.valla);
		}
		// puertas: abajo al patio y en el piso del adarve a las dos murallas
		o.puerta(cu - su * t, 1, cv - sv * r, su > 0 ? Direction.WEST : Direction.EAST);
		for (int d = 1; d <= 2; d++) {
			o.aire(cu - su * t, ALTO_MURO + 1, cv - sv * d, cu - su * t, ALTO_MURO + 2, cv - sv * d);
			o.aire(cu - su * d, ALTO_MURO + 1, cv - sv * t, cu - su * d, ALTO_MURO + 2, cv - sv * t);
		}
		// saeteras hacia fuera y ventanas en la sala de arriba
		for (int y : new int[] {7, 13}) {
			o.aire(cu + su * t, y, cv, cu + su * t, y + 1, cv);
			o.aire(cu, y, cv + sv * t, cu, y + 1, cv + sv * t);
		}
		ventana(o, cu + t, 17, cv);
		ventana(o, cu - t, 17, cv);
		ventana(o, cu, 17, cv + t);
		ventana(o, cu, 17, cv - t);
		// luz y trastos
		o.pon(cu - sa * 2, 4, cv, Obra.farolColgado());
		o.pon(cu + sa * 2, 9, cv, Obra.farolColgado());
		o.pon(cu - sa * 2, 14, cv, Obra.farolColgado());
		o.pon(cu, 19, cv, Obra.farolColgado());
		o.pon(cu - sa * 3, 1, cv - 1, Blocks.BARREL);
		o.pon(cu - sa * 3, 1, cv, Blocks.BARREL);
		o.pon(cu - sa * 3, 2, cv, Blocks.BARREL);
		o.pon(cu - sa * 3, 16, cv - 2, Blocks.BARREL);
		o.pon(cu - sa * 3, 16, cv + 2, Obra.mira(Blocks.CHEST, sa > 0 ? Direction.EAST : Direction.WEST));
		o.pon(cu - sa * 3, 16, cv, Blocks.FLETCHING_TABLE);
		// techo y remate
		o.caja(cu - r, 20, cv - r, cu + r, 20, cv + r, p.tablas);
		o.paredes(cu - t, 20, cv - t, cu + t, 20, cv + t, p.moldura);
		if (p.desierto) {
			o.caja(cu - t, 20, cv - t, cu + t, 20, cv + t, p.tejadoLleno);
			for (int d = -t; d <= t; d += 2) {
				o.pon(cu + d, 21, cv - t, p.moldura);
				o.pon(cu + d, 21, cv + t, p.moldura);
				o.pon(cu - t, 21, cv + d, p.moldura);
				o.pon(cu + t, 21, cv + d, p.moldura);
			}
			o.cupula(cu, cv, 21, 2);
			return;
		}
		o.chapitel(cu, cv, 20, t + 1);
	}

	/** La barbacana: paso abovedado de cinco con rastrillo y, encima, la sala de la guardia abierta hasta el tejado. */
	private void barbacana(Obra o) {
		Paleta p = o.p;
		int b = BARBACANA;
		o.sillares(-b, 1, FRENTE, b, 14, DENTRO, false);
		o.caja(-b + 1, ALTO_MURO, FRENTE + 1, b - 1, ALTO_MURO, DENTRO - 1, p.tablas);
		o.paredes(-b, ALTO_MURO, FRENTE, b, ALTO_MURO, DENTRO, p.moldura);
		o.aire(-b + 1, ALTO_MURO + 1, FRENTE + 1, b - 1, 14, DENTRO - 1);
		// el paso, con arco y el rastrillo a medio subir
		o.aire(-2, 1, FRENTE, 2, 5, DENTRO);
		for (int v = FRENTE; v <= DENTRO; v++) {
			o.pon(-2, 5, v, Obra.escalon(p.piedraEscalon, Direction.WEST, true));
			o.pon(2, 5, v, Obra.escalon(p.piedraEscalon, Direction.EAST, true));
			for (int u = -2; u <= 2; u++) {
				o.pon(u, 0, v, p.losa);
			}
		}
		o.caja(-2, 4, FRENTE + 1, 2, 5, FRENTE + 1, Blocks.IRON_BARS);
		o.pon(0, 5, DENTRO - 1, Obra.farolColgado());
		o.pon(0, 5, FRENTE + 3, Obra.farolColgado());
		// del adarve se pasa a la sala
		for (int a = MURO - 2; a <= MURO - 1; a++) {
			o.aire(-b, ALTO_MURO + 1, -a, -b, ALTO_MURO + 2, -a);
			o.aire(b, ALTO_MURO + 1, -a, b, ALTO_MURO + 2, -a);
		}
		int y = ALTO_MURO + 1;
		o.pon(-b + 1, y, FRENTE + 1, Blocks.BARREL);
		o.pon(-b + 1, y + 1, FRENTE + 1, Blocks.BARREL);
		o.pon(-b + 1, y, FRENTE + 2, Obra.mira(Blocks.CHEST, Direction.EAST));
		o.pon(b - 1, y, FRENTE + 1, Blocks.FLETCHING_TABLE);
		o.pon(b - 1, y, FRENTE + 2, Blocks.BARREL);
		o.mesa(-1, FRENTE + 3, 1, FRENTE + 3, y, true);
		o.caja(-2, y, DENTRO - 1, 2, y, DENTRO - 1, o.tinte("carpet", Blocks.RED_CARPET));
		// ventanas y estandartes de la fachada; pilares con farol a los lados de la entrada
		for (int s = -1; s <= 1; s += 2) {
			ventana(o, 3 * s, 12, FRENTE);
			ventana(o, 3 * s, 12, DENTRO);
			o.estandarte(5 * s, 9, FRENTE - 1, Direction.NORTH);
			o.pon(4 * s, 1, FRENTE - 1, p.muroBajo);
			o.pon(4 * s, 2, FRENTE - 1, p.muroBajo);
			o.pon(4 * s, 3, FRENTE - 1, Blocks.LANTERN);
		}
		if (p.desierto) {
			for (int u = -b; u <= b; u++) {
				for (int v = FRENTE; v <= DENTRO; v++) {
					boolean borde = Math.abs(u) == b || v == FRENTE || v == DENTRO;
					if (borde && Math.floorMod(u + v, 2) == 0) {
						o.pon(u, 15, v, p.moldura);
					}
				}
			}
			o.caja(-b + 1, 14, FRENTE + 1, b - 1, 14, DENTRO - 1, p.tejadoLleno);
			o.cupula(0, (FRENTE + DENTRO) / 2, 15, 2);
			o.pon(0, 13, (FRENTE + DENTRO) / 2, Obra.farolColgado());
			return;
		}
		o.dosAguas(-b - 1, FRENTE - 1, b + 1, DENTRO + 1, 15, true, p.piedra(o, 0, 15, FRENTE));
		o.pon(0, 20, (FRENTE + DENTRO) / 2, Obra.farolColgado());
		// rosetón en el hastial de delante
		int[][] roseton = {{0, 16}, {-1, 17}, {0, 17}, {1, 17}, {0, 18}};
		for (int[] c : roseton) {
			o.pon(c[0], c[1], FRENTE, Blocks.GLASS_PANE);
		}
	}

	// ---- el patio ----

	private void patio(Obra o) {
		Paleta p = o.p;
		for (int u = -PATIO; u <= PATIO; u++) {
			for (int v = -PATIO; v < DELANTE; v++) {
				o.pon(u, 0, v, p.adoquin(o, u, v));
			}
		}
		// el camino de losas de la puerta al palacio, con un anillo alrededor de la fuente
		for (int v = -PATIO; v < DELANTE; v++) {
			for (int u = -2; u <= 2; u++) {
				o.pon(u, 0, v, p.losa);
			}
		}
		for (int du = -6; du <= 6; du++) {
			for (int dv = -6; dv <= 6; dv++) {
				if (octogono(du, dv, 6)) {
					o.pon(du, 0, FV + dv, p.losa);
				}
			}
		}
		if (p.desierto) {
			Block[] azulejos = {Blocks.BLUE_GLAZED_TERRACOTTA, Blocks.ORANGE_GLAZED_TERRACOTTA, Blocks.CYAN_GLAZED_TERRACOTTA};
			for (int du = -8; du <= 8; du++) {
				for (int dv = -8; dv <= 8; dv++) {
					if (octogono(du, dv, 8) && !octogono(du, dv, 5)) {
						Direction d = Direction.from2DDataValue(Math.floorMod(du + dv, 4));
						o.pon(du, 0, FV + dv, Obra.mira(azulejos[Math.floorMod(Math.abs(du) + Math.abs(dv), 3)], d));
					}
				}
			}
		}
		fuente(o, 0, FV);
		// canteros con árboles a los lados, bancos mirando a la fuente y farolas por el camino
		for (int s = -1; s <= 1; s += 2) {
			cantero(o, 8 * s, -19, 13 * s, -14);
			cantero(o, 8 * s, -4, 13 * s, -1);
			o.arbol(s * 10, 1, -17, 5);
			o.arbol(s * 11, 1, -3, 4);
			Direction espalda = s > 0 ? Direction.EAST : Direction.WEST;
			for (int v = FV - 1; v <= FV + 1; v++) {
				o.silla(8 * s, 1, v, espalda);
			}
			o.pon(8 * s, 1, FV - 2, Obra.trampilla(p.trampilla, Direction.NORTH, false, true));
			o.pon(8 * s, 1, FV + 2, Obra.trampilla(p.trampilla, Direction.SOUTH, false, true));
			Direction brazo = s > 0 ? Direction.WEST : Direction.EAST;
			o.farola(4 * s, 1, -19, brazo);
			o.farola(6 * s, 1, -15, brazo);
			o.farola(6 * s, 1, -3, brazo);
		}
		pozo(o, -16, -5);
		campoDeTiro(o);
		herreria(o);
	}

	/** Cantero: bordillo de losas y dentro flores y matas. */
	private static void cantero(Obra o, int u1, int v1, int u2, int v2) {
		int ua = Math.min(u1, u2), ub = Math.max(u1, u2), va = Math.min(v1, v2), vb = Math.max(v1, v2);
		for (int u = ua; u <= ub; u++) {
			for (int v = va; v <= vb; v++) {
				if (u == ua || u == ub || v == va || v == vb) {
					o.pon(u, 0, v, o.p.zocalo);
					o.pon(u, 1, v, Obra.losa(o.p.piedraLosa, false));
				} else {
					o.parterre(u, 1, v);
				}
			}
		}
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

	/** Pozo de piedra con su tejadillo de madera sobre dos postes y un farol colgando. */
	private void pozo(Obra o, int cu, int cv) {
		Paleta p = o.p;
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				if (du == 0 && dv == 0) {
					o.caja(cu, -2, cv, cu, 0, cv, Blocks.WATER);
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
			o.pon(cu + du, 4, cv - 1, Obra.escalon(p.tejadoEscalon, Direction.SOUTH, false));
			o.pon(cu + du, 4, cv + 1, Obra.escalon(p.tejadoEscalon, Direction.NORTH, false));
			o.pon(cu + du, 4, cv, Obra.tronco(p.tronco, Direction.Axis.X));
			o.pon(cu + du, 5, cv, Obra.losa(p.remateLosa, false));
		}
		o.pon(cu, 3, cv, Obra.farolColgado());
	}

	/** Campo de tiro contra la muralla del oeste: dianas sobre heno y dos peleles de paja. */
	private void campoDeTiro(Obra o) {
		for (int v : new int[] {-18, -15, -12}) {
			o.pon(-PATIO, 1, v, Blocks.HAY_BLOCK);
			o.pon(-PATIO, 2, v, Blocks.TARGET);
		}
		for (int v : new int[] {-16, -12}) {
			o.pon(-17, 1, v, o.p.valla);
			o.pon(-17, 2, v, Blocks.HAY_BLOCK);
			o.pon(-17, 3, v, Obra.mira(Blocks.CARVED_PUMPKIN, Direction.WEST));
		}
		o.pon(-14, 1, -18, Blocks.BARREL);
		o.pon(-14, 1, -17, Blocks.BARREL);
		o.pon(-14, 2, -18, Blocks.FLETCHING_TABLE);
	}

	/** La herrería, un cobertizo a un agua contra la muralla del este, con yunque, fragua y piedra de afilar. */
	private void herreria(Obra o) {
		Paleta p = o.p;
		int u0 = 14, va = -19, vb = -14;
		for (int v = va; v <= vb; v++) {
			for (int u = u0; u <= PATIO; u++) {
				int k = u - u0;
				int y = 4 + k / 2;
				o.pon(u, y, v, k % 2 == 0 ? Obra.escalon(p.tejadoEscalon, Direction.EAST, false) : Obra.losa(p.tejadoLosa, true));
				if (u > u0) {
					o.pon(u, 0, v, Blocks.COBBLESTONE);
				}
			}
		}
		for (int v : new int[] {va, vb}) {
			o.caja(u0, 1, v, u0, 3, v, Obra.tronco(p.tronco, Direction.Axis.Y));
		}
		o.pon(PATIO, 1, -18, Obra.mira(Blocks.BLAST_FURNACE, Direction.WEST));
		o.pon(PATIO, 1, -17, Blocks.LAVA_CAULDRON);
		o.pon(PATIO, 1, -16, Obra.mira(Blocks.SMOKER, Direction.WEST));
		o.pon(PATIO, 1, -15, Blocks.BARREL);
		o.pon(18, 1, -17, Obra.mira(Blocks.ANVIL, Direction.NORTH));
		o.pon(17, 1, -15, Obra.mira(Blocks.GRINDSTONE, Direction.WEST));
		o.pon(16, 1, -18, Blocks.SMITHING_TABLE);
		o.pon(15, 1, -15, Obra.mira(Blocks.CHEST, Direction.EAST));
		o.pon(17, 3, -17, Obra.farolColgado());
	}

	// ---- a los lados del palacio ----

	/** Lado oeste: la casa de la entrada a las mazmorras y el huerto. */
	private void ladoOeste(Obra o) {
		Paleta p = o.p;
		for (int u = -PATIO; u <= -PALACIO - 1; u++) {
			for (int v = DELANTE; v <= DETRAS; v++) {
				o.pon(u, 0, v, p.adoquin(o, u, v));
			}
		}
		// la casa: piedra, tejado a dos aguas, puerta al pasillo y la escalera que baja
		int ua = -PATIO, ub = -16, va = 5, vb = 13;
		o.caja(ua, 0, va, ub, 0, vb, p.zocalo);
		o.caja(ua + 1, 0, va + 1, ub - 1, 0, vb - 1, p.losa);
		o.sillares(ua, 1, va, ub, 5, vb, true);
		o.aire(ua + 1, 1, va + 1, ub - 1, 5, vb - 1);
		o.dosAguas(ua - 1, va - 1, ub + 1, vb + 1, 5, true, p.piedra(o, ua, 6, va));
		o.puerta(ub, 1, 7, Direction.EAST);
		o.pon(ub, 3, 7, p.moldura);
		o.estandarte(ub + 1, 4, 9, Direction.EAST);
		o.pon(ub + 1, 1, 6, p.muroBajo);
		o.pon(ub + 1, 2, 6, Blocks.LANTERN);
		o.caja(ub, 2, 10, ub, 3, 11, Blocks.IRON_BARS);
		o.caja(ua, 2, 9, ua, 3, 9, Blocks.IRON_BARS);
		for (int u = -19; u <= -18; u++) {
			o.escalera(u, -5, 12, Direction.NORTH, 6, p.piedraEscalon, p.zocalo);
		}
		for (int v = 8; v <= 11; v++) {
			o.pon(-17, 1, v, Blocks.IRON_BARS);
			o.pon(-20, 1, v, Blocks.IRON_BARS);
		}
		o.pon(-19, 1, 11, Blocks.IRON_BARS);
		o.pon(-18, 1, 11, Blocks.IRON_BARS);
		o.pon(-18, 5, 9, Obra.farolColgado());
		o.pon(-20, 1, 6, Blocks.BARREL);
		o.pon(-20, 1, 12, Obra.mira(Blocks.CHEST, Direction.EAST));
		// el huerto, con su valla, espantapájaros y compostador
		int hu0 = -PATIO, hu1 = -16, hv0 = 15, hv1 = DETRAS - 1;
		Block[] cultivos = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};
		for (int u = hu0; u <= hu1; u++) {
			for (int v = hv0; v <= hv1; v++) {
				if (u == hu1 || v == hv0) {
					if (!(u == hu1 && v == hv0 + 3)) {
						o.pon(u, 1, v, p.valla);
					}
					continue;
				}
				o.pon(u, 0, v, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
				Block c = cultivos[Math.floorMod(u, cultivos.length)];
				BlockState planta = c == Blocks.BEETROOTS ? c.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3)
						: c.defaultBlockState().setValue(BlockStateProperties.AGE_7, 4 + o.azar(u, 1, v, 4));
				o.pon(u, 1, v, planta);
			}
		}
		o.pon(-18, 0, 18, Blocks.COARSE_DIRT);
		o.pon(-18, 1, 18, p.valla);
		o.pon(-18, 2, 18, Blocks.HAY_BLOCK);
		o.pon(-18, 3, 18, Obra.mira(Blocks.CARVED_PUMPKIN, Direction.EAST));
		o.pon(hu1 + 1, 1, hv0 + 1, Blocks.COMPOSTER);
	}

	/** El establo, en el lado este contra la muralla: cuadras con heno y bebedero bajo un tejado a dos aguas. */
	private void establo(Obra o) {
		Paleta p = o.p;
		for (int u = PALACIO + 1; u <= PATIO; u++) {
			for (int v = DELANTE; v <= DETRAS; v++) {
				o.pon(u, 0, v, p.adoquin(o, u, v));
			}
		}
		int u0 = 16, u1 = PATIO, v0 = 6, v1 = 19;
		for (int u = u0 + 1; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				o.pon(u, 0, v, o.azar(u, 0, v, 3) == 0 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
			}
		}
		for (int v : new int[] {v0, 10, 15, v1}) {
			o.caja(u0, 1, v, u0, 3, v, Obra.tronco(p.tronco, Direction.Axis.Y));
		}
		o.viga(u0, v0, u0, v1, 4);
		o.viga(u0, v0, u1, v0, 4);
		o.viga(u0, v1, u1, v1, 4);
		o.dosAguas(u0 - 1, v0 - 1, u1 + 1, v1 + 1, 5, true, p.tablas);
		for (int v : new int[] {10, 15}) {
			o.caja(u0 + 2, 1, v, u1, 1, v, p.valla);
		}
		for (int v : new int[] {7, 11, 16}) {
			o.pon(u1, 1, v, Blocks.HAY_BLOCK);
			o.pon(u1, 1, v + 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
			o.pon(u0, 3, v + 1, Obra.farolColgado());
		}
		o.pon(15, 1, 5, Blocks.HAY_BLOCK);
		o.pon(15, 2, 5, Blocks.HAY_BLOCK);
		o.pon(15, 1, 4, Blocks.BARREL);
		o.pon(15, 1, 20, Blocks.COMPOSTER);
	}

	// ---- el palacio ----

	private void palacio(Obra o) {
		Paleta p = o.p;
		int a = PALACIO;
		// planta baja de piedra
		o.sillares(-a, 1, DELANTE, a, PISOS[1] - 1, DETRAS, true);
		o.caja(-a, 0, DELANTE, a, 0, DETRAS, p.zocalo);
		o.caja(-a + 1, 0, DELANTE + 1, a - 1, 0, DETRAS - 1, p.losa);
		o.aire(-a + 1, 1, DELANTE + 1, a - 1, PISOS[1] - 1, DETRAS - 1);
		// los pisos de arriba, en voladizo por delante
		for (int f = 1; f < PISOS.length; f++) {
			o.caja(-a + 1, PISOS[f], DELANTE, a - 1, PISOS[f], DETRAS - 1, p.tablas);
		}
		if (p.desierto) {
			o.sillares(-a, PISOS[1], DELANTE, a, PISOS[3], DETRAS, true);
			o.aire(-a + 1, PISOS[1] + 1, DELANTE + 1, a - 1, PISOS[2] - 1, DETRAS - 1);
			o.aire(-a + 1, PISOS[2] + 1, DELANTE + 1, a - 1, PISOS[3] - 1, DETRAS - 1);
			o.paredes(-a, PISOS[1], DELANTE, a, PISOS[1], DETRAS, p.moldura);
			o.paredes(-a, PISOS[3], DELANTE, a, PISOS[3], DETRAS, p.moldura);
			for (int u = -a + 2; u <= a - 2; u += 3) {
				for (int f = 1; f <= 2; f++) {
					ventana(o, u, PISOS[f] + 2, DELANTE);
					ventana(o, u, PISOS[f] + 2, DETRAS);
				}
			}
			for (int v = DELANTE + 3; v <= DETRAS - 2; v += 3) {
				for (int f = 1; f <= 2; f++) {
					ventana(o, -a, PISOS[f] + 2, v);
					ventana(o, a, PISOS[f] + 2, v);
				}
			}
		} else {
			entramado(o, -a, DELANTE - 1, a, DETRAS);
			for (int u = -a; u <= a; u++) {
				o.pon(u, PISOS[1] - 1, DELANTE - 1, Obra.escalon(p.maderaEscalon, Direction.SOUTH, true));
			}
		}
		// la fachada de abajo: entrada en arco con porche, ventanas, estandartes y faroles bajo el voladizo
		o.aire(-1, 1, DELANTE, 1, 3, DELANTE);
		o.pon(-1, 3, DELANTE, Obra.escalon(p.piedraEscalon, Direction.WEST, true));
		o.pon(1, 3, DELANTE, Obra.escalon(p.piedraEscalon, Direction.EAST, true));
		o.pon(0, 4, DELANTE, p.moldura);
		porche(o);
		for (int s = -1; s <= 1; s += 2) {
			for (int u : new int[] {5, 8, 11}) {
				ventana(o, s * u, 2, DELANTE);
			}
			for (int v : new int[] {6, 10, 16, 19}) {
				ventana(o, s * a, 2, v);
			}
			o.estandarte(2 * s, 3, DELANTE - 1, Direction.NORTH);
			o.pon(7 * s, 4, DELANTE - 1, Obra.farolColgado());
			o.pon(11 * s, 4, DELANTE - 1, Obra.farolColgado());
		}
		tejado(o);
		salonDelTrono(o);
		cocina(o);
		comedor(o);
		primerPiso(o);
		segundoPiso(o);
		desvan(o);
		homenaje(o);
	}

	/** Porche delante de la entrada: dos postes y un tejadillo a un agua contra el voladizo. */
	private void porche(Obra o) {
		Paleta p = o.p;
		for (int u = -4; u <= 4; u++) {
			o.pon(u, 4, DELANTE - 4, Obra.escalon(p.tejadoEscalon, Direction.SOUTH, false));
			o.pon(u, 4, DELANTE - 3, Obra.losa(p.tejadoLosa, true));
			o.pon(u, 5, DELANTE - 2, Obra.escalon(p.tejadoEscalon, Direction.SOUTH, false));
			o.pon(u, 4, DELANTE - 2, Obra.escalon(p.maderaEscalon, Direction.NORTH, true));
			for (int v = DELANTE - 4; v < DELANTE; v++) {
				if (Math.abs(u) <= 3) {
					o.pon(u, 0, v, p.losa);
				}
			}
		}
		for (int s = -1; s <= 1; s += 2) {
			o.caja(3 * s, 1, DELANTE - 4, 3 * s, 3, DELANTE - 4, Obra.tronco(p.tronco, Direction.Axis.Y));
			o.pon(3 * s, 3, DELANTE - 3, Obra.escalon(p.maderaEscalon, Direction.SOUTH, true));
		}
		o.pon(0, 3, DELANTE - 3, Obra.farolColgado());
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

	/** El tejado del palacio: a dos aguas con buhardillas delante y chimeneas (en el desierto, azotea con cúpulas). */
	private void tejado(Obra o) {
		Paleta p = o.p;
		int a = PALACIO;
		if (p.desierto) {
			for (int u = -a; u <= a; u++) {
				for (int v = DELANTE; v <= DETRAS; v++) {
					if ((Math.abs(u) == a || v == DELANTE || v == DETRAS) && Math.floorMod(u + v, 2) == 0) {
						o.pon(u, PISOS[3] + 1, v, p.moldura);
					}
				}
			}
			for (int s = -1; s <= 1; s += 2) {
				o.cupula(s * 8, 8, PISOS[3] + 1, 2);
			}
		} else {
			o.dosAguas(-a - 1, DELANTE - 2, a + 1, DETRAS + 1, PISOS[3], false, p.enlucido);
			for (int s = -1; s <= 1; s += 2) {
				buhardilla(o, 8 * s);
			}
		}
		// las chimeneas de fuera de las del salón, la cocina y el comedor
		for (int s = -1; s <= 1; s += 2) {
			chimeneaFuera(o, s * (a + 1), 8, PISOS[3] + 9);
			chimeneaFuera(o, s * (a + 1), 17, PISOS[3] + 8);
		}
	}

	/** Buhardilla en el faldón de delante: frente de entramado con ventana y su tejadillo a dos aguas. */
	private void buhardilla(Obra o, int du) {
		Paleta p = o.p;
		int v0 = DELANTE - 1;
		int y = PISOS[3];
		o.aire(du - 1, y + 1, v0 + 1, du + 1, y + 5, v0 + 3);
		for (int u = du - 2; u <= du + 2; u++) {
			boolean poste = Math.abs(u - du) == 2;
			o.pon(u, y, v0, Obra.tronco(p.tronco, Direction.Axis.X));
			o.pon(u, y + 1, v0, poste ? Obra.tronco(p.tronco, Direction.Axis.Y) : Blocks.GLASS_PANE.defaultBlockState());
			o.pon(u, y + 2, v0, poste ? Obra.tronco(p.tronco, Direction.Axis.Y) : Blocks.GLASS_PANE.defaultBlockState());
		}
		for (int s = -1; s <= 1; s += 2) {
			o.caja(du + 2 * s, y, v0 + 1, du + 2 * s, y + 3, v0 + 3, p.enlucido);
		}
		o.dosAguas(du - 3, v0 - 1, du + 3, v0 + 4, y + 3, true, p.enlucido);
		o.pon(du, y + 2, v0 + 2, Obra.farolColgado());
	}

	/** Planta baja, delante: el salón del trono, con estrado, columnas, vigas, lámparas y dos chimeneas. */
	private void salonDelTrono(Obra o) {
		Paleta p = o.p;
		int techo = PISOS[1] - 1;
		// tabique con las alas de detrás (cocina y comedor), con una puerta a cada una
		for (int s = -1; s <= 1; s += 2) {
			o.sillares(s * (HU + 1), 1, HV0, s * (PALACIO - 1), techo, HV0, false);
			o.aire(s * 9, 1, HV0, s * 9, 2, HV0);
		}
		for (int v : new int[] {6, 10}) {
			o.viga(-PALACIO + 1, v, PALACIO - 1, v, techo);
		}
		for (int s = -1; s <= 1; s += 2) {
			o.pilar(s * 5, 6, 1, techo - 1);
			o.pilar(s * 5, 10, 1, techo - 1);
			for (int v : new int[] {6, 10}) {
				o.pon(s * 3, techo - 1, v, Obra.farolColgado());
				o.pon(s * 9, techo - 1, v, Obra.farolColgado());
			}
			o.chimenea(s * (PALACIO - 1), 1, 8, s > 0 ? Direction.WEST : Direction.EAST, PISOS[1]);
		}
		// alfombra hasta el estrado
		o.caja(-1, 1, DELANTE + 1, 1, 1, 9, o.tinte("carpet", Blocks.RED_CARPET));
		// el estrado con el trono, el respaldo dorado y los estandartes del reino detrás
		o.caja(-3, 1, 10, 3, 1, HV0 - 1, p.moldura);
		o.caja(-3, 2, 10, 3, 2, HV0 - 1, o.tinte("carpet", Blocks.RED_CARPET));
		o.silla(0, 2, 11, Direction.SOUTH);
		o.pon(0, 2, HV0 - 1, Blocks.GOLD_BLOCK);
		o.pon(0, 3, HV0 - 1, Blocks.GOLD_BLOCK);
		for (int s = -1; s <= 1; s += 2) {
			o.pon(s, 2, 11, Obra.escalon(p.maderaEscalon, s > 0 ? Direction.EAST : Direction.WEST, false));
			o.estandarte(2 * s, 4, HV0 - 1, Direction.NORTH);
			o.pon(3 * s, 3, HV0 - 1, Blocks.LANTERN);
		}
	}

	/** Ala oeste de abajo: la cocina, con su hogar, fogones, mesa de trabajo y despensa. */
	private void cocina(Obra o) {
		int v = DETRAS - 1;
		o.chimenea(-PALACIO + 1, 1, 17, Direction.EAST, PISOS[1]);
		o.pon(-12, 1, v, Blocks.HAY_BLOCK);
		o.pon(-11, 1, v, Blocks.BARREL);
		o.pon(-10, 1, v, Obra.mira(Blocks.SMOKER, Direction.NORTH));
		o.pon(-9, 1, v, Obra.mira(Blocks.FURNACE, Direction.NORTH));
		o.pon(-8, 1, v, Blocks.CRAFTING_TABLE);
		o.pon(-7, 1, v, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		o.pon(-6, 1, v, Blocks.BARREL);
		o.pon(-6, 2, v, Blocks.PUMPKIN);
		o.pon(-12, 1, 14, Blocks.BARREL);
		o.pon(-12, 2, 14, Blocks.MELON);
		o.mesa(-9, 16, -7, 17, 1, false);
		o.pon(-8, PISOS[1] - 1, 16, Obra.farolColgado());
	}

	/** Ala este de abajo: el comedor, con la mesa larga, chimenea, aparador y lámparas. */
	private void comedor(Obra o) {
		o.mesa(7, 16, 10, 17, 1, true);
		o.chimenea(PALACIO - 1, 1, 17, Direction.WEST, PISOS[1]);
		o.pon(8, PISOS[1] - 1, 16, Obra.farolColgado());
		o.pon(9, PISOS[1] - 1, 17, Obra.farolColgado());
		o.pon(7, 1, DETRAS - 1, Blocks.BARREL);
		o.pon(8, 1, DETRAS - 1, Blocks.BARREL);
		o.pon(7, 2, DETRAS - 1, Blocks.FLOWER_POT);
		o.pon(8, 2, DETRAS - 1, Obra.velas(3));
		o.pon(PALACIO - 1, 1, 14, Blocks.CHISELED_BOOKSHELF);
		o.pon(PALACIO - 1, 1, DETRAS - 1, Blocks.BOOKSHELF);
	}

	/** El tabique entre la parte de delante y las alas en un piso, con una puerta a cada ala. */
	private void tabique(Obra o, int suelo) {
		Paleta p = o.p;
		for (int s = -1; s <= 1; s += 2) {
			o.caja(s * (HU + 1), suelo + 1, HV0, s * (PALACIO - 1), suelo + 4, HV0, p.tablas);
			o.aire(s * 9, suelo + 1, HV0, s * 9, suelo + 2, HV0);
		}
	}

	/** Primer piso: la sala del consejo delante (mesa larga, biblioteca, mapas) y dos alcobas en las alas. */
	private void primerPiso(Obra o) {
		Paleta p = o.p;
		int y = PISOS[1] + 1;
		int techo = PISOS[2] - 1;
		tabique(o, PISOS[1]);
		o.caja(-4, y, 7, 4, y, 10, o.tinte("carpet", Blocks.RED_CARPET));
		o.mesa(-3, 8, 3, 9, y, true);
		o.silla(-4, y, 8, Direction.WEST);
		o.silla(4, y, 9, Direction.EAST);
		for (int v = DELANTE; v < HV0; v++) {
			Block estante = v % 3 == 0 ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF;
			o.caja(-PALACIO + 1, y, v, -PALACIO + 1, y + 2, v, estante);
		}
		o.pon(-PALACIO + 2, y, 8, Obra.mira(Blocks.LECTERN, Direction.EAST));
		o.pon(PALACIO - 1, y, 6, Blocks.CARTOGRAPHY_TABLE);
		o.pon(PALACIO - 1, y, 7, Blocks.BARREL);
		o.pon(PALACIO - 1, y + 1, 7, Blocks.FLOWER_POT);
		for (int u : new int[] {-7, 0, 7}) {
			o.pon(u, techo, 8, Obra.farolColgado());
		}
		// alcoba de la corte (oeste): dos camas, cofre y armario
		o.cama(-PALACIO + 1, y, 15, Direction.WEST, Blocks.WHITE_BED);
		o.cama(-PALACIO + 1, y, 17, Direction.WEST, Blocks.WHITE_BED);
		o.pon(-PALACIO + 1, y, 16, Blocks.BARREL);
		o.pon(-PALACIO + 1, y + 1, 16, Obra.velas(2));
		o.pon(-PALACIO + 1, y, DETRAS - 1, Obra.mira(Blocks.CHEST, Direction.EAST));
		o.caja(-9, y, DETRAS - 1, -8, y + 1, DETRAS - 1, Blocks.BARREL);
		o.caja(-10, y, 15, -9, y, 18, Blocks.BROWN_CARPET);
		o.pon(-9, techo, 17, Obra.farolColgado());
		// alcoba de invitados (este): cama del color del reino, escritorio y estantería
		o.cama(PALACIO - 1, y, 16, Direction.EAST, o.tinte("bed", Blocks.RED_BED));
		o.pon(PALACIO - 1, y, 15, Blocks.BARREL);
		o.pon(PALACIO - 1, y + 1, 15, Blocks.LANTERN);
		o.mesa(8, DETRAS - 1, 9, DETRAS - 1, y, false);
		o.silla(8, y, DETRAS - 2, Direction.NORTH);
		o.caja(11, y, DETRAS - 1, 12, y + 1, DETRAS - 1, Blocks.BOOKSHELF);
		o.pon(9, techo, 16, Obra.farolColgado());
	}

	/** Segundo piso: la cámara del señor delante (cama con dosel, chimenea), el tesoro y la capilla en las alas. */
	private void segundoPiso(Obra o) {
		Paleta p = o.p;
		int y = PISOS[2] + 1;
		int techo = PISOS[3] - 1;
		tabique(o, PISOS[2]);
		// cama con dosel del color del reino
		o.cama(-PALACIO + 1, y, 8, Direction.WEST, o.tinte("bed", Blocks.RED_BED));
		o.cama(-PALACIO + 1, y, 9, Direction.WEST, o.tinte("bed", Blocks.RED_BED));
		for (int u : new int[] {-PALACIO + 1, -PALACIO + 3}) {
			for (int v : new int[] {7, 10}) {
				o.caja(u, y, v, u, y + 2, v, p.valla);
			}
		}
		o.caja(-PALACIO + 1, y + 3, 7, -PALACIO + 3, y + 3, 10, o.tinte("wool", Blocks.RED_WOOL));
		o.caja(-9, y, 7, -6, y, 10, o.tinte("carpet", Blocks.RED_CARPET));
		o.pon(-PALACIO + 1, y, 6, Obra.mira(Blocks.CHEST, Direction.EAST));
		o.pon(-PALACIO + 1, y, 11, Blocks.BARREL);
		o.pon(-PALACIO + 1, y + 1, 11, Obra.velas(3));
		o.chimenea(PALACIO - 1, y, 8, Direction.WEST, PISOS[3]);
		o.silla(PALACIO - 3, y, 7, Direction.WEST);
		o.silla(PALACIO - 3, y, 9, Direction.WEST);
		o.mesa(4, DELANTE, 6, DELANTE, y, false);
		o.silla(5, y, DELANTE + 1, Direction.SOUTH);
		for (int u : new int[] {-6, 0, 7}) {
			o.pon(u, techo, 8, Obra.farolColgado());
		}
		// el tesoro (oeste): cofres, oro y vasijas
		for (int v = 14; v <= DETRAS - 1; v += 2) {
			o.pon(-PALACIO + 1, y, v, Obra.mira(Blocks.CHEST, Direction.EAST));
		}
		o.caja(-8, y, DETRAS - 1, -7, y, DETRAS - 1, Blocks.GOLD_BLOCK);
		o.pon(-8, y + 1, DETRAS - 1, Blocks.GOLD_BLOCK);
		o.pon(-6, y, DETRAS - 1, Obra.mira(Blocks.DECORATED_POT, Direction.NORTH));
		o.pon(-9, y, 14, Obra.mira(Blocks.DECORATED_POT, Direction.SOUTH));
		o.pon(-9, techo, 17, Obra.farolColgado());
		// la capilla (este): altar con velas, atril y bancos mirando al altar
		o.caja(PALACIO - 1, y, 16, PALACIO - 1, y, 17, p.moldura);
		o.pon(PALACIO - 1, y + 1, 16, Obra.velas(4));
		o.pon(PALACIO - 1, y + 1, 17, Obra.velas(3));
		o.estandarte(PALACIO - 1, y + 2, 15, Direction.WEST);
		o.pon(PALACIO - 2, y, 16, Obra.mira(Blocks.LECTERN, Direction.WEST));
		for (int u : new int[] {7, 9}) {
			for (int v = 15; v <= 19; v++) {
				o.silla(u, y, v, Direction.WEST);
			}
		}
		o.pon(9, techo, 17, Obra.farolColgado());
	}

	/** El desván, bajo el tejado: camastros de los criados, arcones, heno y trastos (en el desierto, la azotea). */
	private void desvan(Obra o) {
		if (o.p.desierto) {
			return;
		}
		int y = PISOS[3] + 1;
		for (int u : new int[] {-11, -9, -7}) {
			o.cama(u, y, 9, Direction.NORTH, Blocks.WHITE_BED);
			o.pon(u + 1, y, 9, Blocks.BARREL);
		}
		o.pon(-11, y, 12, Obra.mira(Blocks.CHEST, Direction.SOUTH));
		o.pon(-9, y, 12, Obra.mira(Blocks.CHEST, Direction.SOUTH));
		o.caja(-10, y, 13, -6, y, 13, Blocks.BROWN_CARPET);
		o.pon(7, y, 9, Blocks.HAY_BLOCK);
		o.pon(7, y + 1, 9, Blocks.HAY_BLOCK);
		o.pon(8, y, 9, Blocks.HAY_BLOCK);
		o.caja(10, y, 11, 11, y, 12, Blocks.BARREL);
		o.pon(10, y + 1, 11, Blocks.BARREL);
		o.pon(6, y, 14, Obra.mira(Blocks.CHEST, Direction.NORTH));
		o.pon(8, y, 14, Obra.mira(Blocks.DECORATED_POT, Direction.NORTH));
		o.pon(-11, y, 7, Blocks.LANTERN);
		o.pon(11, y, 7, Blocks.LANTERN);
		o.pon(0, y, 7, Blocks.LANTERN);
		for (int u : new int[] {-8, 0, 8}) {
			o.pon(u, PISOS[3] + 8, 12, Obra.farolColgado());
		}
		o.pon(-12, y + 1, 6, Blocks.COBWEB);
		o.pon(12, y, 18, Blocks.COBWEB);
	}

	// ---- la torre del homenaje ----

	/** Pisos de la escalera de la torre del homenaje: en los impares se llega por la banda oeste al fondo. */
	private static final int[] RELLANOS = {PISOS[1], PISOS[2], PISOS[3], 21, BALCON};

	/**
	 * La torre del homenaje, en medio del palacio por detrás: lleva la escalera de todos los pisos, en tramos de
	 * tres de ancho a los lados de un muro de en medio, con descansillos de lado a lado; arriba, la sala del
	 * balcón y el chapitel.
	 */
	private void homenaje(Obra o) {
		Paleta p = o.p;
		int v0 = HV0 + 1, v1 = DETRAS - 1;
		o.sillares(-HU, 1, HV0, HU, CIMA, DETRAS, true);
		o.aire(-HU + 1, 1, v0, HU - 1, CIMA, v1);
		for (int i = 0; i < RELLANOS.length; i++) {
			int piso = RELLANOS[i];
			o.caja(-HU + 1, piso, v0, HU - 1, piso, v1, p.tablas);
			// el hueco del tramo que llega y su barandilla
			boolean oeste = i % 2 == 0;
			for (int k = 1; k <= 3; k++) {
				int u = oeste ? -k : k;
				if (oeste) {
					o.aire(u, piso, i == 0 ? v0 : v0 + 1, u, piso, v1 - 2);
					if (i > 0) {
						o.pon(u, piso + 1, v0, p.valla);
					}
				} else {
					o.aire(u, piso, v0 + 2, u, piso, v1 - 1);
					o.pon(u, piso + 1, v1, p.valla);
				}
			}
		}
		o.pon(0, PISOS[1] + 1, v0, p.valla);
		o.sillares(0, 1, v0 + 1, 0, BALCON, v1 - 1, false);
		for (int dv = v0 + 1; dv <= v1 - 2; dv++) {
			o.pon(0, BALCON + 1, dv, p.valla);
		}
		// los tramos: el primero, de seis, desde la puerta; luego de cinco, alternando de banda
		for (int k = 1; k <= 3; k++) {
			o.escalera(-k, 1, v0, Direction.SOUTH, 6, p.piedraEscalon, p.zocalo);
			o.escalera(k, PISOS[1] + 1, v1 - 1, Direction.NORTH, 5, p.piedraEscalon, p.zocalo);
			o.escalera(-k, PISOS[2] + 1, v0 + 1, Direction.SOUTH, 5, p.piedraEscalon, p.zocalo);
			o.escalera(k, PISOS[3] + 1, v1 - 1, Direction.NORTH, 5, p.piedraEscalon, p.zocalo);
			o.escalera(-k, 22, v0 + 1, Direction.SOUTH, 5, p.piedraEscalon, p.zocalo);
		}
		// puertas: abajo a la cocina y al comedor; en cada piso, a sus salas; arriba, al balcón
		for (int s = -1; s <= 1; s += 2) {
			o.aire(s * HU, 1, v0, s * HU, 2, v0);
			o.aire(s * HU, PISOS[1] + 1, v1, s * HU, PISOS[1] + 2, v1);
			o.aire(s * HU, PISOS[2] + 1, v0, s * HU, PISOS[2] + 2, v0);
			o.aire(s * HU, PISOS[3] + 1, v1, s * HU, PISOS[3] + 2, v1);
			o.aire(s * HU, BALCON + 1, v1, s * HU, BALCON + 2, v1);
			ventana(o, s * HU, 23, HV);
			ventana(o, s * HU, BALCON + 3, HV);
		}
		o.aire(0, PISOS[2] + 1, HV0, 0, PISOS[2] + 2, HV0);
		o.aire(0, BALCON + 1, HV0, 0, BALCON + 2, HV0);
		ventana(o, 0, BALCON + 3, HV0);
		ventana(o, 0, PISOS[2] + 2, DETRAS);
		ventana(o, 0, 23, DETRAS);
		ventana(o, 0, BALCON + 3, DETRAS);
		// luz en los descansillos y barriles bajo el segundo tramo
		for (int i = 1; i < RELLANOS.length; i++) {
			o.pon(0, RELLANOS[i] - 1, i % 2 == 0 ? v0 : v1, Obra.farolColgado());
		}
		o.pon(0, PISOS[1] - 1, v1, Obra.farolColgado());
		for (int v = v0 + 2; v <= v1 - 1; v++) {
			o.pon(HU - 1, 1, v, Blocks.BARREL);
		}
		o.pon(HU - 1, 2, v0 + 3, Blocks.BARREL);
		// la sala de arriba
		int y = BALCON + 1;
		o.pon(HU - 1, y, v0 + 1, Blocks.CARTOGRAPHY_TABLE);
		o.pon(HU - 1, y, v0 + 2, Blocks.BARREL);
		o.pon(HU - 1, y, v0 + 4, Obra.mira(Blocks.CHEST, Direction.WEST));
		o.pon(1, CIMA, HV, Obra.farolColgado());
		// el balcón alrededor, sobre ménsulas y con barandilla
		int b = HU + 1;
		for (int du = -b; du <= b; du++) {
			for (int dv = -b; dv <= b; dv++) {
				if (Math.max(Math.abs(du), Math.abs(dv)) != b) {
					continue;
				}
				o.pon(du, BALCON, HV + dv, p.moldura);
				o.pon(du, BALCON + 1, HV + dv, p.valla);
				o.pon(du, BALCON + 2, HV + dv, Blocks.AIR);
				if (Math.abs(du) != Math.abs(dv)) {
					Direction adentro = Math.abs(du) == b ? (du > 0 ? Direction.WEST : Direction.EAST) : (dv > 0 ? Direction.NORTH : Direction.SOUTH);
					o.pon(du, BALCON - 1, HV + dv, Obra.escalon(p.piedraEscalon, adentro, true));
				}
			}
		}
		for (int d = -1; d <= 1; d += 2) {
			o.estandarte(2 * d, CIMA - 1, HV0 - 1, Direction.NORTH);
		}
		// cornisa y chapitel (en el desierto, azotea con cúpula)
		o.caja(-HU + 1, CIMA + 1, v0, HU - 1, CIMA + 1, v1, p.tablas);
		o.paredes(-HU, CIMA + 1, HV0, HU, CIMA + 1, DETRAS, p.moldura);
		if (p.desierto) {
			o.caja(-HU, CIMA + 1, HV0, HU, CIMA + 1, DETRAS, p.tejadoLleno);
			for (int d = -HU; d <= HU; d += 2) {
				o.pon(d, CIMA + 2, HV0, p.moldura);
				o.pon(d, CIMA + 2, DETRAS, p.moldura);
				o.pon(-HU, CIMA + 2, HV + d, p.moldura);
				o.pon(HU, CIMA + 2, HV + d, p.moldura);
			}
			o.cupula(0, HV, CIMA + 2, 3);
			return;
		}
		o.chapitel(0, HV, CIMA + 1, HU + 1);
	}

	// ---- bajo tierra ----

	/**
	 * Las mazmorras, bajo el lado oeste: la sala de la guardia al pie de la escalera, un pasillo con dos celdas de
	 * barrotes y, al fondo, la puerta a las catacumbas.
	 */
	private void mazmorras(Obra o) {
		Paleta p = o.p;
		int ua = -PATIO, ub = -PALACIO - 1, va = DELANTE, vb = DETRAS;
		o.sillares(ua, -6, va, ub, -1, vb, true);
		o.caja(ua + 1, -6, va + 1, ub - 1, -6, vb - 1, p.zocalo);
		for (int u = ua + 1; u <= ub - 1; u++) {
			for (int v = va + 1; v <= vb - 1; v++) {
				for (int y = -5; y <= -1; y++) {
					if (o.en(u, y, v) == null) {
						o.pon(u, y, v, Blocks.AIR);
					}
				}
			}
		}
		// la sala de la guardia: mesa, cofre y armas
		o.mesa(-16, 6, -15, 6, -5, true);
		o.pon(-20, -5, 6, Obra.mira(Blocks.CHEST, Direction.EAST));
		o.pon(-20, -5, 5 + 2, Blocks.BARREL);
		o.pon(-15, -5, 10, Blocks.GRINDSTONE);
		o.pon(-17, -1, 7, Obra.farolColgado());
		// las celdas, al oeste del pasillo: muros, barrotes y puerta de hierro
		for (int v : new int[] {13, 17}) {
			o.sillares(ua + 1, -5, v, -18, -1, v, false);
		}
		for (int c = 0; c < 2; c++) {
			int c0 = 14 + 4 * c;
			for (int v = c0; v <= c0 + 2; v++) {
				o.caja(-18, -5, v, -18, -2, v, Blocks.IRON_BARS);
			}
			BlockState puerta = Obra.mira(Blocks.IRON_DOOR, Direction.EAST);
			o.pon(-18, -5, c0 + 1, puerta.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
			o.pon(-18, -4, c0 + 1, puerta.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
			o.pon(-20, -5, c0, Blocks.HAY_BLOCK);
			o.pon(-20, -5, c0 + 2, Blocks.CAULDRON);
			o.pon(-19, -5, c0 + 2, Blocks.SKELETON_SKULL.defaultBlockState());
			o.pon(-20, -2, c0 + 1, Blocks.COBWEB);
		}
		for (int v : new int[] {15, 19}) {
			o.pon(-16, -1, v, Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
		}
		o.pon(-15, -5, DETRAS - 1, Obra.mira(Blocks.CHEST, Direction.WEST));
		// la puerta a las catacumbas
		o.aire(ub, -5, 17, ub + 1, -4, 17);
	}

	/** Las catacumbas, bajo el palacio: pilares, sarcófagos, calaveras en nichos, el altar y luz de almas. */
	private void catacumbas(Obra o) {
		Paleta p = o.p;
		int a = PALACIO;
		o.sillares(-a, -6, DELANTE, a, -1, DETRAS, true);
		o.caja(-a + 1, -6, DELANTE + 1, a - 1, -6, DETRAS - 1, p.zocalo);
		for (int u = -a + 1; u <= a - 1; u++) {
			for (int v = DELANTE + 1; v <= DETRAS - 1; v++) {
				for (int y = -5; y <= -1; y++) {
					if (o.en(u, y, v) == null) {
						o.pon(u, y, v, Blocks.AIR);
					}
				}
			}
		}
		for (int u : new int[] {-8, -4, 4, 8}) {
			for (int v : new int[] {8, 12, 16}) {
				o.sillares(u, -5, v, u, -1, v, false);
			}
		}
		// sarcófagos, con velas encima
		for (int s = -1; s <= 1; s += 2) {
			for (int v : new int[] {6, 10, 14}) {
				o.caja(s * 6, -5, v, s * 6, -5, v + 1, p.moldura);
				o.caja(s * 6, -4, v, s * 6, -4, v + 1, Obra.losa(p.piedraLosa, false));
			}
			o.pon(s * 6, -3, 10, Obra.velas(1 + (s > 0 ? 2 : 0)));
		}
		// calaveras en nichos a lo largo de las paredes
		for (int v = DELANTE + 2; v <= DETRAS - 2; v += 2) {
			o.pon(a - 1, -4, v, Obra.mira(Blocks.SKELETON_WALL_SKULL, Direction.WEST));
			if (v < 15 || v > 19) {
				o.pon(-a + 1, -4, v, Obra.mira(Blocks.SKELETON_WALL_SKULL, Direction.EAST));
			}
		}
		for (int u = -a + 3; u <= a - 3; u += 3) {
			o.pon(u, -4, DELANTE + 1, Obra.mira(Blocks.SKELETON_WALL_SKULL, Direction.SOUTH));
		}
		// el altar del fondo y la luz de almas
		o.pon(0, -5, DETRAS - 1, p.moldura);
		o.pon(0, -4, DETRAS - 1, Obra.velas(4));
		o.estandarte(0, -2, DETRAS - 1, Direction.NORTH);
		for (int v : new int[] {7, 11, 15, 19}) {
			o.pon(0, -1, v, Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
		}
		o.pon(a - 1, -1, DELANTE + 1, Blocks.COBWEB);
		o.pon(-a + 1, -1, DELANTE + 1, Blocks.COBWEB);
		o.pon(a - 1, -1, DETRAS - 1, Blocks.COBWEB);
		o.pon(-6, -5, 18, Obra.mira(Blocks.DECORATED_POT, Direction.NORTH));
	}
}
