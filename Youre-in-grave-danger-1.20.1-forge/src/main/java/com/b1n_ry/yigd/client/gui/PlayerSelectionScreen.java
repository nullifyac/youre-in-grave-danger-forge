package com.b1n_ry.yigd.client.gui;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.packets.ClientPacketHandler;
import com.b1n_ry.yigd.packets.LightPlayerData;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlayerSelectionScreen extends Screen {
    private static final ResourceLocation WINDOW_BG = new ResourceLocation(Yigd.MOD_ID, "window_bg");
    private static final ResourceLocation SLOT = new ResourceLocation(Yigd.MOD_ID, "slot");
    private static final ResourceLocation SCROLL_BAR = new ResourceLocation(Yigd.MOD_ID, "scroll_bar");
    private static final ResourceLocation SCROLL_BAR_PRESSED = new ResourceLocation(Yigd.MOD_ID, "scroll_bar_pressed");

    private static final ResourceLocation CLAIMED_GRAVE = new ResourceLocation(Yigd.MOD_ID, "claimed_grave");
    private static final ResourceLocation DESTROYED_GRAVE = new ResourceLocation(Yigd.MOD_ID, "destroyed_grave");
    private static final ResourceLocation UNCLAIMED_GRAVE = new ResourceLocation(Yigd.MOD_ID, "unclaimed_grave");

    private static final int SCREEN_WIDTH = 226;
    private static final int SCREEN_HEIGHT = 187;
    private static final int SCROLL_MENU_HEIGHT = 128;

    private static final Font FONT = Minecraft.getInstance().font;

    private final List<LightPlayerData> data;
    private final Screen previousScreen;

    private FilterButtonValue activeFilter = FilterButtonValue.WITH_GRAVES;

    private final EditBox searchBox = new EditBox(FONT, 0, 0, 185, 20, Component.empty());
    private final Button changeViewButton = Button.builder(Component.empty(), button -> {
        this.activeFilter = FilterButtonValue.values()[(this.activeFilter.ordinal() + 1) % FilterButtonValue.values().length];
        button.setTooltip(Tooltip.create(this.getFilterTooltip()));
        this.reloadButtons();
    }).size(20, 20).tooltip(Tooltip.create(this.getFilterTooltip())).build();

    private final List<Tuple<LightPlayerData, Button>> buttons = new ArrayList<>();
    private ImageWidget scrollBar = new ImageWidget(0, 0, 6, SCROLL_MENU_HEIGHT, spriteTexture(SCROLL_BAR));
    private double scrollDistance;
    private int scrollContentHeight;
    private boolean scrolling = false;

    public PlayerSelectionScreen(List<LightPlayerData> data, Screen previousScreen) {
        super(Component.translatable("text.yigd.gui.players_on_server"));
        this.data = data;
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        this.searchBox.setResponder(s -> this.reloadButtons());
        this.reloadButtons();
        super.init();
    }

    private void reloadButtons() {
        this.scrollDistance = 0.0D;
        this.clearWidgets();
        this.buttons.clear();

        this.addWidget(this.changeViewButton);
        this.addWidget(this.searchBox);

        for (LightPlayerData playerData : this.data) {
            if (!this.shouldShow(playerData)) continue;
            String searchContent = this.searchBox.getValue().toLowerCase(Locale.ROOT);
            String name = playerData.playerProfile().getName();
            if (!searchContent.isEmpty()) {
                if (name == null || !name.toLowerCase(Locale.ROOT).contains(searchContent)) continue;
            }

            Button button = Button.builder(Component.empty(), b ->
                    ClientPacketHandler.sendGraveSelectionRequest(playerData.playerProfile()))
                    .size(200, 20)
                    .tooltip(Tooltip.create(
                            Component.translatable("text.yigd.gui.unclaimed_count", playerData.unclaimedCount())
                                    .append("\n")
                                    .append(Component.translatable("text.yigd.gui.destroyed_count", playerData.destroyedCount()))
                                    .append("\n")
                                    .append(Component.translatable("text.yigd.gui.total_count", playerData.graveCount()))))
                    .build();

            this.buttons.add(new Tuple<>(playerData, button));
            this.addWidget(button);
        }

        this.scrollContentHeight = this.buttons.size() * 20;
        float fraction = this.scrollContentHeight == 0 ? 1.0F : SCROLL_MENU_HEIGHT / (float) this.scrollContentHeight;
        this.scrollBar.setHeight(Math.max(4, (int) (Math.min(1.0F, fraction) * SCROLL_MENU_HEIGHT)));
    }

    private boolean shouldShow(LightPlayerData playerData) {
        return switch (this.activeFilter) {
            case WITH_GRAVES -> playerData.graveCount() > 0;
            case WITH_CLAIMED -> playerData.graveCount() - playerData.destroyedCount() - playerData.unclaimedCount() > 0;
            case WITH_DESTROYED -> playerData.destroyedCount() > 0;
            case WITH_UNCLAIMED -> playerData.unclaimedCount() > 0;
        };
    }

    private Component getFilterTooltip() {
        return switch (this.activeFilter) {
            case WITH_GRAVES -> Component.translatable("button.yigd.gui.showing_with_data");
            case WITH_UNCLAIMED -> Component.translatable("button.yigd.gui.showing_with_unclaimed");
            case WITH_CLAIMED -> Component.translatable("button.yigd.gui.showing_with_claimed");
            case WITH_DESTROYED -> Component.translatable("button.yigd.gui.showing_with_destroyed");
        };
    }

    private void setScrollDistance(double scrollDistance) {
        this.scrollDistance = Mth.clamp(scrollDistance, 0.0, this.getMaxScrollAmount());
    }

    private int getMaxScrollAmount() {
        return Math.max(0, this.scrollContentHeight - SCROLL_MENU_HEIGHT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && this.minecraft != null && !this.searchBox.isFocused()) {
            this.minecraft.setScreen(this.previousScreen);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!this.isInsideScrollMenu(mouseX, mouseY)) return false;
        this.setScrollDistance(this.scrollDistance - scrollY * 9.0D);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.setFocused(null);
        if (this.scrollBar.isMouseOver(mouseX, mouseY)) {
            this.scrollBar = new ImageWidget(0, 0, this.scrollBar.getWidth(), this.scrollBar.getHeight(), spriteTexture(SCROLL_BAR_PRESSED));
            this.scrolling = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.scrolling) {
            this.scrollBar = new ImageWidget(0, 0, this.scrollBar.getWidth(), this.scrollBar.getHeight(), spriteTexture(SCROLL_BAR));
        }
        this.scrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrolling) {
            int scrollBarHeight = this.scrollBar.getHeight();
            int holderTop = this.height / 2 - SCREEN_HEIGHT / 2 + 49;
            int holderBottom = holderTop + SCROLL_MENU_HEIGHT;
            if (mouseY < holderTop) {
                this.setScrollDistance(0.0D);
            } else if (mouseY > holderBottom) {
                this.setScrollDistance(this.getMaxScrollAmount());
            } else {
                float barMenuRatio = this.getMaxScrollAmount() / (float) (SCROLL_MENU_HEIGHT - scrollBarHeight);
                this.setScrollDistance(this.scrollDistance + dragY * barMenuRatio);
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;

        blitSprite(graphics, WINDOW_BG, leftEdge, topEdge, SCREEN_WIDTH, SCREEN_HEIGHT);
        graphics.drawString(FONT, this.title, leftEdge + 8, topEdge + 8, 0x404040, false);

        this.searchBox.setPosition(leftEdge + 8, topEdge + 24);
        this.searchBox.render(graphics, mouseX, mouseY, partialTick);

        this.changeViewButton.setPosition(leftEdge + 198, topEdge + 24);
        this.changeViewButton.render(graphics, mouseX, mouseY, partialTick);
        switch (this.activeFilter) {
            case WITH_GRAVES -> graphics.renderItem(Items.BARRIER.getDefaultInstance(), leftEdge + 200, topEdge + 26);
            case WITH_UNCLAIMED -> blitSprite(graphics, CLAIMED_GRAVE, leftEdge + 200, topEdge + 26, 16, 16);
            case WITH_DESTROYED -> blitSprite(graphics, DESTROYED_GRAVE, leftEdge + 200, topEdge + 26, 16, 16);
            case WITH_CLAIMED -> blitSprite(graphics, UNCLAIMED_GRAVE, leftEdge + 200, topEdge + 26, 16, 16);
        }

        this.renderScrollbar(graphics, mouseX, mouseY, partialTick, leftEdge + 8, topEdge + 49);
    }

    private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int x, int y) {
        blitSprite(graphics, SLOT, x, y, 202, SCROLL_MENU_HEIGHT + 2);
        blitSprite(graphics, SLOT, x + 202, y, 8, SCROLL_MENU_HEIGHT + 2);

        int movedScrollBar = this.getMaxScrollAmount() == 0 ? 0 : (int) ((this.scrollDistance / this.getMaxScrollAmount()) * (SCROLL_MENU_HEIGHT - this.scrollBar.getHeight()));
        this.scrollBar.setPosition(x + 203, y + 1 + movedScrollBar);
        this.scrollBar.render(graphics, mouseX, mouseY, partialTick);

        graphics.enableScissor(0, y + 1, this.width, y + SCROLL_MENU_HEIGHT + 1);
        for (int i = 0; i < this.buttons.size(); i++) {
            Tuple<LightPlayerData, Button> tuple = this.buttons.get(i);
            Button button = tuple.getB();

            if ((i + 1) * 20 < this.scrollDistance || i * 20 - this.scrollDistance > SCROLL_MENU_HEIGHT) {
                button.active = false;
                continue;
            } else {
                button.active = true;
            }

            int top = y + 1 + i * 20 - (int) this.scrollDistance;
            button.setPosition(x + 21, top);
            button.render(graphics, mouseX, mouseY, partialTick);

            ItemStack stack = Items.PLAYER_HEAD.getDefaultInstance();
            CompoundTag profileNbt = new CompoundTag();
            GameProfile profile = tuple.getA().playerProfile();
            NbtUtils.writeGameProfile(profileNbt, profile);
            stack.getOrCreateTag().put("SkullOwner", profileNbt);
            graphics.renderItem(stack, x + 1, top + 1);
            String displayName = profile.getName() == null ? "PLAYER_NOT_FOUND" : profile.getName();
            graphics.drawString(FONT, displayName, x + 23, top + 6, 0xFFFFFF, false);
        }
        graphics.disableScissor();
    }

    private boolean isInsideScrollMenu(double mouseX, double mouseY) {
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2 + 8;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2 + 49;
        return mouseX >= leftEdge && mouseX <= leftEdge + 210 && mouseY >= topEdge && mouseY <= topEdge + SCROLL_MENU_HEIGHT + 2;
    }

    private static ResourceLocation spriteTexture(ResourceLocation sprite) {
        return new ResourceLocation(sprite.getNamespace(), "textures/gui/sprites/" + sprite.getPath() + ".png");
    }

    private static void blitSprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        ResourceLocation texture = spriteTexture(sprite);
        graphics.blit(texture, x, y, 0, 0.0F, 0.0F, width, height, width, height);
    }

    private enum FilterButtonValue {
        WITH_GRAVES,
        WITH_CLAIMED,
        WITH_UNCLAIMED,
        WITH_DESTROYED
    }
}
