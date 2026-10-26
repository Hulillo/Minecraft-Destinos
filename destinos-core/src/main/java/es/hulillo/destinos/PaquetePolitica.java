package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Servidor → cliente: el estado político que no va en las zonas. El turno de ahora (para marcar en el mapa los
 * ducados que acaban de cambiar de dueño) y las guerras abiertas (para pintar los frentes en rojo).
 */
public record PaquetePolitica(int turno, List<Guerra> guerras) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PaquetePolitica> TIPO =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "politica"));
	public static final StreamCodec<FriendlyByteBuf, PaquetePolitica> CODEC =
			CustomPacketPayload.codec(PaquetePolitica::escribe, PaquetePolitica::lee);
	/** Un ducado se marca en el mapa durante estos turnos después de cambiar de dueño. */
	public static final int TURNOS_RECIENTE = 3;

	/** Guerra entre los reinos de celda (akx, akz) y (bkx, bkz). */
	public record Guerra(int akx, int akz, int bkx, int bkz) {
	}

	private void escribe(FriendlyByteBuf buf) {
		buf.writeVarInt(turno);
		buf.writeVarInt(guerras.size());
		for (Guerra g : guerras) {
			buf.writeVarInt(g.akx());
			buf.writeVarInt(g.akz());
			buf.writeVarInt(g.bkx());
			buf.writeVarInt(g.bkz());
		}
	}

	private static PaquetePolitica lee(FriendlyByteBuf buf) {
		int turno = buf.readVarInt();
		int n = buf.readVarInt();
		if (n < 0 || n > 100000) {
			throw new IllegalArgumentException("Destinos Core: demasiadas guerras en el paquete");
		}
		List<Guerra> guerras = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			guerras.add(new Guerra(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
		}
		return new PaquetePolitica(turno, guerras);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TIPO;
	}
}
