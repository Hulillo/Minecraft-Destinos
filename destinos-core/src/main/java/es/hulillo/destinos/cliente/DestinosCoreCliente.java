package es.hulillo.destinos.cliente;

import es.hulillo.destinos.MapaTerreno;
import es.hulillo.destinos.PaqueteTerritorio;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

/** Lado cliente: guarda los territorios que manda el servidor para que los mapas los pinten. */
public final class DestinosCoreCliente implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Fabric ya entrega los paquetes en el hilo del cliente
		ClientPlayNetworking.registerGlobalReceiver(PaqueteTerritorio.TIPO, (paquete, contexto) -> TerritoriosCliente.recibe(paquete));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, cliente) -> cliente.execute(TerritoriosCliente::borra));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, contexto) ->
				dispatcher.register(ClientCommandManager.literal("destinosmapa").executes(c -> diagnostico(c.getSource()))));
	}

	/** /destinosmapa: qué sabe el cliente del territorio donde estás y qué pintó el mapa la última vez. */
	private static int diagnostico(FabricClientCommandSource fuente) {
		int tx = Math.floorDiv(fuente.getPlayer().getBlockX(), MapaTerreno.CASILLA);
		int tz = Math.floorDiv(fuente.getPlayer().getBlockZ(), MapaTerreno.CASILLA);
		PaqueteTerritorio.Ducado d = TerritoriosCliente.ducado(tx, tz);
		String aqui = d == null
				? "sin datos aquí todavía"
				: "ducado " + d.dcx() + "," + d.dcz() + " · reino " + d.kx() + "," + d.kz()
						+ (d.salvaje() ? " · salvaje" : "") + (d.oceano() ? (d.internacional() ? " · mar internacional" : " · aguas territoriales") : "")
						+ " · color #" + String.format("%06x", d.color()) + " · altura " + TerritoriosCliente.altura(tx, tz);
		fuente.sendFeedback(Component.literal("Destinos Core: " + TerritoriosCliente.zonas() + " zonas recibidas · " + aqui));
		fuente.sendFeedback(Component.literal(TerritoriosCliente.ultimoPintado));
		return 1;
	}
}
