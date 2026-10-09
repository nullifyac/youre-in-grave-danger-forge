package com.b1n_ry.yigd.config;

import com.b1n_ry.yigd.util.DropRule;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

public class CompatConfig {
    public boolean enableTravelersBackpackCompat = true;
    @Comment("While PUT_IN_GRAVE, other drop rules will be prioritized")
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public DropRule defaultTravelersBackpackDropRule = DropRule.PUT_IN_GRAVE;
    public boolean enableCuriosCompat = true;
    @Comment("While PUT_IN_GRAVE, other drop rules will be prioritized")
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public DropRule defaultCuriosDropRule = DropRule.PUT_IN_GRAVE;
}
