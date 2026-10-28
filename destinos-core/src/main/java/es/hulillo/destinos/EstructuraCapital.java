package es.hulillo.destinos;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * La capital de un reino: el pueblo que tocaría en ese bioma (de vanilla o de cualquier mod, como Towns and
 * Towers), pero mucho más grande. No tiene piezas propias: copia la estructura de pueblo con más profundidad y
 * la genera en el centro y en cuatro barrios alrededor, sin que las piezas de un barrio pisen las de otro.
 * Sale donde dice ColocacionCapital; qué pueblo se copia y cómo lo decide PueblosCapital.
 */
public final class EstructuraCapital extends Structure {
	public static final MapCodec<EstructuraCapital> CODEC = simpleCodec(EstructuraCapital::new);
	public static final StructureType<EstructuraCapital> TIPO = () -> CODEC;
	/** Profundidad extra del centro y de los barrios sobre la del pueblo (el máximo del juego es 20). */
	private static final int MAS_PROFUNDIDAD = 4;
	private static final int MAS_PROFUNDIDAD_BARRIO = 2;
	/**
	 * Hasta dónde llegan las calles del centro y de cada barrio, y a qué distancia del centro (en cada eje) nace
	 * cada barrio. Todo tiene que quedar a menos de 128 bloques del chunk de la capital: más lejos el juego ya no
	 * pone las piezas.
	 */
	private static final int DISTANCIA = 80;
	private static final int DISTANCIA_BARRIO = 48;
	private static final int SEPARACION_BARRIO = 64;
	/** De las farolas sueltas del pueblo se deja una de cada tantas. */
	private static final int FAROLAS = 4;
	private static final int K_FAROLAS = 6619;
	/** Largo de cada tramo de las avenidas del plano. */
	private static final int TRAMO = 16;
	/** A qué distancias de la plaza (en un eje) se prueba el castillo de las capitales ricas, de más cerca a más lejos. */
	private static final int[] DISTANCIAS_CASTILLO = {AdornoCapital.PLAZA + CastilloCapital.MEDIO + 4,
			AdornoCapital.PLAZA + CastilloCapital.MEDIO + 16};

