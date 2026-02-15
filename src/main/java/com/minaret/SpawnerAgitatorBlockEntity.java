package com.minaret;

import java.util.Collections;
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
    private static final String TAG_ORIGINAL_RANGE = "OriginalPlayerRange";
    private static final String TAG_ORIGINAL_MIN_DELAY = "OriginalMinDelay";
    private static final String TAG_ORIGINAL_MAX_DELAY = "OriginalMaxDelay";

    // Original spawner values (stored by the top agitator only)
    private int originalRange = -1;
    private int originalMinDelay = -1;
    private int originalMaxDelay = -1;
    private BaseSpawner cachedSpawner;
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
            cachedSpawner = null;
            return;
        }
        if (!level.getBlockState(above).is(Blocks.SPAWNER)) return;
        if (
            !(level.getBlockEntity(above) instanceof
                    SpawnerBlockEntity spawnerBE)
        ) return;

        BaseSpawner spawner = spawnerBE.getSpawner();
        SpawnerAccessor.ensureResolved(spawner);
        if (!SpawnerAccessor.isAvailable()) return;

        cachedSpawner = spawner;

        // Save originals on first bind
        if (originalRange < 0) {
            originalRange = SpawnerAccessor.getRange(spawner);
            originalMinDelay = SpawnerAccessor.getMinDelay(spawner);
            originalMaxDelay = SpawnerAccessor.getMaxDelay(spawner);
            persistOriginals();
        }
        // Force-load chunk so spawner ticks when players are far away
        if (level instanceof ServerLevel serverLevel) {
            ChunkLoaderBlock.forceChunk(serverLevel, worldPosition, true);
        }
        // Track for shutdown cleanup
        BOUND_AGITATORS.add(this);
        // Apply agitation
        SpawnerAccessor.setRange(spawner, AGITATED_RANGE);
        applyDelays(spawner);
        LOGGER.info(
            "Agitator bind at {} — stack={}, originals=[range={}, min={}, max={}]",
            worldPosition,
            cachedStackSize,
            originalRange,
            originalMinDelay,
            originalMaxDelay
        );
    }

    void unbindSpawner() {
        if (
            cachedSpawner != null &&
            originalRange >= 0 &&
            SpawnerAccessor.isAvailable()
        ) {
            SpawnerAccessor.setRange(cachedSpawner, originalRange);
            if (originalMinDelay >= 0) SpawnerAccessor.setMinDelay(
                cachedSpawner,
                originalMinDelay
            );
            if (originalMaxDelay >= 0) SpawnerAccessor.setMaxDelay(
                cachedSpawner,
                originalMaxDelay
            );
            LOGGER.info(
                "Agitator unbind at {} — restored range={}, minDelay={}, maxDelay={}",
                worldPosition,
                originalRange,
                originalMinDelay,
                originalMaxDelay
            );
        }
        // Unforce chunk
        if (level instanceof ServerLevel serverLevel) {
            ChunkLoaderBlock.forceChunk(serverLevel, worldPosition, false);
        }
        BOUND_AGITATORS.remove(this);
        cachedSpawner = null;
        originalRange = -1;
        originalMinDelay = -1;
        originalMaxDelay = -1;
        if (level != null && !level.isClientSide()) {
            persistOriginals();
        }
    }

    private void applyDelays(BaseSpawner spawner) {
        int n = Math.max(1, cachedStackSize);
        if (originalMinDelay > 0) SpawnerAccessor.setMinDelay(
            spawner,
            Math.max(1, originalMinDelay / n)
        );
        if (originalMaxDelay > 0) SpawnerAccessor.setMaxDelay(
            spawner,
            Math.max(1, originalMaxDelay / n)
        );
    }

    boolean isBound() {
        return cachedSpawner != null;
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
            if (cachedSpawner != null) unbindSpawner();
            return;
        }
        boolean spawnerAbove = level.getBlockState(above).is(Blocks.SPAWNER);
        if (spawnerAbove && cachedSpawner == null) {
            bindSpawner();
        } else if (!spawnerAbove && cachedSpawner != null) {
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
        if (data.contains(TAG_ORIGINAL_RANGE)) originalRange = Compat.getTagInt(
            data,
            TAG_ORIGINAL_RANGE
        );
        if (data.contains(TAG_ORIGINAL_MIN_DELAY)) originalMinDelay =
            Compat.getTagInt(data, TAG_ORIGINAL_MIN_DELAY);
        if (data.contains(TAG_ORIGINAL_MAX_DELAY)) originalMaxDelay =
            Compat.getTagInt(data, TAG_ORIGINAL_MAX_DELAY);
        recalcStackSize();
        bindSpawner();
    }

    // ── NBT ─────────────────────────────────────────────────────────────

    private void persistOriginals() {
        CompoundTag data = getPersistentData();
        if (originalRange >= 0) {
            data.putInt(TAG_ORIGINAL_RANGE, originalRange);
            data.putInt(TAG_ORIGINAL_MIN_DELAY, originalMinDelay);
            data.putInt(TAG_ORIGINAL_MAX_DELAY, originalMaxDelay);
        } else {
            data.remove(TAG_ORIGINAL_RANGE);
            data.remove(TAG_ORIGINAL_MIN_DELAY);
            data.remove(TAG_ORIGINAL_MAX_DELAY);
        }
        setChanged();
    }
}
