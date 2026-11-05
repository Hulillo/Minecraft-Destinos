package es.hulillo.destinos;

import es.hulillo.destinos.politica.Grafo;
import es.hulillo.destinos.politica.Politica;
import java.lang.reflect.Method;
import net.minecraft.nbt.CompoundTag;

/**
 * Guardar y cargar la política (con la gente) no pierde nada: lo cargado se vuelve a guardar igual, y la historia
 * sigue exactamente igual que sin haber cargado. Dice también lo que ocupa el guardado. Usa el NBT de mentira de
 * esta carpeta y la rejilla de PruebaPolitica.
 */
public final class PruebaGuardado {
	public static void main(String[] args) throws ReflectiveOperationException {
		Method rejilla = Class.forName("PruebaPolitica").getDeclaredMethod("grafo", int.class);
		rejilla.setAccessible(true);
		int mitad = 20 * Politica.TURNOS_POR_ANIO;
		for (int sal : new int[] {184467221, 99, 1999999999, -123456}) {
			Grafo g = (Grafo) rejilla.invoke(null, sal);
			Politica seguida = new Politica(sal);
			Politica antes = new Politica(sal);
			for (int t = 0; t < mitad; t++) {
				seguida.turno(g, s -> {
				});
				antes.turno(g, s -> {
				});
			}
			CompoundTag guardado = GuardadoPolitica.guarda(antes, 5, 7);
			Politica cargada = GuardadoPolitica.carga(guardado, sal).politica();
			iguales(sal, "lo cargado no se vuelve a guardar igual", guardado.toString(), GuardadoPolitica.guarda(cargada, 5, 7).toString());
			for (int t = 0; t < mitad; t++) {
				seguida.turno(g, s -> {
				});
				cargada.turno(g, s -> {
				});
			}
			CompoundTag fin = GuardadoPolitica.guarda(seguida, 5, 7);
			iguales(sal, "tras cargar, la historia sigue distinta", fin.toString(), GuardadoPolitica.guarda(cargada, 5, 7).toString());
			System.out.println("sal " + sal + ": guardar y cargar OK · " + seguida.gente().personas().size() + " personas · guardado ~"
					+ fin.bytes() / 1024 + " KB (gente ~" + fin.getCompoundOrEmpty("gente").bytes() / 1024 + " KB)");
		}
	}

	private static void iguales(int sal, String motivo, String a, String b) {
		if (a.equals(b)) {
			return;
		}
		int i = 0;
		while (i < Math.min(a.length(), b.length()) && a.charAt(i) == b.charAt(i)) {
			i++;
		}
		System.err.println("FALLA sal " + sal + ": " + motivo + "\n  " + a.substring(Math.max(0, i - 200), Math.min(a.length(), i + 100))
				+ "\n  " + b.substring(Math.max(0, i - 200), Math.min(b.length(), i + 100)));
		System.exit(1);
	}
}
