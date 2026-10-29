package es.hulillo.destinos;

import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * La plaza del mercado, en medio de la plaza mayor de la capital (la más importante de la ciudad): una cuadrícula de
 * parcelas de césped con bordillo, separadas por calles empedradas, con una fuente en medio y en cada parcela un puesto
 * (de toldo, de verduras, de flores o un carro) mirando hacia la fuente, un corral con ganado o un árbol con bancos.
 * Es una pieza propia, como el castillo: allana su suelo y quita las casas del pueblo que pisa, así que sale siempre
 * entera. Cuanto más rica la capital, más grande: 3×3 parcelas en las pobres, 3×3 con seto y paseo alrededor en las
 * normales y 5×5 con seto, paseo y arcos en las entradas en las ricas.
 */
public final class PlazaMercado extends PiezaObra {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) PlazaMercado::new;
	/** Lado de una parcela y ancho de las calles de entre medias. */
	private static final int PARCELA = 7;
	private static final int CALLE = 3;
	private static final int PASO = PARCELA + CALLE;
	/** El paseo y el seto de alrededor (normales y ricas). */
	private static final int BORDE = 4;
	private static final int ALTO = 11;
	/** Lo que lleva una parcela. */
	private static final int CORRAL = PuestoMercado.TIPOS;
	private static final int ARBOL = PuestoMercado.TIPOS + 1;

	private final int nivel;

	PlazaMercado(int cx, int cz, int y0, int rot, int estilo, int madera, int color, int nivel) {
		super(TIPO, cx, cz, y0, rot, estilo, madera, color, medio(nivel) + TALUD, ALTO);
		this.nivel = nivel;
	}

	private PlazaMercado(CompoundTag t) {
		super(TIPO, t);
		nivel = t.getIntOr("nivel", 1);
	}

	int nivel() {
		return nivel;
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "plaza_mercado"), TIPO);
	}

	/** Medio lado de la plaza según la riqueza de la capital. */
	static int medio(int nivel) {
		return extremo(nivel) + (nivel > 0 ? BORDE : 0);
	}

	/** Parcelas por lado. */
	private static int lado(int nivel) {
		return nivel == 2 ? 5 : 3;
	}

	/** Hasta dónde llegan las parcelas desde el centro. */
	private static int extremo(int nivel) {
		return lado(nivel) / 2 * PASO + PARCELA / 2;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		super.addAdditionalSaveData(ctx, t);
		t.putInt("nivel", nivel);
	}

	@Override
	protected void construye(Obra o) {
		int m = medio(nivel);
		int ext = extremo(nivel);
		int n = lado(nivel) / 2;
		o.planta(-m, -m, m, m);
		// el suelo: calles empedradas con una raya de losa por el medio
		for (int u = -m; u <= m; u++) {
			for (int v = -m; v <= m; v++) {
				o.pon(u, 0, v, empedrado(o, u, v));
			}
		}
		// las parcelas: la del centro, la fuente; las de alrededor, puestos y de vez en cuando un corral o un árbol
		int k = 0;
		for (int i = -n; i <= n; i++) {
			for (int j = -n; j <= n; j++) {
				int cu = i * PASO;
				int cv = j * PASO;
				if (i == 0 && j == 0) {
					fuente(o, cu, cv);
					continue;
				}
				parcela(o, cu, cv);
				// hacia la fuente: en las diagonales, mirando a la calle que va de delante a atrás
				Direction frente = Math.abs(i) > Math.abs(j) ? (i > 0 ? Direction.WEST : Direction.EAST)
						: (j > 0 ? Direction.NORTH : Direction.SOUTH);
				boolean anillo = Math.max(Math.abs(i), Math.abs(j)) == 1;
				int tipo = tipo(o, k++, anillo);
				if (tipo == CORRAL) {
					corral(o, cu, cv, frente, k);
				} else if (tipo == ARBOL) {
					arbolConBancos(o, cu, cv);
				} else {
					o.incrusta(PuestoMercado.construye(tipo, estilo, madera, color, Geo.hash(cx + cu, cz + cv, 7451)), cu, cv, giro(frente));
					flores(o, cu, cv);
				}
			}
		}
		// farolas en los cruces de calles
		for (int i = -n; i < n; i++) {
			for (int j = -n; j < n; j++) {
				int u = i * PASO + PASO / 2;
				int v = j * PASO + PASO / 2;
				if (i == -1 && j == -1 || i == 0 && j == 0 || i == -1 && j == 0 || i == 0 && j == -1) {
					// los cuatro cruces junto a la fuente, con bancos en vez de farola
					banco(o, u, v);
					continue;
				}
				o.farola(u, 1, v, Direction.NORTH);
			}
		}
		if (nivel > 0) {
			seto(o, ext, m);
		}
	}

	/** El tipo de una parcela: casi todo puestos; en el anillo de fuera de las ricas, también corrales y árboles. */
	private int tipo(Obra o, int k, boolean anillo) {
		int h = o.azar(k, 3, k * 7, 12);
		if (!anillo && h < 3) {
			return h == 0 ? ARBOL : CORRAL;
		}
		if (anillo && h == 0 && nivel == 0) {
			return CORRAL;
		}
		return Math.floorMod(k + o.azar(0, 9, 0, PuestoMercado.TIPOS), PuestoMercado.TIPOS);
	}

	/** El giro que lleva el mostrador de un puesto (que mira a su norte) hacia frente. */
	static int giro(Direction frente) {
		return switch (frente) {
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
			default -> 0;
		};
	}

	private BlockState empedrado(Obra o, int u, int v) {
		boolean raya = Math.floorMod(u + PASO / 2, PASO) == 0 || Math.floorMod(v + PASO / 2, PASO) == 0;
		int h = o.azar(u, 0, v, 10);
		Block b = switch (estilo) {
			case AdornoCapital.DESIERTO -> raya ? Blocks.CUT_SANDSTONE : h < 7 ? Blocks.SMOOTH_SANDSTONE : Blocks.SANDSTONE;
			case AdornoCapital.SABANA -> raya ? Blocks.MUD_BRICKS : h < 7 ? Blocks.PACKED_MUD : Blocks.COARSE_DIRT;
			case AdornoCapital.NIEVE -> raya ? Blocks.POLISHED_DIORITE : h < 6 ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE;
			case AdornoCapital.TAIGA -> raya ? Blocks.POLISHED_ANDESITE : h < 5 ? Blocks.COBBLESTONE : h < 8 ? Blocks.MOSSY_COBBLESTONE
					: Blocks.STONE_BRICKS;
			default -> raya ? Blocks.POLISHED_ANDESITE : h < 6 ? Blocks.STONE_BRICKS : h < 9 ? Blocks.COBBLESTONE : Blocks.ANDESITE;
		};
		return b.defaultBlockState();
	}

	/** Una parcela de PARCELA × PARCELA: bordillo de piedra y césped (arena en el desierto). */
	private static void parcela(Obra o, int cu, int cv) {
		int r = PARCELA / 2;
		for (int du = -r; du <= r; du++) {
			for (int dv = -r; dv <= r; dv++) {
				boolean borde = Math.abs(du) == r || Math.abs(dv) == r;
				o.pon(cu + du, 0, cv + dv, borde ? o.p.zocalo : (o.p.desierto ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState());
			}
		}
	}

	/** Flores y arbustos en el césped que deja libre el puesto. */
	static void flores(Obra o, int cu, int cv) {
		int r = PARCELA / 2 - 1;
		for (int du = -r; du <= r; du++) {
			for (int dv = -r; dv <= r; dv++) {
				if (o.vacio(cu + du, 1, cv + dv) && o.azar(cu + du, 1, cv + dv, 3) == 0) {
					o.parterre(cu + du, 1, cv + dv);
				}
			}
		}
	}

	/** La fuente del centro: pilón de agua con bordillo de peldaños y una columna con los estandartes y la campana. */
	static void fuente(Obra o, int cu, int cv) {
		Paleta p = o.p;
		int r = PARCELA / 2;
		for (int du = -r; du <= r; du++) {
			for (int dv = -r; dv <= r; dv++) {
				int a = Math.max(Math.abs(du), Math.abs(dv));
				o.pon(cu + du, 0, cv + dv, p.zocalo);
				if (a == r) {
					// el bordillo: peldaños hacia fuera, para sentarse, y en las esquinas un bloque con maceta
					if (Math.abs(du) == r && Math.abs(dv) == r) {
						o.pon(cu + du, 1, cv + dv, p.moldura);
						o.pon(cu + du, 2, cv + dv, Blocks.POTTED_RED_TULIP);
					} else {
						Direction fuera = Math.abs(du) == r ? (du > 0 ? Direction.EAST : Direction.WEST) : (dv > 0 ? Direction.SOUTH : Direction.NORTH);
						o.pon(cu + du, 1, cv + dv, Obra.escalon(p.piedraEscalon, fuera.getOpposite(), false));
					}
				} else if (a > 0) {
					o.pon(cu + du, 1, cv + dv, Blocks.WATER);
				}
			}
		}
		// la columna: basa, fuste, capitel con los estandartes y encima un templete con la campana
		o.pon(cu, 1, cv, p.moldura);
		o.caja(cu, 2, cv, cu, 4, cv, p.muroBajo);
		o.caja(cu - 1, 5, cv - 1, cu + 1, 5, cv + 1, p.moldura);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			o.estandarte(cu + d.getStepX() * 2, 5, cv + d.getStepZ() * 2, d);
		}
		campanario(o, cu, 6, cv);
	}

	/**
	 * Un templete de campana sobre (cu, ly, cv): cuatro postes en las esquinas, un tejadillo de losas con un bloque
	 * macizo en medio y la campana colgada de él (una campana tiene que colgar de un bloque entero o apoyarse en uno;
	 * puesta sobre un muro se cae).
	 */
	static void campanario(Obra o, int cu, int ly, int cv) {
		Paleta p = o.p;
		for (int su = -1; su <= 1; su += 2) {
			for (int sv = -1; sv <= 1; sv += 2) {
				o.caja(cu + su, ly, cv + sv, cu + su, ly + 1, cv + sv, p.muroBajo);
			}
		}
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				o.pon(cu + du, ly + 2, cv + dv, du == 0 && dv == 0 ? p.moldura : Obra.losa(p.piedraLosa, false));
			}
		}
		o.pon(cu, ly + 3, cv, p.muroBajo);
		o.pon(cu, ly + 4, cv, Blocks.LANTERN);
		o.pon(cu, ly + 1, cv, Blocks.BELL.defaultBlockState()
				.setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.CEILING));
	}

	/** Un corral de vallas con su cancela hacia frente, un pesebre, un abrevadero y dos animales. */
	static void corral(Obra o, int cu, int cv, Direction frente, int k) {
		Paleta p = o.p;
		int r = PARCELA / 2 - 1;
		for (int du = -r; du <= r; du++) {
			for (int dv = -r; dv <= r; dv++) {
				if (Math.abs(du) == r || Math.abs(dv) == r) {
					o.pon(cu + du, 1, cv + dv, p.valla);
				}
			}
		}
		o.pon(cu + frente.getStepX() * r, 1, cv + frente.getStepZ() * r, o.portillo(frente));
		Direction atras = frente.getOpposite();
		Direction lado = frente.getClockWise();
		int bu = cu + atras.getStepX() * (r - 1);
		int bv = cv + atras.getStepZ() * (r - 1);
		o.pon(bu + lado.getStepX(), 1, bv + lado.getStepZ(), Blocks.HAY_BLOCK);
		o.pon(bu - lado.getStepX(), 1, bv - lado.getStepZ(), Blocks.WATER_CAULDRON.defaultBlockState()
				.setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
		o.pon(cu + frente.getStepX() * r + lado.getStepX() * r, 2, cv + frente.getStepZ() * r + lado.getStepZ() * r, Blocks.LANTERN);
		String[] ganado = {SeresObra.OVEJA, SeresObra.CERDO, SeresObra.GALLINA, SeresObra.VACA};
		String animal = ganado[Math.floorMod(o.azar(cu, 1, cv, 97) + k, ganado.length)];
		o.ser(animal, cu, 1, cv, frente);
		o.ser(animal, cu + lado.getStepX(), 1, cv + lado.getStepZ(), atras);
	}

	/** Un árbol en medio de la parcela con un banco a cada lado y flores en las esquinas. */
	static void arbolConBancos(Obra o, int cu, int cv) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			o.silla(cu + d.getStepX() * 2, 1, cv + d.getStepZ() * 2, d.getOpposite());
		}
		for (int su = -1; su <= 1; su += 2) {
			for (int sv = -1; sv <= 1; sv += 2) {
				o.parterre(cu + 2 * su, 1, cv + 2 * sv);
			}
		}
		o.arbol(cu, 1, cv, 5);
	}

	/** Un banco de dos en el cruce, junto a la fuente. */
	private static void banco(Obra o, int u, int v) {
		o.silla(u, 1, v, Direction.NORTH);
		o.pon(u - 1, 1, v, Obra.trampilla(o.p.trampilla, Direction.WEST, false, true));
		o.pon(u + 1, 1, v, Obra.trampilla(o.p.trampilla, Direction.EAST, false, true));
	}

	/**
	 * El paseo y el seto de alrededor: el seto en el último anillo, con huecos de entrada frente a las calles, un árbol
	 * en cada esquina del paseo y, en las ricas, un arco con los estandartes en cada entrada.
	 */
	private void seto(Obra o, int ext, int m) {
		Paleta p = o.p;
		int n = lado(nivel) / 2;
		Block hoja = p.desierto ? Blocks.JUNGLE_LEAVES : Blocks.AZALEA_LEAVES;
		for (int a = -m; a <= m; a++) {
			for (int[] borde : new int[][] {{a, -m}, {a, m}, {-m, a}, {m, a}}) {
				int u = borde[0];
				int v = borde[1];
				int largo = Math.abs(u) == m ? v : u;
				if (entrada(largo, n) && Math.abs(largo) < m) {
					continue;
				}
				o.pon(u, 0, v, p.desierto ? Blocks.SAND : Blocks.GRASS_BLOCK);
				if (Math.floorMod(largo, 5) == 0 || Math.abs(largo) == m) {
					o.pon(u, 1, v, p.muroBajo);
					o.pon(u, 2, v, Blocks.LANTERN);
				} else {
					o.pon(u, 1, v, Obra.hojas(o.azar(u, 1, v, 4) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : hoja));
				}
			}
		}
		int esquina = ext + BORDE / 2;
		for (int su = -1; su <= 1; su += 2) {
			for (int sv = -1; sv <= 1; sv += 2) {
				o.pon(su * esquina, 0, sv * esquina, p.desierto ? Blocks.SAND : Blocks.GRASS_BLOCK);
				o.arbol(su * esquina, 1, sv * esquina, 6);
			}
		}
		if (nivel < 2) {
			return;
		}
		// los arcos de las entradas: dos pilares de piedra y un dintel con el estandarte a cada lado
		for (int i = -n; i < n; i++) {
			int c = i * PASO + PASO / 2;
			for (int[] lado : new int[][] {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
				for (int s = -2; s <= 2; s++) {
					int u = lado[0] != 0 ? lado[0] * m : c + s;
					int v = lado[1] != 0 ? lado[1] * m : c + s;
					if (Math.abs(s) == 2) {
						o.caja(u, 1, v, u, 4, v, p.moldura);
					}
					o.pon(u, 5, v, p.zocalo);
					o.pon(u, 6, v, Obra.losa(p.piedraLosa, false));
				}
				Direction fuera = lado[0] > 0 ? Direction.EAST : lado[0] < 0 ? Direction.WEST : lado[1] > 0 ? Direction.SOUTH : Direction.NORTH;
				int u = lado[0] != 0 ? lado[0] * m : c;
				int v = lado[1] != 0 ? lado[1] * m : c;
				o.estandarte(u + fuera.getStepX(), 5, v + fuera.getStepZ(), fuera);
				o.estandarte(u - fuera.getStepX(), 5, v - fuera.getStepZ(), fuera.getOpposite());
				o.pon(u, 4, v, Obra.farolColgado());
			}
		}
	}

	/** Si a lo largo del seto, en largo, cae la entrada de una calle (frente a las calles de entre parcelas). */
	private static boolean entrada(int largo, int n) {
		for (int i = -n; i < n; i++) {
			if (Math.abs(largo - (i * PASO + PASO / 2)) <= CALLE / 2) {
				return true;
			}
		}
		return false;
	}
}
