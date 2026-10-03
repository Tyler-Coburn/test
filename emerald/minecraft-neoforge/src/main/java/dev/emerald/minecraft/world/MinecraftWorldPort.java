package dev.emerald.minecraft.world;

import dev.emerald.core.construction.BlueprintBlock;
import dev.emerald.core.item.ItemStore;
import dev.emerald.core.job.WorldPort;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.Pos;
import dev.emerald.minecraft.server.EmeraldServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Server-authoritative implementation of the job layer's world actions for one village. */
public final class MinecraftWorldPort implements WorldPort {
    private final ServerLevel level;
    private final VillageState village;

    public MinecraftWorldPort(ServerLevel level, VillageState village) {
        this.level = level;
        this.village = village;
    }

    @Override
    public long gameTime() {
        return level.getGameTime();
    }

    @Override
    public String dimension() {
        return level.dimension().location().toString();
    }

    @Override
    public ItemStore warehouse() {
        return village.warehouse() == null ? null : containerAt(level, Positions.toBlockPos(village.warehouse()));
    }

    /** A single block's container (one chest half, barrel, hopper), or null if absent or unloaded. */
    public static ContainerItemStore containerAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof Container c ? new ContainerItemStore(c) : null;
    }

    @Override
    public Optional<Pos> findMatureCrop(Pos center, int radius) {
        BlockPos c = Positions.toBlockPos(center);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-radius, -3, -radius), c.offset(radius, 3, radius))) {
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.WHEAT) && s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s)) {
                double d = p.distSqr(c);
                if (d < bestDist) {
                    bestDist = d;
                    best = p.immutable();
                }
            }
        }
        return Optional.ofNullable(best).map(Positions::toPos);
    }

    @Override
    public Map<String, Integer> harvestCrop(Pos pos, ItemStore into) {
        BlockPos p = Positions.toBlockPos(pos);
        BlockState s = level.getBlockState(p);
        if (!(s.is(Blocks.WHEAT) && s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s))) {
            return Map.of();
        }
        List<ItemStack> drops = Block.getDrops(s, level, p, null);
        level.destroyBlock(p, false);
        Map<String, Integer> out = new TreeMap<>();
        for (ItemStack stack : drops) {
            String id = ContainerItemStore.idOf(stack);
            int kept = into.insert(id, stack.getCount());
            out.merge(id, stack.getCount(), Integer::sum);
            if (kept < stack.getCount()) {
                Block.popResource(level, p, stack.copyWithCount(stack.getCount() - kept));
            }
        }
        return out;
    }

    @Override
    public boolean replant(Pos pos, ItemStore from) {
        BlockPos p = Positions.toBlockPos(pos);
        if (!level.getBlockState(p).isAir() || !(level.getBlockState(p.below()).getBlock() instanceof FarmBlock)) {
            return false;
        }
        if (from.extract("minecraft:wheat_seeds", 1) != 1) {
            return false;
        }
        return level.setBlock(p, Blocks.WHEAT.defaultBlockState(), Block.UPDATE_ALL);
    }

    @Override
    public boolean matches(Pos pos, BlueprintBlock block) {
        BlockState s = level.getBlockState(Positions.toBlockPos(pos));
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString().equals(block.blockId());
    }

    @Override
    public boolean isLoaded(Pos pos) {
        return level.isLoaded(Positions.toBlockPos(pos));
    }

    @Override
    public boolean place(Pos pos, BlueprintBlock block, ItemStore from) {
        BlockPos p = Positions.toBlockPos(pos);
        ResourceLocation id = ResourceLocation.tryParse(block.blockId());
        if (id == null) {
            return false;
        }
        Block b = BuiltInRegistries.BLOCK.get(id);
        if (b == Blocks.AIR || !level.getBlockState(p).canBeReplaced()) {
            return false;
        }
        BlockState state = b.defaultBlockState();
        for (Map.Entry<String, String> e : block.properties().entrySet()) {
            Property<?> prop = b.getStateDefinition().getProperty(e.getKey());
            if (prop != null) {
                state = withValue(state, prop, e.getValue());
            }
        }
        if (!state.canSurvive(level, p)) {
            return false;
        }
        if (from.extract(block.itemId(), 1) != 1) {
            return false;
        }
        return level.setBlock(p, state, Block.UPDATE_ALL);
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> prop, String value) {
        return prop.getValue(value).map(v -> state.setValue(prop, v)).orElse(state);
    }

    @Override
    public Optional<Pos> bodyPosition(UUID citizenId) {
        return EmeraldServer.body(citizenId).map(e -> Positions.toPos(e.blockPosition()));
    }
}
