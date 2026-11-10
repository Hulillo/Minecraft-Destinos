package es.hulillo.destinos;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;

/**
 * La cultura de cada ducado, por su tierra: la del bioma de su centro, como la elige el datapack (cultura/detectar).
 * Así un reino puede ser mixto: su dinastía tiene la cultura de donde nació el reino, pero un ducado de desierto que
 * gana sigue siendo del desierto, con sus nombres de sitio y su gente. Se mira cada ducado una vez por partida, con
 * el generador (sin cargar chunks). Solo desde el hilo del servidor.
 */
final class CulturaDucados {
	private final Map<Long, String> hechas = new HashMap<>();
	private final MuestreadorMundo muestreador;
	private final ServerLevel level;
	private final int sal;

	private CulturaDucados(ServerLevel level, int sal) {
		this.muestreador = new MuestreadorMundo(level);
		this.level = level;
		this.sal = sal;
	}

	private static CulturaDucados actual;

	static CulturaDucados de(ServerLevel level, int sal) {
		if (actual == null || actual.sal != sal || actual.level != level) {
			actual = new CulturaDucados(level, sal);
		}
		return actual;
	}

	static void olvida() {
		actual = null;
	}

	/** La cultura del ducado (dcx, dcz): "norte", "desierto"… */
	String de(int dcx, int dcz) {
		return hechas.computeIfAbsent(Geo.clave(dcx, dcz), k -> {
			int[] c = Geo.centroDucado(dcx, dcz, sal);
			return muestreador.cultura(c[0], c[1]);
		});
	}

	/** La cultura de la tierra en un sitio suelto (para cuando aún no se sabe de qué ducado es). */
	String enSitio(int x, int z) {
		return muestreador.cultura(x, z);
	}
}
