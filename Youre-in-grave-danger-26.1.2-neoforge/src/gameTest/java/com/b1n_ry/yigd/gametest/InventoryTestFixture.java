package com.b1n_ry.yigd.gametest;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.compat.InvModCompat;
import com.b1n_ry.yigd.components.ExpComponent;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.config.*;
import com.b1n_ry.yigd.data.DeathContext;
import com.b1n_ry.yigd.data.DeathInfoManager;
import com.b1n_ry.yigd.util.GraveOverrideAreas;
import com.b1n_ry.yigd.data.GraveStatus;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Isolates mutable YiGD state and creates an actual server player for inventory tests. */
final class InventoryTestFixture implements AutoCloseable {
    final GameTestHelper helper;
    final UUID marker = UUID.randomUUID();
    final Vec3 position;
    final NativeTestPlayer playerFixture;
    final ServerPlayer player;
    final List<GraveComponent> graves = new ArrayList<>();
    private final YigdConfig config = YigdConfig.getConfig();
    private final InventoryConfig oldInventory = config.inventoryConfig;
    private final ExpConfig oldExp = config.expConfig;
    private final GraveConfig oldGrave = config.graveConfig;
    private final CompatConfig oldCompat = config.compatConfig;
    private final ExtraFeaturesConfig oldExtra = config.extraFeatures;
    private final List<InvModCompat<?>> oldCompatMods = new ArrayList<>(InvModCompat.invCompatMods);
    private final GraveOverrideAreas oldAreas = GraveOverrideAreas.INSTANCE;
    private final DeathInfoManager oldManager = DeathInfoManager.INSTANCE;

    InventoryTestFixture(GameTestHelper helper) {
        if (!(helper.getLevel().getServer() instanceof GameTestServer)) {
            throw new IllegalStateException("Inventory fixtures require the isolated GameTest server");
        }
        this.helper = helper;
        this.position = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        this.playerFixture = NativeTestPlayer.create(helper, new GameProfile(marker, "yigd-inventory"));
        this.player = playerFixture.player();
        player.snapTo(position.x(), position.y(), position.z(), 0, 0);
        config.inventoryConfig = new InventoryConfig();
        config.expConfig = new ExpConfig();
        config.expConfig.dropBehaviour = ExpDropBehaviour.PERCENTAGE;
        config.expConfig.dropPercentage = 100;
        config.graveConfig = new GraveConfig();
        config.graveConfig.dropItemsIfDestroyed = false;
        config.compatConfig = new CompatConfig();
        config.extraFeatures = new ExtraFeaturesConfig();
        config.extraFeatures.graveKeys.enabled = false;
        InvModCompat.invCompatMods.clear();
        GraveOverrideAreas.INSTANCE = new GraveOverrideAreas();
        DeathInfoManager.INSTANCE = new DeathInfoManager();
        InventoryComponent.clearPlayer(player);
        ExpComponent.clearXp(player);
    }

    ItemStack stack(Item item, int count, String role) {
        ItemStack stack = new ItemStack(item, count);
        CompoundTag data = new CompoundTag();
        data.putString("yigd_regression", marker.toString());
        data.putString("role", role);
        data.putInt("payload", 2612);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("YiGD test " + role));
        return stack;
    }

    DeathContext deathContext() {
        return new DeathContext(player, helper.getLevel(), position, player.damageSources().generic());
    }

    InventoryComponent roundTrip(InventoryComponent inventory) {
        return InventoryComponent.fromNbt(inventory.toNbt(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
    }

    List<ItemStack> snapshot() {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            items.add(player.getInventory().getItem(slot).copy());
        }
        return items;
    }

    void assertInventory(List<ItemStack> expected) {
        helper.assertValueEqual(player.getInventory().getContainerSize(), expected.size(), "inventory slot count");
        for (int slot = 0; slot < expected.size(); slot++) {
            assertStack(player.getInventory().getItem(slot), expected.get(slot), "vanilla slot " + slot);
        }
        assertStack(player.getItemBySlot(EquipmentSlot.BODY), expected.get(41), "BODY equipment");
        assertStack(player.getItemBySlot(EquipmentSlot.SADDLE), expected.get(42), "SADDLE equipment");
    }

    void assertStack(ItemStack actual, ItemStack expected, String name) {
        helper.assertValueEqual(actual.getCount(), expected.getCount(), name + " count");
        helper.assertTrue(ItemStack.isSameItemSameComponents(actual, expected), name + " lost its item or data components");
    }

    List<ItemEntity> droppedItems() {
        AABB box = new AABB(BlockPos.containing(position)).inflate(4);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, entity -> {
            CustomData data = entity.getItem().get(DataComponents.CUSTOM_DATA);
            return data != null && marker.toString().equals(data.copyTag().getStringOr("yigd_regression", ""));
        });
    }

    GraveComponent grave(InventoryComponent inventory, ExpComponent exp) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        ResolvableProfile profile = ResolvableProfile.createResolved(player.getGameProfile());
        GraveComponent grave = new GraveComponent(profile, inventory, exp, helper.getLevel(), pos.getCenter(), Component.literal("Inventory regression"), null);
        graves.add(grave);
        DeathInfoManager.INSTANCE.addBackup(profile, grave);
        helper.getLevel().setBlock(pos, Yigd.GRAVE.get().defaultBlockState(), 3);
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof GraveBlockEntity, "Expected an actual grave block entity");
        GraveBlockEntity blockEntity = (GraveBlockEntity) helper.getLevel().getBlockEntity(pos);
        blockEntity.setComponent(grave);
        blockEntity.setPreviousState(Blocks.AIR.defaultBlockState());
        return grave;
    }

    @Override
    public void close() {
        try {
            for (GraveComponent grave : graves) {
                grave.setStatus(GraveStatus.CLAIMED);
                DeathInfoManager.INSTANCE.delete(grave.getGraveId());
                helper.getLevel().removeBlock(grave.getPos(), false);
            }
            for (ItemEntity entity : droppedItems()) entity.discard();
            InventoryComponent.clearPlayer(player);
            playerFixture.close();
        } finally {
            config.inventoryConfig = oldInventory;
            config.expConfig = oldExp;
            config.graveConfig = oldGrave;
            config.compatConfig = oldCompat;
            config.extraFeatures = oldExtra;
            InvModCompat.invCompatMods.clear();
            InvModCompat.invCompatMods.addAll(oldCompatMods);
            GraveOverrideAreas.INSTANCE = oldAreas;
            DeathInfoManager.INSTANCE = oldManager;
        }
    }
}
