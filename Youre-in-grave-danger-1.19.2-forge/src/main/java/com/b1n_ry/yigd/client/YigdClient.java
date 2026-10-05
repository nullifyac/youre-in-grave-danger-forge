package com.b1n_ry.yigd.client;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.client.render.GraveBlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Yigd.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class YigdClient {
    private YigdClient() { }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> BlockEntityRenderers.register(Yigd.GRAVE_BLOCK_ENTITY.get(), GraveBlockEntityRenderer::new));
        // TODO: Register networking listeners and config sync once packet handlers are ported to Forge networking
    }
}
