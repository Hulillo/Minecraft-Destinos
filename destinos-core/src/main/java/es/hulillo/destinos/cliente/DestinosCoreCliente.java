package es.hulillo.destinos.cliente;

import com.mojang.blaze3d.platform.InputConstants;
import es.hulillo.destinos.DestinosCore;
import es.hulillo.destinos.MapaTerreno;
import es.hulillo.destinos.PaquetePolitica;
import es.hulillo.destinos.PaqueteTerritorio;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Lado cliente: guarda los territorios que manda el servidor para que los mapas los pinten. La tecla del mapa
 * político (K por defecto) o /destinosmapa alternar lo enciende y apaga.
 */
public final class DestinosCoreCliente implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Fabric ya entrega los paquetes en el hilo del cliente
		ClientPlayNetworking.registerGlobalReceiver(PaqueteTerritorio.TIPO, (paquete, contexto) -> TerritoriosCliente.recibe(paquete));
		ClientPlayNetworking.registerGlobalReceiver(PaquetePolitica.TIPO, (paquete, contexto) -> TerritoriosCliente.politica(paquete));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, cliente) -> cliente.execute(TerritoriosCliente::borra));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, contexto) ->
				dispatcher.register(ClientCommandManager.literal("destinosmapa").executes(c -> diagnostico(c.getSource()))
						.then(ClientCommandManager.literal("alternar").executes(c -> {
							alterna(c.getSource().getClient());
							return 1;
						}))));
		KeyMapping tecla = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.destinos_core.mapa_politico",
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
				KeyMapping.Category.register(Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "destinos"))));
		ClientTickEvents.END_CLIENT_TICK.register(cliente -> {
			while (tecla.consumeClick()) {
				alterna(cliente);
			}
		});
		// con un mapa abierto a pantalla completa las teclas no llegan al juego: se escucha en la pantalla del mapa
		ScreenEvents.AFTER_INIT.register((cliente, pantalla, ancho, alto) -> {
			if (esMapa(pantalla)) {
				ScreenKeyboardEvents.afterKeyPress(pantalla).register((p, evento) -> {
					if (tecla.matches(evento)) {
						alterna(cliente);
					}
				});
			}
		});
	}

	/** Pantallas de JourneyMap y Diorama (por paquete, sin depender de sus clases). Así no salta al escribir en otras. */
	private static boolean esMapa(Screen pantalla) {
		String clase = pantalla.getClass().getName();
		return clase.startsWith("journeymap.") || clase.startsWith("dev.diorama.");
	}

	private static void alterna(Minecraft cliente) {
		TerritoriosCliente.alterna();
		if (cliente.player != null) {
			boolean v = TerritoriosCliente.visible();
			cliente.player.displayClientMessage(Component.translatableWithFallback(
					v ? "destinos.mapa_politico.on" : "destinos.mapa_politico.off",
					v ? "Mapa político: activado" : "Mapa político: desactivado"), true);
		}
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
