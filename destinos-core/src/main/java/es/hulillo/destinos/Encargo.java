package es.hulillo.destinos;

import es.hulillo.destinos.politica.Politica;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/**
 * Un encargo cogido de un tablón: a dónde hay que ir, qué hay que hacer allí, cuánto paga y para cuándo. Los textos no
 * se guardan: salen del tipo y del ducado (así siguen al idioma del jugador y al nombre de hoy del ducado).
 */
final class Encargo {
	enum Tipo {
		/** Llegar a una estructura (o al corazón del ducado si no hay ninguna cerca). */
		exploracion,
		/** Acabar con un campamento de bandidos que aparece al acercarse. */
		caza,
		/** Limpiar una mazmorra despertada: aguantar las oleadas y matar al jefe. */
		mazmorra,
		/** Arrasar un campamento del enemigo en un ducado del frente (solo con el reino del tablón en guerra). */
		guerra,
		/** Llevar una carta sellada a la capital de otro reino: trae la paz (en guerra) o una alianza. */
		embajada
	}

	final int id;
	final Tipo tipo;
	/** Reino del tablón (el que paga en reputación), o Politica.NADIE si era de tierra salvaje. */
	final long reino;
	final long ducado;
	/** El sitio al que hay que ir. */
	int x;
	int z;
	final int estrellas;
	final int premio;
	final int rep;
	/** Hora del mundo (getDayTime) a la que vence. */
	final long vence;
	/** Tablón, día y número de la oferta, para no poder cogerla dos veces. */
	final String origen;
	/** exploracion: si (x, z) es una estructura de verdad y no solo el corazón del ducado. */
	boolean estructura;
	/** caza: si el campamento ya está montado, y los bandidos que quedan vivos. */
	boolean campamento;
	final List<UUID> bandidos = new ArrayList<>();
	/** mazmorra: la clave de la mazmorra (chunk de inicio de su estructura). */
	long mazmorra;
	/** guerra: el reino enemigo dueño del campamento; embajada: el reino al que va la carta. */
	long enemigo = Politica.NADIE;
	/** embajada: si ya han salido los cazarrecompensas por el camino. */
	boolean emboscada;

	Encargo(int id, Tipo tipo, long reino, long ducado, int x, int z, int estrellas, int premio, int rep, long vence, String origen) {
		this.id = id;
		this.tipo = tipo;
		this.reino = reino;
		this.ducado = ducado;
		this.x = x;
		this.z = z;
		this.estrellas = estrellas;
		this.premio = premio;
		this.rep = rep;
		this.vence = vence;
		this.origen = origen;
	}

	/** Lo que completa el título: la mazmorra o el otro reino. */
	long objetivo() {
		return tipo == Tipo.guerra || tipo == Tipo.embajada ? enemigo : mazmorra;
	}

	boolean deReino() {
		return reino != Politica.NADIE;
	}

	CompoundTag guarda() {
		CompoundTag t = new CompoundTag();
		t.putInt("id", id);
		t.putString("tipo", tipo.name());
		t.putLong("reino", reino);
		t.putLong("ducado", ducado);
		t.putInt("x", x);
		t.putInt("z", z);
		t.putInt("estrellas", estrellas);
		t.putInt("premio", premio);
		t.putInt("rep", rep);
		t.putLong("vence", vence);
		t.putString("origen", origen);
		t.putBoolean("estructura", estructura);
		t.putBoolean("campamento", campamento);
		t.putLong("mazmorra", mazmorra);
		t.putLong("enemigo", enemigo);
		t.putBoolean("emboscada", emboscada);
		ListTag l = new ListTag();
		for (UUID u : bandidos) {
			l.add(StringTag.valueOf(u.toString()));
		}
		t.put("bandidos", l);
		return t;
	}

	/** Null si es de otra versión y no se entiende. */
	static Encargo carga(CompoundTag t) {
		Tipo tipo;
		try {
			tipo = Tipo.valueOf(t.getStringOr("tipo", ""));
		} catch (IllegalArgumentException e) {
			return null;
		}
		Encargo e = new Encargo(t.getIntOr("id", 0), tipo, t.getLongOr("reino", Politica.NADIE), t.getLongOr("ducado", 0),
				t.getIntOr("x", 0), t.getIntOr("z", 0), t.getIntOr("estrellas", 1), t.getIntOr("premio", 0), t.getIntOr("rep", 0),
				t.getLongOr("vence", 0), t.getStringOr("origen", ""));
		e.estructura = t.getBooleanOr("estructura", false);
		e.campamento = t.getBooleanOr("campamento", false);
		e.mazmorra = t.getLongOr("mazmorra", 0);
		e.enemigo = t.getLongOr("enemigo", Politica.NADIE);
		e.emboscada = t.getBooleanOr("emboscada", false);
		ListTag l = t.getListOrEmpty("bandidos");
		for (int i = 0; i < l.size(); i++) {
			try {
				e.bandidos.add(UUID.fromString(l.getStringOr(i, "")));
			} catch (IllegalArgumentException x) {
				// se salta
			}
		}
		return e;
	}
}
