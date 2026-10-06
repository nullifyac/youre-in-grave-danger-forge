package com.b1n_ry.yigd.gametest;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.compat.InvModCompat;
import com.b1n_ry.yigd.components.ExpComponent;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathContext;
import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.data.TranslatableDeathMessage;
import com.b1n_ry.yigd.util.DropRule;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Optional integration tests sharing GraveRegressionTests' isolated batch and cleanup. */
@GameTestHolder(Yigd.MOD_ID)
public final class AccessoriesRegressionTests {
    private static final String BATCH = "yigd_grave_regressions";
    private static final String TOKEN = "yigdAccessoriesRegressionToken";
    private static final BlockPos GRAVE_POS = new BlockPos(2, 1, 2);

    private AccessoriesRegressionTests() {}

    @GameTestGenerator
    public static Collection<TestFunction> accessoriesTests() {
        if (!ModList.get().isLoaded("accessories")) return List.of();
        return List.of(
                new TestFunction(BATCH, "yigd.accessories_death_nbt_restore", "yigd:grave_regression_empty",
                        30, 0, true, helper -> verifyRecovery(helper, false)),
                new TestFunction(BATCH, "yigd.accessories_real_grave_claim", "yigd:grave_regression_empty",
                        30, 0, true, helper -> verifyRecovery(helper, true)));
    }

    // Optional API types appear only inside the executed test body, never in class signatures.
    private static void verifyRecovery(GameTestHelper helper, boolean claimGrave) {
        var level = helper.getLevel();
        check(level.getServer() instanceof GameTestServer, "Use the disposable runGameTestServer world");
        check(YigdConfig.getConfig().compatConfig.enableAccessoriesCompat, "Accessories compatibility is disabled");
        check(YigdConfig.getConfig().compatConfig.defaultAccessoriesDropRule == DropRule.PUT_IN_GRAVE,
                "Accessory fixture requires PUT_IN_GRAVE death rules");
        check(InvModCompat.invCompatMods.stream().anyMatch(compat -> compat.getModName().equals("accessories")),
                "YIGD did not activate Accessories compatibility");

        GameProfile owner = new GameProfile(UUID.randomUUID(), "YigdAccessory");
        ServerPlayer player = new ServerPlayer(level.getServer(), level, owner);
        UUID token = UUID.randomUUID();
        ItemStack normal = stack(Items.DIAMOND, 2, token, "normal");
        ItemStack cosmetic = stack(Items.EMERALD, 3, token, "cosmetic");
        ItemStack vanilla = stack(Items.GOLD_INGOT, 4, token, "vanilla");
        player.getInventory().setItem(0, vanilla.copy());

        var capability = io.wispforest.accessories.api.AccessoriesCapability.get(player);
        check(capability != null, "Accessories capability is absent on the real ServerPlayer");
        var container = capability.getContainers().get("ring");
        check(container != null, "Loaded Accessories player data did not create ring slots");
        check(container.getAccessories().getContainerSize() > 0
                        && container.getCosmeticAccessories().getContainerSize() > 0,
                "Accessories fixture has no normal or cosmetic slot");
        // Direct slot assignment isolates YIGD persistence from accessory equip validators.
        container.getAccessories().setItem(0, normal.copy());
        container.getCosmeticAccessories().setItem(0, cosmetic.copy());
        container.renderOptions().set(0, false);
        container.markChanged(false);
        check(ItemStack.matches(container.getAccessories().getItem(0), normal), "Normal slot fixture is empty");
        check(ItemStack.matches(container.getCosmeticAccessories().getItem(0), cosmetic), "Cosmetic fixture is empty");

        InventoryComponent snapshot = new InventoryComponent(player);
        snapshot.onDeath(new DeathContext(player, level, player.position(), level.damageSources().generic()));
        CompoundTag saved = snapshot.toNbt();
        check(saved.getCompound("mods").contains("accessories"), "Death snapshot omitted nonempty accessory slots");
        InventoryComponent.clearPlayer(player);
        var cleared = io.wispforest.accessories.api.AccessoriesCapability.get(player).getContainers().get("ring");
        check(cleared.getAccessories().getItem(0).isEmpty(), "Normal accessory was not cleared on death");
        check(cleared.getCosmeticAccessories().getItem(0).isEmpty(), "Cosmetic accessory was not cleared on death");
        check(player.getInventory().isEmpty(), "Vanilla inventory was not cleared on death");

        InventoryComponent restored = InventoryComponent.fromNbt(saved.copy());
        BlockPos pos = helper.absolutePos(GRAVE_POS);
        if (claimGrave) {
            CompoundTag xp = new CompoundTag();
            xp.putInt("value", 7);
            xp.putDouble("original", 7);
            GraveComponent grave = new GraveComponent(owner, restored, ExpComponent.fromNbt(xp), level,
                    Vec3.atCenterOf(pos), new TranslatableDeathMessage("generic", owner.getName(),
                    null, null, null, null), null);
            grave.backUp();
            check(level.setBlockAndUpdate(pos, Yigd.GRAVE_BLOCK.defaultBlockState()), "Could not place the grave");
            check(level.getBlockEntity(pos) instanceof GraveBlockEntity, "Missing real grave block entity");
            GraveBlockEntity blockEntity = (GraveBlockEntity) level.getBlockEntity(pos);
            blockEntity.setPreviousState(Blocks.AIR.defaultBlockState());
            blockEntity.setComponent(grave);
            check(grave.claim(player, level, Blocks.AIR.defaultBlockState(), pos, ItemStack.EMPTY)
                    == InteractionResult.SUCCESS, "Owner could not claim the accessory grave");
            check(grave.getStatus() == GraveStatus.CLAIMED, "Accessory grave was not marked claimed");
            check(level.getBlockState(pos).isAir(), "Claimed accessory grave was not removed");
            check(player.totalExperience == 7, "Grave claim did not transfer exactly 7 XP");
        } else {
            check(restored.applyToPlayer(player).isEmpty(), "NBT restore unexpectedly produced overflow items");
            check(player.totalExperience == 0, "Inventory-only restore changed XP");
        }

        helper.runAfterDelay(2, () -> {
            var recovered = io.wispforest.accessories.api.AccessoriesCapability.get(player).getContainers().get("ring");
            check(ItemStack.matches(recovered.getAccessories().getItem(0), normal),
                    "Normal accessory tags/count were lost or duplicated");
            check(ItemStack.matches(recovered.getCosmeticAccessories().getItem(0), cosmetic),
                    "Cosmetic accessory tags/count were lost or duplicated");
            check(!recovered.shouldRender(0), "Hidden accessory render option was lost");
            check(ItemStack.matches(player.getInventory().getItem(0), vanilla), "Vanilla tags/count changed");
            check(player.getInventory().items.stream().filter(stack -> !stack.isEmpty()).count() == 1,
                    "Recovery put extra items in vanilla inventory");
            check(player.totalExperience == (claimGrave ? 7 : 0), "Deferred recovery duplicated or lost XP");
            check(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3), entity -> {
                var tag = entity.getItem().getTag();
                return tag != null && tag.hasUUID(TOKEN) && token.equals(tag.getUUID(TOKEN));
            }).isEmpty(), "Recovery also dropped fixture items on the ground");
            helper.succeed();
        });
    }

    private static ItemStack stack(net.minecraft.world.item.Item item, int count, UUID token, String role) {
        ItemStack stack = new ItemStack(item, count);
        stack.getOrCreateTag().putUUID(TOKEN, token);
        stack.getOrCreateTag().putString("role", role);
        return stack;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
