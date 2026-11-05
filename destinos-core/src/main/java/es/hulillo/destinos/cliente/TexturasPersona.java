package es.hulillo.destinos.cliente;

import com.mojang.blaze3d.platform.NativeImage;
import es.hulillo.destinos.AspectoPersona;
import es.hulillo.destinos.DestinosCore;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Las skins de las personas de la corte, pintadas en el cliente la primera vez que hacen falta (AspectoPersona) y
 * guardadas en la tarjeta: como mucho las MAX últimas usadas, las demás se sueltan. Solo desde el hilo del render.
 */
final class TexturasPersona {
	private static final int MAX = 64;
	private static final Map<Integer, Identifier> TEXTURAS = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, Identifier> e) {
			if (size() > MAX) {
				Minecraft.getInstance().getTextureManager().release(e.getValue());
				return true;
			}
			return false;
		}
	};

	private TexturasPersona() {
	}

	static Identifier de(int aspecto) {
		Identifier id = TEXTURAS.get(aspecto);
		if (id == null) {
			id = Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "persona/" + Integer.toHexString(aspecto));
			int[] px = AspectoPersona.pixeles(aspecto);
			NativeImage img = new NativeImage(64, 64, true);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					img.setPixel(x, y, px[y * 64 + x]);
				}
			}
			String nombre = id.toString();
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> nombre, img));
			TEXTURAS.put(aspecto, id);
		}
		return id;
	}

	/** Al salir del mundo. */
	static void borra() {
		for (Identifier id : TEXTURAS.values()) {
			Minecraft.getInstance().getTextureManager().release(id);
		}
		TEXTURAS.clear();
	}
}
