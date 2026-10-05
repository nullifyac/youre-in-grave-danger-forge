package com.b1n_ry.yigd.client.gui;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.data.GraveItem;
import com.b1n_ry.yigd.packets.ClientPacketHandler;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import javax.annotation.Nonnull;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class GraveOverviewScreen extends Screen {
    private static final ResourceLocation WINDOW_BG = new ResourceLocation(Yigd.MOD_ID, "window_bg");
    private static final ResourceLocation SLOT = new ResourceLocation(Yigd.MOD_ID, "slot");
    private static final ResourceLocation EXP_ORB = new ResourceLocation(Yigd.MOD_ID, "exp_orb");
    private static final ResourceLocation GRAVE = new ResourceLocation(Yigd.MOD_ID, "unclaimed_grave");
    private static final ResourceLocation GRAVE_CROSSED = new ResourceLocation(Yigd.MOD_ID, "unclaimed_grave_cross");
    private static final ResourceLocation TRASH_CAN = new ResourceLocation(Yigd.MOD_ID, "trashcan_icon");
    private static final ResourceLocation TRASH_CAN_CROSSED = new ResourceLocation(Yigd.MOD_ID, "trashcan_icon_cross");
    private static final ResourceLocation BOOK_CROSSED = new ResourceLocation(Yigd.MOD_ID, "enchanted_book_cross");
    private static final ResourceLocation DROP_ICON = new ResourceLocation(Yigd.MOD_ID, "drop_icon");
    private static final ResourceLocation DROP_ICON_CROSSED = new ResourceLocation(Yigd.MOD_ID, "drop_icon_cross");
    private static final ResourceLocation RESTORE_ICON = new ResourceLocation(Yigd.MOD_ID, "restore_btn");
    private static final ResourceLocation ROB_ICON = new ResourceLocation(Yigd.MOD_ID, "rob_btn");
    private static final ResourceLocation LOCKED_ICON = new ResourceLocation(Yigd.MOD_ID, "locked_btn");
    private static final ResourceLocation UNLOCKED_ICON = new ResourceLocation(Yigd.MOD_ID, "unlocked_btn");

    private static final int MAIN_SIZE = 36;
    private static final int ARMOR_SIZE = 4;
    private static final int SCREEN_WIDTH = 178;
    private static final int SCREEN_HEIGHT = 178;

    private static final FontRenderer FONT = Minecraft.getInstance().font;

    private final GraveComponent graveComponent;
    private final Screen previousScreen;

    private boolean viewGraveItems = true;
    private boolean viewDeletedItems = false;
    private boolean viewSoulboundItems = false;
    private boolean viewDroppedItems = false;

    private ITextComponent graveItemsTooltip = new TranslationTextComponent("button.yigd.gui.view_grave_items");
    private ITextComponent deletedItemsTooltip = new TranslationTextComponent("button.yigd.gui.hide_deleted_items");
    private ITextComponent droppedItemsTooltip = new TranslationTextComponent("button.yigd.gui.hide_dropped_items");
    private ITextComponent soulboundItemsTooltip = new TranslationTextComponent("button.yigd.gui.hide_soulbound_items");
    private ITextComponent lockTooltip = StringTextComponent.EMPTY;

    private final ItemStack[] mainInv = Stream.generate(() -> ItemStack.EMPTY).limit(MAIN_SIZE).toArray(ItemStack[]::new);
    private final ItemStack[] armor = Stream.generate(() -> ItemStack.EMPTY).limit(ARMOR_SIZE).toArray(ItemStack[]::new);
    private ItemStack offhand = ItemStack.EMPTY;
    private final NonNullList<ItemStack> extraItems = NonNullList.create();

    private final Button toggleGraveItems = new Button(0, 0, 20, 20, StringTextComponent.EMPTY, button -> {
        this.viewGraveItems = !this.viewGraveItems;
        this.graveItemsTooltip = new TranslationTextComponent(this.viewGraveItems ?
                "button.yigd.gui.view_grave_items" : "button.yigd.gui.hide_grave_items");
        this.reloadFilters();
    }, (button, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, this.graveItemsTooltip, mouseX, mouseY));
    private final Button toggleDeletedItems = new Button(0, 0, 20, 20, StringTextComponent.EMPTY, button -> {
        this.viewDeletedItems = !this.viewDeletedItems;
        this.deletedItemsTooltip = new TranslationTextComponent(this.viewDeletedItems ?
                "button.yigd.gui.view_deleted_items" : "button.yigd.gui.hide_deleted_items");
        this.reloadFilters();
    }, (button, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, this.deletedItemsTooltip, mouseX, mouseY));
    private final Button toggleDroppedItems = new Button(0, 0, 20, 20, StringTextComponent.EMPTY, button -> {
        this.viewDroppedItems = !this.viewDroppedItems;
        this.droppedItemsTooltip = new TranslationTextComponent(this.viewDroppedItems ?
                "button.yigd.gui.view_dropped_items" : "button.yigd.gui.hide_dropped_items");
        this.reloadFilters();
    }, (button, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, this.droppedItemsTooltip, mouseX, mouseY));
    private final Button toggleKeptItems = new Button(0, 0, 20, 20, StringTextComponent.EMPTY, button -> {
        this.viewSoulboundItems = !this.viewSoulboundItems;
        this.soulboundItemsTooltip = new TranslationTextComponent(this.viewSoulboundItems ?
                "button.yigd.gui.view_soulbound_items" : "button.yigd.gui.hide_soulbound_items");
        this.reloadFilters();
    }, (button, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, this.soulboundItemsTooltip, mouseX, mouseY));

    private final Map<String, Button> permissionLockedButtons = new HashMap<>();
    private final String[] buttonOrder = {"restore", "rob", "toggle_lock", "delete", "get_key", "get_compass"};

    public GraveOverviewScreen(GraveComponent graveComponent, Screen previousScreen, boolean canRestore, boolean canRob,
                               boolean canDelete, boolean canUnlock, boolean obtainableKeys, boolean obtainableCompass) {
        super(graveComponent.getDeathMessage().getDeathMessage());
        this.graveComponent = graveComponent;
        this.previousScreen = previousScreen;

        ITextComponent lockedText = this.graveComponent.isLocked()
                ? new TranslationTextComponent("button.yigd.gui.locked")
                : new TranslationTextComponent("button.yigd.gui.unlocked");

        if (canRestore) {
            ITextComponent restoreTooltip = new TranslationTextComponent("button.yigd.gui.restore");
            this.permissionLockedButtons.put("restore", new Button(0, 0, 20, 20, StringTextComponent.EMPTY, button -> {
                ClientPacketHandler.sendRestoreGraveRequestPacket(this.graveComponent.getGraveId(), this.viewGraveItems,
                        this.viewDeletedItems, this.viewSoulboundItems, this.viewDroppedItems);
            }, (button, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, restoreTooltip, mouseX, mouseY)));
        }
        if (canRob) {
            ITextComponent robTooltip = new TranslationTextComponent("button.yigd.gui.rob");
            this.permissionLockedButtons.put("rob", new Button(0, 0, 20, 20, StringTextComponent.EMPTY, button -> {
                ClientPacketHandler.sendRobGraveRequestPacket(this.graveComponent.getGraveId(), this.viewGraveItems,
                        this.viewDeletedItems, this.viewSoulboundItems, this.viewDroppedItems);
            }, (button, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, robTooltip, mouseX, mouseY)));
        }
        if (canUnlock) {
            this.lockTooltip = lockedText;
            this.permissionLockedButtons.put("toggle_lock", new Button(0, 0, 20, 20, StringTextComponent.EMPTY, button -> {
                this.graveComponent.setLocked(!this.graveComponent.isLocked());
                boolean locked = this.graveComponent.isLocked();
                this.lockTooltip = locked
                        ? new TranslationTextComponent("button.yigd.gui.locked")
                        : new TranslationTextComponent("button.yigd.gui.unlocked");
                ClientPacketHandler.sendGraveLockRequestPacket(this.graveComponent.getGraveId(), locked);
            }, (button, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, this.lockTooltip, mouseX, mouseY)));
        }
        if (canDelete) {
            ITextComponent deleteTooltip = new TranslationTextComponent("button.yigd.gui.delete");
            this.permissionLockedButtons.put("delete", new Button(0, 0, 20, 20, StringTextComponent.EMPTY, button -> {
                ClientPacketHandler.sendDeleteGraveRequestPacket(this.graveComponent.getGraveId());
            }, (button, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, deleteTooltip, mouseX, mouseY)));
        }
        if (obtainableKeys) {
            ITextComponent keyTooltip = new TranslationTextComponent("button.yigd.gui.obtain_keys");
            this.permissionLockedButtons.put("get_key", new Button(0, 0, 20, 20, StringTextComponent.EMPTY, button -> {
                ClientPacketHandler.sendObtainKeysRequestPacket(this.graveComponent.getGraveId());
            }, (button, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, keyTooltip, mouseX, mouseY)));
        }
        if (obtainableCompass) {
            ITextComponent compassTooltip = new TranslationTextComponent("button.yigd.gui.obtain_compass");
            this.permissionLockedButtons.put("get_compass", new Button(0, 0, 20, 20, StringTextComponent.EMPTY, button -> {
                ClientPacketHandler.sendObtainCompassRequestPacket(this.graveComponent.getGraveId());
            }, (button, poseStack, mouseX, mouseY) -> this.renderTooltip(poseStack, compassTooltip, mouseX, mouseY)));
        }
    }

    @Override
    protected void init() {
        this.reloadFilters();

        this.buttons.clear();
        this.children.clear();
        this.addButton(this.toggleGraveItems);
        this.addButton(this.toggleDeletedItems);
        this.addButton(this.toggleDroppedItems);
        this.addButton(this.toggleKeptItems);
        for (Button button : this.permissionLockedButtons.values()) {
            this.addButton(button);
        }

        super.init();
    }

    private void reloadFilters() {
        Arrays.fill(this.mainInv, ItemStack.EMPTY);
        Arrays.fill(this.armor, ItemStack.EMPTY);
        this.offhand = ItemStack.EMPTY;
        this.extraItems.clear();

        InventoryComponent visibleInventoryComponent = this.graveComponent.getInventoryComponent().filteredInv(dropRule -> {
            switch (dropRule) {
                case PUT_IN_GRAVE:
                    return this.viewGraveItems;
                case DESTROY:
                    return this.viewDeletedItems;
                case KEEP:
                    return this.viewSoulboundItems;
                case DROP:
                    return this.viewDroppedItems;
                default:
                    return false;
            }
        });

        NonNullList<GraveItem> items = visibleInventoryComponent.getItems();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i).stack;
            if (i < MAIN_SIZE) {
                this.mainInv[i] = stack;
            } else if (i < visibleInventoryComponent.mainSize) {
                this.extraItems.add(stack);
            } else if (i - visibleInventoryComponent.mainSize < ARMOR_SIZE) {
                this.armor[i - visibleInventoryComponent.mainSize] = stack;
            } else if (i - visibleInventoryComponent.mainSize < visibleInventoryComponent.armorSize) {
                this.extraItems.add(stack);
            } else if (i - visibleInventoryComponent.mainSize - visibleInventoryComponent.armorSize == 0) {
                this.offhand = stack;
            } else if (!stack.isEmpty()) {
                this.extraItems.add(stack);
            }
        }
        this.extraItems.addAll(visibleInventoryComponent.getAllExtraItems(true));
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(@Nonnull MatrixStack poseStack, int mouseX, int mouseY, float partialTick) {
        super.render(poseStack, mouseX, mouseY, partialTick);

        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;
        blitSprite(poseStack, WINDOW_BG, leftEdge, topEdge, SCREEN_WIDTH, SCREEN_HEIGHT);

        this.renderHotbar(poseStack, mouseX, mouseY);
        this.renderMainInventory(poseStack, mouseX, mouseY);
        this.renderArmor(poseStack, mouseX, mouseY);
        this.renderOffhand(poseStack, mouseX, mouseY);
        this.renderGraveInfo(poseStack);
        this.renderExtraItems(poseStack, mouseX, mouseY);
        this.renderButtons(poseStack, mouseX, mouseY, partialTick);
    }

    private void renderItemSlot(MatrixStack poseStack, int mouseX, int mouseY, int x, int y, ItemStack stack) {
        blitSprite(poseStack, SLOT, x, y, 18, 18);
        this.itemRenderer.renderAndDecorateItem(stack, x + 1, y + 1);
        if (stack.getCount() > 1) {
            String itemCount = String.valueOf(stack.getCount());
            poseStack.pushPose();
            poseStack.translate(0, 0, 400.0F);
            FONT.draw(poseStack, itemCount, x + 18 - FONT.width(itemCount), y + 19 - FONT.lineHeight, 0xFFFFFF);
            poseStack.popPose();
        }
        if (x <= mouseX && mouseX < x + 18 && y <= mouseY && mouseY < y + 18) {
            if (!stack.isEmpty()) {
                this.renderTooltip(poseStack, stack, mouseX, mouseY);
            }
            fill(poseStack, x + 1, y + 1, x + 17, y + 17, 0x55FFFFFF);
        }
    }

    private void renderHotbar(MatrixStack poseStack, int mouseX, int mouseY) {
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;
        for (int i = 0; i < 9; i++) {
            this.renderItemSlot(poseStack, mouseX, mouseY, leftEdge + 8 + i * 18, topEdge + 152, this.mainInv[i]);
        }
    }

    private void renderMainInventory(MatrixStack poseStack, int mouseX, int mouseY) {
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.renderItemSlot(poseStack, mouseX, mouseY, leftEdge + 8 + column * 18,
                        topEdge + 89 + row * 18, this.mainInv[9 + row * 9 + column]);
            }
        }
    }

    private void renderArmor(MatrixStack poseStack, int mouseX, int mouseY) {
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;
        for (int i = 0; i < 4; i++) {
            this.renderItemSlot(poseStack, mouseX, mouseY, leftEdge + 8, topEdge + 62 - i * 18, this.armor[i]);
        }
    }

    private void renderOffhand(MatrixStack poseStack, int mouseX, int mouseY) {
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;
        this.renderItemSlot(poseStack, mouseX, mouseY, leftEdge + 152, topEdge + 62, this.offhand);
    }

    private void renderGraveInfo(MatrixStack poseStack) {
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;

        BlockPos pos = this.graveComponent.getPos();
        String dimId = this.graveComponent.getWorldResourceKey().location().toString();
        ITextComponent number = ITextComponent.nullToEmpty(String.valueOf(this.graveComponent.getExpComponent().getXpLevel()));

        FONT.draw(poseStack, this.title, leftEdge + 28, topEdge + 8, 0x404040);
        FONT.draw(poseStack, ITextComponent.nullToEmpty(String.format("X: %d / Y: %d / Z: %d", pos.getX(), pos.getY(), pos.getZ())),
                leftEdge + 28, topEdge + 26, 0x404040);
        FONT.draw(poseStack, new TranslationTextComponent("text.yigd.dimension.name." + dimId),
                leftEdge + 28, topEdge + 44, 0x404040);
        blitSprite(poseStack, EXP_ORB, leftEdge + 28, topEdge + 62, 12, 12);
        FONT.draw(poseStack, number, leftEdge + 39, topEdge + 68, 0x000000);
        FONT.draw(poseStack, number, leftEdge + 40, topEdge + 67, 0x000000);
        FONT.draw(poseStack, number, leftEdge + 41, topEdge + 68, 0x000000);
        FONT.draw(poseStack, number, leftEdge + 40, topEdge + 69, 0x000000);
        FONT.draw(poseStack, number, leftEdge + 40, topEdge + 68, 0x80FF20);
    }

    private void renderExtraItems(MatrixStack poseStack, int mouseX, int mouseY) {
        if (this.extraItems.isEmpty()) return;
        int leftEdge = this.width / 2 - SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;

        int itemCount = this.extraItems.size();
        int slotsWide = ((itemCount - 1) / 9) + 1;
        int slotsTall = Math.min(itemCount, 9);
        blitSprite(poseStack, WINDOW_BG, leftEdge - slotsWide * 18 - 14, topEdge + 1, 14 + slotsWide * 18, slotsTall * 18 + 14);
        for (int i = 0; i < itemCount; i++) {
            this.renderItemSlot(poseStack, mouseX, mouseY, leftEdge - 7 - (slotsWide - i / 9) * 18,
                    topEdge + 8 + (i % 9) * 18, this.extraItems.get(i));
        }
    }

    private void renderButtons(MatrixStack poseStack, int mouseX, int mouseY, float partialTick) {
        int rightEdge = this.width / 2 + SCREEN_WIDTH / 2;
        int topEdge = this.height / 2 - SCREEN_HEIGHT / 2;

        int width = this.permissionLockedButtons.isEmpty() ? 34 : 58;
        int height = Math.max(106, this.permissionLockedButtons.size() * 24 + 10);

        blitSprite(poseStack, WINDOW_BG, rightEdge, topEdge + 1, width, height);

        this.toggleGraveItems.x = rightEdge + 7;
        this.toggleGraveItems.y = topEdge + 8;
        this.toggleGraveItems.render(poseStack, mouseX, mouseY, partialTick);
        blitSprite(poseStack, this.viewGraveItems ? GRAVE : GRAVE_CROSSED, rightEdge + 9, topEdge + 10, 16, 16);

        this.toggleDeletedItems.x = rightEdge + 7;
        this.toggleDeletedItems.y = topEdge + 32;
        this.toggleDeletedItems.render(poseStack, mouseX, mouseY, partialTick);
        blitSprite(poseStack, this.viewDeletedItems ? TRASH_CAN : TRASH_CAN_CROSSED, rightEdge + 9, topEdge + 34, 16, 16);

        this.toggleKeptItems.x = rightEdge + 7;
        this.toggleKeptItems.y = topEdge + 56;
        this.toggleKeptItems.render(poseStack, mouseX, mouseY, partialTick);
        if (this.viewSoulboundItems) {
            this.itemRenderer.renderAndDecorateItem(Items.ENCHANTED_BOOK.getDefaultInstance(), rightEdge + 9, topEdge + 58);
        } else {
            blitSprite(poseStack, BOOK_CROSSED, rightEdge + 9, topEdge + 58, 16, 16);
        }

        this.toggleDroppedItems.x = rightEdge + 7;
        this.toggleDroppedItems.y = topEdge + 80;
        this.toggleDroppedItems.render(poseStack, mouseX, mouseY, partialTick);
        blitSprite(poseStack, this.viewDroppedItems ? DROP_ICON : DROP_ICON_CROSSED, rightEdge + 9, topEdge + 82, 16, 16);

        for (int i = 0; i < this.buttonOrder.length; i++) {
            String buttonName = this.buttonOrder[i];
            if (!this.permissionLockedButtons.containsKey(buttonName)) continue;

            Button button = this.permissionLockedButtons.get(buttonName);
            button.x = rightEdge + 31;
            button.y = topEdge + 8 + 24 * i;
            button.render(poseStack, mouseX, mouseY, partialTick);

            ResourceLocation sprite;
            switch (buttonName) {
                case "restore":
                    sprite = RESTORE_ICON;
                    break;
                case "rob":
                    sprite = ROB_ICON;
                    break;
                case "toggle_lock":
                    sprite = this.graveComponent.isLocked() ? LOCKED_ICON : UNLOCKED_ICON;
                    break;
                case "delete":
                    sprite = TRASH_CAN;
                    break;
                default:
                    sprite = null;
                    break;
            }

            if (sprite == null) {
                if (buttonName.equals("get_key")) {
                    this.itemRenderer.renderAndDecorateItem(new ItemStack(Yigd.GRAVE_KEY_ITEM), rightEdge + 33, topEdge + 10 + 24 * i);
                } else if (buttonName.equals("get_compass")) {
                    this.itemRenderer.renderAndDecorateItem(Items.COMPASS.getDefaultInstance(), rightEdge + 33, topEdge + 10 + 24 * i);
                }
            } else {
                blitSprite(poseStack, sprite, rightEdge + 33, topEdge + 10 + 24 * i, 16, 16);
            }
        }
    }

    private static ResourceLocation spriteTexture(ResourceLocation sprite) {
        return new ResourceLocation(sprite.getNamespace(), "textures/gui/sprites/" + sprite.getPath() + ".png");
    }

    private static void blitSprite(MatrixStack poseStack, ResourceLocation sprite, int x, int y, int width, int height) {
        ResourceLocation texture = spriteTexture(sprite);
        Minecraft.getInstance().getTextureManager().bind(texture);
        blit(poseStack, x, y, 0, 0.0F, 0.0F, width, height, width, height);
    }
}
