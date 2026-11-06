package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Servidor → cliente (v0.7.1 La corte): lo que dice alguien de la corte al hablarle y lo que se le puede contestar.
 * entidad: el cuerpo con el que se habla (la respuesta vuelve con él); aspecto: AspectoPersona, para pintar la cara.
 * Las opciones las decide el servidor; el cliente solo las enseña y devuelve la clave elegida (PaqueteRespuesta).
 */
public record PaqueteDialogo(int entidad, Component nombre, Component cargo, int aspecto, Component texto, List<Opcion> opciones)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PaqueteDialogo> TIPO =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "dialogo"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PaqueteDialogo> CODEC =
			CustomPacketPayload.codec(PaqueteDialogo::escribe, PaqueteDialogo::lee);
	private static final int MAX_OPCIONES = 8;

	public record Opcion(String clave, Component texto) {
	}

	private static void escribe(PaqueteDialogo p, RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(p.entidad());
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.nombre());
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.cargo());
		buf.writeInt(p.aspecto());
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.texto());
		buf.writeVarInt(p.opciones().size());
		for (Opcion o : p.opciones()) {
			buf.writeUtf(o.clave(), 32);
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, o.texto());
		}
	}

	private static PaqueteDialogo lee(RegistryFriendlyByteBuf buf) {
		int entidad = buf.readVarInt();
		Component nombre = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		Component cargo = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		int aspecto = buf.readInt();
		Component texto = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		int n = buf.readVarInt();
		if (n < 0 || n > MAX_OPCIONES) {
			throw new IllegalArgumentException("Destinos Core: diálogo con demasiadas opciones");
		}
		List<Opcion> l = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			l.add(new Opcion(buf.readUtf(32), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf)));
		}
		return new PaqueteDialogo(entidad, nombre, cargo, aspecto, texto, l);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TIPO;
	}
}
