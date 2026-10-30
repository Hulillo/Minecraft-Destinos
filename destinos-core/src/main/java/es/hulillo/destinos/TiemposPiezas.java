package es.hulillo.destinos;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * Cuánto tiempo se va en poner cada tipo de pieza (en todos los chunks y todos los hilos), para ver qué cuesta más al
 * generar las capitales. Lo mide MixinStructureStart y se escribe en el log al parar el servidor.
 */
public final class TiemposPiezas {
	private static final Map<String, LongAdder[]> TIEMPOS = new ConcurrentHashMap<>();

	private TiemposPiezas() {
	}

	public static void suma(StructurePiece pieza, long nanos) {
		var id = BuiltInRegistries.STRUCTURE_PIECE.getKey(pieza.getType());
		String tipo = id == null ? pieza.getClass().getSimpleName() : id.toString();
		LongAdder[] t = TIEMPOS.computeIfAbsent(tipo, k -> new LongAdder[] {new LongAdder(), new LongAdder()});
		t[0].add(nanos);
		t[1].increment();
	}

	static void escribe() {
		if (TIEMPOS.isEmpty()) {
			return;
		}
		StringBuilder sb = new StringBuilder("Destinos Core: tiempo poniendo piezas de estructuras (total, veces):");
		new TreeMap<>(TIEMPOS).entrySet().stream()
				.sorted((a, b) -> Long.compare(b.getValue()[0].sum(), a.getValue()[0].sum()))
				.limit(16)
				.forEach(e -> sb.append(' ').append(e.getKey()).append(' ').append(e.getValue()[0].sum() / 1_000_000).append(" ms (")
						.append(e.getValue()[1].sum()).append(") ·"));
		DestinosCore.LOG.info(sb.toString());
	}
}
