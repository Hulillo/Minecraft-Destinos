package es.hulillo.destinos.cliente;

import es.hulillo.destinos.PaqueteTerritorio;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Lado cliente: guarda los territorios que manda el servidor para que los mapas los pinten. */
public final class DestinosCoreCliente implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Fabric ya entrega los paquetes en el hilo del cliente
		ClientPlayNetworking.registerGlobalReceiver(PaqueteTerritorio.TIPO, (paquete, contexto) -> TerritoriosCliente.recibe(paquete));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, cliente) -> cliente.execute(TerritoriosCliente::borra));
	}
}
