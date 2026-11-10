package es.hulillo.destinos.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import es.hulillo.destinos.ObrasMundo;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** El terreno de cada chunk se amolda también a las capitales y pueblos propios de cerca (ObrasMundo). */
@Mixin(Beardifier.class)
public abstract class MixinBeardifier {
	@ModifyExpressionValue(method = "forStructuresInChunk", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/StructureManager;startsForStructure(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Predicate;)Ljava/util/List;"))
	private static List<StructureStart> destinos$asentamientos(List<StructureStart> originales,
			@Local(argsOnly = true) StructureManager estructuras, @Local(argsOnly = true) ChunkPos chunk) {
		return ObrasMundo.amolda(estructuras, chunk, originales);
	}
}
