package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Servidor → cliente: los encargos en curso del jugador, para la línea de la pantalla (a dónde ir y cuánto falta). */
public record PaqueteEncargos(List<Activo> activos) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PaqueteEncargos> TIPO =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "encargos"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PaqueteEncargos> CODEC =
			CustomPacketPayload.codec(PaqueteEncargos::escribe, PaqueteEncargos::lee);

	/** Iconos: un encargo cualquiera, uno de mazmorra y una mazmorra despertada sola de noche (sin encargo). */
	public static final int ROMBO = 0;
	public static final int CALAVERA = 1;
	public static final int CALAVERA_NOCHE = 2;

	/** Para y0/y1 cuando no se sabe a qué altura está el sitio (los encargos que no son de mazmorra). */
	public static final int SIN_ALTURA = Integer.MIN_VALUE;

	/**
	 * Un encargo en curso: su título, el sitio (x, z), la hora del mundo a la que vence, su icono y, en las mazmorras,
	 * de qué altura a qué altura va (y0, y1), para saber si hay que subir o bajar.
	 */
	public record Activo(Component titulo, int x, int z, long vence, int icono, int y0, int y1) {
		public Activo(Component titulo, int x, int z, long vence, int icono) {
			this(titulo, x, z, vence, icono, SIN_ALTURA, SIN_ALTURA);
		}
	}

	private void escribe(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(activos.size());
		for (Activo a : activos) {
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, a.titulo());
			buf.writeVarInt(a.x());
			buf.writeVarInt(a.z());
			buf.writeVarLong(a.vence());
			buf.writeVarInt(a.icono());
			buf.writeInt(a.y0());
			buf.writeInt(a.y1());
		}
	}

	private static PaqueteEncargos lee(RegistryFriendlyByteBuf buf) {
		int n = buf.readVarInt();
		if (n < 0 || n > 64) {
			throw new IllegalArgumentException("Destinos Core: demasiados encargos en el paquete");
		}
		List<Activo> l = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			l.add(new Activo(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarInt(),
					buf.readInt(), buf.readInt()));
		}
		return new PaqueteEncargos(l);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TIPO;
	}
}
