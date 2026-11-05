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
 * Servidor → cliente: el árbol familiar de una persona (v0.7 Gente), para la vista de árbol del mapa. kx, kz: el
 * reino desde cuya ficha se abrió. Los miembros vienen ya en el orden en que se pintan, fila a fila.
 */
public record PaqueteArbol(int kx, int kz, int centro, Component casa, List<Miembro> miembros) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PaqueteArbol> TIPO =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "arbol"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PaqueteArbol> CODEC =
			CustomPacketPayload.codec(PaqueteArbol::escribe, PaqueteArbol::lee);

	/**
	 * Una persona del árbol. generacion: -2 abuelos … +2 nietos. nace y muere: años del mundo (muere 0 si vive).
	 * padre, madre, conyuge: ids (Persona.NADIE si no hay). reino: si ha llevado corona; rey: si la lleva hoy.
	 */
	public record Miembro(int id, int generacion, Component nombre, Component pista, boolean mujer, int nace, int muere, int padre,
			int madre, int conyuge, boolean reino, boolean rey) {
	}

	private static void escribe(PaqueteArbol p, RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(p.kx());
		buf.writeVarInt(p.kz());
		buf.writeVarInt(p.centro());
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.casa());
		buf.writeVarInt(p.miembros().size());
		for (Miembro m : p.miembros()) {
			buf.writeVarInt(m.id());
			buf.writeVarInt(m.generacion());
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, m.nombre());
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, m.pista());
			buf.writeBoolean(m.mujer());
			buf.writeVarInt(m.nace());
			buf.writeVarInt(m.muere());
			buf.writeVarInt(m.padre());
			buf.writeVarInt(m.madre());
			buf.writeVarInt(m.conyuge());
			buf.writeBoolean(m.reino());
			buf.writeBoolean(m.rey());
		}
	}

	private static PaqueteArbol lee(RegistryFriendlyByteBuf buf) {
		int kx = buf.readVarInt();
		int kz = buf.readVarInt();
		int centro = buf.readVarInt();
		Component casa = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		int n = buf.readVarInt();
		if (n < 0 || n > 256) {
			throw new IllegalArgumentException("Destinos Core: árbol familiar demasiado grande");
		}
		List<Miembro> l = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			l.add(new Miembro(buf.readVarInt(), buf.readVarInt(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
					ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
		}
		return new PaqueteArbol(kx, kz, centro, casa, l);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TIPO;
	}
}
