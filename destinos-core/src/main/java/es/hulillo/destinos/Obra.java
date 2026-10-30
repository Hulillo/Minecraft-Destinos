package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Un edificio a mano, bloque a bloque, en coordenadas locales (u, ly, v): u a lo ancho, ly hacia arriba desde el
 * suelo de la pieza y v hacia el fondo, con la puerta en v negativa (el norte local). Se construye entero una vez y
 * luego PiezaObra lo pone por chunks, girado. Trae los materiales de la cultura (Paleta), el color del reino y
 * piezas sueltas de construcción (tejados, chapiteles, escaleras, muebles…).
 *
 * Las columnas marcadas como planta se apoyan en el suelo: llevan cimiento, se vacía lo que hay encima y, si en
 * ly = 0 no hay nada puesto, se deja suelo natural (hierba o arena).
 */
final class Obra {
	/** Lo más bajo que se puede construir (catacumbas). */
	static final int MIN = -10;

	final Paleta p;
	/** Color del reino (id de DyeColor), o -1 si no se sabe. */
	final int color;
	final int alto;
	private final int semilla;
	private final Map<Long, Columna> columnas = new HashMap<>();

	static final class Columna {
		final BlockState[] b;
		boolean planta;

		Columna(int alto) {
			b = new BlockState[alto - MIN + 1];
		}

		BlockState get(int ly) {
			return ly < MIN || ly - MIN >= b.length ? null : b[ly - MIN];
		}

		/** Lo más bajo que hay puesto en la columna (o 0 si nada baja del suelo). */
		int fondo() {
			for (int i = 0; i < -MIN; i++) {
				if (b[i] != null) {
					return i + MIN;
				}
			}
			return 0;
		}
	}

	Obra(int estilo, int madera, int color, int alto, int semilla) {
		this.p = Paleta.de(estilo, madera);
		this.color = color;
		this.alto = alto;
		this.semilla = semilla;
	}

	private static long clave(int u, int v) {
		return ((long) u << 32) ^ (v & 0xFFFFFFFFL);
	}

	Columna columna(int u, int v) {
		return columnas.get(clave(u, v));
	}

	private Columna crea(int u, int v) {
		return columnas.computeIfAbsent(clave(u, v), k -> new Columna(alto));
	}

	// ---- poner y leer ----

	void pon(int u, int ly, int v, BlockState b) {
		if (ly < MIN || ly > alto || b == null) {
			return;
		}
		crea(u, v).b[ly - MIN] = b;
	}

	void pon(int u, int ly, int v, Block b) {
		pon(u, ly, v, b.defaultBlockState());
	}

	BlockState en(int u, int ly, int v) {
		Columna c = columna(u, v);
		return c == null ? null : c.get(ly);
	}

	/** Si en (u, ly, v) hay un bloque entero que tapa (una pared). */
	private boolean macizo(int u, int ly, int v) {
		BlockState b = en(u, ly, v);
		return b != null && b.canOcclude() && !(b.getBlock() instanceof DoorBlock);
	}

	private boolean esPuerta(int u, int ly, int v) {
		BlockState b = en(u, ly, v);
		return b != null && b.getBlock() instanceof DoorBlock;
	}

	boolean vacio(int u, int ly, int v) {
		BlockState b = en(u, ly, v);
		return b == null || b.isAir();
	}

