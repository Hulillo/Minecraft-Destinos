package es.hulillo.destinos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * El estandarte propio de cada reino: el fondo es su color, y encima una partición (mitad, banda, cruz…) y una figura
 * (círculo, rombo, flor…) de dos colores que contrastan. Sale siempre igual del reino y de la sal del mundo, así que el
 * servidor lo pone en las capitales y el mapa lo dibuja en la ficha sin guardarlo en ningún sitio.
 *
 * El escudo se guarda en un int: partición (4 bits), su color (4), figura (4) y su color (4); -1 es "sin escudo" (el
 * estandarte liso del color del reino, como antes).
 */
public final class Estandarte {
	/** Particiones del campo: los nombres de los dibujos de estandarte de Minecraft. */
	static final String[] PARTICIONES = {"half_vertical", "half_horizontal", "diagonal_left", "diagonal_right", "stripe_center",
			"stripe_middle", "cross", "straight_cross", "stripe_top", "border"};
	/** Figuras que van encima. */
	static final String[] FIGURAS = {"circle", "rhombus", "triangle_bottom", "triangle_top", "flower", "border", "stripe_top",
			"stripe_bottom"};
	/** Los mismos nombres, para el cliente (que dibuja el estandarte en el mapa). */
	public static final java.util.List<String> PARTICION_NOMBRES = java.util.List.of(PARTICIONES);
	public static final java.util.List<String> FIGURA_NOMBRES = java.util.List.of(FIGURAS);
	/** Los esmaltes con los que se pinta encima del fondo. */
	private static final DyeColor[] ESMALTES = {DyeColor.WHITE, DyeColor.YELLOW, DyeColor.BLACK, DyeColor.RED, DyeColor.BLUE,
			DyeColor.GREEN, DyeColor.ORANGE, DyeColor.LIGHT_BLUE, DyeColor.PURPLE, DyeColor.LIGHT_GRAY};
	private static final int CONTRASTE = 70;
	private static final int SAL_ESCUDO = 4243;

	private Estandarte() {
	}

	/** El escudo del reino (kx, kz) sobre su fondo (id de DyeColor). */
	public static int de(int kx, int kz, int sal, int fondo) {
		if (fondo < 0) {
			return -1;
		}
		int h = Geo.hash(kx, kz, sal + SAL_ESCUDO);
		int particion = Math.floorMod(h, PARTICIONES.length);
		int figura = Math.floorMod(h >>> 8, FIGURAS.length);
		if (FIGURAS[figura].equals(PARTICIONES[particion])) {
			figura = (figura + 1) % FIGURAS.length;
		}
		int c1 = esmalte(fondo, -1, h >>> 16);
		int c2 = esmalte(fondo, c1, h >>> 22);
		return particion | c1 << 4 | figura << 8 | c2 << 12;
	}

	/** Un esmalte que se distinga bien del fondo y que no sea el de antes. */
	private static int esmalte(int fondo, int otro, int h) {
		int lf = luz(fondo);
		for (int i = 0; i < ESMALTES.length; i++) {
			DyeColor d = ESMALTES[Math.floorMod(h + i, ESMALTES.length)];
			if (d.getId() != fondo && d.getId() != otro && Math.abs(luz(d.getId()) - lf) >= CONTRASTE) {
				return d.getId();
			}
		}
		return lf > 128 ? (otro == DyeColor.BLACK.getId() ? DyeColor.BLUE.getId() : DyeColor.BLACK.getId())
				: (otro == DyeColor.WHITE.getId() ? DyeColor.YELLOW.getId() : DyeColor.WHITE.getId());
	}

	private static int luz(int dye) {
		int c = DyeColor.byId(dye).getTextureDiffuseColor();
		return (((c >> 16) & 0xFF) * 3 + ((c >> 8) & 0xFF) * 6 + (c & 0xFF)) / 10;
	}

	public static int particion(int escudo) {
		return escudo & 0xF;
	}

	public static int colorParticion(int escudo) {
		return escudo >> 4 & 0xF;
	}

	public static int figura(int escudo) {
		return escudo >> 8 & 0xF;
	}

	public static int colorFigura(int escudo) {
		return escudo >> 12 & 0xF;
	}

	/** Las capas del escudo para un estandarte de verdad. */
	static BannerPatternLayers capas(HolderGetter<BannerPattern> dibujos, int escudo) {
		BannerPatternLayers.Builder b = new BannerPatternLayers.Builder();
		b.addIfRegistered(dibujos, dibujo(PARTICIONES[particion(escudo) % PARTICIONES.length]), DyeColor.byId(colorParticion(escudo)));
		b.addIfRegistered(dibujos, dibujo(FIGURAS[figura(escudo) % FIGURAS.length]), DyeColor.byId(colorFigura(escudo)));
		return b.build();
	}

	private static ResourceKey<BannerPattern> dibujo(String nombre) {
		return ResourceKey.create(Registries.BANNER_PATTERN, Identifier.withDefaultNamespace(nombre));
	}

	/** Pinta el escudo en el estandarte que hay en pos (puesto ya), sobre el fondo del color del reino. */
	static void aplica(WorldGenLevel level, BlockPos pos, int fondo, int escudo) {
		if (escudo < 0 || fondo < 0 || !(level.getBlockEntity(pos) instanceof BannerBlockEntity be)) {
			return;
		}
		ItemStack s = new ItemStack(Items.WHITE_BANNER);
		s.set(DataComponents.BANNER_PATTERNS, capas(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN), escudo));
		be.fromItem(s, DyeColor.byId(fondo));
		be.setChanged();
	}

	/**
	 * Si el dibujo cubre el punto (u, v) de la tela, con u de 0 (izquierda) a 1 y v de 0 (arriba) a 1. Imita los
	 * dibujos de Minecraft (la tela es de 20 × 40 píxeles) para pintar el estandarte en el mapa.
	 */
	public static boolean cubre(String dibujo, double u, double v) {
		double px = (u - 0.5) * 20;
		double py = (v - 0.5) * 40;
		return switch (dibujo) {
			case "half_vertical" -> u < 0.5;
			case "half_horizontal" -> v < 0.5;
			case "diagonal_left" -> u + v < 1;
			case "diagonal_right" -> v < u;
			case "stripe_center" -> Math.abs(px) < 3;
			case "stripe_middle" -> Math.abs(py) < 3;
			case "cross" -> Math.abs(u - v) < 0.12 || Math.abs(u + v - 1) < 0.12;
			case "straight_cross" -> Math.abs(px) < 3 || Math.abs(py) < 3;
			case "stripe_top" -> v < 0.33;
			case "stripe_bottom" -> v > 0.67;
			case "border" -> u < 0.1 || u > 0.9 || v < 0.05 || v > 0.95;
			case "circle" -> Math.hypot(px, py) < 5;
			case "rhombus" -> Math.abs(px) / 7 + Math.abs(py) / 11 < 1;
			case "triangle_bottom" -> (1 - v) * 40 < 10 - Math.abs(px);
			case "triangle_top" -> v * 40 < 10 - Math.abs(px);
			case "flower" -> Math.hypot(px, py) < 3.5 + 2.5 * Math.abs(Math.cos(2 * Math.atan2(py, px)));
			default -> false;
		};
	}
}
