package com.b1n_ry.yigd.client.gui;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.networking.LightPlayerData;
import com.b1n_ry.yigd.networking.packets.GraveSelectionRequestC2SPacket;
import com.b1n_ry.yigd.networking.packets.PlayerSelectionS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class PlayerSelectionScreen extends Screen {
    private static final Identifier WINDOW_BG = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "window_bg");
    private static final Identifier SLOT = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "slot");
    private static final Identifier SCROLL_BAR = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "scroll_bar");
    private static final Identifier SCROLL_BAR_PRESSED = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "scroll_bar_pressed");

    private static final Identifier CLAIMED_GRAVE = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "claimed_grave");
    private static final Identifier DESTROYED_GRAVE = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "destroyed_grave");
    private static final Identifier UNCLAIMED_GRAVE = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "unclaimed_grave");

    private final List<LightPlayerData> data;
    private final Screen previousScreen;

    private FilterButtonValue activeFilter = FilterButtonValue.WITH_GRAVES;

    private final EditBox searchBox = new EditBox(FONT, 185, 20, Component.empty());
    private final Button changeViewButton = Button.builder(Component.empty(), btn -> {
            this.activeFilter = FilterButtonValue.values()[(this.activeFilter.ordinal() + 1) % FilterButtonValue.values().length];
            btn.setTooltip(Tooltip.create(this.getFilterTooltip()));
            this.reloadButtons();
    })
            .size(20, 20)
            .tooltip(Tooltip.create(this.getFilterTooltip()))
            .build();
    private final List<Tuple<LightPlayerData, Button>> buttons = new ArrayList<>();

    private ImageWidget scrollBar = ImageWidget.sprite(6, SCROLL_MENU_HEIGHT, SCROLL_BAR);
    private double scrollDistance;
    private int scrollContentHeight;

    private boolean scrolling = false;

    private static final int SCREEN_WIDTH = 226;
    private static final int SCREEN_HEIGHT = 187;
    private static final int SCROLL_MENU_HEIGHT = 128;

    private static final Font FONT = Minecraft.getInstance().font;

    public PlayerSelectionScreen(List<LightPlayerData> data, Screen previousScreen) {
        super(Component.translatable("text.yigd.gui.players_on_server"));
        this.data = data;
        this.previousScreen = previousScreen;
    }

    private void reloadButtons() {
        this.scrollDistance = 0.0D;
        this.clearWidgets();
        this.buttons.clear();

        this.addWidget(this.changeViewButton);
        this.addWidget(this.searchBox);

        for (LightPlayerData playerData : this.data) {
            boolean show = switch (this.activeFilter) {
                case WITH_GRAVES -> playerData.graveCount() > 0;
                case WITH_CLAIMED -> playerData.graveCount() - playerData.destroyedCount() - playerData.unclaimedCount() > 0;
                case WITH_DESTROYED -> playerData.destroyedCount() > 0;
                case WITH_UNCLAIMED -> playerData.unclaimedCount() > 0;
            };
            if (!show) continue;
            String searchContent = this.searchBox.getValue();
            if (!searchContent.isEmpty()) {
                Optional<String> name = playerData.playerProfile().name();
                if (name.isEmpty()) continue;
                if (!name.get().toLowerCase(Locale.ROOT).contains(searchContent.toLowerCase(Locale.ROOT))) continue;
            }

            Button button = Button.builder(Component.empty(),
                    btn -> ClientPacketDistributor.sendToServer(new GraveSelectionRequestC2SPacket(playerData.playerProfile())))
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

        float fraction = SCROLL_MENU_HEIGHT / (float) this.scrollContentHeight;
        this.scrollBar.setHeight(Math.max(4, (int) (Math.min(1f, fraction) * SCROLL_MENU_HEIGHT)));
    }

    private Component getFilterTooltip() {
        return switch (this.activeFilter) {
            case WITH_GRAVES -> Component.translatable("button.yigd.gui.showing_with_data");
            case WITH_UNCLAIMED -> Component.translatable("button.yigd.gui.showing_with_unclaimed");
            case WITH_CLAIMED -> Component.translatable("button.yigd.gui.showing_with_claimed");
            case WITH_DESTROYED -> Component.translatable("button.yigd.gui.showing_with_destroyed");
        };
    }

    @Override
    public void init() {
        this.searchBox.setResponder(s -> this.reloadButtons());
        this.reloadButtons();
        super.init();
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
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && this.minecraft != null && !this.searchBox.isFocused()) {
            this.minecraft.setScreen(this.previousScreen);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.isMouseOver(mouseX, mouseY)) return false;
        this.setScrollDistance(this.scrollDistance - scrollY * 9.0D);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        this.clearFocus();
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.scrollBar.isMouseOver(mouseX, mouseY)) {
            this.scrollBar = ImageWidget.sprite(this.scrollBar.getWidth(), this.scrollBar.getHeight(), SCROLL_BAR_PRESSED);
            this.scrolling = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.scrolling) {
            this.scrollBar = ImageWidget.sprite(this.scrollBar.getWidth(), this.scrollBar.getHeight(), SCROLL_BAR);
        }
        this.scrolling = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseY = event.y();
        if (this.scrolling) {
            int scrollBarHeight = this.scrollBar.getHeight();
            int holderTop = this.height / 2 - SCREEN_HEIGHT / 2 + 8;
            if (mouseY < holderTop) {
                this.setScrollDistance(0.0D);
            } else if (mouseY > this.scrollBar.getY() + SCROLL_MENU_HEIGHT) {
                this.setScrollDistance(this.getMaxScrollAmount());
            } else {
                float barMenuRatio = this.getMaxScrollAmount() / (float) (SCROLL_MENU_HEIGHT - scrollBarHeight);
                this.setScrollDistance(this.scrollDistance + dragY * barMenuRatio);
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WINDOW_BG, leftEdge, topEdge, SCREEN_WIDTH, SCREEN_HEIGHT);

        graphics.text(FONT, this.title, leftEdge + 8, topEdge + 8, 0xFF404040, false);

        this.searchBox.setPosition(leftEdge + 8, topEdge + 24);
        this.searchBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.changeViewButton.setPosition(leftEdge + 198, topEdge + 24);
        this.changeViewButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        switch (this.activeFilter) {
            case WITH_GRAVES -> graphics.item(Items.BARRIER.getDefaultInstance(), leftEdge + 200, topEdge + 26);
            case WITH_UNCLAIMED -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CLAIMED_GRAVE, leftEdge + 200, topEdge + 26, 16, 16);
            case WITH_DESTROYED -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DESTROYED_GRAVE, leftEdge + 200, topEdge + 26, 16, 16);
            case WITH_CLAIMED -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, UNCLAIMED_GRAVE, leftEdge + 200, topEdge + 26, 16, 16);
        }

        this.renderScrollbar(graphics, mouseX, mouseY, partialTick, leftEdge + 8, topEdge + 49);
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int x, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, y, 202, SCROLL_MENU_HEIGHT + 2);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x + 202, y, 8, SCROLL_MENU_HEIGHT + 2);

        int movedScrollBar = this.getMaxScrollAmount() == 0 ? 0 : (int) ((this.scrollDistance / this.getMaxScrollAmount()) * (SCROLL_MENU_HEIGHT - this.scrollBar.getHeight()));
        this.scrollBar.setPosition(x + 203, y + 1 + movedScrollBar);
        this.scrollBar.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.enableScissor(0, y + 1, this.width, y + SCROLL_MENU_HEIGHT + 1);
        for (int i = 0; i < this.buttons.size(); i++) {
            Tuple<LightPlayerData, Button> tuple = this.buttons.get(i);
            LightPlayerData playerData = tuple.getA();
            Button button = tuple.getB();

            if ((i + 1) * 20 < this.scrollDistance || i * 20 - this.scrollDistance > SCROLL_MENU_HEIGHT) {
                button.active = false;
                continue;
            } else {
                button.active = true;
            }
            button.setPosition(x + 1, y + 1 + i * 20 - (int) this.scrollDistance);
            button.extractRenderState(graphics, mouseX, mouseY, partialTick);

            ItemStack stack = Items.PLAYER_HEAD.getDefaultInstance();
            stack.set(DataComponents.PROFILE, playerData.playerProfile());
            String playerName = playerData.playerProfile().name().orElse("PLAYER_NOT_FOUND");
            graphics.item(stack, x + 3, y + 3 + i * 20 - (int) this.scrollDistance);
            graphics.text(FONT, playerName, x + 21, y + 5 + i * 20 - (int) this.scrollDistance, 0xFFFFFFFF, false);
        }
        graphics.disableScissor();

    }

    private enum FilterButtonValue {
        WITH_GRAVES, WITH_CLAIMED, WITH_UNCLAIMED, WITH_DESTROYED
    }

    public static void openScreen(PlayerSelectionS2CPacket payload) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.setScreen(new PlayerSelectionScreen(payload.data(), client.screen)));
    }
}