	/** Rellena la caja (las esquinas en cualquier orden). */
	void caja(int u1, int y1, int v1, int u2, int y2, int v2, BlockState b) {
		for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++) {
			for (int v = Math.min(v1, v2); v <= Math.max(v1, v2); v++) {
				for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
					pon(u, y, v, b);
				}
			}
		}
	}

	void caja(int u1, int y1, int v1, int u2, int y2, int v2, Block b) {
		caja(u1, y1, v1, u2, y2, v2, b.defaultBlockState());
	}

	void aire(int u1, int y1, int v1, int u2, int y2, int v2) {
		caja(u1, y1, v1, u2, y2, v2, Blocks.AIR);
	}

	/** Solo el contorno de la caja en planta (las paredes), en todas sus alturas. */
	void paredes(int u1, int y1, int v1, int u2, int y2, int v2, BlockState b) {
		int ua = Math.min(u1, u2), ub = Math.max(u1, u2), va = Math.min(v1, v2), vb = Math.max(v1, v2);
		for (int u = ua; u <= ub; u++) {
			for (int v = va; v <= vb; v++) {
				if (u == ua || u == ub || v == va || v == vb) {
					for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
						pon(u, y, v, b);
					}
				}
			}
		}
	}

	/** Sillares de la cultura en toda la caja (o solo en sus paredes), cada uno con su variación. */
	void sillares(int u1, int y1, int v1, int u2, int y2, int v2, boolean soloParedes) {
		int ua = Math.min(u1, u2), ub = Math.max(u1, u2), va = Math.min(v1, v2), vb = Math.max(v1, v2);
		for (int u = ua; u <= ub; u++) {
			for (int v = va; v <= vb; v++) {
				if (soloParedes && u != ua && u != ub && v != va && v != vb) {
					continue;
				}
				for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
					pon(u, y, v, p.piedra(this, u, y, v));
				}
			}
		}
	}

	/** Marca las columnas de la caja como apoyadas en el suelo. */
	void planta(int u1, int v1, int u2, int v2) {
		for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++) {
			for (int v = Math.min(v1, v2); v <= Math.max(v1, v2); v++) {
				crea(u, v).planta = true;
			}
		}
	}

	/** Un número fijo para ese sitio de esta obra, para variar materiales sin que cambie al volver a generarla. */
	int azar(int u, int ly, int v, int n) {
		return Math.floorMod(Geo.hash(u * 31 + semilla, v * 17 - semilla, ly + 4099), n);
	}

	// ---- bloques orientados (en local: NORTH es hacia la puerta, v negativa) ----

	static BlockState mira(Block b, Direction d) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, d);
	}

	/** Peldaño con el lado alto hacia alto; arriba, del revés (ménsulas, arcos, repisas). */
	static BlockState escalon(Block b, Direction alto, boolean arriba) {
		return mira(b, alto).setValue(BlockStateProperties.HALF, arriba ? Half.TOP : Half.BOTTOM);
	}

	static BlockState losa(Block b, boolean arriba) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, arriba ? SlabType.TOP : SlabType.BOTTOM);
	}

	static BlockState tronco(Block b, Direction.Axis eje) {
		BlockState s = b.defaultBlockState();
		return s.hasProperty(BlockStateProperties.AXIS) ? s.setValue(BlockStateProperties.AXIS, eje) : s;
	}

	/** Peldaño de esquina (forma de fuera) con el cuarto alto hacia la diagonal (hu, hv): las aristas de los chapiteles. */
	static BlockState esquina(Block b, int hu, int hv) {
		Direction mira = hv > 0 ? Direction.SOUTH : Direction.NORTH;
		Direction lado = hu > 0 ? Direction.EAST : Direction.WEST;
		StairsShape forma = mira.getCounterClockWise() == lado ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
		return escalon(b, mira, false).setValue(BlockStateProperties.STAIRS_SHAPE, forma);
	}

	/** Trampilla: cerrada arriba (tablero) o abierta pegada al lado contrario de mira (brazos, contraventanas). */
	static BlockState trampilla(Block b, Direction mira, boolean arriba, boolean abierta) {
		return mira(b, mira).setValue(BlockStateProperties.HALF, arriba ? Half.TOP : Half.BOTTOM)
				.setValue(BlockStateProperties.OPEN, abierta);
	}

	static BlockState hojas(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
	}

	static BlockState farolColgado() {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	}

	static BlockState velas(int n) {
		return Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, n).setValue(BlockStateProperties.LIT, true);
	}

	/** El bloque del color del reino de ese tipo (wool, carpet, wall_banner, bed…), o el de por defecto. */
	Block tinte(String tipo, Block sinColor) {
		if (color < 0) {
			return sinColor;
		}
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(DyeColor.byId(color).getName() + "_" + tipo));
	}

	// ---- piezas de construcción ----

	/** Una puerta de madera de dos mitades que mira hacia mira. */
	void puerta(int u, int ly, int v, Direction mira) {
		BlockState b = mira(p.puerta, mira);
		pon(u, ly, v, b.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		pon(u, ly + 1, v, b.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
	}

	/** Puerta doble: una en (u, ly, v) y la otra al lado, hacia lado, con las bisagras en los extremos. */
	void puertaDoble(int u, int ly, int v, Direction lado, Direction mira) {
		puerta(u, ly, v, mira);
		BlockState b = mira(p.puerta, mira).setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.RIGHT);
		int u2 = u + lado.getStepX(), v2 = v + lado.getStepZ();
		pon(u2, ly, v2, b.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		pon(u2, ly + 1, v2, b.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
	}

	/** Una cama con la cabecera en (u, ly, v) y los pies hacia el lado contrario de cabecera. */
	void cama(int u, int ly, int v, Direction cabecera, Block b) {
		BlockState s = mira(b, cabecera);
		pon(u, ly, v, s.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		pon(u - cabecera.getStepX(), ly, v - cabecera.getStepZ(), s.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
	}

	/** Estandarte del reino colgado de la pared que tiene detrás, mirando hacia mira (sin color, nada). */
	void estandarte(int u, int ly, int v, Direction mira) {
		if (color >= 0) {
			pon(u, ly, v, mira(tinte("wall_banner", Blocks.RED_WALL_BANNER), mira));
		}
	}

	/** Aguja de dos vallas con el estandarte del reino en lo alto. */
	void aguja(int u, int ly, int v) {
		pon(u, ly, v, p.valla);
		pon(u, ly + 1, v, p.valla);
		if (color >= 0) {
			pon(u, ly + 2, v, tinte("banner", Blocks.RED_BANNER));
		}
	}

	/**
	 * Tejado a dos aguas sobre el rectángulo [u1, u2] × [v1, v2] (ya con el alero), arrancando en ly; a lo largo de v
	 * (cumbrera paralela a v) o de u. Doble capa: por fuera peldaños del tejado y por dentro peldaños del revés de
	 * madera, así el desván queda forrado y sin escalones a la vista. Lleva ménsulas bajo el alero, bordes en los
	 * hastiales y cumbrera del remate, y viga de cumbrera por dentro. Los hastiales se cierran con hastial (con
	 * entramado y ventanas si es el enlucido), o se dejan si es null.
	 */
	void dosAguas(int u1, int v1, int u2, int v2, int ly, boolean aLoLargoDeV, BlockState hastial) {
		dosAguas(u1, v1, u2, v2, ly, aLoLargoDeV, hastial, true);
	}

	/** Igual, pero con atras falso solo se cierra el hastial del principio (u o v más bajas): para hastiales que salen de otro tejado. */
	void dosAguas(int u1, int v1, int u2, int v2, int ly, boolean aLoLargoDeV, BlockState hastial, boolean atras) {
		int ua = Math.min(u1, u2), ub = Math.max(u1, u2), va = Math.min(v1, v2), vb = Math.max(v1, v2);
		int ancho = aLoLargoDeV ? ub - ua : vb - va;
		int largo = aLoLargoDeV ? vb - va : ub - ua;
		boolean entramado = hastial != null && hastial.equals(p.enlucido) && !p.desierto;
		for (int d = 0; 2 * d <= ancho; d++) {
			int y = ly + d;
			boolean unaFila = 2 * d == ancho;
			boolean cumbre = 2 * d >= ancho - 1;
			for (int k = 0; k <= largo; k++) {
				boolean borde = k == 0 || k == largo;
				for (int lado = 0; lado < (unaFila ? 1 : 2); lado++) {
					int a = lado == 0 ? d : ancho - d;
					int u = aLoLargoDeV ? ua + a : ua + k;
					int v = aLoLargoDeV ? va + k : va + a;
					Direction alto = aLoLargoDeV ? (lado == 0 ? Direction.EAST : Direction.WEST)
							: (lado == 0 ? Direction.SOUTH : Direction.NORTH);
					// hacia dentro del tejado a lo largo (desde el borde por el que se mira)
					Direction dentro = aLoLargoDeV ? (k == 0 ? Direction.SOUTH : Direction.NORTH) : (k == 0 ? Direction.EAST : Direction.WEST);
					if (unaFila) {
						Direction.Axis eje = aLoLargoDeV ? Direction.Axis.Z : Direction.Axis.X;
						pon(u, y, v, borde ? tronco(p.remateTronco, eje) : p.tejadoLleno);
						if (!borde && d > 0 && vacio(u, y - 1, v)) {
							pon(u, y - 1, v, tronco(p.troncoPelado, eje));
						}
						// bajo la punta de la viga de cumbrera, su ménsula
						if (borde && d > 0 && vacio(u, y - 1, v)) {
							pon(u, y - 1, v, escalon(p.remateEscalon, dentro, true));
						}
					} else {
						pon(u, y, v, escalon(borde ? p.remateEscalon : p.tejadoEscalon, alto, false));
						// la capa de dentro (en los bordes, el canto del alero), solo donde no hay nada
						if (d > 0 && vacio(u, y - 1, v)) {
							pon(u, y - 1, v, escalon(borde ? p.remateEscalon : p.maderaEscalon, alto.getOpposite(), true));
						}
					}
					// la cumbrera por encima, que en las puntas remata con un peldaño del revés
					if (cumbre) {
						pon(u, y + 1, v, borde ? escalon(p.remateEscalon, dentro, true) : losa(p.remateLosa, false));
					}
					// ménsulas bajo el alero, una sí y otra no
					if (d == 0 && !borde && k % 2 == 1 && vacio(u, y - 1, v)) {
						pon(u, y - 1, v, escalon(p.remateEscalon, alto, true));
					}
				}
				// los hastiales: el triángulo de los dos extremos, por dentro del alero
				if (hastial != null && (k == 1 || atras && k == largo - 1)) {
					for (int a = d + 1; a < ancho - d; a++) {
						int u = aLoLargoDeV ? ua + a : ua + k;
						int v = aLoLargoDeV ? va + k : va + a;
						BlockState b = hastial;
						if (entramado) {
							if (ancho % 2 == 0 && 2 * a == ancho) {
								b = tronco(p.tronco, Direction.Axis.Y);
							} else if (d == 0) {
								b = tronco(p.tronco, aLoLargoDeV ? Direction.Axis.X : Direction.Axis.Z);
							} else if (d == 2 && ancho >= 10 && Math.abs(2 * a - ancho) == 4) {
								b = Blocks.GLASS_PANE.defaultBlockState();
							}
						}
						pon(u, y, v, b);
					}
				}
			}
			// bajo los peldaños, el desván vacío (sin tapar lo que ya hubiera debajo de la primera fila)
			if (d > 0) {
				for (int k = 2; k < largo - 1; k++) {
					for (int a = d; a <= ancho - d; a++) {
						int u = aLoLargoDeV ? ua + a : ua + k;
						int v = aLoLargoDeV ? va + k : va + a;
						if (en(u, y, v) == null) {
							pon(u, y, v, Blocks.AIR);
						}
					}
				}
			}
		}
	}

	/**
	 * Chapitel: tejado de pirámide de radio h en el alero, centrado en (cu, cv) y arrancando en ly. Abajo se abre en
	 * campana (dos anillos de peldaños) y luego sube empinado; las aristas van del remate, con troncos y peldaños de
	 * esquina, lleva ménsulas bajo el alero, lucernas en las cuatro caras y en la punta la aguja con el estandarte.
	 * En el desierto, cúpula.
	 */
	void chapitel(int cu, int cv, int ly, int h) {
		if (p.desierto) {
			cupula(cu, cv, ly, h);
			return;
		}
		anillo(cu, cv, ly, h, true);
		for (int du = -h + 1; du < h; du += 2) {
			if (vacio(cu + du, ly - 1, cv - h)) {
				pon(cu + du, ly - 1, cv - h, escalon(p.remateEscalon, Direction.SOUTH, true));
			}
			if (vacio(cu + du, ly - 1, cv + h)) {
				pon(cu + du, ly - 1, cv + h, escalon(p.remateEscalon, Direction.NORTH, true));
			}
			if (vacio(cu - h, ly - 1, cv + du)) {
				pon(cu - h, ly - 1, cv + du, escalon(p.remateEscalon, Direction.EAST, true));
			}
			if (vacio(cu + h, ly - 1, cv + du)) {
				pon(cu + h, ly - 1, cv + du, escalon(p.remateEscalon, Direction.WEST, true));
			}
		}
		int y = ly + 1;
		if (h > 1) {
			anillo(cu, cv, y++, h - 1, true);
		}
		// la falda, de un peldaño por altura hasta media altura, forrada de madera por dentro
		int r0 = h - 2;
		for (; r0 > h / 2 && r0 >= 2; r0--) {
			anillo(cu, cv, y, r0, true);
			forro(cu, cv, y - 1, r0);
			y++;
		}
		for (int r = r0; r >= 1; r--) {
			anillo(cu, cv, y, r, false);
			// lucernas en el primer anillo empinado
			if (r == r0 && r >= 2) {
				pon(cu, y, cv - r, Blocks.GLASS_PANE);
				pon(cu, y, cv + r, Blocks.GLASS_PANE);
				pon(cu - r, y, cv, Blocks.GLASS_PANE);
				pon(cu + r, y, cv, Blocks.GLASS_PANE);
			}
			anillo(cu, cv, y + 1, r, true);
			y += 2;
		}
		pon(cu, y, cv, tronco(p.remateTronco, Direction.Axis.Y));
		aguja(cu, y + 1, cv);
	}

	/** El forro de madera bajo un anillo de peldaños del chapitel (anillo r en y, por dentro de lo que ya hay). */
	private void forro(int cu, int cv, int y, int r) {
		for (int u = -r; u <= r; u++) {
			for (int v = -r; v <= r; v++) {
				if (Math.max(Math.abs(u), Math.abs(v)) != r || !vacio(cu + u, y, cv + v)) {
					continue;
				}
				if (Math.abs(u) == Math.abs(v)) {
					pon(cu + u, y, cv + v, p.tablas);
					continue;
				}
				Direction fuera = Math.abs(u) == r ? (u > 0 ? Direction.EAST : Direction.WEST) : (v > 0 ? Direction.SOUTH : Direction.NORTH);
				pon(cu + u, y, cv + v, escalon(p.maderaEscalon, fuera, true));
			}
		}
	}

	/** Un anillo del chapitel: de peldaños hacia dentro, o lleno; las esquinas siempre del remate. */
	private void anillo(int cu, int cv, int y, int r, boolean peldanos) {
		for (int u = -r; u <= r; u++) {
			for (int v = -r; v <= r; v++) {
				if (Math.max(Math.abs(u), Math.abs(v)) != r) {
					continue;
				}
				BlockState b;
				if (Math.abs(u) == Math.abs(v)) {
					b = peldanos ? esquina(p.remateEscalon, -Integer.signum(u), -Integer.signum(v)) : tronco(p.remateTronco, Direction.Axis.Y);
				} else if (peldanos) {
					Direction alto = Math.abs(u) == r ? (u > 0 ? Direction.WEST : Direction.EAST) : (v > 0 ? Direction.NORTH : Direction.SOUTH);
					b = escalon(p.tejadoEscalon, alto, false);
				} else {
					b = p.tejadoLleno;
				}
				pon(cu + u, y, cv + v, b);
			}
		}
		// por dentro, hueco
		for (int u = -r + 1; u <= r - 1; u++) {
			for (int v = -r + 1; v <= r - 1; v++) {
				if (en(cu + u, y, cv + v) == null) {
					pon(cu + u, y, cv + v, Blocks.AIR);
				}
			}
		}
	}

	/** Cúpula de radio r (en el desierto), con un remate dorado. */
	void cupula(int cu, int cv, int ly, int r) {
		double radio = r + 0.4;
		for (int k = 0; k <= r; k++) {
			double rk = Math.sqrt(Math.max(0, radio * radio - k * k));
			for (int u = -r; u <= r; u++) {
				for (int v = -r; v <= r; v++) {
					if (Math.hypot(u, v) <= rk) {
						pon(cu + u, ly + k, cv + v, k == 0 && Math.hypot(u, v) > rk - 1 ? p.moldura : p.tejadoLleno);
					}
				}
			}
		}
		pon(cu, ly + r + 1, cv, Blocks.CHISELED_SANDSTONE);
		pon(cu, ly + r + 2, cv, Blocks.GOLD_BLOCK);
		aguja(cu, ly + r + 3, cv);
	}

	/**
	 * Tramo recto de escalera que sube hacia sube desde (u, ly, v): n peldaños, con relleno debajo de cada uno (la
	 * zanca) y hueco encima para pasar (abre los suelos que cruce).
	 */
	void escalera(int u, int ly, int v, Direction sube, int n, Block peldano, BlockState zanca) {
		for (int i = 0; i < n; i++) {
			int uu = u + sube.getStepX() * i;
			int vv = v + sube.getStepZ() * i;
			int y = ly + i;
			pon(uu, y, vv, escalon(peldano, sube, false));
			for (int z = y - 1; z >= ly; z--) {
				pon(uu, z, vv, zanca);
			}
			pon(uu, y + 1, vv, Blocks.AIR);
			pon(uu, y + 2, vv, Blocks.AIR);
			pon(uu, y + 3, vv, Blocks.AIR);
		}
	}

	/**
	 * Escalera de ida y vuelta que sube por todos los pisos (pisos: sus suelos, de abajo arriba), con descansillo a
	 * media altura: dos bandas de banda de ancho a los lados de un muro de en medio. Desde el rellano de cada piso (de
	 * cerca de fondo) se sube por la primera banda hacia fondo, se gira en el descansillo del fondo (de lejos de
	 * fondo) y se llega por la segunda al rellano del piso de arriba. (u0, v0) es la esquina del rellano en la primera
	 * banda y lado va de la primera banda a la segunda. Los suelos se ponen antes: aquí se abre el hueco de la
	 * escalera en ellos. Es volada: cada peldaño lleva debajo otro del revés y el descansillo es de una pieza (relleno),
	 * así por debajo se puede pasar y por encima queda alto de sobra; arriba del todo, barandilla al hueco.
	 */
	void escaleraU(int u0, int v0, Direction fondo, Direction lado, int banda, int cerca, int lejos, int[] pisos, Block peldano,
			BlockState relleno) {
		escaleraU(u0, v0, fondo, lado, banda, cerca, lejos, 3, pisos, peldano, relleno);
	}

	/** Igual, con tramos de tramo peldaños (cada piso puede subir hasta 2·tramo). */
	void escaleraU(int u0, int v0, Direction fondo, Direction lado, int banda, int cerca, int lejos, int tramo, int[] pisos,
			Block peldano, BlockState relleno) {
		int fin = cerca + tramo + lejos;
		int top = pisos[pisos.length - 1];
		for (int n = 0; n + 1 < pisos.length; n++) {
			int f = pisos[n];
			int h = pisos[n + 1] - f;
			int ha = h / 2;
			int hb = h - ha;
			int techo = f + h + 1;
			for (int i = 0; i <= 2 * banda; i++) {
				boolean primera = i < banda;
				for (int j = cerca; j < fin; j++) {
					// por el muro de en medio solo pasa el descansillo
					if (i == banda && j < cerca + tramo) {
						continue;
					}
					int u = u0 + lado.getStepX() * i + fondo.getStepX() * j;
					int v = v0 + lado.getStepZ() * i + fondo.getStepZ() * j;
					int k = j - cerca;
					int m = tramo - 1 - k;
					if (primera && k < ha) {
						int y = f + 1 + k;
						pon(u, y, v, escalon(peldano, fondo, false));
						if (y - 1 > f) {
							pon(u, y - 1, v, escalon(peldano, fondo.getOpposite(), true));
						}
						caja(u, y + 1, v, u, Math.min(y + 3, techo), v, Blocks.AIR);
					} else if (!primera && k < tramo && m >= hb) {
						// donde se llega arriba por la segunda banda: el suelo del piso de arriba, tal cual
						continue;
					} else if (k >= tramo || primera) {
						// el descansillo, volado
						pon(u, f + ha, v, relleno);
						caja(u, f + ha + 1, v, u, Math.min(f + ha + 3, techo), v, Blocks.AIR);
					} else {
						int y = f + ha + 1 + m;
						pon(u, y, v, escalon(peldano, fondo.getOpposite(), false));
						pon(u, y - 1, v, escalon(peldano, fondo, true));
						caja(u, y + 1, v, u, y + 3, v, Blocks.AIR);
					}
				}
			}
		}
		// el muro de en medio, de abajo arriba, y la barandilla del hueco en el último piso
		for (int j = cerca; j < cerca + tramo; j++) {
			int u = u0 + lado.getStepX() * banda + fondo.getStepX() * j;
			int v = v0 + lado.getStepZ() * banda + fondo.getStepZ() * j;
			for (int y = pisos[0] + 1; y <= top; y++) {
				pon(u, y, v, p.piedra(this, u, y, v));
			}
			if (j > cerca) {
				pon(u, top + 1, v, p.valla);
			}
		}
		// en cada piso, barandilla alrededor del hueco que abre la escalera en el suelo (por donde no se sube)
		for (int n = 1; n < pisos.length; n++) {
			int y = pisos[n];
			for (int i = -1; i <= 2 * banda + 1; i++) {
				for (int j = cerca - 1; j <= fin; j++) {
					int u = u0 + lado.getStepX() * i + fondo.getStepX() * j;
					int v = v0 + lado.getStepZ() * i + fondo.getStepZ() * j;
					BlockState suelo = en(u, y, v);
					if (suelo == null || suelo.isAir() || suelo.getBlock() instanceof StairBlock || !vacio(u, y + 1, v)) {
						continue;
					}
					for (Direction d : Direction.Plane.HORIZONTAL) {
						BlockState junto = en(u + d.getStepX(), y, v + d.getStepZ());
						if (junto != null && junto.isAir()) {
							pon(u, y + 1, v, p.valla);
							break;
						}
					}
				}
			}
		}
	}

	/**
	 * Buhardilla con el frente centrado en (u, v), en el plano de la pared o del faldón, mirando hacia mira: ventana
	 * de 2·medio - 1 de ancho en ly y ly + 1 entre dos postes, alféizar, mejillas, hueco por dentro para asomarse y
	 * tejadillo a dos aguas con su hastial de entramado.
	 */
	void buhardilla(int u, int v, Direction mira, int ly, int medio) {
		buhardilla(u, v, mira, ly, medio, 3);
	}

	/**
	 * Igual, con el tejadillo metido fondo bloques hacia dentro: en un tejado grande, hasta que la cumbrera se hunde
	 * en el faldón de detrás y no queda la punta al aire (sin hastial de atrás: queda dentro del tejado).
	 */
	void buhardilla(int u, int v, Direction mira, int ly, int medio, int fondo) {
		Direction dentro = mira.getOpposite();
		Direction lado = mira.getClockWise();
		Direction.Axis ejeLado = lado.getAxis();
		for (int s = -medio; s <= medio; s++) {
			int fu = u + lado.getStepX() * s;
			int fv = v + lado.getStepZ() * s;
			pon(fu, ly - 1, fv, tronco(p.tronco, ejeLado));
			boolean poste = Math.abs(s) == medio;
			for (int y = ly; y <= ly + 1; y++) {
				pon(fu, y, fv, poste ? tronco(p.tronco, Direction.Axis.Y) : Blocks.GLASS_PANE.defaultBlockState());
			}
			for (int t = 1; t <= 2; t++) {
				int iu = fu + dentro.getStepX() * t;
				int iv = fv + dentro.getStepZ() * t;
				for (int y = ly; y <= ly + 2; y++) {
					pon(iu, y, iv, poste ? (p.desierto ? p.tablas : p.enlucido) : Blocks.AIR.defaultBlockState());
				}
			}
		}
		int r = medio + 1;
		// las mejillas de fuera, hasta el alero del tejadillo, para que no quede hueco con el tejado de debajo
		for (int s = -r; s <= r; s += 2 * r) {
			for (int t = 0; t <= 2; t++) {
				int cu = u + lado.getStepX() * s + dentro.getStepX() * t;
				int cv = v + lado.getStepZ() * s + dentro.getStepZ() * t;
				caja(cu, ly, cv, cu, ly + 1, cv, p.desierto ? p.tablas : p.enlucido);
			}
		}
		int au = u + mira.getStepX() + lado.getStepX() * -r;
		int av = v + mira.getStepZ() + lado.getStepZ() * -r;
		int bu = u + dentro.getStepX() * fondo + lado.getStepX() * r;
		int bv = v + dentro.getStepZ() * fondo + lado.getStepZ() * r;
		// los dos hastiales empiezan en ly + 2: el de dentro deja libre el paso de abajo. Si el tejadillo va hondo,
		// solo el de delante (el de detrás quedaría metido en el tejado grande)
		BlockState hastial = p.desierto ? p.tablas : p.enlucido;
		boolean aLoLargoDeV = mira.getAxis() == Direction.Axis.Z;
		if (fondo <= 3) {
			dosAguas(au, av, bu, bv, ly + 2, aLoLargoDeV, hastial);
		} else {
			// el hastial de delante es el de u o v más bajas solo si la buhardilla mira hacia ahí
			boolean delanteBajo = mira.getStepX() + mira.getStepZ() < 0;
			if (delanteBajo) {
				dosAguas(au, av, bu, bv, ly + 2, aLoLargoDeV, hastial, false);
			} else {
				dosAguas(au, av, bu, bv, ly + 2, aLoLargoDeV, null);
				// el hastial de delante a mano: el triángulo bajo el tejadillo, por dentro del alero
				int ancho = 2 * r;
				for (int d = 0; 2 * d < ancho; d++) {
					for (int a = d + 1; a < ancho - d; a++) {
						int hu = u + lado.getStepX() * (a - r);
						int hv = v + lado.getStepZ() * (a - r);
						pon(hu, ly + 2 + d, hv, hastial);
					}
				}
			}
		}
	}

	/** La placa de presión de la madera de la cultura (el tablero de las mesas). */
	BlockState placa() {
		Block b = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(
				BuiltInRegistries.BLOCK.getKey(p.valla.getBlock()).getPath().replace("_fence", "_pressure_plate")));
		return (b == Blocks.AIR ? Blocks.OAK_PRESSURE_PLATE : b).defaultBlockState();
	}

	/** La cancela de la valla de madera de la cultura (en el desierto, de roble), mirando hacia mira. */
	BlockState portillo(Direction mira) {
		Block b = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(
				BuiltInRegistries.BLOCK.getKey(p.valla.getBlock()).getPath() + "_gate"));
		if (b == Blocks.AIR) {
			b = Blocks.OAK_FENCE_GATE;
		}
		return mira(b, mira);
	}

	/**
	 * Copia aquí otra obra hecha en local (un puesto de mercado…), con su centro en (du, dv) y girada rot × 90 grados
	 * en el sentido de las agujas del reloj (como PiezaObra: 1 lleva su norte al este).
	 */
	void incrusta(Obra otra, int du, int dv, int rot) {
		Rotation giro = switch (rot) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
		for (Map.Entry<Long, Columna> e : otra.columnas.entrySet()) {
			int u = (int) (e.getKey() >> 32);
			int v = (int) (long) e.getKey();
			int wu = du + switch (rot) {
				case 1 -> -v;
				case 2 -> -u;
				case 3 -> v;
				default -> u;
			};
			int wv = dv + switch (rot) {
				case 1 -> u;
				case 2 -> -v;
				case 3 -> -u;
				default -> v;
			};
			for (int ly = MIN; ly <= otra.alto; ly++) {
				BlockState b = e.getValue().get(ly);
				if (b != null) {
					pon(wu, ly, wv, b.rotate(giro));
				}
			}
		}
	}

	// ---- seres: animales, gente y armaduras que se ponen con la obra ----

	/** Un ser de tipo (ver SeresObra) en (u, ly, v), mirando hacia mira. */
	record Ser(String tipo, int u, int ly, int v, Direction mira) {
	}

	private final List<Ser> seres = new ArrayList<>();

	void ser(String tipo, int u, int ly, int v, Direction mira) {
		seres.add(new Ser(tipo, u, ly, v, mira));
	}

	List<Ser> seres() {
		return seres;
	}

	/**
	 * El cartel con el nombre de la capital, colgado de la pared de detrás y mirando hacia mira. Se pone en blanco:
	 * lo escribe CartelesCapital al cargar el chunk (al generar no hay mundo al que avisar del texto).
	 */
	void cartel(int u, int ly, int v, Direction mira) {
		pon(u, ly, v, mira(CartelesCapital.CARTEL, mira));
	}

	/** Chimenea pegada a la pared: hogar con fuego bajo una repisa y el cañón hasta techo; mira hacia la sala. */
	void chimenea(int u, int ly, int v, Direction mira, int techo) {
		Direction lado = mira.getClockWise();
		for (int s = -1; s <= 1; s++) {
			int uu = u + lado.getStepX() * s;
			int vv = v + lado.getStepZ() * s;
			pon(uu, ly, vv, s == 0 ? Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, mira)
					: p.ladrillo);
			pon(uu, ly + 1, vv, escalon(p.ladrilloEscalon, mira.getOpposite(), true));
			if (s == 0) {
				caja(uu, ly + 2, vv, uu, techo - 1, vv, p.ladrillo);
			}
		}
		// algo en la repisa
		pon(u + lado.getStepX(), ly + 2, v + lado.getStepZ(), Blocks.FLOWER_POT.defaultBlockState());
		pon(u - lado.getStepX(), ly + 2, v - lado.getStepZ(), velas(3));
	}

	/** Silla: un peldaño con el respaldo hacia respaldo. */
	void silla(int u, int ly, int v, Direction respaldo) {
		pon(u, ly, v, escalon(p.maderaEscalon, respaldo, false));
	}

	/** Mesa de (u1, v1) a (u2, v2), con patas de valla y tablero de placa de madera (o mantel, si es larga y con sillas). */
	void mesa(int u1, int v1, int u2, int v2, int ly, boolean sillas) {
		int ua = Math.min(u1, u2), ub = Math.max(u1, u2), va = Math.min(v1, v2), vb = Math.max(v1, v2);
		boolean mantel = sillas && Math.max(ub - ua, vb - va) >= 3;
		caja(ua, ly, va, ub, ly, vb, p.valla);
		caja(ua, ly + 1, va, ub, ly + 1, vb, mantel ? Blocks.WHITE_CARPET.defaultBlockState() : placa());
		if (!sillas) {
			return;
		}
		boolean largaEnU = ub - ua >= vb - va;
		for (int k = largaEnU ? ua : va; k <= (largaEnU ? ub : vb); k++) {
			if (largaEnU) {
				silla(k, ly, va - 1, Direction.NORTH);
				silla(k, ly, vb + 1, Direction.SOUTH);
			} else {
				silla(ua - 1, ly, k, Direction.WEST);
				silla(ub + 1, ly, k, Direction.EAST);
			}
		}
	}

	/** Viga a lo largo de u o v en ly, de un extremo a otro. */
	void viga(int u1, int v1, int u2, int v2, int ly) {
		Direction.Axis eje = u1 == u2 ? Direction.Axis.Z : Direction.Axis.X;
		for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++) {
			for (int v = Math.min(v1, v2); v <= Math.max(v1, v2); v++) {
				pon(u, ly, v, tronco(p.tronco, eje));
			}
		}
	}

	/** Pilar de tronco de ly1 a ly2 con capitel (peldaños del revés alrededor, arriba). */
	void pilar(int u, int v, int ly1, int ly2) {
		caja(u, ly1, v, u, ly2, v, tronco(p.tronco, Direction.Axis.Y));
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (vacio(u + d.getStepX(), ly2, v + d.getStepZ())) {
				pon(u + d.getStepX(), ly2, v + d.getStepZ(), escalon(p.maderaEscalon, d.getOpposite(), true));
			}
		}
	}

	/** Farola de brazo: poste de tres, brazo hacia brazo y el farol colgando. */
	void farola(int u, int ly, int v, Direction brazo) {
		pon(u, ly, v, p.muroBajo);
		pon(u, ly + 1, v, p.valla);
		pon(u, ly + 2, v, p.valla);
		pon(u, ly + 3, v, p.valla);
		pon(u + brazo.getStepX(), ly + 3, v + brazo.getStepZ(), p.valla);
		pon(u + brazo.getStepX(), ly + 2, v + brazo.getStepZ(), farolColgado());
	}

	/** Un arbusto o macizo de flores en (u, ly, v), según el sitio. */
	void parterre(int u, int ly, int v) {
		pon(u, ly - 1, v, p.desierto ? Blocks.SAND : Blocks.GRASS_BLOCK);
		int h = azar(u, ly, v, 10);
		if (h < 3) {
			pon(u, ly, v, hojas(p.desierto ? Blocks.JUNGLE_LEAVES : h == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
		} else if (h < 9) {
			Block[] flores = p.flores;
			pon(u, ly, v, flores[azar(u, ly + 7, v, flores.length)]);
		}
	}

	/** Árbol de patio: tronco de alto y copa redonda (pino en las tierras frías, palmera en el desierto). */
	void arbol(int u, int ly, int v, int alto) {
		caja(u, ly, v, u, ly + alto - 1, v, tronco(p.troncoArbol, Direction.Axis.Y));
		BlockState h = hojas(p.hojas);
		if (p.desierto) {
			aire(u, ly, v, u, ly + alto - 1, v);
			palmera(u, ly, v, alto);
			return;
		}
		if (p.pino) {
			for (int y = 2; y <= alto + 1; y++) {
				int r = y >= alto ? 0 : (alto - y) % 2 == 0 ? 2 : 1;
				for (int du = -r; du <= r; du++) {
					for (int dv = -r; dv <= r; dv++) {
						if ((du != 0 || dv != 0 || y > alto - 1) && Math.abs(du) + Math.abs(dv) <= r + 1 && vacio(u + du, ly + y, v + dv)) {
							pon(u + du, ly + y, v + dv, h);
						}
					}
				}
			}
			return;
		}
		for (int y = alto - 2; y <= alto + 1; y++) {
			int r = y == alto + 1 ? 1 : 2;
			for (int du = -r; du <= r; du++) {
				for (int dv = -r; dv <= r; dv++) {
					boolean esquina = Math.abs(du) == r && Math.abs(dv) == r;
					if (!esquina && vacio(u + du, ly + y, v + dv)) {
						pon(u + du, ly + y, v + dv, h);
					}
				}
			}
		}
	}

	/**
	 * Una palmera en (u, ly, v): tronco de jungla de alto bloques que se inclina un poco hacia un lado al subir, un
	 * penacho de hojas en lo alto con las frondas cayendo hacia fuera y algún coco (cacao maduro) bajo las hojas.
	 */
	void palmera(int u, int ly, int v, int alto) {
		Direction inclina = Direction.from2DDataValue(azar(u, ly, v, 4));
		BlockState tronco = tronco(Blocks.JUNGLE_LOG, Direction.Axis.Y);
		int tu = u;
		int tv = v;
		for (int y = 0; y < alto; y++) {
			if (alto >= 5 && (y == alto / 2 + 1 || y == alto - 1)) {
				// el codo: un tronco tumbado hacia donde se inclina, para que no quede cortado en diagonal
				pon(tu, ly + y, tv, tronco(Blocks.JUNGLE_LOG, inclina.getAxis()));
				tu += inclina.getStepX();
				tv += inclina.getStepZ();
			}
			pon(tu, ly + y, tv, tronco);
		}
		int cima = ly + alto;
		BlockState h = hojas(Blocks.JUNGLE_LEAVES);
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				pon(tu + du, cima, tv + dv, h);
			}
		}
		pon(tu, cima + 1, tv, h);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int su = d.getStepX();
			int sv = d.getStepZ();
			Direction l = d.getClockWise();
			// la fronda larga, recta y cayendo; y la de la diagonal, más corta
			pon(tu + 2 * su, cima, tv + 2 * sv, h);
			pon(tu + 3 * su, cima - 1, tv + 3 * sv, h);
			pon(tu + 4 * su, cima - 2, tv + 4 * sv, h);
			pon(tu + 2 * (su + l.getStepX()), cima - 1, tv + 2 * (sv + l.getStepZ()), h);
			if (azar(tu + su, cima, tv + sv, 3) == 0 && vacio(tu + su, cima - 1, tv + sv)) {
				pon(tu + su, cima - 1, tv + sv, mira(Blocks.COCOA, d.getOpposite()).setValue(BlockStateProperties.AGE_2, 2));
			}
		}
	}

	/**
	 * Faroles donde queda oscuro: en la caja (u1, v1)–(u2, v2) y entre los pisos y1 e y2, cada hueco de aire con suelo
	 * debajo y techo a menos de seis bloques (un sitio cerrado) que no tenga ninguna luz a menos de radio, lleva un
	 * farol colgado del techo. Se mira de paso en paso para no llenarlo todo.
	 */
	void alumbra(int u1, int v1, int u2, int v2, int y1, int y2, int radio) {
		List<int[]> luces = new ArrayList<>();
		for (Map.Entry<Long, Columna> e : columnas.entrySet()) {
			int u = (int) (e.getKey() >> 32);
			int v = e.getKey().intValue();
			for (int ly = y1; ly <= y2 + 6; ly++) {
				BlockState b = e.getValue().get(ly);
				if (b != null && b.getLightEmission() > 0) {
					luces.add(new int[] {u, ly, v});
				}
			}
		}
		for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u += 2) {
			for (int v = Math.min(v1, v2); v <= Math.max(v1, v2); v += 2) {
				for (int ly = y1 + 1; ly <= y2; ly++) {
					BlockState suelo = en(u, ly - 1, v);
					if (suelo == null || suelo.isAir() || !suelo.canOcclude() || !vacio(u, ly, v) || !vacio(u, ly + 1, v)) {
						continue;
					}
					int techo = -1;
					for (int y = ly + 2; y <= ly + 5; y++) {
						if (!vacio(u, y, v)) {
							techo = y;
							break;
						}
					}
					if (techo < 0 || !vacio(u, techo - 1, v)) {
						continue;
					}
					boolean hayLuz = false;
					for (int[] l : luces) {
						if (Math.abs(l[0] - u) <= radio && Math.abs(l[2] - v) <= radio && Math.abs(l[1] - ly) <= 4) {
							hayLuz = true;
							break;
						}
					}
					if (!hayLuz) {
						pon(u, techo - 1, v, farolColgado());
						luces.add(new int[] {u, techo - 1, v});
					}
				}
			}
		}
	}

	// ---- al final: unir vallas, cristales, barrotes y muros con lo que tienen al lado ----

	void conecta() {
		for (Map.Entry<Long, Columna> e : columnas.entrySet()) {
			int u = (int) (e.getKey() >> 32);
			int v = e.getKey().intValue();
			Columna c = e.getValue();
			for (int i = 0; i < c.b.length; i++) {
				BlockState b = c.b[i];
				if (b == null) {
					continue;
				}
				int ly = i + MIN;
				if (b.getBlock() instanceof WallBannerBlock) {
					// un estandarte solo cuelga de una pared de verdad: sobre cristal, barrotes o nada, fuera
					Direction d = b.getValue(WallBannerBlock.FACING);
					BlockState detras = en(u - d.getStepX(), ly, v - d.getStepZ());
					if (detras == null || detras.isAir() || !detras.canOcclude() || detras.getBlock() instanceof TransparentBlock) {
						c.b[i] = Blocks.AIR.defaultBlockState();
					}
					continue;
				}
				if (b.getBlock() instanceof DoorBlock && b.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
					// una puerta va en el hueco de una pared: si delante y detrás hay pared y a los lados no, está
					// girada; se pone de través (las dos mitades)
					Direction f = b.getValue(DoorBlock.FACING);
					Direction l = f.getClockWise();
					boolean delante = macizo(u + f.getStepX(), ly, v + f.getStepZ());
					boolean detras = macizo(u - f.getStepX(), ly, v - f.getStepZ());
					boolean izq = macizo(u + l.getStepX(), ly, v + l.getStepZ()) || esPuerta(u + l.getStepX(), ly, v + l.getStepZ());
					boolean der = macizo(u - l.getStepX(), ly, v - l.getStepZ()) || esPuerta(u - l.getStepX(), ly, v - l.getStepZ());
					if (delante && detras && !izq && !der) {
						c.b[i] = b.setValue(DoorBlock.FACING, l);
						BlockState arriba = en(u, ly + 1, v);
						if (arriba != null && arriba.getBlock() instanceof DoorBlock && i + 1 < c.b.length) {
							c.b[i + 1] = arriba.setValue(DoorBlock.FACING, l);
						}
					}
					continue;
				}
				if (b.getBlock() instanceof CrossCollisionBlock) {
					for (Direction d : Direction.Plane.HORIZONTAL) {
						BlockState junto = en(u + d.getStepX(), ly, v + d.getStepZ());
						b = b.setValue(lado(d), junto != null && seUne(b, junto));
					}
					c.b[i] = b;
				} else if (b.getBlock() instanceof WallBlock) {
					int unidos = 0;
					boolean[] une = new boolean[4];
					for (Direction d : Direction.Plane.HORIZONTAL) {
						BlockState junto = en(u + d.getStepX(), ly, v + d.getStepZ());
						une[d.get2DDataValue()] = junto != null && (junto.getBlock() instanceof WallBlock || junto.canOcclude());
						b = b.setValue(ladoMuro(d), une[d.get2DDataValue()] ? WallSide.LOW : WallSide.NONE);
						unidos += une[d.get2DDataValue()] ? 1 : 0;
					}
					// recto (dos lados opuestos y nada más): sin poste
					boolean recto = unidos == 2 && (une[0] && une[2] || une[1] && une[3]);
					c.b[i] = b.setValue(BlockStateProperties.UP, !recto);
				}
			}
		}
	}

	private static boolean seUne(BlockState b, BlockState junto) {
		if (junto.canOcclude()) {
			return true;
		}
		if (b.getBlock() instanceof FenceBlock) {
			return junto.getBlock() instanceof FenceBlock;
		}
		return junto.getBlock() instanceof IronBarsBlock || junto.getBlock() instanceof WallBlock;
	}

	private static BooleanProperty lado(Direction d) {
		return switch (d) {
			case NORTH -> BlockStateProperties.NORTH;
			case SOUTH -> BlockStateProperties.SOUTH;
			case EAST -> BlockStateProperties.EAST;
			default -> BlockStateProperties.WEST;
		};
	}

	private static EnumProperty<WallSide> ladoMuro(Direction d) {
		return switch (d) {
			case NORTH -> BlockStateProperties.NORTH_WALL;
			case SOUTH -> BlockStateProperties.SOUTH_WALL;
			case EAST -> BlockStateProperties.EAST_WALL;
			default -> BlockStateProperties.WEST_WALL;
		};
	}
}
