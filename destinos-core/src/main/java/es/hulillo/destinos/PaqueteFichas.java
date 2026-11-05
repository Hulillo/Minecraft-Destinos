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
	 * capital (vacío si aún no se sabe) y si la ha perdido. senor: reino del que es vasallo (kx, kz) o null. fondo y
	 * escudo: su estandarte (id de DyeColor y Estandarte). union: el otro reino de su unión (o null) y su nombre.
	 * corte: el rey, su consorte, su heredero, la ley y sus nobles, ya escritos (v0.7 Gente); linaje: sus reyes, del
	 * último al primero.
	 */
	public record Reino(int kx, int kz, Component nombre, int poder, int habitantes, int ducados, int estabilidad, int puesto,
			Component capital, boolean capitalPerdida, int[] senor, List<int[]> guerras, List<int[]> aliados, int rep, String cultura,
			List<Component> cronica, int fondo, int escudo, int[] union, Component nombreUnion, List<Component> corte,
			List<Component> linaje) {
	}

	/**
	 * Un ducado: su nombre (con su tipo: "Ducado de X", "Marca de X", "Tierras de X"…), lo peligroso que es (0 seguro
	 * a 3 muy peligroso) y los animales y monstruos que salen en sus biomas.
	 */
	public record Nombre(int dcx, int dcz, Component nombre, int peligro, List<Component> animales, List<Component> peligros) {
	}

	private static void textos(RegistryFriendlyByteBuf buf, List<Component> l) {
		buf.writeVarInt(l.size());
		for (Component c : l) {
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, c);
		}
	}

	private static List<Component> textos(RegistryFriendlyByteBuf buf) {
		int n = cuenta(buf);
		List<Component> l = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			l.add(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
		}
		return l;
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
			buf.writeUtf(r.cultura());
			textos(buf, r.cronica());
			buf.writeVarInt(r.fondo() + 1);
			buf.writeVarInt(r.escudo() + 1);
			buf.writeBoolean(r.union() != null);
			if (r.union() != null) {
				buf.writeVarInt(r.union()[0]);
				buf.writeVarInt(r.union()[1]);
				ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, r.nombreUnion());
			}
			textos(buf, r.corte());
			textos(buf, r.linaje());
		}
		buf.writeVarInt(ducados.size());
		for (Nombre d : ducados) {
			buf.writeVarInt(d.dcx());
			buf.writeVarInt(d.dcz());
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, d.nombre());
			buf.writeByte(d.peligro());
			textos(buf, d.animales());
			textos(buf, d.peligros());
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
			int rep = buf.readVarInt();
			String cultura = buf.readUtf();
			List<Component> cronica = textos(buf);
			int fondo = buf.readVarInt() - 1;
			int escudo = buf.readVarInt() - 1;
			int[] union = null;
			Component nombreUnion = Component.empty();
			if (buf.readBoolean()) {
				union = new int[] {buf.readVarInt(), buf.readVarInt()};
				nombreUnion = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
			}
			List<Component> corte = textos(buf);
			List<Component> linaje = textos(buf);
			reinos.add(new Reino(kx, kz, nombre, poder, habitantes, ducados, estabilidad, puesto, capital, perdida, senor, guerras,
					aliados, rep, cultura, cronica, fondo, escudo, union, nombreUnion, corte, linaje));
		}
		int m = cuenta(buf);
		List<Nombre> ducados = new ArrayList<>(m);
		for (int i = 0; i < m; i++) {
			int dcx = buf.readVarInt();
			int dcz = buf.readVarInt();
			Component nombre = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
			int peligro = buf.readByte();
			List<Component> animales = textos(buf);
			ducados.add(new Nombre(dcx, dcz, nombre, peligro, animales, textos(buf)));
		}
		return new PaqueteFichas(anio, turno, reinos, ducados);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TIPO;
	}
}
