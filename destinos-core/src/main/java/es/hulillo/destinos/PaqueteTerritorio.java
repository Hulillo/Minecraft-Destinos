package es.hulillo.destinos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Servidor → cliente: los ducados de una zona ya calculada (núcleo de una ventana de MapaTerreno), para que
 * los mapas del cliente (JourneyMap, Diorama) pinten los reinos. Solo lo reciben clientes con Destinos Core.
 */
public record PaqueteTerritorio(int sal, int wx, int wz, List<Ducado> paleta, short[] indice, short[] altura)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PaqueteTerritorio> TIPO =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "territorio"));
	public static final StreamCodec<FriendlyByteBuf, PaqueteTerritorio> CODEC =
			CustomPacketPayload.codec(PaqueteTerritorio::escribe, PaqueteTerritorio::lee);
	/** Las tierras salvajes se pintan de gris piedra: sin señor, sin color de casa. */
	public static final int COLOR_SALVAJE = 0x8a8577;
	private static final int CASILLAS = MapaTerreno.NUCLEO * MapaTerreno.NUCLEO;

	/**
	 * Un ducado tal y como se pinta: su reino, si es salvaje o mar (y si ese mar es internacional, lejos de
	 * toda costa) y el color de su reino (RGB).
	 */
	public record Ducado(int dcx, int dcz, int kx, int kz, boolean salvaje, boolean oceano, boolean internacional, int color) {
		static Ducado de(MapaTerreno.Entrada e, int sal, UnaryOperator<MapaTerreno.Resultado> politica) {
			MapaTerreno.Resultado r = politica.apply(e.territorio());
			int color = r.salvaje() ? COLOR_SALVAJE : Geo.colorReino(r.kx(), r.kz(), sal);
			return new Ducado(r.dcx(), r.dcz(), r.kx(), r.kz(), r.salvaje(), r.oceano(), e.internacional(), color);
		}

		/** Mar de nadie: no se pinta. */
		public boolean deNadie() {
			return oceano && internacional;
		}

		public boolean mismoReino(Ducado o) {
			return salvaje == o.salvaje && kx == o.kx && kz == o.kz;
		}

		public boolean mismoDucado(Ducado o) {
			return dcx == o.dcx && dcz == o.dcz && mismoReino(o);
		}
	}

	/** politica: pone encima el dueño actual de cada ducado (conquistas, crisis…). */
	public static PaqueteTerritorio de(MapaTerreno.Zona z, int sal, UnaryOperator<MapaTerreno.Resultado> politica) {
		List<Ducado> paleta = new ArrayList<>(z.paleta().size());
		for (MapaTerreno.Entrada e : z.paleta()) {
			paleta.add(Ducado.de(e, sal, politica));
		}
		return new PaqueteTerritorio(sal, z.wx(), z.wz(), paleta, z.indice(), z.altura());
	}

	/** Ducado de la casilla (i, j) del núcleo, contando desde la esquina noroeste. */
	public Ducado ducado(int i, int j) {
		return paleta.get(indice[i + j * MapaTerreno.NUCLEO]);
	}

	public int altura(int i, int j) {
		return altura[i + j * MapaTerreno.NUCLEO];
	}

	private void escribe(FriendlyByteBuf buf) {
		buf.writeInt(sal);
		buf.writeVarInt(wx);
		buf.writeVarInt(wz);
		buf.writeVarInt(paleta.size());
		for (Ducado d : paleta) {
			buf.writeVarInt(d.dcx());
			buf.writeVarInt(d.dcz());
			buf.writeVarInt(d.kx());
			buf.writeVarInt(d.kz());
			buf.writeByte((d.salvaje() ? 1 : 0) | (d.oceano() ? 2 : 0) | (d.internacional() ? 4 : 0));
			buf.writeInt(d.color());
		}
		for (int k = 0; k < CASILLAS; k++) {
			buf.writeVarInt(indice[k]);
			buf.writeShort(altura[k]);
		}
	}

	private static PaqueteTerritorio lee(FriendlyByteBuf buf) {
		int sal = buf.readInt();
		int wx = buf.readVarInt();
		int wz = buf.readVarInt();
		int n = buf.readVarInt();
		List<Ducado> paleta = new ArrayList<>(n);
		for (int k = 0; k < n; k++) {
			int dcx = buf.readVarInt();
			int dcz = buf.readVarInt();
			int kx = buf.readVarInt();
			int kz = buf.readVarInt();
			int flags = buf.readByte();
			paleta.add(new Ducado(dcx, dcz, kx, kz, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, buf.readInt()));
		}
		short[] indice = new short[CASILLAS];
		short[] altura = new short[CASILLAS];
		for (int k = 0; k < CASILLAS; k++) {
			int i = buf.readVarInt();
			if (i < 0 || i >= n) {
				throw new IllegalArgumentException("Destinos Core: casilla con ducado fuera de la paleta");
			}
			indice[k] = (short) i;
			altura[k] = buf.readShort();
		}
		return new PaqueteTerritorio(sal, wx, wz, paleta, indice, altura);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TIPO;
	}
}
