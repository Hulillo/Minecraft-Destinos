package es.hulillo.destinos;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerLevel;

/**
 * El año del mundo, el mismo que el de Serene Seasons: un año es una vuelta entera a las estaciones (por defecto
 * 96 días). Serene Seasons se lee por reflexión, así que no hace falta tenerlo instalado: sin él, el año dura
 * DIAS_SIN_ESTACIONES.
 */
final class Estaciones {
	private static final int DIA = 24000;
	private static final int DIAS_SIN_ESTACIONES = 96;
	private static final Method ESTADO;
	private static final Method CICLO;
	private static final Method EN_CICLO;
	private static final Method ESTACION;

	static {
		Method estado = null;
		Method ciclo = null;
		Method enCiclo = null;
		Method estacion = null;
		try {
			Class<?> ayuda = Class.forName("sereneseasons.api.season.SeasonHelper");
			Class<?> iEstado = Class.forName("sereneseasons.api.season.ISeasonState");
			estado = ayuda.getMethod("getSeasonState", net.minecraft.world.level.Level.class);
			ciclo = iEstado.getMethod("getCycleDuration");
			enCiclo = iEstado.getMethod("getSeasonCycleTicks");
			estacion = iEstado.getMethod("getSeason");
			DestinosCore.LOG.info("Destinos Core: el año del mundo sigue las estaciones de Serene Seasons");
		} catch (ClassNotFoundException e) {
			// sin Serene Seasons
		} catch (ReflectiveOperationException | LinkageError e) {
			DestinosCore.LOG.warn("Destinos Core: Serene Seasons está pero no se ha podido leer su año; uso {} días", DIAS_SIN_ESTACIONES, e);
			estado = null;
		}
		ESTADO = estado;
		CICLO = ciclo;
		EN_CICLO = enCiclo;
		ESTACION = estacion;
	}

	private Estaciones() {
	}

	/** La estación de Serene Seasons (SPRING, SUMMER, AUTUMN o WINTER), o null sin Serene Seasons. */
	static String estacion(ServerLevel mundo) {
		if (ESTADO == null || ESTACION == null) {
			return null;
		}
		try {
			Object e = ESTACION.invoke(ESTADO.invoke(null, mundo));
			return e instanceof Enum<?> en ? en.name() : null;
		} catch (ReflectiveOperationException | ClassCastException e) {
			return null;
		}
	}

	/** Año del mundo, desde 1. */
	static int anio(ServerLevel mundo) {
		long ahora = Math.max(0, mundo.getDayTime());
		if (ESTADO != null) {
			try {
				Object estado = ESTADO.invoke(null, mundo);
				long ciclo = ((Number) CICLO.invoke(estado)).longValue();
				long enCiclo = ((Number) EN_CICLO.invoke(estado)).longValue();
				if (ciclo > 0) {
					// las vueltas completas que caben antes del principio del año actual
					return 1 + (int) (Math.max(0, ahora - enCiclo + ciclo / 2) / ciclo);
				}
			} catch (ReflectiveOperationException | ClassCastException e) {
				// se cae al año sin estaciones
			}
		}
		return 1 + (int) (ahora / ((long) DIAS_SIN_ESTACIONES * DIA));
	}
}
