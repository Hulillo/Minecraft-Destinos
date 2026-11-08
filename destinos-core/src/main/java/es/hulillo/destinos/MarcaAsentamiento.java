package es.hulillo.destinos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * La señal de una capital o de un pueblo propio para el sistema de estructuras de Minecraft (/locate, RoadWeaver y
 * los mods que buscan pueblos): ocupa lo que ocupa el asentamiento pero no pone nada. Lo de verdad lo construye
 * ObrasMundo en cada chunk, así que si esta señal se pierde (C2ME, Structurify) el asentamiento sale igual.
 */
public final class MarcaAsentamiento extends StructurePiece {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) MarcaAsentamiento::new;

	MarcaAsentamiento(BoundingBox caja) {
		super(TIPO, 0, caja);
	}

	private MarcaAsentamiento(CompoundTag t) {
		super(TIPO, t);
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "marca"), TIPO);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag t) {
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager estructuras, ChunkGenerator generador, RandomSource azar, BoundingBox caja,
			ChunkPos chunk, BlockPos pivote) {
	}
}
