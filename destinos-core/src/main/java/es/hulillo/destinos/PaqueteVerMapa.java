package es.hulillo.destinos;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Servidor → cliente: abre el mapa político centrado en (x, z). Lo manda /destinosver, que es lo que ejecuta un clic en
 * los nombres de reinos, ducados y capitales de los anuncios del chat.
 */
public record PaqueteVerMapa(int x, int z) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PaqueteVerMapa> TIPO =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "ver_mapa"));
	public static final StreamCodec<FriendlyByteBuf, PaqueteVerMapa> CODEC = CustomPacketPayload.codec((p, buf) -> {
		buf.writeVarInt(p.x());
		buf.writeVarInt(p.z());
	}, buf -> new PaqueteVerMapa(buf.readVarInt(), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TIPO;
	}
}
