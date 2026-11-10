package es.hulillo.destinos;

import java.util.List;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Un trozo de la muralla de una capital (un tramo recto o una torre), según lo rico que es el reino: 0 empalizada de
 * troncos, 1 muralla de piedra con almenas, 2 muralla de ladrillo de piedra más gruesa y alta, con torres. En el
 * desierto, de arenisca.
 *
 * El trazado se hace una vez, en el plano: el contorno de lo construido (con un margen, parándose en el borde de los
 * cortados) se vuelve una línea cerrada de vértices cada PASO_TRAMO bloques, y cada vértice lleva la altura del
 * adarve, suavizada a lo largo del anillo y sin cuestas de más de una por cada dos bloques. Cada tramo une dos
 * vértices: por dentro, la cara de la línea; el grueso, hacia fuera. Así la muralla va por arriba lisa (se puede
 * patrullar), por abajo baja con cimiento hasta el suelo y, donde el suelo se hunde mucho (un barranco, una zanja),
 * lo cruza con arcos sobre pilares en vez de bajar por él. Se para en las casas, deja paso bajo las calles (la
 * empalizada las cierra), cruza el agua con reja y deja libres las puertas.
 */
public final class MurallaCapital extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) MurallaCapital::new;
	/**
	 * Radio interior del anillo según el nivel: rodea el centro de la capital, más ancho cuanto más rica (la rica
	 * necesita sitio para el castillo, el mercado y las casas nobles).
	 */
	private static final int[] RADIOS = {76, 88, 108};
	/** Cuántos arcos tiene el contorno (los radios que se calculan para ceñir la muralla a lo construido). */
	static final int ARCOS = 64;
	private static final int[] ALTO = {4, 5, 7};
	private static final int[] GRUESO = {1, 2, 3};
	/** Cada cuántos bloques va un vértice del trazado. */
	private static final int PASO_TRAMO = 6;
	/** Torres: cada cuántos bloques de muralla como mucho (normal, rica); y en los quiebros, desde la mitad. */
	private static final int[] ESPACIO_TORRES = {0, 44, 30};
	private static final double QUIEBRO = Math.toRadians(22);
	private static final int RADIO_TORRE = 3;
	/** El alto libre bajo el dintel de las puertas. */
	private static final int PASO = 4;
	/** Lo que se baja como mucho con cimiento macizo; más hondo, arcos. */
	private static final int HONDO_MACIZO = 6;
	/** Cada cuántos bloques va un pilar bajo los arcos, y lo gruesos que son. */
	private static final int VANO = 7;
	private static final int PILAR = 2;
	/** Lo hondo que puede ser un hueco bajo los arcos (más, la muralla se corta). */
	private static final int HONDO_ARCOS = 28;
	/** Hasta dónde se mira alrededor de cada vértice el suelo más alto (el de arriba de un cortado o una ladera). */
	private static final int ENTERRADA = 2;

	static int radio(int nivel) {
		return RADIOS[Math.clamp(nivel, 0, RADIOS.length - 1)];
	}

	static int alto(int nivel) {
		return ALTO[Math.clamp(nivel, 0, ALTO.length - 1)];
	}

	static int grueso(int nivel) {
		return GRUESO[Math.clamp(nivel, 0, GRUESO.length - 1)];
	}

	private final int cx;
	private final int cz;
	private final int nivel;
	/** Torre (su caja dice dónde y su minY la altura del adarve menos ALTO) o tramo de (xa, za, ya) a (xb, zb, yb). */
	private final boolean torre;
	private final int xa;
	private final int za;
	private final int ya;
	private final int xb;
	private final int zb;
	private final int yb;
	/** Lo que se lleva andado del anillo al empezar el tramo (para repartir los pilares de los arcos). */
	private final int andado;
	/** El castillo, si lo hay: centro y medio lado de su muralla (ahí la de la ciudad se para y empalma con ella). */
	private final int kx;
	private final int kz;
	private final int km;
	/** Cajas (x0, z0, x1, z1 seguidos) donde no se pone muralla: delante y detrás de las puertas. */
	private final int[] huecos;

	private MurallaCapital(int cx, int cz, int nivel, boolean torre, int[] tramo, int andado, int[] castillo, int[] huecos, BoundingBox caja) {
		super(TIPO, 0, caja);
		this.cx = cx;
		this.cz = cz;
		this.nivel = nivel;
		this.torre = torre;
		this.xa = tramo[0];
		this.za = tramo[1];
		this.ya = tramo[2];
		this.xb = tramo[3];
		this.zb = tramo[4];
		this.yb = tramo[5];
		this.andado = andado;
		this.kx = castillo == null ? 0 : castillo[0];
		this.kz = castillo == null ? 0 : castillo[1];
		this.km = castillo == null ? -1 : castillo[2];
		this.huecos = huecos;
	}

	private MurallaCapital(CompoundTag t) {
		super(TIPO, t);
		cx = t.getIntOr("cx", 0);
		cz = t.getIntOr("cz", 0);
		nivel = Math.clamp(t.getIntOr("nivel", 0), 0, ALTO.length - 1);
		// las piezas de antes (arcos por ángulo, sin "v") ya no se ponen
		torre = t.getIntOr("v", 0) == 2 && t.getBooleanOr("torre", false);
		xa = t.getIntOr("xa", 0);
		za = t.getIntOr("za", 0);
		ya = t.getIntOr("ya", Integer.MIN_VALUE);
		xb = t.getIntOr("xb", 0);
		zb = t.getIntOr("zb", 0);
		yb = t.getIntOr("yb", Integer.MIN_VALUE);
		andado = t.getIntOr("andado", 0);
		kx = t.getIntOr("kx", 0);
		kz = t.getIntOr("kz", 0);
		km = t.getIntOr("km", -1);
		huecos = t.getIntArray("huecos").orElse(new int[0]);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "muralla"), TIPO);
	}

	/** Lo que puede bajar o subir el terreno hacia fuera antes de que la muralla se pare en el borde (un cortado). */
	private static final int CORTADO = 5;

	/**
	 * El radio interior de la muralla en cada uno de los ARCOS: lo justo para dejar dentro las cajas dadas (con un
	 * margen), nunca menos de minimo ni más de maximo, y suavizado para que no haga picos. Si hacia fuera hay un
	 * cortado (el pueblo en una meseta, o al pie de un monte), la muralla se queda en el borde y no baja (o sube) por
	 * él: alto da la altura del terreno.
	 */
	static int[] contorno(int cx, int cz, List<BoundingBox> cajas, int margen, int minimo, int maximo, IntBinaryOperator alto,
			java.util.function.BiPredicate<Integer, Integer> agua) {
		double[] r = new double[ARCOS];
		double[] justo = new double[ARCOS];
		java.util.Arrays.fill(r, minimo);
		for (BoundingBox b : cajas) {
			for (int x = b.minX(); x <= b.maxX() + 3; x += 4) {
				for (int z = b.minZ(); z <= b.maxZ() + 3; z += 4) {
					int px = Math.min(x, b.maxX());
					int pz = Math.min(z, b.maxZ());
					double d = Math.hypot(px - cx, pz - cz) + margen;
					int i = Math.floorMod((int) Math.floor(Math.toDegrees(Math.atan2(pz - cz, px - cx)) / 360 * ARCOS), ARCOS);
					r[i] = Math.max(r[i], d);
					justo[i] = Math.max(justo[i], d);
				}
			}
		}
		// un arco no puede quedar muy por dentro de sus vecinos (si no, la muralla se mete en pico entre dos barrios)
		double[] crudo = r.clone();
		for (int i = 0; i < ARCOS; i++) {
			r[i] = Math.max(crudo[i], Math.max(crudo[(i + 1) % ARCOS], crudo[(i + ARCOS - 1) % ARCOS]) - 4);
		}
		// y suave: media con los vecinos, sin bajar nunca de lo que hace falta para dejar las casas dentro
		for (int pasada = 0; pasada < 3; pasada++) {
			double[] g = r.clone();
			for (int i = 0; i < ARCOS; i++) {
				g[i] = Math.max(crudo[i], (r[(i + ARCOS - 1) % ARCOS] + 2 * r[i] + r[(i + 1) % ARCOS]) / 4);
			}
			r = g;
		}
		int[] llenos = new int[ARCOS];
		int[] radios = new int[ARCOS];
		for (int i = 0; i < ARCOS; i++) {
			llenos[i] = (int) Math.round(Math.clamp(r[i], minimo, maximo));
			radios[i] = borde(cx, cz, i, (int) Math.ceil(justo[i]), llenos[i], alto, agua);
		}
		// un recorte suelto (un arco solo, con los dos vecinos sin recortar) no es el borde de una meseta sino una
		// zanja o un barranquillo estrecho: ahí la muralla seguía su radio y se metía en pico hacia dentro, bajando
		// por la zanja. Se deshace y la muralla pasa por encima como si nada.
		int[] cortado = radios.clone();
		for (int i = 0; i < ARCOS; i++) {
			boolean yo = cortado[i] < llenos[i] - 2;
			boolean antes = cortado[(i + ARCOS - 1) % ARCOS] < llenos[(i + ARCOS - 1) % ARCOS] - 2;
			boolean despues = cortado[(i + 1) % ARCOS] < llenos[(i + 1) % ARCOS] - 2;
			if (yo && !antes && !despues) {
				radios[i] = llenos[i];
			}
		}
		// y entre arcos vecinos, nunca un salto de más de 6 hacia dentro (si no, la muralla hace dientes de sierra y
		// se cuela entre las casas); sin pasar nunca de lo que dejó el borde de un cortado de verdad
		for (int pasada = 0; pasada < 4; pasada++) {
			for (int i = 0; i < ARCOS; i++) {
				int vecino = Math.max(radios[(i + ARCOS - 1) % ARCOS], radios[(i + 1) % ARCOS]);
				if (radios[i] < vecino - 6) {
					radios[i] = Math.min(llenos[i], Math.max(radios[i], Math.min(vecino - 6, cortado[i] + 6)));
				}
			}
		}
		return radios;
	}

	/**
	 * El radio del arco i recortado al borde de un cortado: desde lo justo para las casas (o el centro, si por ahí no
	 * hay ninguna) hacia fuera, el primer punto en que el terreno ha bajado o subido más de CORTADO, un poco antes.
	 */
	private static int borde(int cx, int cz, int i, int justo, int radio, IntBinaryOperator alto, java.util.function.BiPredicate<Integer, Integer> agua) {
		double a = (i + 0.5) / ARCOS * 2 * Math.PI;
		double ux = Math.cos(a);
		double uz = Math.sin(a);
		int desde = Math.max(justo, 8);
		if (desde >= radio) {
			return radio;
		}
		int h = alto.applyAsInt(cx + (int) Math.round(ux * desde), cz + (int) Math.round(uz * desde));
		for (int rr = desde + 2; rr <= radio; rr += 2) {
			int px = cx + (int) Math.round(ux * rr);
			int pz = cz + (int) Math.round(uz * rr);
			// un río o la orilla del mar: la muralla va por la orilla, sin cruzarlo
			if (agua.test(px, pz)) {
				return Math.max(justo, rr - 3);
			}
			int y = alto.applyAsInt(px, pz);
			if (Math.abs(y - h) > CORTADO && sigue(cx, cz, ux, uz, rr, h, y, alto)) {
				return Math.max(justo, rr - 3);
			}
		}
		return radio;
	}

	/**
	 * Si el desnivel encontrado en rr sigue más allá (un cortado de verdad: el terreno no vuelve a la altura de la
	 * ciudad en los siguientes 10 bloques) y no es una zanja o un hoyo que se cruza de un salto.
	 */
	private static boolean sigue(int cx, int cz, double ux, double uz, int rr, int h, int y, IntBinaryOperator alto) {
		boolean baja = y < h;
		for (int mas = 4; mas <= 10; mas += 3) {
			int y2 = alto.applyAsInt(cx + (int) Math.round(ux * (rr + mas)), cz + (int) Math.round(uz * (rr + mas)));
			if (baja ? y2 >= h - 2 : y2 <= h + 2) {
				return false;
			}
		}
		return true;
	}

	/** El radio interior de la muralla en el ángulo a (décimas de grado), entre los de los arcos. */
	static double radioEn(int[] radios, double a) {
		double f = Math.floorMod((long) Math.floor(a * 1000), 3600_000L) / 1000.0 / 3600 * ARCOS;
		int i = (int) Math.floor(f);
		double t = f - i;
		return radios[i % ARCOS] * (1 - t) + radios[(i + 1) % ARCOS] * t;
	}

	/**
	 * Las piezas de la muralla alrededor de (cx, cz) con los radios del contorno (null: el círculo de su nivel);
	 * alturaEn da la altura del suelo. Dentro de la muralla del castillo (centro y medio lado en castillo, o null) no
	 * se pone nada: la de la ciudad llega hasta la suya y ahí empalma.
	 */
	static void piezas(int cx, int cz, int nivel, int[] radios, IntBinaryOperator alturaEn, int[] castillo, int[] huecos, List<StructurePiece> piezas) {
		if (radios == null) {
			radios = new int[ARCOS];
			java.util.Arrays.fill(radios, radio(nivel));
		}
		int[][] v = vertices(cx, cz, radios);
		int[] y = adarve(v, nivel, alturaEn);
		// las torres antes que los tramos: así el tramo ve la torre ya puesta y no la atraviesa
		torres(cx, cz, nivel, v, y, castillo, huecos, piezas);
		tramos(cx, cz, nivel, v, y, castillo, huecos, piezas);
	}

	/** Los vértices de la línea de la muralla (su cara de dentro), cada PASO_TRAMO bloques más o menos. */
	private static int[][] vertices(int cx, int cz, int[] radios) {
		double medio = java.util.Arrays.stream(radios).average().orElse(RADIOS[0]);
		int n = Math.max(24, (int) Math.round(2 * Math.PI * medio / PASO_TRAMO));
		int[][] v = new int[n][2];
		for (int k = 0; k < n; k++) {
			double a = k * 3600.0 / n;
			double r = radioEn(radios, a);
			double ang = Math.toRadians(a / 10);
			v[k][0] = cx + (int) Math.round(Math.cos(ang) * r);
			v[k][1] = cz + (int) Math.round(Math.sin(ang) * r);
		}
		return v;
	}

	/**
	 * La altura del pie de la muralla en cada vértice: la del suelo más alto en su sitio y a ENTERRADA bloques
	 * alrededor (en el borde de un cortado, la de arriba), suavizada a lo largo del anillo pero nunca más de un
	 * bloque por debajo de ese suelo (el suavizado no la mete en las lomas), y sin cuestas de más de una por cada dos
	 * bloques (lo que sobra se sube: más cimiento o arcos, nunca la muralla metida en el suelo).
	 */
	private static int[] adarve(int[][] v, int nivel, IntBinaryOperator alturaEn) {
		int n = v.length;
		double[] h = new double[n];
		int[] alto = new int[n];
		for (int k = 0; k < n; k++) {
			int x = v[k][0];
			int z = v[k][1];
			alto[k] = Math.max(Math.max(alturaEn.applyAsInt(x, z), alturaEn.applyAsInt(x + ENTERRADA, z)),
					Math.max(alturaEn.applyAsInt(x - ENTERRADA, z),
							Math.max(alturaEn.applyAsInt(x, z + ENTERRADA), alturaEn.applyAsInt(x, z - ENTERRADA))));
			h[k] = alto[k];
		}
		for (int pasada = 0; pasada < 3; pasada++) {
			double[] g = h.clone();
			for (int k = 0; k < n; k++) {
				g[k] = (h[(k + n - 1) % n] + 2 * h[k] + h[(k + 1) % n]) / 4;
			}
			h = g;
		}
		int[] y = new int[n];
		for (int k = 0; k < n; k++) {
			y[k] = Math.max((int) Math.round(h[k]), alto[k] - 1);
		}
		for (boolean cambia = true; cambia;) {
			cambia = false;
			for (int k = 0; k < n; k++) {
				int s = (k + 1) % n;
				int cuesta = Math.max(1, (int) Math.hypot(v[s][0] - v[k][0], v[s][1] - v[k][1]) / 2);
				if (y[s] < y[k] - cuesta) {
					y[s] = y[k] - cuesta;
					cambia = true;
				} else if (y[k] < y[s] - cuesta) {
					y[k] = y[s] - cuesta;
					cambia = true;
				}
			}
		}
		return y;
	}

	/** Torres en los quiebros de la línea y, si no, cada ESPACIO_TORRES bloques (las pobres, sin torres). */
	private static void torres(int cx, int cz, int nivel, int[][] v, int[] y, int[] castillo, int[] huecos, List<StructurePiece> piezas) {
		int espacio = ESPACIO_TORRES[nivel];
		if (espacio <= 0) {
			return;
		}
		int n = v.length;
		double andado = espacio / 2.0;
		int medio = GRUESO[nivel] / 2;
		for (int k = 0; k < n; k++) {
			int[] a = v[(k + n - 1) % n];
			int[] b = v[k];
			int[] c = v[(k + 1) % n];
			andado += Math.hypot(b[0] - a[0], b[1] - a[1]);
			double giro = Math.abs(Math.atan2(c[1] - b[1], c[0] - b[0]) - Math.atan2(b[1] - a[1], b[0] - a[0]));
			giro = Math.min(giro, 2 * Math.PI - giro);
			if (andado < espacio && !(giro > QUIEBRO && andado >= espacio / 2.0)) {
				continue;
			}
			double[] fuera = fuera(cx, cz, b[0], b[1]);
			int tx = b[0] + (int) Math.round(fuera[0] * medio);
			int tz = b[1] + (int) Math.round(fuera[1] * medio);
			if (enCastillo(castillo, tx, tz, RADIO_TORRE + 2) || enHueco(huecos, tx, tz, RADIO_TORRE + 4)) {
				continue;
			}
			andado = 0;
			int r = RADIO_TORRE + 1;
			piezas.add(new MurallaCapital(cx, cz, nivel, true, new int[6], 0, castillo, huecos,
					new BoundingBox(tx - r, y[k], tz - r, tx + r, y[k] + 1, tz + r)));
		}
	}

	/** Un tramo por cada par de vértices seguidos (menos los que caen enteros dentro del castillo). */
	private static void tramos(int cx, int cz, int nivel, int[][] v, int[] y, int[] castillo, int[] huecos, List<StructurePiece> piezas) {
		int n = v.length;
		int borde = GRUESO[nivel] + 2;
		double andado = 0;
		for (int k = 0; k < n; k++) {
			int[] a = v[k];
			int[] b = v[(k + 1) % n];
			int s = (k + 1) % n;
			double largo = Math.hypot(b[0] - a[0], b[1] - a[1]);
			boolean tapa = enCastillo(castillo, a[0], a[1], -2) && enCastillo(castillo, b[0], b[1], -2);
			if (!tapa) {
				BoundingBox caja = new BoundingBox(Math.min(a[0], b[0]) - borde, Math.min(y[k], y[s]), Math.min(a[1], b[1]) - borde,
						Math.max(a[0], b[0]) + borde, Math.max(y[k], y[s]) + 1, Math.max(a[1], b[1]) + borde);
				piezas.add(new MurallaCapital(cx, cz, nivel, false, new int[] {a[0], a[1], y[k], b[0], b[1], y[s]}, (int) Math.round(andado),
						castillo, huecos, caja));
			}
			andado += largo;
		}
	}

	/** La dirección hacia fuera del anillo en (x, z), de largo 1. */
	private static double[] fuera(int cx, int cz, double x, double z) {
		double d = Math.max(1e-6, Math.hypot(x - cx, z - cz));
		return new double[] {(x - cx) / d, (z - cz) / d};
	}

	private static boolean enHueco(int[] huecos, int x, int z, int margen) {
		for (int k = 0; k + 3 < huecos.length; k += 4) {
			if (x >= huecos[k] - margen && x <= huecos[k + 2] + margen && z >= huecos[k + 1] - margen && z <= huecos[k + 3] + margen) {
				return true;
			}
		}
		return false;
	}

	private static boolean enCastillo(int[] castillo, int x, int z, int margen) {
		return castillo != null && Math.max(Math.abs(x - castillo[0]), Math.abs(z - castillo[1])) <= castillo[2] + margen;
	}

	/** No cuenta para el allanado: va columna a columna sobre el suelo (y sobre el agua, sin echar tierra en ella). */
	@Override
	public boolean isCloseToChunk(ChunkPos chunk, int distancia) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		t.putInt("v", 2);
		t.putInt("cx", cx);
		t.putInt("cz", cz);
		t.putInt("nivel", nivel);
		t.putBoolean("torre", torre);
		t.putInt("xa", xa);
		t.putInt("za", za);
		t.putInt("ya", ya);
		t.putInt("xb", xb);
		t.putInt("zb", zb);
		t.putInt("yb", yb);
		t.putInt("andado", andado);
		t.putInt("kx", kx);
		t.putInt("kz", kz);
		t.putInt("km", km);
		t.putIntArray("huecos", huecos);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar,
			BoundingBox caja, ChunkPos chunk, BlockPos pivote) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		if (torre) {
			torre(level, caja, pos);
		} else if (ya != Integer.MIN_VALUE) {
			tramo(level, caja, pos);
		}
	}

	/**
	 * El tramo de (xa, za) a (xb, zb): cada columna a menos del grueso de la línea, hacia fuera, con el adarve a la
	 * altura que toca en ese punto. En las puntas, redondeado: así los tramos se empalman sin huecos en los quiebros.
	 */
	private void tramo(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos) {
		BoundingBox mia = getBoundingBox();
		int grueso = GRUESO[nivel];
		double dx = xb - xa;
		double dz = zb - za;
		double largo = Math.max(1e-6, Math.hypot(dx, dz));
		double[] fuera = fuera(cx, cz, (xa + xb) / 2.0, (za + zb) / 2.0);
		// la normal del tramo, hacia fuera del anillo
		double nx = -dz / largo;
		double nz = dx / largo;
		if (nx * fuera[0] + nz * fuera[1] < 0) {
			nx = -nx;
			nz = -nz;
		}
		for (int x = Math.max(mia.minX(), caja.minX()); x <= Math.min(mia.maxX(), caja.maxX()); x++) {
			for (int z = Math.max(mia.minZ(), caja.minZ()); z <= Math.min(mia.maxZ(), caja.maxZ()); z++) {
				if (km >= 0 && Math.max(Math.abs(x - kx), Math.abs(z - kz)) <= km || enHueco(huecos, x, z, 0)) {
					continue;
				}
				double t = Math.clamp(((x - xa) * dx + (z - za) * dz) / (largo * largo), 0, 1);
				double ox = x - (xa + t * dx);
				double oz = z - (za + t * dz);
				double hondo = ox * nx + oz * nz;
				if (hondo < -0.5 || hondo >= grueso - 0.5 || Math.hypot(ox, oz) >= grueso + 0.5) {
					continue;
				}
				int pie = (int) Math.round(ya + t * (yb - ya));
				// empalizada en punta (un tronco sí y otro no, más alto); almenas en el borde de fuera (por dentro queda
				// el adarve, el paso de ronda por lo alto de la muralla)
				boolean almena = (x + z) % 2 == 0 && (nivel == 0 || hondo >= grueso - 1.5);
				// los árboles fuera; pero no la propia empalizada de un tramo vecino (troncos sobre tierra: la tala se
				// la llevaría entera)
				if (!esMuralla(level.getBlockState(pos.set(x, SueloNatural.cima(level, x, z), z)))) {
					SueloNatural.tala(level, caja, x, z);
				}
				columna(level, pos, x, z, pie, pie + ALTO[nivel] + (almena ? 1 : 0), andado + t * largo);
			}
		}
	}

	/**
	 * Una columna de la muralla con el pie en pie y lo alto en arriba (a lo largo del anillo en andado, para los
	 * pilares). Sobre el agua, reja; sobre una casa, nada; sobre una calle, la puerta: paso de PASO de alto y encima el
	 * dintel (la empalizada, que no da para tanto, la cierra). Si el suelo queda por debajo del pie, cimiento hasta él;
	 * si queda muy por debajo, arco entre pilares.
	 */
	private void columna(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int pie, int arriba, double andado) {
		BlockState s = SueloNatural.superficie(level, pos, x, z);
		int suelo = pos.getY();
		if (s.is(Blocks.WATER)) {
			reja(level, pos, Math.max(arriba - suelo, ALTO[nivel]));
			return;
		}
		boolean arena = SueloNatural.arena(s) || s.is(Blocks.SMOOTH_SANDSTONE);
		BlockState bloque = bloque(arena);
		if (calle(s) && nivel > 0 && suelo >= pie - 2) {
			for (int y = suelo + 1; y <= suelo + PASO; y++) {
				libre(level, pos.set(x, y, z));
			}
			pon(level, pos, x, z, suelo + PASO + 1, Math.max(arriba, suelo + PASO + 2), bloque);
			return;
		}
		// sobre cualquier suelo (tierra, piedra, caminos, empedrados, muros de contención del allanado) la muralla
		// sigue; solo se aparta de lo construido de verdad: algo que no es suelo natural muy por encima de su pie (un
		// tejado), y entonces se para al llegar a ello sin dejar el resto del tramo
		if (!calle(s) && !SueloNatural.natural(s) && suelo > pie + 2) {
			return;
		}
		// suelo natural por encima del pie (una loma, la ladera de arriba): la muralla sube con él, como mucho un
		// bloque enterrada, para que nunca quede a ras de suelo con las almenas asomando
		int sube = Math.max(0, suelo - 1 - pie);
		pie += sube;
		arriba += sube;
		for (int y = arriba + 1; y <= suelo; y++) {
			libre(level, pos.set(x, y, z));
		}
		int hondo = pie - suelo;
		int desde;
		if (hondo <= HONDO_MACIZO) {
			desde = Math.min(suelo, pie);
		} else {
			// arco: pilares cada VANO hasta el suelo (o hasta HONDO_ARCOS); entre ellos, la bóveda baja hacia los
			// pilares (más honda junto a ellos)
			double enVano = Math.floorMod((int) Math.floor(andado), VANO);
			double alPilar = Math.min(enVano, VANO - enVano) - PILAR / 2.0;
			desde = alPilar <= 0 ? Math.max(suelo, pie - HONDO_ARCOS) : pie - 1 - (int) Math.max(0, 3 - alPilar);
		}
		arriba = Math.max(arriba, pie + 1);
		BlockState abajo = level.getBlockState(pos.set(x, desde, z));
		if (abajo.isAir() || abajo.canBeReplaced() || SueloNatural.natural(abajo)) {
			level.setBlock(pos, desde == suelo ? cimiento(arena) : bloque, 2);
		}
		pon(level, pos, x, z, desde + 1, arriba, bloque);
	}

	/** Pone bloque de y0 a y1 en la columna x, z, parando donde haya algo hecho (una casa, un tejado). */
	private static void pon(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int y0, int y1, BlockState bloque) {
		for (int y = y0; y <= y1; y++) {
			BlockState aqui = level.getBlockState(pos.set(x, y, z));
			if (!aqui.isAir() && !aqui.canBeReplaced() && !SueloNatural.natural(aqui) && !pavimento(aqui) && !aqui.is(bloque.getBlock())) {
				return;
			}
			level.setBlock(pos, bloque, 2);
		}
	}

	private static void libre(WorldGenLevel level, BlockPos.MutableBlockPos pos) {
		BlockState aqui = level.getBlockState(pos);
		if (aqui.canBeReplaced() || SueloNatural.natural(aqui)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
	}

	private boolean esMuralla(BlockState b) {
		return b.is(bloque(false).getBlock()) || b.is(bloque(true).getBlock());
	}

	/** El material de la muralla según el nivel (y de arenisca en la arena). */
	private BlockState bloque(boolean arena) {
		return switch (nivel) {
			case 0 -> arena ? Blocks.STRIPPED_ACACIA_LOG.defaultBlockState() : Blocks.SPRUCE_LOG.defaultBlockState();
			case 1 -> arena ? Blocks.SANDSTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
			default -> arena ? Blocks.CUT_SANDSTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
		};
	}

	/** El bloque hundido en el suelo bajo la muralla (en la empalizada, el mismo tronco). */
	private BlockState cimiento(boolean arena) {
		return nivel == 0 ? bloque(arena) : (arena ? Blocks.SANDSTONE : Blocks.COBBLESTONE).defaultBlockState();
	}

	/**
	 * Una torre de la muralla, hueca: puerta a ras de suelo hacia la ciudad, escala de mano por dentro hasta la azotea
	 * almenada, un piso a la altura del adarve con salida a la muralla por los dos lados y faroles dentro.
	 */
	private void torre(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos) {
		BoundingBox mia = getBoundingBox();
		int tx = (mia.minX() + mia.maxX()) / 2;
		int tz = (mia.minZ() + mia.maxZ()) / 2;
		int y0 = mia.minY();
		int adarve = y0 + ALTO[nivel];
		int azotea = y0 + ALTO[nivel] + 4;
		// hacia la ciudad (la puerta) y a lo largo de la muralla (las salidas al adarve)
		net.minecraft.core.Direction dentro = Math.abs(cx - tx) >= Math.abs(cz - tz)
				? (cx > tx ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST)
				: (cz > tz ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH);
		net.minecraft.core.Direction lado = dentro.getClockWise();
		int r = RADIO_TORRE;
		BlockState suelo = Blocks.SPRUCE_PLANKS.defaultBlockState();
		for (int x = Math.max(mia.minX(), caja.minX()); x <= Math.min(mia.maxX(), caja.maxX()); x++) {
			for (int z = Math.max(mia.minZ(), caja.minZ()); z <= Math.min(mia.maxZ(), caja.maxZ()); z++) {
				int ox = x - tx;
				int oz = z - tz;
				double d = Math.hypot(ox, oz);
				if (d > r + 0.5) {
					continue;
				}
				BlockState s = SueloNatural.superficie(level, pos, x, z);
				int h = pos.getY();
				if (!SueloNatural.natural(s) && !s.is(Blocks.WATER) && !s.canBeReplaced()) {
					continue;
				}
				BlockState bloque = bloque(SueloNatural.arena(s));
				BlockState base = nivel == 0 ? bloque : (SueloNatural.arena(s) ? Blocks.SANDSTONE : Blocks.COBBLESTONE).defaultBlockState();
				// cimiento hasta el suelo de la torre
				for (int y = Math.min(h, y0); y <= y0; y++) {
					level.setBlock(pos.set(x, y, z), base, 2);
				}
				if (d > r - 0.5) {
					// el muro de la torre, con sus huecos
					boolean puerta = ox == dentro.getStepX() * r && oz == dentro.getStepZ() * r;
					boolean salida = Math.abs(ox) == Math.abs(lado.getStepX() * r) && Math.abs(oz) == Math.abs(lado.getStepZ() * r)
							&& (ox == 0 || oz == 0);
					boolean almena = (x + z) % 2 == 0;
					for (int y = y0 + 1; y <= azotea + (almena ? 1 : 0); y++) {
						boolean hueco = puerta && y <= y0 + 2 || salida && y > adarve && y <= adarve + 2;
						level.setBlock(pos.set(x, y, z), hueco ? Blocks.AIR.defaultBlockState() : bloque, 2);
					}
					continue;
				}
				// por dentro: la escala de mano contra el muro de fuera, suelos de madera y aire
				boolean escala = ox == -dentro.getStepX() * (r - 1) && oz == -dentro.getStepZ() * (r - 1);
				for (int y = y0 + 1; y <= azotea + 2; y++) {
					BlockState b;
					if (escala && y <= azotea) {
						b = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, dentro);
					} else if (y == adarve || y == azotea) {
						b = suelo;
					} else {
						b = Blocks.AIR.defaultBlockState();
					}
					level.setBlock(pos.set(x, y, z), b, 2);
				}
				// faroles: uno abajo y otro en el piso del adarve
				if (ox == lado.getStepX() * (r - 1) && oz == lado.getStepZ() * (r - 1)) {
					level.setBlock(pos.set(x, y0 + 1, z), Blocks.LANTERN.defaultBlockState(), 2);
				}
				if (ox == -lado.getStepX() * (r - 1) && oz == -lado.getStepZ() * (r - 1)) {
					level.setBlock(pos.set(x, adarve + 1, z), Blocks.LANTERN.defaultBlockState(), 2);
				}
			}
		}
	}

	/**
	 * Donde la muralla cruza el agua (un río, la orilla de un lago): pilotes de madera desde el fondo hasta la línea
	 * del agua, una reja de dos encima (de hierro, o de valla en las pobres) y encima la muralla.
	 */
	private void reja(WorldGenLevel level, BlockPos.MutableBlockPos pos, int alto) {
		int agua = pos.getY();
		int x = pos.getX();
		int z = pos.getZ();
		BlockState bloque = bloque(false);
		int fondo = agua;
		while (fondo > level.getMinY() && level.getBlockState(pos.set(x, fondo - 1, z)).is(Blocks.WATER) && agua - fondo < 24) {
			fondo--;
		}
		BlockState barrote = nivel == 0 ? Blocks.SPRUCE_FENCE.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState();
		// bajo el agua, pilotes de madera maciza hasta la línea del agua; la reja, solo por encima
		for (int y = fondo; y <= agua; y++) {
			level.setBlock(pos.set(x, y, z), Blocks.SPRUCE_LOG.defaultBlockState(), 2);
		}
		for (int y = agua + 1; y <= agua + 2; y++) {
			level.setBlock(pos.set(x, y, z), barrote, 2);
		}
		for (int y = agua + 3; y <= agua + Math.max(alto, 4); y++) {
			level.setBlock(pos.set(x, y, z), bloque, 2);
		}
	}

	/** El suelo de calles y plazas, sobre el que la muralla se puede poner (nunca tablones: pueden ser una casa). */
	private static boolean pavimento(BlockState s) {
		return s.is(Blocks.DIRT_PATH) || s.is(Blocks.SMOOTH_SANDSTONE) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE)
				|| s.is(Blocks.PACKED_MUD) || s.is(Blocks.GRAVEL);
	}

	/** El suelo de una calle (de las de la capital o de los caminos de los pueblos). */
	private static boolean calle(BlockState s) {
		return s.is(Blocks.DIRT_PATH) || s.is(Blocks.SMOOTH_SANDSTONE) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.OAK_PLANKS);
	}
}
