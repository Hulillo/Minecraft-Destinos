package es.hulillo.destinos;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * El tablón de encargos: dos postes con una tabla llena de papeles clavados. Sale solo junto a la campana de los
 * pueblos y de las capitales (TablonesPueblos); al usarlo se abre la pantalla con los encargos del día.
 */
public final class TablonBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<TablonBlock> CODEC = simpleCodec(TablonBlock::new);
	private static final VoxelShape NORTE_SUR = Block.box(0, 0, 6, 16, 16, 10);
	private static final VoxelShape ESTE_OESTE = Block.box(6, 0, 0, 10, 16, 16);
	public static Block TABLON;

	public TablonBlock(BlockBehaviour.Properties p) {
		super(p);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	static void registra() {
		Identifier id = Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "tablon");
		ResourceKey<Block> clave = ResourceKey.create(Registries.BLOCK, id);
		TABLON = Registry.register(BuiltInRegistries.BLOCK, clave, new TablonBlock(BlockBehaviour.Properties.of().setId(clave)
				.mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion().ignitedByLava()));
		ResourceKey<Item> item = ResourceKey.create(Registries.ITEM, id);
		Item objeto = Registry.register(BuiltInRegistries.ITEM, item,
				new BlockItem(TABLON, new Item.Properties().setId(item).useBlockDescriptionPrefix()));
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(e -> e.accept(objeto));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
		b.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTE_SUR : ESTE_OESTE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer sp) {
			EncargosMundo.abre(sp, pos);
		}
		return InteractionResult.SUCCESS;
	}
}
