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
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.SkullBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.model.GenericHeadModel;
import net.minecraft.client.renderer.entity.model.HumanoidHeadModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.client.renderer.model.RenderMaterial;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.model.DragonHeadModel;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.client.model.data.EmptyModelData;
import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

public class GraveBlockEntityRenderer extends TileEntityRenderer<GraveBlockEntity> {
    private static final Gson GSON = new Gson();
    private static final float GLOW_R = 1.0F;
    private static final float GLOW_G = 1.0F;
    private static final float GLOW_B = 1.0F;
    private static final float GLOW_A = 96.0F / 255.0F;
    private static final int GLOW_LIGHT = LightTexture.pack(15, 15);

    private static ModelRenderer graveModel;
    @Nullable
    private static TextRenderInfo textRenderInfo = null;
    @Nullable
    private static SkullRenderInfo skullRenderInfo = null;
    private static final Map<String, RenderMaterial> CUBOID_SPRITES = new HashMap<>();
    private static final Map<String, ModelRenderer> CUBOID_PARTS = new HashMap<>();
    private static final Map<String, CuboidInfo> CUBOID_BOUNDS = new HashMap<>();
    private static int modelTextureWidth = 64;
    private static int modelTextureHeight = 64;

    private final Map<SkullBlock.ISkullType, GenericHeadModel> skullModels;
    private final FontRenderer textRenderer;
    private final Minecraft client;
    private final boolean adaptRenderer;

    public static boolean syncedGlowing = true;
    public static int syncedGlowingMaxDistance = Integer.MAX_VALUE;
    public static double syncedDeathSightDistance = Integer.MAX_VALUE;

    public GraveBlockEntityRenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
        this.skullModels = new HashMap<>();
        GenericHeadModel genericHead = new GenericHeadModel(0, 0, 64, 32);
        GenericHeadModel humanoidHead = new HumanoidHeadModel();
        DragonHeadModel dragonHead = new DragonHeadModel(0.0F);
        this.skullModels.put(SkullBlock.Types.SKELETON, genericHead);
        this.skullModels.put(SkullBlock.Types.WITHER_SKELETON, genericHead);
        this.skullModels.put(SkullBlock.Types.PLAYER, humanoidHead);
        this.skullModels.put(SkullBlock.Types.ZOMBIE, humanoidHead);
        this.skullModels.put(SkullBlock.Types.CREEPER, genericHead);
        this.skullModels.put(SkullBlock.Types.DRAGON, dragonHead);

