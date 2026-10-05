package com.b1n_ry.yigd.client.render;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.config.GraveRenderingConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.events.YigdClientEventHandler;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class GraveBlockEntityRenderer implements BlockEntityRenderer<GraveBlockEntity> {
    private static final Gson GSON = new Gson();

    private final Map<SkullBlock.Type, SkullModelBase> skullModels;
    private final Font textRenderer;
    private final Minecraft client;
    private final boolean adaptRenderer;

    private static ModelPart graveModel;
    @Nullable
    private static TextRenderInfo textRenderInfo = null;
    @Nullable
    private static SkullRenderInfo skullRenderInfo = null;
    private static final Map<String, Material> CUBOID_SPRITES = new HashMap<>();
    private static final int GLOW_COLOR = FastColor.ARGB32.color(96, 255, 255, 255);
    private static final int GLOW_LIGHT = LightTexture.FULL_BRIGHT;

    public static boolean syncedGlowing = true;
    public static int syncedGlowingMaxDistance = Integer.MAX_VALUE;
    public static double syncedDeathSightDistance = Integer.MAX_VALUE;

    public GraveBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.skullModels = SkullBlockRenderer.createSkullRenderers(context.getModelSet());
        this.textRenderer = context.getFont();
        this.client = Minecraft.getInstance();

        this.adaptRenderer = YigdConfig.getConfig().graveRendering.adaptRenderer;
    }

    @Override
    public void render(GraveBlockEntity entity, float tickDelta, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        GraveRenderingConfig config = YigdConfig.getConfig().graveRendering;
        if (!config.useCustomFeatureRenderer) return;

        BlockState state = entity.getBlockState();
        Direction direction = state.getValue(BlockStateProperties.HORIZONTAL_FACING);

        float rotation = (float) switch (direction) {
            case SOUTH -> Math.PI;
            case WEST -> Math.PI * 0.5D;
            case EAST -> Math.PI * 1.5D;
            default -> 0;  // North (can't be up/down)
        };

        poseStack.pushPose();

        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(Vector3f.YP.rotation(rotation));
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        if (config.useGlowingEffect && entity.isUnclaimed()) {
            this.renderGlowingOverlay(entity, poseStack, buffers, overlay);
        }

        if (config.useSkullRenderer) {
            this.renderOwnerSkull(entity, tickDelta, poseStack, buffers, light, overlay);
        }
        if (config.useTextRenderer) {
            this.renderGraveText(entity, poseStack, buffers, light);
        }
        this.renderGraveModel(entity, poseStack, buffers, light, overlay);

        poseStack.popPose();
    }

    private void renderOwnerSkull(GraveBlockEntity entity, float tickDelta, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        GameProfile skullOwner = entity.getGraveSkull();
        if (skullOwner == null) return;

        SkullBlock.Type type = SkullBlock.Types.PLAYER;
        RenderType renderLayer = SkullBlockRenderer.getRenderType(type, skullOwner);

        this.renderSkull(poseStack, buffers, light, renderLayer, tickDelta);
    }

    /**
     * Render the model with given RenderType. This lets us draw the skull both with its skin texture and with the outline buffer.
     */
    private void renderSkull(PoseStack poseStack, MultiBufferSource buffers, int light, RenderType renderLayer, float tickDelta) {
        if (skullRenderInfo == null) return;
        SkullBlock.Type type = SkullBlock.Types.PLAYER;

        SkullModelBase model = this.skullModels.get(type);
        if (model == null) return;

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.25f, 0.5f);

        int[] rotation = skullRenderInfo.rotation;

        poseStack.translate(0D, -(4 - skullRenderInfo.height) / 16D, -(8 - skullRenderInfo.depth) / 16D);

        Quaternion angle = Quaternion.fromXYZDegrees(new Vector3f(rotation[0], rotation[1], rotation[2]));
        poseStack.mulPose(angle);
        poseStack.scale(skullRenderInfo.scaleFace, skullRenderInfo.scaleFace, skullRenderInfo.scaleDepth);

        poseStack.translate(-0.5f, -0.25f, -0.5f);

        SkullBlockRenderer.renderSkull(null, 0, tickDelta, poseStack, buffers, light, model, renderLayer);
        poseStack.popPose();
    }

    private void renderGraveText(GraveBlockEntity entity, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Component graveText = entity.getGraveText();
        if (graveText == null || textRenderInfo == null) return;

        poseStack.pushPose();

        poseStack.translate(.5, textRenderInfo.height / 16f, textRenderInfo.depth / 16f - 0.0001f);
        poseStack.scale(-1, -1, 0);

        int textWidth = this.textRenderer.width(graveText);  // width accepts Component directly
        float scale = textRenderInfo.width / (textWidth * 16f);
        poseStack.scale(scale, scale, scale);

        poseStack.translate(-textWidth / 2.0, -4.5, 0);

        this.textRenderer.drawInBatch(graveText, 0f, 0f, 0xFFFFFF, false, poseStack.last().pose(), buffers, false, 0x0, light);

        poseStack.popPose();
    }

    private void renderGraveModel(GraveBlockEntity entity, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (CUBOID_SPRITES.isEmpty()) {
            // Fallback to a single texture if the custom model reload didn't populate sprites.
            Material fallback = new Material(InventoryMenu.BLOCK_ATLAS, new ResourceLocation(Yigd.MOD_ID, "block/grave"));
            VertexConsumer consumer = fallback.buffer(buffers, RenderType::entityCutout);
            graveModel.render(poseStack, consumer, light, overlay);
            return;
        }
        for (Map.Entry<String, Material> cuboid : CUBOID_SPRITES.entrySet()) {
            String key = cuboid.getKey();
            ModelPart part = graveModel.getChild(key);
            if (this.adaptRenderer && key.equals("ground")) {
                Level world = entity.getLevel();
                if (world != null) {
                    BlockPos underPos = entity.getBlockPos().below();
                    BlockState blockUnder = world.getBlockState(underPos);

                    if (blockUnder.isSolidRender(world, underPos)) {
                        ModelPart.Cube cuboidPart = part.getRandomCube(world.random);
                        float scaleX = cuboidPart.maxX - cuboidPart.minX;
                        float scaleZ = cuboidPart.maxZ - cuboidPart.minZ;

                        poseStack.pushPose();

                        poseStack.translate(cuboidPart.minX / 16f + .0005f, cuboidPart.maxY / 16f - 1f, cuboidPart.minZ / 16f + .0005f);
                        poseStack.scale(.999f * (scaleX / 16f), 1f, .999f * (scaleZ / 16f));

                        this.client.getBlockRenderer()
                                .renderBatched(blockUnder, underPos, world, poseStack, buffers.getBuffer(RenderType.cutout()), false, world.random);
                        poseStack.popPose();

                        continue;
                    }
                }
            }
            VertexConsumer consumer = cuboid.getValue().buffer(buffers, RenderType::entityCutout);

            part.render(poseStack, consumer, light, overlay);
        }
    }

    private void renderGlowingOverlay(GraveBlockEntity entity, PoseStack poseStack, MultiBufferSource buffers, int overlay) {
        LocalPlayer player = this.client.player;
        if (player == null || !YigdClientEventHandler.shouldRenderGlowing(entity, player)) return;

        float alpha = FastColor.ARGB32.alpha(GLOW_COLOR) / 255.0f;
        float red = FastColor.ARGB32.red(GLOW_COLOR) / 255.0f;
        float green = FastColor.ARGB32.green(GLOW_COLOR) / 255.0f;
        float blue = FastColor.ARGB32.blue(GLOW_COLOR) / 255.0f;

        if (CUBOID_SPRITES.isEmpty()) {
            Material fallback = new Material(InventoryMenu.BLOCK_ATLAS, new ResourceLocation(Yigd.MOD_ID, "block/grave"));
            VertexConsumer consumer = fallback.buffer(buffers, RenderType::entityTranslucent);
            graveModel.render(poseStack, consumer, GLOW_LIGHT, overlay, red, green, blue, alpha);
            return;
        }
        for (Map.Entry<String, Material> cuboid : CUBOID_SPRITES.entrySet()) {
            ModelPart part = graveModel.getChild(cuboid.getKey());
            VertexConsumer consumer = cuboid.getValue().buffer(buffers, RenderType::entityTranslucent);
            part.render(poseStack, consumer, GLOW_LIGHT, overlay, red, green, blue, alpha);
        }
    }

    /**
     * Takes JSON and reloads current grave model to what the JSON describes.
     * @param json Model json (same format that block models use).
     * @throws IllegalStateException if the model json is incomplete or wrong
     */
    public static void reloadModelFromJson(JsonObject json) throws IllegalStateException {
        CUBOID_SPRITES.clear();
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();

        JsonArray textureSize = json.getAsJsonArray("texture_size");
        JsonObject textures = json.getAsJsonObject("textures");
        JsonArray elements = json.getAsJsonArray("elements");
        JsonObject features = json.has("features") ? json.getAsJsonObject("features") : null;

        int uvX = textureSize.get(0).getAsInt();
        int uvY = textureSize.get(1).getAsInt();
        Map<String, String> nameIds = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
            nameIds.put(e.getKey(), e.getValue().getAsString());
        }
        int i = 0;
        for (JsonElement e : elements) {
            JsonObject o = e.getAsJsonObject();
            String name = o.has("name") ? o.get("name").getAsString() : String.valueOf(i++);
            JsonArray from = o.getAsJsonArray("from");
            JsonArray to = o.getAsJsonArray("to");
            JsonObject faces = o.getAsJsonObject("faces");

            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            String textureName = "";
            for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                JsonObject value = face.getValue().getAsJsonObject();
                JsonArray uv = value.getAsJsonArray("uv");
                textureName = value.get("texture").getAsString();

                minX = Math.min(minX, uv.get(0).getAsFloat());
                minY = Math.min(minY, uv.get(1).getAsFloat());
            }
            minX *= uvX / 16f;
            minY *= uvY / 16f;

            textureName = textureName.replaceFirst("#", "");
            if (nameIds.containsKey(textureName)) {
                textureName = nameIds.get(textureName);
            }
            ResourceLocation texture = new ResourceLocation(textureName);
            Material sprite = new Material(InventoryMenu.BLOCK_ATLAS, texture);

            CUBOID_SPRITES.put(name, sprite);

            float fromX = from.get(0).getAsFloat();
            float fromY = from.get(1).getAsFloat();
            float fromZ = from.get(2).getAsFloat();
            float toX = to.get(0).getAsFloat();
            float toY = to.get(1).getAsFloat();
            float toZ = to.get(2).getAsFloat();

            float lowerX = Math.min(fromX, toX);
            float lowerY = Math.min(fromY, toY);
            float lowerZ = Math.min(fromZ, toZ);
            float higherX = Math.max(fromX, toX);
            float higherY = Math.max(fromY, toY);
            float higherZ = Math.max(fromZ, toZ);
            addChildPart(root, name, (int) minX, (int) minY, lowerX, lowerY, lowerZ, higherX - lowerX, higherY - lowerY, higherZ - lowerZ);
        }
        if (features != null) {
            if (features.has("text")) {
                textRenderInfo = GSON.fromJson(features.get("text"), TextRenderInfo.class);
            }
            if (features.has("skull")) {
                skullRenderInfo = GSON.fromJson(features.get("skull"), SkullRenderInfo.class);
            }
        }
        graveModel = LayerDefinition.create(modelData, uvX, uvY).bakeRoot();
    }

    private static ModelPart getGraveModel() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();
        addChildPart(root, "ground", 0, 0, 0, 0, 0, 16, 1, 16);
        addChildPart(root, "base", 0, 21, 2, 1, 10, 12, 2, 5);
        addChildPart(root, "bust", 0, 28, 3, 3, 11, 10, 12, 3);
        addChildPart(root, "top", 0, 17, 4, 15, 11, 8, 1, 3);

        return LayerDefinition.create(modelData, 64, 64).bakeRoot();
    }

    private static void addChildPart(PartDefinition root, String name, int uvX, int uvY, float minX, float minY, float minZ, float sizeX, float sizeY, float sizeZ) {
        root.addOrReplaceChild(
                name,
                CubeListBuilder.create().texOffs(uvX, uvY).addBox(minX, minY, minZ, sizeX, sizeY, sizeZ),
                PartPose.offsetAndRotation(sizeX + minX * 2, sizeY + minY * 2, 0, 0, 0, (float) Math.PI));
    }

    static {
        graveModel = getGraveModel();
    }

    private static final class TextRenderInfo {
        private float depth;
        private float height;
        private float width;

        private TextRenderInfo() {
        }

        private TextRenderInfo(float depth, float height, float width) {
            this.depth = depth;
            this.height = height;
            this.width = width;
        }
    }

    private static final class SkullRenderInfo {
        private float depth;
        private float height;
        private int[] rotation;
        private float scaleFace;
        private float scaleDepth;

        private SkullRenderInfo() {
        }

        private SkullRenderInfo(float depth, float height, int[] rotation, float scaleFace, float scaleDepth) {
            this.depth = depth;
            this.height = height;
            this.rotation = rotation;
            this.scaleFace = scaleFace;
            this.scaleDepth = scaleDepth;
        }
    }
}
