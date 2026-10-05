package com.b1n_ry.yigd.compat;

import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathContext;
import com.b1n_ry.yigd.data.GraveItem;
import com.b1n_ry.yigd.events.DropRuleEvent;
import com.b1n_ry.yigd.util.DropRule;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.impl.ExpandedSimpleContainer;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class AccessoriesCompat implements InvModCompat<Map<String, AccessoriesCompat.AccessoriesInventoryGroup>> {
    @Override
    public String getModName() {
        return "accessories";
    }

    @Override
    public void clear(ServerPlayer player) {
        AccessoriesCapability.getOptionally(player).ifPresent(inv -> inv.reset(false));
    }

    @Override
    public CompatComponent<Map<String, AccessoriesInventoryGroup>> load(CompoundTag nbt) {
        Map<String, AccessoriesInventoryGroup> inventory = new HashMap<>();

        for (String key : nbt.getAllKeys()) {
            CompoundTag slotNbt = nbt.getCompound(key);
            NonNullList<AccessoriesInventorySlot> normalSlot = InventoryComponent.listFromNbt(
                    slotNbt.getCompound("normal"),
                    this::readAccessoriesSlot,
                    AccessoriesInventorySlot.empty(true)
            );
            NonNullList<AccessoriesInventorySlot> cosmeticSlot = InventoryComponent.listFromNbt(
                    slotNbt.getCompound("cosmetic"),
                    this::readAccessoriesSlot,
                    AccessoriesInventorySlot.empty(true)
            );

            inventory.put(key, new AccessoriesInventoryGroup(normalSlot, cosmeticSlot));
        }
        return new AccessoriesCompatComponent(inventory);
    }

    @Override
    public CompatComponent<Map<String, AccessoriesInventoryGroup>> getNewComponent(ServerPlayer player) {
        return new AccessoriesCompatComponent(player);
    }

    private AccessoriesInventorySlot readAccessoriesSlot(CompoundTag itemNbt) {
        ItemStack stack = ItemStack.of(itemNbt);
        DropRule dropRule = readDropRule(itemNbt);
        boolean visible = !itemNbt.contains("visible") || itemNbt.getBoolean("visible");
        return new AccessoriesInventorySlot(stack, dropRule, visible);
    }

    private DropRule readDropRule(CompoundTag itemNbt) {
        DropRule defaultRule = YigdConfig.getConfig().compatConfig.defaultAccessoriesDropRule;
        if (itemNbt.contains("dropRule")) {
            String dropRuleString = itemNbt.getString("dropRule");
            if ("DEFAULT".equals(dropRuleString)) {
                return defaultRule;
            }
            return DropRule.valueOf(dropRuleString);
        }
        return defaultRule;
    }

    public record AccessoriesInventoryGroup(NonNullList<AccessoriesInventorySlot> normal, NonNullList<AccessoriesInventorySlot> cosmetic) {
        private void addAllNonEmptyToList(Collection<GraveItem> list) {
            this.addAllNonEmptyToList(list, slot -> new GraveItem(slot.graveItem.stack, slot.graveItem.dropRule));
        }

        private void addAllNonEmptyToStackList(Collection<ItemStack> list) {
            this.addAllNonEmptyToList(list, slot -> slot.graveItem.stack);
        }

        private <T> void addAllNonEmptyToList(Collection<T> list, Function<AccessoriesInventorySlot, T> mapFunc) {
            for (AccessoriesInventorySlot slot : this.normal) {
                if (!slot.graveItem.stack.isEmpty()) {
                    list.add(mapFunc.apply(slot));
                }
            }
            for (AccessoriesInventorySlot slot : this.cosmetic) {
                if (!slot.graveItem.stack.isEmpty()) {
                    list.add(mapFunc.apply(slot));
                }
            }
        }
    }

    public static class AccessoriesInventorySlot {
        public GraveItem graveItem;
        public boolean visible;

        public AccessoriesInventorySlot(ItemStack stack, DropRule dropRule, boolean visible) {
            this.graveItem = new GraveItem(stack, dropRule);
            this.visible = visible;
        }

        public static AccessoriesInventorySlot empty(boolean visible) {
            return new AccessoriesInventorySlot(ItemStack.EMPTY, DropRule.PUT_IN_GRAVE, visible);
        }
    }

    private static class AccessoriesCompatComponent extends CompatComponent<Map<String, AccessoriesInventoryGroup>> {
        public AccessoriesCompatComponent(ServerPlayer player) {
            super(player);
        }

        public AccessoriesCompatComponent(Map<String, AccessoriesInventoryGroup> inventory) {
            super(inventory);
        }

        @Override
        public Map<String, AccessoriesInventoryGroup> getInventory(ServerPlayer player) {
            Map<String, AccessoriesInventoryGroup> inventory = new HashMap<>();
            AccessoriesCapability capability = AccessoriesCapability.get(player);
            if (capability == null) return inventory;

            Map<String, AccessoriesContainer> containers = capability.getContainers();
            for (Map.Entry<String, AccessoriesContainer> entry : containers.entrySet()) {
                NonNullList<AccessoriesInventorySlot> normalSlot = NonNullList.create();
                NonNullList<AccessoriesInventorySlot> cosmeticSlot = NonNullList.create();

                AccessoriesContainer container = entry.getValue();
                ExpandedSimpleContainer normal = container.getAccessories();
                ExpandedSimpleContainer cosmetic = container.getCosmeticAccessories();
                for (int i = 0; i < normal.getContainerSize(); i++) {
                    boolean shouldRender = container.shouldRender(i);
                    normalSlot.add(new AccessoriesInventorySlot(normal.getItem(i).copy(), DropRule.PUT_IN_GRAVE, shouldRender));
                }
                for (int i = 0; i < cosmetic.getContainerSize(); i++) {
                    boolean shouldRender = container.shouldRender(i);

                    cosmeticSlot.add(new AccessoriesInventorySlot(cosmetic.getItem(i).copy(), DropRule.PUT_IN_GRAVE, shouldRender));
                }

                inventory.put(entry.getKey(), new AccessoriesInventoryGroup(normalSlot, cosmeticSlot));
            }
            return inventory;
        }

        @Override
        public NonNullList<GraveItem> merge(CompatComponent<?> mergingComponent, ServerPlayer merger) {
            NonNullList<GraveItem> extraItems = NonNullList.create();
            @SuppressWarnings("unchecked")
            Map<String, AccessoriesInventoryGroup> mergingInventory = (Map<String, AccessoriesInventoryGroup>) mergingComponent.inventory;

            for (Map.Entry<String, AccessoriesInventoryGroup> mergeEntry : mergingInventory.entrySet()) {
                String key = mergeEntry.getKey();
                if (!this.inventory.containsKey(key)) {
                    mergeEntry.getValue().addAllNonEmptyToList(extraItems);
                    continue;
                }
                AccessoriesInventoryGroup mergingGroup = mergeEntry.getValue();
                AccessoriesInventoryGroup thisGroup = this.inventory.get(key);

                for (int i = 0; i < mergingGroup.normal.size(); i++) {
                    AccessoriesInventorySlot mergingSlot = mergingGroup.normal.get(i);
                    ItemStack mergingStack = mergingSlot.graveItem.stack.copy();
                    if (mergingStack.isEmpty()) continue;

                    if (thisGroup.normal.size() <= i) {
                        extraItems.add(new GraveItem(mergingStack, mergingSlot.graveItem.dropRule));
                        continue;
                    }

                    AccessoriesInventorySlot currentSlot = thisGroup.normal.get(i);
                    ItemStack currentStack = currentSlot.graveItem.stack;
                    if (YigdConfig.getConfig().graveConfig.treatBindingCurse && !AccessoriesAPI.canUnequip(mergingStack, SlotReference.of(merger, key, i))) {
                        extraItems.add(new GraveItem(currentStack, currentSlot.graveItem.dropRule));
                        thisGroup.normal.set(i, new AccessoriesInventorySlot(mergingStack, mergingSlot.graveItem.dropRule, mergingSlot.visible));
                        continue;
                    }
                    if (!currentStack.isEmpty()) {
                        extraItems.add(new GraveItem(mergingStack, mergingSlot.graveItem.dropRule));
                        continue;
                    }

                    thisGroup.normal.set(i, mergingSlot);
                }
                for (int i = 0; i < mergingGroup.cosmetic.size(); i++) {
                    AccessoriesInventorySlot mergingSlot = mergingGroup.cosmetic.get(i);
                    ItemStack mergingStack = mergingSlot.graveItem.stack.copy();
                    if (mergingStack.isEmpty()) continue;

                    if (thisGroup.cosmetic.size() <= i) {
                        extraItems.add(new GraveItem(mergingStack, mergingSlot.graveItem.dropRule));
                        continue;
                    }

                    AccessoriesInventorySlot currentSlot = thisGroup.cosmetic.get(i);
                    ItemStack currentStack = currentSlot.graveItem.stack;
                    if (YigdConfig.getConfig().graveConfig.treatBindingCurse && !AccessoriesAPI.canUnequip(mergingStack, SlotReference.of(merger, key, i))) {
                        extraItems.add(new GraveItem(currentStack, currentSlot.graveItem.dropRule));
                        thisGroup.cosmetic.set(i, new AccessoriesInventorySlot(mergingStack, mergingSlot.graveItem.dropRule, mergingSlot.visible));
                        continue;
                    }
                    if (!currentStack.isEmpty()) {
                        extraItems.add(new GraveItem(mergingStack, mergingSlot.graveItem.dropRule));
                        continue;
                    }

                    thisGroup.cosmetic.set(i, mergingSlot);
                }
            }
            return extraItems;
        }

        @Override
        public NonNullList<ItemStack> pullBindingCurseItems(ServerPlayer playerRef) {
            NonNullList<ItemStack> noUnequipItems = NonNullList.create();

            if (!YigdConfig.getConfig().graveConfig.treatBindingCurse) return noUnequipItems;

            for (Map.Entry<String, AccessoriesInventoryGroup> entry : this.inventory.entrySet()) {
                AccessoriesInventoryGroup inventoryGroup = entry.getValue();
                for (int i = 0; i < inventoryGroup.normal.size(); i++) {
                    AccessoriesInventorySlot inventorySlot = inventoryGroup.normal.get(i);
                    ItemStack stack = inventorySlot.graveItem.stack;
                    boolean isBound = !AccessoriesAPI.canUnequip(stack, SlotReference.of(playerRef, entry.getKey(), i));
                    if (isBound) {
                        noUnequipItems.add(stack);
                        inventorySlot.graveItem.stack = ItemStack.EMPTY;
                    }
                }
                for (int i = 0; i < inventoryGroup.cosmetic.size(); i++) {
                    AccessoriesInventorySlot inventorySlot = inventoryGroup.cosmetic.get(i);
                    ItemStack stack = inventorySlot.graveItem.stack;
                    boolean isBound = !AccessoriesAPI.canUnequip(stack, SlotReference.of(playerRef, entry.getKey(), i));
                    if (isBound) {
                        noUnequipItems.add(stack);
                        inventorySlot.graveItem.stack = ItemStack.EMPTY;
                    }
                }
            }

            return noUnequipItems;
        }

        @Override
        public NonNullList<ItemStack> storeToPlayer(ServerPlayer player) {
            NonNullList<ItemStack> extraItems = NonNullList.create();
            AccessoriesCapability capability = AccessoriesCapability.get(player);
            if (capability == null) return extraItems;

            Map<String, AccessoriesContainer> containers = capability.getContainers();
            for (Map.Entry<String, AccessoriesInventoryGroup> entry : this.inventory.entrySet()) {
                String key = entry.getKey();
                if (!containers.containsKey(key)) {
                    entry.getValue().addAllNonEmptyToStackList(extraItems);
                    continue;
                }
                AccessoriesContainer container = containers.get(key);
                AccessoriesInventoryGroup inventorySlot = entry.getValue();

                ExpandedSimpleContainer normalAccessories = container.getAccessories();
                ExpandedSimpleContainer cosmeticAccessories = container.getCosmeticAccessories();
                for (int i = 0; i < inventorySlot.normal.size(); i++) {
                    AccessoriesInventorySlot slot = inventorySlot.normal.get(i);
                    if (i >= normalAccessories.getContainerSize()) {
                        extraItems.add(slot.graveItem.stack.copy());
                        continue;
                    }
                    container.renderOptions().set(i, slot.visible);
                    if (slot.graveItem.stack.isEmpty()) continue;

                    normalAccessories.setItem(i, slot.graveItem.stack.copy());
                }
                for (int i = 0; i < inventorySlot.cosmetic.size(); i++) {
                    AccessoriesInventorySlot slot = inventorySlot.cosmetic.get(i);
                    if (i >= cosmeticAccessories.getContainerSize()) {
                        extraItems.add(slot.graveItem.stack.copy());
                        continue;
                    }
                    container.renderOptions().set(i, slot.visible);
                    if (slot.graveItem.stack.isEmpty()) continue;

                    cosmeticAccessories.setItem(i, slot.graveItem.stack.copy());
                }
                container.markChanged(false);
            }

            return extraItems;
        }

        @Override
        public void handleDropRules(DeathContext context) {
            for (Map.Entry<String, AccessoriesInventoryGroup> entry : this.inventory.entrySet()) {
                String key = entry.getKey();
                AccessoriesInventoryGroup inventoryGroup = entry.getValue();
                for (int i = 0; i < inventoryGroup.normal.size(); i++) {
                    AccessoriesInventorySlot slot = inventoryGroup.normal.get(i);
                    ItemStack stack = slot.graveItem.stack;
                    slot.graveItem.dropRule = switch (AccessoriesAPI.getOrDefaultAccessory(stack)
                            .getDropRule(stack, SlotReference.of(context.player(), key, i), context.deathSource())) {
                        case DESTROY -> DropRule.DESTROY;
                        case KEEP -> DropRule.KEEP;
                        default -> {
                            DropRule defaultDropRule = YigdConfig.getConfig().compatConfig.defaultAccessoriesDropRule;
                            if (defaultDropRule != DropRule.PUT_IN_GRAVE) {
                                yield defaultDropRule;
                            }
                            DropRuleEvent event = new DropRuleEvent(stack, -1, context, true);
                            MinecraftForge.EVENT_BUS.post(event);
                            yield event.getDropRule();
                        }
                    };
                }
                for (int i = 0; i < inventoryGroup.cosmetic.size(); i++) {
                    AccessoriesInventorySlot slot = inventoryGroup.cosmetic.get(i);
                    ItemStack stack = slot.graveItem.stack;
                    slot.graveItem.dropRule = switch (AccessoriesAPI.getOrDefaultAccessory(stack)
                            .getDropRule(stack, SlotReference.of(context.player(), key, i), context.deathSource())) {
                        case DESTROY -> DropRule.DESTROY;
                        case KEEP -> DropRule.KEEP;
                        default -> {
                            DropRule defaultDropRule = YigdConfig.getConfig().compatConfig.defaultAccessoriesDropRule;
                            if (defaultDropRule != DropRule.PUT_IN_GRAVE) {
                                yield defaultDropRule;
                            }
                            DropRuleEvent event = new DropRuleEvent(stack, -1, context, true);
                            MinecraftForge.EVENT_BUS.post(event);
                            yield event.getDropRule();
                        }
                    };
                }
            }
        }

        @Override
        public NonNullList<GraveItem> getAsGraveItemList() {
            NonNullList<GraveItem> allItems = NonNullList.create();
            for (AccessoriesInventoryGroup group : this.inventory.values()) {
                for (AccessoriesInventorySlot slot : group.normal) {
                    allItems.add(slot.graveItem);
                }
                for (AccessoriesInventorySlot slot : group.cosmetic) {
                    allItems.add(slot.graveItem);
                }
            }

            return allItems;
        }

        @Override
        public CompatComponent<Map<String, AccessoriesInventoryGroup>> filterInv(Predicate<DropRule> predicate) {
            Map<String, AccessoriesInventoryGroup> filtered = new HashMap<>();
            for (Map.Entry<String, AccessoriesInventoryGroup> entry : this.inventory.entrySet()) {
                AccessoriesInventoryGroup inventorySlot = entry.getValue();
                NonNullList<AccessoriesInventorySlot> normalSlot = NonNullList.create();
                NonNullList<AccessoriesInventorySlot> cosmeticSlot = NonNullList.create();
                for (AccessoriesInventorySlot slot : inventorySlot.normal) {
                    if (predicate.test(slot.graveItem.dropRule)) {
                        normalSlot.add(slot);
                    } else {
                        normalSlot.add(AccessoriesInventorySlot.empty(slot.visible));
                    }
                }
                for (AccessoriesInventorySlot slot : inventorySlot.cosmetic) {
                    if (predicate.test(slot.graveItem.dropRule)) {
                        cosmeticSlot.add(slot);
                    } else {
                        cosmeticSlot.add(AccessoriesInventorySlot.empty(slot.visible));
                    }
                }

                filtered.put(entry.getKey(), new AccessoriesInventoryGroup(normalSlot, cosmeticSlot));
            }
            return new AccessoriesCompatComponent(filtered);
        }

        @Override
        public boolean removeItem(Predicate<ItemStack> predicate, int itemCount) {
            for (AccessoriesInventoryGroup inventorySlot : this.inventory.values()) {
                for (AccessoriesInventorySlot slot : inventorySlot.normal) {
                    ItemStack stack = slot.graveItem.stack;
                    if (predicate.test(stack)) {
                        stack.shrink(itemCount);
                        return true;
                    }
                }
                for (AccessoriesInventorySlot slot : inventorySlot.cosmetic) {
                    ItemStack stack = slot.graveItem.stack;
                    if (predicate.test(stack)) {
                        stack.shrink(itemCount);
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void clear() {
            for (AccessoriesInventoryGroup inventorySlot : this.inventory.values()) {
                Collections.fill(inventorySlot.normal, AccessoriesInventorySlot.empty(true));
                Collections.fill(inventorySlot.cosmetic, AccessoriesInventorySlot.empty(true));
            }
        }

        @Override
        public CompoundTag saveAdditional() {
            CompoundTag nbt = new CompoundTag();
            for (Map.Entry<String, AccessoriesInventoryGroup> entry : this.inventory.entrySet()) {
                AccessoriesInventoryGroup inventorySlot = entry.getValue();
                CompoundTag slotNbt = new CompoundTag();
                CompoundTag normalNbt = InventoryComponent.listToNbt(inventorySlot.normal, slot -> {
                    CompoundTag itemNbt = new CompoundTag();
                    if (!slot.graveItem.stack.isEmpty()) {
                        slot.graveItem.stack.save(itemNbt);
                    }
                    itemNbt.putString("dropRule", slot.graveItem.dropRule.name());
                    itemNbt.putBoolean("visible", slot.visible);

                    return itemNbt;
                }, slot -> slot.graveItem.stack.isEmpty() && slot.visible);
                CompoundTag cosmeticNbt = InventoryComponent.listToNbt(inventorySlot.cosmetic, slot -> {
                    CompoundTag itemNbt = new CompoundTag();
                    if (!slot.graveItem.stack.isEmpty()) {
                        slot.graveItem.stack.save(itemNbt);
                    }
                    itemNbt.putString("dropRule", slot.graveItem.dropRule.name());
                    itemNbt.putBoolean("visible", slot.visible);

                    return itemNbt;
                }, slot -> slot.graveItem.stack.isEmpty() && slot.visible);

                slotNbt.put("normal", normalNbt);
                slotNbt.put("cosmetic", cosmeticNbt);

                nbt.put(entry.getKey(), slotNbt);
            }

            return nbt;
        }
    }
}
