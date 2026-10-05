package com.b1n_ry.yigd.compat;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.config.CompatConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.data.DeathContext;
import com.b1n_ry.yigd.data.GraveItem;
import com.b1n_ry.yigd.events.DropRuleEvent;
import com.b1n_ry.yigd.util.DropRule;
import net.minecraft.util.NonNullList;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.GameRules;
import net.minecraftforge.common.MinecraftForge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;

public class NumismaticOverhaulCompat implements InvModCompat<Long> {
    @Override
    public String getModName() {
        return "numismatic overhaul";
    }

    @Override
    public void clear(ServerPlayerEntity player) {
        long value = NumismaticAccess.getValue(player);
        if (value != 0) {
            NumismaticAccess.silentModify(player, -value);
        }
    }

    @Override
    public CompatComponent<Long> load(CompoundNBT nbt) {
        long value = nbt.getLong("value");
        long dropValue = nbt.contains("dropValue") ? nbt.getLong("dropValue") : 0L;
        long keepValue = nbt.contains("keepValue") ? nbt.getLong("keepValue") : 0L;
        long destroyValue = nbt.contains("destroyValue") ? nbt.getLong("destroyValue") : 0L;
        long graveValue = nbt.contains("graveValue") ? nbt.getLong("graveValue") : 0L;

        return new NumismaticCompatComponent(value, dropValue, keepValue, destroyValue, graveValue);
    }

    @Override
    public CompatComponent<Long> getNewComponent(ServerPlayerEntity player) {
        return new NumismaticCompatComponent(player);
    }

    private static class NumismaticCompatComponent extends CompatComponent<Long> {
        private long dropValue = 0;
        private long keepValue = 0;
        private long destroyValue = 0;
        private long graveValue = 0;

        public NumismaticCompatComponent(ServerPlayerEntity player) {
            super(player);
        }

        public NumismaticCompatComponent(long inventory, long dropValue, long keepValue, long destroyValue, long graveValue) {
            super(inventory);
            this.dropValue = dropValue;
            this.keepValue = keepValue;
            this.destroyValue = destroyValue;
            this.graveValue = graveValue;
        }

        @Override
        public Long getInventory(ServerPlayerEntity player) {
            return NumismaticAccess.getValue(player);
        }

        @Override
        public NonNullList<GraveItem> merge(CompatComponent<?> mergingComponent, ServerPlayerEntity merger) {
            this.inventory += (long) mergingComponent.inventory;
            return NonNullList.create();
        }

        @Override
        public NonNullList<ItemStack> storeToPlayer(ServerPlayerEntity player) {
            NumismaticAccess.modify(player, this.inventory);
            return NonNullList.create();
        }

        @Override
        public void handleDropRules(DeathContext context) {
            CompatConfig compatConfig = YigdConfig.getConfig().compatConfig;

            int dropRate = NumismaticAccess.getMoneyDropRate(context);
            float dropFactor = dropRate * 0.01f;
            float keepFactor = Math.max(1 - dropFactor, 0);

            this.dropValue = 0;
            this.destroyValue = 0;
            this.graveValue = 0;

            this.keepValue = (long) (this.inventory * keepFactor);
            this.inventory -= this.keepValue;

            for (ItemStack stack : NumismaticAccess.getAsItemStackArray(this.inventory)) {
                DropRule dropRule = compatConfig.defaultNumismaticDropRule;
                long itemValue = NumismaticAccess.getCurrencyItemValue(stack);
                if (itemValue == 0) {
                    continue;
                }

                if (dropRule == DropRule.PUT_IN_GRAVE) {
                    DropRuleEvent event = new DropRuleEvent(stack, -1, context, true);
                    MinecraftForge.EVENT_BUS.post(event);
                    dropRule = event.getDropRule();
                }
                switch (dropRule) {
                    case DROP:
                        this.inventory -= itemValue;
                        this.dropValue += itemValue;
                        break;
                    case DESTROY:
                        this.inventory -= itemValue;
                        this.destroyValue += itemValue;
                        break;
                    case KEEP:
                        this.inventory -= itemValue;
                        this.keepValue += itemValue;
                        break;
                    case PUT_IN_GRAVE:
                        this.inventory -= itemValue;
                        this.graveValue += itemValue;
                        break;
                }
            }
        }

