package es.hulillo.destinos.cliente;

import es.hulillo.destinos.DestinosCore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Las marcas que pone el jugador en el mapa (clic derecho): un sitio con nombre. Se guardan en el cliente, una lista
 * por mundo (o servidor), en config/destinos_marcas/. Una de ellas puede ser el destino, y entonces el mapa dibuja la
 * línea de viaje hasta ella.
 */
final class MarcasMapa {
	record Marca(int x, int z, String nombre) {
	}

	private static final List<Marca> MARCAS = new ArrayList<>();
	private static String mundo;
	private static Marca destino;

	private MarcasMapa() {
	}

	/** Las marcas del mundo en el que se está (las lee la primera vez). */
	static List<Marca> todas() {
		String m = mundoActual();
		if (!m.equals(mundo)) {
			mundo = m;
			destino = null;
			lee();
		}
		return MARCAS;
	}

	static void pon(int x, int z, String nombre) {
		todas().add(new Marca(x, z, nombre.isBlank() ? "?" : nombre.strip()));
		guarda();
	}

	static void quita(Marca m) {
		todas().remove(m);
		if (m == destino) {
			destino = null;
		}
		guarda();
	}

	static Marca destino() {
		todas();
		return destino;
	}

	/** Pone o quita la marca como destino. */
	static void alterna(Marca m) {
		destino = destino == m ? null : m;
	}

	/** Un nombre de fichero para el mundo de ahora: el nombre del mundo en un jugador, la dirección en un servidor. */
	private static String mundoActual() {
		Minecraft mc = Minecraft.getInstance();
		String n;
		if (mc.getSingleplayerServer() != null) {
			n = "local_" + mc.getSingleplayerServer().getWorldData().getLevelName();
		} else {
			ServerData s = mc.getCurrentServer();
			n = s == null ? "sin_mundo" : "servidor_" + s.ip;
		}
		return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}

	private static Path fichero() {
		return FabricLoader.getInstance().getConfigDir().resolve("destinos_marcas").resolve(mundo + ".txt");
	}

	/** Una marca por línea: x;z;nombre. */
	private static void lee() {
		MARCAS.clear();
		Path f = fichero();
		if (!Files.exists(f)) {
			return;
		}
		try {
			for (String l : Files.readAllLines(f, StandardCharsets.UTF_8)) {
				String[] p = l.split(";", 3);
				if (p.length == 3) {
					try {
						MARCAS.add(new Marca(Integer.parseInt(p[0].strip()), Integer.parseInt(p[1].strip()), p[2]));
					} catch (NumberFormatException e) {
						// línea rota: se salta
					}
				}
			}
		} catch (IOException e) {
			DestinosCore.LOG.warn("Destinos Core: no se pudieron leer las marcas del mapa de {}", f, e);
		}
	}

	private static void guarda() {
		Path f = fichero();
		List<String> lineas = new ArrayList<>();
		for (Marca m : MARCAS) {
			lineas.add(m.x() + ";" + m.z() + ";" + m.nombre().replace('\n', ' '));
		}
		try {
			Files.createDirectories(f.getParent());
			Files.write(f, lineas, StandardCharsets.UTF_8);
		} catch (IOException e) {
			DestinosCore.LOG.warn("Destinos Core: no se pudieron guardar las marcas del mapa en {}", f, e);
		}
	}
}
