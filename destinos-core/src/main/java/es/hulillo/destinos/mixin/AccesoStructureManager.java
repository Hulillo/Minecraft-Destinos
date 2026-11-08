package es.hulillo.destinos.mixin;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** El mundo de un StructureManager (para saber si un chunk que se genera es del mundo normal). */
@Mixin(StructureManager.class)
public interface AccesoStructureManager {
	@Accessor("level")
	LevelAccessor destinos$level();
}
