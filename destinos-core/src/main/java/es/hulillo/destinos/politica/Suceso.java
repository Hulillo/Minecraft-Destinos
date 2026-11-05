package es.hulillo.destinos.politica;

/**
 * Algo que ha pasado en un turno político, para anunciarlo y guardarlo en la crónica.
 * reinoB y ducado valen Politica.NADIE cuando no hacen falta. ayuda: los jugadores que ayudaron a una conquista
 * arrasando campamentos del enemigo ("hulio, Pepe"), o null. personaA y personaB: la gente del suceso (ids de
 * Persona), o Persona.NADIE.
 */
public record Suceso(Tipo tipo, int anio, long reinoA, long reinoB, long ducado, String ayuda, int personaA, int personaB) {
	public Suceso(Tipo tipo, int anio, long reinoA, long reinoB, long ducado) {
		this(tipo, anio, reinoA, reinoB, ducado, null);
	}

	public Suceso(Tipo tipo, int anio, long reinoA, long reinoB, long ducado, String ayuda) {
		this(tipo, anio, reinoA, reinoB, ducado, ayuda, Persona.NADIE, Persona.NADIE);
	}

	/** Si el suceso nombra a la persona. */
	public boolean nombra(int persona) {
		return persona != Persona.NADIE && (personaA == persona || personaB == persona);
	}

	/** El nombre de cada tipo es también el de su texto (clave de idioma destinos.politica.<nombre>). */
	public enum Tipo {
		guerra, paz, conquista, cae, alianza, ruptura, vasallaje, independencia, anexion, disolucion, expansion,
		/** El reino A toma la capital del B (ducado). */
		capital,
		/** A y B se unen en una corona o liga con nombre propio; fin_union: se separan. */
		union, fin_union,
		// v0.7 Gente: la persona A (y a veces B) del reino A
		/** Muere el rey A (de viejo, de enfermedad…). */
		muerte,
		/** A sube al trono del reino A (B: el rey anterior). */
		sucesion,
		/** El reino A se queda sin heredero: A, un noble, se hace con la corona. */
		crisis,
		/** Boda de A (reino A) y B (reino B). */
		boda,
		/** A derroca a B en el reino A. */
		golpe,
		/** A cae en la guerra (ducado: donde cayó, si lo hay). */
		caido,
		/** A, ya rey del reino B, hereda la corona del reino A: los dos reinos quedan unidos. */
		herencia,
		/** A se gana un mote (renombre). */
		renombre,
		/** Nace A, hijo de los reyes del reino A. */
		nacimiento,
		/** Un jugador (ayuda) mata a A. */
		asesinato
	}
}
