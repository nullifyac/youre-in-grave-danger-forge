package com.b1n_ry.yigd;

import com.b1n_ry.yigd.block.GraveBlock;
import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.compat.InvModCompat;
import com.b1n_ry.yigd.config.ClaimPriority;
import com.b1n_ry.yigd.config.GraveConfig;
import com.b1n_ry.yigd.config.MapEntryConfig;
import com.b1n_ry.yigd.config.YigdConfig;
import com.b1n_ry.yigd.enchantment.DeathSightEnchantment;
import com.b1n_ry.yigd.enchantment.SoulboundEnchantment;
import com.b1n_ry.yigd.item.DeathScrollItem;
import com.b1n_ry.yigd.item.GraveKeyItem;
import com.b1n_ry.yigd.packets.YigdNetwork;
import com.b1n_ry.yigd.util.YigdCommands;
import com.b1n_ry.yigd.util.YigdResourceHandler;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod(Yigd.MOD_ID)
public class Yigd {
    public static final String MOD_ID = "yigd";

    public static final Logger LOGGER = LoggerFactory.getLogger("YIGD");

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    private static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, MOD_ID);

    public static GraveBlock GRAVE_BLOCK;
    public static final RegistryObject<GraveBlock> GRAVE_BLOCK_REG = BLOCKS.register("grave", () ->
            GRAVE_BLOCK = new GraveBlock(BlockBehaviour.Properties.of().strength(-1.0f, 3600000.0f).noOcclusion()));
    public static final RegistryObject<BlockItem> GRAVE_BLOCK_ITEM = ITEMS.register("grave", () -> new BlockItem(GRAVE_BLOCK_REG.get(), new Item.Properties()));

    public static DeathScrollItem DEATH_SCROLL_ITEM;
    public static final RegistryObject<DeathScrollItem> DEATH_SCROLL_ITEM_REG =
            ITEMS.register("death_scroll", () -> DEATH_SCROLL_ITEM = new DeathScrollItem(new Item.Properties()));

    public static GraveKeyItem GRAVE_KEY_ITEM;
    public static final RegistryObject<GraveKeyItem> GRAVE_KEY_ITEM_REG =
            ITEMS.register("grave_key", () -> GRAVE_KEY_ITEM = new GraveKeyItem(new Item.Properties()));

    public static BlockEntityType<GraveBlockEntity> GRAVE_BLOCK_ENTITY;
    public static final RegistryObject<BlockEntityType<GraveBlockEntity>> GRAVE_BLOCK_ENTITY_REG =
            BLOCK_ENTITY_TYPES.register("grave_block_entity", () ->
                    GRAVE_BLOCK_ENTITY = BlockEntityType.Builder.of(GraveBlockEntity::new, GRAVE_BLOCK_REG.get()).build(null));

    public static SoulboundEnchantment SOULBOUND_ENCHANTMENT;
    public static DeathSightEnchantment DEATH_SIGHT_ENCHANTMENT;

    /**
     * Any runnable added to this list will be executed on the end of the current server tick.
     * Use if runnable is required to run before some other event that would have otherwise ran before.
     */
    public static final List<Runnable> END_OF_TICK = new ArrayList<>();

    public static final Map<UUID, List<String>> NOT_NOTIFIED_ROBBERIES = new HashMap<>();
    public static final Map<UUID, ClaimPriority> CLAIM_PRIORITIES = new HashMap<>();
    public static final Map<UUID, ClaimPriority> ROB_PRIORITIES = new HashMap<>();

    public Yigd() {
        AutoConfig.register(YigdConfig.class, GsonConfigSerializer::new);
        runConfigMigrations();
        registerOptionalEnchantments();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        ENCHANTMENTS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreativeTabEntries);
        modEventBus.addListener(YigdResourceHandler::registerClientReloadListeners);

        MinecraftForge.EVENT_BUS.addListener(this::handleServerStarted);
        MinecraftForge.EVENT_BUS.addListener(YigdCommands::register);
        MinecraftForge.EVENT_BUS.addListener(YigdResourceHandler::registerServerReloadListeners);
    }

    private void runConfigMigrations() {
        try {
            boolean changed = false;
            YigdConfig config = YigdConfig.getConfig();

            // Config migration: Ensure per-dimension min grave Y contains Twilight Forest so it doesn't fall back to "misc".
            if (config.graveConfig != null) {
                if (config.graveConfig.minimumGraveYLevel == null) {
                    config.graveConfig.minimumGraveYLevel = new GraveConfig().minimumGraveYLevel;
                    changed = true;
                }

                boolean hasTwilightForestEntry = false;
                for (MapEntryConfig.IntType entry : config.graveConfig.minimumGraveYLevel) {
                    if (entry != null && "twilightforest:twilight_forest".equals(entry.key)) {
                        hasTwilightForestEntry = true;
                        break;
                    }
                }
                if (!hasTwilightForestEntry) {
                    config.graveConfig.minimumGraveYLevel.add(new MapEntryConfig.IntType("twilightforest:twilight_forest", -60));
                    changed = true;
                }
            }

            if (changed) {
                AutoConfig.getConfigHolder(YigdConfig.class).save();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to run YiGD config migrations", e);
        }
    }

    private void registerOptionalEnchantments() {
        YigdConfig config = YigdConfig.getConfig();
        if (config.extraFeatures.soulboundEnchant.enabled) {
            ENCHANTMENTS.register("soulbound", () -> {
                SOULBOUND_ENCHANTMENT = new SoulboundEnchantment(Enchantment.Rarity.VERY_RARE);
                return SOULBOUND_ENCHANTMENT;
            });
        }
        if (config.extraFeatures.deathSightEnchant.enabled) {
            ENCHANTMENTS.register("death_sight", () -> {
                DEATH_SIGHT_ENCHANTMENT = new DeathSightEnchantment(Enchantment.Rarity.RARE, EquipmentSlot.HEAD);
                return DEATH_SIGHT_ENCHANTMENT;
            });
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            InvModCompat.reloadModCompat();
            YigdNetwork.register();
        });
    }

    private void addCreativeTabEntries(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(GRAVE_BLOCK_ITEM.get());
            event.accept(DEATH_SCROLL_ITEM);
            event.accept(GRAVE_KEY_ITEM);
        }
    }

    private void handleServerStarted(ServerStartedEvent event) {
        InvModCompat.reloadModCompat();
    }
}
