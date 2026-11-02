package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Servidor → cliente: lo que hay clavado en un tablón (para abrir su pantalla). Los textos ya vienen montados; el
 * cliente solo los pinta y contesta con PaqueteAccionEncargo.
 */
public record PaqueteTablon(BlockPos pos, Component cabecera, Component subtitulo, Component reputacion, List<Linea> ofertas,
		List<Linea> activos, int maxActivos) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PaqueteTablon> TIPO =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "tablon"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PaqueteTablon> CODEC =
			CustomPacketPayload.codec(PaqueteTablon::escribe, PaqueteTablon::lee);

	/** Un encargo en el tablón o en curso. cogida: ya no se puede coger (en curso o hecha hoy). */
	public record Linea(Component titulo, Component texto, Component detalle, boolean cogida) {
	}

	private void escribe(RegistryFriendlyByteBuf buf) {
		buf.writeBlockPos(pos);
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, cabecera);
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, subtitulo);
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, reputacion);
		lineas(buf, ofertas);
		lineas(buf, activos);
		buf.writeVarInt(maxActivos);
	}

	private static void lineas(RegistryFriendlyByteBuf buf, List<Linea> l) {
		buf.writeVarInt(l.size());
		for (Linea x : l) {
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, x.titulo());
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, x.texto());
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, x.detalle());
			buf.writeBoolean(x.cogida());
		}
	}

	private static List<Linea> lineas(RegistryFriendlyByteBuf buf) {
		int n = buf.readVarInt();
		if (n < 0 || n > 64) {
			throw new IllegalArgumentException("Destinos Core: demasiados encargos en el paquete");
		}
		List<Linea> l = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			l.add(new Linea(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
					ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readBoolean()));
		}
		return l;
	}

	private static PaqueteTablon lee(RegistryFriendlyByteBuf buf) {
		BlockPos pos = buf.readBlockPos();
		Component cabecera = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		Component subtitulo = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		Component reputacion = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		List<Linea> ofertas = lineas(buf);
		List<Linea> activos = lineas(buf);
		return new PaqueteTablon(pos, cabecera, subtitulo, reputacion, ofertas, activos, buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TIPO;
	}
}
