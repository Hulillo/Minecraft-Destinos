package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * Los ojos de la generación: compara un asentamiento ya generado en el mundo con su plano y cuenta lo que ha salido
 * mal, con sitios de ejemplo. Lo usan /destinoscore audita (en el juego) y el servidor de prueba de la CI, que así ve
 * los fallos de muralla, casas y calles sin que nadie tenga que encontrarlos paseando.
 * <ul>
 * <li>Muralla: columnas del plano sin muralla (huecos), enterradas (por fuera asoma menos de dos bloques), colgando
 * (aire justo debajo con el suelo cerca del pie: no es un arco; los arcos se cuentan aparte, solo para saberlo) y
 * portillos por los que no se pasa.</li>
 * <li>Casas: con tierra dentro, colgando, enterradas y con puertas tapadas (un escalón o una losa delante no tapan).</li>
 * <li>Calles: por su eje, donde se pisa (no el tejado ni el dintel de encima): escalones de más de un bloque, trozos
 * lejos de la altura del plano y trozos sobre el agua sin puente.</li>
 * </ul>
 * Solo mira chunks cargados; lo que no lo está se cuenta aparte.
 */
final class Auditoria {
	/** Cuántos sitios de ejemplo se guardan de cada fallo. */
	private static final int EJEMPLOS = 4;
	/** Lo que tiene que asomar la muralla por fuera para no contarla enterrada. */
	private static final int ASOMA = 2;
	/** Bloques de terreno dentro de una casa para contarla con tierra dentro. */
	private static final int TIERRA_CASA = 6;
	/** Lo que la calle puede separarse de la altura de su plano. */
	private static final int LEJOS = 3;

	private final ServerLevel level;
	private final Map<String, Integer> cuenta = new LinkedHashMap<>();
	private final Map<String, List<String>> sitios = new LinkedHashMap<>();
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

	private Auditoria(ServerLevel level) {
		this.level = level;
		for (String k : new String[] {"muralla.columnas", "muralla.arcos", "muralla.portillos", "muralla.hueco", "muralla.enterrada", "muralla.colgando",
			"muralla.portillo_tapado",
			"casas", "casas.tierra_dentro", "casas.colgando", "casas.enterrada", "casas.puerta_tapada", "calles.bloques", "calles.escalon",
			"calles.lejos_del_plano", "calles.sin_suelo", "terrazas.columnas", "terrazas.sin_hacer", "sin_cargar"}) {
			cuenta.put(k, 0);
		}
	}

	/** Audita las piezas de un plano; devuelve las líneas del informe (la primera, el resumen). */
	static List<String> de(ServerLevel level, String que, List<StructurePiece> piezas) {
		Auditoria a = new Auditoria(level);
		for (StructurePiece p : piezas) {
			if (p instanceof TerrazaCapital t) {
				a.terrazas(t);
			} else if (p instanceof MurallaCapital m) {
				a.muralla(m);
			} else if (p instanceof CalleCapital c) {
				a.calle(c);
			} else if (p instanceof PoolElementStructurePiece casa && (casa.getBoundingBox().getXSpan() > 2 || casa.getBoundingBox().getZSpan() > 2)) {
				a.casa(casa);
			}
		}
		return a.informe(que);
	}

	private void suma(String que) {
		cuenta.merge(que, 1, Integer::sum);
	}

	private void fallo(String que, int x, int y, int z) {
		fallo(que, x, y, z, "");
	}

	/** Apunta un fallo, con un detalle para el sitio de ejemplo (qué se esperaba y qué hay). */
	private void fallo(String que, int x, int y, int z, String detalle) {
		suma(que);
		List<String> l = sitios.computeIfAbsent(que, k -> new ArrayList<>());
		if (l.size() < EJEMPLOS) {
			l.add(x + " " + y + " " + z + detalle);
		}
	}

