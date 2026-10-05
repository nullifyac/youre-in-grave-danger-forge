package com.b1n_ry.yigd.packets;

import com.b1n_ry.yigd.Yigd;
import net.minecraft.resources.ResourceLocation;

public interface PacketResourceLocations {
    ResourceLocation GRAVE_OVERVIEW_S2C = idFor("grave_overview_s2c");
    ResourceLocation GRAVE_SELECTION_S2C = idFor("grave_selection_s2c");
    ResourceLocation PLAYER_SELECTION_S2C = idFor("player_selection_s2c");
    ResourceLocation CONFIG_SYNC_S2C = idFor("config_sync_s2c");

    ResourceLocation GRAVE_LOCKING_C2S = idFor("grave_locking_c2s");
    ResourceLocation GRAVE_RESTORE_C2S = idFor("grave_restore_c2s");
    ResourceLocation GRAVE_ROBBING_C2S = idFor("grave_robbing_c2s");
    ResourceLocation GRAVE_DELETE_C2S = idFor("grave_delete_c2s");
    ResourceLocation GRAVE_OBTAIN_KEYS_C2S = idFor("grave_obtain_key_c2s");
    ResourceLocation GRAVE_OBTAIN_COMPASS_C2S = idFor("grave_obtain_compass_c2s");
    ResourceLocation GRAVE_OVERVIEW_REQUEST_C2S = idFor("grave_overview_request_c2s");
    ResourceLocation GRAVE_SELECT_REQUEST_C2S = idFor("grave_select_request_c2s");
    ResourceLocation CONFIG_UPDATE_C2S = idFor("config_update_c2s");

    static ResourceLocation idFor(String path) {
        return new ResourceLocation(Yigd.MOD_ID, path);
    }
}
