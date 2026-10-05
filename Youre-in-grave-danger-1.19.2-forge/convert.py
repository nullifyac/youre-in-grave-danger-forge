import os
import re

# Package mappings - order matters! More specific first
PACKAGE_MAPPINGS = {
    # Util packages first (more specific)
    'net.minecraft.util.hit.': 'net.minecraft.world.phys.',
    'net.minecraft.util.shape.': 'net.minecraft.world.phys.shapes.',
    'net.minecraft.util.math.BlockPos': 'net.minecraft.core.BlockPos',
    'net.minecraft.util.math.Direction': 'net.minecraft.core.Direction',
    'net.minecraft.util.math.Vec3d': 'net.minecraft.world.phys.Vec3',
    'net.minecraft.util.math.': 'net.minecraft.core.',
    'net.minecraft.util.Hand': 'net.minecraft.world.InteractionHand',
    'net.minecraft.util.ActionResult': 'net.minecraft.world.InteractionResult',
    'net.minecraft.util.Identifier': 'net.minecraft.resources.ResourceLocation',

    # Collections
    'net.minecraft.util.collection.DefaultedList': 'net.minecraft.core.NonNullList',

    # World packages (more specific)
    'net.minecraft.world.tick.': 'net.minecraft.world.ticks.',
    'import net.minecraft.world.border.': 'import net.minecraft.world.level.border.',
    'import net.minecraft.world.dimension.': 'import net.minecraft.world.level.dimension.',
    'net.minecraft.world.World': 'net.minecraft.world.level.Level',
    'net.minecraft.world.BlockGetter': 'net.minecraft.world.level.BlockGetter',
    'net.minecraft.world.LevelAccessor': 'net.minecraft.world.level.LevelAccessor',

    # Registry package changes
    'net.minecraft.registry.Registry': 'net.minecraft.core.Registry',
    'net.minecraft.registry.Registries': 'net.minecraft.core.registries.Registries',
    'net.minecraft.registry.ResourceKey': 'net.minecraft.resources.ResourceKey',
    'net.minecraft.registry.': 'net.minecraft.core.registries.',

    # Block packages
    'net.minecraft.block.entity.': 'net.minecraft.world.level.block.entity.',
    'net.minecraft.block.': 'net.minecraft.world.level.block.',

    # Item package (in Fabric: net.minecraft.item, in Forge: net.minecraft.world.item)
    'import net.minecraft.item.': 'import net.minecraft.world.item.',

    # Enchantment package
    'import net.minecraft.enchantment.': 'import net.minecraft.world.item.enchantment.',

    # Screen/Container package
    'import net.minecraft.screen.': 'import net.minecraft.world.inventory.',

    # Entity package (in Fabric: net.minecraft.entity, in Forge: net.minecraft.world.entity)
    'import net.minecraft.entity.': 'import net.minecraft.world.entity.',
    'import net.minecraft.entity.player.': 'import net.minecraft.world.entity.player.',

    # Fluid package
    'import net.minecraft.fluid.': 'import net.minecraft.world.level.material.',

    # Sound package
    'import net.minecraft.sound.': 'import net.minecraft.sounds.',

    # Network packages
    'import net.minecraft.network.listener.': 'import net.minecraft.network.protocol.game.',
    'import net.minecraft.network.packet.Packet': 'import net.minecraft.network.protocol.Packet',
    'import net.minecraft.network.packet.s2c.play.': 'import net.minecraft.network.protocol.game.',

    # State packages
    'net.minecraft.state.property.Properties': 'net.minecraft.world.level.block.state.properties.BlockStateProperties',
    'net.minecraft.state.property.': 'net.minecraft.world.level.block.state.properties.',
    'net.minecraft.state.': 'net.minecraft.world.level.block.state.',

    # Text -> Chat
    'import net.minecraft.text.Text': 'import net.minecraft.network.chat.Component',
    'net.minecraft.text.': 'net.minecraft.network.chat.',

    # Server packages
    'import net.minecraft.server.world.': 'import net.minecraft.server.level.',
    'import net.minecraft.server.network.ServerPlayer': 'import net.minecraft.server.level.ServerPlayer',
    'net.minecraft.server.world.ServerWorld': 'net.minecraft.server.level.ServerLevel',

    # Remove Fabric API imports
    'import net.fabricmc.fabric.api.entity.FakePlayer;': '// Removed Fabric FakePlayer import',
}

