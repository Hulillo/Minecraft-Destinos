package es.hulillo.destinos.cliente;

import es.hulillo.destinos.DestinosCore;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Ajustes del cliente en config/destinos_core.properties: si se ve el mapa político y si ya se vio su tutorial. */
final class Ajustes {
	private static final Path FICHERO = FabricLoader.getInstance().getConfigDir().resolve("destinos_core.properties");
	private static final String VISIBLE = "mapa_politico";
	private static final String TUTORIAL = "tutorial_mapa_visto";

	private Ajustes() {
	}

	private static Properties lee() {
		Properties p = new Properties();
		if (Files.isRegularFile(FICHERO)) {
			try (Reader r = Files.newBufferedReader(FICHERO)) {
				p.load(r);
			} catch (IOException e) {
				DestinosCore.LOG.warn("Destinos Core: no se pudo leer {}", FICHERO, e);
			}
		}
		return p;
	}

	/** Cambia un ajuste y guarda el fichero sin tocar los demás. */
	private static void pon(String clave, String valor) {
		Properties p = lee();
		p.setProperty(clave, valor);
		try (Writer w = Files.newBufferedWriter(FICHERO)) {
			p.store(w, "Destinos Core (cliente)");
		} catch (IOException e) {
			DestinosCore.LOG.warn("Destinos Core: no se pudo guardar {}", FICHERO, e);
		}
	}

	static boolean leeVisible() {
		return !"false".equals(lee().getProperty(VISIBLE));
	}

	static void guardaVisible(boolean visible) {
		pon(VISIBLE, Boolean.toString(visible));
	}

	static boolean tutorialVisto() {
		return "true".equals(lee().getProperty(TUTORIAL));
	}

	static void marcaTutorial() {
		pon(TUTORIAL, "true");
	}
}
