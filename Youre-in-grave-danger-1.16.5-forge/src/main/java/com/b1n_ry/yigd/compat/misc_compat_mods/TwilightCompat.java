package com.b1n_ry.yigd.compat.misc_compat_mods;

import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.data.DeathContext;
import com.b1n_ry.yigd.events.AdjustDropRuleEvent;
import com.b1n_ry.yigd.util.DropRule;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

public class TwilightCompat {
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener((AdjustDropRuleEvent event) -> {
            InventoryComponent inventoryComponent = event.getInventoryComponent();
            DeathContext context = event.getDeathContext();
            if (context == null) return;
            int selectedSlot = context.player().inventory.selected;
            Item keepingCharm3 = ForgeRegistries.ITEMS.getValue(new ResourceLocation("twilightforest", "charm_of_keeping_3"));
            Item keepingCharm2 = ForgeRegistries.ITEMS.getValue(new ResourceLocation("twilightforest", "charm_of_keeping_2"));
            Item keepingCharm1 = ForgeRegistries.ITEMS.getValue(new ResourceLocation("twilightforest", "charm_of_keeping_1"));

            boolean tier3 = keepingCharm3 != null && inventoryComponent.containsAny(
                    stack -> stack.getItem() == keepingCharm3, mod -> true, slot -> true);
            boolean tier2 = tier3 || (keepingCharm2 != null && inventoryComponent.containsAny(
                    stack -> stack.getItem() == keepingCharm2, mod -> true, slot -> true));
            boolean tier1 = tier2 || (keepingCharm1 != null && inventoryComponent.containsAny(
                    stack -> stack.getItem() == keepingCharm1,
                    mod -> true, slot -> true));

            Item firstMatch = tier3 ? keepingCharm3 :
                    tier2 ? keepingCharm2 :
                    tier1 ? keepingCharm1 :
                    null;
            if (firstMatch == null) return;

            final int hotbarSize = 9;  // We don't know for certain, but we can be pretty confident
            int afterOffhandIndex = inventoryComponent.mainSize + inventoryComponent.armorSize + inventoryComponent.offHandSize;
            inventoryComponent.handleGraveItems(mod -> true, (stack, slot, graveItem) -> {
                if (slot >= inventoryComponent.mainSize && slot < afterOffhandIndex || slot == selectedSlot || slot < 0) {  // Tier 1: Will always be true, otherwise we exit sooner
                    graveItem.dropRule = DropRule.KEEP;
                } else if (tier2 && slot < hotbarSize) {
                    graveItem.dropRule = DropRule.KEEP;
                } else if (tier3) {
                    graveItem.dropRule = DropRule.KEEP;
                }

                if (stack.getItem() != firstMatch) return;
                graveItem.dropRule = DropRule.DESTROY;
            });
        });
    }
}
