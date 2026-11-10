package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

/**
 * Prueba del plano de la muralla (PlanMuralla) sin Minecraft, en muchos terrenos de mentira: llano, cuestas, montes,
 * laderas empinadas, barrancos, ríos y ruido, con calles que la cruzan por todas partes. Comprueba lo que antes salía
 * mal en el juego:
 * - el pie nunca se aleja más de un bloque del suelo (ni enterrada ni colgando);
 * - ningún lienzo de muro sube más de lo que puede (ni lienzos sueltos entre laderas);
 * - toda calle que cruza un muro tiene paso: un portón o un portillo, nunca la muralla encima;
 * - ninguna torre encima de una puerta;
 * - la franja dice bien qué pisa la muralla;
 * - y que el plano sale igual cada vez.
 */
public final class PruebaMuralla {
	private static int fallos;

	private PruebaMuralla() {
	}

	public static void main(String[] args) {
		String[] nombres = {"llano", "cuesta", "monte", "ladera", "barranco", "rio", "ruido"};
		int casos = 0;
		for (int nivel = 0; nivel <= 2; nivel++) {
			for (int t = 0; t < nombres.length; t++) {
				for (int semilla = 0; semilla < 12; semilla++) {
					prueba(nombres[t], t, nivel, semilla);
					casos++;
				}
			}
		}
		if (fallos > 0) {
			System.out.println(fallos + " fallos en " + casos + " planos de muralla");
			System.exit(1);
		}
		System.out.println("Muralla: " + casos + " planos sin fallos");
	}

	private static void falla(String caso, String que) {
		fallos++;
		if (fallos <= 40) {
			System.out.println("FALLO " + caso + ": " + que);
		}
	}

	private static IntBinaryOperator terreno(int tipo, long semilla) {
		Random r = new Random(semilla);
		double ox = r.nextInt(120) - 60;
		double oz = r.nextInt(120) - 60;
		double ang = r.nextDouble() * Math.PI * 2;
		double ux = Math.cos(ang);
		double uz = Math.sin(ang);
		double f1 = 0.02 + r.nextDouble() * 0.05;
		double f2 = 0.05 + r.nextDouble() * 0.08;
		return switch (tipo) {
			case 0 -> (x, z) -> 64;
			case 1 -> (x, z) -> 64 + (int) Math.floor((x * ux + z * uz) * 0.3);
			case 2 -> (x, z) -> 64 + (int) Math.round(45 * Math.exp(-(Math.pow(x - ox, 2) + Math.pow(z - oz, 2)) / 3000.0));
			case 3 -> (x, z) -> 64 + (int) Math.floor((x * ux + z * uz) * 1.4);
			case 4 -> (x, z) -> {
				double d = Math.abs(x * uz - z * ux - ox / 3);
				return d < 4 ? 40 : 64;
			};
			case 5 -> (x, z) -> 62;
			default -> (x, z) -> 64 + (int) Math.round(8 * Math.sin(x * f1) * Math.cos(z * f2) + 5 * Math.sin((x + z) * f2));
		};
	}

	private static BiPredicate<Integer, Integer> agua(int tipo, long semilla) {
		if (tipo != 5) {
			return (x, z) -> false;
		}
		Random r = new Random(semilla * 31);
		double ang = r.nextDouble() * Math.PI;
		double ux = Math.cos(ang);
		double uz = Math.sin(ang);
		return (x, z) -> Math.abs(x * uz - z * ux) < 5;
	}

