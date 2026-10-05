package com.b1n_ry.yigd.client.gui;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.packets.ClientPacketHandler;
import com.b1n_ry.yigd.packets.LightPlayerData;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.Tuple;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import javax.annotation.Nonnull;
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

    private static final FontRenderer FONT = Minecraft.getInstance().font;

    private final List<LightPlayerData> data;
    private final Screen previousScreen;

    private FilterButtonValue activeFilter = FilterButtonValue.WITH_GRAVES;
    private ITextComponent changeViewTooltip = this.getFilterTooltip();

    private final TextFieldWidget searchBox = new TextFieldWidget(FONT, 0, 0, 185, 20, StringTextComponent.EMPTY);
    private final Button changeViewButton = new Button(
            0,
            0,
            20,
            20,
            StringTextComponent.EMPTY,
            button -> {
                this.activeFilter = FilterButtonValue.values()[(this.activeFilter.ordinal() + 1) % FilterButtonValue.values().length];
                this.changeViewTooltip = this.getFilterTooltip();
                this.reloadButtons();
            },
            (button, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, this.changeViewTooltip, mouseX, mouseY)
    );

    private final List<Tuple<LightPlayerData, Button>> buttons = new ArrayList<>();
    private ResourceLocation scrollBarTexture = SCROLL_BAR;
    private double scrollDistance;
    private int scrollContentHeight;
    private int scrollBarHeight = SCROLL_MENU_HEIGHT;
    private boolean scrolling = false;

    public PlayerSelectionScreen(List<LightPlayerData> data, Screen previousScreen) {
        super(new TranslationTextComponent("text.yigd.gui.players_on_server"));
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
        super.buttons.clear();
        this.children.clear();
        this.buttons.clear();

        this.addButton(this.changeViewButton);
        this.addWidget(this.searchBox);

        for (LightPlayerData playerData : this.data) {
            if (!this.shouldShow(playerData)) continue;
            String searchContent = this.searchBox.getValue().toLowerCase(Locale.ROOT);
            String name = playerData.playerProfile().getName();
            if (!searchContent.isEmpty()) {
                if (name == null || !name.toLowerCase(Locale.ROOT).contains(searchContent)) continue;
            }

            ITextComponent tooltip = new TranslationTextComponent("text.yigd.gui.unclaimed_count", playerData.unclaimedCount())
                    .append("\n")
                    .append(new TranslationTextComponent("text.yigd.gui.destroyed_count", playerData.destroyedCount()))
                    .append("\n")
                    .append(new TranslationTextComponent("text.yigd.gui.total_count", playerData.graveCount()));
            Button button = new Button(
                    0,
                    0,
                    200,
                    20,
                    StringTextComponent.EMPTY,
                    b -> ClientPacketHandler.sendGraveSelectionRequest(playerData.playerProfile()),
                    (b, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, tooltip, mouseX, mouseY)
            );

            this.buttons.add(new Tuple<>(playerData, button));
            this.addButton(button);
        }

        this.scrollContentHeight = this.buttons.size() * 20;
        float fraction = this.scrollContentHeight == 0 ? 1.0F : SCROLL_MENU_HEIGHT / (float) this.scrollContentHeight;
        this.scrollBarHeight = Math.max(4, (int) (Math.min(1.0F, fraction) * SCROLL_MENU_HEIGHT));
    }

    private boolean shouldShow(LightPlayerData playerData) {
        switch (this.activeFilter) {
            case WITH_GRAVES:
                return playerData.graveCount() > 0;
            case WITH_CLAIMED:
                return playerData.graveCount() - playerData.destroyedCount() - playerData.unclaimedCount() > 0;
            case WITH_DESTROYED:
                return playerData.destroyedCount() > 0;
            case WITH_UNCLAIMED:
                return playerData.unclaimedCount() > 0;
            default:
                return false;
        }
    }

    private ITextComponent getFilterTooltip() {
        switch (this.activeFilter) {
            case WITH_GRAVES:
                return new TranslationTextComponent("button.yigd.gui.showing_with_data");
            case WITH_UNCLAIMED:
                return new TranslationTextComponent("button.yigd.gui.showing_with_unclaimed");
            case WITH_CLAIMED:
                return new TranslationTextComponent("button.yigd.gui.showing_with_claimed");
            case WITH_DESTROYED:
                return new TranslationTextComponent("button.yigd.gui.showing_with_destroyed");
            default:
                return StringTextComponent.EMPTY;
        }
    }

    private void setScrollDistance(double scrollDistance) {
        this.scrollDistance = MathHelper.clamp(scrollDistance, 0.0, this.getMaxScrollAmount());
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
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;
        if (this.isScrollBarMouseOver(mouseX, mouseY, leftEdge + 8, topEdge + 49)) {
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
    public void render(@Nonnull MatrixStack poseStack, int mouseX, int mouseY, float partialTick) {
        super.render(poseStack, mouseX, mouseY, partialTick);

        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;

        blitSprite(poseStack, WINDOW_BG, leftEdge, topEdge, SCREEN_WIDTH, SCREEN_HEIGHT);
        FONT.draw(poseStack, this.title, leftEdge + 8, topEdge + 8, 0x404040);

        this.searchBox.x = leftEdge + 8;
        this.searchBox.y = topEdge + 24;
        this.searchBox.render(poseStack, mouseX, mouseY, partialTick);

        this.changeViewButton.x = leftEdge + 198;
        this.changeViewButton.y = topEdge + 24;
        this.changeViewButton.render(poseStack, mouseX, mouseY, partialTick);
        switch (this.activeFilter) {
            case WITH_GRAVES:
                this.itemRenderer.renderAndDecorateItem(Items.BARRIER.getDefaultInstance(), leftEdge + 200, topEdge + 26);
                break;
            case WITH_UNCLAIMED:
                blitSprite(poseStack, CLAIMED_GRAVE, leftEdge + 200, topEdge + 26, 16, 16);
                break;
            case WITH_DESTROYED:
                blitSprite(poseStack, DESTROYED_GRAVE, leftEdge + 200, topEdge + 26, 16, 16);
                break;
            case WITH_CLAIMED:
                blitSprite(poseStack, UNCLAIMED_GRAVE, leftEdge + 200, topEdge + 26, 16, 16);
                break;
            default:
                break;
        }

        this.renderScrollbar(poseStack, mouseX, mouseY, partialTick, leftEdge + 8, topEdge + 49);
    }

    private void renderScrollbar(MatrixStack poseStack, int mouseX, int mouseY, float partialTick, int x, int y) {
        blitSprite(poseStack, SLOT, x, y, 202, SCROLL_MENU_HEIGHT + 2);
        blitSprite(poseStack, SLOT, x + 202, y, 8, SCROLL_MENU_HEIGHT + 2);

        int movedScrollBar = this.getScrollBarOffset();
        int scrollBarX = x + 203;
        int scrollBarY = y + 1 + movedScrollBar;
        blitSprite(poseStack, this.scrollBarTexture, scrollBarX, scrollBarY, 6, this.scrollBarHeight);

        enableGuiScissor(0, y + 1, this.width, y + SCROLL_MENU_HEIGHT + 1);
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
            button.x = x + 21;
            button.y = top;
            button.render(poseStack, mouseX, mouseY, partialTick);

            ItemStack stack = Items.PLAYER_HEAD.getDefaultInstance();
            CompoundNBT profileNbt = new CompoundNBT();
            GameProfile profile = tuple.getA().playerProfile();
            NBTUtil.writeGameProfile(profileNbt, profile);
            stack.getOrCreateTag().put("SkullOwner", profileNbt);
            this.itemRenderer.renderAndDecorateItem(stack, x + 1, top + 1);
            String displayName = profile.getName() == null ? "PLAYER_NOT_FOUND" : profile.getName();
            FONT.draw(poseStack, displayName, x + 23, top + 6, 0xFFFFFF);
        }
        RenderSystem.disableScissor();
    }

    private boolean isInsideScrollMenu(double mouseX, double mouseY) {
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2 + 8;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2 + 49;
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

    private static void blitSprite(MatrixStack poseStack, ResourceLocation sprite, int x, int y, int width, int height) {
        ResourceLocation texture = spriteTexture(sprite);
        Minecraft.getInstance().getTextureManager().bind(texture);
        blit(poseStack, x, y, 0, 0.0F, 0.0F, width, height, width, height);
    }

    private enum FilterButtonValue {
        WITH_GRAVES,
        WITH_CLAIMED,
        WITH_UNCLAIMED,
        WITH_DESTROYED
    }
}
