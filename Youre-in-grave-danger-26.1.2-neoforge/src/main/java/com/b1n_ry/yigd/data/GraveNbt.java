package com.b1n_ry.yigd.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.storage.ValueInput;

import java.util.Optional;

/** Compatibility reads for the grave metadata written before the 26.x codec APIs. */
public final class GraveNbt {
    private GraveNbt() {}

    public static ResolvableProfile readProfile(Tag value) {
        if (value instanceof CompoundTag legacy && (legacy.contains("Name") || legacy.contains("Id"))) {
            // The modern partial-profile codec otherwise accepts these unknown fields as an empty profile.
            CompoundTag normalized = new CompoundTag();
            String name = legacy.getStringOr("Name", "");
            normalized.putString("name", name);
            normalized.store("id", UUIDUtil.CODEC, legacy.read("Id", UUIDUtil.CODEC)
                    .orElseGet(() -> UUIDUtil.createOfflinePlayerUUID(name)));
            ListTag properties = new ListTag();
            CompoundTag oldProperties = legacy.getCompoundOrEmpty("Properties");
            for (String propertyName : oldProperties.keySet()) {
                for (Tag entry : oldProperties.getListOrEmpty(propertyName)) {
                    if (!(entry instanceof CompoundTag property)) continue;
                    CompoundTag modern = new CompoundTag();
                    modern.putString("name", propertyName);
                    modern.putString("value", property.getStringOr("Value", ""));
                    property.getString("Signature").ifPresent(signature -> modern.putString("signature", signature));
                    properties.add(modern);
                }
            }
            normalized.put("properties", properties);
            return ResolvableProfile.createResolved(ExtraCodecs.STORED_GAME_PROFILE.codec()
                    .parse(NbtOps.INSTANCE, normalized).getOrThrow());
        }
        return ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, value).getOrThrow();
    }

    public static Optional<ResolvableProfile> readProfile(ValueInput input, String name) {
        Optional<CompoundTag> compound = input.read(name, CompoundTag.CODEC);
        if (compound.filter(tag -> tag.contains("Name") || tag.contains("Id")).isPresent()) {
            return compound.map(GraveNbt::readProfile);
        }
        return input.read(name, ResolvableProfile.CODEC);
    }

    public static Component readComponent(CompoundTag tag, String name, HolderLookup.Provider registries) {
        Optional<Component> json = tag.getString(name).flatMap(value -> legacyJson(value, registries));
        if (json.isPresent()) return json.get();
        if (tag.get(name) instanceof CompoundTag legacy && legacy.contains("damageTypeId")) {
            String key = "death.attack." + legacy.getStringOr("damageTypeId", "generic");
            String killed = legacy.getStringOr("killedDisplayName", "");
            String attacker = legacy.getString("attackerDisplayName").orElseGet(() -> legacy.getString("sourceDisplayName").orElse(null));
            if (attacker != null) {
                return legacy.getString("itemDisplayName")
                        .map(item -> Component.translatable(key + ".item", killed, attacker, item))
                        .orElseGet(() -> Component.translatable(key, killed, attacker));
            }
            return legacy.getString("primeAdversaryDisplayName")
                    .map(adversary -> Component.translatable(key + ".player", killed, adversary))
                    .orElseGet(() -> Component.translatable(key, killed));
        }
        return tag.read(name, ComponentSerialization.CODEC, registries.createSerializationContext(NbtOps.INSTANCE)).orElse(Component.empty());
    }

    public static Optional<Component> readComponent(ValueInput input, String name) {
        Optional<Component> json = input.getString(name).flatMap(value -> legacyJson(value, input.lookup()));
        return json.isPresent() ? json : input.read(name, ComponentSerialization.CODEC);
    }

    private static Optional<Component> legacyJson(String value, HolderLookup.Provider registries) {
        String stripped = value.stripLeading();
        if (!(stripped.startsWith("{") || stripped.startsWith("[") || stripped.startsWith("\""))) return Optional.empty();
        try {
            return ComponentSerialization.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(value)).result();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public static BlockPos readPos(CompoundTag tag, String name) {
        if (tag.get(name) instanceof CompoundTag legacy) {
            return new BlockPos(legacy.getIntOr("X", legacy.getIntOr("x", 0)),
                    legacy.getIntOr("Y", legacy.getIntOr("y", 0)), legacy.getIntOr("Z", legacy.getIntOr("z", 0)));
        }
        return tag.read(name, BlockPos.CODEC).orElse(BlockPos.ZERO);
    }
}
