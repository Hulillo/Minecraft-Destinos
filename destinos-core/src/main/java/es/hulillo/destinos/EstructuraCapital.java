package es.hulillo.destinos;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * La capital de un reino, trazada sobre el terreno. Del pueblo que tocaría en ese bioma (de vanilla o de un mod,
 * como Towns and Towers) solo se toma el catálogo de plazas, casas y gente (CatalogoCapital); la ciudad sale del
 * terreno: el castillo en lo alto, la plaza en el mejor llano cerca del centro, el mercado y las casas nobles
 * alrededor, calles por el camino más barato (TrazadoCapital) hasta el castillo, el mercado y las puertas de la
 * muralla, cruzando los ríos por puentes, y casas del catálogo a los dos lados de las calles mirando a ellas. Sale
 * donde dice ColocacionCapital; qué pueblo da el catálogo lo decide PueblosCapital.
 */
public final class EstructuraCapital extends Structure {
	public static final MapCodec<EstructuraCapital> CODEC = simpleCodec(EstructuraCapital::new);
	public static final StructureType<EstructuraCapital> TIPO = () -> CODEC;
	/** Semillas para elegir casas, aldeanos y puertas. */
	private static final int K_CASA = 8821;
	private static final int K_GENTE = 4127;
	private static final int K_PUERTAS = 7307;
	/** Medio ancho de cada clase de calle, en bloques: 5, 4 y 3 de ancho. */
	private static final double ANCHO_PRINCIPAL = 2;
	private static final double ANCHO_RONDA = 1.5;
	private static final double ANCHO_CALLEJON = 1;
	/**
	 * Desnivel máximo (de lo más alto a lo más bajo del sitio) en el que se pone cada obra grande: en un barranco no se
	 * construye, se busca otro sitio o se queda sin ella. Y lo que pesa cada bloque de desnivel al elegir sitio.
	 */
	private static final int MAX_DESNIVEL_CASTILLO = 22;
	private static final int MAX_DESNIVEL_MERCADO = 9;
	private static final int MAX_DESNIVEL_NOBLE = 7;
	private static final int MAX_DESNIVEL_CASA = 5;
	private static final int K_DESNIVEL = 3;
	/** Hasta dónde se busca la plaza desde el centro de la capital (primero cerca; si no hay sitio, más lejos). */
	private static final int[] BUSCA_PLAZA = {28, 52};
	/** Hasta dónde del centro del chunk de la capital puede llegar una pieza (más lejos el juego ya no la pone). */
	private static final int MAX_ALCANCE = 120;
	/** Lo que cuenta que el castillo quede por encima del centro: hasta tantos bloques, más da igual. */
	private static final int ELEVADO = 6;
	/** Entre la muralla y las casas, la ronda libre. */
	private static final int RONDA_MURO = 6;
	/** Lo que queda entre la última casa y la muralla. */
	private static final int MARGEN_MURO = 5;
	/** Lo que siguen las calles pasada la muralla. */
	private static final int SALIDA = 14;
	/** Lo cerca que tiene que pasar una ronda o un callejón de una puerta para que valga la pena. */
	private static final int CERCA_PUERTA = 12;
	/** Lo más que se rebaja o se terraplena para la subida al castillo. */
	private static final int MAX_RAMPA = 16;
	private static final int SEPARA_PUERTAS = 30;
	private static final int SEPARA_MUELLES = 40;
	private static final int K_PLAZUELA = 6151;
	private static final int K_HUERTO = 4721;
	/** Hasta dónde se mira el terreno más allá del radio de la muralla (la rejilla). */
	private static final int TERRENO_FUERA = 36;
	private static final int K_PLAZA = 3371;
	private static final int SEPARA_PLAZUELAS = 48;
	private static final int MAX_DESNIVEL_PLAZUELA = 6;
	/** A qué distancia del eje de la calle va la puerta de las casas: pegada, y si no cabe, más atrás. */
	private static final int[] ACERA = {3, 5, 8};
	/** Cada cuántos bloques de calle sale un callejón, y lo largo que es. */
	private static final int CALLEJON = 18;
	private static final int LARGO_CALLEJON = 26;
	/** Aldeanos como mucho por capital (uno por casa hasta llegar). */
	private static final int MAX_ALDEANOS = 48;

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

