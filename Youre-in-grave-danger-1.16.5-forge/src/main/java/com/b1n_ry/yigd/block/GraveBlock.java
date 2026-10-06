package com.b1n_ry.yigd.block;


import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.config.GraveConfig;
import com.b1n_ry.yigd.config.GraveRenderingConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.data.TimePoint;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
// Removed Fabric FakePlayer import
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.IWaterLoggable;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.IWorld;
import net.minecraftforge.common.util.Constants;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class GraveBlock extends Block implements ITileEntityProvider, IWaterLoggable {
    private static VoxelShape SHAPE_EAST;
    private static VoxelShape SHAPE_WEST;
    private static VoxelShape SHAPE_SOUTH;
    private static VoxelShape SHAPE_NORTH;

    public GraveBlock(AbstractBlock.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH).setValue(BlockStateProperties.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.WATERLOGGED);
    }

    @Override
    public void setPlacedBy(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        TileEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof GraveBlockEntity && itemStack.hasCustomHoverName()) {
            GraveBlockEntity grave = (GraveBlockEntity) blockEntity;
            GraveComponent graveComponent = grave.getComponent();
            if (graveComponent == null) {
                grave.setGraveText(itemStack.getHoverName());
                grave.setChanged();
            }
        }
        super.setPlacedBy(world, pos, state, placer, itemStack);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockItemUseContext ctx) {
        Direction dir = ctx.getHorizontalDirection().getOpposite();  // Have the grave facing you, not away from you
        BlockState state = this.defaultBlockState();
        FluidState fluidState = ctx.getLevel().getFluidState(ctx.getClickedPos());
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING, dir).setValue(BlockStateProperties.WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, IWorld world, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            world.getLiquidTicks().scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }
        return direction.getAxis().isHorizontal() ? state : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @SuppressWarnings("deprecation")
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockRenderType getRenderShape(BlockState state) {
        GraveRenderingConfig config = YigdConfig.getConfig().graveRendering;
        return config.useCustomFeatureRenderer ? BlockRenderType.INVISIBLE : BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public TileEntity newBlockEntity(IBlockReader world) {
        return new GraveBlockEntity();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        Direction direction = state.getValue(BlockStateProperties.HORIZONTAL_FACING);

        switch (direction) {
            case EAST:
                return SHAPE_EAST;
            case WEST:
                return SHAPE_WEST;
            case SOUTH:
                return SHAPE_SOUTH;
            default:
                return SHAPE_NORTH;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        YigdConfig config = YigdConfig.getConfig();
        if (!(player instanceof ServerPlayerEntity) || player instanceof net.minecraftforge.common.util.FakePlayer) return ActionResultType.PASS;
        TileEntity blockEntity = world.getBlockEntity(pos);
        if (!world.isClientSide && blockEntity instanceof GraveBlockEntity) {
            GraveBlockEntity grave = (GraveBlockEntity) blockEntity;
            GraveComponent graveComponent = grave.getComponent();

            if (graveComponent == null) {
                // Check if it actually *is* not a personal grave, or if the component value is just missing
                UUID graveId = grave.getGraveId();
                if (graveId != null) {
                    Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(graveId);
                    if (component.isPresent())
                        graveComponent = component.get();
                }

                if (graveComponent == null)
                    // It was indeed just a normal grave, belonging to no one whatsoever
                    return this.interactWithNonPlayerGrave(grave, state, world, pos, player, hand, hit);
            }

            if (config.graveConfig.persistentGraves.enabled && graveComponent.getStatus() == GraveStatus.CLAIMED && hand == Hand.MAIN_HAND) {
                IFormattableTextComponent message = graveComponent.getDeathMessage().getDeathMessage().copy();

                TimePoint creationTime = graveComponent.getCreationTime();
                if (config.graveConfig.persistentGraves.showDeathDay)
                    message.append(new TranslationTextComponent("text.yigd.message.on_day", creationTime.getDay()));
                if (config.graveConfig.persistentGraves.showDeathIrlTime)
                    message.append(new TranslationTextComponent("text.yigd.message.irl_time",
                            creationTime.getMonthName(),
                            creationTime.getDate(),
                            creationTime.getYear(),
                            creationTime.getHour(config.graveConfig.persistentGraves.useAmPm),
                            creationTime.getMinute(),
                            creationTime.getTimePostfix(config.graveConfig.persistentGraves.useAmPm)
                    ));

                player.sendMessage(message, Util.NIL_UUID);
                return ActionResultType.SUCCESS;
            }

            // If it's not on the client side, player and world should safely be able to be cast into their serverside counterpart classes
            if (config.graveConfig.retrieveMethods.onClick)
                return graveComponent.claim((ServerPlayerEntity) player, (ServerWorld) world, grave.getPreviousState(), pos, player.getItemInHand(hand));
        }
        return ActionResultType.FAIL;
    }
    private ActionResultType interactWithNonPlayerGrave(GraveBlockEntity grave, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult ignoredHit) {
        if (player.isShiftKeyDown()) return ActionResultType.FAIL;

        ItemStack stack = player.getItemInHand(hand);
        CompoundNBT nbt = stack.getTag();
        INBT skullOwnerNbt;
        if (stack.getItem() == Items.PLAYER_HEAD && nbt != null && (skullOwnerNbt = nbt.get("SkullOwner")) != null) {
            byte nbtType = skullOwnerNbt.getId();
            GameProfile profile;
            switch (nbtType) {
                case Constants.NBT.TAG_STRING:
                    profile = new GameProfile(null, skullOwnerNbt.getAsString());
                    break;
                case Constants.NBT.TAG_COMPOUND:
                    profile = NBTUtil.readGameProfile((CompoundNBT) skullOwnerNbt);
                    break;
                default:
                    profile = null;
                    break;
            }

            grave.setGraveSkull(profile);  // Works since profile is nullable
            grave.setChanged();
            world.sendBlockUpdated(pos, state, state, Constants.BlockFlags.DEFAULT);

            if (!player.isCreative())
                stack.shrink(1);

            return ActionResultType.SUCCESS;
        }
        return ActionResultType.PASS;
    }

    @Override
    public void stepOn(World world, BlockPos pos, Entity entity) {
        if (!world.isClientSide && entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            GraveConfig graveConfig = YigdConfig.getConfig().graveConfig;
            if (graveConfig.retrieveMethods.onStand || (graveConfig.retrieveMethods.onSneak && player.isShiftKeyDown())) {
                TileEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity instanceof GraveBlockEntity) {
                    GraveBlockEntity grave = (GraveBlockEntity) blockEntity;
                    GraveComponent graveComponent = grave.getComponent();

                    if (graveComponent == null) {
                        // Check if it actually *is* not a personal grave, or if the component value is just missing
                        UUID graveId = grave.getGraveId();
                        if (graveId != null) {
                            Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(graveId);
                            if (component.isPresent())
                                graveComponent = component.get();
                        }
                    }

                    if (graveComponent != null && graveComponent.getStatus() != GraveStatus.CLAIMED) {
                        graveComponent.claim(player, (ServerWorld) world, grave.getPreviousState(), pos, player.getMainHandItem());
                    }
                }
            }
        }

        super.stepOn(world, pos, entity);
    }

    @Override
    public boolean canEntityDestroy(BlockState state, IBlockReader world, BlockPos pos, Entity entity) {
        // Entity attacks bypass mining hardness; protect graves that still contain a player's loot.
        TileEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof GraveBlockEntity) {
            GraveBlockEntity grave = (GraveBlockEntity) blockEntity;
            if (grave.getGraveId() != null && grave.isUnclaimed()) {
                return false;
            }
        }
        return super.canEntityDestroy(state, world, pos, entity);
    }

    @Override
    public void onRemove(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!world.isClientSide && !isMoving && state.getBlock() != newState.getBlock()) {
            TileEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof GraveBlockEntity) {
                ((GraveBlockEntity) blockEntity).onBroken();
            }
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    public void playerDestroy(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable TileEntity blockEntity, ItemStack tool) {
        YigdConfig config = YigdConfig.getConfig();
        if (!world.isClientSide && blockEntity instanceof GraveBlockEntity) {
            GraveBlockEntity grave = (GraveBlockEntity) blockEntity;
            if (grave.getComponent() != null && grave.getComponent().getStatus() != GraveStatus.CLAIMED) {
                if (config.graveConfig.retrieveMethods.onBreak) {
                    ActionResultType claimResult = grave.getComponent().claim((ServerPlayerEntity) player, (ServerWorld) world, grave.getPreviousState(), pos, tool);
                    if (claimResult != ActionResultType.FAIL)
                        return;
                }
                world.setBlock(pos, state, Constants.BlockFlags.DEFAULT);
                TileEntity replacedEntity = world.getBlockEntity(pos);
                if (replacedEntity instanceof GraveBlockEntity) {
                    GraveBlockEntity graveBlockEntity = (GraveBlockEntity) replacedEntity;

                    graveBlockEntity.setPreviousState(grave.getPreviousState());
                    Optional<GraveComponent> component = DeathInfoManager.INSTANCE.getGrave(grave.getGraveId());
                    component.ifPresent(graveBlockEntity::setComponent);

                    Yigd.END_OF_TICK.add(() -> {  // Required because it might take a tick for the game to realize the block is replaced
                        graveBlockEntity.setChanged();
                        world.sendBlockUpdated(pos, state, state, Constants.BlockFlags.DEFAULT);
                    });

                    return;
                }
            }
        }
        super.playerDestroy(world, player, pos, state, blockEntity, tool);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(BlockState state, PlayerEntity player, IBlockReader world, BlockPos pos) {
        TileEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof GraveBlockEntity) || !((GraveBlockEntity) blockEntity).isUnclaimed()
                || YigdConfig.getConfig().graveConfig.retrieveMethods.onBreak) {
            // Same calculations as done for "normal" blocks, except with the overwritten destroy speed of 0.8
            float f = 0.8f;
            int i = player.hasCorrectToolForDrops(state) ? 30 : 100;
            return player.getDestroySpeed(state) / f / (float) i;
        }
        return super.getDestroyProgress(state, player, world, pos);
    }

    public static void reloadShapeFromJson(JsonObject json) throws IllegalStateException {
        List<VoxelShape> voxelShapesNorth = new ArrayList<>();
        List<VoxelShape> voxelShapesSouth = new ArrayList<>();
        List<VoxelShape> voxelShapesEast = new ArrayList<>();
        List<VoxelShape> voxelShapesWest = new ArrayList<>();

        JsonArray elements = json.getAsJsonArray("elements");
        for (JsonElement element : elements) {
            JsonObject o = element.getAsJsonObject();
            JsonArray from = o.getAsJsonArray("from");
            JsonArray to = o.getAsJsonArray("to");

            double x1 = from.get(0).getAsDouble() / 16D;
            double y1 = from.get(1).getAsDouble() / 16D;
            double z1 = from.get(2).getAsDouble() / 16D;
            double x2 = to.get(0).getAsDouble() / 16D;
            double y2 = to.get(1).getAsDouble() / 16D;
            double z2 = to.get(2).getAsDouble() / 16D;

            voxelShapesNorth.add(VoxelShapes.box(x1, y1, z1, x2, y2, z2));
            voxelShapesEast.add(VoxelShapes.box(1 - z2, y1, x1, 1 - z1, y2, x2));
            voxelShapesSouth.add(VoxelShapes.box(1 - x2, y1, 1 - z2, 1 - x1, y2, 1 - z1));
            voxelShapesWest.add(VoxelShapes.box(z1, y1, 1 - x2, z2, y2, 1 - x1));
        }

        if (voxelShapesNorth.isEmpty()) return;  // This should never happen. If it does, we have a problem. Although here we just make the problem not happen
        SHAPE_NORTH = voxelShapesNorth.remove(0);
        SHAPE_EAST = voxelShapesEast.remove(0);
        SHAPE_SOUTH = voxelShapesSouth.remove(0);
        SHAPE_WEST = voxelShapesWest.remove(0);
        voxelShapesNorth.forEach(shape -> SHAPE_NORTH = VoxelShapes.or(SHAPE_NORTH, shape));
        voxelShapesEast.forEach(shape -> SHAPE_EAST = VoxelShapes.or(SHAPE_EAST, shape));
        voxelShapesSouth.forEach(shape -> SHAPE_SOUTH = VoxelShapes.or(SHAPE_SOUTH, shape));
        voxelShapesWest.forEach(shape -> SHAPE_WEST = VoxelShapes.or(SHAPE_WEST, shape));
    }

    static {
        VoxelShape bottom = VoxelShapes.box(0, 0, 0, 1, 1D / 16D, 1);
        VoxelShape supportEast = VoxelShapes.box(1D / 16D, 1D / 16D, 2D / 16D, 6D / 16D, 3D / 16D, 14D / 16D);
        VoxelShape bustEast = VoxelShapes.box(2D / 16D, 3D / 16D, 3D / 16D, 5D / 16D, 15D / 16D, 13D / 16D);
        VoxelShape topEast = VoxelShapes.box(2D / 16D, 15D / 16D, 4D / 16D, 5D / 16D, 1, 12D / 16D);

        VoxelShape supportWest = VoxelShapes.box(10D / 16D, 1D / 16D, 2D / 16D, 15D / 16D, 3D / 16D, 14D / 16D);
        VoxelShape bustWest = VoxelShapes.box(11D / 16D, 3D / 16D, 3D / 16D, 14D / 16D, 15D / 16D, 13D / 16D);
        VoxelShape topWest = VoxelShapes.box(11D / 16D, 15D / 16D, 4D / 16D, 14D / 16D, 1, 12D / 16D);

        VoxelShape supportSouth = VoxelShapes.box(2D / 16D, 1D / 16D, 1D / 16D, 14D / 16D, 3D / 16D, 6D / 16D);
        VoxelShape bustSouth = VoxelShapes.box(3D / 16D, 3D / 16D, 2D / 16D, 13D / 16D, 15D / 16D, 5D / 16D);
        VoxelShape topSouth = VoxelShapes.box(4D / 16D, 15D / 16D, 2D / 16D, 12D / 16D, 1, 5D / 16D);

        VoxelShape supportNorth = VoxelShapes.box(2D / 16D, 1D / 16D, 10D / 16D, 14D / 16D, 3D / 16D, 15D / 16D);
        VoxelShape bustNorth = VoxelShapes.box(3D / 16D, 3D / 16D, 11D / 16D, 13D / 16D, 15D / 16D, 14D / 16D);
        VoxelShape topNorth = VoxelShapes.box(4D / 16D, 15D / 16D, 11D / 16D, 12D / 16D, 1, 14D / 16D);

        SHAPE_EAST = VoxelShapes.or(bottom, supportEast, bustEast, topEast);
        SHAPE_WEST = VoxelShapes.or(bottom, supportWest, bustWest, topWest);
        SHAPE_SOUTH = VoxelShapes.or(bottom, supportSouth, bustSouth, topSouth);
        SHAPE_NORTH = VoxelShapes.or(bottom, supportNorth, bustNorth, topNorth);
    }
}
