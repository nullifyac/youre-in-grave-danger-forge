package com.b1n_ry.yigd.client;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.client.render.GraveBlockEntityRenderer;
import com.b1n_ry.yigd.config.GraveConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.events.YigdClientEventHandler;
import com.b1n_ry.yigd.networking.packets.UpdateConfigC2SPacket;
import com.b1n_ry.yigd.util.YigdResourceHandler;
import me.shedaniel.autoconfig.AutoConfigClient;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@Mod(value = Yigd.MOD_ID, dist = Dist.CLIENT)
public class YigdClient {
    public YigdClient(IEventBus modBus, ModContainer modContainer) {
        modBus.addListener(this::registerRenderers);

        modBus.addListener(YigdResourceHandler::clientResourceEvent);
        NeoForge.EVENT_BUS.register(new YigdClientEventHandler());

        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, screen) -> AutoConfigClient.getConfigScreen(YigdConfig.class, screen).get());

        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> {
            GraveConfig graveConfig = YigdConfig.getConfig().graveConfig;
            ClientPacketDistributor.sendToServer(new UpdateConfigC2SPacket(graveConfig.claimPriority, graveConfig.graveRobbing.robPriority));
        });
    }

    public void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Yigd.GRAVE_BLOCK_ENTITY.get(), GraveBlockEntityRenderer::new);
    }
}