	/** Al cerrar el mundo: fuera las capitales preparadas y los catálogos, que guardan cosas de ese mundo. */
	static void olvida() {
		synchronized (PREPARADAS) {
			PREPARADAS.clear();
		}
		CatalogoCapital.olvida();
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
		// capital se prepara una vez y las demás veces se reutiliza
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

	/** El pueblo del bioma que da el catálogo: el primero de los candidatos con plazas y casas. */
	private record Pueblo(Holder.Reference<Structure> estructura, CatalogoCapital catalogo) {
	}

	private static Optional<Pueblo> pueblo(Structure.GenerationContext ctx, Holder<Biome> bioma) {
		for (Holder.Reference<Structure> candidato : PueblosCapital.candidatos(ctx.registryAccess(), bioma, ctx.chunkPos().x, ctx.chunkPos().z,
				ctx.seed())) {
			CatalogoCapital c = CatalogoCapital.de(ctx.registryAccess(), ctx.structureTemplateManager(), candidato);
			if (c.sirve()) {
				return Optional.of(new Pueblo(candidato, c));
			}
		}
		return Optional.empty();
	}

	private Optional<Structure.GenerationStub> prepara(Structure.GenerationContext ctx) {
		long antes = System.nanoTime();
		Holder<Biome> bioma = biomaCentro(ctx);
		Optional<Pueblo> elegido = pueblo(ctx, bioma);
		if (elegido.isEmpty()) {
			DestinosCore.LOG.warn("Destinos Core: la capital del chunk {} no tiene ningún pueblo con casas para su catálogo", ctx.chunkPos());
			return Optional.empty();
		}
		Pueblo pueblo = elegido.get();
		CatalogoCapital catalogo = pueblo.catalogo();
		StructureTemplateManager plantillas = ctx.structureTemplateManager();
		int cx = ctx.chunkPos().getMiddleBlockX();
		int cz = ctx.chunkPos().getMiddleBlockZ();
		// la muralla, según lo rico que es el reino (con /place, sin capital en el mapa: normal)
		MapaTerreno.Capital esta = ColocacionCapital.capitalEn(ctx.chunkPos().x, ctx.chunkPos().z);
		int nivel = esta != null ? esta.nivel() : 1;
		int radio = MurallaCapital.radio(nivel);
		TerrenoCapital terreno = new TerrenoCapital(ctx, cx, cz, radio + TERRENO_FUERA);
		// el estilo del pueblo, la madera del bioma y el color del reino
		int estilo = AdornoCapital.estilo(pueblo.estructura().getRegisteredName(), bioma.value().getBaseTemperature());
		MapaTerreno mapa = DestinosCore.mapaActual();
		int tinte = -1;
		if (mapa != null) {
			int[] t = Geo.territorioGeometrico(cx, cz, mapa.sal());
			Geo.Reino reino = esta != null ? Geo.reinoDeDucado(esta.dcx(), esta.dcz(), mapa.sal()) : new Geo.Reino(t[2], t[3], false);
			tinte = AdornoCapital.tinte(Geo.colorReino(reino.kx(), reino.kz(), mapa.sal()));
		}
		int color = tinte;
		boolean enBadlands = bioma.is(net.minecraft.tags.BiomeTags.IS_BADLANDS);
		int madera = Paleta.madera(estilo, bioma.getRegisteredName(), Geo.hash(cx, cz, 5813));

		// 1. lo alto: el castillo de las capitales ricas, en el punto más alto donde cabe
		List<StructurePiece> grandes = new ArrayList<>();
		Optional<CastilloCapital> castillo = nivel == 2 ? castillo(terreno, cx, cz, radio, estilo, madera, color) : Optional.empty();
		castillo.ifPresent(grandes::add);
		// 2. la plaza mayor, que es la del mercado (la más importante de la ciudad): el mejor llano cerca del centro, sin
		// pisar el castillo, tan grande como dé la riqueza y quepa
		int[] plaza = plaza(terreno, cx, cz, PlazaMayor.maximo(nivel), grandes);
		int px = plaza[0];
		int pz = plaza[1];
		int nivelPlaza = nivelPlaza(terreno, px, pz, nivel);
		int alturaPlaza = terreno.relieve(px, pz, PlazaMayor.base(nivelPlaza))[0];
		int rPlaza = PlazaMayor.maximo(nivelPlaza) + 2;
		// 3. las casas nobles alrededor
		List<CasaNoble> nobles = nobles(terreno, px, pz, rPlaza, alturaPlaza, castillo, nivel == 2 ? 4 : nivel == 1 ? 2 : 0, estilo, madera, color, grandes);
		grandes.addAll(nobles);
		// adónde van las calles que salen de la plaza: el castillo, las puertas de la muralla y las casas nobles; por
		// ahí la plaza se abre
		List<int[]> destinos = new ArrayList<>();
		castillo.ifPresent(c -> destinos.add(c.entrada()));
		int desdeMuralla = destinos.size();
		destinos.addAll(puertas(terreno, cx, cz, radio, nivel));
		for (CasaNoble n : nobles) {
			destinos.add(puerta(n.getBoundingBox(), n.cx, n.cz, px, pz));
		}
		int[] entradas = new int[destinos.size()];
		for (int k = 0; k < entradas.length; k++) {
			entradas[k] = (int) Math.round(Math.toDegrees(Math.atan2(destinos.get(k)[1] - pz, destinos.get(k)[0] - px)) * 10);
		}
		PlazaMayor mayor = new PlazaMayor(px, pz, alturaPlaza, estilo, madera, color, nivelPlaza, Geo.hash(px, pz, K_PLAZA), entradas);
		Optional<PlazaMayor> mercado = Optional.of(mayor);

		// 4. las calles: de la plaza al castillo, a las puertas de la muralla y a las casas nobles
		List<BoundingBox> obras = new ArrayList<>();
		grandes.forEach(g -> obras.add(g.getBoundingBox()));
		TrazadoCapital trazado = new TrazadoCapital(terreno, cx, cz, radio + 16, obras, px, pz, rPlaza);
		List<List<int[]>> principales = new ArrayList<>();
		List<int[]> subida = null;
		for (int k = 0; k < destinos.size(); k++) {
			int previas = principales.size();
			calle(trazado, principales, mayor, destinos.get(k));
			// la subida al castillo, hasta delante de su barbacana (luego se hace rampa hasta la altura de su puerta)
			if (k < desdeMuralla && principales.size() > previas) {
				subida = principales.remove(previas);
			}
		}
		// y las rondas: un anillo (dos en las grandes) que va de una calle a otra siguiendo el terreno, para que la ciudad
		// tenga manzanas y no sea solo una estrella de calles que salen de la plaza
		List<List<int[]>> rondas = new ArrayList<>();
		List<Integer> radios = new ArrayList<>(List.of((rPlaza + radio) / 2));
		if (nivel >= 1) {
			radios.add(radio - RONDA_MURO - 10);
		}
		for (int r : radios) {
			int n = Math.max(6, (int) Math.round(2 * Math.PI * r / 30));
			double giro = Math.floorMod(Geo.hash(cx, cz, r), 360) * Math.PI / 180;
			List<int[]> puntos = new ArrayList<>();
			for (int k = 0; k < n; k++) {
				double a = giro + 2 * Math.PI * k / n;
				int x = cx + (int) Math.round(Math.cos(a) * r);
				int z = cz + (int) Math.round(Math.sin(a) * r);
				boolean libre = !terreno.agua(x, z) && obras.stream().noneMatch(o -> x >= o.minX() - 3 && x <= o.maxX() + 3 && z >= o.minZ() - 3 && z <= o.maxZ() + 3);
				puntos.add(libre ? new int[] {x, z} : null);
			}
			for (int k = 0; k < n; k++) {
				int[] a = puntos.get(k);
				int[] b = puntos.get((k + 1) % n);
				if (a != null && b != null) {
					List<int[]> c = trazado.calle(a[0], a[1], b[0], b[1]);
					if (c.size() >= 2) {
						rondas.add(c);
					}
				}
			}
		}
		List<List<int[]>> todas = new ArrayList<>(principales);
		todas.addAll(rondas);
		// cada calle con su ancho (las principales, 5; las rondas, 4; los callejones, 3) y su altura suavizada
		List<CalleCapital> calles = new ArrayList<>();
		tramos(terreno, principales, ANCHO_PRINCIPAL, px, pz, calles);
		if (subida != null) {
			rampa(terreno, subida, castillo.get().suelo(), calles);
			principales.add(subida);
		}
		Set<Long> enCalle = TrazadoCapital.ocupa(principales, (int) Math.ceil(ANCHO_PRINCIPAL));
		enCalle.addAll(TrazadoCapital.ocupa(rondas, (int) Math.ceil(ANCHO_RONDA)));

		// 5. las casas, a los dos lados de las calles con la puerta a la calle
		List<StructurePiece> casas = new ArrayList<>();
		List<int[]> puertasCasas = new ArrayList<>();
		long rMax = (long) (radio - RONDA_MURO) * (radio - RONDA_MURO);
		int[] motivos = new int[7];
		Predicate<BoundingBox> cabe = b -> {
			for (int[] e : new int[][] {{b.minX(), b.minZ()}, {b.maxX(), b.minZ()}, {b.minX(), b.maxZ()}, {b.maxX(), b.maxZ()}}) {
				long dx = e[0] - cx;
				long dz = e[1] - cz;
				if (dx * dx + dz * dz > rMax) {
					motivos[0]++;
					return false;
				}
			}
			if (pisaPlaza(mayor, b)) {
				motivos[1]++;
				return false;
			}
			for (StructurePiece o : grandes) {
				if (solapa(o.getBoundingBox(), b)) {
					motivos[2]++;
					return false;
				}
			}
			for (StructurePiece o : casas) {
				if (solapa(o.getBoundingBox(), b)) {
					motivos[3]++;
					return false;
				}
			}
			for (int x = b.minX(); x <= b.maxX(); x++) {
				for (int z = b.minZ(); z <= b.maxZ(); z++) {
					if (enCalle.contains(Geo.clave(x, z))) {
						motivos[4]++;
						return false;
					}
				}
			}
			if (terreno.agua(b)) {
				motivos[5]++;
				return false;
			}
			if (terreno.desnivel(b) > MAX_DESNIVEL_CASA) {
				motivos[6]++;
				return false;
			}
			return true;
		};
		// antes que los callejones y las casas, unas placitas de barrio a modo de parque junto a las calles, repartidas
		// por la ciudad (los callejones luego las rodean)
		List<PlazuelaCapital> plazuelas = plazuelas(terreno, todas, enCalle, cabe, 1 + 2 * nivel, px, pz, rPlaza, estilo, madera, color);
		casas.addAll(plazuelas);
		for (PlazuelaCapital q : plazuelas) {
			obras.add(q.getBoundingBox());
			trazado.veta(q.getBoundingBox());
		}

		// y los callejones: de cada calle, cada tanto, uno de través hacia un lado y otro (manzanas por dentro)
		List<List<int[]>> callejones = new ArrayList<>();
		long dentroMuro = (long) (radio - RONDA_MURO - 8) * (radio - RONDA_MURO - 8);
		for (List<int[]> c : todas) {
			int lado = Math.floorMod(Geo.hash(c.get(0)[0], c.get(0)[1], K_PUERTAS), 2) * 2 - 1;
			for (int k = CALLEJON / 6; k + 1 < c.size(); k += CALLEJON / 6) {
				int[] a = c.get(k);
				int[] b = c.get(k + 1);
				double largo = Math.max(1, Math.hypot(b[0] - a[0], b[1] - a[1]));
				int x = a[0] + (int) Math.round(-(b[1] - a[1]) / largo * lado * LARGO_CALLEJON);
				int z = a[1] + (int) Math.round((b[0] - a[0]) / largo * lado * LARGO_CALLEJON);
				lado = -lado;
				long dx = x - cx;
				long dz = z - cz;
				long qx = x - px;
				long qz = z - pz;
				if (dx * dx + dz * dz > dentroMuro || qx * qx + qz * qz < (long) (rPlaza + 8) * (rPlaza + 8)
						|| terreno.agua(x, z)
						|| obras.stream().anyMatch(o -> x >= o.minX() - 3 && x <= o.maxX() + 3 && z >= o.minZ() - 3 && z <= o.maxZ() + 3)) {
					continue;
				}
				List<int[]> callejon = trazado.calle(a[0], a[1], x, z);
				if (callejon.size() >= 2) {
					callejones.add(callejon);
				}
			}
		}
		todas.addAll(callejones);
		enCalle.addAll(TrazadoCapital.ocupa(callejones, (int) Math.ceil(ANCHO_CALLEJON) + 1));

		// primero las casas que dan a la plaza mayor, alrededor de su borde con la puerta hacia ella: la forma de la
		// plaza la marcan sus fachadas
		alrededor(plantillas, terreno, catalogo, mayor, cabe, casas, puertasCasas);
		for (List<int[]> c : todas) {
			for (int lado = -1; lado <= 1; lado += 2) {
				aLoLargo(plantillas, terreno, catalogo, c, lado, cx, cz, radio - RONDA_MURO, cabe, casas, puertasCasas);
			}
		}

		// las rondas y los callejones, solo donde dan a alguna casa: sin casas (en una ladera empinada, junto al agua)
		// no son calle de nada y quedaban caminos sueltos por el monte
		tramos(terreno, recorta(rondas, puertasCasas), ANCHO_RONDA, px, pz, calles);
		tramos(terreno, recorta(callejones, puertasCasas), ANCHO_CALLEJON, px, pz, calles);

		// 6. la gente: un aldeano a la puerta de cada casa, gatos y el gólem de la plaza
		List<StructurePiece> gente = gente(plantillas, terreno, catalogo, puertasCasas, px, pz, PlazaMayor.base(nivelPlaza), alturaPlaza, nivel);

		// 8. la muralla, sin los trozos que tocan el agua (allanarían un anillo de tierra en el mar)
		// ceñida a lo construido: rodea casas, mercado y casas nobles con un margen; donde está el castillo, su muralla
		List<BoundingBox> construido = new ArrayList<>();
		casas.forEach(c -> construido.add(c.getBoundingBox()));
		mercado.ifPresent(m -> construido.add(m.getBoundingBox()));
		nobles.forEach(n -> construido.add(n.getBoundingBox()));
		int[] contorno = MurallaCapital.contorno(cx, cz, construido, MARGEN_MURO, (rPlaza + radio) / 2, Math.min(radio + 6, MAX_ALCANCE - 6));
		int[] muroCastillo = castillo.map(c -> new int[] {c.cx, c.cz, CastilloCapital.MURO + 1}).orElse(null);
		// donde la cruza una calle principal, una puerta de verdad: torres, arco y rastrillo; la muralla llega a los lados
		// de sus torres y deja libre lo de delante y lo de detrás (el paso y las puertas de las torres)
		List<PuertaCapital> portones = portones(terreno, principales, contorno, cx, cz, nivel, muroCastillo, estilo, madera, color);
		int[] huecos = new int[portones.size() * 4];
		for (int k = 0; k < portones.size(); k++) {
			int[] a = portones.get(k).mundo(-PuertaCapital.MEDIO, -PuertaCapital.LIBRE);
			int[] b = portones.get(k).mundo(PuertaCapital.MEDIO, PuertaCapital.LIBRE);
			huecos[4 * k] = Math.min(a[0], b[0]);
			huecos[4 * k + 1] = Math.min(a[1], b[1]);
			huecos[4 * k + 2] = Math.max(a[0], b[0]);
			huecos[4 * k + 3] = Math.max(a[1], b[1]);
		}
		List<StructurePiece> muralla = new ArrayList<>();
		MurallaCapital.piezas(cx, cz, nivel, contorno, terreno::alto, muroCastillo, huecos, muralla);
		// las calles que van a las puertas salen un poco por fuera de la muralla y ahí se acaban
		calles.removeIf(c -> fueraDe(contorno, cx, cz, c.x0(), c.z0()) && fueraDe(contorno, cx, cz, c.x1(), c.z1()));
		// sin los trozos que caen enteros en el agua (la orilla del mar); donde cruza un río, reja
		List<StructurePiece> enMuralla = muralla.stream().filter(m -> !terreno.todoAgua(m.getBoundingBox())).toList();
		// y muelles con barcas en la orilla del río, el lago o el mar, dentro de la muralla
		List<MuelleCapital> muelles = muelles(terreno, contorno, cx, cz, nivel, enCalle, casas, grandes, mayor);
		// el faro, junto al muelle que da a mar abierto
		Optional<FaroCapital> faro = faro(terreno, contorno, cx, cz, muelles, enCalle, casas, grandes, estilo, madera, color);
		// los huertos de las afueras, fuera de la muralla
		List<HuertoCapital> huertos = huertos(terreno, contorno, cx, cz, nivel, enCalle, casas, grandes, muelles, portones, estilo, madera, color);
		// en el pantano, las calles cruzan el agua por pasarelas de madera
		if (bioma.is(net.minecraft.world.level.biome.Biomes.SWAMP) || bioma.is(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP)) {
			calles.forEach(CalleCapital::deMadera);
		}

		int techo = ctx.heightAccessor().getMinY() + ctx.heightAccessor().getHeight() - 1;
		long ms = (System.nanoTime() - antes) / 1_000_000;
		BlockPos centro = new BlockPos(px, alturaPlaza, pz);
		return Optional.of(new Structure.GenerationStub(centro, piezas -> {
			List<BoundingBox> cajas = new ArrayList<>();
			// la plaza mayor con el mercado (lleva la campana en la fuente)
			piezas.addPiece(mayor);
			cajas.add(mayor.getBoundingBox());
			for (StructurePiece g : grandes) {
				piezas.addPiece(g);
				cajas.add(g.getBoundingBox());
			}
			for (StructurePiece c : casas) {
				piezas.addPiece(c);
				cajas.add(c.getBoundingBox());
			}
			gente.forEach(piezas::addPiece);
			// las calles antes que la muralla: así la muralla les abre las puertas
			for (CalleCapital c : calles) {
				piezas.addPiece(c);
				cajas.add(c.getBoundingBox());
			}
			muelles.forEach(piezas::addPiece);
			huertos.forEach(piezas::addPiece);
			faro.ifPresent(f -> {
				piezas.addPiece(f);
				cajas.add(f.getBoundingBox());
			});
			// las puertas antes que la muralla: los arcos se paran en sus torres
			for (PuertaCapital q : portones) {
				piezas.addPiece(q);
				cajas.add(q.getBoundingBox());
			}
			for (StructurePiece m : enMuralla) {
				piezas.addPiece(m);
				cajas.add(m.getBoundingBox());
			}
			// y lo último, el adorno, que ya ve todo lo demás puesto
			BoundingBox.encapsulatingBoxes(cajas).ifPresent(caja -> piezas.addPiece(
					new AdornoCapital(px, alturaPlaza, pz, estilo, color, caja, techo, false, 0, mayor.nivel(), mayor.semilla(), mayor.entradas(), enBadlands)));
			DestinosCore.LOG.info("Destinos Core: capital en {}, {}, {} (muralla de nivel {}{}): {} calles, {} tramos de ronda y {} callejones, {} casas, {} de gente, {} puntos de terreno, trazada en {} ms (casas que no caben: fuera {}, plaza {}, obra {}, casa {}, calle {}, agua {}, cuesta {}) · pedida por {}",
					px, alturaPlaza, pz, nivel,
					(castillo.isPresent() ? ", con castillo" : "") + (mercado.isPresent() ? ", con mercado" : "")
							+ (nobles.isEmpty() ? "" : ", " + nobles.size() + " casas nobles") + ", " + plazuelas.size() + " placitas, " + portones.size() + " puertas, " + muelles.size() + " muelles" + (faro.isPresent() ? ", con faro" : "") + ", " + huertos.size() + " huertos",
					principales.size(), rondas.size(), callejones.size(), casas.size(), gente.size(), terreno.puntos(), ms, motivos[0], motivos[1], motivos[2], motivos[3], motivos[4],
					motivos[5], motivos[6], ColocacionCapital.quien());
		}));
	}

	/**
	 * Los tramos de las calles dadas, de medio ancho radio, con la altura de cada punto suavizada a lo largo de la
	 * calle (así no sube y baja con cada bulto del terreno). Lo que cae dentro de la plaza mayor se lo deja a ella.
	 */
	private static void tramos(TerrenoCapital terreno, List<List<int[]>> lista, double radio, int px, int pz, List<CalleCapital> calles) {
		long dentroPlaza = (long) (AdornoCapital.PLAZA / 2) * (AdornoCapital.PLAZA / 2);
		for (List<int[]> c : lista) {
			int[] y = perfil(terreno, c);
			for (int k = 0; k + 1 < c.size(); k++) {
				int[] a = c.get(k);
				int[] b = c.get(k + 1);
				long ax = a[0] - px, az = a[1] - pz, bx = b[0] - px, bz = b[1] - pz;
				if (ax * ax + az * az < dentroPlaza && bx * bx + bz * bz < dentroPlaza) {
					continue;
				}
				calles.add(new CalleCapital(a[0], a[1], y[k], b[0], b[1], y[k + 1], radio, true));
			}
		}
	}

	/**
	 * La subida al castillo: la altura de la calle se lleva hasta la de su puerta al final, como mucho un bloque de cada
	 * dos de camino hacia atrás, rebajando o terraplenando lo que haga falta.
	 */
	private static void rampa(TerrenoCapital terreno, List<int[]> c, int arriba, List<CalleCapital> calles) {
		int[] y = perfil(terreno, c);
		int n = c.size();
		y[n - 1] = arriba;
		int maximo = 0;
		for (int k = n - 2; k >= 0; k--) {
			double d = Math.hypot(c.get(k + 1)[0] - c.get(k)[0], c.get(k + 1)[1] - c.get(k)[1]);
			int lim = Math.max(1, (int) Math.floor(d / 2));
			int terreno0 = y[k];
			y[k] = Math.clamp(y[k], y[k + 1] - lim, y[k + 1] + lim);
			maximo = Math.max(maximo, Math.abs(y[k] - terreno0));
		}
		maximo = Math.max(maximo, Math.abs(arriba - terreno.alto(c.get(n - 1)[0], c.get(n - 1)[1]))) + 2;
		for (int k = 0; k + 1 < n; k++) {
			int[] a = c.get(k);
			int[] b = c.get(k + 1);
			calles.add(new CalleCapital(a[0], a[1], y[k], b[0], b[1], y[k + 1], ANCHO_PRINCIPAL, true, Math.min(maximo, MAX_RAMPA)));
		}
	}

	/** La altura de cada punto de la calle: la del terreno, pasada tres veces por una media de cada uno con sus vecinos. */
	private static int[] perfil(TerrenoCapital terreno, List<int[]> c) {
		int n = c.size();
		double[] h = new double[n];
		for (int k = 0; k < n; k++) {
			h[k] = terreno.alto(c.get(k)[0], c.get(k)[1]);
		}
		for (int pasada = 0; pasada < 3; pasada++) {
			double[] g = h.clone();
			for (int k = 1; k + 1 < n; k++) {
				g[k] = (h[k - 1] + 2 * h[k] + h[k + 1]) / 4;
			}
			h = g;
		}
		int[] y = new int[n];
		for (int k = 0; k < n; k++) {
			y[k] = (int) Math.round(h[k]);
		}
		return y;
	}

	/**
	 * Hasta n placitas junto a las calles: a un lado de cada trozo, un poco apartadas, donde cabe todo (como una casa:
	 * dentro de la muralla, en seco y sin mucha cuesta) y lejos de la plaza mayor y de las otras placitas.
	 */
	private static List<PlazuelaCapital> plazuelas(TerrenoCapital terreno, List<List<int[]>> calles, Set<Long> enCalle,
			Predicate<BoundingBox> cabe, int n, int px, int pz, int rPlaza, int estilo, int madera, int color) {
		List<PlazuelaCapital> r = new ArrayList<>();
		int aparte = (int) Math.ceil(ANCHO_PRINCIPAL) + 2 + PlazuelaCapital.MEDIO;
		List<int[]> sitios = new ArrayList<>();
		for (List<int[]> c : calles) {
			for (int k = 1; k + 1 < c.size(); k += 3) {
				int[] a = c.get(k);
				int[] b = c.get(k + 1);
				double largo = Math.max(1, Math.hypot(b[0] - a[0], b[1] - a[1]));
				for (int lado = -1; lado <= 1; lado += 2) {
					sitios.add(new int[] {a[0] + (int) Math.round(-(b[1] - a[1]) / largo * lado * aparte),
							a[1] + (int) Math.round((b[0] - a[0]) / largo * lado * aparte)});
				}
			}
		}
		// en un orden que cambia de capital a capital, pero siempre el mismo en cada una
		sitios.sort(java.util.Comparator.comparingInt(s -> Geo.hash(s[0], s[1], K_PLAZUELA)));
		for (int[] s : sitios) {
			if (r.size() >= n) {
				break;
			}
			if (Math.hypot(s[0] - px, s[1] - pz) < rPlaza + 16
					|| r.stream().anyMatch(q -> Math.hypot(q.cx - s[0], q.cz - s[1]) < SEPARA_PLAZUELAS)) {
				continue;
			}
			int[] rel = terreno.relieve(s[0], s[1], PlazuelaCapital.MEDIO);
			if (rel[1] > MAX_DESNIVEL_PLAZUELA) {
				continue;
			}
			PlazuelaCapital q = new PlazuelaCapital(s[0], s[1], rel[0], estilo, madera, color);
			BoundingBox nucleo = new BoundingBox(s[0] - PlazuelaCapital.MEDIO - 1, 0, s[1] - PlazuelaCapital.MEDIO - 1,
					s[0] + PlazuelaCapital.MEDIO + 1, 0, s[1] + PlazuelaCapital.MEDIO + 1);
			if (pisaCalle(nucleo, enCalle)) {
				continue;
			}
			// el resto de pruebas, las de una casa, con la caja sin el talud (que puede llegar a la calle)
			if (!cabe.test(nucleo)) {
				continue;
			}
			r.add(q);
		}
		return r;
	}

	/**
	 * Hasta 1 + nivel muelles: en la rejilla del terreno, dentro de la muralla, puntos de tierra con agua a 4 y a 8
	 * bloques en algún sentido (una orilla de verdad, no un charco), libres de calles, casas y obras; separados entre sí.
	 */
	private static List<MuelleCapital> muelles(TerrenoCapital terreno, int[] contorno, int cx, int cz, int nivel, Set<Long> enCalle,
			List<StructurePiece> casas, List<StructurePiece> grandes, PlazaMayor plaza) {
		List<MuelleCapital> r = new ArrayList<>();
		int paso = TerrenoCapital.PASO;
		int lejos = MurallaCapital.radio(nivel) + 10;
		for (int dx = -lejos; dx <= lejos && r.size() < 1 + nivel; dx += paso) {
			for (int dz = -lejos; dz <= lejos && r.size() < 1 + nivel; dz += paso) {
				int x = cx + dx;
				int z = cz + dz;
				double a = Math.floorMod((int) Math.floor(Math.toDegrees(Math.atan2(dz, dx)) * 10), 3600);
				if (Math.hypot(dx, dz) > MurallaCapital.radioEn(contorno, a) - 4 || terreno.agua(x, z) || enCalle.contains(Geo.clave(x, z))
						|| plaza.dentro(x, z, 4)) {
					continue;
				}
				for (Direction d : Direction.Plane.HORIZONTAL) {
					if (!terreno.agua(x + d.getStepX() * 4, z + d.getStepZ() * 4) || !terreno.agua(x + d.getStepX() * 8, z + d.getStepZ() * 8)) {
						continue;
					}
					MuelleCapital m = new MuelleCapital(x, z, terreno.alto(x + d.getStepX() * 6, z + d.getStepZ() * 6), d);
					BoundingBox b = m.getBoundingBox();
					boolean libre = casas.stream().noneMatch(c -> solapa(c.getBoundingBox(), b)) && grandes.stream().noneMatch(g -> solapa(g.getBoundingBox(), b))
							&& r.stream().noneMatch(o -> Math.hypot(o.getBoundingBox().getCenter().getX() - x, o.getBoundingBox().getCenter().getZ() - z) < SEPARA_MUELLES);
					if (libre) {
						r.add(m);
					}
					break;
				}
			}
		}
		return r;
	}

	/**
	 * El faro: junto al primer muelle que da a mar abierto (agua lejos hacia delante y a los lados), en tierra llana a
	 * un lado del arranque, dentro de la muralla y sin pisar casas ni calles, con la puerta hacia tierra.
	 */
	private static Optional<FaroCapital> faro(TerrenoCapital terreno, int[] contorno, int cx, int cz, List<MuelleCapital> muelles, Set<Long> enCalle,
			List<StructurePiece> casas, List<StructurePiece> grandes, int estilo, int madera, int color) {
		int r = FaroCapital.RADIO + 1;
		for (MuelleCapital m : muelles) {
			Direction d = m.dir();
			Direction lado = d.getClockWise();
			int sx = d.getStepX();
			int sz = d.getStepZ();
			if (!terreno.agua(m.x() + sx * 18, m.z() + sz * 18) || !terreno.agua(m.x() + sx * 28, m.z() + sz * 28)
					|| !terreno.agua(m.x() + sx * 22 + lado.getStepX() * 10, m.z() + sz * 22 + lado.getStepZ() * 10)
					|| !terreno.agua(m.x() + sx * 22 - lado.getStepX() * 10, m.z() + sz * 22 - lado.getStepZ() * 10)) {
				continue;
			}
			for (int atras = 2; atras <= 8; atras += 2) {
				for (int fuera = 6; fuera <= 12; fuera += 2) {
					for (int s = -1; s <= 1; s += 2) {
						int x = m.x() + lado.getStepX() * fuera * s - sx * atras;
						int z = m.z() + lado.getStepZ() * fuera * s - sz * atras;
						double a = Math.floorMod((int) Math.floor(Math.toDegrees(Math.atan2(z - cz, x - cx)) * 10), 3600);
						if (Math.hypot(x - cx, z - cz) > MurallaCapital.radioEn(contorno, a) - 8) {
							continue;
						}
						boolean seco = true;
						for (int i = -r; i <= r && seco; i += r) {
							for (int j = -r; j <= r && seco; j += r) {
								seco = !terreno.agua(x + i, z + j) && !enCalle.contains(Geo.clave(x + i, z + j));
							}
						}
						int[] rel = terreno.relieve(x, z, r);
						if (!seco || rel[1] > 4) {
							continue;
						}
						int rot = (d.getOpposite().get2DDataValue() + 2) % 4;
						FaroCapital f = new FaroCapital(x, z, rel[0], rot, estilo, madera, color);
						BoundingBox b = f.getBoundingBox();
						if (casas.stream().noneMatch(c -> solapa(c.getBoundingBox(), b)) && grandes.stream().noneMatch(g -> solapa(g.getBoundingBox(), b))
								&& muelles.stream().noneMatch(o -> solapa(o.getBoundingBox(), b))) {
							return Optional.of(f);
						}
					}
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * Los huertos de las afueras: en anillos justo fuera de la muralla, en tierra seca y llana, sin pisar calles,
	 * puertas, muelles ni otras obras, con la cancela hacia la ciudad. Más cuanto más rica es la capital.
	 */
	private static List<HuertoCapital> huertos(TerrenoCapital terreno, int[] contorno, int cx, int cz, int nivel, Set<Long> enCalle,
			List<StructurePiece> casas, List<StructurePiece> grandes, List<MuelleCapital> muelles, List<PuertaCapital> portones, int estilo,
			int madera, int color) {
		List<HuertoCapital> r = new ArrayList<>();
		int quiero = 3 + 2 * nivel;
		int m = HuertoCapital.MEDIO;
		int fuera = MurallaCapital.grueso(nivel) / 2 + m + PiezaObra.TALUD + 3;
		int giro = Math.floorMod(Geo.hash(cx, cz, K_HUERTO), 3600);
		for (int anillo = 0; anillo < 3 && r.size() < quiero; anillo++) {
			for (int k = 0; k < 3600 && r.size() < quiero; k += 70) {
				double a = Math.toRadians((k + giro) % 3600 / 10.0);
				double d = MurallaCapital.radioEn(contorno, (k + giro) % 3600) + fuera + anillo * (2 * (m + PiezaObra.TALUD) + 2);
				// fuera de la rejilla del terreno no se sabe la altura
				if (d + m > MurallaCapital.radio(nivel) + TERRENO_FUERA - TerrenoCapital.PASO) {
					continue;
				}
				int x = cx + (int) Math.round(Math.cos(a) * d);
				int z = cz + (int) Math.round(Math.sin(a) * d);
				boolean libre = true;
				for (int i = -m; i <= m && libre; i += m) {
					for (int j = -m; j <= m && libre; j += m) {
						libre = !terreno.agua(x + i, z + j) && !enCalle.contains(Geo.clave(x + i, z + j));
					}
				}
				int[] rel = terreno.relieve(x, z, m);
				if (!libre || rel[1] > 3) {
					continue;
				}
				// la cancela hacia la ciudad
				Direction puerta = Math.abs(cx - x) >= Math.abs(cz - z) ? (cx > x ? Direction.EAST : Direction.WEST) : (cz > z ? Direction.SOUTH : Direction.NORTH);
				HuertoCapital h = new HuertoCapital(x, z, rel[0], (puerta.get2DDataValue() + 2) % 4, estilo, madera, color);
				BoundingBox b = h.getBoundingBox();
				if (casas.stream().anyMatch(c -> solapa(c.getBoundingBox(), b)) || grandes.stream().anyMatch(g -> solapa(g.getBoundingBox(), b))
						|| muelles.stream().anyMatch(o -> solapa(o.getBoundingBox(), b)) || portones.stream().anyMatch(o -> solapa(o.getBoundingBox(), b))
						|| r.stream().anyMatch(o -> solapa(o.getBoundingBox(), b))) {
					continue;
				}
				r.add(h);
			}
		}
		return r;
	}

	/** Casas a lo largo del borde de la plaza, cada una con la puerta hacia el centro, a la altura de la plaza. */
	private static void alrededor(StructureTemplateManager plantillas, TerrenoCapital terreno, CatalogoCapital catalogo, PlazaMayor plaza,
			Predicate<BoundingBox> cabe, List<StructurePiece> casas, List<int[]> puertas) {
		double a = Math.floorMod(plaza.semilla(), 360) * Math.PI / 180;
		double fin = a + 2 * Math.PI;
		while (a < fin) {
			double r = plaza.radio(a) + 3;
			int x = plaza.cx + (int) Math.round(Math.cos(a) * r);
			int z = plaza.cz + (int) Math.round(Math.sin(a) * r);
			// la puerta mira al centro (en el eje más parecido); la casa se extiende a lo largo del borde
			double ux = -Math.cos(a);
			double uz = -Math.sin(a);
			Direction mira = Math.abs(ux) >= Math.abs(uz) ? (ux > 0 ? Direction.EAST : Direction.WEST) : (uz > 0 ? Direction.SOUTH : Direction.NORTH);
			int lx = mira.getAxis() == Direction.Axis.X ? 0 : 1;
			int lz = 1 - lx;
			int hueco = ponCasa(plantillas, terreno, catalogo, x, z, plaza.y0, lx, lz, mira, cabe, casas, puertas);
			// lo que ocupa a lo largo, pasado a ángulo en este radio
			a += (hueco > 0 ? hueco + 2 : 3) / Math.max(8, r);
		}
	}

	/** Si la caja pisa la plaza mayor (o se queda a menos de dos bloques de su borde). */
	private static boolean pisaPlaza(PlazaMayor plaza, BoundingBox b) {
		for (int x = b.minX(); x <= b.maxX(); x += 2) {
			for (int z = b.minZ(); z <= b.maxZ(); z += 2) {
				if (plaza.dentro(x, z, 2)) {
					return true;
				}
			}
		}
		return plaza.dentro(b.maxX(), b.maxZ(), 2) || plaza.dentro(b.minX(), b.maxZ(), 2) || plaza.dentro(b.maxX(), b.minZ(), 2);
	}

	private static boolean pisaCalle(BoundingBox b, Set<Long> enCalle) {
		for (int x = b.minX(); x <= b.maxX(); x++) {
			for (int z = b.minZ(); z <= b.maxZ(); z++) {
				if (enCalle.contains(Geo.clave(x, z))) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Las puertas de la muralla: donde cada calle principal pasa por ella (por el medio de su grueso), una puerta
	 * mirando hacia fuera, a la altura de la calle; no dos muy juntas, ni en el agua ni en el castillo.
	 */
	private static List<PuertaCapital> portones(TerrenoCapital terreno, List<List<int[]>> calles, int[] contorno, int cx, int cz, int nivel,
			int[] castillo, int estilo, int madera, int color) {
		List<PuertaCapital> r = new ArrayList<>();
		double medio = MurallaCapital.grueso(nivel) / 2.0;
		for (List<int[]> c : calles) {
			int[] y = perfil(terreno, c);
			for (int k = 1; k < c.size(); k++) {
				int[] q = c.get(k);
				double a = Math.floorMod((int) Math.floor(Math.toDegrees(Math.atan2(q[1] - cz, q[0] - cx)) * 10), 3600);
				double anillo = MurallaCapital.radioEn(contorno, a) + medio;
				if (Math.hypot(q[0] - cx, q[1] - cz) < anillo) {
					continue;
				}
				// el punto de la calle justo en la muralla (entre este y el anterior)
				int[] p = c.get(k - 1);
				double dp = Math.hypot(p[0] - cx, p[1] - cz);
				double dq = Math.hypot(q[0] - cx, q[1] - cz);
				double f = dq - dp < 1e-6 ? 1 : Math.clamp((anillo - dp) / (dq - dp), 0, 1);
				int x = (int) Math.round(p[0] + (q[0] - p[0]) * f);
				int z = (int) Math.round(p[1] + (q[1] - p[1]) * f);
				int alto = (int) Math.round(y[k - 1] + (y[k] - y[k - 1]) * f);
				boolean cerca = r.stream().anyMatch(o -> Math.hypot(o.cx - x, o.cz - z) < SEPARA_PUERTAS);
				boolean enCastillo = castillo != null && Math.max(Math.abs(x - castillo[0]), Math.abs(z - castillo[1])) <= castillo[2] + PuertaCapital.MEDIO + 2;
				if (!cerca && !enCastillo && !terreno.agua(x, z)) {
					r.add(new PuertaCapital(x, z, alto, CastilloCapital.giroHacia(x - cx, z - cz), estilo, madera, color, nivel));
				}
				break;
			}
		}
		return r;
	}

	/**
	 * Las calles dadas sin los cabos que no llevan a ninguna casa: cada una, del primer punto con alguna puerta cerca
	 * al último (con un punto más a cada lado para empalmar); fuera las que no tienen ninguna.
	 */
	private static List<List<int[]>> recorta(List<List<int[]>> calles, List<int[]> puertas) {
		List<List<int[]>> r = new ArrayList<>();
		long cerca = (long) CERCA_PUERTA * CERCA_PUERTA;
		for (List<int[]> c : calles) {
			int primero = -1;
			int ultimo = -1;
			for (int k = 0; k < c.size(); k++) {
				int[] p = c.get(k);
				for (int[] q : puertas) {
					long dx = p[0] - q[0];
					long dz = p[1] - q[1];
					if (dx * dx + dz * dz <= cerca) {
						if (primero < 0) {
							primero = k;
						}
						ultimo = k;
						break;
					}
				}
			}
			if (primero >= 0) {
				List<int[]> trozo = c.subList(Math.max(0, primero - 1), Math.min(c.size(), ultimo + 2));
				if (trozo.size() >= 2) {
					r.add(trozo);
				}
			}
		}
		return r;
	}

	/** Si (x, z) queda bastante por fuera de la muralla del contorno dado. */
	private static boolean fueraDe(int[] contorno, int cx, int cz, int x, int z) {
		double a = Math.floorMod((int) Math.floor(Math.toDegrees(Math.atan2(z - cz, x - cx)) * 10), 3600);
		return Math.hypot(x - cx, z - cz) > MurallaCapital.radioEn(contorno, a) + SALIDA;
	}

	/** Traza una calle de la plaza a (destino) y la apunta si hay camino. */
	private static void calle(TrazadoCapital trazado, List<List<int[]>> calles, PlazaMayor plaza, int[] destino) {
		// sale del borde de la plaza, por donde mira hacia su destino
		double a = Math.atan2(destino[1] - plaza.cz, destino[0] - plaza.cx);
		double r = plaza.radio(a) + 1;
		int px = plaza.cx + (int) Math.round(Math.cos(a) * r);
		int pz = plaza.cz + (int) Math.round(Math.sin(a) * r);
		List<int[]> c = trazado.calle(px, pz, destino[0], destino[1]);
		if (c.size() >= 2) {
			calles.add(c);
		} else {
			DestinosCore.LOG.info("Destinos Core: sin camino de la plaza ({}, {}) a {}, {}", px, pz, destino[0], destino[1]);
		}
	}

	/** Delante de la puerta de una obra que mira hacia (px, pz): el punto justo fuera de su caja en ese eje. */
	private static int[] puerta(BoundingBox b, int ox, int oz, int px, int pz) {
		int dx = px - ox;
		int dz = pz - oz;
		if (Math.abs(dz) >= Math.abs(dx)) {
			return new int[] {ox, dz < 0 ? b.minZ() - 2 : b.maxZ() + 2};
		}
		return new int[] {dx < 0 ? b.minX() - 2 : b.maxX() + 2, oz};
	}

	/**
	 * Las puertas de la muralla: 3 en las pobres, 4 en las normales y 5 en las ricas, repartidas alrededor; en cada
	 * tramo, donde el terreno es más suave y seco. El punto va justo pasada la muralla, para que la calle la cruce.
	 */
	private static List<int[]> puertas(TerrenoCapital terreno, int cx, int cz, int radio, int nivel) {
		int n = 3 + nivel;
		double base = Math.floorMod(Geo.hash(cx, cz, K_PUERTAS), 360) * Math.PI / 180;
		List<int[]> r = new ArrayList<>();
		for (int k = 0; k < n; k++) {
			int[] mejor = null;
			int nota = Integer.MAX_VALUE;
			for (int s = -2; s <= 2; s++) {
				double a = base + 2 * Math.PI * k / n + s * Math.PI / (n * 3.0);
				int x = cx + (int) Math.round(Math.cos(a) * (radio + 10));
				int z = cz + (int) Math.round(Math.sin(a) * (radio + 10));
				if (terreno.agua(x, z)) {
					continue;
				}
				int d = terreno.relieve(x, z, 8)[1] + Math.abs(s);
				if (d < nota) {
					nota = d;
					mejor = new int[] {x, z};
				}
			}
			if (mejor != null) {
				r.add(mejor);
			}
		}
		return r;
	}

	/**
	 * La plaza: en la rejilla del terreno cerca del centro, el sitio más llano y seco, sin pisar el castillo (con su
	 * margen); a igualdad, el más cercano al centro.
	 */
	private static int[] plaza(TerrenoCapital terreno, int cx, int cz, int lado, List<StructurePiece> grandes) {
		int r = Math.max(AdornoCapital.PLAZA, lado);
		for (int busca : BUSCA_PLAZA) {
			int[] mejor = null;
			double nota = Double.MAX_VALUE;
			for (int dx = -busca; dx <= busca; dx += TerrenoCapital.PASO) {
				for (int dz = -busca; dz <= busca; dz += TerrenoCapital.PASO) {
					int x = cx + dx;
					int z = cz + dz;
					BoundingBox b = new BoundingBox(x - r, 0, z - r, x + r, 0, z + r);
					if (terreno.agua(x, z) || grandes.stream().anyMatch(g -> solapa(g.getBoundingBox().inflatedBy(6), b))) {
						continue;
					}
					// el mercado tiene que caber: con agua en una esquina o mucha cuesta, solo si no queda otra
					int desnivel = terreno.relieve(x, z, r)[1];
					double n = desnivel * K_DESNIVEL + (terreno.aguaEsquinas(b) ? 200 : 0) + (desnivel > MAX_DESNIVEL_MERCADO ? 100 : 0)
							+ Math.hypot(dx, dz) / 6;
					if (n < nota) {
						nota = n;
						mejor = new int[] {x, z};
					}
				}
			}
			// si lo mejor de cerca no vale para el mercado, se busca más lejos
			if (mejor != null && (nota < 100 || busca == BUSCA_PLAZA[BUSCA_PLAZA.length - 1])) {
				return mejor;
			}
		}
		return new int[] {cx, cz};
	}

	/**
	 * Dónde va el castillo: hacia el borde de la ciudad, en un sitio llano y seco, mejor si queda algo elevado, con
	 * la puerta hacia el centro de la capital, donde irá la plaza.
	 */
	private static Optional<CastilloCapital> castillo(TerrenoCapital terreno, int cx, int cz, int radio, int estilo, int madera, int color) {
		CastilloCapital mejor = null;
		double nota = Double.MAX_VALUE;
		int medio = CastilloCapital.MEDIO;
		// el castillo es tan grande que hace de ciudadela en el borde: puede asomar por la muralla, pero no pasar de
		// donde el juego aún pone piezas, y deja sitio en el centro para la plaza y la ciudad
		int alcance = MAX_ALCANCE - medio - PiezaObra.TALUD;
		int base = terreno.alto(cx, cz);
		for (int dx = -alcance; dx <= alcance; dx += TerrenoCapital.PASO) {
			for (int dz = -alcance; dz <= alcance; dz += TerrenoCapital.PASO) {
				if (Math.hypot(dx, dz) < radio * 0.4) {
					continue;
				}
				int x = cx + dx;
				int z = cz + dz;
				int[] rel = terreno.relieve(x, z, medio - 6);
				if (rel[1] > MAX_DESNIVEL_CASTILLO) {
					continue;
				}
				CastilloCapital c = new CastilloCapital(x, z, rel[0], CastilloCapital.giroHacia(-dx, -dz), estilo, madera, color);
				if (terreno.aguaEsquinas(c.getBoundingBox())) {
					continue;
				}
				// sobre todo llano; algo por encima del centro suma, pero sin buscar el pico (un poco elevado basta)
				double n = rel[1] * K_DESNIVEL - Math.clamp(rel[0] - base, 0, ELEVADO) * 1.5 + Math.abs(Math.hypot(dx, dz) - radio * 0.6) / 16;
				if (n < nota) {
					nota = n;
					mejor = c;
				}
			}
		}
		if (mejor == null) {
			DestinosCore.LOG.info("Destinos Core: la capital en {}, {} es rica pero no tiene sitio para el castillo", cx, cz);
		}
		return Optional.ofNullable(mejor);
	}

	/** El nivel (tamaño) de la plaza mayor: el de la capital o, si ahí no cabe (cuesta, agua), uno más pequeño. */
	private static int nivelPlaza(TerrenoCapital terreno, int px, int pz, int nivel) {
		for (int n = nivel; n > 0; n--) {
			int r = PlazaMayor.base(n);
			BoundingBox b = new BoundingBox(px - r, 0, pz - r, px + r, 0, pz + r);
			if (terreno.relieve(px, pz, r)[1] <= MAX_DESNIVEL_MERCADO && !terreno.aguaEsquinas(b)) {
				return n;
			}
		}
		return 0;
	}

	/**
	 * Hasta n casas nobles alrededor de la plaza, con la puerta hacia ella: en las ocho direcciones y a tres
	 * distancias, primero donde el terreno es llano y alto (el barrio noble sube hacia el castillo).
	 */
	private static List<CasaNoble> nobles(TerrenoCapital terreno, int px, int pz, int rPlaza, int alturaPlaza, Optional<CastilloCapital> castillo, int n,
			int estilo, int madera, int color, List<StructurePiece> grandes) {
		List<CasaNoble> elegidas = new ArrayList<>();
		if (n == 0) {
			return elegidas;
		}
		int distancia = rPlaza + CasaNoble.FONDO + PiezaObra.TALUD + 3;
		List<CasaNoble> sitios = new ArrayList<>();
		List<Double> notas = new ArrayList<>();
		for (int[] d : OCHO) {
			double largo = Math.hypot(d[0], d[1]);
			for (int mas : new int[] {0, 8, 16}) {
				int x = px + (int) Math.round(d[0] / largo * (distancia + mas));
				int z = pz + (int) Math.round(d[1] / largo * (distancia + mas));
				int[] rel = terreno.relieve(x, z, CasaNoble.FONDO);
				if (rel[1] > MAX_DESNIVEL_NOBLE) {
					continue;
				}
				CasaNoble c = new CasaNoble(x, z, rel[0], CastilloCapital.giroHacia(-d[0], -d[1]), estilo, madera, color);
				if (terreno.aguaEsquinas(c.getBoundingBox()) || grandes.stream().anyMatch(g -> solapa(g.getBoundingBox(), c.getBoundingBox()))) {
					continue;
				}
				double nota = rel[1] * K_DESNIVEL - (rel[0] - alturaPlaza) + mas / 4.0;
				if (castillo.isPresent()) {
					nota += Math.hypot(x - castillo.get().cx, z - castillo.get().cz) / 16;
				}
				sitios.add(c);
				notas.add(nota);
			}
		}
		while (elegidas.size() < n && !sitios.isEmpty()) {
			int mejor = 0;
			for (int i = 1; i < sitios.size(); i++) {
				if (notas.get(i) < notas.get(mejor)) {
					mejor = i;
				}
			}
			CasaNoble c = sitios.remove(mejor);
			notas.remove(mejor);
			if (elegidas.stream().noneMatch(e -> solapa(e.getBoundingBox(), c.getBoundingBox()))) {
				elegidas.add(c);
			}
		}
		return elegidas;
	}

	/**
	 * Casas del catálogo a un lado (lado) de la calle, con la puerta a ella: se avanza por la calle y en cada sitio se
	 * prueba una casa; si cabe, se salta lo que ocupa y si no, un poco.
	 */
	private static void aLoLargo(StructureTemplateManager plantillas, TerrenoCapital terreno, CatalogoCapital catalogo, List<int[]> calle, int lado,
			int cx, int cz, int limite, Predicate<BoundingBox> cabe, List<StructurePiece> casas, List<int[]> puertas) {
		// la calle por bloques, para medir y tener la dirección en cada punto
		List<double[]> p = new ArrayList<>();
		List<Double> acum = new ArrayList<>();
		double total = 0;
		for (int k = 0; k < calle.size(); k++) {
			if (k > 0) {
				total += Math.hypot(calle.get(k)[0] - calle.get(k - 1)[0], calle.get(k)[1] - calle.get(k - 1)[1]);
			}
			p.add(new double[] {calle.get(k)[0], calle.get(k)[1]});
			acum.add(total);
		}
		int[] perfil = perfil(terreno, calle);
		double t = AdornoCapital.PLAZA / 2.0;
		int seg = 0;
		while (t < total) {
			while (seg + 1 < acum.size() - 1 && acum.get(seg + 1) < t) {
				seg++;
			}
			double[] a = p.get(seg);
			double[] b = p.get(Math.min(seg + 1, p.size() - 1));
			double largo = Math.max(1e-6, acum.get(Math.min(seg + 1, acum.size() - 1)) - acum.get(seg));
			double f = Math.clamp((t - acum.get(seg)) / largo, 0, 1);
			double x = a[0] + (b[0] - a[0]) * f;
			double z = a[1] + (b[1] - a[1]) * f;
			double ux = (b[0] - a[0]) / largo;
			double uz = (b[1] - a[1]) / largo;
			// hacia el lado de la casa; la puerta mira al revés (a la calle), en el eje más parecido
			double nx = -uz * lado;
			double nz = ux * lado;
			int yCalle = (int) Math.round(perfil[seg] + (perfil[Math.min(seg + 1, perfil.length - 1)] - perfil[seg]) * f);
			Direction mira = Math.abs(nx) >= Math.abs(nz) ? (nx > 0 ? Direction.WEST : Direction.EAST) : (nz > 0 ? Direction.NORTH : Direction.SOUTH);
			int lx = mira.getAxis() == Direction.Axis.X ? 0 : 1;
			int lz = 1 - lx;
			// pasada la muralla (las calles que van a las puertas siguen un poco) ya no hay casas
			if (Math.hypot(x + nx * ACERA[0] - cx, z + nz * ACERA[0] - cz) > limite) {
				t += 3;
				continue;
			}
			// pegada a la calle; si una esquina la pisa (en las curvas y en las calles en diagonal), un poco más atrás
			int hueco = 0;
			for (int acera : ACERA) {
				int fx = (int) Math.round(x + nx * acera);
				int fz = (int) Math.round(z + nz * acera);
				hueco = ponCasa(plantillas, terreno, catalogo, fx, fz, yCalle, lx, lz, mira, cabe, casas, puertas);
				if (hueco > 0) {
					break;
				}
			}
			t += hueco > 0 ? hueco + 2 : 3;
		}
	}

	/**
	 * Intenta poner una casa con la entrada mirando a (mira) y el bloque de delante de la puerta en (x, z), empezando
	 * a lo largo de la calle en (lx, lz). Devuelve lo que ocupa a lo largo de la calle, o 0 si no cabe ninguna.
	 */
	private static int ponCasa(StructureTemplateManager plantillas, TerrenoCapital terreno, CatalogoCapital catalogo, int x, int z, int yCalle, int lx, int lz,
			Direction mira, Predicate<BoundingBox> cabe, List<StructurePiece> casas, List<int[]> puertas) {
		List<CatalogoCapital.Modelo> modelos = catalogo.casas;
		int primero = Math.floorMod(Geo.hash(x, z, K_CASA), modelos.size());
		for (int k = 0; k < Math.min(4, modelos.size()); k++) {
			CatalogoCapital.Modelo m = modelos.get((primero + k) % modelos.size());
			Rotation giro = Rotation.NONE;
			for (Rotation g : Rotation.values()) {
				if (g.rotate(m.mira()) == mira) {
					giro = g;
				}
			}
			BlockPos entrada = m.entrada().rotate(giro);
			BoundingBox b0 = m.elemento().getBoundingBox(plantillas, BlockPos.ZERO, giro);
			// la entrada justo detrás del bloque (x, z); a lo largo, que la casa empiece en (x, z)
			int ex = x - mira.getStepX();
			int ez = z - mira.getStepZ();
			int ox = ex - entrada.getX() + (lx != 0 ? x - (ex - entrada.getX() + b0.minX()) : 0);
			int oz = ez - entrada.getZ() + (lz != 0 ? z - (ez - entrada.getZ() + b0.minZ()) : 0);
			BoundingBox enPlanta = m.elemento().getBoundingBox(plantillas, new BlockPos(ox, 0, oz), giro);
			if (!cabe.test(enPlanta)) {
				continue;
			}
			// la puerta a la altura de la calle (que va suavizada), si el suelo no queda muy lejos
			int suelo = terreno.alto(x, z);
			int y = (Math.abs(suelo - yCalle) <= 3 ? yCalle : suelo) + 1 - entrada.getY();
			BlockPos sitio = new BlockPos(ox, y, oz);
			BoundingBox b = m.elemento().getBoundingBox(plantillas, sitio, giro);
			casas.add(new PoolElementStructurePiece(plantillas, m.elemento(), sitio, m.elemento().getGroundLevelDelta(), giro, b,
					LiquidSettings.APPLY_WATERLOGGING));
			// delante de la puerta, donde espera su aldeano
			puertas.add(new int[] {x, z});
			return lx != 0 ? b.getXSpan() : b.getZSpan();
		}
		return 0;
	}

	/** Aldeanos a la puerta de las casas (hasta MAX_ALDEANOS), unos gatos y los gólems de la plaza. */
	private static List<StructurePiece> gente(StructureTemplateManager plantillas, TerrenoCapital terreno, CatalogoCapital catalogo,
			List<int[]> puertas, int px, int pz, int rPlaza, int alturaPlaza, int nivel) {
		List<StructurePiece> r = new ArrayList<>();
		if (!catalogo.aldeanos.isEmpty()) {
			for (int i = 0; i < puertas.size() && i < MAX_ALDEANOS; i++) {
				int[] p = puertas.get(i);
				ser(plantillas, catalogo.aldeanos, p[0], terreno.alto(p[0], p[1]) + 1, p[1], r);
			}
		}
		if (!catalogo.gatos.isEmpty()) {
			for (int i = 0; i < 3 + nivel && puertas.size() > 0; i++) {
				int[] p = puertas.get(Math.floorMod(Geo.hash(px + i, pz, K_GENTE), puertas.size()));
				ser(plantillas, catalogo.gatos, p[0], terreno.alto(p[0], p[1]) + 1, p[1], r);
			}
		}
		if (!catalogo.golems.isEmpty()) {
			for (int i = 0; i <= nivel; i++) {
				double a = i * 2 * Math.PI / (nivel + 1) + 0.4;
				int x = px + (int) Math.round(Math.cos(a) * (rPlaza - 2));
				int z = pz + (int) Math.round(Math.sin(a) * (rPlaza - 2));
				ser(plantillas, catalogo.golems, x, alturaPlaza + 1, z, r);
			}
		}
		return r;
	}

	/** Una pieza de gente del pueblo (la plantilla lleva el aldeano, el gato o el gólem) en (x, y, z). */
	private static void ser(StructureTemplateManager plantillas, List<StructurePoolElement> elementos, int x, int y, int z, List<StructurePiece> r) {
		StructurePoolElement e = elementos.get(Math.floorMod(Geo.hash(x, z, K_GENTE), elementos.size()));
		BlockPos sitio = new BlockPos(x, y - e.getGroundLevelDelta() + 1, z);
		try {
			r.add(new PoolElementStructurePiece(plantillas, e, sitio, e.getGroundLevelDelta(), Rotation.NONE,
					e.getBoundingBox(plantillas, sitio, Rotation.NONE), LiquidSettings.IGNORE_WATERLOGGING));
		} catch (RuntimeException fallo) {
			DestinosCore.LOG.debug("Destinos Core: no se ha podido poner {} en una capital", e, fallo);
		}
	}

	private static final int[][] CRUZ = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
	private static final int[][] OCHO = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

	/** Si dos cajas se pisan vistas desde arriba. */
	private static boolean solapa(BoundingBox p, BoundingBox q) {
		return p.minX() <= q.maxX() && p.maxX() >= q.minX() && p.minZ() <= q.maxZ() && p.maxZ() >= q.minZ();
	}

	/**
	 * Si en (x, z) puede empezar una capital: tierra firme alrededor y algún pueblo del bioma con casas para el
	 * catálogo. Es barato (no se monta nada). Lo usa MapaTerreno para elegir el sitio antes de poner la corona: así
	 * la corona va siempre donde la capital sí sale.
	 */
	static boolean cabe(ServerLevel level, int x, int z) {
		Structure.GenerationContext ctx = contexto(level, x, z);
		// primero, que de verdad sea tierra: el bioma y la altura estimada del mapa no bastan con generadores de
		// terreno de mods
		return tierraFirme(ctx, x, z) && pueblo(ctx, biomaCentro(ctx)).isPresent();
	}

	/** La capital del chunk de (x, z) montada fuera de la generación del mundo (para ReparaCapitales), o null. */
	StructureStart inicio(ServerLevel level, int x, int z) {
		Structure.GenerationContext ctx = contexto(level, x, z);
		return findGenerationPoint(ctx).map(stub -> new StructureStart(this, ctx.chunkPos(), 0, stub.getPiecesBuilder().build()))
				.orElse(null);
	}

	/** Si alrededor de (x, z) hay tierra para una capital (lo mismo que mira cabe antes de mirar los pueblos). */
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

	@Override
	public StructureType<?> type() {
		return TIPO;
	}
}
