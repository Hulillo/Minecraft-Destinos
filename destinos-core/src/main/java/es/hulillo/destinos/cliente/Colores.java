package es.hulillo.destinos.cliente;

/** Los colores del mapa político que comparten todos los mapas (RGB, sin alfa). */
public final class Colores {
	/** Ducados que acaban de cambiar de dueño y coronas de las capitales. */
	public static final int ORO = 0xffc83d;
	/** Frentes de guerra. */
	public static final int ROJO = 0xd42a2a;
	/** Corona de una capital que hoy tiene otro reino. */
	public static final int GRIS = 0x9e9e9e;

	private Colores() {
	}

	/** El color oscurecido a 3/5 (líneas de ducado); aplicado dos veces, el de reino. */
	public static int oscuro(int rgb) {
		return (rgb >> 16 & 0xff) * 3 / 5 << 16 | (rgb >> 8 & 0xff) * 3 / 5 << 8 | (rgb & 0xff) * 3 / 5;
	}
}