# String replacements for common conversions
# Note: Be careful with class name replacements as they can affect file names and mixins!
STRING_REPLACEMENTS = {
    # Classes - but DON'T replace in @Mixin or class declarations
    'extends BlockWithEntity': 'extends BaseEntityBlock',
    'extends ServerPlayerEntity': 'extends ServerPlayer',
    'extends PlayerEntity': 'extends Player',
    'extends World': 'extends Level',
    'extends ServerWorld': 'extends ServerLevel',
    'BlockView': 'BlockGetter',
    'WorldAccess': 'LevelAccessor',
    'StateManager': 'StateDefinition',
    'BlockStateProperties': 'BlockStateProperties',  # Already correct
    'ItemPlacementContext': 'BlockPlaceContext',
    'OrderedTick': 'ScheduledTick',
    'MutableText': 'MutableComponent',
    'Waterloggable': 'SimpleWaterloggedBlock',
    'NbtCompound': 'CompoundTag',
    'Vec3d': 'Vec3',
    'PlayerManager': 'PlayerList',
    'RegistryWrapper': 'HolderLookup',
    'ClientPlayPacketListener': 'ClientGamePacketListener',
    'NbtHelper': 'NbtUtils',
    'NbtIntArray': 'IntArrayTag',
    'ActionResult': 'InteractionResult',
    'Identifier': 'ResourceLocation',
    'RegistryKey': 'ResourceKey',
    'RegistryKeys': 'Registries',
    ' Text.': ' Component.',  # Space before to avoid replacing in identifiers
    'ItemScatterer': 'Containers',
    'ExperienceOrbEntity': 'ExperienceOrb',
    'DimensionTypes': 'BuiltinDimensionTypes',
    'BlockEntityUpdateS2CPacket': 'ClientboundBlockEntityDataPacket',
    'DefaultedList': 'NonNullList',
    'Fluidloggable': 'SimpleWaterloggedBlock',  # Fluid variant
    'WorldBorder': 'WorldBorder',  # Same in both
    'FluidState': 'FluidState',  # Same in both
    'PacketIdentifiers': 'PacketResourceLocations',  # Interface rename
    'ServerPlayerEntity': 'ServerPlayer',
    'PlayerEntity': 'Player',
    'LivingEntity': 'LivingEntity',  # Same
    'DamageSource': 'DamageSource',  # Same location
    'Enchantment': 'Enchantment',  # Same

    # Methods and fields
    '.isClient': '.isClientSide',
    '.getDefaultState()': '.defaultBlockState()',
    '.markDirty()': '.setChanged()',
    '.updateListeners(': '.sendBlockUpdated(',
    '.with(': '.setValue(',
    '.get(': '.getValue(',
    '.registryKey()': '.key()',
    '.getRegistryKey()': '.dimension()',
    'setDefaultState': 'registerDefaultState',
    'createBlockEntity': 'newBlockEntity',
    'writeNbt': 'saveAdditional',
    'readNbt': 'load',
    'toInitialChunkDataNbt': 'getUpdateTag',
    'toUpdatePacket': 'getUpdatePacket',
    'onPlaced': 'setPlacedBy',
    'getPlacementState': 'getStateForPlacement',
    'getStateForNeighborUpdate': 'updateShape',
    'getRenderType': 'getRenderShape',
    'getOutlineShape': 'getShape',
    'onUse': 'use',
    'onSteppedOn': 'stepOn',
    'afterBreak': 'playerDestroy',
    'calcBlockBreakingDelta': 'getDestroyProgress',
    'appendProperties': 'createBlockStateDefinition',
    'putUuid': 'putUUID',
    'getUuid': 'getUUID',
    'hasCustomName': 'hasCustomHoverName',
    'getCustomName': 'getCustomName',
    'decrement': 'shrink',
    'getStackInHand': 'getItemInHand',
    'sendMessage': 'sendSystemMessage',

    # Constructors
    'new Identifier(': 'new ResourceLocation(',
    'Identifier.of(': 'new ResourceLocation(',

    # FakePlayer handling - use Forge's method
    ' instanceof FakePlayer': ' instanceof net.minecraftforge.common.util.FakePlayer',
}

def rename_files_after_conversion():
    """Rename files that have incorrect names due to class renames"""
    renames = [
        ('src/main/java/com/b1n_ry/yigd/packets/PacketIdentifiers.java',
         'src/main/java/com/b1n_ry/yigd/packets/PacketResourceLocations.java'),
    ]

    for old_path, new_path in renames:
        old_full = os.path.join(os.path.dirname(__file__), old_path)
        new_full = os.path.join(os.path.dirname(__file__), new_path)
        if os.path.exists(old_full) and not os.path.exists(new_full):
            os.rename(old_full, new_full)
            print(f'Renamed: {old_path} -> {new_path}')

def convert_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original_content = content

    # Apply package mappings
    for old_pkg, new_pkg in PACKAGE_MAPPINGS.items():
        content = content.replace(old_pkg, new_pkg)

    # Apply string replacements
    for old_str, new_str in STRING_REPLACEMENTS.items():
        content = content.replace(old_str, new_str)

    # Only write if changed
    if content != original_content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

def main():

    # Rename files if needed
    rename_files_after_conversion()
    src_dir = os.path.join(os.path.dirname(__file__), 'src', 'main', 'java')

    converted_count = 0
    total_count = 0

    for root, dirs, files in os.walk(src_dir):
        for file in files:
            if file.endswith('.java') and not file.endswith('.disabled'):
                filepath = os.path.join(root, file)
                total_count += 1
                if convert_file(filepath):
                    converted_count += 1
                    print(f'Converted: {filepath}')

    print(f'\n=== Conversion Complete ===')
    print(f'Total files: {total_count}')
    print(f'Modified files: {converted_count}')
    print(f'Unchanged files: {total_count - converted_count}')

if __name__ == '__main__':
    main()
