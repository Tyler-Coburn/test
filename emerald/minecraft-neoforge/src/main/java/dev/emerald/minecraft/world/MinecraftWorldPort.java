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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import dev.emerald.core.world.Box;

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

    @Override
    public ItemStore containerAt(Pos pos) {
        return containerAt(level, Positions.toBlockPos(pos));
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
    public int countCropPlots(Pos center, int radius) {
        BlockPos c = Positions.toBlockPos(center);
        int plots = 0;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-radius, -3, -radius), c.offset(radius, 3, radius))) {
            if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof FarmBlock
                    && level.getBlockState(p.above()).getBlock() instanceof CropBlock) {
                plots++;
            }
        }
        return plots;
    }

    @Override
    public int countAnimals(Box region, String entityId) {
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        if (id == null) {
            return 0;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        AABB box = new AABB(region.min().x() - 1, region.min().y() - 1, region.min().z() - 1,
                region.max().x() + 2, region.max().y() + 2, region.max().z() + 2);
        return level.getEntities((Entity) null, box, e -> e.getType() == type && e.isAlive()).size();
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
        if (b == Blocks.AIR || from.count(block.itemId()) < 1 || !clearForPlacement(p)) {
            return false;
        }
        BlockState state = stateFor(b, block);
        if (!state.canSurvive(level, p)) {
            return false;
        }
        if (from.extract(block.itemId(), 1) != 1) {
            return false;
        }
        return level.setBlock(p, state, Block.UPDATE_ALL);
    }

    @Override
    public boolean placeMaterialized(Pos pos, BlueprintBlock block) {
        BlockPos p = Positions.toBlockPos(pos);
        ResourceLocation id = ResourceLocation.tryParse(block.blockId());
        if (id == null || !level.isLoaded(p) || !level.getBlockState(p).canBeReplaced()) {
            return false;
        }
        Block b = BuiltInRegistries.BLOCK.get(id);
        if (b == Blocks.AIR) {
            return false;
        }
        BlockState state = stateFor(b, block);
        return state.canSurvive(level, p) && level.setBlock(p, state, Block.UPDATE_ALL);
    }

    /**
     * Free, or a soft natural block (dirt, grass, sand, gravel, snow) the builder digs out first; its
     * drop falls in the world as normal. Never digs player blocks, containers or stone.
     */
    private boolean clearForPlacement(BlockPos p) {
        BlockState existing = level.getBlockState(p);
        if (existing.canBeReplaced()) {
            return true;
        }
        boolean soft = existing.is(BlockTags.DIRT) || existing.is(BlockTags.SAND) || existing.is(Blocks.GRAVEL)
                || existing.is(Blocks.SNOW_BLOCK) || existing.is(Blocks.CLAY);
        return soft && !existing.hasBlockEntity() && level.destroyBlock(p, true);
    }

    private static BlockState stateFor(Block b, BlueprintBlock block) {
        BlockState state = b.defaultBlockState();
        for (Map.Entry<String, String> e : block.properties().entrySet()) {
            Property<?> prop = b.getStateDefinition().getProperty(e.getKey());
            if (prop != null) {
                state = withValue(state, prop, e.getValue());
            }
        }
        return state;
    }

    @Override
    public Optional<Pos> findBuildSite(Pos center, int sizeX, int sizeZ, int minDist, int maxDist, List<Box> avoid) {
        for (int r = minDist; r <= maxDist; r += 3) {
            for (int d = -r; d <= r; d += 4) {
                int[][] candidates = {{d, -r}, {d, r}, {-r, d}, {r, d}};
                for (int[] c : candidates) {
                    Optional<Pos> site = checkSite(center.x() + c[0], center.z() + c[1], sizeX, sizeZ, avoid);
                    if (site.isPresent()) {
                        return site;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Flat (one surface height), solid, dry, clear for four blocks, loaded, not overlapping {@code avoid}. */
    private Optional<Pos> checkSite(int x0, int z0, int sx, int sz, List<Box> avoid) {
        int y = -1;
        for (int x = x0; x < x0 + sx; x++) {
            for (int z = z0; z < z0 + sz; z++) {
                BlockPos column = new BlockPos(x, 0, z);
                if (!level.isLoaded(column)) {
                    return Optional.empty();
                }
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y == -1) {
                    y = h;
                } else if (h != y) {
                    return Optional.empty();
                }
                BlockState ground = level.getBlockState(new BlockPos(x, h - 1, z));
                if (!ground.isSolid() || !ground.getFluidState().isEmpty() || ground.getBlock() instanceof FarmBlock) {
                    return Optional.empty();
                }
                for (int dy = 0; dy < 4; dy++) {
                    if (!level.getBlockState(new BlockPos(x, h + dy, z)).canBeReplaced()) {
                        return Optional.empty();
                    }
                }
            }
        }
        Pos origin = new Pos(x0, y, z0);
        Box footprint = Box.of(origin, origin.offset(sx - 1, 3, sz - 1));
        for (Box b : avoid) {
            if (footprint.min().x() <= b.max().x() && footprint.max().x() >= b.min().x()
                    && footprint.min().y() <= b.max().y() && footprint.max().y() >= b.min().y()
                    && footprint.min().z() <= b.max().z() && footprint.max().z() >= b.min().z()) {
                return Optional.empty();
            }
        }
        return Optional.of(origin);
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> prop, String value) {
        return prop.getValue(value).map(v -> state.setValue(prop, v)).orElse(state);
    }

    @Override
    public Optional<Pos> bodyPosition(UUID citizenId) {
        return EmeraldServer.body(citizenId).map(e -> Positions.toPos(e.blockPosition()));
    }
}
