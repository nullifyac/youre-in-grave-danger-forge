package com.b1n_ry.yigd.client.gui;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.components.ExpComponent;
import com.b1n_ry.yigd.data.GraveStatus;
import com.b1n_ry.yigd.packets.ClientPacketHandler;
import com.b1n_ry.yigd.packets.LightGraveData;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class GraveSelectionScreen extends Screen {
    private static final ResourceLocation WINDOW_BG = new ResourceLocation(Yigd.MOD_ID, "window_bg");
    private static final ResourceLocation SLOT = new ResourceLocation(Yigd.MOD_ID, "slot");
    private static final ResourceLocation SCROLL_BAR = new ResourceLocation(Yigd.MOD_ID, "scroll_bar");
    private static final ResourceLocation SCROLL_BAR_PRESSED = new ResourceLocation(Yigd.MOD_ID, "scroll_bar_pressed");

    private static final ResourceLocation CLAIMED_GRAVE = new ResourceLocation(Yigd.MOD_ID, "claimed_grave");
    private static final ResourceLocation CLAIMED_GRAVE_CROSS = new ResourceLocation(Yigd.MOD_ID, "claimed_grave_cross");
    private static final ResourceLocation DESTROYED_GRAVE = new ResourceLocation(Yigd.MOD_ID, "destroyed_grave");
    private static final ResourceLocation DESTROYED_GRAVE_CROSS = new ResourceLocation(Yigd.MOD_ID, "destroyed_grave_cross");
    private static final ResourceLocation UNCLAIMED_GRAVE = new ResourceLocation(Yigd.MOD_ID, "unclaimed_grave");
    private static final ResourceLocation UNCLAIMED_GRAVE_CROSS = new ResourceLocation(Yigd.MOD_ID, "unclaimed_grave_cross");
    private static final ResourceLocation SHOW_STATUS = new ResourceLocation(Yigd.MOD_ID, "show_status");
    private static final ResourceLocation HIDE_STATUS = new ResourceLocation(Yigd.MOD_ID, "hide_status");

    private static final int SCREEN_WIDTH = 248;
    private static final int SCREEN_HEIGHT = 164;
    private static final int SCROLL_MENU_HEIGHT = 128;

    private static final Font FONT = Minecraft.getInstance().font;

    private final List<LightGraveData> data;
    private final Screen previousScreen;

    private final List<Button> buttons = new ArrayList<>();
    private final List<Integer> overlayColorList = new ArrayList<>();
    private ResourceLocation scrollBarTexture = SCROLL_BAR;
    private int scrollBarHeight = SCROLL_MENU_HEIGHT;

    private double scrollDistance;
    private int scrollContentHeight;
    private boolean scrolling = false;

    private boolean showClaimed = false;
    private boolean showDestroyed = true;
    private boolean showUnclaimed = true;
    private boolean overlayColors = false;

    private Component claimedTooltip = Component.empty();
    private Component destroyedTooltip = Component.empty();
    private Component unclaimedTooltip = Component.empty();
    private Component overlayTooltip = Component.empty();

    private final Button claimedToggle = new Button(0, 0, 20, 20, Component.empty(), button -> {
        this.showClaimed = !this.showClaimed;
        this.reloadButtons();
    }, (button, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, this.claimedTooltip, mouseX, mouseY));
    private final Button destroyedToggle = new Button(0, 0, 20, 20, Component.empty(), button -> {
        this.showDestroyed = !this.showDestroyed;
        this.reloadButtons();
    }, (button, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, this.destroyedTooltip, mouseX, mouseY));
    private final Button unclaimedToggle = new Button(0, 0, 20, 20, Component.empty(), button -> {
        this.showUnclaimed = !this.showUnclaimed;
        this.reloadButtons();
    }, (button, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, this.unclaimedTooltip, mouseX, mouseY));
    private final Button overlayToggle = new Button(0, 0, 20, 20, Component.empty(), button -> {
        this.overlayColors = !this.overlayColors;
        this.reloadButtons();
    }, (button, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, this.overlayTooltip, mouseX, mouseY));

    public GraveSelectionScreen(List<LightGraveData> data, GameProfile profile, Screen previousScreen) {
        super(Component.translatable("text.yigd.gui.graves_of", profile.getName()));
        this.data = data;
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        this.reloadButtons();
        super.init();
    }

    private void reloadButtons() {
        this.scrollDistance = 0.0D;
        this.clearWidgets();
        this.buttons.clear();
        this.overlayColorList.clear();

        for (int index = this.data.size() - 1; index >= 0; index--) {
            LightGraveData graveData = this.data.get(index);
            if (!this.shouldDisplay(graveData.status())) continue;

            BlockPos gravePos = graveData.pos();
            String dimensionName = graveData.registryKey().location().toString();
            Component tooltip = Component.translatable("text.yigd.gui.grave_location", gravePos.getX(), gravePos.getY(), gravePos.getZ())
                    .append("\n")
                    .append(Component.translatable("text.yigd.dimension.name." + dimensionName))
                    .append("\n")
                    .append(Component.translatable("text.yigd.gui.item_count", graveData.itemCount()))
                    .append("\n")
                    .append(Component.translatable("text.yigd.gui.level_count", ExpComponent.xpToLevels(graveData.xpPoints())));
            Button button = new Button(
                    0,
                    0,
                    200,
                    20,
                    graveData.deathMessage().getDeathMessage(),
                    b -> ClientPacketHandler.sendGraveOverviewRequest(graveData.id()),
                    (b, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, tooltip, mouseX, mouseY)
            );

            this.overlayColorList.add(graveData.status().getTransparentColor());
            this.buttons.add(button);
            this.addWidget(button);
        }

        this.scrollContentHeight = this.buttons.size() * 20;
        float fraction = this.scrollContentHeight == 0 ? 1.0F : SCROLL_MENU_HEIGHT / (float) this.scrollContentHeight;
        this.scrollBarHeight = Math.max(4, (int) (Math.min(1.0F, fraction) * SCROLL_MENU_HEIGHT));

        this.claimedTooltip = Component.translatable(this.showClaimed ? "button.yigd.gui.viewing_claimed" : "button.yigd.gui.hiding_claimed");
        this.destroyedTooltip = Component.translatable(this.showDestroyed ? "button.yigd.gui.viewing_destroyed" : "button.yigd.gui.hiding_destroyed");
        this.unclaimedTooltip = Component.translatable(this.showUnclaimed ? "button.yigd.gui.viewing_unclaimed" : "button.yigd.gui.hiding_unclaimed");
        this.overlayTooltip = Component.translatable(this.overlayColors ? "button.yigd.gui.showing_status" : "button.yigd.gui.hiding_status");

        this.addWidget(this.claimedToggle);
        this.addWidget(this.destroyedToggle);
        this.addWidget(this.unclaimedToggle);
        this.addWidget(this.overlayToggle);
    }

    private boolean shouldDisplay(GraveStatus status) {
        return switch (status) {
            case CLAIMED -> this.showClaimed;
            case DESTROYED -> this.showDestroyed;
            case UNCLAIMED -> this.showUnclaimed;
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!this.isInsideScrollMenu(mouseX, mouseY)) return false;
        this.setScrollDistance(this.scrollDistance - scrollY * 9.0D);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;
        if (this.isScrollBarMouseOver(mouseX, mouseY, leftEdge + 8, topEdge + 20)) {
            this.scrollBarTexture = SCROLL_BAR_PRESSED;
            this.scrolling = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.scrolling) {
            this.scrollBarTexture = SCROLL_BAR;
        }
        this.scrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrolling) {
            int scrollBarHeight = this.scrollBarHeight;
            int holderTop = this.height / 2 - SCREEN_HEIGHT / 2 + 8;
            if (mouseY < holderTop) {
                this.setScrollDistance(0.0D);
            } else if (mouseY > holderTop + SCROLL_MENU_HEIGHT) {
                this.setScrollDistance(this.getMaxScrollAmount());
            } else {
                float barMenuRatio = this.getMaxScrollAmount() / (float) (SCROLL_MENU_HEIGHT - scrollBarHeight);
                this.setScrollDistance(this.scrollDistance + dragY * barMenuRatio);
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && this.minecraft != null) {
            this.minecraft.setScreen(this.previousScreen);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(@NotNull PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        super.render(poseStack, mouseX, mouseY, partialTick);

        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;
        blitSprite(poseStack, WINDOW_BG, leftEdge, topEdge, SCREEN_WIDTH, SCREEN_HEIGHT);

        FONT.draw(poseStack, this.title, leftEdge + 8, topEdge + 8, 0x404040);

        this.renderScrollMenu(poseStack, mouseX, mouseY, partialTick, leftEdge + 8, topEdge + 20);
        this.renderToggleButtons(poseStack, mouseX, mouseY, partialTick, leftEdge + 220, topEdge + 20);
    }

    private void renderScrollMenu(PoseStack poseStack, int mouseX, int mouseY, float partialTick, int x, int y) {
        blitSprite(poseStack, SLOT, x, y, 202, SCROLL_MENU_HEIGHT + 2);
        blitSprite(poseStack, SLOT, x + 202, y, 8, SCROLL_MENU_HEIGHT + 2);

        int movedScrollBar = this.getScrollBarOffset();
        int scrollBarX = x + 203;
        int scrollBarY = y + 1 + movedScrollBar;
        blitSprite(poseStack, this.scrollBarTexture, scrollBarX, scrollBarY, 6, this.scrollBarHeight);

        this.enableGuiScissor(0, y + 1, this.width, y + SCROLL_MENU_HEIGHT + 1);
        for (int i = 0; i < this.buttons.size(); i++) {
            Button button = this.buttons.get(i);
            int top = y + 1 + i * 20 - (int) this.scrollDistance;
            if ((i + 1) * 20 < this.scrollDistance || i * 20 - this.scrollDistance > SCROLL_MENU_HEIGHT) {
                button.active = false;
                continue;
            } else {
                button.active = true;
            }
            button.x = x + 1;
            button.y = top;
            button.render(poseStack, mouseX, mouseY, partialTick);
            if (this.overlayColors) {
                fill(poseStack, button.x, button.y, button.x + button.getWidth(), button.y + button.getHeight(), this.overlayColorList.get(i));
            }
        }
        RenderSystem.disableScissor();
    }

    private void renderToggleButtons(PoseStack poseStack, int mouseX, int mouseY, float partialTick, int x, int y) {
        this.claimedToggle.x = x;
        this.claimedToggle.y = y;
        this.claimedToggle.render(poseStack, mouseX, mouseY, partialTick);
        blitSprite(poseStack, this.showClaimed ? CLAIMED_GRAVE : CLAIMED_GRAVE_CROSS, x + 2, y + 2, 16, 16);

        this.unclaimedToggle.x = x;
        this.unclaimedToggle.y = y + 24;
        this.unclaimedToggle.render(poseStack, mouseX, mouseY, partialTick);
        blitSprite(poseStack, this.showUnclaimed ? UNCLAIMED_GRAVE : UNCLAIMED_GRAVE_CROSS, x + 2, y + 26, 16, 16);

        this.destroyedToggle.x = x;
        this.destroyedToggle.y = y + 48;
        this.destroyedToggle.render(poseStack, mouseX, mouseY, partialTick);
        blitSprite(poseStack, this.showDestroyed ? DESTROYED_GRAVE : DESTROYED_GRAVE_CROSS, x + 2, y + 50, 16, 16);

        this.overlayToggle.x = x;
        this.overlayToggle.y = y + 72;
        this.overlayToggle.render(poseStack, mouseX, mouseY, partialTick);
        blitSprite(poseStack, this.overlayColors ? SHOW_STATUS : HIDE_STATUS, x + 2, y + 74, 16, 16);
    }

    private boolean isInsideScrollMenu(double mouseX, double mouseY) {
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2 + 8;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2 + 20;
        return mouseX >= leftEdge && mouseX <= leftEdge + 210 && mouseY >= topEdge && mouseY <= topEdge + SCROLL_MENU_HEIGHT + 2;
    }

    private int getScrollBarOffset() {
        return this.getMaxScrollAmount() == 0 ? 0 : (int) ((this.scrollDistance / this.getMaxScrollAmount()) * (SCROLL_MENU_HEIGHT - this.scrollBarHeight));
    }

    private boolean isScrollBarMouseOver(double mouseX, double mouseY, int baseX, int baseY) {
        int scrollBarX = baseX + 203;
        int scrollBarY = baseY + 1 + this.getScrollBarOffset();
        return mouseX >= scrollBarX
                && mouseX <= scrollBarX + 6
                && mouseY >= scrollBarY
                && mouseY <= scrollBarY + this.scrollBarHeight;
    }

    private void enableGuiScissor(int x0, int y0, int x1, int y1) {
        double scale = this.minecraft.getWindow().getGuiScale();
        int x = (int) (x0 * scale);
        int y = (int) ((this.minecraft.getWindow().getGuiScaledHeight() - y1) * scale);
        int width = (int) ((x1 - x0) * scale);
        int height = (int) ((y1 - y0) * scale);
        RenderSystem.enableScissor(x, y, width, height);
    }

    private static ResourceLocation spriteTexture(ResourceLocation sprite) {
        return new ResourceLocation(sprite.getNamespace(), "textures/gui/sprites/" + sprite.getPath() + ".png");
    }

    private static void blitSprite(PoseStack poseStack, ResourceLocation sprite, int x, int y, int width, int height) {
        ResourceLocation texture = spriteTexture(sprite);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        blit(poseStack, x, y, 0, 0.0F, 0.0F, width, height, width, height);
    }
}
