package es.hulillo.destinos;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Servidor → cliente: la barra de la mazmorra en la que peleas (oleada o vida del jefe). Va por aquí y no como barra
 * de jefe de vanilla para poder pintarla más abajo. visible = false la quita.
 */
public record PaqueteMazmorra(boolean visible, Component nombre, float progreso, boolean jefe) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PaqueteMazmorra> TIPO =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "mazmorra"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PaqueteMazmorra> CODEC =
			CustomPacketPayload.codec(PaqueteMazmorra::escribe, PaqueteMazmorra::lee);
	static final PaqueteMazmorra NADA = new PaqueteMazmorra(false, Component.empty(), 0, false);

	private void escribe(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(visible);
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, nombre);
		buf.writeFloat(progreso);
		buf.writeBoolean(jefe);
	}

	private static PaqueteMazmorra lee(RegistryFriendlyByteBuf buf) {
		return new PaqueteMazmorra(buf.readBoolean(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readFloat(), buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TIPO;
	}
}
