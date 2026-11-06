package es.hulillo.destinos;

import es.hulillo.destinos.politica.Persona;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.7.1 La corte: hablar con alguien de la corte. El servidor decide qué dice y qué se le puede contestar (según
 * quién es, la hora y tu reputación con su reino); el cliente solo lo enseña (PantallaDialogo) y devuelve la opción
 * elegida. Al rey, además, se le da en mano la carta de las embajadas.
 */
final class DialogoCorte {
	/** Para contestar hay que seguir cerca de quien habla. */
	private static final double ALCANCE = 8;
	private static final int NOTICIAS = 3;
	private static final int RUMORES = 3;
	/** Con esta reputación o más, te reciben como a un amigo. */
	private static final int REP_AMIGO = 30;

	private DialogoCorte() {
	}

	/** Al hacer clic en alguien de la corte. */
	static void abre(ServerPlayer p, CuerpoPersona c) {
		PoliticaMundo pol = DestinosCore.politica();
		Persona x = pol == null ? null : pol.gente().persona(c.persona());
		if (x == null || !x.vivo()) {
			return;
		}
		manda(p, c, x, saludo(p, pol, c, x));
	}

	/** La opción que ha elegido el jugador en la pantalla. */
	static void responde(ServerPlayer p, PaqueteRespuesta r) {
		Entity e = p.level().getEntity(r.entidad());
		PoliticaMundo pol = DestinosCore.politica();
		if (!(e instanceof CuerpoPersona c) || !c.isAlive() || pol == null || p.distanceToSqr(c) > ALCANCE * ALCANCE) {
			return;
		}
		Persona x = pol.gente().persona(c.persona());
		if (x == null || !x.vivo()) {
			return;
		}
		MinecraftServer server = p.level().getServer();
		EncargosMundo em = EncargosMundo.de(server);
		boolean rey = c.cargo == RutinaCorte.Cargo.rey;
		boolean general = c.cargo == RutinaCorte.Cargo.general;
		boolean consejero = c.cargo == RutinaCorte.Cargo.consejero;
		Component texto = switch (r.clave()) {
			case "quien" -> quien(server, pol, c, x);
			case "noticias" -> noticias(server, pol, c);
			case "donde" -> donde(server, pol, c);
			case "familia" -> familia(pol, x);
			case "servir" -> rey ? em.encargoDeCorte(server, p, c.blockPosition(), false) : null;
			case "jurar" -> rey ? em.jura(server, pol, p, c.reino) : null;
			case "perdon" -> rey ? em.perdon(server, pol, p, c.reino) : null;
			case "presente" -> em.presente(server, pol, p, c.reino);
			case "alistarse" -> general ? em.encargoDeCorte(server, p, c.blockPosition(), true) : null;
			case "guerra" -> general ? lista(pol.guerra(server, p, c.reino), "destinos.corte.guerra.paz",
					"Ahora mismo no hay guerra. Que dure.") : null;
			case "rumores" -> consejero ? lista(pol.rumores(server, c.reino, server.overworld().getDayTime() / 24000, RUMORES),
					"destinos.corte.rumor.nada", "Nada que merezca la pena contar. Los reinos de alrededor andan tranquilos.") : null;
			case "peligros" -> consejero ? peligros(server, em, p, c) : null;
			case "carta" -> EncargosMundo.de(server).entregaEnMano(server, pol, p, c.reino)
					? Component.translatableWithFallback("destinos.corte.carta.gracias", "Leeré tu carta con atención. Dile a quien te envía que tendrá respuesta.")
					: Component.translatableWithFallback("destinos.corte.carta.nada", "No traes nada para mí.");
			default -> null;
		};
		if (texto != null) {
			manda(p, c, x, texto);
		}
	}

	private static void manda(ServerPlayer p, CuerpoPersona c, Persona x, Component texto) {
		if (!ServerPlayNetworking.canSend(p, PaqueteDialogo.TIPO)) {
			// sin el mod en el cliente: lo que dice, al chat
			p.sendSystemMessage(Component.empty().append(c.getCustomName() == null ? Component.empty() : c.getCustomName()).append(": ").append(texto));
			return;
		}
		PoliticaMundo pol = DestinosCore.politica();
		MinecraftServer server = p.level().getServer();
		Component cargo = Component.translatableWithFallback("destinos.corte.del_reino", "%s del %s", CorteCapital.cargo(c.cargo, x.mujer),
				pol.nombreReino(server, c.reino));
		ServerPlayNetworking.send(p, new PaqueteDialogo(c.getId(), NombresPolitica.nombre(x), cargo, c.aspecto(), texto,
				opciones(p, c)));
	}

