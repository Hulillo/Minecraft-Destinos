package es.hulillo.destinos;

import es.hulillo.destinos.politica.Gente;
import es.hulillo.destinos.politica.Grafo;
import es.hulillo.destinos.politica.Persona;
import es.hulillo.destinos.politica.Politica;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * v0.7.1 La corte: el rey y los suyos en su capital. Cada 2 s mira qué capitales tienen algún jugador cerca; en
 * esas pone los cuerpos que falten (CuerpoPersona), les dice a qué sala ir según la hora (RutinaCorte) y quita a quien
 * ya no es de la corte. Cuando no queda nadie cerca, los quita todos: fuera de la vista no hay nada que mover ni
 * guardar. Las salas (piezas del castillo, la plaza y la casa noble) se buscan una vez por capital y se guardan, igual
 * que cada sitio libre ya encontrado. Solo se usa desde el hilo del servidor.
 */
final class CorteCapital {
	private static final int CADA = 40;
	/** A esta distancia del centro de la capital sale la corte; a más de la otra, se va. */
	private static final int RADIO_PON = 96;
	private static final int RADIO_QUITA = 128;
	/** Si no se encuentran las salas (la capital aún no está generada), se vuelve a mirar al rato. */
	private static final int REINTENTO = 200;
	/** Hasta dónde se busca un hueco libre alrededor de cada punto de la rutina. */
	private static final int HUECO = 3;
	private static final int HIJOS = 3;
	private static final int REP_GOLPE = 15;
	/** Entre golpe y golpe que bajan la reputación (para que una tanda de espadazos cuente una vez). */
	private static final int ESPERA_GOLPE = 60;
	private static final Identifier CAPITAL = Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "capital");

	/** Alguien de la corte y su cargo en ella. */
	record Miembro(Persona persona, RutinaCorte.Cargo cargo) {
	}

	/** Las piezas donde vive la corte de una capital (castillo, o plaza y casa noble) y los sitios libres ya buscados. */
	static final class Salas {
		final CastilloCapital castillo;
		final PlazaMayor plaza;
		final CasaNoble casa;
		private final Map<Integer, BlockPos> libres = new HashMap<>();

		Salas(CastilloCapital castillo, PlazaMayor plaza, CasaNoble casa) {
			this.castillo = castillo;
			this.plaza = plaza;
			this.casa = casa;
		}

		/** El sitio de la rutina para la persona i-ésima que está en él, o null si cae en un chunk sin cargar. */
		BlockPos sitio(ServerLevel level, RutinaCorte.Sitio s, int i) {
			int clave = s.ordinal() * 64 + Math.min(i, 63);
			BlockPos b = libres.get(clave);
			if (b != null) {
				return b;
			}
			PiezaObra pieza = RutinaCorte.delCastillo(s) ? castillo : s == RutinaCorte.Sitio.casa ? casa : plaza;
			if (pieza == null) {
				pieza = plaza;
			}
			if (pieza == null) {
				return null;
			}
			int[] uyv = RutinaCorte.punto(s, i);
			// si son más que puntos, los que sobran un paso al lado
			int[] xz = pieza.mundo(uyv[0] + i / RutinaCorte.plazas(s), uyv[2]);
			BlockPos base = new BlockPos(xz[0], pieza.y0 + uyv[1], xz[1]);
			if (!level.isLoaded(base)) {
				return null;
			}
			b = hueco(level, base);
			libres.put(clave, b);
			return b;
		}
	}

	private static final class Corte {
		final MapaTerreno.Capital cap;
		long reino = Politica.NADIE;
		Salas salas;
		long reintento;
		/** Las personas con cuerpo puesto en esta capital. */
		final Set<Integer> personas = new HashSet<>();

		Corte(MapaTerreno.Capital cap) {
			this.cap = cap;
		}
	}

	/** Las capitales con la corte puesta, por ducado de la capital. */
	private static final Map<Long, Corte> cortes = new HashMap<>();
	/** Las salas de cada capital ya encontradas (no cambian: se guardan para la siguiente visita). */
	private static final Map<Long, Salas> salas = new HashMap<>();
	/** El cuerpo de cada persona: como mucho uno en todo el mundo. */
	private static final Map<Integer, UUID> cuerpos = new HashMap<>();
	private static final Map<UUID, Long> golpes = new HashMap<>();

	private CorteCapital() {
	}

	static void olvida() {
		cortes.clear();
		salas.clear();
		cuerpos.clear();
		golpes.clear();
	}

	static void tick(MinecraftServer server) {
		if (server.getTickCount() % CADA != 0) {
			return;
		}
		PoliticaMundo pol = DestinosCore.politica();
		if (pol == null) {
			return;
		}
		ServerLevel level = server.overworld();
		Set<Long> pon = new HashSet<>();
		Set<Long> sigue = new HashSet<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.level() != level || p.isSpectator()) {
				continue;
			}
			MapaTerreno.Capital c = pol.capitalJunto(p.getBlockX(), p.getBlockZ(), RADIO_QUITA);
			if (c == null) {
				continue;
			}
			long id = Grafo.id(c.dcx(), c.dcz());
			sigue.add(id);
			long dx = c.x() - p.getBlockX();
			long dz = c.z() - p.getBlockZ();
			if (dx * dx + dz * dz < (long) RADIO_PON * RADIO_PON) {
				pon.add(id);
				cortes.computeIfAbsent(id, k -> new Corte(c));
			}
		}
		for (Iterator<Map.Entry<Long, Corte>> it = cortes.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Long, Corte> en = it.next();
			if (!sigue.contains(en.getKey())) {
				quitaTodo(level, en.getValue());
				it.remove();
			} else {
				actualiza(level, pol, en.getKey(), en.getValue(), pon.contains(en.getKey()));
			}
		}
	}

	private static void actualiza(ServerLevel level, PoliticaMundo pol, long id, Corte c, boolean pon) {
		long reino = pol.duenoDe(id);
		// solo en la capital de su propio reino (una conquistada queda sin corte)
		if (reino == Politica.SALVAJE || pol.capitalViva(reino) != c.cap) {
			quitaTodo(level, c);
			c.reino = Politica.NADIE;
			return;
		}
		if (reino != c.reino) {
			quitaTodo(level, c);
			c.reino = reino;
		}
		if (c.salas == null) {
			c.salas = salas.get(id);
			if (c.salas == null) {
				if (level.getGameTime() < c.reintento) {
					return;
				}
				c.salas = busca(level, c.cap);
				if (c.salas == null) {
					c.reintento = level.getGameTime() + REINTENTO;
					return;
				}
				salas.put(id, c.salas);
			}
		}
		int anio = pol.anio();
		RutinaCorte.Franja franja = RutinaCorte.Franja.de(level.getDayTime());
		int[] cuantos = new int[RutinaCorte.Sitio.values().length];
		Set<Integer> quedan = new HashSet<>();
		for (Miembro m : miembros(pol.gente(), reino)) {
			int persona = m.persona().id;
			CuerpoPersona cuerpo = cuerpo(level, persona);
			if (cuerpo != null && !c.personas.contains(persona)) {
				// ya está en otra capital (un rey de dos reinos unidos): allí se queda
				continue;
			}
			RutinaCorte.Sitio s = c.salas.castillo != null ? RutinaCorte.sitio(m.cargo(), franja)
					: RutinaCorte.sinCastillo(franja, c.salas.casa != null);
			BlockPos sitio = c.salas.sitio(level, s, cuantos[s.ordinal()]++);
			if (cuerpo == null) {
				if (!pon || sitio == null || (cuerpo = crea(level, sitio)) == null) {
					continue;
				}
				cuerpos.put(persona, cuerpo.getUUID());
				c.personas.add(persona);
			}
			quedan.add(persona);
			viste(pol, cuerpo, m, reino, anio);
			if (sitio != null) {
				cuerpo.ancla(sitio, s == RutinaCorte.Sitio.patio_honor || s == RutinaCorte.Sitio.patio_armas || s == RutinaCorte.Sitio.plaza);
			}
		}
		// quien ya no es de la corte (ha muerto, hay rey nuevo, se ha ido a gobernar un ducado…) se va
		for (Iterator<Integer> it = c.personas.iterator(); it.hasNext();) {
			int persona = it.next();
			if (!quedan.contains(persona)) {
				quita(level, persona);
				it.remove();
			}
		}
	}

	/** La corte de un reino: el rey, su consorte, hasta tres hijos que vivan con ellos, el general y el consejero. */
	static List<Miembro> miembros(Gente g, long reino) {
		List<Miembro> l = new ArrayList<>();
		Persona rey = g.rey(reino);
		if (rey == null || !rey.vivo()) {
			return l;
		}
		l.add(new Miembro(rey, RutinaCorte.Cargo.rey));
		Persona consorte = g.conyuge(rey);
		if (consorte != null) {
			l.add(new Miembro(consorte, RutinaCorte.Cargo.consorte));
		}
		int hijos = 0;
		for (Persona h : g.hijos(rey)) {
			if (h.vivo() && h.rol == Persona.Rol.familia && h.conyuge == Persona.NADIE && hijos < HIJOS) {
				l.add(new Miembro(h, RutinaCorte.Cargo.hijo));
				hijos++;
			}
		}
		Persona general = g.general(reino);
		if (general != null && general.vivo()) {
			l.add(new Miembro(general, RutinaCorte.Cargo.general));
		}
		Persona consejero = g.consejero(reino);
		if (consejero != null && consejero.vivo()) {
			l.add(new Miembro(consejero, RutinaCorte.Cargo.consejero));
		}
		return l;
	}

	/** Busca las piezas de la capital en su inicio de estructura (solo si su chunk está cargado). */
	private static Salas busca(ServerLevel level, MapaTerreno.Capital cap) {
		int cx = cap.x() >> 4;
		int cz = cap.z() >> 4;
		if (!level.hasChunk(cx, cz)) {
			return null;
		}
		Structure e = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(CAPITAL);
		if (e == null) {
			return null;
		}
		StructureStart st = level.getChunk(cx, cz).getStartForStructure(e);
		if (st == null || !st.isValid()) {
			return null;
		}
		CastilloCapital castillo = null;
		PlazaMayor plaza = null;
		CasaNoble casa = null;
		long mejor = Long.MAX_VALUE;
		for (StructurePiece p : st.getPieces()) {
			if (p instanceof CastilloCapital k) {
				castillo = k;
			} else if (p instanceof PlazaMayor m && plaza == null) {
				plaza = m;
			} else if (p instanceof CasaNoble n) {
				// la casa noble más cerca del centro
				long dx = n.cx - cap.x();
				long dz = n.cz - cap.z();
				if (dx * dx + dz * dz < mejor) {
					mejor = dx * dx + dz * dz;
					casa = n;
				}
			}
		}
		return castillo == null && plaza == null ? null : new Salas(castillo, plaza, casa);
	}

	/** Un hueco para estar de pie cerca de base (dos bloques de aire sobre algo firme), o base si no hay. */
	private static BlockPos hueco(ServerLevel level, BlockPos base) {
		for (int r = 0; r <= HUECO; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy : new int[] {0, 1, -1}) {
						BlockPos b = base.offset(dx, dy, dz);
						if (dePie(level, b)) {
							return b;
						}
					}
				}
			}
		}
		return base;
	}

	private static boolean dePie(ServerLevel level, BlockPos b) {
		BlockState pies = level.getBlockState(b);
		BlockState cabeza = level.getBlockState(b.above());
		BlockState suelo = level.getBlockState(b.below());
		return !pies.blocksMotion() && !cabeza.blocksMotion() && pies.getFluidState().isEmpty() && suelo.blocksMotion();
	}

	private static CuerpoPersona cuerpo(ServerLevel level, int persona) {
		UUID u = cuerpos.get(persona);
		if (u == null) {
			return null;
		}
		Entity e = level.getEntity(u);
		if (e instanceof CuerpoPersona c && c.isAlive()) {
			return c;
		}
		// se ha descargado su chunk (no se guarda) o ha muerto
		cuerpos.remove(persona);
		return null;
	}

	private static CuerpoPersona crea(ServerLevel level, BlockPos b) {
		CuerpoPersona c = CuerpoPersona.TIPO.create(level, EntitySpawnReason.EVENT);
		if (c == null) {
			return null;
		}
		c.snapTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
		level.addFreshEntity(c);
		return c;
	}

	/** Nombre, cargo, aspecto, vida, tamaño y arma de un cuerpo; solo se toca lo que cambia. */
	private static void viste(PoliticaMundo pol, CuerpoPersona c, Miembro m, long reino, int anio) {
		Persona x = m.persona();
		c.fija(x.id, aspecto(x, m.cargo(), pol.sal(), reino, anio));
		c.reino = reino;
		c.cargo = m.cargo();
		Component nombre = Component.translatableWithFallback("destinos.corte.nombre", "%s · %s", NombresPolitica.nombre(x), cargo(m.cargo(), x.mujer));
		if (!nombre.equals(c.getCustomName())) {
			c.setCustomName(nombre);
			c.setCustomNameVisible(true);
		}
		boolean lucha = m.cargo() == RutinaCorte.Cargo.general;
		if (c.lucha != lucha || c.getMainHandItem().isEmpty() == lucha) {
			c.lucha = lucha;
			c.setItemSlot(EquipmentSlot.MAINHAND, lucha ? new ItemStack(Items.IRON_SWORD) : ItemStack.EMPTY);
			c.setDropChance(EquipmentSlot.MAINHAND, 0);
		}
		base(c, Attributes.MAX_HEALTH, m.cargo() == RutinaCorte.Cargo.rey ? 40 : lucha ? 30 : 20, true);
		base(c, Attributes.SCALE, x.edad(anio) < Gente.ADULTO ? 0.6 : 1, false);
	}

	private static void base(CuerpoPersona c, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, double v,
			boolean cura) {
		AttributeInstance i = c.getAttribute(a);
		if (i != null && i.getBaseValue() != v) {
			i.setBaseValue(v);
			if (cura) {
				c.setHealth((float) v);
			}
		}
	}

	/** El aspecto de la persona (AspectoPersona): de su cultura, su edad y su cargo, con la ropa del color del reino. */
	static int aspecto(Persona x, RutinaCorte.Cargo cargo, int sal, long reino, int anio) {
		int h = Geo.hash(x.id, 7717, sal);
		int edad = x.edad(anio);
		int ropa = AdornoCapital.tinte(Geo.colorReino(Grafo.x(reino), Grafo.z(reino), sal));
		int c = switch (cargo) {
			case rey -> AspectoPersona.REY;
			case consorte -> AspectoPersona.CONSORTE;
			case hijo -> AspectoPersona.HIJO;
			case consejero -> AspectoPersona.CONSEJERO;
			case general -> AspectoPersona.GENERAL;
		};
		return AspectoPersona.arma(AspectoPersona.piel(x.cultura, Math.floorMod(h, 3)), AspectoPersona.peloDe(x.cultura, Math.floorMod(h >> 3, 100)),
				Math.floorMod(h >> 10, 4), ropa, c, x.mujer, edad >= 55, !x.mujer && edad >= 20 && Math.floorMod(h >> 12, 3) != 0,
				edad < Gente.ADULTO, h >> 15);
	}

	/** "Rey", "Reina consorte", "Princesa"… */
	static Component cargo(RutinaCorte.Cargo cargo, boolean mujer) {
		String es = switch (cargo) {
			case rey -> mujer ? "Reina" : "Rey";
			case consorte -> mujer ? "Reina consorte" : "Rey consorte";
			case hijo -> mujer ? "Princesa" : "Príncipe";
			case consejero -> mujer ? "Consejera real" : "Consejero real";
			case general -> "General";
		};
		return Component.translatableWithFallback("destinos.corte.cargo." + cargo.name() + "." + (mujer ? "f" : "m"), es);
	}

	/** Dónde está ahora el rey del cuerpo c (para "¿Dónde está el rey?"), o null si no se sabe. */
	static RutinaCorte.Sitio sitioDelRey(CuerpoPersona c) {
		for (Corte k : cortes.values()) {
			if (k.personas.contains(c.persona()) && k.salas != null) {
				RutinaCorte.Franja f = RutinaCorte.Franja.de(c.level().getDayTime());
				return k.salas.castillo != null ? RutinaCorte.sitio(RutinaCorte.Cargo.rey, f) : RutinaCorte.sinCastillo(f, k.salas.casa != null);
			}
		}
		return null;
	}

	private static void quita(ServerLevel level, int persona) {
		UUID u = cuerpos.remove(persona);
		Entity e = u == null ? null : level.getEntity(u);
		if (e != null) {
			e.discard();
		}
	}

	private static void quitaTodo(ServerLevel level, Corte c) {
		for (int persona : c.personas) {
			quita(level, persona);
		}
		c.personas.clear();
	}

	// ------------------------------------------------------------------ golpes y muertes

	/**
	 * Un jugador ha herido (o matado) a alguien de la corte. Herirlo baja la reputación con su reino y saca a la
	 * guardia; matarlo lo mata también en la política (si era el rey, sube el heredero en el acto) y el reino ya no
	 * olvida: reputación al mínimo.
	 */
	static void golpe(MinecraftServer server, CuerpoPersona c, ServerPlayer p, boolean muerto) {
		PoliticaMundo pol = DestinosCore.politica();
		if (pol == null || c.reino == Politica.NADIE) {
			return;
		}
		long reino = c.reino;
		EncargosMundo em = EncargosMundo.de(server);
		ServerLevel level = (ServerLevel) c.level();
		Component quien = c.getCustomName() == null ? Component.empty() : c.getCustomName();
		if (muerto) {
			int persona = c.persona();
			cuerpos.remove(persona);
			for (Corte k : cortes.values()) {
				k.personas.remove(persona);
			}
			int v = em.castiga(p, reino, 200);
			pol.asesinato(server, persona, p.getScoreboardName());
			p.sendSystemMessage(Component.translatableWithFallback("destinos.corte.asesinato",
					"☠ Has matado a %s. El %s no lo olvidará · reputación: %s", quien, pol.nombreReino(server, reino), v)
					.withStyle(ChatFormatting.DARK_RED));
			guardias(level, p, pol, reino, 3);
			return;
		}
		long ahora = server.getTickCount();
		Long antes = golpes.get(p.getUUID());
		if (antes != null && ahora - antes < ESPERA_GOLPE) {
			return;
		}
		golpes.put(p.getUUID(), ahora);
		int v = em.castiga(p, reino, REP_GOLPE);
		p.displayClientMessage(Component.translatableWithFallback("destinos.corte.golpe",
				"¡Guardias! Has puesto la mano encima a %s · reputación con el %s: %s (-%s)", quien, pol.nombreReino(server, reino), v, REP_GOLPE)
				.withStyle(ChatFormatting.RED), true);
		if (!Embajadas.persiguen(p.getUUID())) {
			guardias(level, p, pol, reino, 2);
		}
	}

	/** La guardia sale de los patios del castillo (o de la plaza) a por el jugador; si no se sabe dónde, por fuera. */
	private static void guardias(ServerLevel level, ServerPlayer p, PoliticaMundo pol, long reino, int n) {
		Salas s = null;
		for (Corte k : cortes.values()) {
			if (k.reino == reino && k.salas != null) {
				s = k.salas;
			}
		}
		List<BlockPos> sitios = new ArrayList<>();
		if (s != null) {
			RutinaCorte.Sitio patio = s.castillo != null ? RutinaCorte.Sitio.patio_armas : RutinaCorte.Sitio.plaza;
			for (int i = 0; i < n; i++) {
				BlockPos b = s.sitio(level, patio, RutinaCorte.plazas(patio) - 1 - i);
				if (b != null) {
					sitios.add(b);
				}
			}
		}
		int salen = sitios.isEmpty() ? Embajadas.guardias(level, p, pol, reino, n) : Embajadas.guardias(level, p, pol, reino, sitios);
		if (salen > 0) {
			EncargosMundo.sonido(level.getServer(), p, "minecraft:entity.iron_golem.repair", 1, 0.6f);
		}
	}
}
