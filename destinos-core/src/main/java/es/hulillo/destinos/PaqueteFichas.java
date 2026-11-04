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
 * Servidor → cliente: lo que el mapa político enseña al pasar el ratón y en la ficha de cada reino. Los nombres de
 * los reinos y ducados conocidos (son del datapack, el cliente no los sabe), y de cada reino su poder, gente,
 * estabilidad, capital, guerras, aliados y la reputación que le tiene el jugador.
 */
public record PaqueteFichas(int anio, int turno, List<Reino> reinos, List<Nombre> ducados) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PaqueteFichas> TIPO =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "fichas"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PaqueteFichas> CODEC =
			CustomPacketPayload.codec(PaqueteFichas::escribe, PaqueteFichas::lee);
	private static final int MAXIMO = 100000;

	/**
	 * Un reino vivo. puesto: lugar en el ranking de poder (1 = el más fuerte). capital: nombre del ducado de su
	 * capital (vacío si aún no se sabe) y si la ha perdido. senor: reino del que es vasallo (kx, kz) o null.
	 */
	public record Reino(int kx, int kz, Component nombre, int poder, int habitantes, int ducados, int estabilidad, int puesto,
			Component capital, boolean capitalPerdida, int[] senor, List<int[]> guerras, List<int[]> aliados, int rep) {
	}

	/** Nombre de un ducado (con su tipo: "Ducado de X", "Marca de X", "Tierras de X"…). */
	public record Nombre(int dcx, int dcz, Component nombre) {
	}

	private static void celdas(RegistryFriendlyByteBuf buf, List<int[]> l) {
		buf.writeVarInt(l.size());
		for (int[] c : l) {
			buf.writeVarInt(c[0]);
			buf.writeVarInt(c[1]);
		}
	}

	private static List<int[]> celdas(RegistryFriendlyByteBuf buf) {
		int n = cuenta(buf);
		List<int[]> l = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			l.add(new int[] {buf.readVarInt(), buf.readVarInt()});
		}
		return l;
	}

	private static int cuenta(RegistryFriendlyByteBuf buf) {
		int n = buf.readVarInt();
		if (n < 0 || n > MAXIMO) {
			throw new IllegalArgumentException("Destinos Core: lista demasiado larga en las fichas del mapa");
		}
		return n;
	}

	private void escribe(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(anio);
		buf.writeVarInt(turno);
		buf.writeVarInt(reinos.size());
		for (Reino r : reinos) {
			buf.writeVarInt(r.kx());
			buf.writeVarInt(r.kz());
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, r.nombre());
			buf.writeVarInt(r.poder());
			buf.writeVarInt(r.habitantes());
			buf.writeVarInt(r.ducados());
			buf.writeVarInt(r.estabilidad());
			buf.writeVarInt(r.puesto());
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, r.capital());
			buf.writeBoolean(r.capitalPerdida());
			buf.writeBoolean(r.senor() != null);
			if (r.senor() != null) {
				buf.writeVarInt(r.senor()[0]);
				buf.writeVarInt(r.senor()[1]);
			}
			celdas(buf, r.guerras());
			celdas(buf, r.aliados());
			buf.writeVarInt(r.rep());
		}
		buf.writeVarInt(ducados.size());
		for (Nombre d : ducados) {
			buf.writeVarInt(d.dcx());
			buf.writeVarInt(d.dcz());
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, d.nombre());
		}
	}

	private static PaqueteFichas lee(RegistryFriendlyByteBuf buf) {
		int anio = buf.readVarInt();
		int turno = buf.readVarInt();
		int n = cuenta(buf);
		List<Reino> reinos = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			int kx = buf.readVarInt();
			int kz = buf.readVarInt();
			Component nombre = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
			int poder = buf.readVarInt();
			int habitantes = buf.readVarInt();
			int ducados = buf.readVarInt();
			int estabilidad = buf.readVarInt();
			int puesto = buf.readVarInt();
			Component capital = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
			boolean perdida = buf.readBoolean();
			int[] senor = buf.readBoolean() ? new int[] {buf.readVarInt(), buf.readVarInt()} : null;
			List<int[]> guerras = celdas(buf);
			List<int[]> aliados = celdas(buf);
			reinos.add(new Reino(kx, kz, nombre, poder, habitantes, ducados, estabilidad, puesto, capital, perdida, senor, guerras,
					aliados, buf.readVarInt()));
		}
		int m = cuenta(buf);
		List<Nombre> ducados = new ArrayList<>(m);
		for (int i = 0; i < m; i++) {
			ducados.add(new Nombre(buf.readVarInt(), buf.readVarInt(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf)));
		}
		return new PaqueteFichas(anio, turno, reinos, ducados);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TIPO;
	}
}
