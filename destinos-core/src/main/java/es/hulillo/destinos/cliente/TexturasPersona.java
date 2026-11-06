package es.hulillo.destinos.cliente;

import com.mojang.blaze3d.platform.NativeImage;
import es.hulillo.destinos.AspectoPersona;
import es.hulillo.destinos.DestinosCore;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Las skins de las personas de la corte, pintadas en el cliente la primera vez que hacen falta (AspectoPersona) y
 * guardadas en la tarjeta: como mucho las MAX últimas usadas, las demás se sueltan. Solo desde el hilo del render.
 */
final class TexturasPersona {
	private static final int MAX = 64;
	/** Si está 3D Skin Layers (y responde): entonces cada skin lleva también sus mallas en 3D (Capas3D). */
	static boolean capas3d = FabricLoader.getInstance().isModLoaded("skinlayers3d");

	/** Una skin hecha: su textura y, con 3D Skin Layers, sus mallas (un Capas3D.Mallas, o null). */
	private record Skin(Identifier id, Object capas) {
	}

	private static final Map<Integer, Skin> TEXTURAS = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, Skin> e) {
			if (size() > MAX) {
				Minecraft.getInstance().getTextureManager().release(e.getValue().id());
				return true;
			}
			return false;
		}
	};

	private TexturasPersona() {
	}

	static Identifier de(int aspecto) {
		return skin(aspecto).id();
	}

	/** Las mallas en 3D de la skin (un Capas3D.Mallas), o null si no hay 3D Skin Layers. */
	static Object capas(int aspecto) {
		return capas3d ? skin(aspecto).capas() : null;
	}

	private static Skin skin(int aspecto) {
		Skin skin = TEXTURAS.get(aspecto);
		if (skin == null) {
			Identifier id = Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "persona/" + Integer.toHexString(aspecto));
			int[] px = AspectoPersona.pixeles(aspecto);
			NativeImage img = new NativeImage(64, 64, true);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					img.setPixel(x, y, px[y * 64 + x]);
				}
			}
			// las mallas se leen de la imagen antes de que la textura se la quede
			Object capas = capas3d ? Capas3D.crea(img) : null;
			String nombre = id.toString();
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> nombre, img));
			skin = new Skin(id, capas);
			TEXTURAS.put(aspecto, skin);
		}
		return skin;
	}

	/** Al salir del mundo. */
	static void borra() {
		for (Skin s : TEXTURAS.values()) {
			Minecraft.getInstance().getTextureManager().release(s.id());
		}
		TEXTURAS.clear();
	}
}
