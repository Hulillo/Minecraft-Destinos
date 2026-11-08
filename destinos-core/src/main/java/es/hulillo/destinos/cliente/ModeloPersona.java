package es.hulillo.destinos.cliente;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * El modelo del jugador (brazos anchos) para la corte. Guarda las piezas de la capa exterior para que, con 3D Skin
 * Layers, cada persona cuelgue en ellas las mallas en 3D de su propia skin justo antes de pintarse.
 */
final class ModeloPersona extends HumanoidModel<RenderPersona.Estado> {
	final ModelPart chaqueta;
	final ModelPart mangaIzq;
	final ModelPart mangaDer;
	final ModelPart perneraIzq;
	final ModelPart perneraDer;

	ModeloPersona(ModelPart raiz) {
		super(raiz);
		chaqueta = hijo(body, "jacket");
		mangaIzq = hijo(leftArm, "left_sleeve");
		mangaDer = hijo(rightArm, "right_sleeve");
		perneraIzq = hijo(leftLeg, "left_pants");
		perneraDer = hijo(rightLeg, "right_pants");
	}

	private static ModelPart hijo(ModelPart p, String nombre) {
		return p.hasChild(nombre) ? p.getChild(nombre) : null;
	}

	@Override
	public void setupAnim(RenderPersona.Estado s) {
		super.setupAnim(s);
		if (TexturasPersona.capas3d) {
			// el modelo es de todas las personas: cada una pone las suyas (o quita las de la anterior)
			Capas3D.pon(this, TexturasPersona.capas(s.aspecto));
		}
	}
}
