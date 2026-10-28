package es.hulillo.destinos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * El castillo de una capital rica (muralla de nivel 2): un patio cuadrado con muralla almenada (zócalo,
 * contrafuertes y matacanes), una torre redonda con tejado cónico en cada esquina, una barbacana con rastrillo en la
 * puerta y la torre del homenaje en medio, con tejado a cuatro aguas. El homenaje se vive por dentro: salón del trono
 * abajo, comedor y cocina en el primer piso, dormitorios en el segundo y catacumbas debajo. En el patio, un pozo, el
 * establo y la herrería. Los materiales van con la cultura del pueblo y los estandartes con el color del reino. Todo a
 * una altura fija (el suelo del centro), con cimientos hasta el suelo de verdad.
 *
 * Coordenadas locales: (u, v) desde el centro, con la puerta en v negativa; rot gira lo local al mundo de 90 en
 * 90 grados en el sentido de las agujas del reloj (0: la puerta mira al norte).
 */
public final class CastilloCapital extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) CastilloCapital::new;
	/** Muralla: los anillos MURO - 1 y MURO, y por fuera (FUERA) zócalo, contrafuertes, matacanes y parapeto. */
	private static final int MURO = 14;
	private static final int FUERA = MURO + 1;
	private static final int ALTO_MURO = 9;
	/** Torres de las esquinas: centro en (±ESQUINA, ±ESQUINA); el tejado sobresale hasta ALERO. */
	private static final int ESQUINA = 14;
	private static final double RADIO_TORRE = 4;
	private static final double ALERO = 5;
	private static final int ALTO_TORRE = 15;
	/** La barbacana: de -BARBACANA a BARBACANA de ancho, desde la cara de dentro de la muralla hasta FRENTE. */
	private static final int BARBACANA = 4;
	private static final int FRENTE = -(MURO + 2);
	private static final int ALTO_PUERTA = 13;
	/** Torre del homenaje: de -HOMENAJE a HOMENAJE, pisos en PISOS y el desván en ALTO_HOMENAJE. */
	private static final int HOMENAJE = 6;
	private static final int[] PISOS = {6, 11};
	private static final int ALTO_HOMENAJE = 16;
	/** Las catacumbas, bajo el homenaje: el suelo CRIPTA + 1 por debajo del salón. */
	private static final int CRIPTA = 5;
	/** Hasta dónde llega lo más alto (el estandarte de la aguja del homenaje). */
	private static final int ALTO = 27;
	/** Medio lado de la caja: la muralla más lo que sobresalen las torres y sus tejados. */
	static final int MEDIO = ESQUINA + (int) Math.ceil(ALERO);
	/** Hasta dónde se rellena el cimiento por debajo, buscando suelo firme. */
	private static final int CIMIENTO = 16;
	/** Marca del suelo del patio: el que hay, o tierra si ahí no queda nada firme. */
	private static final BlockState PATIO = Blocks.STRUCTURE_VOID.defaultBlockState();

	private final int cx;
	private final int cz;
	private final int y0;
	private final int rot;
	private final int estilo;
	/** Color del reino (id de DyeColor), o -1 si no se sabe. */
	private final int color;

	CastilloCapital(int cx, int cz, int y0, int rot, int estilo, int color) {
		super(TIPO, 0, new BoundingBox(cx - MEDIO, y0, cz - MEDIO, cx + MEDIO, y0 + ALTO, cz + MEDIO));
		this.cx = cx;
		this.cz = cz;
		this.y0 = y0;
		this.rot = rot;
		this.estilo = estilo;
		this.color = color;
	}

	private CastilloCapital(CompoundTag t) {
		super(TIPO, t);
		cx = t.getIntOr("cx", 0);
		cz = t.getIntOr("cz", 0);
		y0 = t.getIntOr("y0", 0);
		rot = t.getIntOr("rot", 0);
		// los castillos guardados antes de los estilos solo sabían si eran del desierto
		estilo = t.getIntOr("estilo", t.getIntOr("desierto", 0) != 0 ? AdornoCapital.DESIERTO : AdornoCapital.TEMPLADO);
		color = t.getIntOr("color", -1);
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

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		t.putInt("cx", cx);
		t.putInt("cz", cz);
		t.putInt("y0", y0);
		t.putInt("rot", rot);
		t.putInt("estilo", estilo);
		t.putInt("color", color);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar,
			BoundingBox caja, ChunkPos chunk, BlockPos pivote) {
		BoundingBox mia = getBoundingBox();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = Math.max(mia.minX(), caja.minX()); x <= Math.min(mia.maxX(), caja.maxX()); x++) {
			for (int z = Math.max(mia.minZ(), caja.minZ()); z <= Math.min(mia.maxZ(), caja.maxZ()); z++) {
				int dx = x - cx;
				int dz = z - cz;
				// del mundo a lo local: el giro al revés
				int u = switch (rot) {
					case 1 -> dz;
					case 2 -> -dx;
					case 3 -> -dz;
					default -> dx;
				};
				int v = switch (rot) {
					case 1 -> -dx;
					case 2 -> -dz;
					case 3 -> dx;
					default -> dz;
				};
				if (dentro(u, v)) {
					columna(level, pos, x, z, u, v);
				}
			}
		}
	}

	/** Si la columna local (u, v) tiene algo del castillo, aunque sea solo el alero de un tejado. */
	private static boolean dentro(int u, int v) {
		return anillo(u, v) <= FUERA || enBarbacana(u, v) || v == FRENTE - 1 && Math.abs(u) <= BARBACANA || torre(u, v) <= ALERO;
	}

	/** Si la columna local (u, v) se apoya en el suelo (lleva cimiento). */
	private static boolean planta(int u, int v) {
		return anillo(u, v) <= FUERA || enBarbacana(u, v) || torre(u, v) <= RADIO_TORRE;
	}

	private static int anillo(int u, int v) {
		return Math.max(Math.abs(u), Math.abs(v));
	}

	/** Distancia al centro de la torre de la esquina más cercana. */
	private static double torre(int u, int v) {
		return Math.hypot(Math.abs(u) - ESQUINA, Math.abs(v) - ESQUINA);
	}

	private static boolean enBarbacana(int u, int v) {
		return Math.abs(u) <= BARBACANA && v >= FRENTE && v <= -(MURO - 1);
	}

	private void columna(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int u, int v) {
		// bajo el homenaje van las catacumbas: el cimiento empieza debajo de ellas
		int abajo = anillo(u, v) <= HOMENAJE ? -CRIPTA - 1 : 0;
		if (planta(u, v)) {
			// cimiento: hacia abajo hasta tocar suelo firme, para que no cuelgue en las cuestas
			pos.set(x, y0 + abajo - 1, z);
			for (int i = 0; i < CIMIENTO; i++) {
				BlockState s = level.getBlockState(pos);
				if (!s.isAir() && !s.canBeReplaced()) {
					break;
				}
				level.setBlock(pos, base(), 2);
				pos.move(0, -1, 0);
			}
		}
		for (int ly = abajo; ly <= ALTO; ly++) {
			BlockState b = bloque(u, v, ly, x, z);
			if (b == null) {
				continue;
			}
			pos.set(x, y0 + ly, z);
			if (b == PATIO) {
				BlockState s = level.getBlockState(pos);
				if (s.isAir() || s.canBeReplaced()) {
					level.setBlock(pos, estilo == AdornoCapital.DESIERTO ? Blocks.SAND.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(), 2);
				}
			} else {
				level.setBlock(pos.immutable(), b, 2);
			}
		}
	}

	/** El bloque en (u, v, ly); null para no tocar lo que haya. */
	private BlockState bloque(int u, int v, int ly, int x, int z) {
		int anillo = anillo(u, v);
		if (enBarbacana(u, v) || v == FRENTE - 1 && Math.abs(u) <= BARBACANA) {
			return barbacana(u, v, ly, x, z);
		}
		double torre = torre(u, v);
		if (ly > ALTO_TORRE && torre <= ALERO) {
			return tejadoTorre(torre, ly);
		}
		if (torre <= RADIO_TORRE) {
			return torre(u, v, ly, torre, anillo, x, z);
		}
		if (anillo > FUERA) {
			return null;
		}
		if (anillo >= MURO - 1) {
			return muralla(u, v, ly, anillo, x, z);
		}
		if (anillo <= HOMENAJE || ly > ALTO_HOMENAJE && anillo == HOMENAJE + 1) {
			return homenaje(u, v, ly, anillo, x, z);
		}
		return patio(u, v, ly);
	}

	// ---- muralla, torres y barbacana ----

	private BlockState muralla(int u, int v, int ly, int anillo, int x, int z) {
		boolean lado = Math.abs(u) >= Math.abs(v);
		int largo = lado ? v : u;
		Direction adentro = lado ? (u > 0 ? Direction.WEST : Direction.EAST) : (v > 0 ? Direction.NORTH : Direction.SOUTH);
		if (ly == 0) {
			return base();
		}
		if (anillo <= MURO) {
			// saeteras (solo un hueco en la cara de fuera) entre contrafuerte y contrafuerte
			if (anillo == MURO && Math.floorMod(largo, 6) == 3 && (ly == 4 || ly == 5)) {
				return aire();
			}
			return ly <= ALTO_MURO ? muro(x, z, ly) : aire();
		}
		// por fuera: zócalo, contrafuertes, la ménsula de los matacanes y el parapeto almenado
		boolean contrafuerte = Math.floorMod(largo, 6) == 0;
		if (ly == 1) {
			return contrafuerte ? zocalo() : peldano(zocaloEscalon(), adentro, false);
		}
		if (ly < ALTO_MURO - 1) {
			return contrafuerte ? muro(x, z, ly) : aire();
		}
		if (ly == ALTO_MURO - 1) {
			return contrafuerte ? muro(x, z, ly) : peldano(piedraEscalon(), adentro, true);
		}
		if (ly <= ALTO_MURO + 1) {
			return muro(x, z, ly);
		}
		return ly == ALTO_MURO + 2 && almena(x, z) ? muro(x, z, ly) : aire();
	}

	private BlockState torre(int u, int v, int ly, double torre, int anillo, int x, int z) {
		boolean pared = torre > RADIO_TORRE - 1;
		if (ly == 0) {
			return base();
		}
		if (pared) {
			// por donde llega el adarve de la muralla se entra en la torre
			if ((ly == ALTO_MURO + 1 || ly == ALTO_MURO + 2) && anillo >= MURO - 1 && anillo <= MURO
					&& Math.min(Math.abs(u), Math.abs(v)) < ESQUINA - 2) {
				return aire();
			}
			// saeteras hacia fuera: abajo solo el hueco (la torre es maciza), arriba se asoman a la sala
			int au = Math.abs(u) - ESQUINA;
			int av = Math.abs(v) - ESQUINA;
			boolean saetera = au == 0 && av >= 3 || av == 0 && au >= 3;
			if (saetera && (ly == 5 || ly == ALTO_TORRE - 3 || ly == ALTO_TORRE - 2)) {
				return aire();
			}
			return ly == ALTO_TORRE ? moldura() : muro(x, z, ly);
		}
		// maciza hasta el adarve (sin huecos oscuros abajo), una sala a esa altura y su techo
		if (ly < ALTO_MURO) {
			return muro(x, z, ly);
		}
		if (ly == ALTO_MURO || ly == ALTO_TORRE) {
			return madera();
		}
		return ly == ALTO_MURO + 1 && torre < 0.8 ? Blocks.LANTERN.defaultBlockState() : aire();
	}

	/** Tejado cónico (cúpula en el desierto) con aguja y estandarte. */
	private BlockState tejadoTorre(double torre, int ly) {
		int k = ly - ALTO_TORRE - 1;
		double r;
		int punta;
		if (estilo == AdornoCapital.DESIERTO) {
			double radio = ALERO - 1;
			r = k <= radio ? Math.sqrt(radio * radio - k * k) : -1;
			punta = (int) radio + 1;
		} else {
			r = ALERO - k * 0.8;
			punta = (int) (ALERO / 0.8) + 1;
		}
		if (r >= 0 && torre <= r) {
			return tejado();
		}
		return torre < 0.5 ? aguja(k - punta) : aire();
	}

	private BlockState barbacana(int u, int v, int ly, int x, int z) {
		int au = Math.abs(u);
		if (v < FRENTE) {
			// lo que vuela por delante: ménsula, matacán almenado y dos estandartes del reino
			if (ly == ALTO_PUERTA - 2) {
				return peldano(piedraEscalon(), Direction.SOUTH, true);
			}
			if (ly == ALTO_PUERTA - 1) {
				return muro(x, z, ly);
			}
			if (ly == ALTO_PUERTA) {
				return almena(x, z) ? muro(x, z, ly) : aire();
			}
			if (ly == ALTO_PUERTA - 5 && au == 3) {
				return estandarte(Direction.NORTH);
			}
			return null;
		}
		boolean paso = au <= 1;
		if (ly == 0) {
			return paso ? camino() : base();
		}
		if (paso && ly <= 4) {
			// el arco y el rastrillo a medio bajar
			if (ly == 4 && au == 1) {
				return peldano(piedraEscalon(), u > 0 ? Direction.EAST : Direction.WEST, true);
			}
			if (v == FRENTE + 1 && (ly == 3 || ly == 4)) {
				return Blocks.IRON_BARS.defaultBlockState();
			}
			return aire();
		}
		boolean borde = au == BARBACANA || v == FRENTE || v == -(MURO - 1);
		boolean esquina = au == BARBACANA && (v == FRENTE || v == -(MURO - 1));
		if (ly < ALTO_PUERTA - 1) {
			if (v == FRENTE && au == 2 && (ly == 7 || ly == 8)) {
				return aire();
			}
			return borde && ly == 5 ? moldura() : muro(x, z, ly);
		}
		if (ly == ALTO_PUERTA - 1) {
			return muro(x, z, ly);
		}
		if (esquina) {
			// torrecillas en las cuatro esquinas, con un farol encima
			return ly <= ALTO_PUERTA + 1 ? muro(x, z, ly) : ly == ALTO_PUERTA + 2 ? Blocks.LANTERN.defaultBlockState() : aire();
		}
		return ly == ALTO_PUERTA && borde && almena(x, z) ? muro(x, z, ly) : aire();
	}

	// ---- la torre del homenaje ----

	private BlockState homenaje(int u, int v, int ly, int anillo, int x, int z) {
		if (ly > ALTO_HOMENAJE) {
			return tejadoHomenaje(u, v, ly, anillo, x, z);
		}
		if (ly < 0) {
			return cripta(u, v, ly, anillo, x, z);
		}
		if (anillo == HOMENAJE) {
			if (ly == 0) {
				return base();
			}
			if (u == 0 && v == -HOMENAJE && (ly == 1 || ly == 2)) {
				return puerta(ly == 1);
			}
			if (ventana(u, v, ly)) {
				return Blocks.GLASS_PANE.defaultBlockState();
			}
			// sillares en las esquinas y una moldura en cada piso
			if (Math.abs(u) == HOMENAJE && Math.abs(v) == HOMENAJE || ly == PISOS[0] || ly == PISOS[1] || ly == ALTO_HOMENAJE) {
				return moldura();
			}
			return muro(x, z, ly);
		}
		// la escalera de mano, en el rincón del fondo, de las catacumbas al segundo piso
		if (u == HOMENAJE - 1 && v == HOMENAJE - 1 && ly <= PISOS[1]) {
			return escalera(Direction.NORTH);
		}
		if (ly == 0) {
			return losa();
		}
		if (ly == PISOS[0] || ly == PISOS[1] || ly == ALTO_HOMENAJE) {
			return madera();
		}
		if (ly < PISOS[0]) {
			return salonDelTrono(u, v, ly);
		}
		if (ly < PISOS[1]) {
			return comedor(u, v, ly - PISOS[0]);
		}
		return dormitorios(u, v, ly - PISOS[1]);
	}

	/** Ventanas a los lados de cada piso; abajo, ninguna en la pared del trono. */
	private static boolean ventana(int u, int v, int ly) {
		int largo = Math.abs(u) == HOMENAJE ? v : u;
		if (Math.abs(largo) != 3 || Math.abs(u) == HOMENAJE && Math.abs(v) == HOMENAJE) {
			return false;
		}
		if (ly == 2 || ly == 3) {
			return v != HOMENAJE;
		}
		return ly == PISOS[0] + 2 || ly == PISOS[0] + 3 || ly == PISOS[1] + 2 || ly == PISOS[1] + 3;
	}

	/** Planta baja: alfombra hasta el estrado con el trono, mesa del consejo a un lado y biblioteca al otro. */
	private BlockState salonDelTrono(int u, int v, int ly) {
		int au = Math.abs(u);
		if (v >= 3 && au <= 3) {
			if (ly == 1) {
				return moldura();
			}
			if (ly == 2) {
				if (u == 0 && v == 4) {
					return peldano(maderaEscalon(), Direction.SOUTH, false);
				}
				return u == 0 && v == 5 ? Blocks.GOLD_BLOCK.defaultBlockState() : tinte("carpet", Blocks.RED_CARPET);
			}
			if (ly == 3 && u == 0 && v == 5) {
				return Blocks.GOLD_BLOCK.defaultBlockState();
			}
			if (ly == 4 && au == 2 && v == 5) {
				return estandarte(Direction.NORTH);
			}
			return aire();
		}
		boolean consejo = v >= -3 && v <= 1;
		if (ly == 1) {
			if (au <= 1 && v <= 2) {
				return Blocks.RED_CARPET.defaultBlockState();
			}
			if (u == -4 && consejo) {
				return valla();
			}
			if (u == -5 && consejo) {
				return peldano(maderaEscalon(), Direction.WEST, false);
			}
			if (u == -3 && consejo) {
				return peldano(maderaEscalon(), Direction.EAST, false);
			}
			if (u == -4 && v == -4) {
				return Blocks.CARTOGRAPHY_TABLE.defaultBlockState();
			}
		}
		if (ly == 2 && u == -4 && consejo) {
			return tinte("carpet", Blocks.RED_CARPET);
		}
		if ((ly == 1 || ly == 2) && u == HOMENAJE - 1 && v >= -4 && v <= 1) {
			return Blocks.BOOKSHELF.defaultBlockState();
		}
		if (ly == PISOS[0] - 1 && au == 3 && (v == -3 || v == 1)) {
			return farolColgado();
		}
		return aire();
	}

	/** Primer piso: comedor con la mesa larga delante y la cocina detrás de un tabique. */
	private BlockState comedor(int u, int v, int h) {
		int au = Math.abs(u);
		if (v == 1) {
			return u == 0 && h <= 2 ? aire() : madera();
		}
		if (v < 1) {
			boolean mesa = v >= -4 && v <= -1;
			if (h == 1) {
				if (u == 0 && mesa) {
					return valla();
				}
				if (au == 1 && mesa) {
					return peldano(maderaEscalon(), u > 0 ? Direction.EAST : Direction.WEST, false);
				}
				if (u == 0 && v == -5) {
					return peldano(maderaEscalon(), Direction.NORTH, false);
				}
				if (u == -5 && v >= -4 && v <= -2) {
					return Blocks.BARREL.defaultBlockState();
				}
			}
			if (h == 2 && u == 0 && mesa) {
				return Blocks.WHITE_CARPET.defaultBlockState();
			}
			return h == 4 && au == 3 && v == -3 ? farolColgado() : aire();
		}
		if (h == 1) {
			if (v == HOMENAJE - 1) {
				switch (u) {
					case -4:
						return mirando(Blocks.SMOKER, Direction.NORTH);
					case -3:
						return mirando(Blocks.FURNACE, Direction.NORTH);
					case -2:
						return Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3);
					case -1, 2:
						return Blocks.BARREL.defaultBlockState();
					case 1:
						return Blocks.CRAFTING_TABLE.defaultBlockState();
					default:
						break;
				}
			}
			if (u == -5 && v >= 2 && v <= 4) {
				return v == 3 ? Blocks.BARREL.defaultBlockState() : Blocks.HAY_BLOCK.defaultBlockState();
			}
		}
		return h == 4 && u == 0 && v == 3 ? farolColgado() : aire();
	}

	/** Segundo piso: la alcoba del señor (cama doble del color del reino) y la de la corte, con un tabique. */
	private BlockState dormitorios(int u, int v, int h) {
		if (u == 0) {
			return v == -4 && h <= 2 ? aire() : madera();
		}
		if (h == 1) {
			boolean cabecera = v == HOMENAJE - 2;
			if ((u == -2 || u == -3) && (cabecera || v == HOMENAJE - 3)) {
				return cama(tinteBloque("bed", Blocks.RED_BED), cabecera);
			}
			if ((u == 2 || u == 4) && (cabecera || v == HOMENAJE - 3)) {
				return cama(Blocks.WHITE_BED, cabecera);
			}
			if ((u == -1 || u == 3) && v == HOMENAJE - 1) {
				return mirando(Blocks.CHEST, Direction.NORTH);
			}
			if (u >= -4 && u <= -1 && v >= -2 && v <= 1) {
				return tinte("carpet", Blocks.RED_CARPET);
			}
		}
		return h == 4 && Math.abs(u) == 3 && v == 0 ? farolColgado() : aire();
	}

	/** Las catacumbas: tumbas, calaveras en nichos y faroles de almas. */
	private BlockState cripta(int u, int v, int ly, int anillo, int x, int z) {
		if (ly == -CRIPTA - 1) {
			return base();
		}
		if (u == HOMENAJE - 1 && v == HOMENAJE - 1) {
			return escalera(Direction.NORTH);
		}
		if (anillo == HOMENAJE) {
			return muro(x, z, ly);
		}
		if (anillo == HOMENAJE - 1) {
			// se sale de la escalera por un lado
			if (u == HOMENAJE - 2 && v == HOMENAJE - 1 && ly <= -CRIPTA + 1) {
				return aire();
			}
			boolean lado = Math.abs(u) >= Math.abs(v);
			int largo = lado ? v : u;
			if (ly == -CRIPTA + 1 && Math.floorMod(largo, 2) == 0 && Math.abs(largo) <= 3) {
				Direction adentro = lado ? (u > 0 ? Direction.WEST : Direction.EAST) : (v > 0 ? Direction.NORTH : Direction.SOUTH);
				return mirando(Blocks.SKELETON_WALL_SKULL, adentro);
			}
			return muro(x, z, ly);
		}
		if (Math.abs(u) == 3 && (Math.abs(v) == 2 || Math.abs(v) == 3)) {
			return ly == -CRIPTA ? moldura() : ly == -CRIPTA + 1 ? piedraLosa() : aire();
		}
		if (u == 0 && v == 0) {
			return ly == -CRIPTA ? moldura() : ly == -CRIPTA + 1 ? Blocks.SOUL_LANTERN.defaultBlockState() : aire();
		}
		if (ly == -1) {
			if (Math.abs(u) == 3 && v == 0) {
				return Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
			}
			if (Math.abs(u) == 4 && Math.abs(v) == 4 && !(u == 4 && v == 4)) {
				return Blocks.COBWEB.defaultBlockState();
			}
		}
		return aire();
	}

	/** Tejado a cuatro aguas (cúpula con pretil en el desierto) con aguja y estandarte. */
	private BlockState tejadoHomenaje(int u, int v, int ly, int anillo, int x, int z) {
		int k = ly - ALTO_HOMENAJE - 1;
		boolean centro = u == 0 && v == 0;
		if (estilo == AdornoCapital.DESIERTO) {
			if (anillo > HOMENAJE) {
				return aire();
			}
			if (anillo == HOMENAJE) {
				return k == 0 || k == 1 && almena(x, z) ? muro(x, z, ly) : aire();
			}
			double radio = HOMENAJE - 1.5;
			if (k <= radio && Math.hypot(u, v) <= Math.sqrt(radio * radio - k * k)) {
				return tejado();
			}
			return centro ? aguja(k - (int) radio - 1) : aire();
		}
		int borde = HOMENAJE + 1 - k;
		if (borde < 0) {
			return centro ? aguja(-borde - 1) : aire();
		}
		if (anillo != borde) {
			return aire();
		}
		if (borde == 0 || Math.abs(u) == Math.abs(v)) {
			return tejado();
		}
		Direction alto = Math.abs(u) == borde ? (u > 0 ? Direction.WEST : Direction.EAST) : (v > 0 ? Direction.NORTH : Direction.SOUTH);
		return peldano(tejadoEscalon(), alto, false);
	}

	/** Lo que hay encima de la punta de un tejado, i bloques más arriba: dos de valla y el estandarte. */
	private BlockState aguja(int i) {
		if (i == 0 || i == 1) {
			return valla();
		}
		if (i == 2 && color >= 0) {
			return tinteBloque("banner", Blocks.RED_BANNER).defaultBlockState().setValue(BlockStateProperties.ROTATION_16, rot * 4);
		}
		return aire();
	}

	// ---- el patio ----

	private BlockState patio(int u, int v, int ly) {
		int au = Math.abs(u);
		if (ly == 0) {
			if (au <= 1 && v < 0) {
				return camino();
			}
			return u == POZO_U && v == POZO_V ? Blocks.WATER.defaultBlockState() : PATIO;
		}
		// escaleras de mano al adarve, a los lados de la barbacana
		if (au == BARBACANA + 1 && v == -(MURO - 2) && ly <= ALTO_MURO) {
			return escalera(Direction.SOUTH);
		}
		if (au == HOMENAJE - 1 && v == -(HOMENAJE + 1) && ly == PISOS[1] + 3) {
			return estandarte(Direction.NORTH);
		}
		if (au == 2 && v == -(MURO - 5) && ly == 1) {
			return Blocks.LANTERN.defaultBlockState();
		}
		BlockState b = pozo(u, v, ly);
		if (b == null) {
			b = cobertizo(u, v, ly);
		}
		return b != null ? b : aire();
	}

	private static final int POZO_U = -10;
	private static final int POZO_V = -8;

	private BlockState pozo(int u, int v, int ly) {
		int du = u - POZO_U;
		int dv = v - POZO_V;
		if (Math.abs(du) > 1 || Math.abs(dv) > 1) {
			return null;
		}
		boolean esquina = Math.abs(du) == 1 && Math.abs(dv) == 1;
		return switch (ly) {
			case 1 -> du == 0 && dv == 0 ? aire() : zocalo();
			case 2, 3 -> esquina ? valla() : aire();
			case 4 -> maderaLosa();
			default -> aire();
		};
	}

	/** El establo (oeste) y la herrería (este), dos cobertizos de madera pegados a la muralla. */
	private BlockState cobertizo(int u, int v, int ly) {
		int au = Math.abs(u);
		if (au < 9 || au > MURO - 2 || v < 1 || v > 7) {
			return null;
		}
		if (ly == 4) {
			return maderaLosa();
		}
		if (ly == 5 && au == 9 && (v == 1 || v == 7)) {
			return Blocks.LANTERN.defaultBlockState();
		}
		if (ly > 4) {
			return aire();
		}
		if (au == 9 && (v == 1 || v == 4 || v == 7)) {
			return valla();
		}
		if (ly != 1) {
			return aire();
		}
		if (u < 0) {
			if (au == MURO - 2 && v % 2 == 0) {
				return Blocks.HAY_BLOCK.defaultBlockState();
			}
			return (v == 3 || v == 5) && au >= 10 ? valla() : aire();
		}
		if (au != MURO - 2) {
			return aire();
		}
		return switch (v) {
			case 2 -> Blocks.ANVIL.defaultBlockState();
			case 3 -> Blocks.SMITHING_TABLE.defaultBlockState();
			case 4 -> mirando(Blocks.BLAST_FURNACE, Direction.WEST);
			case 5 -> mirando(Blocks.GRINDSTONE, Direction.WEST).setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
			case 6 -> Blocks.BARREL.defaultBlockState();
			case 7 -> Blocks.LAVA_CAULDRON.defaultBlockState();
			default -> aire();
		};
	}

	// ---- bloques orientados ----

	/** Del lado local (NORTH = hacia la puerta) al del mundo, con el giro del castillo. */
	private Direction mundo(Direction local) {
		Direction d = local;
		for (int i = 0; i < rot; i++) {
			d = d.getClockWise();
		}
		return d;
	}

	private BlockState mirando(Block b, Direction local) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, mundo(local));
	}

	/** Un peldaño con el lado alto hacia alto (en local); arriba, del revés (ménsulas y arcos). */
	private BlockState peldano(Block b, Direction alto, boolean arriba) {
		return mirando(b, alto).setValue(BlockStateProperties.HALF, arriba ? Half.TOP : Half.BOTTOM);
	}

	/** Una escalera de mano que mira hacia lado (en local), es decir, pegada a la pared del lado contrario. */
	private BlockState escalera(Direction lado) {
		return mirando(Blocks.LADDER, lado);
	}

	private BlockState puerta(boolean abajo) {
		return mirando(maderaPuerta(), Direction.NORTH).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
				abajo ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
	}

	/** Media cama: la cabecera hacia el fondo (v positiva). */
	private BlockState cama(Block b, boolean cabecera) {
		return mirando(b, Direction.SOUTH).setValue(BlockStateProperties.BED_PART, cabecera ? BedPart.HEAD : BedPart.FOOT);
	}

	/** Estandarte del reino colgado de la pared, mirando hacia lado (en local); sin color, nada. */
	private BlockState estandarte(Direction lado) {
		return color >= 0 ? mirando(tinteBloque("wall_banner", Blocks.RED_WALL_BANNER), lado) : aire();
	}

	private static BlockState farolColgado() {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	}

	private static boolean almena(int x, int z) {
		return Math.floorMod(x + z, 2) == 0;
	}

	/** El bloque del color del reino de ese tipo (wool, wall_banner…), o otro si no se sabe el color. */
	private Block tinteBloque(String tipo, Block sinColor) {
		if (color < 0) {
			return sinColor;
		}
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(DyeColor.byId(color).getName() + "_" + tipo));
	}

	private BlockState tinte(String tipo, Block sinColor) {
		return tinteBloque(tipo, sinColor).defaultBlockState();
	}

	// ---- materiales según la cultura ----

	/** Piedra del muro, con alguna variación (siempre la misma en cada sitio). */
	private BlockState muro(int x, int z, int y) {
		int h = Math.floorMod(Geo.hash(x, z, y0 + y), 9);
		return (switch (estilo) {
			case AdornoCapital.DESIERTO -> h == 0 ? Blocks.SANDSTONE : h == 1 ? Blocks.SMOOTH_SANDSTONE : Blocks.CUT_SANDSTONE;
			case AdornoCapital.SABANA -> h < 2 ? Blocks.PACKED_MUD : Blocks.MUD_BRICKS;
			case AdornoCapital.TAIGA -> h == 0 ? Blocks.MOSSY_COBBLESTONE : h == 1 ? Blocks.COBBLESTONE : h == 2 ? Blocks.MOSSY_STONE_BRICKS
					: Blocks.STONE_BRICKS;
			case AdornoCapital.NIEVE -> h == 0 ? Blocks.CRACKED_STONE_BRICKS : h == 1 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS;
			default -> h == 0 ? Blocks.MOSSY_STONE_BRICKS : h == 1 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS;
		}).defaultBlockState();
	}

	/** Molduras, sillares de las esquinas, el estrado y las tumbas. */
	private BlockState moldura() {
		return (switch (estilo) {
			case AdornoCapital.DESIERTO -> Blocks.CHISELED_SANDSTONE;
			case AdornoCapital.SABANA -> Blocks.TERRACOTTA;
			case AdornoCapital.TAIGA -> Blocks.POLISHED_DEEPSLATE;
			case AdornoCapital.NIEVE -> Blocks.POLISHED_DIORITE;
			default -> Blocks.POLISHED_ANDESITE;
		}).defaultBlockState();
	}

	private BlockState zocalo() {
		return (switch (estilo) {
			case AdornoCapital.DESIERTO -> Blocks.SMOOTH_SANDSTONE;
			case AdornoCapital.SABANA -> Blocks.PACKED_MUD;
			default -> Blocks.COBBLESTONE;
		}).defaultBlockState();
	}

	private Block zocaloEscalon() {
		return switch (estilo) {
			case AdornoCapital.DESIERTO -> Blocks.SMOOTH_SANDSTONE_STAIRS;
			case AdornoCapital.SABANA -> Blocks.MUD_BRICK_STAIRS;
			default -> Blocks.COBBLESTONE_STAIRS;
		};
	}

	private Block piedraEscalon() {
		return switch (estilo) {
			case AdornoCapital.DESIERTO -> Blocks.SANDSTONE_STAIRS;
			case AdornoCapital.SABANA -> Blocks.MUD_BRICK_STAIRS;
			default -> Blocks.STONE_BRICK_STAIRS;
		};
	}

	private BlockState piedraLosa() {
		return (switch (estilo) {
			case AdornoCapital.DESIERTO -> Blocks.SANDSTONE_SLAB;
			case AdornoCapital.SABANA -> Blocks.MUD_BRICK_SLAB;
			default -> Blocks.STONE_BRICK_SLAB;
		}).defaultBlockState();
	}

	private BlockState tejado() {
		return (switch (estilo) {
			case AdornoCapital.DESIERTO -> Blocks.SMOOTH_SANDSTONE;
			case AdornoCapital.SABANA -> Blocks.ACACIA_PLANKS;
			case AdornoCapital.TAIGA -> Blocks.SPRUCE_PLANKS;
			default -> Blocks.DEEPSLATE_TILES;
		}).defaultBlockState();
	}

	private Block tejadoEscalon() {
		return switch (estilo) {
			case AdornoCapital.DESIERTO -> Blocks.SMOOTH_SANDSTONE_STAIRS;
			case AdornoCapital.SABANA -> Blocks.ACACIA_STAIRS;
			case AdornoCapital.TAIGA -> Blocks.SPRUCE_STAIRS;
			default -> Blocks.DEEPSLATE_TILE_STAIRS;
		};
	}

	private BlockState base() {
		return zocalo();
	}

	/** El suelo del salón del trono. */
	private BlockState losa() {
		return (estilo == AdornoCapital.DESIERTO || estilo == AdornoCapital.SABANA ? Blocks.SMOOTH_SANDSTONE : Blocks.SMOOTH_STONE)
				.defaultBlockState();
	}

	private BlockState camino() {
		return (estilo == AdornoCapital.DESIERTO ? Blocks.SMOOTH_SANDSTONE : Blocks.DIRT_PATH).defaultBlockState();
	}

	private boolean conifera() {
		return estilo == AdornoCapital.TAIGA || estilo == AdornoCapital.NIEVE;
	}

	private boolean acacia() {
		return estilo == AdornoCapital.SABANA || estilo == AdornoCapital.DESIERTO;
	}

	private BlockState madera() {
		return (conifera() ? Blocks.SPRUCE_PLANKS : acacia() ? Blocks.ACACIA_PLANKS : Blocks.DARK_OAK_PLANKS).defaultBlockState();
	}

	private Block maderaEscalon() {
		return conifera() ? Blocks.SPRUCE_STAIRS : acacia() ? Blocks.ACACIA_STAIRS : Blocks.DARK_OAK_STAIRS;
	}

	private BlockState maderaLosa() {
		return (conifera() ? Blocks.SPRUCE_SLAB : acacia() ? Blocks.ACACIA_SLAB : Blocks.DARK_OAK_SLAB).defaultBlockState();
	}

	private BlockState valla() {
		return (conifera() ? Blocks.SPRUCE_FENCE : acacia() ? Blocks.ACACIA_FENCE : Blocks.DARK_OAK_FENCE).defaultBlockState();
	}

	private Block maderaPuerta() {
		return conifera() ? Blocks.SPRUCE_DOOR : acacia() ? Blocks.ACACIA_DOOR : Blocks.DARK_OAK_DOOR;
	}

	private static BlockState aire() {
		return Blocks.AIR.defaultBlockState();
	}
}
