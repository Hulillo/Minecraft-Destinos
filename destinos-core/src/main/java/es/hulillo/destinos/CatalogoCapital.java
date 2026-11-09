package es.hulillo.destinos;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Lo que una capital toma de un pueblo (de vanilla o de un mod) sin montarlo: sus plazas, sus casas (cada una con su
 * entrada, el bloque puzle por el que se engancha a la calle) y su gente (aldeanos, gatos, gólem), recorriendo sus
 * listas de piezas desde la de inicio. Se hace una vez por pueblo y se guarda.
 */
final class CatalogoCapital {
	/** Una casa que se puede poner: su elemento y, sin girar, dónde está su entrada y hacia dónde mira. */
	/**
	 * Una casa del catálogo. puerta: a qué altura del puzle de la entrada está el pie de su puerta (0 o 1), o
	 * SIN_PUERTA si no se encontró: unas casas llevan el puzle a la altura del suelo de la calle y otras a la del
	 * umbral, y por eso unas salían subidas un escalón y otras hundidas.
	 */
	record Modelo(StructurePoolElement elemento, BlockPos entrada, Direction mira, int ancho, int largo, int puerta) {
	}

	static final int SIN_PUERTA = Integer.MIN_VALUE;

	/** Lo más larga que puede ser una casa del catálogo (las más grandes no caben en las parcelas). */
	static final int MAX_CASA = 16;
	/** Listas de piezas que se recorren como mucho por pueblo (los de mods pueden tener muchísimas). */
	private static final int MAX_LISTAS = 80;

	final List<StructurePoolElement> plazas;
	final List<Modelo> casas;
	final List<StructurePoolElement> aldeanos;
	final List<StructurePoolElement> gatos;
	final List<StructurePoolElement> golems;

	private CatalogoCapital(List<StructurePoolElement> plazas, List<Modelo> casas, List<StructurePoolElement> aldeanos,
			List<StructurePoolElement> gatos, List<StructurePoolElement> golems) {
		this.plazas = plazas;
		this.casas = casas;
		this.aldeanos = aldeanos;
		this.gatos = gatos;
		this.golems = golems;
	}

	boolean sirve() {
		return !plazas.isEmpty() && casas.size() >= 3;
	}

	private static final Map<String, CatalogoCapital> HECHOS = new ConcurrentHashMap<>();
	private static volatile RegistryAccess registrosHechos;

	static CatalogoCapital de(RegistryAccess registros, StructureTemplateManager plantillas, Holder.Reference<Structure> pueblo) {
		if (registrosHechos != registros) {
			HECHOS.clear();
			registrosHechos = registros;
		}
		Identifier k = registros.lookupOrThrow(Registries.STRUCTURE).getKey(pueblo.value());
		return HECHOS.computeIfAbsent(String.valueOf(k), clave -> recorre(registros, plantillas, pueblo.value(), k));
	}

	static void olvida() {
		HECHOS.clear();
	}

	private static CatalogoCapital recorre(RegistryAccess registros, StructureTemplateManager plantillas, Structure pueblo, Identifier k) {
		List<StructurePoolElement> plazas = new ArrayList<>();
		Map<StructurePoolElement, Modelo> casas = new LinkedHashMap<>();
		Set<StructurePoolElement> aldeanos = new java.util.LinkedHashSet<>();
		Set<StructurePoolElement> gatos = new java.util.LinkedHashSet<>();
		Set<StructurePoolElement> golems = new java.util.LinkedHashSet<>();
		Optional<Holder.Reference<StructureTemplatePool>> inicio = inicio(registros, pueblo);
		if (inicio.isEmpty()) {
			DestinosCore.LOG.warn("Destinos Core: el pueblo {} no tiene lista de inicio; no sirve para capitales", k);
			return new CatalogoCapital(List.of(), List.of(), List.of(), List.of(), List.of());
		}
		Registry<StructureTemplatePool> listas = registros.lookupOrThrow(Registries.TEMPLATE_POOL);
		Set<Identifier> vistas = new HashSet<>();
		Deque<Holder.Reference<StructureTemplatePool>> pendientes = new ArrayDeque<>();
		pendientes.add(inicio.get());
		vistas.add(listas.getKey(inicio.get().value()));
		RandomSource azar = RandomSource.create(0);
		while (!pendientes.isEmpty() && vistas.size() <= MAX_LISTAS) {
			Holder.Reference<StructureTemplatePool> lista = pendientes.poll();
			String nombre = String.valueOf(listas.getKey(lista.value()));
			boolean esInicio = lista == inicio.get();
			Set<StructurePoolElement> elementos = new java.util.LinkedHashSet<>(lista.value().getShuffledTemplates(azar));
			for (StructurePoolElement e : elementos) {
				if (PueblosCapital.abandonado(e.toString())) {
					continue;
				}
				if (esInicio) {
					plazas.add(e);
				} else if (nombre.contains("villagers")) {
					aldeanos.add(e);
				} else if (nombre.contains("cats")) {
					gatos.add(e);
				} else if (nombre.contains("golem")) {
					golems.add(e);
				} else if (e.getProjection() == StructureTemplatePool.Projection.RIGID && !nombre.contains("decor")) {
					modelo(plantillas, e).ifPresent(m -> casas.putIfAbsent(e, m));
				}
				List<StructureTemplate.JigsawBlockInfo> puzles;
				try {
					puzles = e.getShuffledJigsawBlocks(plantillas, BlockPos.ZERO, Rotation.NONE, azar);
				} catch (RuntimeException fallo) {
					continue;
				}
				for (StructureTemplate.JigsawBlockInfo j : puzles) {
					listas.get(j.pool()).ifPresent(siguiente -> {
						if (vistas.add(listas.getKey(siguiente.value()))) {
							pendientes.add(siguiente);
						}
					});
				}
			}
		}
		CatalogoCapital c = new CatalogoCapital(plazas, new ArrayList<>(casas.values()), new ArrayList<>(aldeanos), new ArrayList<>(gatos),
				new ArrayList<>(golems));
		DestinosCore.LOG.info("Destinos Core: catálogo de capitales de {}: {} plazas, {} casas, {} aldeanos, {} gatos, {} gólems ({} listas)", k,
				c.plazas.size(), c.casas.size(), c.aldeanos.size(), c.gatos.size(), c.golems.size(), vistas.size());
		return c;
	}