        @Override
        public NonNullList<GraveItem> getAsGraveItemList() {
            NonNullList<GraveItem> list = NonNullList.create();
            addStacks(list, this.graveValue, DropRule.PUT_IN_GRAVE);
            addStacks(list, this.dropValue, DropRule.DROP);
            addStacks(list, this.keepValue, DropRule.KEEP);
            addStacks(list, this.destroyValue, DropRule.DESTROY);
            return list;
        }

        private void addStacks(NonNullList<GraveItem> list, long value, DropRule rule) {
            if (value == 0) {
                return;
            }
            for (ItemStack stack : NumismaticAccess.getAsItemStackArray(value)) {
                if (!stack.isEmpty()) {
                    list.add(new GraveItem(stack, rule));
                }
            }
        }

        @Override
        public CompatComponent<Long> filterInv(Predicate<DropRule> predicate) {
            long totalValue = 0;
            long dropValue = 0;
            long keepValue = 0;
            long destroyValue = 0;
            long graveValue = 0;

            if (predicate.test(DropRule.DROP)) {
                totalValue += this.dropValue;
                dropValue = this.dropValue;
            }
            if (predicate.test(DropRule.KEEP)) {
                totalValue += this.keepValue;
                keepValue = this.keepValue;
            }
            if (predicate.test(DropRule.DESTROY)) {
                totalValue += this.destroyValue;
                destroyValue = this.destroyValue;
            }
            if (predicate.test(DropRule.PUT_IN_GRAVE)) {
                totalValue += this.graveValue;
                graveValue = this.graveValue;
            }

            return new NumismaticCompatComponent(totalValue, dropValue, keepValue, destroyValue, graveValue);
        }

        @Override
        public boolean removeItem(Predicate<ItemStack> predicate, int itemCount) {
            for (ItemStack stack : NumismaticAccess.getAsItemStackArray(this.inventory)) {
                if (!predicate.test(stack)) {
                    continue;
                }
                long stackValue = NumismaticAccess.getCurrencyItemValue(stack);
                int count = stack.getCount();
                if (count <= 0 || stackValue == 0) {
                    return false;
                }
                long perItemValue = stackValue / count;
                this.inventory -= perItemValue * itemCount;
                return true;
            }
            return false;
        }

        @Override
        public void clear() {
            this.inventory = 0L;
        }

        @Override
        public boolean containsGraveItems() {
            return this.graveValue != 0L;
        }

        @Override
        public CompoundNBT saveAdditional() {
            CompoundNBT nbt = new CompoundNBT();
            nbt.putLong("value", this.inventory);
            nbt.putLong("dropValue", this.dropValue);
            nbt.putLong("keepValue", this.keepValue);
            nbt.putLong("destroyValue", this.destroyValue);
            nbt.putLong("graveValue", this.graveValue);

            return nbt;
        }
    }

    private static final class NumismaticAccess {
        private static final ItemStack[] EMPTY_STACKS = new ItemStack[0];
        private static final String MOD_COMPONENTS = "com.glisco.numismaticoverhaul.ModComponents";
        private static final String NUMISMATIC_OVERHAUL = "com.glisco.numismaticoverhaul.NumismaticOverhaul";
        private static final String CURRENCY_COMPONENT = "com.glisco.numismaticoverhaul.currency.CurrencyComponent";
        private static final String CURRENCY_CONVERTER = "com.glisco.numismaticoverhaul.currency.CurrencyConverter";
        private static final String CURRENCY_ITEM = "com.glisco.numismaticoverhaul.item.CurrencyItem";

        private static boolean initialized;
        private static boolean available;
        private static Object currencyKey;
        private static Method currencyKeyGet;
        private static Method componentGetValue;
        private static Method componentSilentModify;
        private static Method componentModify;
        private static Method converterGetAsArray;
        private static Class<?> currencyItemClass;
        private static Method currencyItemGetValue;
        private static Object moneyDropPercentageKey;
        private static Method rulesGetInt;

        static long getValue(ServerPlayerEntity player) {
            Object component = getComponent(player);
            if (component == null) {
                return 0L;
            }
            try {
                Object value = componentGetValue.invoke(component);
                return value instanceof Number ? ((Number) value).longValue() : 0L;
            } catch (Exception e) {
                logFailure("Failed to read numismatic value", e);
                return 0L;
            }
        }

