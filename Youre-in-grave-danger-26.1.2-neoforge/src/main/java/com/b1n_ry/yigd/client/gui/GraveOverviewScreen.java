package com.b1n_ry.yigd.client.gui;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.data.GraveItem;
import com.b1n_ry.yigd.networking.packets.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class GraveOverviewScreen extends Screen {
    private static final Identifier WINDOW_BG = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "window_bg");
    private static final Identifier SLOT = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "slot");
    private static final Identifier EXP_ORB = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "exp_orb");
    private static final Identifier GRAVE = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "unclaimed_grave");
    private static final Identifier GRAVE_CROSSED = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "unclaimed_grave_cross");
    private static final Identifier TRASH_CAN = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "trashcan_icon");
    private static final Identifier TRASH_CAN_CROSSED = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "trashcan_icon_cross");
    private static final Identifier BOOK_CROSSED = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "enchanted_book_cross");
    private static final Identifier DROP_ICON = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "drop_icon");
    private static final Identifier DROP_ICON_CROSSED = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "drop_icon_cross");
    private static final Identifier RESTORE_ICON = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "restore_btn");
    private static final Identifier ROB_ICON = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "rob_btn");
    private static final Identifier LOCKED_ICON = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "locked_btn");
    private static final Identifier UNLOCKED_ICON = Identifier.fromNamespaceAndPath(Yigd.MOD_ID, "unlocked_btn");

    private final GraveComponent graveComponent;
    // These are hard coded since otherwise the layout has to change, and that's kinda annoying
    private static final int MAIN_SIZE = 36;
    private static final int ARMOR_SIZE = 4;

    private static final int SCREEN_WIDTH = 178;
    private static final int SCREEN_HEIGHT = 178;

    private boolean viewGraveItems = true;
    private boolean viewDeletedItems = false;
    private boolean viewSoulboundItems = false;
    private boolean viewDroppedItems = false;
    private final Screen previousScreen;

    private final ItemStack[] mainInv = Stream.generate(() -> ItemStack.EMPTY).limit(MAIN_SIZE).toArray(ItemStack[]::new);
    private final ItemStack[] armor = Stream.generate(() -> ItemStack.EMPTY).limit(ARMOR_SIZE).toArray(ItemStack[]::new);
    private ItemStack offhand = ItemStack.EMPTY;
    private final NonNullList<ItemStack> extraItems = NonNullList.create();

    private final Button toggleGraveItems = Button.builder(Component.empty(), button -> {
        this.viewGraveItems = !this.viewGraveItems;
        button.setTooltip(Tooltip.create(this.viewGraveItems ?
                Component.translatable("button.yigd.gui.view_grave_items") :
                Component.translatable("button.yigd.gui.hide_grave_items")));
        this.reloadFilters();
    }).size(20, 20).tooltip(Tooltip.create(this.viewGraveItems ?
            Component.translatable("button.yigd.gui.view_grave_items") :
            Component.translatable("button.yigd.gui.hide_grave_items"))).build();
    private final Button toggleDeletedItems = Button.builder(Component.empty(), button -> {
        this.viewDeletedItems = !this.viewDeletedItems;
        button.setTooltip(Tooltip.create(this.viewDeletedItems ?
                Component.translatable("button.yigd.gui.view_deleted_items") :
                Component.translatable("button.yigd.gui.hide_deleted_items")));
        this.reloadFilters();
    }).size(20, 20).tooltip(Tooltip.create(this.viewDeletedItems ?
            Component.translatable("button.yigd.gui.view_deleted_items") :
            Component.translatable("button.yigd.gui.hide_deleted_items"))).build();
    private final Button toggleDroppedItems = Button.builder(Component.empty(), button -> {
        this.viewDroppedItems = !this.viewDroppedItems;
        button.setTooltip(Tooltip.create(this.viewDroppedItems ?
                Component.translatable("button.yigd.gui.view_dropped_items") :
                Component.translatable("button.yigd.gui.hide_dropped_items")));
        this.reloadFilters();
    }).size(20, 20).tooltip(Tooltip.create(this.viewDroppedItems ?
            Component.translatable("button.yigd.gui.view_dropped_items") :
            Component.translatable("button.yigd.gui.hide_dropped_items"))).build();
    private final Button toggleKeptItems = Button.builder(Component.empty(), button -> {
        this.viewSoulboundItems = !this.viewSoulboundItems;
        button.setTooltip(Tooltip.create(this.viewSoulboundItems ?
                Component.translatable("button.yigd.gui.view_soulbound_items") :
                Component.translatable("button.yigd.gui.hide_soulbound_items")));
        this.reloadFilters();
    }).size(20, 20).tooltip(Tooltip.create(this.viewSoulboundItems ?
            Component.translatable("button.yigd.gui.view_soulbound_items") :
            Component.translatable("button.yigd.gui.hide_soulbound_items"))).build();

    private final Map<String, Button> permissionLockedButtons = new HashMap<>();
    private final String[] buttonOrder = { "restore", "rob", "toggle_lock", "delete", "get_key", "get_compass" };

    private static final Font FONT = Minecraft.getInstance().font;

    public GraveOverviewScreen(GraveComponent graveComponent, Screen previousScreen, boolean canRestore, boolean canRob,
                               boolean canDelete, boolean canUnlock, boolean obtainableKeys, boolean obtainableCompass) {
        super(graveComponent.getDeathMessage());

        this.graveComponent = graveComponent;
        this.previousScreen = previousScreen;

        Component lockedText = this.graveComponent.isLocked() ? Component.translatable("button.yigd.gui.locked") : Component.translatable("button.yigd.gui.unlocked");

        if (canRestore) this.permissionLockedButtons.put("restore", Button.builder(Component.empty(), button -> ClientPacketDistributor.sendToServer(
                new RestoreGraveC2SPacket(this.graveComponent.getGraveId(), this.viewGraveItems,
                        this.viewDeletedItems, this.viewSoulboundItems, this.viewDroppedItems)
        )).size(20, 20).tooltip(Tooltip.create(Component.translatable("button.yigd.gui.restore"))).build());
        if (canRob) this.permissionLockedButtons.put("rob", Button.builder(Component.empty(), button -> ClientPacketDistributor.sendToServer(
                new RobGraveC2SPacket(this.graveComponent.getGraveId(), this.viewGraveItems,
                        this.viewDeletedItems, this.viewSoulboundItems, this.viewDroppedItems)
        )).size(20, 20).tooltip(Tooltip.create(Component.translatable("button.yigd.gui.rob"))).build());
        if (canUnlock) this.permissionLockedButtons.put("toggle_lock", Button.builder(Component.empty(), button -> {
            this.graveComponent.setLocked(!this.graveComponent.isLocked());
            boolean locked = this.graveComponent.isLocked();
            Component lockedComponent = this.graveComponent.isLocked() ? Component.translatable("button.yigd.gui.locked") : Component.translatable("button.yigd.gui.unlocked");
            button.setTooltip(Tooltip.create(lockedComponent));
            ClientPacketDistributor.sendToServer(new LockGraveC2SPacket(this.graveComponent.getGraveId(), locked));
        }).size(20, 20).tooltip(Tooltip.create(lockedText)).build());
        if (canDelete) this.permissionLockedButtons.put("delete", Button.builder(Component.empty(), button -> ClientPacketDistributor.sendToServer(
                new DeleteGraveC2SPacket(this.graveComponent.getGraveId())
        )).size(20, 20).tooltip(Tooltip.create(Component.translatable("button.yigd.gui.delete"))).build());
        if (obtainableKeys) this.permissionLockedButtons.put("get_key", Button.builder(Component.empty(), button -> ClientPacketDistributor.sendToServer(
                new RequestKeyC2SPacket(this.graveComponent.getGraveId())
        )).size(20, 20).tooltip(Tooltip.create(Component.translatable("button.yigd.gui.obtain_keys"))).build());
        if (obtainableCompass) this.permissionLockedButtons.put("get_compass", Button.builder(Component.empty(), button -> ClientPacketDistributor.sendToServer(
                new RequestCompassC2SPacket(this.graveComponent.getGraveId())
        )).size(20, 20).tooltip(Tooltip.create(Component.translatable("button.yigd.gui.obtain_compass"))).build());
    }

    @Override
    public void init() {
        this.reloadFilters();

        this.clearWidgets();
        this.addWidget(this.toggleGraveItems);
        this.addWidget(this.toggleDeletedItems);
        this.addWidget(this.toggleKeptItems);
        this.addWidget(this.toggleDroppedItems);
        for (Button b : this.permissionLockedButtons.values()) {
            this.addWidget(b);
        }

        super.init();
    }

    private void reloadFilters() {
        InventoryComponent visibleInventoryComponent = this.graveComponent.getInventoryComponent().filteredInv(dropRule -> switch (dropRule) {
            case PUT_IN_GRAVE -> this.viewGraveItems;
            case DESTROY -> this.viewDeletedItems;
            case KEEP -> this.viewSoulboundItems;
            case DROP -> this.viewDroppedItems;
        });

        this.extraItems.clear();
        NonNullList<GraveItem> items = visibleInventoryComponent.getItems();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i).stack;
            if (i < MAIN_SIZE) {  // Main size on screen
                this.mainInv[i] = stack;
            } else if (i < visibleInventoryComponent.mainSize) {  // Main size in inventory but can't fit on screen
                this.extraItems.add(stack);
            } else if (i - visibleInventoryComponent.mainSize < ARMOR_SIZE) {  // First 4 in armor
                this.armor[i - visibleInventoryComponent.mainSize] = stack;
            } else if (i - visibleInventoryComponent.mainSize < visibleInventoryComponent.armorSize) {  // Still armor but can't fit
                this.extraItems.add(stack);
            } else if (i - visibleInventoryComponent.mainSize - visibleInventoryComponent.armorSize == 0) {  // First after armor (offhand)
                this.offhand = stack;
            } else if (!stack.isEmpty()) {  // Everything that is after the offhand (and not empty)
                this.extraItems.add(stack);
            }
        }
        this.extraItems.addAll(visibleInventoryComponent.getAllExtraItems(true));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && this.minecraft != null) {
            this.minecraft.setScreen(this.previousScreen);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WINDOW_BG, leftEdge, topEdge, SCREEN_WIDTH, SCREEN_HEIGHT);

        this.renderHotbar(graphics, mouseX, mouseY);
        this.renderMainInventory(graphics, mouseX, mouseY);
        this.renderArmor(graphics, mouseX, mouseY);
        this.renderOffhand(graphics, mouseX, mouseY);

        this.renderGraveInfo(graphics);
        this.renderExtraItems(graphics, mouseX, mouseY);
        this.renderButtons(graphics, mouseX, mouseY, partialTick);
    }

    private void renderItemSlot(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, ItemStack stack) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, y, 18, 18);
        graphics.item(stack, x + 1, y + 1);
        graphics.itemDecorations(FONT, stack, x + 1, y + 1);
        if (x <= mouseX && mouseX < x + 18 && y <= mouseY && mouseY < y + 18) {
            if (!stack.isEmpty()) graphics.setTooltipForNextFrame(FONT, stack, mouseX, mouseY);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x55FFFFFF);
        }
    }

    private void renderHotbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;

        for (int i = 0; i < 9; i++) {
            this.renderItemSlot(graphics, mouseX, mouseY, leftEdge + 8 + i * 18, topEdge + 152, this.mainInv[i]);
        }
    }
    private void renderMainInventory(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.renderItemSlot(graphics, mouseX, mouseY, leftEdge + 8 + j * 18, topEdge + 89 + i * 18, this.mainInv[9 + i * 9 + j]);
            }
        }
    }
    private void renderArmor(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;

        for (int i = 0; i < 4; i++) {
            this.renderItemSlot(graphics, mouseX, mouseY, leftEdge + 8, topEdge + 62 - i * 18, this.armor[i]);
        }
    }
    private void renderOffhand(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;

        this.renderItemSlot(graphics, mouseX, mouseY, leftEdge + 152, topEdge + 62, this.offhand);
    }

    private void renderGraveInfo(GuiGraphicsExtractor graphics) {
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;

        BlockPos pos = this.graveComponent.getPos();
        String dimId = this.graveComponent.getWorldRegistryKey().identifier().toString();
        Component number = Component.nullToEmpty(String.valueOf(this.graveComponent.getExpComponent().getXpLevel()));

        graphics.text(FONT, this.title, leftEdge + 28, topEdge + 8, 0xFF404040, false);
        graphics.text(FONT, Component.nullToEmpty("X: %d / Y: %d / Z: %d".formatted(pos.getX(), pos.getY(), pos.getZ())), leftEdge + 28, topEdge + 26, 0xFF404040, false);
        graphics.text(FONT, Component.translatableWithFallback("text.yigd.dimension.name." + dimId, dimId), leftEdge + 28, topEdge + 44, 0xFF404040, false);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXP_ORB, leftEdge + 28, topEdge + 62, 12, 12);
        graphics.text(FONT, number, leftEdge + 39, topEdge + 68, 0xFF000000, false);
        graphics.text(FONT, number, leftEdge + 40, topEdge + 67, 0xFF000000, false);
        graphics.text(FONT, number, leftEdge + 41, topEdge + 68, 0xFF000000, false);
        graphics.text(FONT, number, leftEdge + 40, topEdge + 69, 0xFF000000, false);
        graphics.text(FONT, number, leftEdge + 40, topEdge + 68, 0xFF80FF20, false);
    }

    public void renderExtraItems(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.extraItems.isEmpty()) return;
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;

        int itemCount = this.extraItems.size();

        int slotsWide = ((itemCount - 1) / 9) + 1;
        int slotsTall = Math.min(itemCount, 9);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WINDOW_BG, leftEdge - slotsWide * 18 - 14, topEdge + 1, 14 + slotsWide * 18, slotsTall * 18 + 14);

        for (int i = 0; i < itemCount; i++) {
            this.renderItemSlot(graphics, mouseX, mouseY, leftEdge - 7 - (slotsWide - i / 9) * 18, topEdge + 8 + (i % 9) * 18, this.extraItems.get(i));
        }
    }

    public void renderButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int rightEdge = this.width / 2 + SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;

        int width = this.permissionLockedButtons.isEmpty() ? 34 : 58;
        int height = Math.max(106, this.permissionLockedButtons.size() * 24 + 10);

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WINDOW_BG, rightEdge, topEdge + 1, width, height);
        this.toggleGraveItems.setPosition(rightEdge + 7, topEdge + 8);
        this.toggleGraveItems.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.viewGraveItems ? GRAVE : GRAVE_CROSSED, rightEdge + 9, topEdge + 10, 16, 16);
        this.toggleDeletedItems.setPosition(rightEdge + 7, topEdge + 32);
        this.toggleDeletedItems.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.viewDeletedItems ? TRASH_CAN : TRASH_CAN_CROSSED, rightEdge + 9, topEdge + 34, 16, 16);
        this.toggleKeptItems.setPosition(rightEdge + 7, topEdge + 56);
        this.toggleKeptItems.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (this.viewSoulboundItems)
            graphics.item(Items.ENCHANTED_BOOK.getDefaultInstance(), rightEdge + 9, topEdge + 58);
        else
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BOOK_CROSSED, rightEdge + 9, topEdge + 58, 16, 16);
        this.toggleDroppedItems.setPosition(rightEdge + 7, topEdge + 80);
        this.toggleDroppedItems.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.viewDroppedItems ? DROP_ICON : DROP_ICON_CROSSED, rightEdge + 9, topEdge + 82, 16, 16);

        for (int i = 0; i < this.buttonOrder.length; i++) {
            String buttonName = this.buttonOrder[i];
            if (!this.permissionLockedButtons.containsKey(buttonName))
                continue;

            Button button = this.permissionLockedButtons.get(buttonName);

            button.setPosition(rightEdge + 31, topEdge + 8 + 24 * i);
            button.extractRenderState(graphics, mouseX, mouseY, partialTick);
            Identifier sprite = switch (buttonName) {
                case "restore" -> RESTORE_ICON;
                case "rob" -> ROB_ICON;
                case "toggle_lock" -> this.graveComponent.isLocked() ? LOCKED_ICON : UNLOCKED_ICON;
                case "delete" -> TRASH_CAN;
                default -> null;
            };
            if (sprite == null) {
                if (buttonName.equals("get_key")) {
                    graphics.item(Yigd.GRAVE_KEY_ITEM.toStack(), rightEdge + 33, topEdge + 10 + 24 * i);
                } else if (buttonName.equals("get_compass")) {
                    graphics.item(Items.RECOVERY_COMPASS.getDefaultInstance(), rightEdge + 33, topEdge + 10 + 24 * i);
                }
            } else {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, rightEdge + 33, topEdge + 10 + 24 * i, 16, 16);
            }
        }
    }

    public static void openScreen(GraveOverviewS2CPacket payload) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.setScreen(new GraveOverviewScreen(payload.component(), client.screen,
                payload.canRestore(), payload.canRob(), payload.canDelete(), payload.canUnlock(),
                payload.obtainableKeys(), payload.obtainableCompass())));
    }
}
