package com.b1n_ry.yigd.compat;

import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathContext;
import com.b1n_ry.yigd.data.GraveItem;
import com.b1n_ry.yigd.events.DropRuleEvent;
import com.b1n_ry.yigd.util.DropRule;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.LazyOptional;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.DropRulesEvent;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class CuriosCompat implements InvModCompat<Map<String, CuriosCompat.CuriosSlotEntry>> {
    @Override
    public String getModName() {
        return "curios";
    }

    @Override
    public void clear(ServerPlayer player) {
        CuriosApi.getCuriosHelper().getCuriosHandler(player).ifPresent(handler -> {
            for (ICurioStacksHandler stacksHandler : handler.getCurios().values()) {
                IDynamicStackHandler[] stackHandlers = { stacksHandler.getStacks(), stacksHandler.getCosmeticStacks() };
                for (IDynamicStackHandler dynamicStackHandler : stackHandlers) {
                    for (int i = 0; i < dynamicStackHandler.getSlots(); i++) {
                        dynamicStackHandler.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }
            }
        });
    }

    @Override
    public CompatComponent<Map<String, CuriosSlotEntry>> load(CompoundTag nbt) {
        Map<String, CuriosSlotEntry> inventory = new HashMap<>();

        for (String key : nbt.getAllKeys()) {
            CompoundTag slotNbt = nbt.getCompound(key);
            NonNullList<GraveItem> normalSlot = InventoryComponent.listFromNbt(slotNbt.getCompound("normal"), this::readGraveItem, InventoryComponent.EMPTY_GRAVE_ITEM);
            NonNullList<GraveItem> cosmeticSlot = InventoryComponent.listFromNbt(slotNbt.getCompound("cosmetic"), this::readGraveItem, InventoryComponent.EMPTY_GRAVE_ITEM);
            inventory.put(key, new CuriosSlotEntry(normalSlot, cosmeticSlot));
        }

        return new CuriosCompatComponent(inventory);
    }

    @Override
    public CompatComponent<Map<String, CuriosSlotEntry>> getNewComponent(ServerPlayer player) {
        return new CuriosCompatComponent(player);
    }

    private GraveItem readGraveItem(CompoundTag itemNbt) {
        ItemStack stack = ItemStack.of(itemNbt);
        DropRule dropRule = readDropRule(itemNbt);
        return new GraveItem(stack, dropRule);
    }

    private DropRule readDropRule(CompoundTag itemNbt) {
        DropRule defaultRule = YigdConfig.getConfig().compatConfig.defaultCuriosDropRule;
        if (itemNbt.contains("dropRule")) {
            String dropRuleString = itemNbt.getString("dropRule");
            if ("DEFAULT".equals(dropRuleString)) {
                return defaultRule;
            }
            return DropRule.valueOf(dropRuleString);
        }
        return defaultRule;
    }

    private static class CuriosCompatComponent extends CompatComponent<Map<String, CuriosSlotEntry>> {
        public CuriosCompatComponent(ServerPlayer player) {
            super(player);
        }
        public CuriosCompatComponent(Map<String, CuriosSlotEntry> inventory) {
            super(inventory);
        }

        @Override
        public Map<String, CuriosSlotEntry> getInventory(ServerPlayer player) {
            Map<String, CuriosSlotEntry> curiosInventory = new HashMap<>();
            Optional<ICuriosItemHandler> optionalHandler = CuriosApi.getCuriosHelper().getCuriosHandler(player).resolve();
            if (optionalHandler.isEmpty()) return curiosInventory;

            for (Map.Entry<String, ICurioStacksHandler> entry : optionalHandler.get().getCurios().entrySet()) {
                ICurioStacksHandler stacksHandler = entry.getValue();
                NonNullList<GraveItem> normalItems = NonNullList.create();
                NonNullList<GraveItem> cosmeticItems = NonNullList.create();

                IDynamicStackHandler normalStacks = stacksHandler.getStacks();
                for (int i = 0; i < normalStacks.getSlots(); i++) {
                    normalItems.add(new GraveItem(normalStacks.getStackInSlot(i).copy(), DropRule.PUT_IN_GRAVE));
                }

                IDynamicStackHandler cosmeticStacks = stacksHandler.getCosmeticStacks();
                for (int i = 0; i < cosmeticStacks.getSlots(); i++) {
                    cosmeticItems.add(new GraveItem(cosmeticStacks.getStackInSlot(i).copy(), DropRule.PUT_IN_GRAVE));
                }

                curiosInventory.put(entry.getKey(), new CuriosSlotEntry(normalItems, cosmeticItems));
            }

            return curiosInventory;
        }

        @Override
        public NonNullList<GraveItem> merge(CompatComponent<?> mergingComponent, ServerPlayer merger) {
            NonNullList<GraveItem> extraItems = NonNullList.create();
            @SuppressWarnings("unchecked")
            Map<String, CuriosSlotEntry> mergingInventory = (Map<String, CuriosSlotEntry>) mergingComponent.inventory;

            for (Map.Entry<String, CuriosSlotEntry> entry : mergingInventory.entrySet()) {
                String key = entry.getKey();
                if (!this.inventory.containsKey(key)) {
                    entry.getValue().addAllNonEmptyToGraveList(extraItems);
                    continue;
                }

                CuriosSlotEntry targetSlot = this.inventory.get(key);
                CuriosSlotEntry mergingSlot = entry.getValue();
                mergeSlotList(extraItems, merger, key, targetSlot.normal, mergingSlot.normal, false);
                mergeSlotList(extraItems, merger, key, targetSlot.cosmetic, mergingSlot.cosmetic, true);
            }

            return extraItems;
        }

        private void mergeSlotList(NonNullList<GraveItem> extraItems, ServerPlayer player, String slotKey,
                                   NonNullList<GraveItem> target, NonNullList<GraveItem> source, boolean cosmetic) {
            for (int i = 0; i < source.size(); i++) {
                GraveItem sourceItem = source.get(i).copy();
                ItemStack sourceStack = sourceItem.stack;
                if (sourceStack.isEmpty()) continue;

                if (target.size() <= i) {
                    extraItems.add(sourceItem);
                    continue;
                }

                GraveItem targetItem = target.get(i);
                ItemStack currentStack = targetItem.stack;
                if (YigdConfig.getConfig().graveConfig.treatBindingCurse && blockUnequip(sourceStack, slotKey, i, player, cosmetic)) {
                    extraItems.add(targetItem);
                    target.set(i, sourceItem);
                    continue;
                }

                if (!currentStack.isEmpty()) {
                    extraItems.add(sourceItem);
                    continue;
                }

                target.set(i, sourceItem);
            }
        }

        @Override
        public NonNullList<ItemStack> pullBindingCurseItems(ServerPlayer playerRef) {
            NonNullList<ItemStack> lockedItems = NonNullList.create();
            if (!YigdConfig.getConfig().graveConfig.treatBindingCurse) return lockedItems;

            for (Map.Entry<String, CuriosSlotEntry> entry : this.inventory.entrySet()) {
                String key = entry.getKey();
                CuriosSlotEntry slotEntry = entry.getValue();

                for (int i = 0; i < slotEntry.normal.size(); i++) {
                    GraveItem graveItem = slotEntry.normal.get(i);
                    if (graveItem.stack.isEmpty()) continue;
                    if (blockUnequip(graveItem.stack, key, i, playerRef, false)) {
                        lockedItems.add(graveItem.stack.copy());
                        graveItem.stack = ItemStack.EMPTY;
                    }
                }
                for (int i = 0; i < slotEntry.cosmetic.size(); i++) {
                    GraveItem graveItem = slotEntry.cosmetic.get(i);
                    if (graveItem.stack.isEmpty()) continue;
                    if (blockUnequip(graveItem.stack, key, i, playerRef, true)) {
                        lockedItems.add(graveItem.stack.copy());
                        graveItem.stack = ItemStack.EMPTY;
                    }
                }
            }

            return lockedItems;
        }

        private boolean blockUnequip(ItemStack stack, String key, int index, ServerPlayer playerRef, boolean cosmetic) {
            LazyOptional<ICurio> curio = CuriosApi.getCuriosHelper().getCurio(stack);
            return !curio.map(value -> value.canUnequip(new SlotContext(key, playerRef, index, cosmetic, false))).orElse(true);
        }

        @Override
        public NonNullList<ItemStack> storeToPlayer(ServerPlayer player) {
            NonNullList<ItemStack> extraItems = NonNullList.create();
            Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosHelper().getCuriosHandler(player).resolve();
            if (optional.isEmpty()) return extraItems;

            Map<String, ICurioStacksHandler> curios = optional.get().getCurios();
            for (Map.Entry<String, CuriosSlotEntry> entry : this.inventory.entrySet()) {
                String key = entry.getKey();
                CuriosSlotEntry slotEntry = entry.getValue();
                if (!curios.containsKey(key)) {
                    slotEntry.addAllNonEmptyStacks(extraItems);
                    continue;
                }

                ICurioStacksHandler handler = curios.get(key);
                writeSlotToHandler(extraItems, slotEntry.normal, handler.getStacks());
                writeSlotToHandler(extraItems, slotEntry.cosmetic, handler.getCosmeticStacks());
            }

            return extraItems;
        }

        private void writeSlotToHandler(Collection<ItemStack> overflow, NonNullList<GraveItem> source, IDynamicStackHandler target) {
            for (int i = 0; i < source.size(); i++) {
                GraveItem graveItem = source.get(i).copy();
                if (i >= target.getSlots()) {
                    overflow.add(graveItem.stack);
                    continue;
                }

                target.setStackInSlot(i, graveItem.stack);
            }
        }

        @Override
        public void handleDropRules(DeathContext context) {
            ServerPlayer player = context.player();
            List<Tuple<Predicate<ItemStack>, ICurio.DropRule>> overrides = new ArrayList<>();
            CuriosApi.getCuriosHelper().getCuriosHandler(player).ifPresent(handler -> {
                DropRulesEvent event = new DropRulesEvent(player, handler, context.deathSource(), 0, false);
                MinecraftForge.EVENT_BUS.post(event);
                overrides.addAll(event.getOverrides());
            });

            for (Map.Entry<String, CuriosSlotEntry> entry : this.inventory.entrySet()) {
                String key = entry.getKey();
                CuriosSlotEntry slotEntry = entry.getValue();
                applyDropRules(slotEntry.normal, key, context, false, overrides);
                applyDropRules(slotEntry.cosmetic, key, context, true, overrides);
            }
        }

        private void applyDropRules(NonNullList<GraveItem> slotItems, String key, DeathContext context, boolean cosmetic,
                                    List<Tuple<Predicate<ItemStack>, ICurio.DropRule>> overrides) {
            for (int i = 0; i < slotItems.size(); i++) {
                GraveItem graveItem = slotItems.get(i);
                ItemStack stack = graveItem.stack;
                if (stack.isEmpty()) continue;

                DropRule defaultDropRule = YigdConfig.getConfig().compatConfig.defaultCuriosDropRule;
                ICurio.DropRule curiosRule = resolveDropRule(stack, key, i, context, cosmetic, overrides);
                graveItem.dropRule = switch (curiosRule) {
                    case DESTROY -> DropRule.DESTROY;
                    case ALWAYS_KEEP -> DropRule.KEEP;
                    default -> {
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

        private ICurio.DropRule resolveDropRule(ItemStack stack, String key, int index, DeathContext context, boolean cosmetic,
                                                List<Tuple<Predicate<ItemStack>, ICurio.DropRule>> overrides) {
            for (Tuple<Predicate<ItemStack>, ICurio.DropRule> override : overrides) {
                if (override.getA().test(stack)) {
                    return override.getB();
                }
            }
            LazyOptional<ICurio> curio = CuriosApi.getCuriosHelper().getCurio(stack);
            return curio.map(value -> value.getDropRule(new SlotContext(key, context.player(), index, cosmetic, false), context.deathSource(), 0, false))
                    .orElse(ICurio.DropRule.DEFAULT);
        }

        @Override
        public NonNullList<GraveItem> getAsGraveItemList() {
            NonNullList<GraveItem> allItems = NonNullList.create();
            for (CuriosSlotEntry slotEntry : this.inventory.values()) {
                allItems.addAll(slotEntry.normal);
                allItems.addAll(slotEntry.cosmetic);
            }
            return allItems;
        }

        @Override
        public CompatComponent<Map<String, CuriosSlotEntry>> filterInv(Predicate<DropRule> predicate) {
            Map<String, CuriosSlotEntry> filtered = new HashMap<>();
            for (Map.Entry<String, CuriosSlotEntry> entry : this.inventory.entrySet()) {
                NonNullList<GraveItem> filteredNormal = NonNullList.create();
                NonNullList<GraveItem> filteredCosmetic = NonNullList.create();

                for (GraveItem graveItem : entry.getValue().normal) {
                    filteredNormal.add(predicate.test(graveItem.dropRule) ? graveItem : InventoryComponent.EMPTY_GRAVE_ITEM);
                }
                for (GraveItem graveItem : entry.getValue().cosmetic) {
                    filteredCosmetic.add(predicate.test(graveItem.dropRule) ? graveItem : InventoryComponent.EMPTY_GRAVE_ITEM);
                }

                filtered.put(entry.getKey(), new CuriosSlotEntry(filteredNormal, filteredCosmetic));
            }
            return new CuriosCompatComponent(filtered);
        }

        @Override
        public boolean removeItem(Predicate<ItemStack> predicate, int itemCount) {
            for (CuriosSlotEntry slotEntry : this.inventory.values()) {
                for (GraveItem graveItem : slotEntry.normal) {
                    if (predicate.test(graveItem.stack)) {
                        graveItem.stack.shrink(itemCount);
                        return true;
                    }
                }
                for (GraveItem graveItem : slotEntry.cosmetic) {
                    if (predicate.test(graveItem.stack)) {
                        graveItem.stack.shrink(itemCount);
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void clear() {
            for (CuriosSlotEntry slotEntry : this.inventory.values()) {
                Collections.fill(slotEntry.normal, InventoryComponent.EMPTY_GRAVE_ITEM);
                Collections.fill(slotEntry.cosmetic, InventoryComponent.EMPTY_GRAVE_ITEM);
            }
        }

        @Override
        public CompoundTag saveAdditional() {
            CompoundTag nbt = new CompoundTag();
            for (Map.Entry<String, CuriosSlotEntry> entry : this.inventory.entrySet()) {
                CuriosSlotEntry slotEntry = entry.getValue();
                CompoundTag slotNbt = new CompoundTag();

                CompoundTag normalNbt = InventoryComponent.listToNbt(slotEntry.normal, graveItem -> {
                    CompoundTag itemNbt = new CompoundTag();
                    graveItem.stack.save(itemNbt);
                    itemNbt.putString("dropRule", graveItem.dropRule.name());
                    return itemNbt;
                }, graveItem -> graveItem.stack.isEmpty());

                CompoundTag cosmeticNbt = InventoryComponent.listToNbt(slotEntry.cosmetic, graveItem -> {
                    CompoundTag itemNbt = new CompoundTag();
                    graveItem.stack.save(itemNbt);
                    itemNbt.putString("dropRule", graveItem.dropRule.name());
                    return itemNbt;
                }, graveItem -> graveItem.stack.isEmpty());

                slotNbt.put("normal", normalNbt);
                slotNbt.put("cosmetic", cosmeticNbt);
                nbt.put(entry.getKey(), slotNbt);
            }
            return nbt;
        }
    }

    public record CuriosSlotEntry(NonNullList<GraveItem> normal, NonNullList<GraveItem> cosmetic) {
        private void addAllNonEmptyToGraveList(Collection<GraveItem> list) {
            addAll(list, GraveItem::copy);
        }
        private void addAllNonEmptyStacks(Collection<ItemStack> list) {
            addAll(list, graveItem -> graveItem.stack.copy());
        }
        private <T> void addAll(Collection<T> list, Function<GraveItem, T> mapper) {
            for (GraveItem graveItem : this.normal) {
                if (!graveItem.stack.isEmpty()) {
                    list.add(mapper.apply(graveItem));
                }
            }
            for (GraveItem graveItem : this.cosmetic) {
                if (!graveItem.stack.isEmpty()) {
                    list.add(mapper.apply(graveItem));
                }
            }
        }
    }
}
