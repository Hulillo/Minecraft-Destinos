package es.hulillo.destinos;

import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * El castillo de una capital rica (muralla de nivel 2), con forma de castillo de verdad: muralla gruesa con adarve
 * de cuatro de ancho que da la vuelta entera, cuatro torres en las esquinas y una puerta fuerte entre dos cubos, todo
 * comunicado por el adarve y con escaleras de ida y vuelta de tres de ancho hasta la sala de arriba, bajo un
 * chapitel con buhardillas. Al entrar, el patio de armas: el establo con sus caballos, la herrería con el herrero,
 * el campo de tiro, el pozo y las escaleras al adarve. Detrás de un murete, el patio de honor con la fuente, entre
 * las dos alas (galería de arcos abajo, cuartos de la guardia, botica, almacén y alcobas arriba) y al fondo el
 * palacio: el salón del trono a doble altura entre dos torres del homenaje con balcón, pasillos a los lados con
 * tribunas al salón, cocina, armería, comedor y capilla abajo, salitas, consejo y biblioteca en el primer piso, las
 * cámaras del rey y la reina, el tesoro y la sala de música en el segundo, y el desván de los criados. Bajo el ala
 * oeste, las mazmorras; bajo la capilla, la cripta. Los materiales van con la cultura y la madera de la capital
 * (Paleta), y los estandartes con el color del reino.
 *
 * Coordenadas locales (Obra): la puerta mira a v negativa.
 */
