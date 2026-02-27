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

    /** Original spawner values saved on first bind, restored on unbind. */
    private record SpawnerState(int range, int minDelay, int maxDelay) {}

    private final List<BaseSpawner> cachedSpawners = new ArrayList<>();
    private final List<SpawnerState> originalStates = new ArrayList<>();
    private int cachedStackSize = 1;

    public SpawnerAgitatorBlockEntity(BlockPos pos, BlockState state) {
        super(MinaretRegistries.SPAWNER_AGITATOR_BE.get(), pos, state);
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
        if (level.getBlockState(above).getBlock() instanceof SpawnerAgitatorBlock) {
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
            if (!(level.getBlockEntity(check) instanceof SpawnerBlockEntity spawnerBE)) break;
            BaseSpawner spawner = spawnerBE.getSpawner();
            SpawnerAccessor.ensureResolved(spawner);
            if (!SpawnerAccessor.isAvailable()) return;
            foundSpawners.add(spawner);
            check = check.above();
        }

        if (foundSpawners.isEmpty()) return;

        cachedSpawners.clear();
        cachedSpawners.addAll(foundSpawners);

        // Save originals on first bind (empty means first time)
        if (originalStates.isEmpty()) {
            for (BaseSpawner spawner : cachedSpawners) {
                originalStates.add(new SpawnerState(
                    SpawnerAccessor.getRange(spawner),
                    SpawnerAccessor.getMinDelay(spawner),
                    SpawnerAccessor.getMaxDelay(spawner)
                ));
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
            worldPosition, cachedStackSize, cachedSpawners.size(), originalStates
        );
    }

    void unbindSpawner() {
        if (!cachedSpawners.isEmpty() && SpawnerAccessor.isAvailable()) {
            int count = Math.min(cachedSpawners.size(), originalStates.size());
            for (int i = 0; i < count; i++) {
                BaseSpawner spawner = cachedSpawners.get(i);
                SpawnerState state = originalStates.get(i);
                if (state.range >= 0) SpawnerAccessor.setRange(spawner, state.range);
                if (state.minDelay >= 0) SpawnerAccessor.setMinDelay(spawner, state.minDelay);
                if (state.maxDelay >= 0) SpawnerAccessor.setMaxDelay(spawner, state.maxDelay);
            }
            LOGGER.info(
                "Agitator unbind at {} — restored {} spawners, originals={}",
                worldPosition, count, originalStates
            );
        }
        // Unforce chunk
        if (level instanceof ServerLevel serverLevel) {
            ChunkLoaderBlock.forceChunk(serverLevel, worldPosition, false);
        }
        BOUND_AGITATORS.remove(this);
        cachedSpawners.clear();
        originalStates.clear();
        if (level != null && !level.isClientSide()) {
            persistOriginals();
        }
    }

    private void applyDelays(BaseSpawner spawner, int index) {
        int n = Math.max(1, cachedStackSize);
        if (index < originalStates.size()) {
            SpawnerState state = originalStates.get(index);
            if (state.minDelay > 0) SpawnerAccessor.setMinDelay(spawner, Math.max(1, state.minDelay / n));
            if (state.maxDelay > 0) SpawnerAccessor.setMaxDelay(spawner, Math.max(1, state.maxDelay / n));
        }
    }

    /**
     * Periodic integrity recheck (called on random tick). Counts contiguous spawners
     * above and recalculates stack size. Only unbinds+rebinds if the spawner count
     * changed, so unnecessary NBT writes are avoided.
     */
    void recheckSpawners() {
        if (level == null || level.isClientSide()) return;
        // Only the topmost agitator in the column should act
        if (level.getBlockState(worldPosition.above()).getBlock() instanceof SpawnerAgitatorBlock) return;

        recalcStackSize();

        int currentCount = 0;
        BlockPos check = worldPosition.above();
        while (level.getBlockState(check).is(Blocks.SPAWNER)) {
            currentCount++;
            check = check.above();
        }

        if (currentCount != cachedSpawners.size()) {
            if (!cachedSpawners.isEmpty()) unbindSpawner();
            if (currentCount > 0) bindSpawner();
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
        if (level.getBlockState(above).getBlock() instanceof SpawnerAgitatorBlock) {
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
            level, worldPosition, exclude, SpawnerAgitatorBlock.class, false
        );
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    @Override
    public void onLoad() {
        super.onLoad();
        CompoundTag data = getPersistentData();
        if (data.contains(TAG_SPAWNER_COUNT)) {
            int count = Compat.getTagInt(data, TAG_SPAWNER_COUNT);
            originalStates.clear();
            for (int i = 0; i < count; i++) {
                originalStates.add(new SpawnerState(
                    Compat.getTagInt(data, TAG_ORIGINAL_RANGE + i),
                    Compat.getTagInt(data, TAG_ORIGINAL_MIN_DELAY + i),
                    Compat.getTagInt(data, TAG_ORIGINAL_MAX_DELAY + i)
                ));
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
        if (!originalStates.isEmpty()) {
            data.putInt(TAG_SPAWNER_COUNT, originalStates.size());
            for (int i = 0; i < originalStates.size(); i++) {
                SpawnerState state = originalStates.get(i);
                data.putInt(TAG_ORIGINAL_RANGE + i, state.range);
                data.putInt(TAG_ORIGINAL_MIN_DELAY + i, state.minDelay);
                data.putInt(TAG_ORIGINAL_MAX_DELAY + i, state.maxDelay);
            }
        }
        setChanged();
    }
}
