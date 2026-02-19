package com.minaret;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SpawnerAgitatorBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int AGITATED_RANGE = -1;
    private static final Set<SpawnerAgitatorBlockEntity> BOUND_AGITATORS =
        Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final String TAG_SPAWNER_COUNT = "SpawnerCount";
    private static final String TAG_ORIGINAL_RANGE = "OriginalRange_";
    private static final String TAG_ORIGINAL_MIN_DELAY = "OriginalMinDelay_";
    private static final String TAG_ORIGINAL_MAX_DELAY = "OriginalMaxDelay_";

    // Multiple spawners bound by the topmost agitator
    private final List<BaseSpawner> cachedSpawners = new ArrayList<>();
    private final List<Integer> originalRanges = new ArrayList<>();
    private final List<Integer> originalMinDelays = new ArrayList<>();
    private final List<Integer> originalMaxDelays = new ArrayList<>();
    private int cachedStackSize = 1;

    public SpawnerAgitatorBlockEntity(BlockPos pos, BlockState state) {
        super(MinaretMod.SPAWNER_AGITATOR_BE.get(), pos, state);
    }

    // ── Event-driven spawner binding ────────────────────────────────────

    void bindSpawner() {
        bindSpawner(null);
    }

    void bindSpawner(BlockPos exclude) {
        if (level == null || level.isClientSide()) return;
        BlockPos above = worldPosition.above();
        if (exclude != null && above.equals(exclude)) above = above.above();
        // Only the topmost agitator should bind
        if (
            level.getBlockState(above).getBlock() instanceof
                SpawnerAgitatorBlock
        ) {
            cachedSpawners.clear();
            return;
        }

        // Walk up collecting all contiguous spawners
        List<BaseSpawner> foundSpawners = new ArrayList<>();
        BlockPos check = above;
        while (true) {
            if (exclude != null && check.equals(exclude)) {
                check = check.above();
                continue;
            }
            if (!level.getBlockState(check).is(Blocks.SPAWNER)) break;
            if (
                !(level.getBlockEntity(check) instanceof
                        SpawnerBlockEntity spawnerBE)
            ) break;
            BaseSpawner spawner = spawnerBE.getSpawner();
            SpawnerAccessor.ensureResolved(spawner);
            if (!SpawnerAccessor.isAvailable()) return;
            foundSpawners.add(spawner);
            check = check.above();
        }

        if (foundSpawners.isEmpty()) return;

        cachedSpawners.clear();
        cachedSpawners.addAll(foundSpawners);

        // Save originals on first bind (list empty means first time)
        if (originalRanges.isEmpty()) {
            for (BaseSpawner spawner : cachedSpawners) {
                originalRanges.add(SpawnerAccessor.getRange(spawner));
                originalMinDelays.add(SpawnerAccessor.getMinDelay(spawner));
                originalMaxDelays.add(SpawnerAccessor.getMaxDelay(spawner));
            }
            persistOriginals();
        }

        // Force-load chunk so spawners tick when players are far away
        if (level instanceof ServerLevel serverLevel) {
            ChunkLoaderBlock.forceChunk(serverLevel, worldPosition, true);
        }
        // Track for shutdown cleanup
        BOUND_AGITATORS.add(this);
        // Apply agitation to all spawners
        for (int i = 0; i < cachedSpawners.size(); i++) {
            BaseSpawner spawner = cachedSpawners.get(i);
            SpawnerAccessor.setRange(spawner, AGITATED_RANGE);
            applyDelays(spawner, i);
        }
        LOGGER.info(
            "Agitator bind at {} — stack={}, spawners={}, originals={}",
            worldPosition,
            cachedStackSize,
            cachedSpawners.size(),
            originalRanges
        );
    }

    void unbindSpawner() {
        if (!cachedSpawners.isEmpty() && SpawnerAccessor.isAvailable()) {
            int count = Math.min(cachedSpawners.size(), originalRanges.size());
            for (int i = 0; i < count; i++) {
                BaseSpawner spawner = cachedSpawners.get(i);
                int range = originalRanges.get(i);
                int minDelay = originalMinDelays.get(i);
                int maxDelay = originalMaxDelays.get(i);
                if (range >= 0) SpawnerAccessor.setRange(spawner, range);
                if (minDelay >= 0) SpawnerAccessor.setMinDelay(
                    spawner,
                    minDelay
                );
                if (maxDelay >= 0) SpawnerAccessor.setMaxDelay(
                    spawner,
                    maxDelay
                );
            }
            LOGGER.info(
                "Agitator unbind at {} — restored {} spawners, ranges={}",
                worldPosition,
                count,
                originalRanges
            );
        }
        // Unforce chunk
        if (level instanceof ServerLevel serverLevel) {
            ChunkLoaderBlock.forceChunk(serverLevel, worldPosition, false);
        }
        BOUND_AGITATORS.remove(this);
        cachedSpawners.clear();
        originalRanges.clear();
        originalMinDelays.clear();
        originalMaxDelays.clear();
        if (level != null && !level.isClientSide()) {
            persistOriginals();
        }
    }

    private void applyDelays(BaseSpawner spawner, int index) {
        int n = Math.max(1, cachedStackSize);
        if (index < originalMinDelays.size()) {
            int minDelay = originalMinDelays.get(index);
            if (minDelay > 0) SpawnerAccessor.setMinDelay(
                spawner,
                Math.max(1, minDelay / n)
            );
        }
        if (index < originalMaxDelays.size()) {
            int maxDelay = originalMaxDelays.get(index);
            if (maxDelay > 0) SpawnerAccessor.setMaxDelay(
                spawner,
                Math.max(1, maxDelay / n)
            );
        }
    }

    boolean isBound() {
        return !cachedSpawners.isEmpty();
    }

    static void unbindAll() {
        for (var be : Set.copyOf(BOUND_AGITATORS)) {
            be.unbindSpawner();
        }
    }

    void onNeighborChanged() {
        if (level == null || level.isClientSide()) return;
        BlockPos above = worldPosition.above();
        if (
            level.getBlockState(above).getBlock() instanceof
                SpawnerAgitatorBlock
        ) {
            if (!cachedSpawners.isEmpty()) unbindSpawner();
            return;
        }
        boolean spawnerAbove = level.getBlockState(above).is(Blocks.SPAWNER);
        if (spawnerAbove && cachedSpawners.isEmpty()) {
            bindSpawner();
        } else if (!spawnerAbove && !cachedSpawners.isEmpty()) {
            unbindSpawner();
        }
    }

    // ── Stack size ──────────────────────────────────────────────────────

    void recalcStackSize() {
        recalcStackSize(null);
    }

    void recalcStackSize(BlockPos exclude) {
        if (level == null) return;
        cachedStackSize = ColumnHelper.countBelow(
            level,
            worldPosition,
            exclude,
            SpawnerAgitatorBlock.class,
            false
        );
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    @Override
    public void onLoad() {
        super.onLoad();
        CompoundTag data = getPersistentData();
        if (data.contains(TAG_SPAWNER_COUNT)) {
            int count = Compat.getTagInt(data, TAG_SPAWNER_COUNT);
            originalRanges.clear();
            originalMinDelays.clear();
            originalMaxDelays.clear();
            for (int i = 0; i < count; i++) {
                originalRanges.add(
                    Compat.getTagInt(data, TAG_ORIGINAL_RANGE + i)
                );
                originalMinDelays.add(
                    Compat.getTagInt(data, TAG_ORIGINAL_MIN_DELAY + i)
                );
                originalMaxDelays.add(
                    Compat.getTagInt(data, TAG_ORIGINAL_MAX_DELAY + i)
                );
            }
        }
        recalcStackSize();
        bindSpawner();
    }

    // ── NBT ─────────────────────────────────────────────────────────────

    private void persistOriginals() {
        CompoundTag data = getPersistentData();
        // Clear old entries first
        if (data.contains(TAG_SPAWNER_COUNT)) {
            int oldCount = Compat.getTagInt(data, TAG_SPAWNER_COUNT);
            for (int i = 0; i < oldCount; i++) {
                data.remove(TAG_ORIGINAL_RANGE + i);
                data.remove(TAG_ORIGINAL_MIN_DELAY + i);
                data.remove(TAG_ORIGINAL_MAX_DELAY + i);
            }
            data.remove(TAG_SPAWNER_COUNT);
        }
        if (!originalRanges.isEmpty()) {
            data.putInt(TAG_SPAWNER_COUNT, originalRanges.size());
            for (int i = 0; i < originalRanges.size(); i++) {
                data.putInt(TAG_ORIGINAL_RANGE + i, originalRanges.get(i));
                data.putInt(
                    TAG_ORIGINAL_MIN_DELAY + i,
                    originalMinDelays.get(i)
                );
                data.putInt(
                    TAG_ORIGINAL_MAX_DELAY + i,
                    originalMaxDelays.get(i)
                );
            }
        }
        setChanged();
    }
}
