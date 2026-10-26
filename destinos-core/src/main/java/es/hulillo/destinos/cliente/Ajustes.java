package es.hulillo.destinos.cliente;

import es.hulillo.destinos.DestinosCore;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Ajustes del cliente en config/destinos_core.properties (de momento, si se ve el mapa político). */
final class Ajustes {
	private static final Path FICHERO = FabricLoader.getInstance().getConfigDir().resolve("destinos_core.properties");
	private static final String VISIBLE = "mapa_politico";

	private Ajustes() {
	}

	static boolean leeVisible() {
		Properties p = new Properties();
		if (Files.isRegularFile(FICHERO)) {
			try (Reader r = Files.newBufferedReader(FICHERO)) {
				p.load(r);
			} catch (IOException e) {
				DestinosCore.LOG.warn("Destinos Core: no se pudo leer {}", FICHERO, e);
			}
		}
		return !"false".equals(p.getProperty(VISIBLE));
	}

	static void guardaVisible(boolean visible) {
		Properties p = new Properties();
		p.setProperty(VISIBLE, Boolean.toString(visible));
		try (Writer w = Files.newBufferedWriter(FICHERO)) {
			p.store(w, "Destinos Core (cliente)");
		} catch (IOException e) {
			DestinosCore.LOG.warn("Destinos Core: no se pudo guardar {}", FICHERO, e);
		}
	}
}
