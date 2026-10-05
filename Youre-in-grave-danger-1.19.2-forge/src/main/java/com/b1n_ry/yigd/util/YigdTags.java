package com.b1n_ry.yigd.util;

import com.b1n_ry.yigd.Yigd;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

public interface YigdTags {
    TagKey<Block> REPLACE_SOFT_WHITELIST = TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation(Yigd.MOD_ID, "replace_soft_whitelist"));
    TagKey<Block> KEEP_STRICT_BLACKLIST = TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation(Yigd.MOD_ID, "keep_strict_blacklist"));
    TagKey<Block> REPLACE_GRAVE_BLACKLIST = TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation(Yigd.MOD_ID, "replace_grave_blacklist"));

    TagKey<Item> NATURAL_SOULBOUND = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation(Yigd.MOD_ID, "natural_soulbound"));
    TagKey<Item> NATURAL_VANISHING = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation(Yigd.MOD_ID, "natural_vanishing"));
    TagKey<Item> LOSS_IMMUNE = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation(Yigd.MOD_ID, "loss_immune"));
    TagKey<Item> GRAVE_INCOMPATIBLE = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation(Yigd.MOD_ID, "grave_incompatible"));  // For items that should be dropped instead of put into graves
    TagKey<Item> SOULBOUND_BLACKLIST = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation(Yigd.MOD_ID, "soulbound_blacklist"));
}