	/** La casa, si lo es: de buen tamaño y con una entrada de lado (un puzle horizontal). */
	private static Optional<Modelo> modelo(StructureTemplateManager plantillas, StructurePoolElement e) {
		BoundingBox b;
		try {
			b = e.getBoundingBox(plantillas, BlockPos.ZERO, Rotation.NONE);
		} catch (RuntimeException fallo) {
			return Optional.empty();
		}
		int ancho = Math.min(b.getXSpan(), b.getZSpan());
		int largo = Math.max(b.getXSpan(), b.getZSpan());
		if (ancho < 5 || largo > MAX_CASA || b.getYSpan() < 4) {
			return Optional.empty();
		}
		List<StructureTemplate.JigsawBlockInfo> puzles = e.getShuffledJigsawBlocks(plantillas, BlockPos.ZERO, Rotation.NONE, RandomSource.create(0));
		// solo las casas de una pieza: si cuelga de ella otra parte (el piso de arriba, un ala, los animales del
		// corral…), sola salía a medio hacer o vacía
		for (StructureTemplate.JigsawBlockInfo j : puzles) {
			if (!suelta(j.pool().identifier().toString())) {
				return Optional.empty();
			}
		}
		for (StructureTemplate.JigsawBlockInfo j : puzles) {
			Direction d = JigsawBlock.getFrontFacing(j.info().state());
			if (d.getAxis().isHorizontal()) {
				return Optional.of(new Modelo(e, j.info().pos(), d, ancho, largo, puerta(plantillas, e, j.info().pos())));
			}
		}
		return Optional.empty();
	}

	private static final java.util.regex.Pattern PLANTILLA = java.util.regex.Pattern.compile("Left\\[([a-z0-9_.\\-]+:[a-z0-9_./\\-]+)\\]");

	/**
	 * La altura del pie de la puerta más cercana a la entrada, respecto a la entrada (solo si es 0 o 1; si no, la
	 * casa tiene porche con escalones o la puerta está lejos, y vale lo de siempre). La plantilla se saca del nombre
	 * de la pieza (Single[Left[id]]).
	 */
	private static int puerta(StructureTemplateManager plantillas, StructurePoolElement e, BlockPos entrada) {
		try {
			java.util.regex.Matcher m = PLANTILLA.matcher(e.toString());
			if (!m.find()) {
				return SIN_PUERTA;
			}
			Optional<StructureTemplate> t = plantillas.get(Identifier.parse(m.group(1)));
			if (t.isEmpty()) {
				return SIN_PUERTA;
			}
			net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings ajustes =
					new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
			int mejor = SIN_PUERTA;
			int distancia = Integer.MAX_VALUE;
			for (net.minecraft.world.level.block.Block b : Puertas.TODAS) {
				for (StructureTemplate.StructureBlockInfo i : t.get().filterBlocks(BlockPos.ZERO, ajustes, b)) {
					if (i.state().getValue(net.minecraft.world.level.block.DoorBlock.HALF)
							!= net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
						continue;
					}
					int dh = Math.abs(i.pos().getX() - entrada.getX()) + Math.abs(i.pos().getZ() - entrada.getZ());
					if (dh < distancia) {
						distancia = dh;
						mejor = i.pos().getY() - entrada.getY();
					}
				}
			}
			return distancia <= 3 && (mejor == 0 || mejor == 1) ? mejor : SIN_PUERTA;
		} catch (RuntimeException fallo) {
			return SIN_PUERTA;
		}
	}

	/** Todas las puertas que hay (de los mods también), sacadas la primera vez que hacen falta (el registro ya está lleno). */
	private static final class Puertas {
		static final List<net.minecraft.world.level.block.Block> TODAS = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
				.filter(b -> b instanceof net.minecraft.world.level.block.DoorBlock).toList();
	}

	/** Las listas a las que puede apuntar un puzle de una casa sin que le falte nada: vacía, calles, gente y adornos. */
	private static final String[] SUELTAS = {"empty", "street", "terminator", "villagers", "cats", "golem", "decor", "road", "path"};

	private static boolean suelta(String lista) {
		for (String s : SUELTAS) {
			if (lista.contains(s)) {
				return true;
			}
		}
		return false;
	}

	/** La lista de inicio del pueblo, leída con el codec (los campos de JigsawStructure cambian entre versiones). */
	private static Optional<Holder.Reference<StructureTemplatePool>> inicio(RegistryAccess registros, Structure pueblo) {
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registros);
		Optional<JsonElement> json = Structure.DIRECT_CODEC.encodeStart(ops, pueblo).result();
		if (json.isEmpty() || !json.get().isJsonObject()) {
			return Optional.empty();
		}
		JsonElement inicio = json.get().getAsJsonObject().get("start_pool");
		if (inicio == null || !inicio.isJsonPrimitive()) {
			return Optional.empty();
		}
		Identifier id = Identifier.tryParse(inicio.getAsString());
		return id == null ? Optional.empty() : registros.lookupOrThrow(Registries.TEMPLATE_POOL).get(id);
	}
}
