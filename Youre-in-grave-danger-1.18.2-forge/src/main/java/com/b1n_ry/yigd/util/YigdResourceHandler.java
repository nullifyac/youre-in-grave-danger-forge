package com.b1n_ry.yigd.util;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.block.GraveBlock;
import com.b1n_ry.yigd.client.render.GraveBlockEntityRenderer;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.data.GraveyardData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.AddReloadListenerEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class YigdResourceHandler {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ResourceLocation.class, (JsonDeserializer<ResourceLocation>) (elem, type, context) -> new ResourceLocation(elem.getAsString()))
            .registerTypeAdapter(Vec3i.class, (JsonDeserializer<Vec3i>) (elem, type, context) -> new Vec3i(
                    elem.getAsJsonArray().get(0).getAsInt(),
                    elem.getAsJsonArray().get(1).getAsInt(),
                    elem.getAsJsonArray().get(2).getAsInt()))
            .create();

    private YigdResourceHandler() { }

    public static void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new GraveResourceLoader());
    }

    public static void registerServerReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new GraveServerModelLoader());
        event.addListener(new GraveyardDataLoader());
        event.addListener(new GraveAreaOverrideLoader());
    }

    private abstract static class SyncReloadListener implements PreparableReloadListener {
        private final String id;

        protected SyncReloadListener(String name) {
            this.id = Yigd.MOD_ID + ":" + name;
        }

        @Override
        public String getName() {
            return this.id;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager,
                                              ProfilerFiller prepProfiler, ProfilerFiller reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return CompletableFuture
                    .supplyAsync(() -> {
                        this.reloadResources(manager);
                        return null;
                    }, backgroundExecutor)
                    .thenCompose(barrier::wait)
                    .thenAcceptAsync(v -> {}, gameExecutor);
        }

        protected abstract void reloadResources(ResourceManager manager);
    }

    private static class GraveResourceLoader extends SyncReloadListener {
        private GraveResourceLoader() {
            super("custom_grave_model");
        }

        @Override
        protected void reloadResources(ResourceManager manager) {
            loadFrom(manager, new ResourceLocation(Yigd.MOD_ID, "models/block/grave.json"), true);
        }

        private void loadFrom(ResourceManager manager, ResourceLocation resourceLocation, boolean client) {
            try {
                List<Resource> resources = manager.getResources(resourceLocation);
                for (Resource resource : resources) {
                    try (InputStream is = resource.getInputStream()) {
                        Yigd.LOGGER.info("Reloading grave model ({})", client ? "client" : "server");
                        JsonObject json = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
                        GraveBlockEntityRenderer.reloadModelFromJson(json);
                        GraveBlock.reloadShapeFromJson(json);
                        Yigd.LOGGER.info("Grave model and shape reload successful ({})", client ? "client" : "server");
                    } catch (IOException | ClassCastException | NullPointerException e) {
                        Yigd.LOGGER.error("Could not load resource '{}' from resource pack '{}'", resourceLocation, resource.getSourceName(), e);
                    }
                }
            } catch (IOException e) {
                Yigd.LOGGER.error("Could not enumerate resources for '{}'", resourceLocation, e);
            }
        }
    }

    private static class GraveServerModelLoader extends SyncReloadListener {
        private GraveServerModelLoader() {
            super("custom_server_grave_shape");
        }

        @Override
        protected void reloadResources(ResourceManager manager) {
            loadServerShape(manager, new ResourceLocation(Yigd.MOD_ID, "custom/grave_shape.json"));
        }

        private void loadServerShape(ResourceManager manager, ResourceLocation resourceLocation) {
            try {
                List<Resource> resources = manager.getResources(resourceLocation);
                for (Resource resource : resources) {
                    try (InputStream is = resource.getInputStream()) {
                        Yigd.LOGGER.info("Reloading grave shape (server)");
                        JsonObject json = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
                        GraveBlock.reloadShapeFromJson(json);
                        Yigd.LOGGER.info("Grave model and shape reload successful (server)");
                    } catch (IOException | ClassCastException | NullPointerException e) {
                        Yigd.LOGGER.error("Could not load resource '{}' from datapack '{}'", resourceLocation, resource.getSourceName(), e);
                    }
                }
            } catch (IOException e) {
                Yigd.LOGGER.error("Could not enumerate resources for '{}'", resourceLocation, e);
            }
        }
    }

    private static class GraveyardDataLoader extends SyncReloadListener {
        private GraveyardDataLoader() {
            super("graveyard");
        }

        @Override
        protected void reloadResources(ResourceManager manager) {
            ResourceLocation resourceLocation = new ResourceLocation(Yigd.MOD_ID, "custom/graveyard.json");
            try {
                List<Resource> resources = manager.getResources(resourceLocation);
                for (Resource resource : resources) {
                    try (InputStream is = resource.getInputStream()) {
                        Yigd.LOGGER.info("Reloading YIGD graveyard data (server)");
                        GraveComponent.graveyardData = GSON.fromJson(new InputStreamReader(is), GraveyardData.class);
                        GraveComponent.graveyardData.handlePoint2Point();
                        Yigd.LOGGER.info("Graveyard data successfully reloaded (server)");
                    } catch (IOException | ClassCastException | NullPointerException e) {
                        Yigd.LOGGER.error("Could not load resource '{}' from datapack '{}'", resourceLocation, resource.getSourceName(), e);
                    }
                }
            } catch (IOException e) {
                Yigd.LOGGER.error("Could not enumerate resources for '{}'", resourceLocation, e);
            }
        }
    }

    private static class GraveAreaOverrideLoader extends SyncReloadListener {
        private GraveAreaOverrideLoader() {
            super("grave_area_override");
        }

        @Override
        protected void reloadResources(ResourceManager manager) {
            ResourceLocation resourceLocation = new ResourceLocation(Yigd.MOD_ID, "custom/grave_areas.json");
            try {
                List<Resource> resources = manager.getResources(resourceLocation);
                for (Resource resource : resources) {
                    try (InputStream is = resource.getInputStream()) {
                        Yigd.LOGGER.info("Reloading YIGD grave area overrides (server)");
                        GraveOverrideAreas.INSTANCE = GSON.fromJson(new InputStreamReader(is), GraveOverrideAreas.class);
                        Yigd.LOGGER.info("Grave area overrides successfully reloaded (server)");
                    } catch (IOException | ClassCastException | NullPointerException e) {
                        Yigd.LOGGER.error("Could not load resource '{}' from datapack '{}'", resourceLocation, resource.getSourceName(), e);
                    }
                }
            } catch (IOException e) {
                Yigd.LOGGER.error("Could not enumerate resources for '{}'", resourceLocation, e);
            }
        }
    }
}
