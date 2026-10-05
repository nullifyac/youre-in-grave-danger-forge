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
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IFutureReloadListener;
import net.minecraft.resources.IResource;
import net.minecraft.resources.IResourceManager;
import net.minecraft.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3i;
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
            .registerTypeAdapter(Vector3i.class, (JsonDeserializer<Vector3i>) (elem, type, context) -> new Vector3i(
                    elem.getAsJsonArray().get(0).getAsInt(),
                    elem.getAsJsonArray().get(1).getAsInt(),
                    elem.getAsJsonArray().get(2).getAsInt()))
            .create();

    private YigdResourceHandler() { }

    public static void registerClientReloadListeners(IResourceManager manager) {
        if (manager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) manager).registerReloadListener(new GraveResourceLoader());
        }
    }

    public static void registerServerReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new GraveServerModelLoader());
        event.addListener(new GraveyardDataLoader());
        event.addListener(new GraveAreaOverrideLoader());
    }

    private abstract static class SyncReloadListener implements IFutureReloadListener {
        private final String id;

        protected SyncReloadListener(String name) {
            this.id = Yigd.MOD_ID + ":" + name;
        }

        @Override
        public String getName() {
            return this.id;
        }

        @Override
        public CompletableFuture<Void> reload(IStage stage, IResourceManager manager,
                                              IProfiler prepProfiler, IProfiler reloadProfiler,
                                              Executor backgroundExecutor, Executor gameExecutor) {
            return CompletableFuture
                    .supplyAsync(() -> {
                        this.reloadResources(manager);
                        return null;
                    }, backgroundExecutor)
                    .thenCompose(stage::wait)
                    .thenAcceptAsync(v -> {}, gameExecutor);
        }

        protected abstract void reloadResources(IResourceManager manager);
    }

    private static class GraveResourceLoader extends SyncReloadListener {
        private GraveResourceLoader() {
            super("custom_grave_model");
        }

        @Override
        protected void reloadResources(IResourceManager manager) {
            loadFrom(manager, new ResourceLocation(Yigd.MOD_ID, "models/block/grave.json"), true);
        }

        private void loadFrom(IResourceManager manager, ResourceLocation resourceLocation, boolean client) {
            List<IResource> resources;
            try {
                resources = manager.getResources(resourceLocation);
            } catch (IOException e) {
                Yigd.LOGGER.error("Could not load resource '{}' from resource pack", resourceLocation, e);
                return;
            }
            for (IResource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    Yigd.LOGGER.info("Reloading grave model ({})", client ? "client" : "server");
                    JsonObject json = new JsonParser().parse(new InputStreamReader(is)).getAsJsonObject();
                    GraveBlockEntityRenderer.reloadModelFromJson(json);
                    GraveBlock.reloadShapeFromJson(json);
                    Yigd.LOGGER.info("Grave model and shape reload successful ({})", client ? "client" : "server");
                } catch (IOException | ClassCastException | NullPointerException e) {
                    Yigd.LOGGER.error("Could not load resource '{}' from resource pack '{}'", resourceLocation, resource.getSourceName(), e);
                }
            }
        }
    }

    private static class GraveServerModelLoader extends SyncReloadListener {
        private GraveServerModelLoader() {
            super("custom_server_grave_shape");
        }

        @Override
        protected void reloadResources(IResourceManager manager) {
            loadServerShape(manager, new ResourceLocation(Yigd.MOD_ID, "custom/grave_shape.json"));
        }

        private void loadServerShape(IResourceManager manager, ResourceLocation resourceLocation) {
            List<IResource> resources;
            try {
                resources = manager.getResources(resourceLocation);
            } catch (IOException e) {
                Yigd.LOGGER.error("Could not load resource '{}' from datapack", resourceLocation, e);
                return;
            }
            for (IResource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    Yigd.LOGGER.info("Reloading grave shape (server)");
                    JsonObject json = new JsonParser().parse(new InputStreamReader(is)).getAsJsonObject();
                    GraveBlock.reloadShapeFromJson(json);
                    Yigd.LOGGER.info("Grave model and shape reload successful (server)");
                } catch (IOException | ClassCastException | NullPointerException e) {
                    Yigd.LOGGER.error("Could not load resource '{}' from datapack '{}'", resourceLocation, resource.getSourceName(), e);
                }
            }
        }
    }

    private static class GraveyardDataLoader extends SyncReloadListener {
        private GraveyardDataLoader() {
            super("graveyard");
        }

        @Override
        protected void reloadResources(IResourceManager manager) {
            ResourceLocation resourceLocation = new ResourceLocation(Yigd.MOD_ID, "custom/graveyard.json");
            List<IResource> resources;
            try {
                resources = manager.getResources(resourceLocation);
            } catch (IOException e) {
                Yigd.LOGGER.error("Could not load resource '{}' from datapack", resourceLocation, e);
                return;
            }
            for (IResource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    Yigd.LOGGER.info("Reloading YIGD graveyard data (server)");
                    GraveComponent.graveyardData = GSON.fromJson(new InputStreamReader(is), GraveyardData.class);
                    GraveComponent.graveyardData.handlePoint2Point();
                    Yigd.LOGGER.info("Graveyard data successfully reloaded (server)");
                } catch (IOException | ClassCastException | NullPointerException e) {
                    Yigd.LOGGER.error("Could not load resource '{}' from datapack '{}'", resourceLocation, resource.getSourceName(), e);
                }
            }
        }
    }

    private static class GraveAreaOverrideLoader extends SyncReloadListener {
        private GraveAreaOverrideLoader() {
            super("grave_area_override");
        }

        @Override
        protected void reloadResources(IResourceManager manager) {
            ResourceLocation resourceLocation = new ResourceLocation(Yigd.MOD_ID, "custom/grave_areas.json");
            List<IResource> resources;
            try {
                resources = manager.getResources(resourceLocation);
            } catch (IOException e) {
                Yigd.LOGGER.error("Could not load resource '{}' from datapack", resourceLocation, e);
                return;
            }
            for (IResource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    Yigd.LOGGER.info("Reloading YIGD grave area overrides (server)");
                    GraveOverrideAreas.INSTANCE = GSON.fromJson(new InputStreamReader(is), GraveOverrideAreas.class);
                    Yigd.LOGGER.info("Grave area overrides successfully reloaded (server)");
                } catch (IOException | ClassCastException | NullPointerException e) {
                    Yigd.LOGGER.error("Could not load resource '{}' from datapack '{}'", resourceLocation, resource.getSourceName(), e);
                }
            }
        }
    }
}
