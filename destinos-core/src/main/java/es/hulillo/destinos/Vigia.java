package es.hulillo.destinos;

import java.io.IOException;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/**
 * Vigía del servidor: si el hilo del servidor deja de atender sus tareas durante un rato (el mundo se queda
 * congelado: sin colisiones, sin comandos, sin log), escribe en logs/destinos-congelado-*.txt lo que estaba haciendo
 * cada hilo, con sus pilas enteras y quién tiene cada candado. Así se ve qué se ha quedado esperando a qué.
 * <p>
 * El latido es una tarea que se manda al servidor cada pocos segundos: el servidor las atiende también con el juego
 * en pausa, así que la pausa no cuenta como cuelgue. Una sola foto por cuelgue (y otra un poco después, para ver si
 * sigue en el mismo sitio); si el servidor se recupera, vuelve a vigilar.
 */
final class Vigia {
	private static final long LATIDO_MS = 5_000;
	private static final long CUELGUE_MS = 30_000;
	private static final long SEGUNDA_FOTO_MS = 60_000;

	private static volatile Thread hilo;

	private Vigia() {
	}

	static synchronized void arranca(MinecraftServer server) {
		para();
		Thread t = new Thread(() -> vigila(server), "Destinos Core - vigía");
		t.setDaemon(true);
		hilo = t;
		t.start();
	}

	static synchronized void para() {
		Thread t = hilo;
		hilo = null;
		if (t != null) {
			t.interrupt();
		}
	}

	private static void vigila(MinecraftServer server) {
		long[] ultimo = {System.currentTimeMillis()};
		int fotos = 0;
		long ultimaFoto = 0;
		while (hilo == Thread.currentThread() && server.isRunning()) {
			try {
				Thread.sleep(LATIDO_MS);
			} catch (InterruptedException e) {
				return;
			}
			server.execute(() -> ultimo[0] = System.currentTimeMillis());
			long ahora = System.currentTimeMillis();
			long parado = ahora - ultimo[0];
			if (parado < CUELGUE_MS + LATIDO_MS) {
				fotos = 0;
				continue;
			}
			if (fotos == 0 || fotos == 1 && ahora - ultimaFoto >= SEGUNDA_FOTO_MS) {
				fotos++;
				ultimaFoto = ahora;
				foto(parado);
			}
		}
	}

	private static void foto(long parado) {
		StringBuilder b = new StringBuilder();
		b.append("Destinos Core: el hilo del servidor lleva ").append(parado / 1000)
				.append(" s sin atender sus tareas. Pilas de todos los hilos:\n\n");
		ThreadMXBean mx = ManagementFactory.getThreadMXBean();
		long[] bloqueados = mx.isSynchronizerUsageSupported() ? mx.findDeadlockedThreads() : mx.findMonitorDeadlockedThreads();
		if (bloqueados != null) {
			b.append("*** INTERBLOQUEO entre ").append(bloqueados.length).append(" hilos (la JVM lo confirma) ***\n\n");
		}
		for (ThreadInfo t : mx.dumpAllThreads(mx.isObjectMonitorUsageSupported(), mx.isSynchronizerUsageSupported())) {
			escribe(b, t);
		}
		String nombre = "destinos-congelado-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt";
		Path f = FabricLoader.getInstance().getGameDir().resolve("logs").resolve(nombre);
		try {
			Files.createDirectories(f.getParent());
			Files.writeString(f, b.toString(), StandardCharsets.UTF_8);
			DestinosCore.LOG.error("Destinos Core: el servidor parece colgado ({} s); pilas de los hilos en {}", parado / 1000, f);
		} catch (IOException e) {
			DestinosCore.LOG.error("Destinos Core: el servidor parece colgado ({} s) y no se pudo escribir {}:\n{}", parado / 1000, f, b);
		}
	}

	/** Como ThreadInfo.toString, pero con la pila entera (aquel corta a 8 líneas). */
	private static void escribe(StringBuilder b, ThreadInfo t) {
		b.append('"').append(t.getThreadName()).append("\" id=").append(t.getThreadId()).append(' ').append(t.getThreadState());
		if (t.getLockName() != null) {
			b.append(" esperando ").append(t.getLockName());
		}
		if (t.getLockOwnerName() != null) {
			b.append(" que tiene \"").append(t.getLockOwnerName()).append("\" id=").append(t.getLockOwnerId());
		}
		b.append('\n');
		StackTraceElement[] pila = t.getStackTrace();
		MonitorInfo[] monitores = t.getLockedMonitors();
		for (int i = 0; i < pila.length; i++) {
			b.append("\tat ").append(pila[i]).append('\n');
			for (MonitorInfo m : monitores) {
				if (m.getLockedStackDepth() == i) {
					b.append("\t- tiene ").append(m).append('\n');
				}
			}
		}
		LockInfo[] sincros = t.getLockedSynchronizers();
		if (sincros.length > 0) {
			b.append("\tSincronizadores que tiene:\n");
			for (LockInfo l : sincros) {
				b.append("\t- ").append(l).append('\n');
			}
		}
		b.append('\n');
	}
}