	/** Capitales ya preparadas, por chunk (las últimas; hay pocas y se piden varias veces seguidas). */
	private static final Map<Long, Optional<Structure.GenerationStub>> PREPARADAS = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Optional<Structure.GenerationStub>> mas) {
			return size() > 16;
		}
	};

	public EstructuraCapital(Structure.StructureSettings ajustes) {
		super(ajustes);
	}

	/** Al cerrar el mundo: fuera las capitales preparadas, que guardan cosas de ese mundo. */
	static void olvida() {
		synchronized (PREPARADAS) {
			PREPARADAS.clear();
		}
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "capital"), TIPO);
	}

	/**
	 * Sin mirar el bioma: dónde va la capital lo decide ColocacionCapital, y el bioma justo en el centro puede ser
	 * uno que no está en ningún tag (ríos y biomas de mods). Con la comprobación de vanilla, si la generación del
	 * chunk veía un bioma así, la capital se quedaba sin generar aunque la corona sí saliera en el mapa.
	 */
	@Override
	public Optional<Structure.GenerationStub> findValidGenerationPoint(Structure.GenerationContext ctx) {
		return findGenerationPoint(ctx);
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext ctx) {
		// la generación y las búsquedas de estructuras (/locate, RoadWeaver…) preguntan por el mismo chunk: la
		// capital se prepara una vez (son cientos de piezas) y las demás veces se reutiliza
		long clave = ctx.chunkPos().toLong() * 31 + ctx.seed();
		synchronized (PREPARADAS) {
			Optional<Structure.GenerationStub> ya = PREPARADAS.get(clave);
			if (ya != null) {
				return ya;
			}
		}
		Optional<Structure.GenerationStub> stub = prepara(ctx);
		synchronized (PREPARADAS) {
			PREPARADAS.put(clave, stub);
		}
		return stub;
	}

	private Optional<Structure.GenerationStub> prepara(Structure.GenerationContext ctx) {
		Holder<Biome> bioma = biomaCentro(ctx);
		// en el sitio justo; si ahí no cabe ningún pueblo, la plaza un poco más allá (la capital sigue siendo de
		// este chunk). Nunca uno abandonado.
		Optional<Plaza<Montado>> encontrada = primeraQueEmpieza(ctx, bioma, true, EstructuraCapital::monta);
		if (encontrada.isEmpty()) {
			DestinosCore.LOG.warn("Destinos Core: la capital del chunk {} no ha podido empezar con ningún pueblo", ctx.chunkPos());
			return Optional.empty();
		}
		Holder.Reference<Structure> pueblo = encontrada.get().pueblo();
		Montado principal = encontrada.get().resultado();
		// los barrios: el mismo pueblo, en las cuatro diagonales (menos los que caen en el agua)
		Agua agua = new Agua(ctx);
		List<Montado> barrios = new ArrayList<>();
		Optional<Structure> barrio = PueblosCapital.agrandado(ctx.registryAccess(), pueblo, MAS_PROFUNDIDAD_BARRIO, DISTANCIA_BARRIO);
		if (barrio.isPresent()) {
			for (int[] d : DIAGONALES) {
				ChunkPos sitio = new ChunkPos(ctx.chunkPos().x + d[0] * SEPARACION_BARRIO / 16,
						ctx.chunkPos().z + d[1] * SEPARACION_BARRIO / 16);
				if (!agua.en(sitio.getMiddleBlockX(), sitio.getMiddleBlockZ())) {
					monta(barrio.get(), contexto(ctx, sitio, ctx.seed())).ifPresent(barrios::add);
				}
			}
		}
		// el plano de calles: la cruz de avenidas y la ronda; de los pueblos solo se queda lo que da a ellas
		PlanoCapital plano = new PlanoCapital(principal.position().getX(), principal.position().getZ());
		// la muralla, según lo rico que es el reino (con /place, sin capital en el mapa: normal)
		MapaTerreno.Capital esta = ColocacionCapital.capitalEn(ctx.chunkPos().x, ctx.chunkPos().z);
		int nivel = esta != null ? esta.nivel() : 1;
		List<StructurePiece> muralla = new ArrayList<>();
		MurallaCapital.piezas(principal.position().getX(), principal.position().getZ(), nivel, (mx, mz) -> suelo(ctx, mx, mz),
				muralla);
		// lo bonito (calles empedradas, plaza mayor, farolas): con el estilo del pueblo y el color del reino
		int estilo = AdornoCapital.estilo(pueblo.getRegisteredName(), bioma.value().getBaseTemperature());
		MapaTerreno mapa = DestinosCore.mapaActual();
		int tinte = -1;
		if (mapa != null) {
			// el reino de la capital (con /place, el del sitio)
			int[] t = Geo.territorioGeometrico(principal.position().getX(), principal.position().getZ(), mapa.sal());
			Geo.Reino reino = esta != null ? Geo.reinoDeDucado(esta.dcx(), esta.dcz(), mapa.sal()) : new Geo.Reino(t[2], t[3], false);
			tinte = AdornoCapital.tinte(Geo.colorReino(reino.kx(), reino.kz(), mapa.sal()));
		}
		int color = tinte;
		int madera = Paleta.madera(estilo, bioma.getRegisteredName(), Geo.hash(principal.position().getX(), principal.position().getZ(), 5813));
		// y las capitales ricas, su castillo dentro de la muralla
		Optional<CastilloCapital> castillo = nivel == 2 ? castillo(ctx, agua, principal, estilo, madera, color) : Optional.empty();
		int techo = ctx.heightAccessor().getMinY() + ctx.heightAccessor().getHeight() - 1;
		// el barrio noble: casas nobles mirando a la plaza mayor, más cuanto más rica es la capital
		List<StructurePiece> grandes = new ArrayList<>(castillo.stream().toList());
		// la plaza del mercado mayor, a otro lado de la plaza del pueblo (todas las capitales)
		Optional<PlazaMercado> mercado = mercado(ctx, agua, principal, nivel, estilo, madera, color, grandes);
		mercado.ifPresent(grandes::add);
		List<CasaNoble> nobles = nobles(ctx, agua, principal, nivel == 2 ? 4 : nivel == 1 ? 2 : 0, estilo, madera, color, grandes);
		grandes.addAll(nobles);
		List<CalleCapital> calles = callesDelPlano(ctx, agua, principal.position().getX(), principal.position().getZ(), grandes);
		return Optional.of(new Structure.GenerationStub(principal.position(), piezas -> {
			List<StructurePiece> puestas = new ArrayList<>();
			for (StructurePiece c : grandes) {
				puestas.add(c);
				piezas.addPiece(c);
			}
			List<StructurePiece> delCentro = principal.piezas();
			for (int i = 0; i < delCentro.size(); i++) {
				StructurePiece pieza = delCentro.get(i);
				// la plaza siempre; el resto, si no está metido en el mar (calles y casas flotando) ni en el castillo, y
				// sin casas en la plaza mayor (las calles sí: se enlosan)
				if (i == 0 || !agua.tapa(pieza) && grandes.stream().noneMatch(c -> enPlanta(c, pieza))
						&& !(casa(pieza) && cerca(pieza, principal.position(), AdornoCapital.PLAZA)) && !farolaDeSobra(pieza)
						&& (casa(pieza) ? plano.casa(pieza.getBoundingBox()) : plano.calle(pieza.getBoundingBox()))) {
					puestas.add(pieza);
					piezas.addPiece(pieza);
				}
			}
			for (Montado b : barrios) {
				for (StructurePiece pieza : b.piezas()) {
					// una pieza de barrio que choca con algo ya puesto, cae en el agua o no da a ninguna calle del
					// plano se queda fuera (de los barrios solo quedan casas: sus calles no son del centro)
					if (!agua.tapa(pieza) && casa(pieza) && plano.casa(pieza.getBoundingBox())
							&& puestas.stream().noneMatch(otra -> otra.getBoundingBox().intersects(pieza.getBoundingBox()))
							&& grandes.stream().noneMatch(c -> enPlanta(c, pieza)) && !farolaDeSobra(pieza)) {
						puestas.add(pieza);
						piezas.addPiece(pieza);
					}
				}
			}
			// las calles del plano, antes que la muralla: así la muralla les abre las puertas
			calles.forEach(piezas::addPiece);
			int enCalles = calles.size();
			// la muralla al final, para que vea las calles y casas ya puestas y les deje hueco. Ningún trozo que toque
			// el agua: cada pieza allana el terreno bajo su caja, y sobre el mar eso levantaba un anillo de tierra
			List<StructurePiece> enMuralla = muralla.stream().filter(pieza -> !agua.toca(pieza)).toList();
			enMuralla.forEach(piezas::addPiece);
			// y lo último, el adorno, que ya ve todo lo demás puesto (las calles van de casas a casas: no amplían la caja)
			List<BoundingBox> cajas = new ArrayList<>();
			puestas.forEach(pieza -> cajas.add(pieza.getBoundingBox()));
			enMuralla.forEach(pieza -> cajas.add(pieza.getBoundingBox()));
			BoundingBox.encapsulatingBoxes(cajas).ifPresent(caja -> piezas.addPiece(
					new AdornoCapital(principal.position().getX(), principal.position().getZ(), estilo, color, caja, techo,
							mercado.isEmpty())));
			DestinosCore.LOG.info("Destinos Core: capital en {}, {}, {} (muralla de nivel {}{}): {} de {} piezas del centro, {} barrios, {} piezas en total · pedida por {}",
					principal.position().getX(), principal.position().getY(), principal.position().getZ(), nivel,
					(castillo.isPresent() ? ", con castillo" : "") + (mercado.isPresent() ? ", con mercado" : "") + (nobles.isEmpty() ? "" : ", " + nobles.size() + " casas nobles"), delCentro.stream()
							.filter(puestas::contains).count(), delCentro.size(), barrios.size(), puestas.size() + enCalles,
					ColocacionCapital.quien());
		}));
	}

	/**
	 * Dónde va el castillo: a un lado de la plaza (norte, este, sur u oeste, primero cerca), sin tocar la plaza ni el
	 * agua, y donde quite menos casas del centro. Con la puerta hacia la plaza.
	 */
	private static Optional<CastilloCapital> castillo(Structure.GenerationContext ctx, Agua agua, Montado principal, int estilo, int madera, int color) {
		int px = principal.position().getX();
		int pz = principal.position().getZ();
		CastilloCapital mejor = null;
		long quita = Long.MAX_VALUE;
		for (int distancia : DISTANCIAS_CASTILLO) {
			for (int[] d : CRUZ) {
				int x = px + d[0] * distancia;
				int z = pz + d[1] * distancia;
				CastilloCapital c = new CastilloCapital(x, z, suelo(ctx, x, z), CastilloCapital.giroHacia(-d[0], -d[1]), estilo, madera, color);
				if (agua.toca(c) || enPlanta(c, principal.piezas().get(0))) {
					continue;
				}
				long n = principal.piezas().stream().filter(p -> enPlanta(c, p)).count();
				if (n < quita) {
					mejor = c;
					quita = n;
				}
			}
		}
		if (mejor == null) {
			DestinosCore.LOG.info("Destinos Core: la capital del chunk {} es rica pero no tiene sitio para el castillo", ctx.chunkPos());
		}
		return Optional.ofNullable(mejor);
	}

	/**
	 * Dónde va la plaza del mercado: pegada a un lado de la plaza del pueblo, sin tocar la plaza, el agua ni el
	 * castillo, y donde quite menos casas. Con la entrada hacia la plaza. Si no cabe, se prueba más pequeña.
	 */
	private static Optional<PlazaMercado> mercado(Structure.GenerationContext ctx, Agua agua, Montado principal, int nivel, int estilo, int madera,
			int color, List<StructurePiece> grandes) {
		int px = principal.position().getX();
		int pz = principal.position().getZ();
		for (int n = nivel; n >= 0; n--) {
			PlazaMercado mejor = null;
			long quita = Long.MAX_VALUE;
			int medio = PlazaMercado.medio(n);
			for (int distancia : new int[] {AdornoCapital.PLAZA + medio + 2, AdornoCapital.PLAZA + medio + 10}) {
				for (int[] d : CRUZ) {
					int x = px + d[0] * distancia;
					int z = pz + d[1] * distancia;
					PlazaMercado m = new PlazaMercado(x, z, suelo(ctx, x, z), CastilloCapital.giroHacia(-d[0], -d[1]), estilo, madera, color, n);
					if (agua.toca(m) || enPlanta(m, principal.piezas().get(0)) || grandes.stream().anyMatch(g -> enPlanta(g, m))) {
						continue;
					}
					long q = principal.piezas().stream().filter(p -> enPlanta(m, p)).count();
					if (q < quita) {
						mejor = m;
						quita = q;
					}
				}
			}
			if (mejor != null) {
				return Optional.of(mejor);
			}
		}
		DestinosCore.LOG.info("Destinos Core: la capital del chunk {} no tiene sitio para la plaza del mercado", ctx.chunkPos());
		return Optional.empty();
	}

	/**
	 * Las farolas sueltas del pueblo (plains_lamp_1, taiga_lamp_post_1…): en un pueblo agrandado salen demasiadas, así
	 * que solo se deja una de cada FAROLAS. Las calles ya llevan las del adorno.
	 */
	private static boolean farolaDeSobra(StructurePiece pieza) {
		if (!(pieza instanceof PoolElementStructurePiece p) || !p.getElement().toString().contains("lamp")) {
			return false;
		}
		BoundingBox b = pieza.getBoundingBox();
		return Math.floorMod(Geo.hash(b.minX(), b.minZ(), K_FAROLAS), FAROLAS) != 0;
	}

	/** Si la pieza es una casa (rígida) del pueblo, no una calle que sigue el terreno. */
	private static boolean casa(StructurePiece pieza) {
		return pieza instanceof PoolElementStructurePiece p && p.getElement().getProjection() == StructureTemplatePool.Projection.RIGID;
	}

	/** Si la caja de la pieza llega a menos de r bloques de (centro), vista desde arriba. */
	private static boolean cerca(StructurePiece pieza, BlockPos centro, int r) {
		BoundingBox b = pieza.getBoundingBox();
		long dx = Math.max(0, Math.max(b.minX() - centro.getX(), centro.getX() - b.maxX()));
		long dz = Math.max(0, Math.max(b.minZ() - centro.getZ(), centro.getZ() - b.maxZ()));
		return dx * dx + dz * dz <= (long) r * r;
	}

	/**
	 * Hasta n casas nobles alrededor de la plaza mayor, justo fuera de ella y con la puerta hacia el centro: en las
	 * ocho direcciones, sin tocar el agua, la plaza ni otras piezas grandes (el castillo, otras casas nobles), y
	 * primero donde quitan menos casas del pueblo.
	 */
	private static List<CasaNoble> nobles(Structure.GenerationContext ctx, Agua agua, Montado principal, int n, int estilo, int madera, int color,
			List<StructurePiece> grandes) {
		List<CasaNoble> elegidas = new ArrayList<>();
		if (n == 0) {
			return elegidas;
		}
		int px = principal.position().getX();
		int pz = principal.position().getZ();
		int distancia = AdornoCapital.PLAZA + CasaNoble.FONDO + 3;
		List<CasaNoble> sitios = new ArrayList<>();
		List<Long> quitan = new ArrayList<>();
		for (int[] d : OCHO) {
			// en las diagonales: los cuatro lados de la plaza son para las avenidas
			if (d[0] == 0 || d[1] == 0) {
				continue;
			}
			double largo = Math.hypot(d[0], d[1]);
			int x = px + (int) Math.round(d[0] / largo * distancia);
			int z = pz + (int) Math.round(d[1] / largo * distancia);
			CasaNoble c = new CasaNoble(x, z, suelo(ctx, x, z), CastilloCapital.giroHacia(-d[0], -d[1]), estilo, madera, color);
			if (agua.toca(c) || enPlanta(c, principal.piezas().get(0)) || grandes.stream().anyMatch(g -> enPlanta(g, c))) {
				continue;
			}
			sitios.add(c);
			quitan.add(principal.piezas().stream().filter(p -> enPlanta(c, p)).count());
		}
		while (elegidas.size() < n && !sitios.isEmpty()) {
			int mejor = 0;
			for (int i = 1; i < sitios.size(); i++) {
				if (quitan.get(i) < quitan.get(mejor)) {
					mejor = i;
				}
			}
			CasaNoble c = sitios.remove(mejor);
			quitan.remove(mejor);
			if (elegidas.stream().noneMatch(e -> enPlanta(e, c))) {
				elegidas.add(c);
			}
		}
		return elegidas;
	}

	/** Si dos piezas se pisan vistas desde arriba (la altura no cuenta: el castillo vacía todo lo de encima). */
	private static boolean enPlanta(StructurePiece a, StructurePiece b) {
		BoundingBox p = a.getBoundingBox();
		BoundingBox q = b.getBoundingBox();
		return p.minX() <= q.maxX() && p.maxX() >= q.minX() && p.minZ() <= q.maxZ() && p.maxZ() >= q.minZ();
	}

	/** Un pueblo ya montado: dónde empieza y sus piezas (sin las casas abandonadas sueltas). */
	private record Montado(BlockPos position, List<StructurePiece> piezas) {
	}

	/** La plaza de la capital: de qué pueblo es y lo que ha dado probarla. */
	private record Plaza<T>(Holder.Reference<Structure> pueblo, T resultado) {
	}

	/** Dónde probar la plaza, en chunks desde el de la capital: primero el suyo y luego alrededor. */
	private static final int[][] DESPLAZAMIENTOS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final int[][] DIAGONALES = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
	private static final int[][] CRUZ = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
	private static final int[][] OCHO = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

	/** Intentos de montar un pueblo que no salga abandonado antes de pasar al siguiente estilo. */
	private static final int INTENTOS = 6;

	/**
	 * Prueba los pueblos candidatos del bioma en cada desplazamiento (el chunk de la capital y luego los de al lado)
	 * hasta que uno empieza: prueba recibe la copia agrandada y el contexto de ese chunk. avisa: apunta en el log
	 * cada pueblo que no ha podido.
	 */
	private static <T> Optional<Plaza<T>> primeraQueEmpieza(Structure.GenerationContext ctx, Holder<Biome> bioma, boolean avisa,
			BiFunction<Structure, Structure.GenerationContext, Optional<T>> prueba) {
		List<Holder.Reference<Structure>> orden = PueblosCapital.candidatos(ctx.registryAccess(), bioma, ctx.chunkPos().x,
				ctx.chunkPos().z, ctx.seed());
		for (int[] d : DESPLAZAMIENTOS) {
			Structure.GenerationContext aqui = contexto(ctx, new ChunkPos(ctx.chunkPos().x + d[0], ctx.chunkPos().z + d[1]), ctx.seed());
			for (Holder.Reference<Structure> candidato : orden) {
				Optional<T> resultado = PueblosCapital.agrandado(ctx.registryAccess(), candidato, MAS_PROFUNDIDAD, DISTANCIA)
						.flatMap(copia -> prueba.apply(copia, aqui));
				if (resultado.isPresent()) {
					return Optional.of(new Plaza<>(candidato, resultado.get()));
				}
				if (avisa) {
					DestinosCore.LOG.info("Destinos Core: la capital del chunk {} ({}) no ha podido empezar con {} en {}; prueba otro",
							ctx.chunkPos(), bioma.unwrapKey().map(Object::toString).orElse("?"),
							ctx.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(candidato.value()), aqui.chunkPos());
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * Si en (x, z) puede empezar una capital (algún pueblo, en su chunk o al lado). Solo prueba la plaza de inicio,
	 * sin montar el pueblo entero, así que es barato. Lo usa MapaTerreno para elegir el sitio antes de poner la
	 * corona: así la corona va siempre donde la capital sí sale.
	 */
	static boolean cabe(ServerLevel level, int x, int z) {
		Structure.GenerationContext ctx = contexto(level, x, z);
		// primero, que de verdad sea tierra: el pueblo empieza igual en medio del mar (solo queda la plaza en un
		// islote), y el bioma y la altura estimada del mapa no bastan con generadores de terreno de mods
		if (!tierraFirme(ctx, x, z)) {
			return false;
		}
		return primeraQueEmpieza(ctx, biomaCentro(ctx), false, (copia, aqui) -> {
			for (int intento = 0; intento < INTENTOS; intento++) {
				if (copia.findValidGenerationPoint(contexto(aqui, aqui.chunkPos(), aqui.seed() + intento * Geo.MEZCLA)).isPresent()) {
					return Optional.of(Boolean.TRUE);
				}
			}
			return Optional.<Boolean>empty();
		}).isPresent();
	}

	/** La capital del chunk de (x, z) montada fuera de la generación del mundo (para ReparaCapitales), o null. */
	StructureStart inicio(ServerLevel level, int x, int z) {
		Structure.GenerationContext ctx = contexto(level, x, z);
		return findGenerationPoint(ctx).map(stub -> new StructureStart(this, ctx.chunkPos(), 0, stub.getPiecesBuilder().build()))
				.orElse(null);
	}

	/** Si alrededor de (x, z) hay tierra para una capital (lo mismo que mira cabe antes de probar pueblos). */
	static boolean enTierra(ServerLevel level, int x, int z) {
		return tierraFirme(contexto(level, x, z), x, z);
	}

	/** Contexto de generación del chunk de (x, z) en el mundo abierto, sin mirar el bioma. */
	private static Structure.GenerationContext contexto(ServerLevel level, int x, int z) {
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		return new Structure.GenerationContext(level.registryAccess(), gen, gen.getBiomeSource(),
				level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(),
				new ChunkPos(x >> 4, z >> 4), level, b -> true);
	}

	/** Separación de los puntos en los que se mira si alrededor de una capital hay tierra. */
	private static final int PASO_TIERRA = 40;
	/** Puntos con agua que se aceptan en los 8 de al lado del centro (un río) y en los 24 hasta 80 bloques (costa). */
	private static final int AGUA_CERCA = 1;
	private static final int AGUA_TOTAL = 8;

	/**
	 * Si alrededor de (x, z) hay tierra para una capital, con la altura de verdad del generador: el centro seco,
	 * casi todo seco a 40 bloques y como mucho un tercio con agua hasta 80 (lo que ocupa el centro de la capital).
	 */
	private static boolean tierraFirme(Structure.GenerationContext ctx, int x, int z) {
		if (mojado(ctx, x, z)) {
			return false;
		}
		int cerca = 0;
		int total = 0;
		for (int anillo = 1; anillo <= 2; anillo++) {
			for (int dz = -anillo; dz <= anillo; dz++) {
				for (int dx = -anillo; dx <= anillo; dx++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != anillo || !mojado(ctx, x + dx * PASO_TIERRA, z + dz * PASO_TIERRA)) {
						continue;
					}
					total++;
					if (anillo == 1) {
						cerca++;
					}
					if (cerca > AGUA_CERCA || total > AGUA_TOTAL) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * Si en (x, z) hay agua de al menos dos de hondo (mar, lago o río, también los que están por encima del nivel
	 * del mar), según el generador y sin generar el chunk: la superficie con agua y el fondo sin ella.
	 */
	private static boolean mojado(Structure.GenerationContext ctx, int x, int z) {
		int superficie = ctx.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(),
				ctx.randomState());
		int fondo = ctx.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(),
				ctx.randomState());
		return superficie - fondo >= 2;
	}

	/** El mismo contexto en otro chunk y con otra semilla, y sin mirar el bioma (la capital ya ha elegido pueblo). */
	private static Structure.GenerationContext contexto(Structure.GenerationContext ctx, ChunkPos chunk, long semilla) {
		return new Structure.GenerationContext(ctx.registryAccess(), ctx.chunkGenerator(), ctx.biomeSource(), ctx.randomState(),
				ctx.structureTemplateManager(), semilla, chunk, ctx.heightAccessor(), b -> true);
	}

	/** Altura de la superficie en (x, z) según el generador, sin generar el chunk. */
	private static int suelo(Structure.GenerationContext ctx, int x, int z) {
		return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(),
				ctx.randomState());
	}

	/** El bioma en la superficie del centro del chunk: decide el estilo de la capital. */
	private static Holder<Biome> biomaCentro(Structure.GenerationContext ctx) {
		int x = ctx.chunkPos().getMiddleBlockX();
		int z = ctx.chunkPos().getMiddleBlockZ();
		return ctx.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(suelo(ctx, x, z)), QuartPos.fromBlock(z),
				ctx.randomState().sampler());
	}

	/**
	 * Monta el pueblo en ese chunk, pero no uno abandonado: los pueblos zombi de vanilla (y los abandonados de
	 * algunos mods) salen del mismo pueblo, con una plaza de inicio rara; si toca una, se vuelve a tirar con otra
	 * semilla. Las casas abandonadas sueltas dentro de un pueblo normal se quitan.
	 */
	private static Optional<Montado> monta(Structure pueblo, Structure.GenerationContext ctx) {
		for (int intento = 0; intento < INTENTOS; intento++) {
			// con otra semilla puede tocar otra plaza de inicio que sí quepa: no se rinde a la primera
			Optional<Structure.GenerationStub> stub = pueblo.findValidGenerationPoint(contexto(ctx, ctx.chunkPos(),
					ctx.seed() + intento * Geo.MEZCLA));
			if (stub.isEmpty()) {
				continue;
			}
			List<StructurePiece> piezas = stub.get().getPiecesBuilder().build().pieces();
			if (piezas.isEmpty()) {
				continue;
			}
			// abandonado de verdad: la mayoría de sus piezas (los pueblos zombi lo son enteros); una casa suelta
			// con un nombre así no basta
			long abandonadas = piezas.stream().filter(PueblosCapital::abandonada).count();
			if (abandonadas * 2 > piezas.size()) {
				DestinosCore.LOG.info("Destinos Core: la capital del chunk {} salía abandonada ({} de {} piezas, plaza {}); se vuelve a tirar",
						ctx.chunkPos(), abandonadas, piezas.size(), piezas.get(0) instanceof PoolElementStructurePiece p ? p.getElement() : "?");
				continue;
			}
			List<StructurePiece> buenas = new ArrayList<>(piezas.size());
			for (int i = 0; i < piezas.size(); i++) {
				if (i == 0 || !PueblosCapital.abandonada(piezas.get(i))) {
					buenas.add(piezas.get(i));
				}
			}
			return Optional.of(new Montado(stub.get().position(), buenas));
		}
		return Optional.empty();
	}

	/** Tramos de calle de (x0, z0) a (x1, z1), cada uno a la altura del suelo en su mitad. */
	/**
	 * Las calles del plano (ver PlanoCapital): las cuatro avenidas, de la plaza hasta pasada la muralla, y la ronda
	 * por dentro del muro, en tramos. Sin los tramos que caen sobre el agua o dentro de una pieza grande (el castillo,
	 * el mercado, las casas nobles): la avenida sigue al otro lado.
	 */
	private static List<CalleCapital> callesDelPlano(Structure.GenerationContext ctx, Agua agua, int cx, int cz, List<StructurePiece> grandes) {
		List<CalleCapital> calles = new ArrayList<>();
		for (int[] d : CRUZ) {
			for (int r = AdornoCapital.PLAZA - 1; r < PlanoCapital.FIN_AVENIDA; r += TRAMO) {
				int r2 = Math.min(r + TRAMO, PlanoCapital.FIN_AVENIDA);
				tramo(ctx, agua, grandes, calles, cx + d[0] * r, cz + d[1] * r, cx + d[0] * r2, cz + d[1] * r2);
			}
		}
		int n = (int) Math.round(2 * Math.PI * PlanoCapital.RONDA / 10);
		for (int k = 0; k < n; k++) {
			double a0 = 2 * Math.PI * k / n;
			double a1 = 2 * Math.PI * (k + 1) / n;
			tramo(ctx, agua, grandes, calles, cx + (int) Math.round(Math.cos(a0) * PlanoCapital.RONDA),
					cz + (int) Math.round(Math.sin(a0) * PlanoCapital.RONDA), cx + (int) Math.round(Math.cos(a1) * PlanoCapital.RONDA),
					cz + (int) Math.round(Math.sin(a1) * PlanoCapital.RONDA));
		}
		return calles;
	}

	private static void tramo(Structure.GenerationContext ctx, Agua agua, List<StructurePiece> grandes, List<CalleCapital> calles, int ax, int az,
			int bx, int bz) {
		CalleCapital c = new CalleCapital(ax, az, bx, bz, suelo(ctx, (ax + bx) / 2, (az + bz) / 2));
		if (!agua.tapa(c) && grandes.stream().noneMatch(g -> enPlanta(g, c))) {
			calles.add(c);
		}
	}

	/** Dónde hay agua en vez de tierra (ver mojado), mirado por casillas de 16 bloques. */
	private static final class Agua {
		private final Structure.GenerationContext ctx;
		private final Map<Long, Boolean> visto = new ConcurrentHashMap<>();

		Agua(Structure.GenerationContext ctx) {
			this.ctx = ctx;
		}

		boolean en(int x, int z) {
			return visto.computeIfAbsent(Geo.mezcla(Geo.clave(x >> 4, z >> 4)), k -> mojado(ctx, (x & ~15) + 8, (z & ~15) + 8));
		}

		/** Si la pieza está casi toda sobre el agua: el centro y al menos dos esquinas. */
		boolean tapa(StructurePiece pieza) {
			BoundingBox c = pieza.getBoundingBox();
			return en(c.getCenter().getX(), c.getCenter().getZ()) && esquinasMojadas(c) >= 2;
		}

		/** Si algo de la pieza (el centro o una esquina) cae sobre el agua. */
		boolean toca(StructurePiece pieza) {
			BoundingBox c = pieza.getBoundingBox();
			return en(c.getCenter().getX(), c.getCenter().getZ()) || esquinasMojadas(c) > 0;
		}

		private int esquinasMojadas(BoundingBox c) {
			return (en(c.minX(), c.minZ()) ? 1 : 0) + (en(c.maxX(), c.minZ()) ? 1 : 0)
					+ (en(c.minX(), c.maxZ()) ? 1 : 0) + (en(c.maxX(), c.maxZ()) ? 1 : 0);
		}
	}

	@Override
	public StructureType<?> type() {
		return TIPO;
	}
}
