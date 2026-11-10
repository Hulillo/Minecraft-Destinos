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
import net.minecraft.world.level.block.Block;
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
 * Todo se decide en el plano (PlanMuralla): por dónde va, a qué altura, dónde no hay muro (laderas muy empinadas),
 * las puertas de cada calle que la cruza y las torres. Aquí solo se pone: el terreno ya se ha amoldado a lo largo de
 * la muralla (como a las casas), cada columna va del pie al adarve sin mirar qué hay, con cimiento hasta el suelo o
 * arcos sobre pilares donde se hunde mucho, y reja sobre el agua.
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
	/** Lo que se baja como mucho con cimiento macizo; más hondo, arcos. */
	private static final int HONDO_MACIZO = 6;
	/** Cada cuántos bloques va un pilar bajo los arcos, y lo gruesos que son. */
	private static final int VANO = 7;
	private static final int PILAR = 2;
	/** Hasta dónde bajan los pilares de los arcos. */
	private static final int HONDO_ARCOS = 28;

	static int radio(int nivel) {
		return RADIOS[Math.clamp(nivel, 0, RADIOS.length - 1)];
	}

	static int alto(int nivel) {
		return PlanMuralla.alto(nivel);
	}

	static int grueso(int nivel) {
		return PlanMuralla.grueso(nivel);
	}

	private final int cx;
	private final int cz;
	private final int nivel;
	/** Torre (su caja dice dónde y su minY la altura de su suelo) o tramo de (xa, za, ya) a (xb, zb, yb). */
	private final boolean torre;
	private final int xa;
	private final int za;
	private final int ya;
	private final int xb;
	private final int zb;
	private final int yb;
	/** Lo que se lleva andado del anillo al empezar el tramo (para repartir los pilares de los arcos). */
	private final int andado;
	/** Si toca agua: ahí no se amolda el terreno (no se echa tierra en el río). */
	private final boolean agua;
	/** El castillo, si lo hay: centro y medio lado de su muralla (ahí la de la ciudad se para y empalma con ella). */
	private final int kx;
	private final int kz;
	private final int km;
	/** Cajas (x0, z0, x1, z1 seguidos) donde no se pone muralla: delante y detrás de los portones. */
	private final int[] huecos;
	/** Los portillos de cerca (x, z, y, medio ancho seguidos). */
	private final int[] portillos;
	/** Las torres de cerca (x, z seguidos): el tramo no entra en ellas. */
	private final int[] torres;
	/** De arenisca (capitales del desierto y de las tierras baldías): lo dice el plano, no el bloque que haya debajo. */
	private final boolean arena;

	private MurallaCapital(PlanMuralla p, boolean arena, boolean torre, int[] tramo, int andado, boolean agua, int[] portillos, int[] torres,
			BoundingBox caja) {
		super(TIPO, 0, caja);
		this.cx = p.cx;
		this.cz = p.cz;
		this.nivel = p.nivel;
		this.torre = torre;
		this.xa = tramo[0];
		this.za = tramo[1];
		this.ya = tramo[2];
		this.xb = tramo[3];
		this.zb = tramo[4];
		this.yb = tramo[5];
		this.andado = andado;
		this.agua = agua;
		this.kx = p.castillo == null ? 0 : p.castillo[0];
		this.kz = p.castillo == null ? 0 : p.castillo[1];
		this.km = p.castillo == null ? -1 : p.castillo[2];
		this.huecos = p.huecos;
		this.portillos = portillos;
		this.torres = torres;
		this.arena = arena;
	}

	private MurallaCapital(CompoundTag t) {
		super(TIPO, t);
		cx = t.getIntOr("cx", 0);
		cz = t.getIntOr("cz", 0);
		nivel = Math.clamp(t.getIntOr("nivel", 0), 0, PlanMuralla.ALTO.length - 1);
		// las piezas de antes del plano (sin "v" 3) ya no se ponen
		boolean nueva = t.getIntOr("v", 0) == 3;
		torre = nueva && t.getBooleanOr("torre", false);
		xa = t.getIntOr("xa", 0);
		za = t.getIntOr("za", 0);
		ya = nueva ? t.getIntOr("ya", Integer.MIN_VALUE) : Integer.MIN_VALUE;
		xb = t.getIntOr("xb", 0);
		zb = t.getIntOr("zb", 0);
		yb = t.getIntOr("yb", Integer.MIN_VALUE);
		andado = t.getIntOr("andado", 0);
		agua = t.getBooleanOr("agua", false);
		kx = t.getIntOr("kx", 0);
		kz = t.getIntOr("kz", 0);
		km = t.getIntOr("km", -1);
		huecos = t.getIntArray("huecos").orElse(new int[0]);
		portillos = t.getIntArray("portillos").orElse(new int[0]);
		torres = t.getIntArray("torres").orElse(new int[0]);
		arena = t.getBooleanOr("arena", false);
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
		return PlanMuralla.radioEn(radios, a);
	}

	/**
	 * Las piezas del plano: una torre por cada torre y un tramo por cada tramo de muro (los de cuesta y los que caen
	 * dentro del castillo, no).
	 */
	static void piezas(PlanMuralla p, boolean arena, List<StructurePiece> piezas) {
		int r = PlanMuralla.RADIO_TORRE + 1;
		for (PlanMuralla.Torre t : p.torres) {
			BoundingBox caja = new BoundingBox(t.x() - r, t.y(), t.z() - r, t.x() + r, t.y() + 1, t.z() + r);
			piezas.add(new MurallaCapital(p, arena, true, new int[6], 0, false, new int[0], new int[0], caja));
		}
		int borde = PlanMuralla.grueso(p.nivel) + 2;
		double andado = 0;
		for (int k = 0; k < p.n(); k++) {
			int s = p.siguiente(k);
			int[] a = p.v[k];
			int[] b = p.v[s];
			if (p.hayMuro(k)) {
				BoundingBox caja = new BoundingBox(Math.min(a[0], b[0]) - borde, Math.min(p.pie[k], p.pie[s]), Math.min(a[1], b[1]) - borde,
						Math.max(a[0], b[0]) + borde, Math.max(p.pie[k], p.pie[s]) + 1, Math.max(a[1], b[1]) + borde);
				piezas.add(new MurallaCapital(p, arena, false, new int[] {a[0], a[1], p.pie[k], b[0], b[1], p.pie[s]}, (int) Math.round(andado),
						p.agua[k], cercanos(p, caja), torresCerca(p, caja), caja));
			}
			andado += p.largo(k);
		}
	}

	/** Para /destinoscore muralla: lienzos, torres, portillos y portones de las piezas dadas, y qué hay en aqui. "" si no hay muralla. */
	static String resumen(List<StructurePiece> piezas, BlockPos aqui) {
		int lienzos = 0;
		int torresN = 0;
		int conAgua = 0;
		java.util.Set<Long> portillosV = new java.util.HashSet<>();
		java.util.Set<String> portones = new java.util.HashSet<>();
		String encima = "";
		for (StructurePiece pieza : piezas) {
			if (!(pieza instanceof MurallaCapital m) || m.ya == Integer.MIN_VALUE && !m.torre) {
				continue;
			}
			if (m.torre) {
				torresN++;
			} else {
				lienzos++;
				conAgua += m.agua ? 1 : 0;
			}
			for (int k = 0; k + 3 < m.portillos.length; k += 4) {
				portillosV.add(Geo.clave(m.portillos[k], m.portillos[k + 1]));
			}
			portones.add(java.util.Arrays.toString(m.huecos));
			if (m.getBoundingBox().isInside(aqui.getX(), m.getBoundingBox().minY(), aqui.getZ())) {
				encima = m.torre ? " · estás en una torre (suelo a y=" + m.getBoundingBox().minY() + ")"
						: " · estás en un lienzo (pie de y=" + m.ya + " a y=" + m.yb + (m.agua ? ", con agua" : "") + ")";
			}
		}
		if (lienzos + torresN == 0) {
			return "";
		}
		int huecos = portones.stream().mapToInt(h -> h.equals("[]") ? 0 : h.split(",").length / 4).max().orElse(0);
		return lienzos + " lienzos (" + conAgua + " con agua), " + torresN + " torres, " + portillosV.size() + " portillos, " + huecos
				+ " portones" + encima;
	}

	private static int[] cercanos(PlanMuralla p, BoundingBox caja) {
		return p.portillos.stream()
				.filter(q -> q.x() >= caja.minX() - 3 && q.x() <= caja.maxX() + 3 && q.z() >= caja.minZ() - 3 && q.z() <= caja.maxZ() + 3)
				.flatMapToInt(q -> java.util.stream.IntStream.of(q.x(), q.z(), q.y(), q.medio())).toArray();
	}

	private static int[] torresCerca(PlanMuralla p, BoundingBox caja) {
		int r = PlanMuralla.RADIO_TORRE + 2;
		return p.torres.stream()
				.filter(t -> t.x() >= caja.minX() - r && t.x() <= caja.maxX() + r && t.z() >= caja.minZ() - r && t.z() <= caja.maxZ() + r)
				.flatMapToInt(t -> java.util.stream.IntStream.of(t.x(), t.z())).toArray();
	}

	private static boolean enHueco(int[] huecos, int x, int z) {
		for (int k = 0; k + 3 < huecos.length; k += 4) {
			if (x >= huecos[k] && x <= huecos[k + 2] && z >= huecos[k + 1] && z <= huecos[k + 3]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * El terreno se amolda a la muralla como a las casas (lo de encima de su pie se corta y lo de debajo se rellena),
	 * menos donde toca agua: ahí no se echa tierra en el río.
	 */
	@Override
	public boolean isCloseToChunk(ChunkPos chunk, int distancia) {
		return !agua && (torre || ya != Integer.MIN_VALUE) && super.isCloseToChunk(chunk, distancia);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
		t.putInt("v", 3);
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
		t.putBoolean("agua", agua);
		t.putInt("kx", kx);
		t.putInt("kz", kz);
		t.putInt("km", km);
		t.putIntArray("huecos", huecos);
		t.putIntArray("portillos", portillos);
		t.putIntArray("torres", torres);
		t.putBoolean("arena", arena);
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

	/** Una columna del tramo según el plano: dónde, su pie, lo alto, el suelo del portillo (o SIN_PORTILLO), lo andado y hacia dónde es fuera. */
	interface Columna {
		void en(int x, int z, int pie, int arriba, int portillo, double andado, double nx, double nz, boolean caraDeFuera);
	}

	static final int SIN_PORTILLO = Integer.MIN_VALUE;

	/**
	 * Las columnas del tramo de (xa, za) a (xb, zb) dentro de caja: las que quedan a menos del grueso de la línea, hacia
	 * fuera, con el adarve a la altura que toca en ese punto. En las puntas, redondeado: así los tramos se empalman sin
	 * huecos en los quiebros. Fuera quedan solo los portones, el castillo y las torres. Lo usan la colocación y la
	 * auditoría: las dos ven las mismas columnas.
	 */
	void recorre(BoundingBox caja, Columna c) {
		if (torre || ya == Integer.MIN_VALUE) {
			return;
		}
		BoundingBox mia = getBoundingBox();
		int grueso = PlanMuralla.grueso(nivel);
		double dx = xb - xa;
		double dz = zb - za;
		double largo = Math.max(1e-6, Math.hypot(dx, dz));
		double d = Math.max(1e-6, Math.hypot((xa + xb) / 2.0 - cx, (za + zb) / 2.0 - cz));
		// la normal del tramo, hacia fuera del anillo
		double nx = -dz / largo;
		double nz = dx / largo;
		if (nx * ((xa + xb) / 2.0 - cx) / d + nz * ((za + zb) / 2.0 - cz) / d < 0) {
			nx = -nx;
			nz = -nz;
		}
		for (int x = Math.max(mia.minX(), caja.minX()); x <= Math.min(mia.maxX(), caja.maxX()); x++) {
			for (int z = Math.max(mia.minZ(), caja.minZ()); z <= Math.min(mia.maxZ(), caja.maxZ()); z++) {
				if (km >= 0 && Math.max(Math.abs(x - kx), Math.abs(z - kz)) <= km || enHueco(huecos, x, z) || enTorre(x, z)) {
					continue;
				}
				double t = Math.clamp(((x - xa) * dx + (z - za) * dz) / (largo * largo), 0, 1);
				double qx = xa + t * dx;
				double qz = za + t * dz;
				double ox = x - qx;
				double oz = z - qz;
				double hondo = ox * nx + oz * nz;
				if (hondo < -0.5 || hondo >= grueso - 0.5 || Math.hypot(ox, oz) >= grueso + 0.5) {
					continue;
				}
				int pie = (int) Math.round(ya + t * (yb - ya));
				// empalizada en punta (un tronco sí y otro no, más alto); almenas en el borde de fuera (por dentro queda
				// el adarve, el paso de ronda por lo alto de la muralla)
				boolean almena = (x + z) % 2 == 0 && (nivel == 0 || hondo >= grueso - 1.5);
				int[] portillo = portillo(qx, qz);
				c.en(x, z, pie, pie + PlanMuralla.alto(nivel) + (almena ? 1 : 0), portillo == null ? SIN_PORTILLO : portillo[2], andado + t * largo,
						nx, nz, hondo >= grueso - 1.5);
			}
		}
	}

	/** Pone el trozo del tramo que cae en caja. */
	private void tramo(WorldGenLevel level, BoundingBox caja, BlockPos.MutableBlockPos pos) {
		recorre(caja, (x, z, pie, arriba, portillo, andado, nx, nz, fuera) -> {
			// los árboles fuera; pero no la propia empalizada de un tramo vecino (troncos sobre tierra: la tala se la
			// llevaría entera)
			if (!esMuralla(level.getBlockState(pos.set(x, SueloNatural.cima(level, x, z), z)))) {
				SueloNatural.tala(level, caja, x, z);
			}
			if (portillo != SIN_PORTILLO) {
				portillo(level, pos, x, z, portillo, arriba);
			} else {
				columna(level, pos, x, z, pie, arriba, andado);
			}
		});
	}

	/** Si el bloque es de esta muralla (su material, o la reja sobre el agua). */
	boolean deMuralla(BlockState b) {
		return esMuralla(b) || b.is(zapata().getBlock()) || b.is(Blocks.IRON_BARS) || b.is(Blocks.SPRUCE_FENCE);
	}

	boolean esTorre() {
		return torre;
	}

	int nivel() {
		return nivel;
	}

	private boolean enTorre(int x, int z) {
		for (int k = 0; k + 1 < torres.length; k += 2) {
			if (Math.hypot(x - torres[k], z - torres[k + 1]) <= PlanMuralla.RADIO_TORRE + 0.5) {
				return true;
			}
		}
		return false;
	}

	/** El portillo cuyo paso incluye el punto (qx, qz) de la línea, o null. */
	private int[] portillo(double qx, double qz) {
		for (int k = 0; k + 3 < portillos.length; k += 4) {
			if (Math.hypot(qx - portillos[k], qz - portillos[k + 1]) <= portillos[k + 3] + 0.5) {
				return new int[] {portillos[k], portillos[k + 1], portillos[k + 2]};
			}
		}
		return null;
	}

	/**
	 * Una columna del paso de un portillo: el suelo a la altura de la calle (lo que falte debajo, cimiento), libre
	 * PASO_PORTILLO encima y el dintel con la muralla hasta arriba (al menos dos por encima del paso).
	 */
	private void portillo(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int suelo, int arriba) {
		SueloNatural.superficie(level, pos, x, z);
		int h = pos.getY();
		BlockState bloque = bloque();
		for (int y = Math.min(h, suelo - 1); y < suelo; y++) {
			if (!level.getBlockState(pos.set(x, y, z)).isFaceSturdy(level, pos, net.minecraft.core.Direction.UP)) {
				level.setBlock(pos, zapata(), 2);
			}
		}
		BlockState pisa = level.getBlockState(pos.set(x, suelo, z));
		if (pisa.isAir() || pisa.canBeReplaced()) {
			level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 2);
		}
		for (int y = suelo + 1; y <= suelo + PlanMuralla.PASO_PORTILLO; y++) {
			level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
		}
		for (int y = suelo + PlanMuralla.PASO_PORTILLO + 1; y <= Math.max(arriba, suelo + PlanMuralla.PASO_PORTILLO + 2); y++) {
			level.setBlock(pos.set(x, y, z), bloque, 2);
		}
		for (int y = Math.max(arriba, suelo + PlanMuralla.PASO_PORTILLO + 2) + 1; y <= h; y++) {
			libre(level, pos.set(x, y, z));
		}
	}

	/**
	 * Una columna de la muralla con el pie en pie y lo alto en arriba (a lo largo del anillo en andado, para los
	 * pilares). Sobre el agua, reja. Si el suelo queda por debajo del pie, cimiento hasta él; si queda muy por debajo,
	 * arco entre pilares. Si el amoldado no llegó a quitar una loma (suelo por encima del pie), la columna sube con
	 * ella, como mucho un bloque enterrada: nunca a ras de suelo.
	 *
	 * Sale igual las veces que se ponga: en el repaso (al final del chunk, cuando calles y casas ya han hecho lo suyo)
	 * repone lo que le hayan quitado. Para eso su suelo es lo de debajo de la propia muralla, y la zapata (el bloque
	 * que la muralla hunde en el suelo, que no es de terreno ni de la muralla) marca dónde se apoyó la primera vez.
	 */
	private void columna(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int pie, int arriba, double andado) {
		BlockState s = SueloNatural.superficie(level, pos, x, z);
		Block barrote = barrote().getBlock();
		boolean puesta = false;
		while (pos.getY() > level.getMinY()) {
			// (la arenisca del desierto es terreno y es muralla: del pie para abajo cuenta como suelo)
			if (esMuralla(s) && (pos.getY() > pie || !SueloNatural.natural(s))) {
				puesta = true;
			} else if (puesta && s.is(barrote)) {
				// la reja sobre el agua ya está
				return;
			} else if (!s.isAir()) {
				break;
			}
			pos.move(0, -1, 0);
			s = level.getBlockState(pos);
		}
		int suelo = pos.getY();
		if (s.is(Blocks.WATER)) {
			reja(level, pos, Math.max(arriba - suelo, PlanMuralla.alto(nivel)));
			return;
		}
		BlockState bloque = bloque();
		// sobre una loma sin amoldar, sube con ella: sobre suelo natural queda un bloque enterrada; sobre su zapata (ya
		// subió la primera vez), justo encima
		int sube = s.is(zapata().getBlock()) ? Math.max(0, suelo - pie) : SueloNatural.natural(s) ? Math.max(0, suelo - 1 - pie) : 0;
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
			level.setBlock(pos, desde <= suelo ? zapata() : bloque, 2);
		}
		for (int y = desde + 1; y <= arriba; y++) {
			level.setBlock(pos.set(x, y, z), bloque, 2);
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

	private BlockState bloque() {
		return bloque(arena);
	}

	/** El material de la muralla según el nivel (y de arenisca en la arena). */
	private BlockState bloque(boolean arena) {
		return switch (nivel) {
			case 0 -> arena ? Blocks.STRIPPED_ACACIA_LOG.defaultBlockState() : Blocks.SPRUCE_LOG.defaultBlockState();
			case 1 -> arena ? Blocks.SANDSTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
			default -> arena ? Blocks.CUT_SANDSTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
		};
	}

	/**
	 * La zapata: el bloque que la muralla hunde en el suelo. Ni de terreno ni del material del muro, a propósito: así
	 * la tala de las calles no toma la empalizada por un árbol (un tronco de verdad sale de la tierra) y en el repaso
	 * se sabe dónde se apoyó la columna.
	 */
	private BlockState zapata() {
		return switch (nivel) {
			case 0 -> Blocks.PACKED_MUD.defaultBlockState();
			case 1 -> (arena ? Blocks.CHISELED_SANDSTONE : Blocks.MOSSY_COBBLESTONE).defaultBlockState();
			default -> (arena ? Blocks.SANDSTONE : Blocks.COBBLESTONE).defaultBlockState();
		};
	}

	/** El barrote de la reja sobre el agua: de hierro, o de valla en las pobres. */
	private BlockState barrote() {
		return nivel == 0 ? Blocks.SPRUCE_FENCE.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState();
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
		int adarve = y0 + PlanMuralla.alto(nivel);
		int azotea = y0 + PlanMuralla.alto(nivel) + 4;
		// hacia la ciudad (la puerta) y a lo largo de la muralla (las salidas al adarve)
		net.minecraft.core.Direction dentro = Math.abs(cx - tx) >= Math.abs(cz - tz)
				? (cx > tx ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST)
				: (cz > tz ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH);
		net.minecraft.core.Direction lado = dentro.getClockWise();
		int r = PlanMuralla.RADIO_TORRE;
		BlockState suelo = Blocks.SPRUCE_PLANKS.defaultBlockState();
		for (int x = Math.max(mia.minX(), caja.minX()); x <= Math.min(mia.maxX(), caja.maxX()); x++) {
			for (int z = Math.max(mia.minZ(), caja.minZ()); z <= Math.min(mia.maxZ(), caja.maxZ()); z++) {
				int ox = x - tx;
				int oz = z - tz;
				double d = Math.hypot(ox, oz);
				if (d > r + 0.5) {
					continue;
				}
				SueloNatural.superficie(level, pos, x, z);
				int h = pos.getY();
				BlockState bloque = bloque();
				BlockState base = nivel == 0 ? bloque : (arena ? Blocks.SANDSTONE : Blocks.COBBLESTONE).defaultBlockState();
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
		BlockState bloque = bloque();
		int fondo = agua;
		while (fondo > level.getMinY() && level.getBlockState(pos.set(x, fondo - 1, z)).is(Blocks.WATER) && agua - fondo < 24) {
			fondo--;
		}
		BlockState barrote = barrote();
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

}
