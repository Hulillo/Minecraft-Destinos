package es.hulillo.destinos.cliente;

import es.hulillo.destinos.CuerpoPersona;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * v0.7.1 La corte: las personas se pintan con el modelo del jugador (brazos anchos) y una skin hecha en el cliente
 * a partir de su aspecto (TexturasPersona); con 3D Skin Layers, su capa exterior sale en 3D (Capas3D). Los niños salen pequeños por su atributo de escala.
 */
public final class RenderPersona extends HumanoidMobRenderer<CuerpoPersona, RenderPersona.Estado, ModeloPersona> {
	/** Lo que hace falta para pintar a la persona: lo de cualquier humanoide más su aspecto. */
	public static final class Estado extends HumanoidRenderState {
		int aspecto;
	}

	public RenderPersona(EntityRendererProvider.Context ctx) {
		super(ctx, new ModeloPersona(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
	}

	@Override
	public Estado createRenderState() {
		return new Estado();
	}

	@Override
	public void extractRenderState(CuerpoPersona e, Estado s, float pt) {
		super.extractRenderState(e, s, pt);
		s.aspecto = e.aspecto();
	}

	@Override
	public Identifier getTextureLocation(Estado s) {
		return TexturasPersona.de(s.aspecto);
	}
}
