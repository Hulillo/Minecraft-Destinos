package es.hulillo.destinos;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Cliente → servidor: se ha abierto el mapa político. El servidor contesta con todas las zonas que ya tiene
 * calculadas (no solo las de alrededor) y con las fichas de reinos y nombres de ducados (PaqueteFichas).
 */
public record PaquetePedirMapa(boolean fichas) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PaquetePedirMapa> TIPO =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "pedir_mapa"));
	public static final StreamCodec<FriendlyByteBuf, PaquetePedirMapa> CODEC =
			CustomPacketPayload.codec((p, buf) -> buf.writeBoolean(p.fichas()), buf -> new PaquetePedirMapa(buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TIPO;
	}
}
