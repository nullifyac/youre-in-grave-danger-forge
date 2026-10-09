package com.b1n_ry.yigd.client.render;

import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.config.GraveRenderingConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.events.YigdEvents;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;

import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Extracts live grave data once, then submits immutable frame data to the native render pipeline. */
public class GraveBlockEntityRenderer implements BlockEntityRenderer<GraveBlockEntity, GraveBlockEntityRenderer.State> {
    private static final Gson GSON = new Gson();
    private static volatile ModelDefinition modelDefinition = new ModelDefinition(getGraveModel(), Map.of(), null, null);
    // The native outline shader samples only alpha. This opaque vanilla texture exists in 26.1;
    // textures/misc/white.png no longer does, and would fall back to the missing texture.
    private static final RenderType OUTLINE_RENDER_LAYER = RenderTypes.outline(Identifier.withDefaultNamespace("textures/block/white_concrete.png"));

    private final SkullModelBase skullModel;
    private final Font textRenderer;
    private final PlayerSkinRenderCache skinRenderCache;
    private final SpriteGetter sprites;

    public static boolean syncedGlowing = true;
    public static int syncedGlowingMaxDistance = Integer.MAX_VALUE;
    public static double syncedDeathSightDistance = Integer.MAX_VALUE;

    public GraveBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.skullModel = SkullBlockRenderer.createModel(context.entityModelSet(), SkullBlock.Types.PLAYER);
        this.textRenderer = context.font();
        this.skinRenderCache = context.playerSkinRenderCache();
        this.sprites = context.sprites();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GraveBlockEntity entity, State state, float partialTicks,
                                   Vec3 cameraPosition, @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
        GraveRenderingConfig config = YigdConfig.getConfig().graveRendering;
        ModelDefinition definition = modelDefinition;
        state.enabled = config.useCustomFeatureRenderer;
        state.definition = definition;
        state.rotation = switch (entity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            case SOUTH -> (float) Math.PI;
            case WEST -> (float) (Math.PI * 0.5);
            case EAST -> (float) (Math.PI * 1.5);
            default -> 0;
        };
        state.text = config.useTextRenderer ? entity.getGraveText() : null;
        ResolvableProfile owner = entity.getGraveSkull();
        state.skullRenderType = config.useSkullRenderer && owner != null
                ? this.skinRenderCache.getOrDefault(owner).renderType() : null;
        state.glowing = config.useGlowingEffect && entity.isUnclaimed()
                && Minecraft.getInstance().player != null
                && NeoForge.EVENT_BUS.post(new YigdEvents.RenderGlowingGraveEvent(entity, Minecraft.getInstance().player)).isRenderGlowing();
        state.parts.clear();
        state.ground = null;
        for (Map.Entry<String, SpriteId> entry : definition.spriteIds.entrySet()) {
            state.parts.add(new Part(definition.model.getChild(entry.getKey()), this.sprites.get(entry.getValue()),
                    entry.getKey().equals("ground")));
        }
        ClientLevel level = entity.getLevel() instanceof ClientLevel clientLevel ? clientLevel : null;
        if (config.adaptRenderer && level != null && definition.spriteIds.containsKey("ground")) {
            BlockPos underPos = entity.getBlockPos().below();
            BlockState underState = level.getBlockState(underPos);
            if (underState.isSolidRender()) {
                MovingBlockRenderState ground = new MovingBlockRenderState();
                ground.blockPos = underPos;
                ground.randomSeedPos = underPos;
                ground.blockState = underState;
                ground.biome = level.getBiome(underPos);
                ground.cardinalLighting = level.cardinalLighting();
                ground.lightEngine = level.getLightEngine();
                state.ground = ground;
                state.groundCube = definition.model.getChild("ground").getRandomCube(level.getRandom());
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.enabled) return;
        poseStack.pushPose();
        poseStack.rotateAround(Axis.YP.rotation(state.rotation), .5f, .5f, .5f);
        for (Part part : state.parts) {
            if (part.ground && state.ground != null) {
                ModelPart.Cube cube = state.groundCube;
                poseStack.pushPose();
                poseStack.translate(cube.minX / 16f + .0005f, cube.maxY / 16f - 1f, cube.minZ / 16f + .0005f);
                poseStack.scale(.999f * (cube.maxX - cube.minX) / 16f, 1f, .999f * (cube.maxZ - cube.minZ) / 16f);
                collector.submitMovingBlock(poseStack, state.ground);
                poseStack.popPose();
            } else {
                collector.submitModelPart(part.model, poseStack, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS),
                        state.lightCoords, OverlayTexture.NO_OVERLAY, part.sprite, -1, state.breakProgress);
            }
        }
        if (state.glowing) {
            collector.submitModelPart(state.definition.model, poseStack, OUTLINE_RENDER_LAYER, state.lightCoords,
                    OverlayTexture.NO_OVERLAY, null, false, false, -1, null, 0xFFFFFFFF);
        }
        if (state.skullRenderType != null) this.submitSkull(state, poseStack, collector);
        if (state.text != null) this.submitText(state, poseStack, collector);
        poseStack.popPose();
    }

    private void submitSkull(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        SkullRenderInfo info = state.definition.skullInfo;
        if (info == null || this.skullModel == null) return;
        poseStack.pushPose();
        poseStack.translate(.5f, .25f, .5f);
        poseStack.translate(0, -(4 - info.height) / 16.0, -(8 - info.depth) / 16.0);
        poseStack.mulPose(new Quaternionf().rotateXYZ((float) Math.toRadians(info.rotation[0]),
                (float) Math.toRadians(info.rotation[1]), (float) Math.toRadians(info.rotation[2])));
        poseStack.scale(info.scaleFace, info.scaleFace, info.scaleDepth);
        poseStack.translate(0, -.25f, 0);
        // Vanilla's player-skull model uses the same negative X/Y scale as the old renderSkull helper.
        poseStack.scale(-1, -1, 1);
        SkullBlockRenderer.submitSkull(0, poseStack, collector, state.lightCoords, this.skullModel,
                state.skullRenderType, state.glowing ? 0xFFFFFFFF : 0, state.breakProgress);
        poseStack.popPose();
    }

    private void submitText(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        TextRenderInfo info = state.definition.textInfo;
        if (info == null) return;
        int width = this.textRenderer.width(state.text);
        if (width == 0) return;
        poseStack.pushPose();
        poseStack.translate(.5, info.height / 16f, info.depth / 16f - .0001f);
        poseStack.scale(-1, -1, 1);
        float scale = info.width / (width * 16f);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-width / 2.0, -4.5, 0);
        collector.submitText(poseStack, 0, 0, state.text.getVisualOrderText(), false, Font.DisplayMode.NORMAL,
                state.lightCoords, 0xFFFFFFFF, 0, 0);
        poseStack.popPose();
    }

    public static final class State extends BlockEntityRenderState {
        private boolean enabled;
        private boolean glowing;
        private float rotation;
        private ModelDefinition definition;
        private @Nullable Component text;
        private @Nullable RenderType skullRenderType;
        private final List<Part> parts = new ArrayList<>();
        private @Nullable MovingBlockRenderState ground;
        private ModelPart.Cube groundCube;
    }

    private record Part(ModelPart model, TextureAtlasSprite sprite, boolean ground) { }
    private record ModelDefinition(ModelPart model, Map<String, SpriteId> spriteIds,
                                   @Nullable TextRenderInfo textInfo, @Nullable SkullRenderInfo skullInfo) { }

    public static void reloadModelFromJson(JsonObject json) throws IllegalStateException {
        Map<String, SpriteId> spriteIds = new HashMap<>();
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
            String key = e.getKey();
            String value = e.getValue().getAsString();

            nameIds.put(key, value);
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
            Identifier texture = Identifier.parse(textureName);
            SpriteId sprite = new SpriteId(TextureAtlas.LOCATION_BLOCKS, texture);

            spriteIds.put(name, sprite);

            float fromX = from.get(0).getAsFloat();
            float fromY = from.get(1).getAsFloat();
            float fromZ = from.get(2).getAsFloat();
            float toX = to.get(0).getAsFloat();
            float toY = to.get(1).getAsFloat();
            float toZ = to.get(2).getAsFloat();

            // Min no longer have to be in from
            float lowerX = Math.min(fromX, toX);
            float lowerY = Math.min(fromY, toY);
            float lowerZ = Math.min(fromZ, toZ);
            float higherX = Math.max(fromX, toX);
            float higherY = Math.max(fromY, toY);
            float higherZ = Math.max(fromZ, toZ);
            addChildPart(root, name, (int) minX, (int) minY, lowerX, lowerY, lowerZ, higherX - lowerX, higherY - lowerY, higherZ - lowerZ);
        }
        TextRenderInfo textRenderInfo = null;
        SkullRenderInfo skullRenderInfo = null;
        if (features != null) {
            if (features.has("text")) {
                textRenderInfo = GSON.fromJson(features.get("text"), TextRenderInfo.class);
            }
            if (features.has("skull")) {
                skullRenderInfo = GSON.fromJson(features.get("skull"), SkullRenderInfo.class);
            }
        }
        modelDefinition = new ModelDefinition(LayerDefinition.create(modelData, uvX, uvY).bakeRoot(), Map.copyOf(spriteIds), textRenderInfo, skullRenderInfo);
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


    private record TextRenderInfo(float depth, float height, float width) { }
    private record SkullRenderInfo(float depth, float height, int[] rotation, float scaleFace, float scaleDepth) { }
}
