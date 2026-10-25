package es.hulillo.destinos.cliente.journeymap;

import es.hulillo.destinos.DestinosCore;
import es.hulillo.destinos.cliente.TerritoriosCliente;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;

/**
 * Enchufe para JourneyMap (entrypoint "journeymap" de fabric.mod.json). JourneyMap solo carga esta clase si
 * está instalado, así que nada más del mod debe nombrarla.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class PluginJourneyMap implements IClientPlugin {
	@Override
	public void initialize(IClientAPI api) {
		TerritoriosCliente.escucha(new CapaJourneyMap(api));
		DestinosCore.LOG.info("Destinos Core: reinos pintados en JourneyMap");
	}

	@Override
	public String getModId() {
		return DestinosCore.MOD_ID;
	}
}
