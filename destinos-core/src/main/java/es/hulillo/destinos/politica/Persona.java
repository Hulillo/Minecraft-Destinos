package es.hulillo.destinos.politica;

/**
 * Una persona de verdad del mundo: un rey, su familia, un duque, un general… Vive en el motor de la política (se
 * guarda con la partida), envejece con los años del mundo y muere. Su cuerpo en el mundo (v0.7.1) sale de aquí.
 * Las personas no se borran al morir mientras la crónica o el linaje las nombren.
 */
public final class Persona {
	public static final int NADIE = -1;

	/** Lo que es hoy en su reino (o lo último que fue, si ha muerto). */
	public enum Rol {
		rey, consorte, familia, duque, general, consejero,
		/** Sin cargo: destronados, señores sin tierras, exiliados de un reino caído. */
		sin_cargo
	}

	/** Rasgos de carácter: cada persona tiene dos, nunca dos opuestos. */
	public enum Rasgo {
		valiente, cobarde, cruel, justo, ambicioso, humilde, codicioso, generoso, piadoso, astuto;

		public boolean opuesto(Rasgo o) {
			return this != o && ordinal() < 8 && o.ordinal() < 8 && ordinal() / 2 == o.ordinal() / 2;
		}
	}

	/** De qué ha muerto. */
	public enum Muerte {
		vejez, enfermedad, guerra, golpe, asesinato, parto
	}

	public final int id;
	public final String nombre;
	public String casa;
	public final String cultura;
	public final boolean mujer;
	/** Año del mundo en que nació (puede ser negativo: nació antes del año 1). */
	public final int nace;
	public Rol rol;
	/** Reino al que sirve o en el que vive. */
	public long reino;
	/** Ducado que gobierna (duques), o Politica.NADIE. */
	public long ducado = Politica.NADIE;
	public int padre = NADIE;
	public int madre = NADIE;
	public int conyuge = NADIE;
	public final Rasgo rasgo1;
	public final Rasgo rasgo2;
	/** Notas de 0 a 10. */
	public final int guerra;
	public final int diplomacia;
	public final int intriga;
	public int fama;
	/** Lealtad a su rey, de 0 a 100 (los reyes no la usan). */
	public int lealtad = 60;
	/** Número de rey (Aldric II), 0 si nunca ha reinado. */
	public int ordinal;
	/** Mote ganado en vida ("conquistador"…, clave de idioma destinos.gente.mote.*), o null. */
	public String mote;
	/** Año de su muerte, o 0 si vive. */
	public int muere;
	public Muerte causa;

	public Persona(int id, String nombre, String casa, String cultura, boolean mujer, int nace, Rol rol, long reino, Rasgo rasgo1,
			Rasgo rasgo2, int guerra, int diplomacia, int intriga) {
		this.id = id;
		this.nombre = nombre;
		this.casa = casa;
		this.cultura = cultura;
		this.mujer = mujer;
		this.nace = nace;
		this.rol = rol;
		this.reino = reino;
		this.rasgo1 = rasgo1;
		this.rasgo2 = rasgo2;
		this.guerra = guerra;
		this.diplomacia = diplomacia;
		this.intriga = intriga;
	}

	public boolean vivo() {
		return muere == 0;
	}

	public int edad(int anio) {
		return (vivo() ? anio : muere) - nace;
	}

	public boolean tiene(Rasgo r) {
		return rasgo1 == r || rasgo2 == r;
	}

	/** Lo bueno que es mandando, para elegir al más capaz. */
	public int valia() {
		return guerra + diplomacia + intriga + fama / 10;
	}

	/** "Aldric II" (o "Aldric" si nunca ha reinado). */
	public String nombreCorto() {
		return ordinal > 0 ? nombre + " " + romano(ordinal) : nombre;
	}

	public static String romano(int n) {
		int[] v = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
		String[] s = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < v.length; i++) {
			while (n >= v[i]) {
				b.append(s[i]);
				n -= v[i];
			}
		}
		return b.toString();
	}

	@Override
	public String toString() {
		return id + ":" + nombreCorto() + "/" + casa + "/" + rol + "/" + nace + "/" + muere + (causa == null ? "" : causa.name());
	}
}