	/** Lo que se le puede decir: depende de su cargo, de tu reputación y de lo que lleves. */
	private static List<PaqueteDialogo.Opcion> opciones(ServerPlayer p, CuerpoPersona c) {
		List<PaqueteDialogo.Opcion> l = new ArrayList<>();
		MinecraftServer server = p.level().getServer();
		EncargosMundo em = EncargosMundo.de(server);
		int rep = em.rep(p, c.reino);
		boolean odio = rep <= Embajadas.REP_GUARDIAS;
		switch (c.cargo) {
			case rey -> {
				if (em.llevaCarta(p, c.reino)) {
					l.add(opcion("carta", "✉ Entregar la carta sellada"));
				}
				if (rep < 0) {
					l.add(opcion("perdon", "⚖ Pido perdón (pagaré la multa)"));
				}
				if (!odio) {
					l.add(opcion("servir", "⚔ ¿Puedo servir a la corona?"));
					if (rep >= EncargosMundo.REP_LEALTAD && em.leal(p) != c.reino) {
						l.add(opcion("jurar", "⚜ Os juro lealtad"));
					}
				}
			}
			case general -> {
				if (!odio) {
					l.add(opcion("alistarse", "⚔ Quiero alistarme"));
					l.add(opcion("guerra", "⚑ ¿Cómo va la guerra?"));
				}
			}
			case consejero -> {
				if (!odio) {
					l.add(opcion("rumores", "✎ ¿Qué se dice de otros reinos?"));
					l.add(opcion("peligros", "☠ ¿Sabéis de algún peligro por aquí?"));
				}
			}
			case consorte, hijo -> {
				if (!odio) {
					l.add(opcion("familia", "⚘ Contadme de vuestra familia"));
				}
			}
		}
		if (!odio) {
			l.add(opcion("quien", "¿Quién eres?"));
			l.add(opcion("noticias", "¿Qué se cuenta por aquí?"));
			if (c.cargo != RutinaCorte.Cargo.rey) {
				l.add(opcion("donde", "¿Dónde puedo encontrar al rey?"));
			}
			if (l.size() < 7 && c.cargo != RutinaCorte.Cargo.hijo) {
				l.add(opcion("presente", "❖ Un presente para vos (20 cobre)"));
			}
		}
		l.add(opcion("adios", "Adiós"));
		return l;
	}

	private static PaqueteDialogo.Opcion opcion(String clave, String es) {
		return new PaqueteDialogo.Opcion(clave, Component.translatableWithFallback("destinos.corte.opcion." + clave, es));
	}

	/** Lo primero que dice: según tu reputación con su reino y su cargo. */
	private static Component saludo(ServerPlayer p, PoliticaMundo pol, CuerpoPersona c, Persona x) {
		MinecraftServer server = p.level().getServer();
		int rep = EncargosMundo.de(server).rep(p, c.reino);
		if (rep <= Embajadas.REP_GUARDIAS) {
			return Component.translatableWithFallback("destinos.corte.saludo.odio", "No eres bienvenido aquí. Vete antes de que llame a la guardia.");
		}
		if (rep >= REP_AMIGO) {
			return Component.translatableWithFallback("destinos.corte.saludo.amigo", "¡Bienvenido! En el %s se habla bien de ti.",
					pol.nombreReino(server, c.reino));
		}
		boolean nino = x.edad(pol.anio()) < es.hulillo.destinos.politica.Gente.ADULTO;
		String clave = nino ? "nino" : c.cargo.name();
		String es = switch (clave) {
			case "nino" -> "¿Eres un aventurero de verdad? ¡Cuéntame algo!";
			case "rey" -> "Estás ante el trono del %s. Habla.";
			case "consorte" -> "Sé bienvenido a la corte del %s.";
			case "hijo" -> "¿Qué te trae a palacio, forastero?";
			case "general" -> "¿Vienes a alistarte o solo estás de paso?";
			default -> "El consejo tiene mucho trabajo. Sé breve.";
		};
		return Component.translatableWithFallback("destinos.corte.saludo." + clave, es, pol.nombreReino(server, c.reino));
	}

	private static Component quien(MinecraftServer server, PoliticaMundo pol, CuerpoPersona c, Persona x) {
		return Component.translatableWithFallback("destinos.corte.quien", "Soy %s, %s del %s. Tengo %s años, y dicen que soy %s y %s.",
				NombresPolitica.nombre(x), CorteCapital.cargo(c.cargo, x.mujer), pol.nombreReino(server, c.reino), x.edad(pol.anio()),
				NombresPolitica.rasgo(x, x.rasgo1), NombresPolitica.rasgo(x, x.rasgo2));
	}

	private static Component noticias(MinecraftServer server, PoliticaMundo pol, CuerpoPersona c) {
		List<Component> l = pol.noticias(server, c.reino, NOTICIAS);
		if (l.isEmpty()) {
			return Component.translatableWithFallback("destinos.corte.noticias.nada", "Últimamente todo está tranquilo, gracias a Dios.");
		}
		MutableComponent t = Component.translatableWithFallback("destinos.corte.noticias", "Esto es lo que se cuenta:");
		for (Component s : l) {
			t.append("\n").append(s);
		}
		return t;
	}

