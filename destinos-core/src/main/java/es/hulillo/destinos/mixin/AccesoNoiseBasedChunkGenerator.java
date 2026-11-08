package es.hulillo.destinos.mixin;

import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * El recorrido de una columna del generador de arriba abajo que se para donde se le diga (TerrenoCapital): para saber
 * la altura del suelo y del agua de una capital no hace falta montar la columna entera hasta el fondo del mundo.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public interface AccesoNoiseBasedChunkGenerator {
	@Invoker("iterateNoiseColumn")
	OptionalInt destinos$recorre(LevelHeightAccessor alturas, RandomState azar, int x, int z, MutableObject<NoiseColumn> columna,
			Predicate<BlockState> parada);
}
