package es.hulillo.destinos.cliente;

import com.mojang.blaze3d.platform.NativeImage;
import dev.tr7zw.skinlayers.accessor.ModelPartInjector;
import dev.tr7zw.skinlayers.api.Mesh;
import dev.tr7zw.skinlayers.api.MeshHelper;
import dev.tr7zw.skinlayers.api.OffsetProvider;
import dev.tr7zw.skinlayers.api.SkinLayersAPI;
import es.hulillo.destinos.DestinosCore;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Enchufe con 3D Skin Layers (si el jugador lo tiene): la capa exterior de la skin de la corte (pelo, coronas, capas,
 * cota de malla) sale en 3D, como la de los jugadores. Las mallas se hacen una vez por skin (junto a su textura) y
 * se cuelgan de las piezas de la capa exterior del modelo en cada setupAnim; el mod las pinta en lugar de la capa
 * plana. Sin el mod esta clase ni se carga (TexturasPersona.capas3d lo mira antes) y la capa se ve plana.
 */
final class Capas3D {
	/** Las seis mallas de una skin. */
	record Mallas(Mesh cabeza, Mesh torso, Mesh brazoIzq, Mesh brazoDer, Mesh piernaIzq, Mesh piernaDer) {
	}

	private Capas3D() {
	}

	/** Las mallas de la capa exterior de una skin de 64x64 con brazos anchos (mismas medidas que usa el mod). */
	static Mallas crea(NativeImage img) {
		try {
			MeshHelper h = SkinLayersAPI.getMeshHelper();
			return new Mallas(h.create3DMesh(img, 8, 8, 8, 32, 0, false, 0.6f),
					h.create3DMesh(img, 8, 12, 4, 16, 32, true, 0),
					h.create3DMesh(img, 4, 12, 4, 48, 48, true, -2f),
					h.create3DMesh(img, 4, 12, 4, 40, 32, true, -2f),
					h.create3DMesh(img, 4, 12, 4, 0, 48, true, 0),
					h.create3DMesh(img, 4, 12, 4, 0, 32, true, 0));
		} catch (Throwable t) {
			apaga(t);
			return null;
		}
	}

	/** Cuelga las mallas (o las quita, con null) de las piezas de la capa exterior. */
	static void pon(ModeloPersona m, Object mallas) {
		try {
			Mallas c = (Mallas) mallas;
			pon(m.hat, c == null ? null : c.cabeza(), OffsetProvider.HEAD);
			pon(m.chaqueta, c == null ? null : c.torso(), OffsetProvider.BODY);
			pon(m.mangaIzq, c == null ? null : c.brazoIzq(), OffsetProvider.LEFT_ARM);
			pon(m.mangaDer, c == null ? null : c.brazoDer(), OffsetProvider.RIGHT_ARM);
			pon(m.perneraIzq, c == null ? null : c.piernaIzq(), OffsetProvider.LEFT_LEG);
			pon(m.perneraDer, c == null ? null : c.piernaDer(), OffsetProvider.RIGHT_LEG);
		} catch (Throwable t) {
			apaga(t);
		}
	}

	private static void pon(ModelPart p, Mesh malla, OffsetProvider o) {
		if (p != null) {
			((ModelPartInjector) (Object) p).setInjectedMesh(malla, o);
		}
	}

	private static void apaga(Throwable t) {
		if (TexturasPersona.capas3d) {
			TexturasPersona.capas3d = false;
			DestinosCore.LOG.warn("Destinos Core: 3D Skin Layers no responde como se esperaba; la corte se verá con la capa plana", t);
		}
	}
}
