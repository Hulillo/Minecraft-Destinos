package es.hulillo.destinos.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import es.hulillo.destinos.TiemposPiezas;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mide lo que tarda cada pieza en ponerse (TiemposPiezas). */
@Mixin(StructureStart.class)
public abstract class MixinStructureStart {
	@WrapOperation(method = "placeInChunk", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/structure/StructurePiece;postProcess(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/core/BlockPos;)V"))
	private void destinos$mide(StructurePiece pieza, WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador,
			RandomSource azar, BoundingBox caja, ChunkPos chunk, BlockPos pivote, Operation<Void> original) {
		long t = System.nanoTime();
		original.call(pieza, level, estructuras, generador, azar, caja, chunk, pivote);
		TiemposPiezas.suma(pieza, System.nanoTime() - t);
	}
}
