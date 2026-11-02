package es.hulillo.destinos;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Cliente → servidor: coger la oferta i del tablón pos, o dejar el encargo en curso i. */
public record PaqueteAccionEncargo(boolean coger, BlockPos pos, int indice) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PaqueteAccionEncargo> TIPO =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "accion_encargo"));
	public static final StreamCodec<FriendlyByteBuf, PaqueteAccionEncargo> CODEC =
			CustomPacketPayload.codec(PaqueteAccionEncargo::escribe, PaqueteAccionEncargo::lee);

	private void escribe(FriendlyByteBuf buf) {
		buf.writeBoolean(coger);
		buf.writeBlockPos(pos);
		buf.writeVarInt(indice);
	}

	private static PaqueteAccionEncargo lee(FriendlyByteBuf buf) {
		return new PaqueteAccionEncargo(buf.readBoolean(), buf.readBlockPos(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TIPO;
	}
}
