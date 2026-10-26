package es.hulillo.destinos.politica;

/**
 * Algo que ha pasado en un turno político, para anunciarlo y guardarlo en la crónica.
 * reinoB y ducado valen Politica.NADIE cuando no hacen falta.
 */
public record Suceso(Tipo tipo, int anio, long reinoA, long reinoB, long ducado) {
	/** El nombre de cada tipo es también el de su texto (clave de idioma destinos.politica.<nombre>). */
	public enum Tipo {
		guerra, paz, conquista, cae, alianza, ruptura, vasallaje, independencia, anexion, disolucion, expansion,
		/** El reino A toma la capital del B (ducado). */
		capital,
		/** A y B se unen en una corona o liga con nombre propio; fin_union: se separan. */
		union, fin_union
	}
}
