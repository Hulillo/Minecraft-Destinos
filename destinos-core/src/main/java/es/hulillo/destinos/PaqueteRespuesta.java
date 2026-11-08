package es.hulillo.destinos;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Cliente → servidor (v0.7.1 La corte): la opción elegida en el diálogo con alguien de la corte (DialogoCorte). */
public record PaqueteRespuesta(int entidad, String clave) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PaqueteRespuesta> TIPO =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "respuesta"));
	public static final StreamCodec<FriendlyByteBuf, PaqueteRespuesta> CODEC = CustomPacketPayload.codec((p, buf) -> {
		buf.writeVarInt(p.entidad());
		buf.writeUtf(p.clave(), 32);
	}, buf -> new PaqueteRespuesta(buf.readVarInt(), buf.readUtf(32)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TIPO;
	}
}