        this.textRenderer = Minecraft.getInstance().font;
        this.client = Minecraft.getInstance();
        this.adaptRenderer = YigdConfig.getConfig().graveRendering.adaptRenderer;
    }

    @Override
    public void render(GraveBlockEntity entity, float tickDelta, MatrixStack poseStack, IRenderTypeBuffer buffers, int light, int overlay) {
        GraveRenderingConfig config = YigdConfig.getConfig().graveRendering;
        if (!config.useCustomFeatureRenderer) return;

        BlockState state = entity.getBlockState();
        Direction direction = state.getValue(BlockStateProperties.HORIZONTAL_FACING);

        float rotation;
        switch (direction) {
            case SOUTH:
                rotation = (float) Math.PI;
                break;
            case WEST:
                rotation = (float) (Math.PI * 0.5D);
                break;
            case EAST:
                rotation = (float) (Math.PI * 1.5D);
                break;
            default:
                rotation = 0f;  // North (can't be up/down)
                break;
        }

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

    private void renderOwnerSkull(GraveBlockEntity entity, float tickDelta, MatrixStack poseStack, IRenderTypeBuffer buffers, int light, int overlay) {
        GameProfile skullOwner = entity.getGraveSkull();
        if (skullOwner == null) return;

        RenderType renderLayer = getSkullRenderType(skullOwner);

        this.renderSkull(poseStack, buffers, light, renderLayer, tickDelta);
    }

    /**
     * Render the model with given RenderType. This lets us draw the skull both with its skin texture and with the outline buffer.
     */
    private void renderSkull(MatrixStack poseStack, IRenderTypeBuffer buffers, int light, RenderType renderLayer, float tickDelta) {
        if (skullRenderInfo == null) return;
        GenericHeadModel model = this.skullModels.get(SkullBlock.Types.PLAYER);
        if (model == null) return;

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.25f, 0.5f);

        int[] rotation = skullRenderInfo.rotation;

        poseStack.translate(0D, -(4 - skullRenderInfo.height) / 16D, -(8 - skullRenderInfo.depth) / 16D);

        Quaternion angle = new Quaternion(rotation[0], rotation[1], rotation[2], true);
        poseStack.mulPose(angle);
        poseStack.scale(skullRenderInfo.scaleFace, skullRenderInfo.scaleFace, skullRenderInfo.scaleDepth);

        poseStack.translate(-0.5f, -0.25f, -0.5f);

        IVertexBuilder builder = buffers.getBuffer(renderLayer);
        model.setupAnim(tickDelta, 0.0F, 0.0F);
        model.renderToBuffer(poseStack, builder, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    private static RenderType getSkullRenderType(@Nullable GameProfile profile) {
        if (profile != null) {
            Minecraft minecraft = Minecraft.getInstance();
            Map<Type, MinecraftProfileTexture> textures = minecraft.getSkinManager().getInsecureSkinInformation(profile);
            if (textures.containsKey(Type.SKIN)) {
                ResourceLocation skin = minecraft.getSkinManager().registerTexture(textures.get(Type.SKIN), Type.SKIN);
                return RenderType.entityTranslucent(skin);
            }
            return RenderType.entityCutoutNoCull(DefaultPlayerSkin.getDefaultSkin(PlayerEntity.createPlayerUUID(profile)));
        }
        return RenderType.entityCutoutNoCullZOffset(DefaultPlayerSkin.getDefaultSkin());
    }

    private void renderGraveText(GraveBlockEntity entity, MatrixStack poseStack, IRenderTypeBuffer buffers, int light) {
        ITextComponent graveText = entity.getGraveText();
        if (graveText == null || textRenderInfo == null) return;

        poseStack.pushPose();

        poseStack.translate(.5, textRenderInfo.height / 16f, textRenderInfo.depth / 16f - 0.0001f);
        poseStack.scale(-1, -1, 0);

        int textWidth = this.textRenderer.width(graveText.getString());
        if (textWidth == 0) {
            poseStack.popPose();
            return;
        }
        float scale = textRenderInfo.width / (textWidth * 16f);
        poseStack.scale(scale, scale, scale);

        poseStack.translate(-textWidth / 2.0, -4.5, 0);

        this.textRenderer.drawInBatch(graveText, 0f, 0f, 0xFFFFFF, false, poseStack.last().pose(), buffers, false, 0, light);

        poseStack.popPose();
    }

    private void renderGraveModel(GraveBlockEntity entity, MatrixStack poseStack, IRenderTypeBuffer buffers, int light, int overlay) {
        if (CUBOID_SPRITES.isEmpty()) {
            // Fallback to a single texture if the custom model reload didn't populate sprites.
            RenderMaterial fallback = new RenderMaterial(PlayerContainer.BLOCK_ATLAS, new ResourceLocation(Yigd.MOD_ID, "block/grave"));
            IVertexBuilder consumer = fallback.buffer(buffers, RenderType::entityCutout);
            graveModel.render(poseStack, consumer, light, overlay);
            return;
        }
        for (Map.Entry<String, RenderMaterial> cuboid : CUBOID_SPRITES.entrySet()) {
            String key = cuboid.getKey();
            ModelRenderer part = CUBOID_PARTS.get(key);
            if (part == null) {
                continue;
            }
            if (this.adaptRenderer && key.equals("ground")) {
                World world = entity.getLevel();
                if (world != null) {
                    BlockPos underPos = entity.getBlockPos().below();
                    BlockState blockUnder = world.getBlockState(underPos);

                    if (blockUnder.isSolidRender(world, underPos)) {
                        CuboidInfo cuboidInfo = CUBOID_BOUNDS.get(key);
                        if (cuboidInfo != null) {
                            float scaleX = cuboidInfo.maxX - cuboidInfo.minX;
                            float scaleZ = cuboidInfo.maxZ - cuboidInfo.minZ;

                            poseStack.pushPose();

                            poseStack.translate(cuboidInfo.minX / 16f + .0005f, cuboidInfo.maxY / 16f - 1f, cuboidInfo.minZ / 16f + .0005f);
                            poseStack.scale(.999f * (scaleX / 16f), 1f, .999f * (scaleZ / 16f));

                            IVertexBuilder blockBuffer = buffers.getBuffer(RenderType.cutout());
                            this.client.getBlockRenderer()
                                    .renderModel(blockUnder, underPos, world, poseStack, blockBuffer, false, world.random, EmptyModelData.INSTANCE);
                            poseStack.popPose();

                            continue;
                        }
                    }
                }
            }
            IVertexBuilder consumer = cuboid.getValue().buffer(buffers, RenderType::entityCutout);

            part.render(poseStack, consumer, light, overlay);
        }
    }

    private void renderGlowingOverlay(GraveBlockEntity entity, MatrixStack poseStack, IRenderTypeBuffer buffers, int overlay) {
        ClientPlayerEntity player = this.client.player;
        if (player == null || !YigdClientEventHandler.shouldRenderGlowing(entity, player)) return;

        if (CUBOID_SPRITES.isEmpty()) {
            RenderMaterial fallback = new RenderMaterial(PlayerContainer.BLOCK_ATLAS, new ResourceLocation(Yigd.MOD_ID, "block/grave"));
            IVertexBuilder consumer = fallback.buffer(buffers, RenderType::entityTranslucent);
            graveModel.render(poseStack, consumer, GLOW_LIGHT, overlay, GLOW_R, GLOW_G, GLOW_B, GLOW_A);
            return;
        }
        for (Map.Entry<String, RenderMaterial> cuboid : CUBOID_SPRITES.entrySet()) {
            ModelRenderer part = CUBOID_PARTS.get(cuboid.getKey());
            if (part == null) {
                continue;
            }
            IVertexBuilder consumer = cuboid.getValue().buffer(buffers, RenderType::entityTranslucent);
            part.render(poseStack, consumer, GLOW_LIGHT, overlay, GLOW_R, GLOW_G, GLOW_B, GLOW_A);
        }
    }

    /**
     * Takes JSON and reloads current grave model to what the JSON describes.
     * @param json Model json (same format that block models use).
     * @throws IllegalStateException if the model json is incomplete or wrong
     */
    public static void reloadModelFromJson(JsonObject json) throws IllegalStateException {
        CUBOID_SPRITES.clear();
        CUBOID_PARTS.clear();
        CUBOID_BOUNDS.clear();

        JsonArray textureSize = json.getAsJsonArray("texture_size");
        JsonObject textures = json.getAsJsonObject("textures");
        JsonArray elements = json.getAsJsonArray("elements");
        JsonObject features = json.has("features") ? json.getAsJsonObject("features") : null;

        modelTextureWidth = textureSize.get(0).getAsInt();
        modelTextureHeight = textureSize.get(1).getAsInt();
        Map<String, String> nameIds = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
            nameIds.put(e.getKey(), e.getValue().getAsString());
        }

        ModelRenderer root = new ModelRenderer(modelTextureWidth, modelTextureHeight, 0, 0);
        int i = 0;
        for (JsonElement e : elements) {
            JsonObject o = e.getAsJsonObject();
            String name = o.has("name") ? o.get("name").getAsString() : String.valueOf(i++);
            JsonArray from = o.getAsJsonArray("from");
            JsonArray to = o.getAsJsonArray("to");
            JsonObject faces = o.getAsJsonObject("faces");

            float minU = Float.MAX_VALUE;
            float minV = Float.MAX_VALUE;
            String textureName = "";
            for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                JsonObject value = face.getValue().getAsJsonObject();
                JsonArray uv = value.getAsJsonArray("uv");
                textureName = value.get("texture").getAsString();

                minU = Math.min(minU, uv.get(0).getAsFloat());
                minV = Math.min(minV, uv.get(1).getAsFloat());
            }
            minU *= modelTextureWidth / 16f;
            minV *= modelTextureHeight / 16f;

            textureName = textureName.replaceFirst("#", "");
            if (nameIds.containsKey(textureName)) {
                textureName = nameIds.get(textureName);
            }
            ResourceLocation texture = new ResourceLocation(textureName);
            RenderMaterial sprite = new RenderMaterial(PlayerContainer.BLOCK_ATLAS, texture);

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

            ModelRenderer part = addChildPart(root, (int) minU, (int) minV, lowerX, lowerY, lowerZ, higherX - lowerX, higherY - lowerY, higherZ - lowerZ);
            CUBOID_PARTS.put(name, part);
            CUBOID_BOUNDS.put(name, new CuboidInfo(lowerX, lowerY, lowerZ, higherX, higherY, higherZ));
        }
        if (features != null) {
            if (features.has("text")) {
                textRenderInfo = GSON.fromJson(features.get("text"), TextRenderInfo.class);
            }
            if (features.has("skull")) {
                skullRenderInfo = GSON.fromJson(features.get("skull"), SkullRenderInfo.class);
            }
        }
        graveModel = root;
    }

    private static ModelRenderer getGraveModel() {
        modelTextureWidth = 64;
        modelTextureHeight = 64;
        ModelRenderer root = new ModelRenderer(modelTextureWidth, modelTextureHeight, 0, 0);
        addChildPart(root, 0, 0, 0, 0, 0, 16, 1, 16);
        addChildPart(root, 0, 21, 2, 1, 10, 12, 2, 5);
        addChildPart(root, 0, 28, 3, 3, 11, 10, 12, 3);
        addChildPart(root, 0, 17, 4, 15, 11, 8, 1, 3);

        return root;
    }

    private static ModelRenderer addChildPart(ModelRenderer root, int uvX, int uvY, float minX, float minY, float minZ, float sizeX, float sizeY, float sizeZ) {
        ModelRenderer part = new ModelRenderer(modelTextureWidth, modelTextureHeight, uvX, uvY);
        part.addBox(minX, minY, minZ, sizeX, sizeY, sizeZ);
        part.setPos(sizeX + minX * 2, sizeY + minY * 2, 0);
        part.zRot = (float) Math.PI;
        root.addChild(part);
        return part;
    }

    static {
        graveModel = getGraveModel();
    }

    private static class TextRenderInfo {
        public float depth;
        public float height;
        public float width;
    }

    private static class SkullRenderInfo {
        public float depth;
        public float height;
        public int[] rotation;
        public float scaleFace;
        public float scaleDepth;
    }

    private static class CuboidInfo {
        public final float minX;
        public final float minY;
        public final float minZ;
        public final float maxX;
        public final float maxY;
        public final float maxZ;

        public CuboidInfo(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }
}
