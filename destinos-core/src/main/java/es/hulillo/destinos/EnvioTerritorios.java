package es.hulillo.destinos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Manda a cada jugador las zonas ya calculadas a su alrededor, una sola vez por conexión.
 * Solo se usa desde el hilo del servidor.
 */
final class EnvioTerritorios {
	private final Map<UUID, Set<Long>> enviadas = new HashMap<>();
	/** Jugadores que ya tienen el estado político de ahora (guerras y turno). */
	private final Set<UUID> conPolitica = new HashSet<>();

	void envia(MapaTerreno mapa, PaqueteTerritorio.Politica politica, Supplier<PaquetePolitica> estado, ServerPlayer jugador) {
		if (!ServerPlayNetworking.canSend(jugador, PaqueteTerritorio.TIPO)) {
			return;
		}
		if (conPolitica.add(jugador.getUUID()) && ServerPlayNetworking.canSend(jugador, PaquetePolitica.TIPO)) {
			ServerPlayNetworking.send(jugador, estado.get());
		}
		Set<Long> ya = enviadas.computeIfAbsent(jugador.getUUID(), k -> new HashSet<>());
		int wx = MapaTerreno.indiceVentana(jugador.getBlockX());
		int wz = MapaTerreno.indiceVentana(jugador.getBlockZ());
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				long clave = Geo.clave(wx + ox, wz + oz);
				if (ya.contains(clave)) {
					continue;
				}
				MapaTerreno.Zona zona = mapa.zonaSiLista(wx + ox, wz + oz);
				if (zona != null) {
					ServerPlayNetworking.send(jugador, PaqueteTerritorio.de(zona, mapa.sal(), politica));
					ya.add(clave);
				}
			}
		}
	}

	void olvida(UUID jugador) {
		enviadas.remove(jugador);
		conPolitica.remove(jugador);
	}

	/** Ha pasado un turno: el estado político se vuelve a mandar a todos. */
	void olvidaPolitica() {
		conPolitica.clear();
	}

	/** Zonas que han cambiado (conquistas): se vuelven a mandar a quien ya las tenía. */
	void olvidaZonas(Set<Long> claves) {
		if (!claves.isEmpty()) {
			enviadas.values().forEach(ya -> ya.removeAll(claves));
		}
	}

	/** Otro mundo u otra sal: hay que volver a mandarlo todo. */
	void olvidaTodo() {
		enviadas.clear();
		conPolitica.clear();
	}
}