public final class CastilloCapital extends PiezaObra {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) CastilloCapital::new;

	/** La cara de fuera de la muralla; la muralla son los anillos de DENTRO + 1 a MURO. */
	private static final int MURO = 31;
	/** Hasta dónde se construye por dentro de la muralla. */
	private static final int DENTRO = MURO - 5;
	private static final int ALTO_MURO = 10;
	/** Medio lado de las torres de las esquinas. */
	private static final int TORRE = 5;
	private static final int[] PISOS_TORRE = {0, 5, 10, 15, 20};
	/** Los cubos de la puerta: centro en v y pisos. */
	private static final int CUBO_V = -31;
	private static final int[] PISOS_CUBO = {0, 5, 10, 15};
	/** La puerta: de la cara de fuera a la de dentro. */
	private static final int FRENTE = -33;
	private static final int TRAS_PUERTA = -27;
	/** El palacio: fachada y fondo. */
	private static final int FACHADA = 4;
	private static final int FONDO = DENTRO;
	/** Las torres del homenaje: centro en v, pisos, balcón y último piso. */
	private static final int HV = 8;
	private static final int[] PISOS_HOMENAJE = {0, 6, 12, 18, 24, 30, 36};
	private static final int BALCON = 30;
	private static final int CIMA = 36;
	/** Las alas, a los lados del patio de honor. */
	private static final int ALA = 14;
	private static final int ALA_V1 = -12;
	private static final int ALA_V2 = 3;

	static final int MEDIO = MURO + TORRE + 2;
	private static final int ALTO = 50;

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
		o.planta(-13, FRENTE - 3, 13, -MURO - 1);
		for (int su = -1; su <= 1; su += 2) {
			for (int sv = -1; sv <= 1; sv += 2) {
				o.planta(su * MURO - TORRE, sv * MURO - TORRE, su * MURO + TORRE, sv * MURO + TORRE);
			}
		}
		patioDeArmas(o);
		patioDeHonor(o);
		muralla(o);
		for (int su = -1; su <= 1; su += 2) {
			for (int sv = -1; sv <= 1; sv += 2) {
				torre(o, su, sv);
			}
		}
		puerta(o);
		for (int s = -1; s <= 1; s += 2) {
			cubo(o, s);
		}
		establo(o);
		herreria(o);
		for (int s = -1; s <= 1; s += 2) {
			ala(o, s);
		}
		palacio(o);
		for (int s = -1; s <= 1; s += 2) {
			homenaje(o, s);
		}
		mazmorras(o);
		cripta(o);
	}

	// ---- piezas sueltas ----

	private static void ventanas(Obra o, int u1, int y1, int v1, int u2, int y2, int v2) {
		o.caja(u1, y1, v1, u2, y2, v2, Blocks.GLASS_PANE);
	}

	private static boolean octogono(int du, int dv, int r) {
		return Math.max(Math.abs(du), Math.abs(dv)) <= r && Math.abs(du) + Math.abs(dv) <= r + r / 2;
	}

	private static Direction lado(int s) {
		return s > 0 ? Direction.EAST : Direction.WEST;
	}

	/** Pared recta de entramado de (u1, v1) a (u2, v2): postes cada tres y en las puntas, vigas en vigas y enlucido entre medias (en el desierto, sillares). */
	private static void muroEntramado(Obra o, int u1, int v1, int u2, int v2, int y1, int y2, int... vigas) {
		Paleta p = o.p;
		boolean enU = v1 == v2;
		int a = enU ? Math.min(u1, u2) : Math.min(v1, v2);
		int b = enU ? Math.max(u1, u2) : Math.max(v1, v2);
		for (int k = a; k <= b; k++) {
			int u = enU ? k : u1;
			int v = enU ? v1 : k;
			boolean poste = k == a || k == b || Math.floorMod(k, 3) == 0;
			for (int y = y1; y <= y2; y++) {
				boolean viga = false;
				for (int vy : vigas) {
					viga |= vy == y;
				}
				BlockState s;
				if (p.desierto) {
					s = p.piedra(o, u, y, v);
				} else if (poste) {
					s = Obra.tronco(p.tronco, Direction.Axis.Y);
				} else if (viga) {
					s = Obra.tronco(p.tronco, enU ? Direction.Axis.X : Direction.Axis.Z);
				} else {
					s = p.enlucido;
				}
				o.pon(u, y, v, s);
			}
		}
	}

	/** Tejado a dos aguas (Obra.dosAguas); en el desierto, azotea almenada. */
	private static void tejado(Obra o, int u1, int v1, int u2, int v2, int ly, boolean aLoLargoDeV, BlockState hastial, boolean atras) {
		if (!o.p.desierto) {
			o.dosAguas(u1, v1, u2, v2, ly, aLoLargoDeV, hastial, atras);
			return;
		}
		int ua = Math.min(u1, u2) + 1, ub = Math.max(u1, u2) - 1, va = Math.min(v1, v2) + 1, vb = Math.max(v1, v2) - 1;
		o.caja(ua, ly, va, ub, ly, vb, o.p.tejadoLleno);
		for (int u = ua; u <= ub; u++) {
			for (int v = va; v <= vb; v++) {
				if ((u == ua || u == ub || v == va || v == vb) && Math.floorMod(u + v, 2) == 0) {
					o.pon(u, ly + 1, v, o.p.moldura);
				}
			}
		}
	}

	/** Almenas sobre las paredes de una torre de medio lado t. */
	private static void almenas(Obra o, int cu, int cv, int t, int y) {
		for (int d = -t; d <= t; d += 2) {
			o.pon(cu + d, y, cv - t, o.p.piedra(o, cu + d, y, cv - t));
			o.pon(cu + d, y, cv + t, o.p.piedra(o, cu + d, y, cv + t));
			o.pon(cu - t, y, cv + d, o.p.piedra(o, cu - t, y, cv + d));
			o.pon(cu + t, y, cv + d, o.p.piedra(o, cu + t, y, cv + d));
		}
	}

	/** El remate de una torre: chapitel con una buhardilla en cada cara, o almenas en el desierto. */
	private static void remate(Obra o, int cu, int cv, int t, int ly) {
		if (o.p.desierto) {
			almenas(o, cu, cv, t, ly + 1);
			return;
		}
		o.chapitel(cu, cv, ly, t + 1);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			o.buhardilla(cu + d.getStepX() * t, cv + d.getStepZ() * t, d, ly + 1, 1);
		}
	}

	private static BlockState muela() {
		return Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
	}

	private static BlockState caldero() {
		return Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3);
	}

	private static BlockState farolAlmas() {
		return Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
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
	private static void fuente(Obra o, int cu, int cv) {
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
	private static void pozo(Obra o, int cu, int cv) {
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
		o.pon(cu + 1, 1, cv - 2, Blocks.BARREL);
	}

	// ---- los patios ----

	/** El patio de armas, entre la puerta y el patio de honor: empedrado, escaleras al adarve, campo de tiro y pozo. */
	private void patioDeArmas(Obra o) {
		Paleta p = o.p;
		for (int u = -DENTRO; u <= DENTRO; u++) {
			for (int v = -DENTRO; v < ALA_V1; v++) {
				o.pon(u, 0, v, p.adoquin(o, u, v));
			}
		}
		for (int u = -3; u <= 3; u++) {
			for (int v = -DENTRO; v < 0; v++) {
				o.pon(u, 0, v, p.losa);
			}
		}
		for (int s = -1; s <= 1; s += 2) {
			// escalera ancha al adarve pegada a la muralla de delante, con su pretil y el rellano arriba
			for (int v = -26; v <= -24; v++) {
				o.escalera(s * 14, 1, v, lado(s), 10, p.piedraEscalon, p.zocalo);
			}
			o.sillares(s * 24, 1, -26, s * 26, ALTO_MURO, -24, false);
			for (int i = 0; i < 10; i++) {
				int u = s * (14 + i);
				o.sillares(u, 1, -23, u, 1 + i, -23, false);
				o.pon(u, 2 + i, -23, p.muroBajo);
			}
			o.sillares(s * 24, 1, -23, s * 26, ALTO_MURO, -23, false);
			o.caja(s * 24, ALTO_MURO + 1, -23, s * 26, ALTO_MURO + 1, -23, p.muroBajo);
			// el murete entre los dos patios, con pilares y faroles a los lados del paso
			for (int x = 5; x <= 13; x++) {
				o.pon(s * x, 0, -13, p.zocalo);
				o.pon(s * x, 1, -13, p.piedra(o, s * x, 1, -13));
				o.pon(s * x, 2, -13, p.muroBajo);
			}
			o.sillares(s * 4, 1, -13, s * 4, 3, -13, false);
			o.pon(s * 4, 4, -13, Blocks.LANTERN);
			o.farola(s * 4, 1, -14, lado(-s));
		}
		// oeste: campo de tiro, con dianas sobre heno contra la muralla y dos peleles
		for (int v = -21; v <= -15; v += 3) {
			o.pon(-25, 1, v, Blocks.HAY_BLOCK);
			o.pon(-25, 2, v, Blocks.TARGET);
		}
		o.ser(SeresObra.PELELE, -18, 1, -20, Direction.EAST);
		o.ser(SeresObra.PELELE, -20, 1, -16, Direction.EAST);
		o.pon(-15, 1, -22, Blocks.FLETCHING_TABLE);
		o.pon(-16, 1, -22, Blocks.BARREL);
		o.pon(-16, 2, -22, Blocks.HAY_BLOCK);
		o.pon(-15, 1, -14, Blocks.BARREL);
		o.arbol(-24, 1, -14, 5);
		// este: el pozo, la leñera de la herrería, un carro de heno y un árbol
		pozo(o, 20, -18);
		for (int v = -22; v <= -20; v++) {
			o.caja(15, 1, v, 16, 2, v, Obra.tronco(p.tronco, Direction.Axis.Z));
		}
		o.caja(15, 3, -22, 16, 3, -20, Obra.losa(p.maderaLosa, false));
		o.pon(17, 1, -14, Blocks.HAY_BLOCK);
		o.pon(18, 1, -14, Blocks.HAY_BLOCK);
		o.pon(17, 2, -14, Blocks.HAY_BLOCK);
		o.pon(16, 1, -14, Obra.trampilla(p.trampilla, Direction.EAST, false, true));
		o.pon(19, 1, -14, Obra.trampilla(p.trampilla, Direction.WEST, false, true));
		o.arbol(24, 1, -15, 5);
	}

	/** El patio de honor, entre las alas: la fuente en un anillo de losas, canteros con árboles, bancos y el porche. */
	private void patioDeHonor(Obra o) {
		Paleta p = o.p;
		int fv = -6;
		for (int u = -ALA + 1; u <= ALA - 1; u++) {
			for (int v = ALA_V1; v < FACHADA; v++) {
				o.pon(u, 0, v, Math.abs(u) <= 3 ? p.losa : p.adoquin(o, u, v));
			}
		}
		for (int du = -6; du <= 6; du++) {
			for (int dv = -6; dv <= 6; dv++) {
				if (octogono(du, dv, 6)) {
					o.pon(du, 0, fv + dv, p.losa);
				}
			}
		}
		if (p.desierto) {
			Block[] azulejos = {Blocks.BLUE_GLAZED_TERRACOTTA, Blocks.ORANGE_GLAZED_TERRACOTTA, Blocks.CYAN_GLAZED_TERRACOTTA};
			for (int du = -6; du <= 6; du++) {
				for (int dv = -6; dv <= 6; dv++) {
					if (octogono(du, dv, 6) && !octogono(du, dv, 5)) {
						Direction d = Direction.from2DDataValue(Math.floorMod(du + dv, 4));
						o.pon(du, 0, fv + dv, Obra.mira(azulejos[Math.floorMod(Math.abs(du) + Math.abs(dv), 3)], d));
					}
				}
			}
		}
		fuente(o, 0, fv);
		for (int s = -1; s <= 1; s += 2) {
			cantero(o, 8 * s, -11, 12 * s, -8);
			cantero(o, 8 * s, -1, 12 * s, 2);
			o.arbol(s * 10, 1, -10, 5);
			o.arbol(s * 10, 1, 0, 4);
			Direction espalda = lado(s);
			for (int v = fv - 1; v <= fv + 1; v++) {
				o.silla(7 * s, 1, v, espalda);
			}
			o.pon(7 * s, 1, fv - 2, Obra.trampilla(p.trampilla, Direction.NORTH, false, true));
			o.pon(7 * s, 1, fv + 2, Obra.trampilla(p.trampilla, Direction.SOUTH, false, true));
			o.farola(12 * s, 1, -4, lado(-s));
		}
		porche(o);
	}

	/** El porche de la entrada del palacio: dos columnas y tejadillo a dos aguas con hastial de entramado. */
	private void porche(Obra o) {
		Paleta p = o.p;
		for (int u = -6; u <= 6; u++) {
			for (int v = 0; v < FACHADA; v++) {
				o.pon(u, 0, v, p.losa);
			}
		}
		for (int s = -1; s <= 1; s += 2) {
			o.caja(s * 6, 1, 1, s * 6, 4, 1, Obra.tronco(p.tronco, Direction.Axis.Y));
			o.estandarte(s * 4, 4, 3, Direction.NORTH);
			o.pon(s * 3, 1, 3, Blocks.POTTED_FLOWERING_AZALEA);
		}
		if (p.desierto) {
			tejado(o, -7, -1, 7, 4, 5, true, null, false);
			o.pon(0, 4, 1, Obra.farolColgado());
			return;
		}
		o.dosAguas(-7, 0, 7, 3, 5, true, p.enlucido, false);
		o.pon(0, 10, 2, Obra.farolColgado());
	}

	// ---- muralla, torres y puerta ----

	/** La muralla: cinco de grueso, adarve enlosado de cuatro con pretil por dentro, almenas, faroles y contrafuertes. */
	private void muralla(Obra o) {
		Paleta p = o.p;
		for (int u = -MURO - 1; u <= MURO + 1; u++) {
			for (int v = -MURO - 1; v <= MURO + 1; v++) {
				int anillo = Math.max(Math.abs(u), Math.abs(v));
				if (anillo <= DENTRO) {
					continue;
				}
				int largo = Math.abs(u) >= Math.abs(v) ? v : u;
				o.pon(u, 0, v, p.zocalo);
				if (anillo <= MURO) {
					o.sillares(u, 1, v, u, ALTO_MURO - 1, v, false);
					if (anillo == MURO) {
						o.pon(u, ALTO_MURO - 1, v, p.moldura);
						o.pon(u, ALTO_MURO, v, p.piedra(o, u, ALTO_MURO, v));
						if (Math.floorMod(largo, 2) == 0) {
							o.pon(u, ALTO_MURO + 1, v, p.piedra(o, u, ALTO_MURO + 1, v));
							o.pon(u, ALTO_MURO + 2, v, Obra.losa(p.piedraLosa, false));
						}
					} else {
						o.pon(u, ALTO_MURO, v, p.losa);
						if (anillo == DENTRO + 1) {
							o.pon(u, ALTO_MURO - 1, v, p.moldura);
							o.pon(u, ALTO_MURO + 1, v, p.muroBajo);
						} else if (anillo == MURO - 1 && Math.floorMod(largo, 12) == 6) {
							o.pon(u, ALTO_MURO + 1, v, p.valla);
							o.pon(u, ALTO_MURO + 2, v, Blocks.LANTERN);
						}
					}
				} else if (Math.floorMod(largo, 6) == 0) {
					o.sillares(u, 1, v, u, ALTO_MURO - 3, v, false);
					Direction afuera = Math.abs(u) > Math.abs(v) ? lado(u) : (v > 0 ? Direction.SOUTH : Direction.NORTH);
					o.pon(u, ALTO_MURO - 2, v, Obra.escalon(p.piedraEscalon, afuera.getOpposite(), false));
				} else {
					o.pon(u, 1, v, p.zocalo);
				}
			}
		}
		// el pretil se abre donde llegan las escaleras del patio de armas
		for (int s = -1; s <= 1; s += 2) {
			o.aire(s * 24, ALTO_MURO + 1, -DENTRO - 1, s * 25, ALTO_MURO + 1, -DENTRO - 1);
			o.aire(s * (DENTRO + 1), ALTO_MURO + 1, -25, s * (DENTRO + 1), ALTO_MURO + 1, -24);
		}
	}

	/**
	 * Torre de esquina, de 11 de lado: escalera de ida y vuelta de tres de ancho por los cinco pisos, puertas al adarve
	 * de las dos murallas, literas, armas y sala de mapas, y arriba el desván del chapitel con una buhardilla por cara.
	 * En local de la torre, a va hacia fuera en u y b hacia fuera en v; la escalera ocupa a de -2 a 4 y b de -4 a 2, y
	 * quedan libres para los muebles la franja a = -4..-3 y el fondo b = 3..4.
	 */
	private void torre(Obra o, int su, int sv) {
		Paleta p = o.p;
		int cu = su * MURO;
		int cv = sv * MURO;
		int t = TORRE;
		int r = t - 1;
		Direction ha = lado(su);
		Direction hb = sv > 0 ? Direction.SOUTH : Direction.NORTH;
		int arriba = PISOS_TORRE[PISOS_TORRE.length - 1] + 1;
		o.caja(cu - t, 0, cv - t, cu + t, 0, cv + t, p.zocalo);
		o.sillares(cu - t, 1, cv - t, cu + t, arriba, cv + t, true);
		o.aire(cu - r, 1, cv - r, cu + r, arriba - 1, cv + r);
		for (int f : PISOS_TORRE) {
			o.caja(cu - r, f, cv - r, cu + r, f, cv + r, p.tablas);
		}
		o.paredes(cu - t, ALTO_MURO, cv - t, cu + t, ALTO_MURO, cv + t, p.moldura);
		o.escaleraU(cu - 2 * su, cv - 4 * sv, hb, ha, 3, 2, 2, PISOS_TORRE, p.piedraEscalon, p.zocalo);
		// puertas de dos de ancho al adarve de las dos murallas
		o.aire(cu - t * su, ALTO_MURO + 1, cv - 3 * sv, cu - t * su, ALTO_MURO + 3, cv - 2 * sv);
		o.aire(cu - 3 * su, ALTO_MURO + 1, cv - t * sv, cu - 2 * su, ALTO_MURO + 3, cv - t * sv);
		// saeteras hacia fuera en los pisos de abajo y ventanas en la sala de arriba
		for (int f = 0; f <= 10; f += 5) {
			o.aire(cu + t * su, f + 2, cv, cu + t * su, f + 3, cv);
			o.aire(cu, f + 2, cv + t * sv, cu, f + 3, cv + t * sv);
		}
		int alta = PISOS_TORRE[3];
		for (int d = -1; d <= 1; d += 2) {
			ventanas(o, cu + d * t, alta + 2, cv, cu + d * t, alta + 3, cv);
			ventanas(o, cu, alta + 2, cv + d * t, cu, alta + 3, cv + d * t);
		}
		// muebles en la franja libre (a = -4..-3) y al fondo (b = 3..4), piso a piso
		int a4 = cu - 4 * su, a3 = cu - 3 * su;
		Direction alCentro = ha;
		o.pon(a4, 1, cv + 2 * sv, Blocks.BARREL);
		o.pon(a4, 1, cv + 3 * sv, Blocks.BARREL);
		o.pon(a4, 2, cv + 3 * sv, Blocks.BARREL);
		o.pon(a4, 1, cv + 4 * sv, Obra.mira(Blocks.CHEST, alCentro));
		o.pon(cu, 1, cv + 4 * sv, Blocks.HAY_BLOCK);
		int f1 = PISOS_TORRE[1] + 1;
		o.cama(a4, f1, cv + 4 * sv, hb, Blocks.WHITE_BED);
		o.cama(a3, f1, cv + 4 * sv, hb, Blocks.WHITE_BED);
		o.pon(a4, f1, cv + 2 * sv, Blocks.BARREL);
		o.pon(a4, f1 + 1, cv + 2 * sv, Obra.velas(2));
		o.pon(cu + 2 * su, f1, cv + 4 * sv, Obra.mira(Blocks.CHEST, hb.getOpposite()));
		int f2 = PISOS_TORRE[2] + 1;
		o.ser(SeresObra.GUARDIA, a4, f2, cv + 3 * sv, alCentro);
		o.pon(a4, f2, cv + 1 * sv, Blocks.FLETCHING_TABLE);
		o.pon(cu, f2, cv + 4 * sv, Blocks.BARREL);
		o.pon(cu + su, f2, cv + 4 * sv, Blocks.BARREL);
		int f3 = alta + 1;
		o.pon(a4, f3, cv + 2 * sv, Blocks.CARTOGRAPHY_TABLE);
		o.pon(a4, f3, cv + 3 * sv, Obra.mira(Blocks.LECTERN, alCentro));
		o.pon(a4, f3, cv + 4 * sv, Obra.mira(Blocks.CHEST, alCentro));
		o.silla(a3, f3, cv + 2 * sv, ha.getOpposite());
		o.estandarte(cu, f3 + 1, cv + 4 * sv, hb.getOpposite());
		for (int f : PISOS_TORRE) {
			if (f < arriba - 1) {
				o.pon(cu, f + 4, cv + 4 * sv, Obra.farolColgado());
			}
		}
		o.pon(a4, arriba, cv + 4 * sv, Blocks.LANTERN);
		remate(o, cu, cv, t, arriba);
	}

	/** La puerta: paso de cinco de ancho y seis de alto con rastrillo, entre dos cubos, y encima la sala de la guardia. */
	private void puerta(Obra o) {
		Paleta p = o.p;
		int fr = FRENTE, de = TRAS_PUERTA;
		o.caja(-4, 0, fr, 4, 0, de, p.zocalo);
		o.sillares(-4, 1, fr, 4, 14, de, false);
		// el paso, con arco a lo largo, el rastrillo a medio subir y faroles
		o.aire(-2, 1, fr, 2, 6, de);
		for (int v = fr; v <= de; v++) {
			o.pon(-2, 6, v, Obra.escalon(p.piedraEscalon, Direction.WEST, true));
			o.pon(2, 6, v, Obra.escalon(p.piedraEscalon, Direction.EAST, true));
			for (int u = -2; u <= 2; u++) {
				o.pon(u, 0, v, p.losa);
			}
		}
		o.caja(-2, 5, fr + 1, 2, 6, fr + 1, Blocks.IRON_BARS);
		o.pon(0, 6, -30, Obra.farolColgado());
		// la sala de la guardia, con puertas a los dos cubos
		o.caja(-3, ALTO_MURO, fr + 1, 3, ALTO_MURO, de - 1, p.tablas);
		o.aire(-3, ALTO_MURO + 1, fr + 1, 3, 14, de - 1);
		o.paredes(-4, ALTO_MURO, fr, 4, ALTO_MURO, de, p.moldura);
		for (int s = -1; s <= 1; s += 2) {
			o.aire(s * 3, 1, -28, s * 5, 3, -28);
			o.aire(s * 4, ALTO_MURO + 1, -29, s * 5, ALTO_MURO + 3, -28);
			ventanas(o, s * 2, 12, fr, s * 2, 13, fr);
			ventanas(o, s * 2, 12, de, s * 2, 13, de);
			o.estandarte(s * 3, 9, fr - 1, Direction.NORTH);
			o.estandarte(s * 3, 9, de + 1, Direction.SOUTH);
			o.pon(s * 4, 1, fr - 1, p.muroBajo);
			o.pon(s * 4, 2, fr - 1, p.muroBajo);
			o.pon(s * 4, 3, fr - 1, Blocks.LANTERN);
		}
		int y = ALTO_MURO + 1;
		o.mesa(-1, -31, 1, -31, y, true);
		o.pon(-3, y, fr + 1, Blocks.BARREL);
		o.pon(-3, y + 1, fr + 1, Blocks.BARREL);
		o.pon(-3, y, fr + 2, Blocks.BARREL);
		o.pon(3, y, fr + 1, Obra.mira(Blocks.CHEST, Direction.WEST));
		o.pon(3, y, fr + 2, Blocks.FLETCHING_TABLE);
		o.ser(SeresObra.GUARDIA, 3, y, -30, Direction.WEST);
		o.caja(-2, y, de - 1, 2, y, de - 1, o.tinte("carpet", Blocks.RED_CARPET));
		if (p.desierto) {
			tejado(o, -5, fr - 1, 5, de + 1, 15, true, null, true);
			o.cupula(0, -30, 16, 2);
			o.pon(0, 14, -30, Obra.farolColgado());
			return;
		}
		o.dosAguas(-5, fr - 1, 5, de + 1, 15, true, p.piedra(o, 0, 15, fr));
		o.pon(0, 18, -30, Obra.farolColgado());
		int[][] roseton = {{0, 16}, {-1, 17}, {0, 17}, {1, 17}, {0, 18}};
		for (int[] c : roseton) {
			o.pon(c[0], c[1], fr, Blocks.GLASS_PANE);
		}
	}

	/** Cubo de la puerta: escalera de ida y vuelta de tres de ancho del paso a la sala de la guardia y al adarve, y chapitel. */
	private void cubo(Obra o, int s) {
		Paleta p = o.p;
		int cu = s * 9, cv = CUBO_V, t = 4, r = 3;
		int arriba = PISOS_CUBO[PISOS_CUBO.length - 1] + 1;
		o.caja(cu - t, 0, cv - t, cu + t, 0, cv + t, p.zocalo);
		o.sillares(cu - t, 1, cv - t, cu + t, arriba, cv + t, true);
		o.aire(cu - r, 1, cv - r, cu + r, arriba - 1, cv + r);
		for (int f : PISOS_CUBO) {
			o.caja(cu - r, f, cv - r, cu + r, f, cv + r, p.tablas);
		}
		o.paredes(cu - t, ALTO_MURO, cv - t, cu + t, ALTO_MURO, cv + t, p.moldura);
		o.escaleraU(cu - 3 * s, cv + 3, Direction.NORTH, lado(s), 3, 2, 2, PISOS_CUBO, p.piedraEscalon, p.zocalo);
		// al adarve de fuera (las de dentro las abre la puerta)
		o.aire(cu + t * s, ALTO_MURO + 1, -29, cu + t * s, ALTO_MURO + 3, -28);
		for (int f = 0; f <= 10; f += 5) {
			o.aire(cu, f + 2, cv - t, cu, f + 3, cv - t);
		}
		for (int f : PISOS_CUBO) {
			if (f < arriba - 1) {
				o.pon(cu, f + 4, -28, Obra.farolColgado());
			}
		}
		o.pon(cu - 2 * s, arriba, cv - 3, Blocks.BARREL);
		o.pon(cu + 2 * s, arriba, cv - 3, Blocks.LANTERN);
		remate(o, cu, cv, t, arriba);
	}

	// ---- el patio de armas: establo y herrería ----

	/** El establo, contra la muralla: tres cuadras con su pesebre y bebedero, caballos y un burro, pasillo y pajar. */
	private void establo(Obra o) {
		Paleta p = o.p;
		int ua = -13, ub = -4, va = -DENTRO, vb = -16;
		for (int u = ua + 1; u <= ub - 1; u++) {
			for (int v = va + 1; v <= vb; v++) {
				o.pon(u, 0, v, v <= -20 ? Blocks.COARSE_DIRT.defaultBlockState() : p.adoquin(o, u, v));
			}
		}
		o.sillares(ua, 1, va, ub, 5, va, false);
		o.sillares(ua, 1, va, ua, 1, vb, false);
		o.sillares(ub, 1, va, ub, 1, vb, false);
		muroEntramado(o, ua, va, ua, vb, 2, 5, 5);
		muroEntramado(o, ub, va, ub, vb, 2, 5, 5);
		// la fachada abierta: postes y viga
		for (int u = ua; u <= ub; u += 3) {
			o.caja(u, 1, vb, u, 4, vb, Obra.tronco(p.tronco, Direction.Axis.Y));
		}
		o.viga(ua, vb, ub, vb, 5);
		// las cuadras: separaciones, frentes con cancela, pesebre de heno y bebedero
		for (int u : new int[] {-10, -7}) {
			o.caja(u, 1, va + 1, u, 2, -21, p.valla);
			o.caja(u, 1, -20, u, 4, -20, Obra.tronco(p.tronco, Direction.Axis.Y));
		}
		for (int c : new int[] {-12, -9, -6}) {
			o.pon(c, 1, -20, p.valla);
			o.pon(c + 1, 1, -20, o.portillo(Direction.SOUTH));
			o.pon(c, 1, va + 1, Blocks.HAY_BLOCK);
			o.pon(c + 1, 1, va + 1, caldero());
			o.pon(c, 3, va + 1, Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		}
		o.ser(SeresObra.CABALLO, -12, 1, -23, Direction.SOUTH);
		o.ser(SeresObra.CABALLO, -9, 1, -23, Direction.SOUTH);
		o.ser(SeresObra.BURRO, -6, 1, -23, Direction.SOUTH);
		// el pajar encima de las cuadras, con su escalera de mano
		o.caja(ua + 1, 5, va + 1, ub - 1, 5, -20, p.tablas);
		o.caja(-12, 1, -19, -12, 5, -19, Obra.mira(Blocks.LADDER, Direction.EAST));
		for (int u = -11; u <= -5; u++) {
			if (o.azar(u, 6, -24, 3) > 0) {
				o.pon(u, 6, -24, Blocks.HAY_BLOCK);
			}
			o.pon(u, 6, va + 1, Blocks.HAY_BLOCK);
		}
		o.pon(-5, 6, -22, Blocks.BARREL);
		// el pasillo: monturas, heno y luz
		o.pon(-5, 1, -19, Blocks.HAY_BLOCK);
		o.pon(-5, 2, -19, Blocks.HAY_BLOCK);
		o.pon(-5, 1, -18, Blocks.COMPOSTER);
		o.pon(-5, 1, -17, Obra.mira(Blocks.CHEST, Direction.WEST));
		o.pon(-11, 1, -17, Blocks.BARREL);
		o.pon(-12, 1, -17, caldero());
		o.pon(-12, 3, -18, Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		o.pon(-5, 3, -18, Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
		o.pon(-8, 4, vb, Obra.farolColgado());
		tejado(o, ua - 1, va, ub + 1, vb + 1, 6, false, p.enlucido, true);
		if (!p.desierto) {
			o.aguja(-8, 13, -21);
			// la viga del polipasto del pajar, con un farol
			o.pon(-8, 9, vb + 1, Obra.tronco(p.tronco, Direction.Axis.Z));
			o.pon(-8, 8, vb + 1, Obra.farolColgado());
		}
	}

	/** La herrería, contra la muralla: fragua con fuelle de horno, chimenea, yunques, pilón, armaduras y el herrero. */
	private void herreria(Obra o) {
		Paleta p = o.p;
		int ua = 4, ub = 13, va = -DENTRO, vb = -16;
		for (int u = ua + 1; u <= ub - 1; u++) {
			for (int v = va + 1; v <= vb; v++) {
				o.pon(u, 0, v, p.zocalo);
			}
		}
		o.sillares(ua, 1, va, ub, 5, va, false);
		o.sillares(ua, 1, va, ua, 5, vb, false);
		o.sillares(ub, 1, va, ub, 5, vb, false);
		for (int u : new int[] {7, 10}) {
			o.caja(u, 1, vb, u, 4, vb, Obra.tronco(p.tronco, Direction.Axis.Y));
		}
		o.viga(ua, vb, ub, vb, 5);
		// la fragua
		o.pon(7, 1, va + 1, Obra.mira(Blocks.BLAST_FURNACE, Direction.SOUTH));
		o.pon(8, 1, va + 1, Blocks.LAVA_CAULDRON);
		o.pon(9, 1, va + 1, Obra.mira(Blocks.BLAST_FURNACE, Direction.SOUTH));
		o.caja(7, 3, va + 1, 9, 3, va + 1, p.ladrillo);
		for (int u = 7; u <= 9; u++) {
			o.pon(u, 3, va + 2, Obra.escalon(p.ladrilloEscalon, Direction.NORTH, true));
		}
		// el taller
		o.pon(5, 1, va + 1, Blocks.SMITHING_TABLE);
		o.pon(6, 1, va + 1, Obra.mira(Blocks.CHEST, Direction.SOUTH));
		o.pon(10, 1, va + 1, caldero());
		o.pon(11, 1, va + 1, Blocks.COAL_BLOCK);
		o.pon(11, 2, va + 1, Blocks.COAL_BLOCK);
		o.pon(12, 1, va + 1, Blocks.BARREL);
		o.pon(12, 2, va + 1, Blocks.BARREL);
		o.pon(7, 1, -21, Obra.mira(Blocks.ANVIL, Direction.EAST));
		o.pon(10, 1, -21, Obra.mira(Blocks.CHIPPED_ANVIL, Direction.EAST));
		o.pon(5, 1, -23, muela());
		o.pon(5, 1, -21, Obra.mira(Blocks.STONECUTTER, Direction.EAST));
		o.pon(5, 1, -19, Blocks.BARREL);
		o.pon(5, 2, -19, Blocks.BARREL);
		o.pon(5, 1, -17, Blocks.CRAFTING_TABLE);
		o.ser(SeresObra.GUARDIA, 12, 1, -21, Direction.WEST);
		o.ser(SeresObra.MALLA, 12, 1, -19, Direction.WEST);
		o.ser(SeresObra.HERRERO, 8, 1, -22, Direction.NORTH);
		o.pon(12, 1, -17, Obra.trampilla(p.trampilla, Direction.WEST, false, true));
		tejado(o, ua - 1, va, ub + 1, vb + 1, 6, false, p.piedra(o, 0, 6, va), true);
		if (!p.desierto) {
			o.pon(6, 9, -21, Obra.farolColgado());
			o.pon(11, 9, -21, Obra.farolColgado());
		} else {
			o.pon(8, 5, -21, Obra.farolColgado());
		}
		// la chimenea de la fragua, por encima del tejado, con humo
		o.caja(8, 4, va + 1, 8, 15, va + 1, p.ladrillo);
		o.pon(8, 16, va + 1, Blocks.CAMPFIRE);
	}

	// ---- las alas ----

	/**
	 * Un ala a un lado del patio de honor (s: -1 oeste, 1 este), de |u| = ALA a DENTRO: abajo, galería de arcos al
	 * patio y dos cuartos (al oeste la guardia de las mazmorras, con la bajada, y el cuartel; al este el almacén y la
	 * botica); arriba, pasillo y dos alcobas, y una escalera al desván con buhardillas.
	 */
	private void ala(Obra o, int s) {
		Paleta p = o.p;
		int va = ALA_V1, vb = ALA_V2;
		Direction patio = lado(-s);
		for (int x = ALA + 1; x <= DENTRO - 1; x++) {
			for (int v = va + 1; v <= vb - 1; v++) {
				o.pon(s * x, 0, v, x <= 17 ? p.losa : p.tablas);
				o.pon(s * x, 6, v, p.tablas);
				o.pon(s * x, 12, v, p.tablas);
			}
		}
		o.paredes(s * ALA, 0, va, s * DENTRO, 0, vb, p.zocalo);
		o.sillares(s * ALA, 1, va, s * DENTRO, 5, va, false);
		o.sillares(s * ALA, 1, vb, s * DENTRO, 5, vb, false);
		o.sillares(s * DENTRO, 1, va, s * DENTRO, 5, vb, false);
		// la arcada al patio
		for (int v = va; v <= vb; v++) {
			boolean pilar = v == va || v == -8 || v == -4 || v == 0 || v == vb;
			if (pilar) {
				o.sillares(s * ALA, 1, v, s * ALA, 5, v, false);
				continue;
			}
			o.sillares(s * ALA, 4, v, s * ALA, 5, v, false);
			boolean pilarAntes = v - 1 == va || v - 1 == -8 || v - 1 == -4 || v - 1 == 0;
			boolean pilarDespues = v + 1 == -8 || v + 1 == -4 || v + 1 == 0 || v + 1 == vb;
			if (pilarAntes) {
				o.pon(s * ALA, 3, v, Obra.escalon(p.piedraEscalon, Direction.NORTH, true));
			} else if (pilarDespues) {
				o.pon(s * ALA, 3, v, Obra.escalon(p.piedraEscalon, Direction.SOUTH, true));
			}
		}
		muroEntramado(o, s * ALA, va, s * DENTRO, va, 6, 12, 6, 12);
		muroEntramado(o, s * ALA, vb, s * DENTRO, vb, 6, 12, 6, 12);
		muroEntramado(o, s * ALA, va, s * ALA, vb, 6, 12, 6, 12);
		muroEntramado(o, s * DENTRO, va, s * DENTRO, vb, 6, 12, 6, 12);
		// tabiques: la galería (|u| 15..17) y los cuartos (|u| 19..25), partidos en v = -4
		for (int f : new int[] {0, 6}) {
			o.caja(s * 18, f + 1, va + 1, s * 18, f + 5, vb - 1, p.tablas);
			o.caja(s * 19, f + 1, -4, s * 25, f + 5, -4, p.tablas);
		}
		// la galería: vigas sobre los pilares, faroles, y la puerta al patio de armas
		for (int v : new int[] {-8, -4, 0}) {
			o.viga(s * 15, v, s * 17, v, 5);
		}
		for (int v : new int[] {-10, -6, -2, 1}) {
			o.pon(s * 16, 5, v, Obra.farolColgado());
			o.pon(s * 16, 11, v, Obra.farolColgado());
		}
		o.aire(s * 15, 1, va, s * 17, 3, va);
		o.estandarte(s * 17, 4, va - 1, Direction.NORTH);
		o.caja(s * 16, 7, va + 1, s * 16, 7, vb - 1, o.tinte("carpet", Blocks.RED_CARPET));
		// puertas de los cuartos
		o.aire(s * 18, 1, -9, s * 18, 3, -7);
		o.aire(s * 18, 1, -1, s * 18, 3, 0);
		o.aire(s * 18, 7, -8, s * 18, 9, -7);
		o.aire(s * 18, 7, -1, s * 18, 9, 0);
		// ventanas: al patio y a la fachada
		for (int v : new int[] {-11, -8, -2, 1}) {
			ventanas(o, s * ALA, 8, v, s * ALA, 9, v + 1);
		}
		for (int x : new int[] {16, 19, 22}) {
			ventanas(o, s * x, 8, va, s * (x + 1), 9, va);
		}
		ventanas(o, s * 20, 2, va, s * 21, 3, va);
		ventanas(o, s * 23, 2, va, s * 24, 3, va);
		// la escalera al desván, en el cuarto de atrás del primer piso, con barandilla arriba
		o.escalera(s * 24, 7, vb - 1, patio, 6, p.maderaEscalon, p.tablas);
		for (int x = 20; x <= 22; x++) {
			o.pon(s * x, 13, vb - 2, p.valla);
		}
		if (s < 0) {
			guardiaMazmorras(o);
			cuartel(o);
		} else {
			almacen(o);
			botica(o);
		}
		alcobaAla(o, s, -11, -5);
		alcobaAla(o, s, -3, 1);
		// el desván: camastros y trastos
		int y = 13;
		o.cama(s * 23, y, -6, Direction.NORTH, Blocks.WHITE_BED);
		o.cama(s * 21, y, -6, Direction.NORTH, Blocks.WHITE_BED);
		o.pon(s * 22, y, -7, Blocks.BARREL);
		o.pon(s * 19, y, -10, Obra.mira(Blocks.CHEST, Direction.SOUTH));
		o.pon(s * 20, y, -10, Blocks.HAY_BLOCK);
		o.pon(s * 22, y, -2, Blocks.LANTERN);
		o.pon(s * 23, y, -10, Blocks.COBWEB);
		tejado(o, s * (ALA - 1), va - 1, s * DENTRO, vb, 12, true, p.enlucido, false);
		if (!p.desierto) {
			o.buhardilla(s * ALA, -8, patio, 13, 1);
			o.buhardilla(s * ALA, -1, patio, 13, 1);
		}
	}

	/** Alcoba del primer piso de un ala, de v1 a v2: cama, arcón, alfombra, mesita y luz. */
	private void alcobaAla(Obra o, int s, int v1, int v2) {
		int y = 7;
		boolean conEscalera = v2 >= 1;
		int vc = conEscalera ? v1 + 1 : v1 + 2;
		o.cama(s * 25, y, vc, lado(s), s > 0 ? o.tinte("bed", Blocks.RED_BED) : Blocks.BLUE_BED);
		o.pon(s * 25, y, vc + 1, Blocks.BARREL);
		o.pon(s * 25, y + 1, vc + 1, Obra.velas(2));
		o.pon(s * 25, y, v1, Obra.mira(Blocks.CHEST, lado(-s)));
		o.caja(s * 21, y, vc, s * 23, y, vc + 1, Blocks.BROWN_CARPET);
		if (!conEscalera) {
			o.mesa(s * 20, v2, s * 21, v2, y, false);
			o.silla(s * 20, y, v2 - 1, Direction.NORTH);
			o.caja(s * 23, y, v2, s * 24, y + 1, v2, Blocks.BOOKSHELF);
		}
		o.pon(s * 22, 11, vc, Obra.farolColgado());
	}

	/** Oeste, abajo delante: la guardia de las mazmorras, con la bajada de tres de ancho y su barandilla. */
	private void guardiaMazmorras(Obra o) {
		Paleta p = o.p;
		for (int v = -8; v <= -6; v++) {
			o.pon(-22, 1, v, p.valla);
		}
		for (int u = -25; u <= -23; u++) {
			o.pon(u, 1, -9, p.valla);
		}
		o.mesa(-21, -11, -20, -11, 1, false);
		o.silla(-21, 1, -10, Direction.NORTH);
		o.pon(-19, 1, -11, Obra.mira(Blocks.CHEST, Direction.SOUTH));
		o.ser(SeresObra.GUARDIA, -19, 1, -5, Direction.EAST);
		o.pon(-21, 1, -5, Blocks.BARREL);
		o.pon(-24, 5, -7, Obra.farolColgado());
		o.pon(-20, 5, -8, Obra.farolColgado());
	}

	/** Oeste, abajo detrás: el cuartel de la guardia, con literas, arcones y armero. */
	private void cuartel(Obra o) {
		for (int v : new int[] {-3, -1, 1}) {
			o.cama(-25, 1, v, Direction.WEST, Blocks.WHITE_BED);
		}
		o.pon(-25, 1, -2, Obra.mira(Blocks.CHEST, Direction.EAST));
		o.pon(-25, 1, 0, Obra.mira(Blocks.CHEST, Direction.EAST));
		o.mesa(-21, -2, -20, -1, 1, false);
		o.silla(-22, 1, -2, Direction.WEST);
		o.ser(SeresObra.MALLA, -19, 1, 2, Direction.NORTH);
		o.pon(-20, 1, 2, Blocks.BARREL);
		o.pon(-21, 1, 2, muela());
		o.pon(-22, 5, -1, Obra.farolColgado());
	}

	/** Este, abajo delante: el almacén, con barriles, sacos, arcones y vasijas. */
	private void almacen(Obra o) {
		for (int v = -11; v <= -5; v++) {
			o.pon(25, 1, v, Blocks.BARREL);
			if (v % 2 == 0) {
				o.pon(25, 2, v, Blocks.BARREL);
			}
		}
		o.pon(23, 1, -11, Blocks.HAY_BLOCK);
		o.pon(22, 1, -11, Blocks.HAY_BLOCK);
		o.pon(22, 2, -11, Blocks.HAY_BLOCK);
		o.pon(20, 1, -11, Obra.mira(Blocks.CHEST, Direction.SOUTH));
		o.pon(21, 1, -11, Obra.mira(Blocks.CHEST, Direction.SOUTH));
		o.pon(22, 1, -6, Obra.mira(Blocks.DECORATED_POT, Direction.NORTH));
		o.pon(21, 1, -6, Blocks.PUMPKIN);
		o.pon(20, 1, -5, Blocks.MELON);
		o.pon(22, 5, -8, Obra.farolColgado());
	}

	/** Este, abajo detrás: la botica, con alambiques, caldero, estantes de frascos y macetas. */
	private void botica(Obra o) {
		Paleta p = o.p;
		o.pon(25, 1, -3, Blocks.BREWING_STAND);
		o.pon(25, 1, -2, caldero());
		o.pon(25, 1, -1, Blocks.BREWING_STAND);
		o.caja(25, 1, 0, 25, 2, 2, Blocks.BOOKSHELF);
		o.mesa(21, -2, 22, -1, 1, false);
		o.pon(21, 2, -2, Blocks.BREWING_STAND);
		o.pon(20, 1, 2, Obra.mira(Blocks.LECTERN, Direction.NORTH));
		o.pon(22, 1, 2, Blocks.POTTED_FLOWERING_AZALEA);
		o.pon(23, 1, 2, Blocks.POTTED_RED_MUSHROOM);
		o.pon(24, 1, 2, Blocks.BARREL);
		o.pon(19, 1, 2, Obra.mira(Blocks.CHEST, Direction.NORTH));
		o.pon(19, 3, -3, Obra.trampilla(p.trampilla, Direction.SOUTH, false, true));
		o.pon(22, 5, -1, Obra.farolColgado());
	}

	// ---- el palacio ----

	/**
	 * El palacio, al fondo (v de FACHADA a FONDO, de muralla a muralla): planta baja de piedra y dos pisos de
	 * entramado, el salón del trono a doble altura en medio, pasillos a los lados (con tribunas al salón en el
	 * primer piso), cuartos delante y a los lados, la cámara del rey encima del salón y el desván bajo un tejado a
	 * dos aguas con un hastial en la fachada entre las dos torres del homenaje.
	 */
	private void palacio(Obra o) {
		Paleta p = o.p;
		int va = FACHADA, vb = FONDO, e = DENTRO;
		// suelos (el salón, a doble altura, sin el del primer piso)
		o.caja(-e + 1, 0, va + 1, e - 1, 0, vb - 1, p.tablas);
		o.caja(-6, 0, va + 1, 6, 0, vb - 1, p.losa);
		for (int f : new int[] {6, 12, 18}) {
			o.caja(-e + 1, f, va + 1, e - 1, f, vb - 1, p.tablas);
		}
		o.aire(-6, 6, va + 1, 6, 6, vb - 1);
		// paredes de fuera: piedra abajo y entramado arriba
		o.paredes(-e, 0, va, e, 0, vb, p.zocalo);
		o.sillares(-e, 1, va, e, 5, vb, true);
		muroEntramado(o, -e, va, e, va, 6, 18, 6, 12, 18);
		muroEntramado(o, -e, vb, e, vb, 6, 18, 6, 12, 18);
		muroEntramado(o, -e, va, -e, vb, 6, 18, 6, 12, 18);
		muroEntramado(o, e, va, e, vb, 6, 18, 6, 12, 18);
		// paredes de dentro: las del salón, de piedra, y los tabiques de los pasillos y los cuartos
		for (int s = -1; s <= 1; s += 2) {
			o.sillares(s * 7, 1, va + 1, s * 7, 17, vb - 1, false);
			for (int f : new int[] {0, 6, 12}) {
				o.caja(s * 11, f + 1, 13, s * 11, f + 5, vb - 1, p.tablas);
				o.caja(s * 12, f + 1, 13, s * (e - 1), f + 5, 13, p.tablas);
			}
		}
		o.caja(-6, 13, 14, 6, 17, 14, p.tablas);
		puertasPalacio(o);
		ventanasPalacio(o);
		salonDelTrono(o);
		pasillos(o);
		cocina(o);
		armeria(o);
		comedor(o);
		capilla(o);
		salita(o, 1);
		salita(o, -1);
		consejo(o);
		biblioteca(o);
		vestidor(o);
		tesoro(o);
		camaraReina(o);
		salaMusica(o);
		estudio(o);
		camaraRey(o);
		desvan(o);
		tejadoPalacio(o);
		// las chimeneas: hogares en cada piso y el cañón por encima del tejado
		for (int s = -1; s <= 1; s += 2) {
			hogares(o, s * (e - 1), 8, lado(-s), 0, 6, 12);
			if (s > 0) {
				hogares(o, s * (e - 1), 19, lado(-s), 0, 6, 12);
			} else {
				hogares(o, s * (e - 1), 19, lado(-s), 6, 12);
			}
		}
		o.chimenea(0, 13, vb - 1, Direction.NORTH, 18);
		o.caja(0, 18, vb - 1, 0, alturaTejado(vb - 1) + 3, vb - 1, p.ladrillo);
		o.pon(0, alturaTejado(vb - 1) + 4, vb - 1, Blocks.CAMPFIRE);
	}

	/** La altura del tejado del palacio sobre la fila v. */
	private static int alturaTejado(int v) {
		return 18 + Math.min(v - (FACHADA - 1), FONDO + 1 - v);
	}

	/** Un cañón de chimenea pegado a la pared: hogar en los pisos de pisos, ladrillo en el resto y humo encima del tejado. */
	private void hogares(Obra o, int u, int v, Direction mira, int... pisos) {
		Paleta p = o.p;
		for (int f = 0; f <= 12; f += 6) {
			boolean hogar = false;
			for (int h : pisos) {
				hogar |= h == f;
			}
			if (hogar) {
				o.chimenea(u, f + 1, v, mira, f + 6);
			} else {
				o.caja(u, f + 1, v, u, f + 5, v, p.ladrillo);
			}
			o.pon(u, f + 6, v, p.ladrillo);
		}
		int alto = alturaTejado(v) + 3;
		o.caja(u, 18, v, u, alto, v, p.ladrillo);
		o.pon(u, alto + 1, v, Blocks.CAMPFIRE);
	}

	private void puertasPalacio(Obra o) {
		Paleta p = o.p;
		// el portal: cinco de ancho y cinco de alto, con arco
		o.aire(-2, 1, FACHADA, 2, 5, FACHADA);
		o.pon(-2, 5, FACHADA, Obra.escalon(p.piedraEscalon, Direction.WEST, true));
		o.pon(2, 5, FACHADA, Obra.escalon(p.piedraEscalon, Direction.EAST, true));
		for (int s = -1; s <= 1; s += 2) {
			o.aire(s * 7, 1, 18, s * 7, 3, 19);
			for (int f : new int[] {0, 6, 12}) {
				o.aire(s * 11, f + 1, 16, s * 11, f + 3, 17);
				o.aire(s * 20, f + 1, 13, s * 21, f + 3, 13);
			}
			// a las alas, por la fachada (también la pared del ala)
			for (int f : new int[] {0, 6}) {
				o.aire(s * 16, f + 1, ALA_V2, s * 17, f + 3, FACHADA);
			}
			o.aire(s * 7, 13, 19, s * 7, 15, 20);
			// las tribunas del primer piso, que se asoman al salón
			for (int v0 : new int[] {14, 18, 22}) {
				o.aire(s * 7, 7, v0, s * 7, 9, v0 + 2);
				o.caja(s * 7, 7, v0, s * 7, 7, v0 + 2, p.valla);
			}
		}
		o.aire(-1, 13, 14, 1, 15, 14);
	}

	private void ventanasPalacio(Obra o) {
		int e = DENTRO;
		for (int s = -1; s <= 1; s += 2) {
			for (int f : new int[] {0, 6, 12}) {
				// en los lados, sin tapar los hogares
				ventanas(o, s * e, f + 2, 5, s * e, f + 3, 5);
				ventanas(o, s * e, f + 2, 10, s * e, f + 3, 11);
				ventanas(o, s * e, f + 2, 16, s * e, f + 3, 17);
				ventanas(o, s * e, f + 2, 22, s * e, f + 3, 23);
				// detrás y delante, en los cuartos
				ventanas(o, s * 16, f + 2, FONDO, s * 17, f + 3, FONDO);
				ventanas(o, s * 22, f + 2, FONDO, s * 23, f + 3, FONDO);
				ventanas(o, s * 19, f + 2, FACHADA, s * 20, f + 3, FACHADA);
				ventanas(o, s * 22, f + 2, FACHADA, s * 23, f + 3, FACHADA);
			}
			// el salón (altas, a los dos lados del trono y en la fachada) y la cámara del rey
			ventanas(o, s * 4, 8, FONDO, s * 5, 10, FONDO);
			ventanas(o, s * 4, 8, FACHADA, s * 5, 10, FACHADA);
			ventanas(o, s * 4, 14, FONDO, s * 5, 15, FONDO);
			ventanas(o, s * 1, 14, FACHADA, s * 2, 15, FACHADA);
			ventanas(o, s * 4, 14, FACHADA, s * 5, 15, FACHADA);
		}
	}

	/** El salón del trono: trece de ancho y veintiuno de largo, a doble altura, con columnas, arañas, estrado y dosel. */
	private void salonDelTrono(Obra o) {
		Paleta p = o.p;
		Block alfombra = o.tinte("carpet", Blocks.RED_CARPET);
		for (int s = -1; s <= 1; s += 2) {
			for (int v : new int[] {8, 12, 16}) {
				o.pilar(s * 4, v, 1, 10);
			}
			for (int v : new int[] {9, 13, 17, 21}) {
				o.estandarte(s * 6, 5, v, lado(-s));
			}
			// braseros y guardias
			o.pon(s * 6, 1, 6, p.moldura);
			o.pon(s * 6, 2, 6, Blocks.CAMPFIRE);
			o.pon(s * 6, 1, 20, p.moldura);
			o.pon(s * 6, 2, 20, Blocks.CAMPFIRE);
			o.ser(SeresObra.GUARDIA, s * 3, 1, 6, Direction.NORTH);
			o.ser(SeresObra.GUARDIA, s * 5, 2, 23, Direction.NORTH);
		}
		for (int v : new int[] {8, 12, 16, 20}) {
			o.viga(-6, v, 6, v, 11);
		}
		// arañas de vallas y faroles
		for (int v : new int[] {10, 14, 18}) {
			o.pon(0, 11, v, p.valla);
			o.caja(-1, 10, v, 1, 10, v, p.valla);
			o.caja(-1, 9, v, 1, 9, v, Obra.farolColgado());
		}
		o.caja(-1, 1, FACHADA + 1, 1, 1, 20, alfombra);
		// el estrado, de dos escalones, con alfombra
		for (int u = -5; u <= 5; u++) {
			o.pon(u, 1, 21, Obra.escalon(p.piedraEscalon, Direction.SOUTH, false));
		}
		o.caja(-5, 1, 22, 5, 1, FONDO - 1, p.moldura);
		o.caja(-5, 2, 22, 5, 2, 22, alfombra);
		for (int u = -3; u <= 3; u++) {
			o.pon(u, 2, 23, Obra.escalon(p.piedraEscalon, Direction.SOUTH, false));
		}
		o.caja(-3, 2, 24, 3, 2, FONDO - 1, p.moldura);
		o.caja(-3, 3, 24, 3, 3, 24, alfombra);
		// el trono, con brazos y respaldo dorado, bajo un dosel del color del reino
		o.silla(0, 3, 24, Direction.SOUTH);
		o.pon(-1, 3, 24, Obra.escalon(p.maderaEscalon, Direction.WEST, false));
		o.pon(1, 3, 24, Obra.escalon(p.maderaEscalon, Direction.EAST, false));
		o.pon(0, 3, FONDO - 1, Blocks.GOLD_BLOCK);
		o.pon(0, 4, FONDO - 1, Blocks.GOLD_BLOCK);
		for (int s = -1; s <= 1; s += 2) {
			o.caja(s * 3, 3, 23, s * 3, 6, 23, p.valla);
			o.caja(s * 3, 3, FONDO - 1, s * 3, 6, FONDO - 1, p.valla);
			o.estandarte(s * 2, 5, FONDO - 1, Direction.NORTH);
			o.pon(s * 5, 2, FONDO - 1, Blocks.LANTERN);
		}
		o.caja(-3, 7, 23, 3, 7, FONDO - 1, o.tinte("wool", Blocks.RED_WOOL));
	}

	/** Los pasillos a los lados del salón, en los tres pisos: alfombra, faroles, estandartes y macetas. */
	private void pasillos(Obra o) {
		Block alfombra = o.tinte("carpet", Blocks.RED_CARPET);
		for (int s = -1; s <= 1; s += 2) {
			for (int f : new int[] {0, 6, 12}) {
				o.caja(s * 9, f + 1, 13, s * 9, f + 1, FONDO - 1, alfombra);
				for (int v : new int[] {14, 20, 24}) {
					o.pon(s * 9, f + 5, v, Obra.farolColgado());
				}
				o.estandarte(s * 10, f + 3, 22, lado(-s));
				o.pon(s * 10, f + 1, FONDO - 1, Blocks.POTTED_FLOWERING_AZALEA);
				o.pon(s * 8, f + 1, FONDO - 1, Blocks.POTTED_FLOWERING_AZALEA);
			}
			o.ser(SeresObra.GUARDIA, s * 10, 1, 14, lado(-s));
		}
	}

	/** Este, abajo delante: la cocina, con su hogar, fogones, pila, mesa de trabajo, despensa y el gato. */
	private void cocina(Obra o) {
		int y = 1;
		o.pon(18, y, 5, Obra.mira(Blocks.SMOKER, Direction.SOUTH));
		o.pon(19, y, 5, Obra.mira(Blocks.FURNACE, Direction.SOUTH));
		o.pon(20, y, 5, caldero());
		o.pon(21, y, 5, Blocks.CRAFTING_TABLE);
		o.pon(22, y, 5, Blocks.BARREL);
		o.pon(22, y + 1, 5, Blocks.PUMPKIN);
		o.pon(23, y, 5, Blocks.BARREL);
		o.pon(24, y, 5, Obra.mira(Blocks.SMOKER, Direction.SOUTH));
		o.pon(25, y, 5, Blocks.BARREL);
		o.pon(25, y + 1, 5, Blocks.MELON);
		o.mesa(19, 8, 21, 9, y, false);
		for (int u = 16; u <= 19; u++) {
			o.pon(u, y, 12, Blocks.BARREL);
		}
		o.pon(17, y + 1, 12, Blocks.HAY_BLOCK);
		o.pon(22, y, 12, Obra.mira(Blocks.CHEST, Direction.NORTH));
		o.pon(23, y, 12, Blocks.BARREL);
		o.pon(24, y, 12, Blocks.COMPOSTER);
		o.pon(25, y, 11, Blocks.BARREL);
		o.ser(SeresObra.GATO, 22, y, 10, Direction.WEST);
		o.pon(20, 5, 7, Obra.farolColgado());
		o.pon(20, 5, 10, Obra.farolColgado());
	}

	/** Oeste, abajo delante: la armería, con armaduras en sus soportes, yunque, muela y arcones. */
	private void armeria(Obra o) {
		o.ser(SeresObra.GUARDIA, -18, 1, 5, Direction.SOUTH);
		o.ser(SeresObra.MALLA, -20, 1, 5, Direction.SOUTH);
		o.ser(SeresObra.ORO, -22, 1, 5, Direction.SOUTH);
		o.ser(SeresObra.MALLA, -24, 1, 5, Direction.SOUTH);
		o.pon(-24, 1, 11, Obra.mira(Blocks.ANVIL, Direction.NORTH));
		o.pon(-23, 1, 12, Obra.mira(Blocks.CHEST, Direction.NORTH));
		o.pon(-22, 1, 12, Obra.mira(Blocks.CHEST, Direction.NORTH));
		o.pon(-19, 1, 12, Blocks.BARREL);
		o.pon(-18, 1, 12, muela());
		o.pon(-17, 1, 12, Blocks.SMITHING_TABLE);
		o.estandarte(-25, 3, 6, Direction.EAST);
		o.estandarte(-25, 3, 11, Direction.EAST);
		o.pon(-20, 5, 8, Obra.farolColgado());
	}

	/** Este, abajo al lado: el comedor, con la mesa larga, chimenea, aparador y lámparas. */
	private void comedor(Obra o) {
		o.mesa(15, 19, 21, 20, 1, true);
		o.caja(14, 1, 17, 14, 1, 22, o.tinte("carpet", Blocks.RED_CARPET));
		for (int u = 13; u <= 16; u++) {
			o.pon(u, 1, FONDO - 1, Blocks.BARREL);
		}
		o.pon(13, 2, FONDO - 1, Blocks.FLOWER_POT);
		o.pon(15, 2, FONDO - 1, Obra.velas(3));
		o.pon(24, 1, FONDO - 1, Blocks.BOOKSHELF);
		o.pon(25, 1, FONDO - 1, Blocks.CHISELED_BOOKSHELF);
		o.pon(25, 1, 14, Blocks.POTTED_FLOWERING_AZALEA);
		o.pon(17, 5, 19, Obra.farolColgado());
		o.pon(20, 5, 20, Obra.farolColgado());
	}

	/** Oeste, abajo al lado: la capilla, con altar, vidrieras, atril y bancos; por ella se baja a la cripta. */
	private void capilla(Obra o) {
		Paleta p = o.p;
		o.caja(-24, 1, 18, -24, 1, 20, p.moldura);
		o.pon(-24, 2, 18, Obra.velas(3));
		o.pon(-24, 2, 19, Obra.velas(4));
		o.pon(-24, 2, 20, Obra.velas(2));
		o.estandarte(-25, 3, 17, Direction.EAST);
		o.estandarte(-25, 3, 21, Direction.EAST);
		o.pon(-22, 1, 19, Obra.mira(Blocks.LECTERN, Direction.EAST));
		for (int u : new int[] {-20, -18, -16}) {
			for (int v : new int[] {16, 17, 18, 20, 21, 22}) {
				o.silla(u, 1, v, Direction.EAST);
			}
		}
		Block vidriera = o.tinte("stained_glass_pane", Blocks.YELLOW_STAINED_GLASS_PANE);
		o.caja(-17, 2, FONDO, -16, 3, FONDO, vidriera);
		o.caja(-23, 2, FONDO, -22, 3, FONDO, vidriera);
		o.pon(-25, 1, 24, Blocks.POTTED_FLOWERING_AZALEA);
		o.pon(-25, 1, 14, Blocks.POTTED_FLOWERING_AZALEA);
		o.pon(-19, 5, 19, Obra.farolColgado());
		o.pon(-14, 5, 17, Obra.farolColgado());
	}

	/** Salita del primer piso delante (s: el lado): butacas junto al hogar, alfombra, estantes y macetas. */
	private void salita(Obra o, int s) {
		int y = 7;
		Direction alHogar = lado(s);
		o.caja(s * 20, y, 7, s * 23, y, 9, s > 0 ? o.tinte("carpet", Blocks.RED_CARPET) : Blocks.BROWN_CARPET);
		o.silla(s * 22, y, 7, lado(-s));
		o.silla(s * 22, y, 9, lado(-s));
		o.pon(s * 22, y, 8, Obra.trampilla(o.p.trampilla, alHogar, true, false));
		o.silla(s * 19, y, 8, lado(-s));
		o.caja(s * 18, y, 5, s * 18, y + 2, 5, Blocks.BOOKSHELF);
		o.caja(s * 24, y, 5, s * 25, y + 1, 5, Blocks.BOOKSHELF);
		o.pon(s * 25, y, 11, Blocks.POTTED_FLOWERING_AZALEA);
		o.pon(s * 23, y, 12, Blocks.BARREL);
		o.pon(s * 23, y + 1, 12, Obra.velas(3));
		o.pon(s * 21, 11, 8, Obra.farolColgado());
	}

	/** Este, primer piso al lado: la sala del consejo, con la mesa larga, mapas y estandartes. */
	private void consejo(Obra o) {
		int y = 7;
		o.caja(14, y, 17, 22, y, 22, o.tinte("carpet", Blocks.RED_CARPET));
		o.mesa(15, 19, 21, 20, y, true);
		o.silla(14, y, 19, Direction.WEST);
		o.silla(22, y, 20, Direction.EAST);
		o.pon(13, y, FONDO - 1, Blocks.CARTOGRAPHY_TABLE);
		o.pon(14, y, FONDO - 1, Obra.mira(Blocks.LECTERN, Direction.NORTH));
		o.caja(16, y, FONDO - 1, 21, y + 1, FONDO - 1, Blocks.BOOKSHELF);
		o.estandarte(13, y + 2, 14, Direction.SOUTH);
		o.estandarte(23, y + 2, 14, Direction.SOUTH);
		o.pon(16, 11, 19, Obra.farolColgado());
		o.pon(20, 11, 20, Obra.farolColgado());
	}

	/** Oeste, primer piso al lado: la biblioteca, con estanterías por las paredes, atriles y mesa de lectura. */
	private void biblioteca(Obra o) {
		int y = 7;
		for (int u = -24; u <= -13; u++) {
			Block estante = u % 3 == 0 ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF;
			o.caja(u, y, FONDO - 1, u, y + 3, FONDO - 1, estante);
		}
		for (int v = 14; v <= 23; v++) {
			if (v < 17 || v > 21) {
				o.caja(-25, y, v, -25, y + 3, v, Blocks.BOOKSHELF);
			}
		}
		for (int u : new int[] {-22, -18}) {
			o.caja(u, y, 16, u, y + 2, 21, Blocks.BOOKSHELF);
		}
		o.mesa(-16, 18, -15, 20, y, false);
		o.silla(-14, y, 19, Direction.EAST);
		o.pon(-20, y, 15, Obra.mira(Blocks.LECTERN, Direction.SOUTH));
		o.pon(-20, y, 22, Obra.mira(Blocks.LECTERN, Direction.NORTH));
		o.pon(-20, 11, 18, Obra.farolColgado());
		o.pon(-15, 11, 15, Obra.farolColgado());
	}

	/** Este, segundo piso delante: el vestidor y alcoba de invitados. */
	private void vestidor(Obra o) {
		int y = 13;
		o.cama(25, y, 11, Direction.EAST, o.tinte("bed", Blocks.RED_BED));
		o.pon(25, y, 12, Blocks.BARREL);
		o.pon(25, y + 1, 12, Blocks.LANTERN);
		o.ser(SeresObra.ORO, 18, y, 5, Direction.SOUTH);
		o.pon(20, y, 5, Obra.mira(Blocks.CHEST, Direction.SOUTH));
		o.pon(21, y, 5, Obra.mira(Blocks.CHEST, Direction.SOUTH));
		o.caja(23, y, 5, 24, y + 1, 5, Blocks.BARREL);
		o.caja(21, y, 9, 23, y, 11, Blocks.WHITE_CARPET);
		o.pon(20, 17, 9, Obra.farolColgado());
	}

	/** Oeste, segundo piso delante: el tesoro, con cofres, oro y vasijas. */
	private void tesoro(Obra o) {
		int y = 13;
		for (int u = -24; u <= -18; u += 2) {
			o.pon(u, y, 5, Obra.mira(Blocks.CHEST, Direction.SOUTH));
		}
		o.caja(-25, y, 11, -24, y, 12, Blocks.GOLD_BLOCK);
		o.pon(-25, y + 1, 12, Blocks.GOLD_BLOCK);
		o.pon(-23, y, 12, Obra.mira(Blocks.DECORATED_POT, Direction.NORTH));
		o.pon(-18, y, 12, Obra.mira(Blocks.DECORATED_POT, Direction.NORTH));
		o.pon(-17, y, 12, Blocks.BARREL);
		o.ser(SeresObra.GUARDIA, -17, y, 9, Direction.WEST);
		o.pon(-21, 17, 8, Obra.farolColgado());
	}

	/** Este, segundo piso al lado: la cámara de la reina, con cama con dosel, tocador y chimenea. */
	private void camaraReina(Obra o) {
		Paleta p = o.p;
		int y = 13;
		Block cama = o.tinte("bed", Blocks.RED_BED);
		o.cama(13, y, 23, Direction.WEST, cama);
		o.cama(13, y, 24, Direction.WEST, cama);
		for (int v : new int[] {22, 25}) {
			o.caja(12, y, v, 12, y + 2, v, p.valla);
			o.caja(14, y, v, 14, y + 2, v, p.valla);
		}
		o.caja(12, y + 3, 22, 14, y + 3, 25, o.tinte("wool", Blocks.RED_WOOL));
		o.caja(16, y, 21, 19, y, 24, o.tinte("carpet", Blocks.RED_CARPET));
		o.mesa(19, FONDO - 1, 20, FONDO - 1, y, false);
		o.silla(19, y, FONDO - 2, Direction.SOUTH);
		o.pon(21, y, FONDO - 1, Blocks.BARREL);
		o.pon(21, y + 1, FONDO - 1, Blocks.POTTED_FLOWERING_AZALEA);
		o.silla(23, y, 16, Direction.EAST);
		o.pon(13, y, 15, Obra.mira(Blocks.CHEST, Direction.SOUTH));
		o.pon(17, 17, 21, Obra.farolColgado());
	}

	/** Oeste, segundo piso al lado: la sala de música, con instrumentos, butacas y chimenea. */
	private void salaMusica(Obra o) {
		int y = 13;
		o.pon(-13, y, FONDO - 1, Blocks.JUKEBOX);
		for (int u = -18; u <= -15; u++) {
			o.pon(u, y, FONDO - 1, Blocks.NOTE_BLOCK);
		}
		o.caja(-22, y, 17, -19, y, 21, Blocks.BROWN_CARPET);
		for (int v : new int[] {17, 19, 21}) {
			o.silla(-23, y, v, Direction.WEST);
		}
		o.silla(-18, y, 19, Direction.EAST);
		o.pon(-13, y, 22, Blocks.POTTED_FLOWERING_AZALEA);
		o.pon(-24, y, 15, Blocks.BOOKSHELF);
		o.pon(-20, 17, 19, Obra.farolColgado());
	}

	/** El estudio del rey, encima de la entrada del salón: escritorio, mapas, estantes y las puertas de las torres. */
	private void estudio(Obra o) {
		int y = 13;
		o.caja(-3, y, 8, 3, y, 11, o.tinte("carpet", Blocks.RED_CARPET));
		o.mesa(-1, 6, 1, 6, y, false);
		o.silla(0, y, 7, Direction.SOUTH);
		o.pon(-3, y, 5, Blocks.CARTOGRAPHY_TABLE);
		o.pon(3, y, 5, Obra.mira(Blocks.LECTERN, Direction.SOUTH));
		o.caja(-6, y, 5, -6, y + 2, 8, Blocks.BOOKSHELF);
		o.caja(6, y, 5, 6, y + 2, 8, Blocks.BOOKSHELF);
		o.pon(-5, y, 13, Obra.mira(Blocks.CHEST, Direction.NORTH));
		o.pon(5, y, 13, Blocks.POTTED_FLOWERING_AZALEA);
		o.estandarte(-2, y + 2, 13, Direction.NORTH);
		o.estandarte(2, y + 2, 13, Direction.NORTH);
		o.pon(0, 17, 9, Obra.farolColgado());
	}

	/** La cámara del rey, encima del estrado: cama con dosel, chimenea, arcones y butacas. */
	private void camaraRey(Obra o) {
		Paleta p = o.p;
		int y = 13;
		Block cama = o.tinte("bed", Blocks.RED_BED);
		o.cama(6, y, 22, Direction.EAST, cama);
		o.cama(6, y, 23, Direction.EAST, cama);
		for (int v : new int[] {21, 24}) {
			o.caja(6, y, v, 6, y + 2, v, p.valla);
			o.caja(4, y, v, 4, y + 2, v, p.valla);
		}
		o.caja(4, y + 3, 21, 6, y + 3, 24, o.tinte("wool", Blocks.RED_WOOL));
		o.caja(-3, y, 18, 2, y, 23, o.tinte("carpet", Blocks.RED_CARPET));
		o.pon(6, y, 25, Obra.mira(Blocks.CHEST, Direction.WEST));
		o.pon(-6, y, 25, Obra.mira(Blocks.CHEST, Direction.EAST));
		o.silla(-2, y, 23, Direction.SOUTH);
		o.silla(2, y, 23, Direction.SOUTH);
		o.mesa(-6, 16, -6, 17, y, false);
		o.silla(-5, y, 16, Direction.EAST);
		o.pon(6, y, 16, Blocks.BARREL);
		o.pon(6, y + 1, 16, Obra.velas(3));
		o.pon(0, 17, 20, Obra.farolColgado());
	}

	/** El desván, bajo el tejado: camastros de los criados, arcones, heno y trastos (en el desierto, la azotea). */
	private void desvan(Obra o) {
		if (o.p.desierto) {
			return;
		}
		int y = 19;
		for (int s = -1; s <= 1; s += 2) {
			for (int u = 18; u <= 22; u += 2) {
				o.cama(s * u, y, 15, Direction.NORTH, Blocks.WHITE_BED);
			}
			o.pon(s * 17, y, 14, Blocks.BARREL);
			o.pon(s * 24, y, 17, Obra.mira(Blocks.CHEST, lado(-s)));
			o.pon(s * 13, y, 20, Blocks.HAY_BLOCK);
			o.pon(s * 13, y + 1, 20, Blocks.HAY_BLOCK);
			o.pon(s * 20, y, 20, Blocks.LANTERN);
			o.pon(s * 9, y, 16, Blocks.LANTERN);
			o.pon(s * 19, y + 2, 21, Blocks.COBWEB);
		}
		o.caja(-3, y, 16, 3, y, 16, Blocks.BARREL);
		o.pon(0, y + 1, 16, Obra.mira(Blocks.DECORATED_POT, Direction.NORTH));
	}

	/** El tejado del palacio: a dos aguas de muralla a muralla, con el hastial de la fachada, buhardillas y chimeneas. */
	private void tejadoPalacio(Obra o) {
		Paleta p = o.p;
		int e = DENTRO;
		if (p.desierto) {
			tejado(o, -e - 1, FACHADA - 1, e + 1, FONDO + 1, 18, false, null, true);
			for (int s = -1; s <= 1; s += 2) {
				o.cupula(s * 19, 17, 19, 2);
			}
			o.cupula(0, 18, 19, 3);
			return;
		}
		o.dosAguas(-e - 1, FACHADA - 1, e + 1, FONDO + 1, 18, false, p.enlucido);
		// el hastial de la fachada, entre las dos torres
		for (int u = -5; u <= 5; u++) {
			o.aire(u, 19, FACHADA, u, 18 + 6 - Math.abs(u), FACHADA + 4);
		}
		o.dosAguas(-6, FACHADA - 1, 6, FACHADA + 5, 18, true, p.enlucido, false);
		for (int s = -1; s <= 1; s += 2) {
			o.buhardilla(s * 5, FONDO, Direction.SOUTH, 19, 1);
			o.buhardilla(s * 19, FONDO, Direction.SOUTH, 19, 1);
			o.buhardilla(s * 20, FACHADA, Direction.NORTH, 19, 1);
		}
	}

	// ---- las torres del homenaje ----

	/**
	 * Torre del homenaje a un lado de la fachada (s), de 9 de lado: escalera de ida y vuelta de tres de ancho por los
	 * siete pisos, con puertas en cada rellano (al salón, a la tribuna sobre el salón, al estudio y al desván por
	 * dentro; al pasillo por detrás; a los cuartos de delante por fuera), balcón alrededor en el piso 30 y arriba el
	 * desván del chapitel con la campana.
	 */
	private void homenaje(Obra o, int s) {
		Paleta p = o.p;
		int cu = s * 11, cv = HV, t = 4, r = 3;
		int arriba = CIMA + 1;
		o.caja(cu - t, 0, cv - t, cu + t, 0, cv + t, p.zocalo);
		o.sillares(cu - t, 1, cv - t, cu + t, arriba, cv + t, true);
		o.aire(cu - r, 1, cv - r, cu + r, arriba - 1, cv + r);
		for (int f : PISOS_HOMENAJE) {
			o.caja(cu - r, f, cv - r, cu + r, f, cv + r, p.tablas);
		}
		for (int f : new int[] {18, 24}) {
			o.paredes(cu - t, f, cv - t, cu + t, f, cv + t, p.moldura);
		}
		o.escaleraU(s * 8, 11, Direction.NORTH, lado(s), 3, 2, 2, PISOS_HOMENAJE, p.piedraEscalon, p.zocalo);
		// puertas en los rellanos
		o.aire(s * 7, 1, 10, s * 7, 3, 11);
		o.aire(s * 7, 7, 10, s * 7, 9, 11);
		o.caja(s * 7, 7, 10, s * 7, 7, 11, p.valla);
		o.aire(s * 7, 13, 10, s * 7, 15, 11);
		o.aire(s * 7, 19, 10, s * 7, 21, 11);
		o.aire(s * 15, 19, 10, s * 15, 21, 11);
		for (int f : new int[] {0, 6, 12}) {
			o.aire(s * 8, f + 1, cv + t, s * 10, f + 3, cv + t);
			o.aire(s * 15, f + 1, 10, s * 15, f + 3, 11);
		}
		// ventanas en la fachada (encima de los descansillos) y estandartes
		for (int f : PISOS_HOMENAJE) {
			if (f < CIMA) {
				ventanas(o, cu, f + 4, cv - t, cu, f + 5, cv - t);
				o.pon(s * 13, f + 5, 11, Obra.farolColgado());
			}
		}
		o.estandarte(cu - 2, 10, cv - t - 1, Direction.NORTH);
		o.estandarte(cu + 2, 10, cv - t - 1, Direction.NORTH);
		// el balcón: tres de ancho alrededor, con ménsulas y antepecho, sin meterse en el tejado de detrás
		for (int du = -7; du <= 7; du++) {
			for (int dv = -7; dv <= 5; dv++) {
				int anillo = Math.max(Math.abs(du), Math.abs(dv));
				if (anillo < 5) {
					continue;
				}
				int u = cu + du, v = cv + dv;
				Direction aLaTorre = Math.abs(du) >= Math.abs(dv) ? lado(-du) : (dv > 0 ? Direction.NORTH : Direction.SOUTH);
				o.pon(u, BALCON, v, p.losa);
				o.pon(u, BALCON - 1, v, anillo == 7 ? Obra.escalon(p.piedraEscalon, aLaTorre, true) : p.piedra(o, u, BALCON - 1, v));
				if (anillo == 7 || dv == 5) {
					o.pon(u, BALCON + 1, v, p.muroBajo);
				}
			}
		}
		o.aire(s * 7, BALCON + 1, 10, s * 7, BALCON + 3, 11);
		o.aire(s * 15, BALCON + 1, 10, s * 15, BALCON + 3, 11);
		o.aire(s * 8, BALCON + 1, cv + t, s * 10, BALCON + 3, cv + t);
		o.pon(cu - 6 * s, BALCON + 1, cv - 6, Blocks.LANTERN);
		o.pon(cu + 6 * s, BALCON + 1, cv - 6, Blocks.LANTERN);
		// arriba: la campana en el descansillo y el chapitel con buhardillas
		o.pon(s * 9, arriba, cv - r, Blocks.BELL);
		o.pon(s * 13, arriba, cv - r, Blocks.LANTERN);
		remate(o, cu, cv, t, arriba);
	}

	// ---- bajo tierra ----

	/** Las mazmorras, bajo el ala oeste y el patio de armas: antesala de la guardia, pasillo y seis celdas con un preso. */
	private void mazmorras(Obra o) {
		Paleta p = o.p;
		int ua = -26, ub = -14, va = -27, vb = -11;
		o.sillares(ua, -8, va, ub, -1, vb, false);
		o.caja(ua + 1, -7, va + 1, ub - 1, -7, vb - 1, p.zocalo);
		o.aire(ua + 1, -6, va + 1, ub - 1, -2, vb - 1);
		// la bajada, de tres de ancho, desde el cuarto de la guardia del ala
		o.sillares(-26, -7, vb, -22, -1, -5, false);
		for (int u = -25; u <= -23; u++) {
			o.escalera(u, -6, vb, Direction.SOUTH, 6, p.piedraEscalon, p.zocalo);
		}
		// la antesala, cerrada por un muro con puerta de tres
		o.sillares(ua + 1, -6, -16, ub - 1, -2, -16, false);
		o.aire(-21, -6, -16, -19, -4, -16);
		o.mesa(-17, -13, -16, -13, -6, true);
		o.pon(-15, -6, -15, Obra.mira(Blocks.CHEST, Direction.WEST));
		o.pon(-15, -6, -12, Blocks.BARREL);
		o.pon(-15, -5, -12, Blocks.BARREL);
		o.ser(SeresObra.GUARDIA, -21, -6, -15, Direction.EAST);
		o.pon(-18, -2, -14, farolAlmas());
		// las celdas, a los dos lados del pasillo (u -21..-19)
		for (int v : new int[] {-23, -19}) {
			o.sillares(-25, -6, v, -22, -2, v, false);
			o.sillares(-18, -6, v, -15, -2, v, false);
		}
		int[][] filas = {{-26, -24}, {-22, -20}, {-18, -17}};
		for (int[] f : filas) {
			for (int u : new int[] {-22, -18}) {
				o.caja(u, -6, f[0], u, -3, f[1], Blocks.IRON_BARS);
				BlockState puerta = Obra.mira(Blocks.IRON_DOOR, u == -22 ? Direction.EAST : Direction.WEST);
				int vp = f[1];
				o.pon(u, -6, vp, puerta.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
				o.pon(u, -5, vp, puerta.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
				int fondo = u == -22 ? -25 : -15;
				o.pon(fondo, -6, f[0], Blocks.HAY_BLOCK);
				o.pon(fondo, -6, f[0] + 1, Blocks.CAULDRON);
				o.pon(fondo, -3, f[0], Blocks.COBWEB);
			}
		}
		o.pon(-24, -6, -25, Blocks.SKELETON_SKULL.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 4));
		o.ser(SeresObra.PRESO, -24, -6, -21, Direction.EAST);
		for (int v : new int[] {-25, -21}) {
			o.pon(-20, -2, v, farolAlmas());
		}
	}

	/** La cripta, bajo la capilla: pilares, sarcófagos, calaveras en nichos, el altar y luz de almas. */
	private void cripta(Obra o) {
		Paleta p = o.p;
		int ua = -26, ub = -11, va = 13, vb = 26;
		o.sillares(ua, -8, va, ub, -1, vb, false);
		o.caja(ua + 1, -7, va + 1, ub - 1, -7, vb - 1, p.zocalo);
		o.aire(ua + 1, -6, va + 1, ub - 1, -2, vb - 1);
		for (int u = -14; u <= -12; u++) {
			o.escalera(u, -6, 24, Direction.NORTH, 6, p.piedraEscalon, p.zocalo);
		}
		for (int v = 19; v <= 21; v++) {
			o.pon(-15, 1, v, p.valla);
		}
		for (int u = -14; u <= -12; u++) {
			o.pon(u, 1, 22, p.valla);
		}
		for (int u : new int[] {-22, -18}) {
			for (int v : new int[] {17, 21}) {
				o.sillares(u, -6, v, u, -2, v, false);
			}
		}
		for (int v : new int[] {15, 23}) {
			for (int u : new int[] {-23, -19}) {
				o.caja(u, -6, v, u + 1, -6, v, p.moldura);
				o.caja(u, -5, v, u + 1, -5, v, Obra.losa(p.piedraLosa, false));
			}
		}
		o.pon(-20, -4, 23, Obra.velas(2));
		o.caja(-25, -6, 18, -25, -6, 20, p.moldura);
		o.pon(-25, -5, 19, Obra.velas(4));
		o.estandarte(-25, -3, 19, Direction.EAST);
		for (int u = -24; u <= -16; u += 2) {
			o.pon(u, -4, va + 1, Obra.mira(Blocks.SKELETON_WALL_SKULL, Direction.SOUTH));
		}
		for (int u = -24; u <= -16; u += 2) {
			o.pon(u, -4, vb - 1, Obra.mira(Blocks.SKELETON_WALL_SKULL, Direction.NORTH));
		}
		for (int v : new int[] {16, 22}) {
			o.pon(-20, -2, v, farolAlmas());
		}
		o.pon(-15, -2, 19, farolAlmas());
		o.pon(-25, -2, va + 1, Blocks.COBWEB);
		o.pon(-12, -2, va + 1, Blocks.COBWEB);
		o.pon(-16, -6, 15, Obra.mira(Blocks.DECORATED_POT, Direction.NORTH));
	}
}
