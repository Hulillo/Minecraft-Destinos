package es.hulillo.destinos;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * El plano de calles de una capital: una cruz de cuatro avenidas desde la plaza mayor hasta las cuatro puertas de la
 * muralla (norte, este, sur y oeste), una calle de ronda por dentro del muro y otra ronda a media distancia. Las
 * casas del pueblo solo se quedan si dan a una de esas calles (o están en el centro, alrededor de la plaza), nunca
 * encima de ellas ni entre la ronda y la muralla; de las calles del pueblo solo quedan las del centro. Donde las
 * calles se quedan sin casas, se ponen casas nuevas mirando a ellas.
 */
final class PlanoCapital {
	/** El centro, alrededor de la plaza: ahí se quedan las casas y las calles del pueblo tal cual. */
	private static final int CENTRO = AdornoCapital.PLAZA + 26;
	/** A cuánto de una calle del plano puede quedar una casa para que se quede. */
	private static final int JUNTO = 14;

	private final int cx;
	private final int cz;
	/** Radio de la calle de ronda (su eje), por dentro de la muralla. */
	final int ronda;
	/** Radio de la ronda de en medio, entre el centro y la ronda del muro. */
	final int rondaMedia;
	/** Hasta dónde llegan las avenidas: un poco más allá de la muralla, para que la crucen por la puerta. */
	final int finAvenida;

	PlanoCapital(int cx, int cz, int nivel) {
		this.cx = cx;
		this.cz = cz;
		int radio = MurallaCapital.radio(nivel);
		ronda = radio - 6;
		rondaMedia = (AdornoCapital.PLAZA + ronda) / 2 + 6;
		finAvenida = radio + 8;
	}

	/** Lo más cerca del centro que llega la caja, en planta. */
	private double cerca(BoundingBox b) {
		long dx = Math.max(0, Math.max(b.minX() - cx, cx - b.maxX()));
		long dz = Math.max(0, Math.max(b.minZ() - cz, cz - b.maxZ()));
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Lo más lejos del centro que llega la caja (su esquina más lejana). */
	private double lejos(BoundingBox b) {
		long dx = Math.max(Math.abs(b.minX() - cx), Math.abs(b.maxX() - cx));
		long dz = Math.max(Math.abs(b.minZ() - cz), Math.abs(b.maxZ() - cz));
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Distancia de la caja a la avenida más cercana (las dos rectas que cruzan el centro). */
	private long aAvenida(BoundingBox b) {
		long dx = Math.max(0, Math.max(b.minX() - cx, cx - b.maxX()));
		long dz = Math.max(0, Math.max(b.minZ() - cz, cz - b.maxZ()));
		return Math.min(dx, dz);
	}

	/** Distancia de la caja a una ronda de radio r. */
	private double aRonda(BoundingBox b, int r) {
		double c = cerca(b);
		double l = lejos(b);
		return c > r ? c - r : l < r ? r - l : 0;
	}

	/** Distancia de la caja a la ronda más cercana. */
	private double aRonda(BoundingBox b) {
		return Math.min(aRonda(b, ronda), aRonda(b, rondaMedia));
	}

	/** Si la caja pisa una avenida (fuera de la plaza) o la ronda, con un bloque de margen. */
	boolean pisa(BoundingBox b) {
		return aAvenida(b) <= 2 && lejos(b) > AdornoCapital.PLAZA || aRonda(b) <= 2;
	}

	/** Si algo de la caja queda pasada la ronda, pegado a la muralla. */
	boolean juntoAlMuro(BoundingBox b) {
		return lejos(b) > ronda - 2;
	}

	/** Si una casa del pueblo se queda: en el centro o dando a una calle del plano, sin pisarla y lejos del muro. */
	boolean casa(BoundingBox b) {
		if (pisa(b) || juntoAlMuro(b)) {
			return false;
		}
		return cerca(b) <= CENTRO || aAvenida(b) <= JUNTO || aRonda(b) <= JUNTO;
	}

	/**
	 * Si cabe una casa nueva puesta a lo largo de una calle: sin tocar ninguna calle del plano (la caja puede quedar
	 * pegada a ella), fuera de la plaza mayor y lejos del muro.
	 */
	boolean cabe(BoundingBox b) {
		return aAvenida(b) >= 2 && aRonda(b) >= 1.5 && cerca(b) > AdornoCapital.PLAZA + 2 && !juntoAlMuro(b);
	}

	/** Si una calle del pueblo se queda: solo las del centro. */
	boolean calle(BoundingBox b) {
		return lejos(b) <= CENTRO && !juntoAlMuro(b);
	}
}