	private static void prueba(String nombre, int tipo, int nivel, int semilla) {
		String caso = nombre + " nivel " + nivel + " semilla " + semilla;
		Random r = new Random(semilla * 7919L + tipo);
		IntBinaryOperator alto = terreno(tipo, semilla);
		BiPredicate<Integer, Integer> agua = agua(tipo, semilla);
		int cx = 0;
		int cz = 0;
		int[] radios = new int[64];
		double base = 70 + r.nextInt(30);
		double fase = r.nextDouble() * 6;
		for (int i = 0; i < radios.length; i++) {
			radios[i] = (int) Math.round(base + 10 * Math.sin(i * 2 * Math.PI / 64 * 3 + fase));
		}
		// calles: radiales (unas pocas principales, con su portón) y algunas de través que cruzan la línea
		List<int[]> calles = new ArrayList<>();
		List<int[]> huecosL = new ArrayList<>();
		int radiales = 6 + r.nextInt(8);
		for (int k = 0; k < radiales; k++) {
			double a = r.nextDouble() * Math.PI * 2;
			int largo = (int) base + 30;
			int px = 0;
			int pz = 0;
			for (int d = 8; d <= largo; d += 6) {
				int x = (int) Math.round(Math.cos(a) * d + Math.sin(d * 0.2) * 2);
				int z = (int) Math.round(Math.sin(a) * d);
				calles.add(new int[] {px, pz, alto.applyAsInt(px, pz), x, z, alto.applyAsInt(x, z), 15 + r.nextInt(15)});
				px = x;
				pz = z;
			}
			if (k < 2) {
				double rr = PlanMuralla.radioEn(radios, Math.toDegrees(a) * 10);
				int gx = (int) Math.round(Math.cos(a) * rr);
				int gz = (int) Math.round(Math.sin(a) * rr);
				huecosL.add(new int[] {gx - 9, gz - 9, gx + 9, gz + 9});
			}
		}
		for (int k = 0; k < 3; k++) {
			double a = r.nextDouble() * Math.PI * 2;
			double rr = PlanMuralla.radioEn(radios, Math.toDegrees(a) * 10);
			int x0 = (int) Math.round(Math.cos(a) * (rr - 12));
			int z0 = (int) Math.round(Math.sin(a) * (rr - 12));
			int x1 = (int) Math.round(Math.cos(a + 0.15) * (rr + 12));
			int z1 = (int) Math.round(Math.sin(a + 0.15) * (rr + 12));
			calles.add(new int[] {x0, z0, alto.applyAsInt(x0, z0), x1, z1, alto.applyAsInt(x1, z1), 20});
		}
		int[] huecos = new int[huecosL.size() * 4];
		for (int k = 0; k < huecosL.size(); k++) {
			System.arraycopy(huecosL.get(k), 0, huecos, 4 * k, 4);
		}
		int[] castillo = nivel == 2 ? new int[] {(int) base - 10, 0, 14} : null;
		PlanMuralla p = PlanMuralla.traza(cx, cz, nivel, radios, alto, agua, castillo, huecos, calles);
		PlanMuralla otra = PlanMuralla.traza(cx, cz, nivel, radios, alto, agua, castillo, huecos, calles);
		if (!p.resumen().equals(otra.resumen()) || !java.util.Arrays.equals(p.pie, otra.pie)) {
			falla(caso, "el plano no sale igual dos veces");
		}
		int n = p.n();
		int muros = 0;
		for (int k = 0; k < n; k++) {
			int[] v = p.v[k];
			int suelo = Integer.MIN_VALUE;
			for (int[] d : new int[][] {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
				suelo = Math.max(suelo, alto.applyAsInt(v[0] + d[0], v[1] + d[1]));
			}
			if (Math.abs(p.pie[k] - suelo) > 1) {
				falla(caso, "vértice " + k + " con el pie a " + p.pie[k] + " y el suelo a " + suelo);
			}
			int s = p.siguiente(k);
			if (p.tipo[k] == PlanMuralla.Tipo.MURO) {
				muros++;
				if (Math.abs(p.pie[s] - p.pie[k]) > Math.max(1, p.largo(k) * PlanMuralla.PENDIENTE)) {
					falla(caso, "lienzo " + k + " demasiado empinado");
				}
				if (p.tipo[(k + n - 1) % n] == PlanMuralla.Tipo.CUESTA && p.tipo[s] == PlanMuralla.Tipo.CUESTA) {
					falla(caso, "lienzo suelto " + k + " entre dos laderas");
				}
			}
		}
		if ((tipo == 0 || tipo == 1 || tipo == 5) && muros < n - 1) {
			falla(caso, "en terreno suave faltan lienzos: " + p.resumen());
		}
		// toda calle que cruza un muro tiene paso
		int grueso = PlanMuralla.grueso(nivel);
		for (int[] c : calles) {
			for (int k = 0; k < n; k++) {
				if (!p.hayMuro(k) || p.agua[k]) {
					continue;
				}
				int[] a = p.v[k];
				int[] b = p.v[p.siguiente(k)];
				double[] t = PlanMuralla.cruce(a[0], a[1], b[0], b[1], c[0], c[1], c[3], c[4]);
				if (t == null) {
					continue;
				}
				int x = (int) Math.round(a[0] + (b[0] - a[0]) * t[0]);
				int z = (int) Math.round(a[1] + (b[1] - a[1]) * t[0]);
				if (p.enHueco(x, z, grueso + 2) || p.enCastillo(x, z, 2)) {
					continue;
				}
				boolean paso = p.portillos.stream().anyMatch(q -> Math.hypot(q.x() - x, q.z() - z) <= q.medio() + 0.5);
				if (!paso) {
					falla(caso, "calle que cruza el muro en " + x + ", " + z + " sin paso");
				}
			}
		}
		for (PlanMuralla.Torre t : p.torres) {
			if (p.enHueco(t.x(), t.z(), PlanMuralla.RADIO_TORRE + 3)) {
				falla(caso, "torre en " + t.x() + ", " + t.z() + " encima de un portón");
			}
			for (PlanMuralla.Portillo q : p.portillos) {
				if (Math.hypot(q.x() - t.x(), q.z() - t.z()) < PlanMuralla.RADIO_TORRE + 3) {
					falla(caso, "torre en " + t.x() + ", " + t.z() + " encima de un portillo");
				}
			}
		}
		// la franja: una caja en plena línea de un muro la pisa; una en el centro, no
		for (int k = 0; k < n; k++) {
			if (p.hayMuro(k)) {
				int[] v = p.v[k];
				if (!p.pisa(v[0] - 1, v[1] - 1, v[0] + 1, v[1] + 1)) {
					falla(caso, "la franja no ve una caja encima del vértice " + k);
				}
				break;
			}
		}
		if (p.pisa(-5, -5, 5, 5)) {
			falla(caso, "la franja dice que el centro de la ciudad pisa la muralla");
		}
	}
}
