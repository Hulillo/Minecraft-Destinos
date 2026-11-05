package es.hulillo.destinos;

import es.hulillo.destinos.politica.Persona;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Cliente → servidor: el árbol familiar de una persona (o, con persona = Persona.NADIE, el del rey del reino kx, kz).
 * El servidor contesta con PaqueteArbol. Solo se pide al abrirlo, para no mandar la familia de todos los reinos con
 * cada ficha.
 */
public record PaquetePedirArbol(int kx, int kz, int persona) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PaquetePedirArbol> TIPO =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "pedir_arbol"));
	public static final StreamCodec<FriendlyByteBuf, PaquetePedirArbol> CODEC = CustomPacketPayload.codec((p, buf) -> {
		buf.writeVarInt(p.kx());
		buf.writeVarInt(p.kz());
		buf.writeVarInt(p.persona());
	}, buf -> new PaquetePedirArbol(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

	/** El árbol del rey de hoy del reino. */
	public static PaquetePedirArbol delRey(int kx, int kz) {
		return new PaquetePedirArbol(kx, kz, Persona.NADIE);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TIPO;
	}
}
