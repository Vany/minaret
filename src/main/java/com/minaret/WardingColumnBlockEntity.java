package com.minaret;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Base class for all warding column block entities.
 *
 * The BOTTOM block of the column is the actor:
 *   - Runs the server tick (repel mobs if any RepellingPostBlock in column)
 *   - Owns totalRadius, hasRepel, hasInhibit for the column
 *
 * Each block type contributes to the column radius:
 *   - WardingPostBlock:         +6 (radius only)
 *   - RepellingPostBlock:       +4 (radius + mob push)
 *   - TeleporterInhibitorBlock: +4 (radius + teleport block)
 */
public abstract class WardingColumnBlockEntity extends BlockEntity {

    static final double VERTICAL_RANGE  = 2.5;
    static final double PUSH_STRENGTH   = 0.5;
    static final double PUSH_UPWARD     = 0.1;
    static final double CENTER_EPSILON  = 0.01;
    private static final int TICK_INTERVAL = 4;

    /** Contribution per WardingPostBlock in column. */
    static final double WARDING_RADIUS   = 6.0;
    /** Contribution per RepellingPostBlock in column. */
    static final double REPELLING_RADIUS = 4.0;
    /** Contribution per TeleporterInhibitorBlock in column. */
    static final double INHIBITOR_RADIUS = 4.0;

    // ── Column state (valid only when isBottomOfColumn) ──────────────────
    double totalRadius      = 6.0;  // default: single warding post = 6
    boolean hasRepel        = false;
    boolean hasInhibit      = false;
    boolean isBottomOfColumn = true;

    private int tickCounter;

    protected WardingColumnBlockEntity(
        BlockEntityType<?> type, BlockPos pos, BlockState state
    ) {
        super(type, pos, state);
    }

    // ── Inhibitor registry (for O(n) teleport checks) ────────────────────

    /**
     * Bottom-of-column positions with hasInhibit=true, keyed by dimension.
     * Updated in recalcColumn() and setRemoved(). Read by WardingPostTeleportHandler.
     */
    static final Map<ResourceKey<Level>, Set<BlockPos>> inhibitorRegistry =
        new ConcurrentHashMap<>();

    private void updateRegistry() {
        if (level == null || level.isClientSide()) return;
        ResourceKey<Level> key = ((ServerLevel) level).dimension();
        Set<BlockPos> set = inhibitorRegistry.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        if (isBottomOfColumn && hasInhibit) {
            set.add(worldPosition.immutable());
        } else {
            set.remove(worldPosition);
        }
    }

    /** Called from MinaretMod.onServerStopping() to clear all registry entries. */
    public static void clearRegistry() {
        inhibitorRegistry.clear();
    }

    // ── Tick ─────────────────────────────────────────────────────────────

    public static void serverTick(
        Level level,
        BlockPos pos,
        BlockState state,
        WardingColumnBlockEntity be
    ) {
        if (!be.isBottomOfColumn) return;
        if (!be.hasRepel) return;
        if (++be.tickCounter < TICK_INTERVAL) return;
        be.tickCounter = 0;

        double radius = be.totalRadius + 0.5;
        Vec3 center = Vec3.atCenterOf(pos);
        AABB area = new AABB(
            center.x - radius, center.y - VERTICAL_RANGE, center.z - radius,
            center.x + radius, center.y + VERTICAL_RANGE, center.z + radius
        );

        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, e -> e instanceof Enemy)) {
            Vec3 dir = mob.position().subtract(center);
            double dist = dir.horizontalDistance();
            if (dist < CENTER_EPSILON) { dir = new Vec3(1, 0, 0); dist = 1; }
            double scale = PUSH_STRENGTH / dist;
            mob.push(dir.x * scale, PUSH_UPWARD, dir.z * scale);
            mob.hurtMarked = true;
        }
    }

    // ── Column recalc ─────────────────────────────────────────────────────

    void recalcColumn() { recalcColumn(null); }

    void recalcColumn(BlockPos exclude) {
        if (level == null) return;
        isBottomOfColumn = ColumnHelper.isBottomOfColumn(
            level, worldPosition, exclude, WardingColumnBlock.class
        );
        if (!isBottomOfColumn) {
            updateRegistry();
            return;
        }

        int wardingCount = ColumnHelper.countInColumn(
            level, worldPosition, exclude, WardingColumnBlock.class, WardingPostBlock.class
        );
        int repelCount = ColumnHelper.countInColumn(
            level, worldPosition, exclude, WardingColumnBlock.class, RepellingPostBlock.class
        );
        int inhibitCount = ColumnHelper.countInColumn(
            level, worldPosition, exclude, WardingColumnBlock.class, TeleporterInhibitorBlock.class
        );

        totalRadius = WARDING_RADIUS * wardingCount
                    + REPELLING_RADIUS * repelCount
                    + INHIBITOR_RADIUS * inhibitCount;
        hasRepel   = repelCount > 0;
        hasInhibit = inhibitCount > 0;
        updateRegistry();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public void onLoad() {
        super.onLoad();
        recalcColumn();
    }

    @Override
    public void setRemoved() {
        // Safe: only removes from a static Set, no world access.
        if (level != null && !level.isClientSide()) {
            ResourceKey<Level> key = ((ServerLevel) level).dimension();
            Set<BlockPos> set = inhibitorRegistry.get(key);
            if (set != null) set.remove(worldPosition);
        }
        super.setRemoved();
    }
}
