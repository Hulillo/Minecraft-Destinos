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
	/** A qué distancia del borde de la calle va la puerta de las casas. */
	private static final int ACERA = 3;
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
		TerrenoCapital terreno = new TerrenoCapital(ctx, cx, cz, radio + 24);
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
		int madera = Paleta.madera(estilo, bioma.getRegisteredName(), Geo.hash(cx, cz, 5813));

		// 1. lo alto: el castillo de las capitales ricas, en el punto más alto donde cabe
		List<StructurePiece> grandes = new ArrayList<>();
		Optional<CastilloCapital> castillo = nivel == 2 ? castillo(terreno, cx, cz, radio, estilo, madera, color) : Optional.empty();
		castillo.ifPresent(grandes::add);
		// 2. la plaza: el mejor llano cerca del centro, sin pisar el castillo
		int[] plaza = plaza(terreno, cx, cz, grandes);
		int px = plaza[0];
		int pz = plaza[1];
		int alturaPlaza = terreno.relieve(px, pz, AdornoCapital.PLAZA)[0];
		// 3. el mercado junto a la plaza y las casas nobles alrededor, cuesta arriba (hacia el castillo)
		Optional<PlazaMercado> mercado = mercado(terreno, px, pz, nivel, estilo, madera, color, grandes);
		mercado.ifPresent(grandes::add);
		List<CasaNoble> nobles = nobles(terreno, px, pz, alturaPlaza, castillo, nivel == 2 ? 4 : nivel == 1 ? 2 : 0, estilo, madera, color, grandes);
		grandes.addAll(nobles);

		// 4. las calles: de la plaza al castillo, al mercado, a las casas nobles y a las puertas de la muralla
		List<BoundingBox> obras = new ArrayList<>();
		grandes.forEach(g -> obras.add(g.getBoundingBox()));
		TrazadoCapital trazado = new TrazadoCapital(terreno, cx, cz, radio + 16, obras, px, pz, AdornoCapital.PLAZA);
		List<List<int[]>> principales = new ArrayList<>();
		castillo.ifPresent(c -> calle(trazado, principales, px, pz, puerta(c.getBoundingBox(), c.cx, c.cz, px, pz)));
		mercado.ifPresent(m -> calle(trazado, principales, px, pz, puerta(m.getBoundingBox(), m.cx, m.cz, px, pz)));
		for (int[] p : puertas(terreno, cx, cz, radio, nivel)) {
			calle(trazado, principales, px, pz, p);
		}
		for (CasaNoble n : nobles) {
			calle(trazado, principales, px, pz, puerta(n.getBoundingBox(), n.cx, n.cz, px, pz));
		}
		// y las rondas: un anillo (dos en las grandes) que va de una calle a otra siguiendo el terreno, para que la ciudad
		// tenga manzanas y no sea solo una estrella de calles que salen de la plaza
		List<List<int[]>> rondas = new ArrayList<>();
		List<Integer> radios = new ArrayList<>(List.of((AdornoCapital.PLAZA + radio) / 2));
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
		List<CalleCapital> calles = new ArrayList<>();
		for (List<int[]> c : todas) {
			for (int k = 0; k + 1 < c.size(); k++) {
				int[] a = c.get(k);
				int[] b = c.get(k + 1);
				calles.add(new CalleCapital(a[0], a[1], b[0], b[1], terreno.alto((a[0] + b[0]) / 2, (a[1] + b[1]) / 2), true));
			}
		}
		Set<Long> enCalle = TrazadoCapital.ocupa(todas, 2);

		// 5. las casas, a los dos lados de las calles con la puerta a la calle
		List<StructurePiece> casas = new ArrayList<>();
		List<int[]> puertasCasas = new ArrayList<>();
		long rMax = (long) (radio - RONDA_MURO) * (radio - RONDA_MURO);
		long rPlaza = (long) (AdornoCapital.PLAZA + 2) * (AdornoCapital.PLAZA + 2);
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
			long qx = Math.max(0, Math.max(b.minX() - px, px - b.maxX()));
			long qz = Math.max(0, Math.max(b.minZ() - pz, pz - b.maxZ()));
			if (qx * qx + qz * qz <= rPlaza) {
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
		for (List<int[]> c : todas) {
			for (int lado = -1; lado <= 1; lado += 2) {
				aLoLargo(plantillas, terreno, catalogo, c, lado, cx, cz, radio - RONDA_MURO, cabe, casas, puertasCasas);
			}
		}

		// 6. la gente: un aldeano a la puerta de cada casa, gatos y el gólem de la plaza
		List<StructurePiece> gente = gente(plantillas, terreno, catalogo, puertasCasas, px, pz, alturaPlaza, nivel);

		// 7. la plaza del pueblo (pozo, campana…) en el centro de la plaza mayor
		StructurePoolElement elemPlaza = catalogo.plazas.get(Math.floorMod(Geo.hash(px, pz, K_CASA), catalogo.plazas.size()));
		BoundingBox b0 = elemPlaza.getBoundingBox(plantillas, BlockPos.ZERO, Rotation.NONE);
		BlockPos sitioPlaza = new BlockPos(px - b0.getXSpan() / 2, alturaPlaza + 1 - elemPlaza.getGroundLevelDelta(), pz - b0.getZSpan() / 2);
		PoolElementStructurePiece piezaPlaza = new PoolElementStructurePiece(plantillas, elemPlaza, sitioPlaza, elemPlaza.getGroundLevelDelta(),
				Rotation.NONE, elemPlaza.getBoundingBox(plantillas, sitioPlaza, Rotation.NONE), LiquidSettings.APPLY_WATERLOGGING);

		// 8. la muralla, sin los trozos que tocan el agua (allanarían un anillo de tierra en el mar)
		List<StructurePiece> muralla = new ArrayList<>();
		MurallaCapital.piezas(cx, cz, nivel, terreno::alto, muralla);
		List<StructurePiece> enMuralla = muralla.stream().filter(m -> !terreno.aguaEsquinas(m.getBoundingBox())).toList();

		int techo = ctx.heightAccessor().getMinY() + ctx.heightAccessor().getHeight() - 1;
		long ms = (System.nanoTime() - antes) / 1_000_000;
		BlockPos centro = new BlockPos(px, alturaPlaza, pz);
		return Optional.of(new Structure.GenerationStub(centro, piezas -> {
			List<BoundingBox> cajas = new ArrayList<>();
			piezas.addPiece(piezaPlaza);
			cajas.add(piezaPlaza.getBoundingBox());
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
			for (StructurePiece m : enMuralla) {
				piezas.addPiece(m);
				cajas.add(m.getBoundingBox());
			}
			// y lo último, el adorno, que ya ve todo lo demás puesto
			BoundingBox.encapsulatingBoxes(cajas).ifPresent(caja -> piezas.addPiece(
					new AdornoCapital(px, alturaPlaza, pz, estilo, color, caja, techo, mercado.isEmpty())));
			DestinosCore.LOG.info("Destinos Core: capital en {}, {}, {} (muralla de nivel {}{}): {} calles y {} tramos de ronda, {} casas, {} de gente, {} puntos de terreno, trazada en {} ms (casas que no caben: fuera {}, plaza {}, obra {}, casa {}, calle {}, agua {}, cuesta {}) · pedida por {}",
					px, alturaPlaza, pz, nivel,
					(castillo.isPresent() ? ", con castillo" : "") + (mercado.isPresent() ? ", con mercado" : "")
							+ (nobles.isEmpty() ? "" : ", " + nobles.size() + " casas nobles"),
					principales.size(), rondas.size(), casas.size(), gente.size(), terreno.puntos(), ms, motivos[0], motivos[1], motivos[2], motivos[3], motivos[4],
					motivos[5], motivos[6], ColocacionCapital.quien());
		}));
	}

	/** Traza una calle de la plaza a (destino) y la apunta si hay camino. */
	private static void calle(TrazadoCapital trazado, List<List<int[]>> calles, int px, int pz, int[] destino) {
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
	private static int[] plaza(TerrenoCapital terreno, int cx, int cz, List<StructurePiece> grandes) {
		int r = AdornoCapital.PLAZA;
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
					double n = terreno.relieve(x, z, r)[1] * K_DESNIVEL + (terreno.aguaEsquinas(b) ? 20 : 0) + Math.hypot(dx, dz) / 6;
					if (n < nota) {
						nota = n;
						mejor = new int[] {x, z};
					}
				}
			}
			if (mejor != null) {
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

	/**
	 * Dónde va la plaza del mercado: pegada a un lado de la plaza, sin tocar el agua ni otras obras, en el sitio más
	 * llano. Con la entrada hacia la plaza. Si no cabe, se prueba más pequeña.
	 */
	private static Optional<PlazaMercado> mercado(TerrenoCapital terreno, int px, int pz, int nivel, int estilo, int madera, int color,
			List<StructurePiece> grandes) {
		for (int n = nivel; n >= 0; n--) {
			PlazaMercado mejor = null;
			int nota = Integer.MAX_VALUE;
			int medio = PlazaMercado.medio(n) + PiezaObra.TALUD;
			for (int distancia : new int[] {AdornoCapital.PLAZA + medio + 2, AdornoCapital.PLAZA + medio + 10}) {
				for (int[] d : CRUZ) {
					int x = px + d[0] * distancia;
					int z = pz + d[1] * distancia;
					int[] rel = terreno.relieve(x, z, PlazaMercado.medio(n));
					if (rel[1] > MAX_DESNIVEL_MERCADO) {
						continue;
					}
					PlazaMercado m = new PlazaMercado(x, z, rel[0], CastilloCapital.giroHacia(-d[0], -d[1]), estilo, madera, color, n);
					if (terreno.aguaEsquinas(m.getBoundingBox()) || grandes.stream().anyMatch(g -> solapa(g.getBoundingBox(), m.getBoundingBox()))) {
						continue;
					}
					int q = rel[1] * K_DESNIVEL + (distancia > AdornoCapital.PLAZA + medio + 2 ? 4 : 0);
					if (q < nota) {
						mejor = m;
						nota = q;
					}
				}
			}
			if (mejor != null) {
				return Optional.of(mejor);
			}
		}
		DestinosCore.LOG.info("Destinos Core: la capital en {}, {} no tiene sitio para la plaza del mercado", px, pz);
		return Optional.empty();
	}

	/**
	 * Hasta n casas nobles alrededor de la plaza, con la puerta hacia ella: en las ocho direcciones y a tres
	 * distancias, primero donde el terreno es llano y alto (el barrio noble sube hacia el castillo).
	 */
	private static List<CasaNoble> nobles(TerrenoCapital terreno, int px, int pz, int alturaPlaza, Optional<CastilloCapital> castillo, int n,
			int estilo, int madera, int color, List<StructurePiece> grandes) {
		List<CasaNoble> elegidas = new ArrayList<>();
		if (n == 0) {
			return elegidas;
		}
		int distancia = AdornoCapital.PLAZA + CasaNoble.FONDO + PiezaObra.TALUD + 3;
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
			Direction mira = Math.abs(nx) >= Math.abs(nz) ? (nx > 0 ? Direction.WEST : Direction.EAST) : (nz > 0 ? Direction.NORTH : Direction.SOUTH);
			int fx = (int) Math.round(x + nx * ACERA);
			int fz = (int) Math.round(z + nz * ACERA);
			// pasada la muralla (las calles que van a las puertas siguen un poco) ya no hay casas
			if (Math.hypot(fx - cx, fz - cz) > limite) {
				t += 3;
				continue;
			}
			int lx = mira.getAxis() == Direction.Axis.X ? 0 : 1;
			int lz = 1 - lx;
			int hueco = ponCasa(plantillas, terreno, catalogo, fx, fz, lx, lz, mira, cabe, casas, puertas);
			t += hueco > 0 ? hueco + 2 : 3;
		}
	}

	/**
	 * Intenta poner una casa con la entrada mirando a (mira) y el bloque de delante de la puerta en (x, z), empezando
	 * a lo largo de la calle en (lx, lz). Devuelve lo que ocupa a lo largo de la calle, o 0 si no cabe ninguna.
	 */
	private static int ponCasa(StructureTemplateManager plantillas, TerrenoCapital terreno, CatalogoCapital catalogo, int x, int z, int lx, int lz,
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
			int y = terreno.alto(x, z) + 1 - entrada.getY();
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
			List<int[]> puertas, int px, int pz, int alturaPlaza, int nivel) {
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
				int x = px + (int) Math.round(Math.cos(a) * (AdornoCapital.PLAZA - 4));
				int z = pz + (int) Math.round(Math.sin(a) * (AdornoCapital.PLAZA - 4));
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
