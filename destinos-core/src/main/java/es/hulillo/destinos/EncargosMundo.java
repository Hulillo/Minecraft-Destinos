package es.hulillo.destinos;

import es.hulillo.destinos.politica.Grafo;
import es.hulillo.destinos.politica.Politica;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

/**
 * Los encargos de los tablones y la reputación de cada jugador con cada reino. Cada tablón ofrece cada día unos
 * encargos (siempre los mismos ese día en ese tablón, para todos): explorar un sitio de un ducado de la zona o acabar
 * con un campamento de bandidos. Los cumple quien los coge y pagan en monedas y reputación con el reino del tablón.
 * Se guarda en el storage destinos:encargos. Solo se usa desde el hilo del servidor.
 */
final class EncargosMundo {
	private static final Identifier STORAGE = Identifier.fromNamespaceAndPath("destinos", "encargos");
	static final TagKey<Structure> EXPLORABLES = TagKey.create(Registries.STRUCTURE,
			Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "explorable"));
	static final String BANDIDO = "destinos_bandido";
	static final int MAX_ACTIVOS = 3;
	private static final int DIA = 24000;
	/** Tablones a menos de esto de una capital: más encargos. */
	private static final int RADIO_CAPITAL = 200;
	private static final int OFERTAS = 3;
	private static final int OFERTAS_CAPITAL = 5;
	/** Reputación para que un reino te dé encargos de 4 y 5 estrellas. */
	static final int REP_DIFICILES = 30;
	/** Hasta dónde se busca una estructura que explorar alrededor del sitio elegido (en regiones de estructura). */
	private static final int RADIO_ESTRUCTURA = 10;
	private static final int MAX_HECHOS = 32;
	/** Hasta dónde se busca una mazmorra apuntada alrededor del sitio elegido para un encargo de mazmorra. */
	private static final int RADIO_MAZMORRA = 700;
	private static final String[] RUMBOS = {"e", "se", "s", "so", "o", "no", "n", "ne"};

	private static EncargosMundo actual;

	/** Lo de cada jugador: su reputación por reino, los encargos en curso y los últimos que ha cogido. */
	private static final class Jugador {
		final Map<Long, Integer> rep = new TreeMap<>();
		final List<Encargo> activos = new ArrayList<>();
		final ArrayDeque<String> hechos = new ArrayDeque<>();
	}

	/** Una oferta del tablón (aún sin coger). saltos: a cuántos ducados está del tablón. */
	record Oferta(Encargo.Tipo tipo, long ducado, int x, int z, int estrellas, int premio, int rep, int dias, int saltos, long objetivo) {
	}

	private final Map<UUID, Jugador> jugadores = new HashMap<>();
	/** Mazmorra elegida para cada oferta de hoy (tablón, día y hueco), para que no cambie al cargar más mundo. */
	private final Map<String, Long> fijadas = new HashMap<>();
	private long diaFijadas = Long.MIN_VALUE;
	/** Campanas que ya tienen su tablón (o que no lo llevan): no se vuelven a mirar. */
	final Set<Long> campanas = new HashSet<>();
	private int siguienteId = 1;
	private boolean sucio;

	private EncargosMundo() {
	}

	static EncargosMundo de(MinecraftServer server) {
		if (actual == null) {
			actual = carga(server);
		}
		return actual;
	}

	static void olvida() {
		actual = null;
		explorables = null;
	}

	// ------------------------------------------------------------------ guardado

	private static EncargosMundo carga(MinecraftServer server) {
		EncargosMundo e = new EncargosMundo();
		CompoundTag t = server.getCommandStorage().get(STORAGE);
		e.siguienteId = Math.max(1, t.getIntOr("siguiente", 1));
		for (long c : t.getLongArray("campanas").orElse(new long[0])) {
			e.campanas.add(c);
		}
		ListTag js = t.getListOrEmpty("jugadores");
		for (int i = 0; i < js.size(); i++) {
			CompoundTag c = js.getCompoundOrEmpty(i);
			UUID id;
			try {
				id = UUID.fromString(c.getStringOr("uuid", ""));
			} catch (IllegalArgumentException x) {
				continue;
			}
			Jugador j = new Jugador();
			ListTag rep = c.getListOrEmpty("rep");
			for (int k = 0; k < rep.size(); k++) {
				CompoundTag r = rep.getCompoundOrEmpty(k);
				j.rep.put(r.getLongOr("reino", 0), r.getIntOr("valor", 0));
			}
			ListTag activos = c.getListOrEmpty("activos");
			for (int k = 0; k < activos.size(); k++) {
				Encargo en = Encargo.carga(activos.getCompoundOrEmpty(k));
				if (en != null) {
					j.activos.add(en);
				}
			}
			ListTag hechos = c.getListOrEmpty("hechos");
			for (int k = 0; k < hechos.size(); k++) {
				j.hechos.add(hechos.getStringOr(k, ""));
			}
			e.jugadores.put(id, j);
		}
		return e;
	}

	void guarda(MinecraftServer server) {
		CompoundTag t = new CompoundTag();
		t.putInt("siguiente", siguienteId);
		t.putLongArray("campanas", campanas.stream().mapToLong(Long::longValue).toArray());
		ListTag js = new ListTag();
		jugadores.forEach((id, j) -> {
			CompoundTag c = new CompoundTag();
			c.putString("uuid", id.toString());
			ListTag rep = new ListTag();
			j.rep.forEach((reino, v) -> {
				CompoundTag r = new CompoundTag();
				r.putLong("reino", reino);
				r.putInt("valor", v);
				rep.add(r);
			});
			c.put("rep", rep);
			ListTag activos = new ListTag();
			for (Encargo en : j.activos) {
				activos.add(en.guarda());
			}
			c.put("activos", activos);
			ListTag hechos = new ListTag();
			for (String h : j.hechos) {
				hechos.add(StringTag.valueOf(h));
			}
			c.put("hechos", hechos);
			js.add(c);
		});
		t.put("jugadores", js);
		server.getCommandStorage().set(STORAGE, t);
		sucio = false;
	}

	/** Las fijadas solo valen para hoy: al cambiar de día se tiran. */
	private void diaFijadas(long dia) {
		if (dia != diaFijadas) {
			fijadas.clear();
			diaFijadas = dia;
		}
	}

	void ensucia() {
		sucio = true;
	}

	private Jugador jugador(ServerPlayer p) {
		return jugadores.computeIfAbsent(p.getUUID(), k -> new Jugador());
	}

	int rep(ServerPlayer p, long reino) {
		Jugador j = jugadores.get(p.getUUID());
		return j == null ? 0 : j.rep.getOrDefault(reino, 0);
	}

	private int sumaRep(ServerPlayer p, long reino, int delta) {
		Jugador j = jugador(p);
		int v = Math.max(-100, Math.min(100, j.rep.getOrDefault(reino, 0) + delta));
		j.rep.put(reino, v);
		sucio = true;
		return v;
	}

	// ------------------------------------------------------------------ tablón

	/** Lo que necesita un tablón para saber qué ofrece: dónde está y de quién es esa tierra hoy. */
	private record Sitio(MapaTerreno mapa, PoliticaMundo pol, long ducado, long reino, boolean capital) {
	}

	private static Sitio sitio(MinecraftServer server, BlockPos pos) {
		MapaTerreno mapa = DestinosCore.mapa(server);
		PoliticaMundo pol = DestinosCore.politica();
		if (mapa == null || pol == null) {
			return null;
		}
		MapaTerreno.Resultado r = mapa.consultaSiLista(pos.getX(), pos.getZ());
		if (r == null) {
			return null;
		}
		r = pol.aplica(r);
		long ducado = Grafo.id(r.dcx(), r.dcz());
		if (pol.grafo().ducado(ducado) == null) {
			return null;
		}
		long reino = r.salvaje() || r.oceano() ? Politica.NADIE : Grafo.id(r.kx(), r.kz());
		return new Sitio(mapa, pol, ducado, reino, pol.cercaDeCapital(pos.getX(), pos.getZ(), RADIO_CAPITAL));
	}

	/** El jugador usa un tablón: se le manda lo que hay clavado. */
	static void abre(ServerPlayer p, BlockPos pos) {
		MinecraftServer server = ((ServerLevel) p.level()).getServer();
		if (p.level().dimension() != Level.OVERWORLD) {
			return;
		}
		Sitio s = sitio(server, pos);
		if (s == null) {
			p.displayClientMessage(Component.translatableWithFallback("destinos.encargo.vacio",
					"El tablón está vacío: los mensajeros aún no han llegado. Vuelve en un momento.").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		de(server).mandaTablon(server, p, pos, s);
	}

	private void mandaTablon(MinecraftServer server, ServerPlayer p, BlockPos pos, Sitio s) {
		Jugador j = jugador(p);
		List<Oferta> ofertas = ofertas(server, pos, s, maxEstrellas(p, s.reino()));
		long dia = dia(server);
		List<PaqueteTablon.Linea> lineas = new ArrayList<>();
		for (int i = 0; i < ofertas.size(); i++) {
			Oferta o = ofertas.get(i);
			String origen = origen(pos, dia, i);
			boolean cogida = j.hechos.contains(origen) || j.activos.stream().anyMatch(e -> e.origen.equals(origen));
			lineas.add(new PaqueteTablon.Linea(titulo(server, s.pol(), o.tipo(), o.ducado(), o.objetivo()), texto(server, s.pol(), o.tipo(), o.ducado(), o.objetivo()),
					detalle(o.estrellas(), o.premio(), s.reino() != Politica.NADIE ? o.rep() : 0, o.dias(), p, o.x(), o.z()), cogida));
		}
		Component cabecera = Component.translatableWithFallback("destinos.encargo.tablon", "Tablón de encargos · %s",
				s.pol().nombreDucado(server, s.ducado()));
		Component sub = s.reino() == Politica.NADIE
				? Component.translatableWithFallback("destinos.encargo.libres", "Encargos de gente libre: aquí no hay señor al que servir")
				: Component.translatableWithFallback("destinos.encargo.del_reino", "Encargos del %s", s.pol().nombreReino(server, s.reino()));
		Component reputacion = s.reino() == Politica.NADIE ? Component.empty()
				: Component.translatableWithFallback("destinos.encargo.tu_rep", "Tu reputación aquí: %s/100%s", rep(p, s.reino()),
						rep(p, s.reino()) >= REP_DIFICILES ? Component.empty()
								: Component.translatableWithFallback("destinos.encargo.tu_rep_dificiles",
										" · con %s te darán encargos de 4 y 5 estrellas", REP_DIFICILES));
		ServerPlayNetworking.send(p, new PaqueteTablon(pos, cabecera, sub, reputacion, lineas, lineasActivos(server, p), MAX_ACTIVOS));
	}

	private int maxEstrellas(ServerPlayer p, long reino) {
		return reino != Politica.NADIE && rep(p, reino) >= REP_DIFICILES ? 5 : 3;
	}

	private List<PaqueteTablon.Linea> lineasActivos(MinecraftServer server, ServerPlayer p) {
		PoliticaMundo pol = DestinosCore.politica();
		List<PaqueteTablon.Linea> l = new ArrayList<>();
		Jugador j = jugadores.get(p.getUUID());
		if (j == null || pol == null) {
			return l;
		}
		for (Encargo e : j.activos) {
			l.add(new PaqueteTablon.Linea(titulo(server, pol, e.tipo, e.ducado, e.objetivo()), estado(e),
					detalle(e.estrellas, e.premio, e.deReino() ? e.rep : 0, diasQuedan(server, e), p, e.x, e.z), false));
		}
		return l;
	}

	private static long dia(MinecraftServer server) {
		return Math.floorDiv(server.overworld().getDayTime(), DIA);
	}

	private static String origen(BlockPos pos, long dia, int i) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ() + "@" + dia + "#" + i;
	}

	private static int diasQuedan(MinecraftServer server, Encargo e) {
		return (int) Math.max(0, (e.vence - server.overworld().getDayTime() + DIA - 1) / DIA);
	}

	/**
	 * Las ofertas de hoy en un tablón. Salen de la sal, el día y el sitio del tablón: todos ven lo mismo y no cambian
	 * hasta el día siguiente. Solo depende del jugador cuántas estrellas pueden tener (según su reputación).
	 */
	static List<Oferta> ofertas(MinecraftServer server, BlockPos pos, Sitio s, int maxEstrellas) {
		long dia = dia(server);
		int sal = s.pol().sal();
		SplittableRandom rnd = new SplittableRandom(((long) Geo.hash(pos.getX(), pos.getZ(), sal + pos.getY()) << 32)
				^ (dia * 0x9E3779B97F4A7C15L) ^ sal);
		List<List<Long>> anillos = anillos(s.pol().grafo(), s.ducado(), 3);
		int n = s.capital() ? OFERTAS_CAPITAL : OFERTAS;
		List<Oferta> res = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			int dado = rnd.nextInt(100);
			Encargo.Tipo tipo = dado < 30 ? Encargo.Tipo.caza : dado < 60 ? Encargo.Tipo.exploracion : Encargo.Tipo.mazmorra;
			int saltos = tipo == Encargo.Tipo.caza ? rnd.nextInt(2) : tipo == Encargo.Tipo.mazmorra ? rnd.nextInt(3) : 1 + rnd.nextInt(3);
			while (saltos > 0 && anillos.get(saltos).isEmpty()) {
				saltos--;
			}
			List<Long> lista = anillos.get(saltos);
			long ducado = lista.get(rnd.nextInt(lista.size()));
			// el mismo encargo dos veces en el tablón no: se prueba con otro ducado (o el mismo, si no hay más)
			for (int intento = 0; intento < 6 && repetido(res, tipo, ducado, saltos); intento++) {
				ducado = lista.get(rnd.nextInt(lista.size()));
			}
			int[] c;
			if (saltos == 0) {
				// en el mismo ducado: a unos cientos de bloques del tablón
				double a = rnd.nextDouble() * Math.PI * 2;
				int r = 250 + rnd.nextInt(300);
				c = enTierra(s.mapa(), ducado, pos.getX() + (int) (Math.cos(a) * r), pos.getZ() + (int) (Math.sin(a) * r));
			} else {
				c = puntoDe(s, ducado, rnd);
			}
			long mazmorra = 0;
			int fija = 0;
			if (tipo == Encargo.Tipo.mazmorra) {
				// una mazmorra ya apuntada cerca del sitio elegido; si no hay ninguna, se queda en exploración
				// la elegida se fija para ese tablón, día y hueco: si no, al cargar chunks nuevos podía salir otra más
				// cerca entre que lo ves y lo coges (cogías un bastión y te daban una mina)
				MazmorrasMundo mm = MazmorrasMundo.de(server);
				EncargosMundo em = de(server);
				String clave = origen(pos, dia, i);
				em.diaFijadas(dia);
				Long elegida = em.fijadas.get(clave);
				Mazmorra m = elegida != null ? mm.get(elegida) : mm.cercana(c[0], c[1], RADIO_MAZMORRA, maxEstrellas);
				if (elegida == null && m != null) {
					em.fijadas.put(clave, m.clave);
				}
				if (m == null || yaOfrecida(res, m.clave)) {
					tipo = Encargo.Tipo.exploracion;
				} else {
					mazmorra = m.clave;
					c = new int[] {m.x(), m.z()};
					fija = m.estrellas;
				}
			}
			double dx = c[0] - pos.getX();
			double dz = c[1] - pos.getZ();
			int distancia = (int) Math.sqrt(dx * dx + dz * dz);
			double lejosSpawn = Math.sqrt((double) c[0] * c[0] + (double) c[1] * c[1]);
			int estrellas = 1 + saltos + (lejosSpawn > 4000 ? 1 : 0) + (rnd.nextInt(4) == 0 ? 1 : 0);
			estrellas = fija > 0 ? fija : Math.max(1, Math.min(maxEstrellas, estrellas));
			// en cobre: de unas pocas monedas (1★ cerca) a varias de plata (5★ lejos)
			int premio = (tipo == Encargo.Tipo.mazmorra ? 6 + 3 * estrellas * estrellas
					: tipo == Encargo.Tipo.caza ? 4 + 5 * estrellas * estrellas / 2 : 3 + 2 * estrellas * estrellas) + distancia / 150;
			int rep = (tipo == Encargo.Tipo.mazmorra ? 3 : tipo == Encargo.Tipo.caza ? 2 : 1) + estrellas;
			int dias = 2 + distancia / 700 + (tipo == Encargo.Tipo.exploracion ? 0 : 1);
			res.add(new Oferta(tipo, ducado, c[0], c[1], estrellas, premio, rep, dias, saltos, mazmorra));
		}
		// con el reino del tablón en guerra, uno más al final (así no se mueven los otros si la guerra acaba)
		Oferta guerra = ofertaGuerra(s, pos, rnd, dia, maxEstrellas);
		if (guerra != null) {
			res.add(guerra);
		}
		return res;
	}

	/**
	 * El encargo de guerra: arrasar un campamento del enemigo en el ducado del frente (el más fácil de cruzar, el
	 * mismo por el que ataca la política). Si el reino tiene varias guerras, cada día toca un frente.
	 */
	private static Oferta ofertaGuerra(Sitio s, BlockPos pos, SplittableRandom rnd, long dia, int maxEstrellas) {
		if (s.reino() == Politica.NADIE) {
			return null;
		}
		List<Long> enemigos = s.pol().enemigos(s.reino());
		if (enemigos.isEmpty()) {
			return null;
		}
		long enemigo = enemigos.get((int) Math.floorMod(dia, enemigos.size()));
		long ducado = s.pol().frente(s.reino(), enemigo);
		if (ducado == Politica.NADIE) {
			return null;
		}
		int[] c = puntoDe(s, ducado, rnd);
		double dx = c[0] - pos.getX();
		double dz = c[1] - pos.getZ();
		int distancia = (int) Math.sqrt(dx * dx + dz * dz);
		int estrellas = Math.max(2, Math.min(maxEstrellas, 2 + (distancia > 1500 ? 1 : 0) + (rnd.nextInt(3) == 0 ? 1 : 0)));
		int premio = 8 + 3 * estrellas * estrellas + distancia / 150;
		int rep = 3 + estrellas;
		int dias = 3 + distancia / 700;
		return new Oferta(Encargo.Tipo.guerra, ducado, c[0], c[1], estrellas, premio, rep, dias, 1, enemigo);
	}

	/**
	 * Un sitio de tierra del ducado para un encargo de otro tablón: no el corazón pelado (si no, todos los tablones de
	 * alrededor mandaban al mismo sitio), sino uno al azar de ese tablón y día, entre 100 y 450 bloques del centro.
	 */
	private static int[] puntoDe(Sitio s, long ducado, SplittableRandom rnd) {
		int[] centro = Geo.centroDucado(Grafo.x(ducado), Grafo.z(ducado), s.pol().sal());
		double a = rnd.nextDouble() * Math.PI * 2;
		int r = 100 + rnd.nextInt(350);
		int x = centro[0] + (int) (Math.cos(a) * r);
		int z = centro[1] + (int) (Math.sin(a) * r);
		MapaTerreno.Resultado res = s.mapa().consultaSiLista(x, z);
		if (res != null && !res.oceano() && Grafo.id(res.dcx(), res.dcz()) == ducado) {
			return new int[] {x, z};
		}
		// fuera del ducado o en el agua: hacia el centro
		return enTierra(s.mapa(), ducado, (x + centro[0]) / 2, (z + centro[1]) / 2);
	}

	private static boolean yaOfrecida(List<Oferta> ya, long mazmorra) {
		return ya.stream().anyMatch(o -> o.tipo() == Encargo.Tipo.mazmorra && o.objetivo() == mazmorra);
	}

	/** Si ya hay en el tablón un encargo igual (lejos del tablón, mismo tipo y ducado). */
	private static boolean repetido(List<Oferta> ya, Encargo.Tipo tipo, long ducado, int saltos) {
		return saltos > 0 && ya.stream().anyMatch(o -> o.tipo() == tipo && o.ducado() == ducado);
	}

	/** Ducados a 0, 1, 2… saltos por tierra del de partida (los que se conocen). */
	private static List<List<Long>> anillos(Grafo g, long desde, int max) {
		List<List<Long>> res = new ArrayList<>();
		Set<Long> vistos = new HashSet<>();
		List<Long> anillo = new ArrayList<>(List.of(desde));
		vistos.add(desde);
		res.add(anillo);
		for (int i = 1; i <= max; i++) {
			Set<Long> sig = new LinkedHashSet<>();
			for (long d : anillo) {
				for (long v : g.vecinos(d).keySet()) {
					if (vistos.add(v)) {
						sig.add(v);
					}
				}
			}
			anillo = new ArrayList<>(sig);
			res.add(anillo);
		}
		return res;
	}

	/**
	 * Un punto de tierra del ducado cerca de (x, z): se prueba en espiral alrededor. Si la zona aún no está calculada
	 * se queda (x, z) tal cual.
	 */
	private static int[] enTierra(MapaTerreno mapa, long ducado, int x, int z) {
		for (int i = 0; i < 16; i++) {
			double a = i * 2.4;
			int r = i * 90;
			int px = x + (int) (Math.cos(a) * r);
			int pz = z + (int) (Math.sin(a) * r);
			MapaTerreno.Resultado res = mapa.consultaSiLista(px, pz);
			if (res == null) {
				return new int[] {x, z};
			}
			if (!res.oceano() && Grafo.id(res.dcx(), res.dcz()) == ducado) {
				return new int[] {px, pz};
			}
		}
		return new int[] {x, z};
	}

	// ------------------------------------------------------------------ coger y dejar

	/** Lo que pide el cliente desde la pantalla del tablón. */
	static void accion(ServerPlayer p, PaqueteAccionEncargo a) {
		MinecraftServer server = ((ServerLevel) p.level()).getServer();
		EncargosMundo em = de(server);
		if (a.coger()) {
			em.coge(server, p, a.pos(), a.indice());
		} else {
			em.deja(server, p, a.indice());
		}
		Sitio s = p.level().dimension() == Level.OVERWORLD ? sitio(server, a.pos()) : null;
		if (s != null && p.level().getBlockState(a.pos()).is(TablonBlock.TABLON)) {
			em.mandaTablon(server, p, a.pos(), s);
		}
	}

	private void coge(MinecraftServer server, ServerPlayer p, BlockPos pos, int indice) {
		if (!p.level().getBlockState(pos).is(TablonBlock.TABLON) || p.distanceToSqr(Vec3.atCenterOf(pos)) > 64) {
			return;
		}
		Sitio s = sitio(server, pos);
		if (s == null) {
			return;
		}
		Jugador j = jugador(p);
		if (j.activos.size() >= MAX_ACTIVOS) {
			p.displayClientMessage(Component.translatableWithFallback("destinos.encargo.lleno",
					"Ya llevas %s encargos: termina o deja alguno antes de coger otro.", MAX_ACTIVOS).withStyle(ChatFormatting.RED), false);
			return;
		}
		List<Oferta> ofertas = ofertas(server, pos, s, maxEstrellas(p, s.reino()));
		if (indice < 0 || indice >= ofertas.size()) {
			return;
		}
		String origen = origen(pos, dia(server), indice);
		if (j.hechos.contains(origen) || j.activos.stream().anyMatch(e -> e.origen.equals(origen))) {
			return;
		}
		Oferta o = ofertas.get(indice);
		Encargo e = new Encargo(siguienteId++, o.tipo(), s.reino(), o.ducado(), o.x(), o.z(), o.estrellas(), o.premio(), o.rep(),
				server.overworld().getDayTime() + (long) o.dias() * DIA, origen);
		if (e.tipo == Encargo.Tipo.guerra) {
			e.enemigo = o.objetivo();
		}
		if (e.tipo == Encargo.Tipo.mazmorra) {
			e.mazmorra = o.objetivo();
			MazmorrasMundo.de(server).despierta(server, e.mazmorra, e.vence);
		}
		j.activos.add(e);
		j.hechos.addLast(origen);
		while (j.hechos.size() > MAX_HECHOS) {
			j.hechos.removeFirst();
		}
		sucio = true;
		p.sendSystemMessage(Component.translatableWithFallback("destinos.encargo.cogido", "📜 Encargo aceptado: %s",
				titulo(server, s.pol(), e.tipo, e.ducado, e.objetivo())).withStyle(ChatFormatting.GOLD));
		p.sendSystemMessage(Component.empty().append(texto(server, s.pol(), e.tipo, e.ducado, e.objetivo())).withStyle(ChatFormatting.GRAY));
		sonido(server, p, "minecraft:item.book.page_turn", 1, 1);
		envia(server, p);
	}

	/**
	 * Para explorar, mejor una estructura de verdad cerca del sitio elegido que el corazón pelado del ducado. Se busca
	 * cuando el jugador ya se acerca y solo entre los chunks que ya están cargados (sus inicios de estructura ya están
	 * calculados): buscar al coger el encargo con findNearestMapStructure generaba chunks lejanos y daba tirones.
	 */
	private static boolean buscaEstructura(ServerLevel level, Encargo e) {
		Set<Structure> explorables = explorables(level);
		int cx = e.x >> 4;
		int cz = e.z >> 4;
		BlockPos mejor = null;
		long mejorD = Long.MAX_VALUE;
		for (int dx = -RADIO_ESTRUCTURA; dx <= RADIO_ESTRUCTURA; dx++) {
			for (int dz = -RADIO_ESTRUCTURA; dz <= RADIO_ESTRUCTURA; dz++) {
				LevelChunk c = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
				if (c == null) {
					continue;
				}
				for (Map.Entry<Structure, StructureStart> en : c.getAllStarts().entrySet()) {
					if (!explorables.contains(en.getKey()) || !en.getValue().isValid()) {
						continue;
					}
					BlockPos centro = en.getValue().getBoundingBox().getCenter();
					long d = (long) (centro.getX() - e.x) * (centro.getX() - e.x) + (long) (centro.getZ() - e.z) * (centro.getZ() - e.z);
					if (d < mejorD) {
						mejorD = d;
						mejor = centro;
					}
				}
			}
		}
		if (mejor == null) {
			return false;
		}
		e.x = mejor.getX();
		e.z = mejor.getZ();
		e.estructura = true;
		return true;
	}

	private static Set<Structure> explorables;

	private static Set<Structure> explorables(ServerLevel level) {
		if (explorables == null) {
			Set<Structure> s = new HashSet<>();
			for (Holder<Structure> h : level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getTagOrEmpty(EXPLORABLES)) {
				s.add(h.value());
			}
			explorables = s;
		}
		return explorables;
	}

	private void deja(MinecraftServer server, ServerPlayer p, int indice) {
		Jugador j = jugadores.get(p.getUUID());
		if (j == null || indice < 0 || indice >= j.activos.size()) {
			return;
		}
		Encargo e = j.activos.remove(indice);
		if (e.deReino()) {
			sumaRep(p, e.reino, -1);
		}
		sucio = true;
		PoliticaMundo pol = DestinosCore.politica();
		if (pol != null) {
			p.sendSystemMessage(Component.translatableWithFallback("destinos.encargo.dejado", "Dejas el encargo: %s",
					titulo(server, pol, e.tipo, e.ducado, e.objetivo())).withStyle(ChatFormatting.GRAY));
		}
		envia(server, p);
	}

	// ------------------------------------------------------------------ en marcha

	/** Cada segundo: plazos, llegadas, campamentos que montar y encargos que se cumplen. */
	void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		PoliticaMundo pol = DestinosCore.politica();
		if (pol == null) {
			return;
		}
		ServerLevel level = server.overworld();
		long ahora = level.getDayTime();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Jugador j = jugadores.get(p.getUUID());
			if (j == null || j.activos.isEmpty()) {
				continue;
			}
			boolean cambia = false;
			for (Encargo e : new ArrayList<>(j.activos)) {
				if (ahora > e.vence) {
					falla(server, pol, p, j, e);
					cambia = true;
					continue;
				}
				if (p.level() != level || e.tipo == Encargo.Tipo.mazmorra) {
					// las mazmorras se cumplen solas al matar a su jefe (MazmorrasMundo)
					continue;
				}
				double dx = e.x + 0.5 - p.getX();
				double dz = e.z + 0.5 - p.getZ();
				double d2 = dx * dx + dz * dz;
				if (e.tipo == Encargo.Tipo.exploracion) {
					if (!e.estructura && d2 < 300 * 300 && server.getTickCount() % 100 == 0 && buscaEstructura(level, e)) {
						p.displayClientMessage(Component.translatableWithFallback("destinos.encargo.avistada",
								"A lo lejos se ve algo: el encargo te lleva hasta allí.").withStyle(ChatFormatting.YELLOW), true);
						cambia = true;
						sucio = true;
						continue;
					}
					int r = e.estructura ? 32 : 48;
					if (d2 < r * r) {
						cumple(server, pol, p, j, e);
						cambia = true;
					}
				} else if (!e.campamento) {
					if (d2 < 96 * 96 && level.hasChunksAt(new BlockPos(e.x - 48, 64, e.z - 48), new BlockPos(e.x + 48, 64, e.z + 48))) {
						monta(server, pol, level, p, e);
						cambia = true;
					}
				} else if (e.bandidos.isEmpty() || (d2 < 48 * 48 && CampamentoBandidos.desaparecidos(level, e))) {
					cumple(server, pol, p, j, e);
					cambia = true;
				}
			}
			if (cambia) {
				envia(server, p);
			}
		}
		if (sucio && server.getTickCount() % 600 == 0) {
			guarda(server);
		}
	}

	private void monta(MinecraftServer server, PoliticaMundo pol, ServerLevel level, ServerPlayer p, Encargo e) {
		boolean guerra = e.tipo == Encargo.Tipo.guerra;
		CampamentoBandidos.Montado m = guerra
				? CampamentoBandidos.montaGuerra(level, e.x, e.z, e.estrellas, Geo.colorReino(Grafo.x(e.enemigo), Grafo.z(e.enemigo), pol.sal()),
						pol.nombreReino(server, e.enemigo))
				: CampamentoBandidos.monta(level, e.x, e.z, e.estrellas, e.id);
		e.x = m.x();
		e.z = m.z();
		e.bandidos.clear();
		e.bandidos.addAll(m.bandidos());
		e.campamento = true;
		sucio = true;
		p.displayClientMessage((guerra ? Component.translatableWithFallback("destinos.encargo.tambores",
				"Se oyen tambores de guerra: el campamento enemigo está cerca.")
				: Component.translatableWithFallback("destinos.encargo.humo",
						"Huele a humo de hoguera: el campamento de los bandidos está cerca.")).withStyle(ChatFormatting.YELLOW), true);
		if (guerra) {
			sonido(server, p, "minecraft:event.raid.horn", 0.7f, 1);
		}
		DestinosCore.LOG.info("Destinos Core: campamento {} en {}, {} ({} enemigos)", guerra ? "de guerra" : "de bandidos", m.x(), m.z(),
				m.bandidos().size());
	}

	private void cumple(MinecraftServer server, PoliticaMundo pol, ServerPlayer p, Jugador j, Encargo e) {
		j.activos.remove(e);
		sucio = true;
		Monedas.paga(p, e.premio);
		Component titulo = titulo(server, pol, e.tipo, e.ducado, e.objetivo());
		MutableComponent sub = Component.translatableWithFallback("destinos.encargo.paga", "+%s", Monedas.texto(e.premio));
		if (e.deReino()) {
			int v = sumaRep(p, e.reino, e.rep);
			sub = Component.translatableWithFallback("destinos.encargo.paga_rep", "+%s · reputación con el %s: %s (+%s)",
					Monedas.texto(e.premio), pol.nombreReino(server, e.reino), v, e.rep);
		}
		p.connection.send(new ClientboundSetTitleTextPacket(Component.translatableWithFallback("destinos.encargo.cumplido",
				"¡Encargo cumplido!").withStyle(ChatFormatting.GOLD)));
		p.connection.send(new ClientboundSetSubtitleTextPacket(titulo));
		p.sendSystemMessage(Component.translatableWithFallback("destinos.encargo.cumplido_chat", "✔ %s · %s", titulo, sub)
				.withStyle(ChatFormatting.GREEN));
		sonido(server, p, "minecraft:ui.toast.challenge_complete", 0.6f, 1);
		if (e.tipo == Encargo.Tipo.guerra) {
			cumpleGuerra(server, pol, p, e);
		}
	}

	/** Tras arrasar un campamento del enemigo: te odian un poco más allí y el frente se mueve (Politica.empuja). */
	private void cumpleGuerra(MinecraftServer server, PoliticaMundo pol, ServerPlayer p, Encargo e) {
		if (e.enemigo != Politica.NADIE) {
			int baja = 1 + e.estrellas / 2;
			int v = sumaRep(p, e.enemigo, -baja);
			p.sendSystemMessage(Component.translatableWithFallback("destinos.encargo.rep_enemigo", "El %s no lo olvidará · reputación: %s (-%s)",
					pol.nombreReino(server, e.enemigo), v, baja).withStyle(ChatFormatting.RED));
		}
		if (!e.deReino() || pol.duenoDe(e.ducado) != e.enemigo) {
			p.sendSystemMessage(Component.translatableWithFallback("destinos.encargo.sin_guerra",
					"La guerra ya ha acabado: el campamento no cambia nada en el frente.").withStyle(ChatFormatting.GRAY));
			return;
		}
		int n = pol.empuja(server, e.ducado, e.reino, p.getScoreboardName());
		if (n <= 0) {
			p.sendSystemMessage(Component.translatableWithFallback("destinos.encargo.sin_guerra",
					"La guerra ya ha acabado: el campamento no cambia nada en el frente.").withStyle(ChatFormatting.GRAY));
		} else if (n >= Politica.CAMPAMENTOS_CONQUISTA) {
			p.sendSystemMessage(Component.translatableWithFallback("destinos.encargo.frente_roto",
					"⚔ ¡El frente se rompe! En el próximo turno el %s tomará %s.", pol.nombreReino(server, e.reino),
					pol.nombreDucado(server, e.ducado)).withStyle(ChatFormatting.GOLD));
			sonido(server, p, "minecraft:item.goat_horn.sound.0", 0.8f, 1);
		} else {
			p.sendSystemMessage(Component.translatableWithFallback("destinos.encargo.frente", "⚔ Campamentos arrasados en %s: %s de %s",
					pol.nombreDucado(server, e.ducado), n, Politica.CAMPAMENTOS_CONQUISTA).withStyle(ChatFormatting.YELLOW));
		}
	}

	/**
	 * Una mazmorra ha quedado limpia: se cumplen los encargos que la pedían (de quien esté conectado) y, si se había
	 * despertado sola de noche, los que estaban en la pelea sin encargo ganan reputación con el dueño de esas tierras.
	 */
	void mazmorraLimpia(MinecraftServer server, Mazmorra m, List<ServerPlayer> cerca, boolean nocturna) {
		PoliticaMundo pol = DestinosCore.politica();
		if (pol == null) {
			return;
		}
		Set<UUID> conEncargo = new HashSet<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Jugador j = jugadores.get(p.getUUID());
			if (j == null) {
				continue;
			}
			for (Encargo e : new ArrayList<>(j.activos)) {
				if (e.tipo == Encargo.Tipo.mazmorra && e.mazmorra == m.clave) {
					cumple(server, pol, p, j, e);
					conEncargo.add(p.getUUID());
				}
			}
			if (conEncargo.contains(p.getUUID())) {
				envia(server, p);
			}
		}
		if (!nocturna) {
			return;
		}
		long[] d = MazmorrasMundo.dueno(server, m.x(), m.z());
		if (d == null || d[1] == Politica.NADIE) {
			return;
		}
		int delta = 2 + m.estrellas / 2;
		for (ServerPlayer p : cerca) {
			if (conEncargo.contains(p.getUUID())) {
				continue;
			}
			int v = sumaRep(p, d[1], delta);
			sucio = true;
			p.sendSystemMessage(Component.translatableWithFallback("destinos.mazmorra.rep", "El %s te lo agradece · reputación: %s (+%s)",
					pol.nombreReino(server, d[1]), v, delta).withStyle(ChatFormatting.DARK_AQUA));
		}
	}

	private void falla(MinecraftServer server, PoliticaMundo pol, ServerPlayer p, Jugador j, Encargo e) {
		j.activos.remove(e);
		sucio = true;
		MutableComponent t = Component.translatableWithFallback("destinos.encargo.vencido", "✘ Se acabó el plazo: %s",
				titulo(server, pol, e.tipo, e.ducado, e.objetivo()));
		if (e.deReino()) {
			int v = sumaRep(p, e.reino, -2);
			t.append(Component.translatableWithFallback("destinos.encargo.rep_baja", " · reputación con el %s: %s (-%s)",
					pol.nombreReino(server, e.reino), v, 2));
		}
		p.sendSystemMessage(t.withStyle(ChatFormatting.RED));
	}

	/** Al morir algo: los bandidos de los campamentos cuentan, y matar aldeanos o gólems baja la reputación. */
	void alMorir(MinecraftServer server, LivingEntity muerto, DamageSource fuente) {
		if (muerto.getTags().contains(MazmorrasMundo.TAG)) {
			MazmorrasMundo.de(server).alMorir(muerto);
			return;
		}
		if (muerto.getTags().contains(BANDIDO)) {
			UUID id = muerto.getUUID();
			for (Map.Entry<UUID, Jugador> en : jugadores.entrySet()) {
				for (Encargo e : en.getValue().activos) {
					if (e.bandidos.remove(id)) {
						sucio = true;
						ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
						if (p != null && !e.bandidos.isEmpty()) {
							p.displayClientMessage((e.tipo == Encargo.Tipo.guerra
									? Component.translatableWithFallback("destinos.encargo.quedan_soldados", "Quedan %s soldados", e.bandidos.size())
									: Component.translatableWithFallback("destinos.encargo.quedan", "Quedan %s bandidos", e.bandidos.size()))
									.withStyle(ChatFormatting.YELLOW), true);
						}
					}
				}
			}
			return;
		}
		boolean aldeano = muerto.getType() == EntityType.VILLAGER;
		boolean golem = muerto.getType() == EntityType.IRON_GOLEM;
		if ((!aldeano && !golem) || !(fuente.getEntity() instanceof ServerPlayer p) || muerto.level().dimension() != Level.OVERWORLD) {
			return;
		}
		MapaTerreno mapa = DestinosCore.mapaActual();
		PoliticaMundo pol = DestinosCore.politica();
		if (mapa == null || pol == null) {
			return;
		}
		MapaTerreno.Resultado r = mapa.consultaSiLista(muerto.getBlockX(), muerto.getBlockZ());
		if (r == null) {
			return;
		}
		r = pol.aplica(r);
		if (r.salvaje() || r.oceano()) {
			return;
		}
		long reino = Grafo.id(r.kx(), r.kz());
		int delta = aldeano ? 5 : 3;
		int v = sumaRep(p, reino, -delta);
		p.displayClientMessage(Component.translatableWithFallback("destinos.encargo.crimen", "Reputación con el %s: %s (-%s)",
				pol.nombreReino(server, reino), v, delta).withStyle(ChatFormatting.RED), true);
	}

	// ------------------------------------------------------------------ textos

	static Component titulo(MinecraftServer server, PoliticaMundo pol, Encargo.Tipo tipo, long ducado, long objetivo) {
		if (tipo == Encargo.Tipo.mazmorra) {
			Mazmorra m = MazmorrasMundo.de(server).get(objetivo);
			Component n = m != null ? MazmorrasMundo.nombre(server, m) : pol.nombreDucado(server, ducado);
			return Component.translatableWithFallback("destinos.encargo.mazmorra", "Limpia %s", n);
		}
		Component d = pol.nombreDucado(server, ducado);
		if (tipo == Encargo.Tipo.guerra) {
			return Component.translatableWithFallback("destinos.encargo.guerra", "⚔ Arrasa el campamento del %s en %s",
					pol.nombreReino(server, objetivo), d);
		}
		return tipo == Encargo.Tipo.caza
				? Component.translatableWithFallback("destinos.encargo.caza", "Bandidos en %s", d)
				: Component.translatableWithFallback("destinos.encargo.exploracion", "Explora %s", d);
	}

	private static Component texto(MinecraftServer server, PoliticaMundo pol, Encargo.Tipo tipo, long ducado, long objetivo) {
		if (tipo == Encargo.Tipo.mazmorra) {
			Mazmorra m = MazmorrasMundo.de(server).get(objetivo);
			Component n = m != null ? MazmorrasMundo.nombre(server, m) : pol.nombreDucado(server, ducado);
			return Component.translatableWithFallback("destinos.encargo.mazmorra.texto",
					"Algo se ha despertado en %s. Entra, aguanta las oleadas y acaba con lo que las manda.", n);
		}
		Component d = pol.nombreDucado(server, ducado);
		if (tipo == Encargo.Tipo.guerra) {
			return Component.translatableWithFallback("destinos.encargo.guerra.texto",
					"Las tropas del %s han acampado en %s, en el frente. Arrasa el campamento: con %s campamentos arrasados en ese ducado, nuestro ejército lo tomará.",
					pol.nombreReino(server, objetivo), d, Politica.CAMPAMENTOS_CONQUISTA);
		}
		return tipo == Encargo.Tipo.caza
				? Component.translatableWithFallback("destinos.encargo.caza.texto",
						"Una banda asalta los caminos de %s. Encuentra su campamento y acaba con todos.", d)
				: Component.translatableWithFallback("destinos.encargo.exploracion.texto",
						"Nadie ha vuelto con noticias de %s. Viaja hasta allí y descubre qué esconde.", d);
	}

	/** Cómo va un encargo en curso. */
	private static Component estado(Encargo e) {
		if (e.tipo == Encargo.Tipo.mazmorra) {
			MazmorrasMundo mm = MazmorrasMundo.siHay();
			Mazmorra m = mm == null ? null : mm.get(e.mazmorra);
			if (m != null && m.oleada == MazmorrasMundo.JEFE) {
				return Component.translatableWithFallback("destinos.encargo.estado.jefe", "Solo queda el jefe.");
			}
			return m != null && m.oleada > 0
					? Component.translatableWithFallback("destinos.encargo.estado.oleada", "Oleada %s de %s.", m.oleada, MazmorrasMundo.OLEADAS)
					: Component.translatableWithFallback("destinos.encargo.estado.entra", "Entra en la mazmorra y aguanta las oleadas.");
		}
		if (e.tipo == Encargo.Tipo.guerra) {
			return e.campamento
					? Component.translatableWithFallback("destinos.encargo.estado.soldados", "Quedan %s soldados en el campamento.", e.bandidos.size())
					: Component.translatableWithFallback("destinos.encargo.estado.buscar_guerra", "Ve a la zona marcada y busca el campamento enemigo.");
		}
		if (e.tipo == Encargo.Tipo.caza) {
			return e.campamento
					? Component.translatableWithFallback("destinos.encargo.estado.quedan", "Quedan %s bandidos en el campamento.", e.bandidos.size())
					: Component.translatableWithFallback("destinos.encargo.estado.buscar", "Ve a la zona marcada y busca su campamento.");
		}
		return e.estructura
				? Component.translatableWithFallback("destinos.encargo.estado.estructura", "Dicen que allí hay unas ruinas. Llega hasta ellas.")
				: Component.translatableWithFallback("destinos.encargo.estado.corazon", "Llega al corazón del ducado.");
	}

	/** "★★★☆☆ · 2 plata 5 cobre · +5 reputación · 6 días · a 1200 bloques al NE". */
	private static Component detalle(int estrellas, int premio, int rep, int dias, ServerPlayer p, int x, int z) {
		MutableComponent t = Component.literal("★".repeat(estrellas) + "☆".repeat(Math.max(0, 5 - estrellas))).withStyle(ChatFormatting.GOLD);
		t.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
		t.append(Monedas.texto(premio));
		if (rep > 0) {
			t.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
			t.append(Component.translatableWithFallback("destinos.encargo.rep", "+%s reputación", rep).withStyle(ChatFormatting.DARK_AQUA));
		}
		t.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
		t.append(Component.translatableWithFallback(dias == 1 ? "destinos.encargo.dia" : "destinos.encargo.dias",
				dias == 1 ? "%s día" : "%s días", dias));
		t.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
		t.append(distancia(x - p.getX(), z - p.getZ()));
		return t;
	}

	/** "a 1200 bloques al NE". */
	static Component distancia(double dx, double dz) {
		int d = (int) Math.round(Math.sqrt(dx * dx + dz * dz) / 50) * 50;
		if (d < 100) {
			return Component.translatableWithFallback("destinos.encargo.aqui", "aquí mismo");
		}
		// el norte es -z en Minecraft; atan2 con z hacia abajo da el rumbo en sentido horario desde el este
		int rumbo = Math.floorMod((int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4)), 8);
		return Component.translatableWithFallback("destinos.encargo.lejos", "a %s bloques al %s", d,
				Component.translatableWithFallback("destinos.rumbo." + RUMBOS[rumbo], RUMBOS[rumbo].toUpperCase()));
	}

	static void sonido(MinecraftServer server, ServerPlayer p, String sonido, float volumen, float tono) {
		CommandSourceStack f = server.createCommandSourceStack().withLevel((ServerLevel) p.level()).withPosition(p.position())
				.withSuppressedOutput();
		server.getCommands().performPrefixedCommand(f, "playsound " + sonido + " master " + p.getScoreboardName() + " ~ ~ ~ "
				+ volumen + " " + tono);
	}

	// ------------------------------------------------------------------ al cliente y comandos

	/** Los encargos en curso a la línea de la pantalla del jugador. */
	void envia(MinecraftServer server, ServerPlayer p) {
		PoliticaMundo pol = DestinosCore.politica();
		List<PaqueteEncargos.Activo> l = new ArrayList<>();
		Jugador j = jugadores.get(p.getUUID());
		if (j != null && pol != null) {
			for (Encargo e : j.activos) {
				l.add(new PaqueteEncargos.Activo(titulo(server, pol, e.tipo, e.ducado, e.objetivo()), e.x, e.z, e.vence,
						e.tipo == Encargo.Tipo.mazmorra ? PaqueteEncargos.CALAVERA : PaqueteEncargos.ROMBO));
			}
		}
		// las mazmorras despertadas solas de noche cerca, aunque no lleves su encargo
		if (pol != null && p.level() == server.overworld()) {
			for (Mazmorra m : MazmorrasMundo.de(server).nocturnas(p, 1500, 2)) {
				if (j != null && j.activos.stream().anyMatch(e -> e.tipo == Encargo.Tipo.mazmorra && e.mazmorra == m.clave)) {
					continue;
				}
				l.add(new PaqueteEncargos.Activo(MazmorrasMundo.nombre(server, m), m.x(), m.z(), m.hasta, PaqueteEncargos.CALAVERA_NOCHE));
			}
		}
		if (ServerPlayNetworking.canSend(p, PaqueteEncargos.TIPO)) {
			ServerPlayNetworking.send(p, new PaqueteEncargos(l));
		}
	}

	/** /destinos encargos */
	void lista(CommandSourceStack fuente, ServerPlayer p) {
		MinecraftServer server = fuente.getServer();
		PoliticaMundo pol = DestinosCore.politica();
		Jugador j = jugadores.get(p.getUUID());
		if (pol == null || j == null || j.activos.isEmpty()) {
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.encargo.ninguno",
					"No llevas ningún encargo. Búscalos en el tablón junto a la campana de los pueblos.").withStyle(ChatFormatting.GRAY), false);
			return;
		}
		fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.encargo.en_curso", "📜 Encargos en curso")
				.withStyle(ChatFormatting.GOLD), false);
		for (Encargo e : j.activos) {
			Component linea = Component.empty().append(titulo(server, pol, e.tipo, e.ducado, e.objetivo())).append(" · ").append(estado(e))
					.append(" · ").append(detalle(e.estrellas, e.premio, e.deReino() ? e.rep : 0, diasQuedan(server, e), p, e.x, e.z));
			fuente.sendSuccess(() -> linea, false);
		}
	}

	/** /destinos reputacion */
	void reputacion(CommandSourceStack fuente, ServerPlayer p) {
		MinecraftServer server = fuente.getServer();
		PoliticaMundo pol = DestinosCore.politica();
		Jugador j = jugadores.get(p.getUUID());
		if (pol == null || j == null || j.rep.isEmpty()) {
			fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.encargo.sin_rep",
					"Ningún reino sabe aún quién eres. Haz encargos para que se hable de ti.").withStyle(ChatFormatting.GRAY), false);
			return;
		}
		fuente.sendSuccess(() -> Component.translatableWithFallback("destinos.encargo.reputacion", "⚖ Tu reputación (de -100 a 100)")
				.withStyle(ChatFormatting.GOLD), false);
		j.rep.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).forEach(en -> {
			int v = en.getValue();
			Component linea = Component.translatableWithFallback("destinos.encargo.reputacion_linea", "%s: %s",
					pol.nombreReino(server, en.getKey()), Component.literal(String.valueOf(v))
							.withStyle(v >= REP_DIFICILES ? ChatFormatting.GREEN : v < 0 ? ChatFormatting.RED : ChatFormatting.WHITE));
			fuente.sendSuccess(() -> linea, false);
		});
	}

	/** /destinoscore encargos x z: las ofertas que tendría hoy un tablón en (x, z), para probar. */
	static void prueba(CommandSourceStack fuente, int x, int z) {
		MinecraftServer server = fuente.getServer();
		BlockPos pos = new BlockPos(x, 64, z);
		Sitio s = sitio(server, pos);
		if (s == null) {
			fuente.sendFailure(Component.literal("Destinos Core: la zona del tablón aún no está calculada"));
			return;
		}
		List<Oferta> ofertas = ofertas(server, pos, s, 5);
		StringBuilder b = new StringBuilder("Destinos Core: tablón en " + x + ", " + z + (s.capital() ? " (capital)" : "") + ": "
				+ ofertas.size() + " encargos");
		for (Oferta o : ofertas) {
			b.append(" · ").append(o.tipo()).append(" a ").append(o.saltos()).append(" ducados (").append(o.x()).append(", ")
					.append(o.z()).append(") ").append(o.estrellas()).append("★ ").append(o.premio()).append(" cobre ")
					.append(o.dias()).append(" días");
		}
		String texto = b.toString();
		DestinosCore.LOG.info(texto);
		fuente.sendSuccess(() -> Component.literal(texto), false);
	}

	/**
	 * /destinoscore encargos guerra: monta un campamento de guerra de prueba donde se lanza el comando (rojo), mira
	 * que salgan sus soldados de cuero teñido y su capitán con más vida, lo quita y cuenta los frentes de las guerras.
	 */
	static void pruebaGuerra(CommandSourceStack fuente) {
		ServerLevel level = fuente.getServer().overworld();
		BlockPos pos = BlockPos.containing(fuente.getPosition());
		String t;
		if (!level.hasChunksAt(pos.offset(-16, 0, -16), pos.offset(16, 0, 16))) {
			t = "Destinos Core: campamento de guerra de prueba · la zona no está cargada";
		} else {
			CampamentoBandidos.Montado m = CampamentoBandidos.montaGuerra(level, pos.getX(), pos.getZ(), 3, 0xB02E26,
					Component.literal("Reino de Prueba"));
			int soldados = 0;
			int tenidos = 0;
			boolean capitan = false;
			for (UUID u : m.bandidos()) {
				if (!(level.getEntity(u) instanceof net.minecraft.world.entity.Mob b)) {
					continue;
				}
				soldados++;
				if (b.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS)
						.has(net.minecraft.core.component.DataComponents.DYED_COLOR)) {
					tenidos++;
				}
				capitan |= b.getMaxHealth() > 30;
				b.discard();
			}
			t = "Destinos Core: campamento de guerra de prueba · " + soldados + " soldados · " + tenidos + " de cuero teñido · capitán "
					+ (capitan ? "bien" : "MAL");
		}
		PoliticaMundo pol = DestinosCore.politica();
		int[] f = pol == null ? new int[] {0, 0} : pol.frentes();
		String texto = t + " · " + f[0] + " guerras, " + f[1] + " con frente";
		DestinosCore.LOG.info(texto);
		fuente.sendSuccess(() -> Component.literal(texto), false);
	}
}
