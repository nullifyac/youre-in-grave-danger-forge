package com.b1n_ry.yigd.util;

import com.b1n_ry.yigd.Yigd;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ITag;
import net.minecraft.tags.ItemTags;

public interface YigdTags {
    ITag.INamedTag<Block> REPLACE_SOFT_WHITELIST = BlockTags.bind(Yigd.MOD_ID + ":replace_soft_whitelist");
    ITag.INamedTag<Block> KEEP_STRICT_BLACKLIST = BlockTags.bind(Yigd.MOD_ID + ":keep_strict_blacklist");
    ITag.INamedTag<Block> REPLACE_GRAVE_BLACKLIST = BlockTags.bind(Yigd.MOD_ID + ":replace_grave_blacklist");

    ITag.INamedTag<Item> NATURAL_SOULBOUND = ItemTags.bind(Yigd.MOD_ID + ":natural_soulbound");
    ITag.INamedTag<Item> NATURAL_VANISHING = ItemTags.bind(Yigd.MOD_ID + ":natural_vanishing");
    ITag.INamedTag<Item> LOSS_IMMUNE = ItemTags.bind(Yigd.MOD_ID + ":loss_immune");
    ITag.INamedTag<Item> GRAVE_INCOMPATIBLE = ItemTags.bind(Yigd.MOD_ID + ":grave_incompatible");  // For items that should be dropped instead of put into graves
    ITag.INamedTag<Item> SOULBOUND_BLACKLIST = ItemTags.bind(Yigd.MOD_ID + ":soulbound_blacklist");
}