	private static Component donde(MinecraftServer server, PoliticaMundo pol, CuerpoPersona c) {
		Persona rey = pol.gente().rey(c.reino);
		RutinaCorte.Sitio s = CorteCapital.sitioDelRey(c);
		if (rey == null || s == null) {
			return Component.translatableWithFallback("destinos.corte.donde.nose", "No sabría decirte.");
		}
		return Component.translatableWithFallback("destinos.corte.donde", "A estas horas, %s está %s.", NombresPolitica.conTitulo(rey, pol.anio()),
				Component.translatableWithFallback("destinos.corte.sitio." + s.name(), SITIOS[s.ordinal()]));
	}

	/** Varias líneas en un solo texto; si no hay ninguna, el texto de "nada". */
	private static Component lista(List<Component> l, String nada, String nadaEs) {
		if (l.isEmpty()) {
			return Component.translatableWithFallback(nada, nadaEs);
		}
		MutableComponent t = Component.empty();
		for (int i = 0; i < l.size(); i++) {
			t.append(i == 0 ? Component.empty() : Component.literal("\n")).append(l.get(i));
		}
		return t;
	}

	/** El consejero avisa de la mazmorra más cercana que puedas hacer. */
	private static Component peligros(MinecraftServer server, EncargosMundo em, ServerPlayer p, CuerpoPersona c) {
		Mazmorra m = em.peligroCerca(server, p, c.reino, c.getBlockX(), c.getBlockZ());
		if (m == null) {
			return Component.translatableWithFallback("destinos.corte.peligro.nada", "Por estas tierras no se sabe de nada que dé miedo. Aún.");
		}
		return Component.translatableWithFallback("destinos.corte.peligro", "Dicen que en %s%s algo se ha despertado. Cosa de %s★: ve preparado.",
				MazmorrasMundo.nombre(server, m), NombresPolitica.donde(p, m.x(), m.z()), m.estrellas);
	}

	/** Su familia contada por él mismo: padres, hermanos, pareja, hijos y quién heredará. */
	private static Component familia(PoliticaMundo pol, Persona x) {
		es.hulillo.destinos.politica.Gente g = pol.gente();
		List<Component> l = new ArrayList<>();
		String gx = x.mujer ? "f" : "m";
		Persona padre = x.padre == Persona.NADIE ? null : g.persona(x.padre);
		Persona madre = x.madre == Persona.NADIE ? null : g.persona(x.madre);
		if (padre != null && madre != null) {
			l.add(Component.translatableWithFallback("destinos.corte.familia.padres", "Mis padres son %s y %s.", vive(padre), vive(madre)));
			int hermanos = 0;
			for (Persona h : g.hijos(padre)) {
				if (h.id != x.id && h.vivo()) {
					hermanos++;
				}
			}
			if (hermanos > 0) {
				l.add(Component.translatableWithFallback("destinos.corte.familia.hermanos", "Tengo %s hermanos con vida.", hermanos));
			}
		}
		Persona pareja = g.conyuge(x);
		if (pareja != null) {
			l.add(Component.translatableWithFallback("destinos.corte.familia.casado." + gx, x.mujer ? "Estoy casada con %s." : "Estoy casado con %s.",
					NombresPolitica.nombre(pareja)));
		}
		int hijos = (int) g.hijos(x).stream().filter(Persona::vivo).count();
		if (hijos > 0) {
			l.add(Component.translatableWithFallback("destinos.corte.familia.hijos", "Tengo %s hijos.", hijos));
		}
		Persona heredero = pol.heredero(x.reino);
		if (heredero != null && heredero.id != x.id) {
			l.add(Component.translatableWithFallback("destinos.corte.familia.heredero", "Algún día reinará %s. Ya veremos qué tal.",
					NombresPolitica.nombre(heredero)));
		} else if (heredero != null) {
			l.add(Component.translatableWithFallback("destinos.corte.familia.yo", "Y algún día la corona será mía. Pesa solo de pensarlo."));
		}
		if (l.isEmpty()) {
			return Component.translatableWithFallback("destinos.corte.familia.nada", "Mi familia es la corte. No hay mucho más que contar.");
		}
		return lista(l, "", "");
	}

	/** El nombre, con una cruz si ya murió. */
	private static Component vive(Persona x) {
		return x.vivo() ? NombresPolitica.nombre(x) : Component.translatableWithFallback("destinos.corte.familia.muerto", "%s (†)", NombresPolitica.nombre(x));
	}

	/** Dónde está alguien, para "¿Dónde está el rey?" (en el orden de RutinaCorte.Sitio). */
	private static final String[] SITIOS = {"en el salón del trono", "junto al trono", "en el patio de honor", "en la sala del consejo",
		"en el patio de armas", "comiendo en el gran comedor", "rezando en la capilla", "en sus aposentos", "en los aposentos de la reina",
		"en los aposentos de los príncipes", "en la biblioteca", "en el cuartel", "en la plaza, junto a la campana", "en la casa noble"};
}