        static void silentModify(ServerPlayerEntity player, long delta) {
            Object component = getComponent(player);
            if (component == null) {
                return;
            }
            try {
                componentSilentModify.invoke(component, delta);
            } catch (Exception e) {
                logFailure("Failed to modify numismatic value silently", e);
            }
        }

        static void modify(ServerPlayerEntity player, long delta) {
            Object component = getComponent(player);
            if (component == null) {
                return;
            }
            try {
                componentModify.invoke(component, delta);
            } catch (Exception e) {
                logFailure("Failed to modify numismatic value", e);
            }
        }

        static ItemStack[] getAsItemStackArray(long value) {
            if (!ensureInitialized()) {
                return EMPTY_STACKS;
            }
            try {
                Object result = converterGetAsArray.invoke(null, value);
                if (result instanceof ItemStack[]) {
                    return (ItemStack[]) result;
                }
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    ItemStack[] stacks = new ItemStack[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        Object obj = objects[i];
                        if (obj instanceof ItemStack) {
                            stacks[i] = (ItemStack) obj;
                        } else {
                            stacks[i] = ItemStack.EMPTY;
                        }
                    }
                    return stacks;
                }
            } catch (Exception e) {
                logFailure("Failed to convert numismatic value to stacks", e);
            }
            return EMPTY_STACKS;
        }

        static long getCurrencyItemValue(ItemStack stack) {
            if (!ensureInitialized() || stack.isEmpty()) {
                return 0L;
            }
            Object item = stack.getItem();
            if (!currencyItemClass.isInstance(item)) {
                return 0L;
            }
            try {
                Object value = currencyItemGetValue.invoke(item, stack);
                return value instanceof Number ? ((Number) value).longValue() : 0L;
            } catch (Exception e) {
                logFailure("Failed to read currency item value", e);
                return 0L;
            }
        }

        static int getMoneyDropRate(DeathContext context) {
            if (!ensureInitialized() || moneyDropPercentageKey == null) {
                return 0;
            }
            try {
                Object value = rulesGetInt.invoke(context.world().getGameRules(), moneyDropPercentageKey);
                return value instanceof Integer ? (Integer) value : 0;
            } catch (Exception e) {
                logFailure("Failed to read money drop gamerule", e);
                return 0;
            }
        }

        private static Object getComponent(ServerPlayerEntity player) {
            if (!ensureInitialized()) {
                return null;
            }
            try {
                return currencyKeyGet.invoke(currencyKey, player);
            } catch (Exception e) {
                logFailure("Failed to access currency component", e);
                return null;
            }
        }

        private static boolean ensureInitialized() {
            if (initialized) {
                return available;
            }
            initialized = true;
            try {
                Class<?> modComponents = Class.forName(MOD_COMPONENTS);
                Field currencyField = modComponents.getField("CURRENCY");
                currencyKey = currencyField.get(null);
                currencyKeyGet = findMethod(currencyKey.getClass(), "get", 1);

                Class<?> currencyComponent = Class.forName(CURRENCY_COMPONENT);
                componentGetValue = currencyComponent.getMethod("getValue");
                componentSilentModify = currencyComponent.getMethod("silentModify", long.class);
                componentModify = currencyComponent.getMethod("modify", long.class);

                Class<?> converter = Class.forName(CURRENCY_CONVERTER);
                converterGetAsArray = converter.getMethod("getAsItemStackArray", long.class);

                currencyItemClass = Class.forName(CURRENCY_ITEM);
                currencyItemGetValue = findMethod(currencyItemClass, "getValue", 1);

                Class<?> numismaticOverhaul = Class.forName(NUMISMATIC_OVERHAUL);
                Field moneyDropField = numismaticOverhaul.getField("MONEY_DROP_PERCENTAGE");
                moneyDropPercentageKey = moneyDropField.get(null);

                rulesGetInt = findMethod(GameRules.class, "getInt", 1);

                available = currencyKeyGet != null && currencyItemGetValue != null && rulesGetInt != null;
            } catch (Exception e) {
                logFailure("Failed to initialize numismatic reflection", e);
                available = false;
            }
            return available;
        }

        private static Method findMethod(Class<?> type, String name, int paramCount) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
                    return method;
                }
            }
            return null;
        }

        private static void logFailure(String message, Exception e) {
            Yigd.LOGGER.warn(message, e);
        }
    }
}
