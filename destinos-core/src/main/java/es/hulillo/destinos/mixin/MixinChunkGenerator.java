package es.hulillo.destinos.mixin;

import es.hulillo.destinos.ObrasMundo;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Al empezar la decoración de cada chunk (antes de los árboles), su trozo de las capitales y pueblos propios. */
@Mixin(ChunkGenerator.class)
public abstract class MixinChunkGenerator {
	@Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
	private void destinos$asentamientos(WorldGenLevel level, ChunkAccess chunk, StructureManager estructuras, CallbackInfo ci) {
		ObrasMundo.construye(level, chunk, estructuras, (ChunkGenerator) (Object) this);
	}
}