	private String nombre(int x, int y, int z) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(en(x, y, z).getBlock()).getPath();
	}

	private boolean cargado(int x, int z) {
		if (level.hasChunk(x >> 4, z >> 4)) {
			return true;
		}
		suma("sin_cargar");
		return false;
	}

	private BlockState en(int x, int y, int z) {
		return level.getBlockState(pos.set(x, y, z));
	}

	/** El suelo firme de arriba en (x, z), sin hojas ni troncos ni plantas. */
	private int suelo(int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		while (y > level.getMinY() && (en(x, y, z).is(BlockTags.LOGS) || en(x, y, z).is(BlockTags.LEAVES))) {
			y--;
		}
		return y;
	}

	// ------------------------------------------------------------------ terrazas

	/** Las columnas de terreno que no han quedado a la altura de su bancal (más de un bloque de diferencia). */
	private void terrazas(TerrazaCapital t) {
		BoundingBox b = t.getBoundingBox();
		for (int x = b.minX(); x <= b.maxX(); x++) {
			for (int z = b.minZ(); z <= b.maxZ(); z++) {
				int alto = t.bancal(x, z);
				if (TerrazaCapital.sin(alto) || !level.hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				int h = suelo(x, z);
				if (!SueloNatural.natural(en(x, h, z))) {
					continue;
				}
				suma("terrazas.columnas");
				if (Math.abs(h - alto) > 1) {
					fallo("terrazas.sin_hacer", x, h, z, " (bancal " + alto + ")");
				}
			}
		}
	}

	// ------------------------------------------------------------------ muralla

	private void muralla(MurallaCapital m) {
		if (m.esTorre()) {
			return;
		}
		BoundingBox todo = new BoundingBox(Integer.MIN_VALUE / 2, level.getMinY(), Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2, level.getMaxY(),
				Integer.MAX_VALUE / 2);
		int alto = PlanMuralla.alto(m.nivel());
		m.recorre(todo, (x, z, pie, arriba, portillo, andado, nx, nz, caraDeFuera) -> {
			if (!cargado(x, z)) {
				return;
			}
			suma("muralla.columnas");
			if (portillo != MurallaCapital.SIN_PORTILLO) {
				// se pasa si hay dos bloques libres seguidos sobre un suelo a la altura del portillo, uno arriba o uno abajo
				suma("muralla.portillos");
				boolean pasa = false;
				for (int f = portillo - 1; f <= portillo + 1 && !pasa; f++) {
					pasa = !libre(x, f, z) && libre(x, f + 1, z) && libre(x, f + 2, z);
				}
				if (!pasa) {
					fallo("muralla.portillo_tapado", x, portillo + 1, z,
							" (" + nombre(x, portillo, z) + ", " + nombre(x, portillo + 1, z) + ", " + nombre(x, portillo + 2, z) + ")");
				}
				return;
			}
			// lo que hay de muralla en la columna, de bastante por debajo del pie a por encima de lo alto (puede haber
			// subido con una loma)
			int abajo = Integer.MAX_VALUE;
			int cima = Integer.MIN_VALUE;
			for (int y = pie - 30; y <= arriba + 12; y++) {
				if (m.deMuralla(en(x, y, z))) {
					abajo = Math.min(abajo, y);
					cima = y;
				}
			}
			// tiene que haber muralla de verdad por encima del pie, no solo un bloque del mismo material en el suelo
			int puesta = 0;
			for (int y = Math.max(abajo, cima - alto); y <= cima && cima != Integer.MIN_VALUE; y++) {
				puesta += m.deMuralla(en(x, y, z)) ? 1 : 0;
			}
			if (puesta < Math.min(3, alto)) {
				fallo("muralla.hueco", x, pie + 1, z);
				return;
			}
			if (caraDeFuera) {
				int fx = x + (int) Math.round(nx * 2);
				int fz = z + (int) Math.round(nz * 2);
				if (level.hasChunk(fx >> 4, fz >> 4) && cima - suelo(fx, fz) < ASOMA) {
					fallo("muralla.enterrada", x, cima, z, " (pie " + pie + ", fuera " + suelo(fx, fz) + " de " + nombre(fx, suelo(fx, fz), fz) + ")");
				}
			}
			// aire justo debajo: un arco si el suelo queda hondo bajo el pie (para eso son); si queda cerca, cuelga
			if (en(x, abajo - 1, z).isAir()) {
				int y = abajo - 1;
				while (y > pie - 40 && en(x, y, z).isAir()) {
					y--;
				}
				if (pie - y <= 6) {
					fallo("muralla.colgando", x, abajo, z, " (pie " + pie + ", suelo " + y + " de " + nombre(x, y, z) + ")");
				} else {
					suma("muralla.arcos");
				}
			}
		});
	}

	// ------------------------------------------------------------------ casas

	private void casa(PoolElementStructurePiece casa) {
		BoundingBox b = casa.getBoundingBox();
		int cx = (b.minX() + b.maxX()) / 2;
		int cz = (b.minZ() + b.maxZ()) / 2;
		if (!level.hasChunk(b.minX() >> 4, b.minZ() >> 4) || !level.hasChunk(b.maxX() >> 4, b.maxZ() >> 4) || !level.hasChunk(b.minX() >> 4, b.maxZ() >> 4)
				|| !level.hasChunk(b.maxX() >> 4, b.minZ() >> 4)) {
			suma("sin_cargar");
			return;
		}
		suma("casas");
		int suelo = b.minY() + casa.getGroundLevelDelta();
		// tierra dentro: terreno natural por encima del suelo de la casa, sin contar su borde (jardineras, setos)
		int tierra = 0;
		for (int x = b.minX() + 1; x <= b.maxX() - 1; x++) {
			for (int z = b.minZ() + 1; z <= b.maxZ() - 1; z++) {
				for (int y = suelo + 2; y <= b.maxY(); y++) {
					if (SueloNatural.natural(en(x, y, z))) {
						tierra++;
					}
				}
			}
		}
		if (tierra >= TIERRA_CASA) {
			fallo("casas.tierra_dentro", cx, suelo + 1, cz);
		}
		// colgando: bajo sus esquinas y su centro, donde la casa tiene bloque, aire o agua
		int alAire = 0;
		int[][] puntos = {{b.minX(), b.minZ()}, {b.maxX(), b.minZ()}, {b.minX(), b.maxZ()}, {b.maxX(), b.maxZ()}, {cx, cz}};
		for (int[] p : puntos) {
			int y = b.minY();
			while (y <= b.minY() + 2 && en(p[0], y, p[1]).isAir()) {
				y++;
			}
			if (y > b.minY() + 2) {
				continue;
			}
			BlockState debajo = en(p[0], y - 1, p[1]);
			if (debajo.isAir() || !debajo.getFluidState().isEmpty()) {
				alAire++;
			}
		}
		if (alAire >= 2) {
			fallo("casas.colgando", cx, b.minY(), cz);
		}
		// enterrada: el terreno de fuera, a media pared, muy por encima de su suelo en dos lados o más
		int tapados = 0;
		int[][] lados = {{b.minX() - 2, cz}, {b.maxX() + 2, cz}, {cx, b.minZ() - 2}, {cx, b.maxZ() + 2}};
		for (int[] p : lados) {
			if (level.hasChunk(p[0] >> 4, p[1] >> 4) && SueloNatural.natural(en(p[0], suelo + 3, p[1]))) {
				tapados++;
			}
		}
		if (tapados >= 2) {
			fallo("casas.enterrada", cx, suelo + 1, cz);
		}
		// puertas: por los dos lados tiene que haber hueco para pasar y suelo como mucho un bloque más abajo
		for (int x = b.minX(); x <= b.maxX(); x++) {
			for (int z = b.minZ(); z <= b.maxZ(); z++) {
				for (int y = b.minY(); y <= b.maxY(); y++) {
					BlockState s = en(x, y, z);
					if (!(s.getBlock() instanceof DoorBlock) || s.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
						continue;
					}
					Direction d = s.getValue(DoorBlock.FACING);
					if (!paso(x + d.getStepX(), y, z + d.getStepZ()) || !paso(x - d.getStepX(), y, z - d.getStepZ())) {
						fallo("casas.puerta_tapada", x, y, z);
					}
				}
			}
		}
	}

	/** Si el bloque de (x, y, z) deja pasar (no tiene con qué chocar). */
	private boolean libre(int x, int y, int z) {
		return en(x, y, z).getCollisionShape(level, pos).isEmpty();
	}

	/**
	 * Si en (x, y, z) cabe una persona de pie, con suelo en y - 1 o y - 2 (un escalón); o si lo que hay es un escalón
	 * o una losa (la escalera de la entrada) con sitio encima.
	 */
	private boolean paso(int x, int y, int z) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return true;
		}
		BlockState s = en(x, y, z);
		if (s.getBlock() instanceof StairBlock || s.getBlock() instanceof SlabBlock) {
			return libre(x, y + 1, z) && libre(x, y + 2, z);
		}
		if (!libre(x, y, z) || !libre(x, y + 1, z)) {
			return false;
		}
		return !libre(x, y - 1, z) || !libre(x, y - 2, z);
	}

	// ------------------------------------------------------------------ calles

	private void calle(CalleCapital c) {
		int[] t = c.trozo();
		int largo = (int) Math.ceil(Math.hypot(t[3] - t[0], t[4] - t[1]));
		int antes = Integer.MIN_VALUE;
		for (int k = 0; k <= largo; k++) {
			double f = largo == 0 ? 0 : k / (double) largo;
			int x = (int) Math.round(t[0] + (t[3] - t[0]) * f);
			int z = (int) Math.round(t[1] + (t[4] - t[1]) * f);
			if (!cargado(x, z)) {
				antes = Integer.MIN_VALUE;
				continue;
			}
			suma("calles.bloques");
			int h;
			if (t[2] != Integer.MIN_VALUE) {
				// donde se pisa: lo primero firme bajando desde un poco por encima de la altura del plano (así no
				// cuentan el tejado de una casa, el dintel de un portillo ni el arco de un portón que pasan por encima)
				int y = (int) Math.floor(t[2] + (t[5] - t[2]) * f);
				// el suelo pisable más cercano a esa altura: firme, con dos bloques libres encima (un alero o un arco a
				// cuatro bloques por encima no es el suelo de la calle)
				h = Integer.MIN_VALUE;
				for (int d = 0; d <= LEJOS && h == Integer.MIN_VALUE; d++) {
					for (int piso : new int[] {y + d, y - d}) {
						if (h == Integer.MIN_VALUE && !pisable(x, piso, z) && pisable(x, piso + 1, z) && pisable(x, piso + 2, z)) {
							h = piso;
						}
					}
				}
				if (h == Integer.MIN_VALUE) {
					h = y + LEJOS + 1;
					while (h > y - LEJOS - 1 && pisable(x, h, z)) {
						h--;
					}
				}
				if (Math.abs(h - y) > LEJOS) {
					fallo("calles.lejos_del_plano", x, h, z, " (plano " + y + ", " + nombre(x, h, z) + ")");
					antes = Integer.MIN_VALUE;
					continue;
				}
			} else {
				h = suelo(x, z);
			}
			if (!en(x, h, z).getFluidState().isEmpty()) {
				fallo("calles.sin_suelo", x, h, z);
				antes = Integer.MIN_VALUE;
				continue;
			}
			if (antes != Integer.MIN_VALUE && Math.abs(h - antes) > 1) {
				fallo("calles.escalon", x, h, z);
			}
			antes = h;
		}
	}

	/** Si por (x, y, z) se pasa andando: sin nada con qué chocar (ni agua), o plantas, hojas y troncos de árbol. */
	private boolean pisable(int x, int y, int z) {
		BlockState s = en(x, y, z);
		return s.getFluidState().isEmpty() && (s.getCollisionShape(level, pos).isEmpty() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS));
	}

	// ------------------------------------------------------------------ informe

	private List<String> informe(String que) {
		List<String> r = new ArrayList<>();
		StringBuilder s = new StringBuilder("AUDITORIA ").append(que).append(':');
		int fallos = 0;
		for (Map.Entry<String, Integer> e : cuenta.entrySet()) {
			s.append(' ').append(e.getKey()).append('=').append(e.getValue());
			if (sitios.containsKey(e.getKey())) {
				fallos += e.getValue();
			}
		}
		s.append(" · fallos=").append(fallos);
		r.add(s.toString());
		for (Map.Entry<String, List<String>> e : sitios.entrySet()) {
			r.add("AUDITORIA   " + e.getKey() + " en: " + String.join(" | ", e.getValue()));
		}
		return r;
	}
}
