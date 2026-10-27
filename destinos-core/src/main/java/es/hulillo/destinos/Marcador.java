package es.hulillo.destinos;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

/** Los marcadores por los que se habla con el datapack (jugadores falsos como #sal o #vx). */
final class Marcador {
	/** Constantes del datapack (#sal) y cuentas de geo/territorio (#vx, #tdx…). */
	static final String CONST = "dr.const";
	static final String CALC = "dr.calc";

	private Marcador() {
	}

	/** El valor de nombre en el objetivo, o null si el objetivo o el valor no existen aún. */
	static Integer lee(MinecraftServer server, String nombre, String objetivo) {
		Scoreboard sb = server.getScoreboard();
		Objective o = sb.getObjective(objetivo);
		if (o == null) {
			return null;
		}
		ReadOnlyScoreInfo info = sb.getPlayerScoreInfo(ScoreHolder.forNameOnly(nombre), o);
		return info == null ? null : info.value();
	}

	/** Escribe en dr.calc (si el datapack ya lo ha creado). */
	static void escribe(MinecraftServer server, String nombre, int valor) {
		Scoreboard sb = server.getScoreboard();
		Objective o = sb.getObjective(CALC);
		if (o != null) {
			sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly(nombre), o).set(valor);
		}
	}
}
