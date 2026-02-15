package com.minaret;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WardingPostBlockEntity extends BlockEntity {

    private static final int TICK_INTERVAL = 4;
    private static final double RADIUS_PER_POST = 4.0;
    private static final double VERTICAL_RANGE = 2.5;
    private static final double PUSH_STRENGTH = 0.5;
    private static final double PUSH_UPWARD = 0.1;
    private static final double CENTER_EPSILON = 0.01;

    private int tickCounter;
    int cachedColumnHeight = 1;
    boolean isTopOfColumn = true;

    public WardingPostBlockEntity(BlockPos pos, BlockState state) {
        super(MinaretMod.WARDING_POST_BE.get(), pos, state);
    }

    public static void serverTick(
        Level level,
        BlockPos pos,
        BlockState state,
        WardingPostBlockEntity be
    ) {
        if (!be.isTopOfColumn) return;
        if (++be.tickCounter < TICK_INTERVAL) return;
        be.tickCounter = 0;

        double radius = RADIUS_PER_POST * be.cachedColumnHeight + 0.5;
        Vec3 center = Vec3.atCenterOf(pos);
        AABB area = new AABB(
            center.x - radius,
            center.y - VERTICAL_RANGE,
            center.z - radius,
            center.x + radius,
            center.y + VERTICAL_RANGE,
            center.z + radius
        );

        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, e ->
            e instanceof Enemy
        )) {
            Vec3 dir = mob.position().subtract(center);
            double dist = dir.horizontalDistance();
            if (dist < CENTER_EPSILON) {
                dir = new Vec3(1, 0, 0);
                dist = 1;
            }
            double scale = PUSH_STRENGTH / dist;
            mob.push(dir.x * scale, PUSH_UPWARD, dir.z * scale);
            mob.hurtMarked = true;
        }
    }

    // ── Column helpers ──────────────────────────────────────────────────

    void recalcColumn() {
        recalcColumn(null);
    }

    void recalcColumn(BlockPos exclude) {
        if (level == null) return;

        // Check if there's a warding post above (not this one being top)
        BlockPos above = worldPosition.above();
        if (exclude != null && above.equals(exclude)) above = above.above();
        isTopOfColumn = !(level.getBlockState(above).getBlock() instanceof
                WardingPostBlock);

        // Count posts below (including self)
        int count = 1;
        BlockPos check = worldPosition.below();
        while (
            level.getBlockState(check).getBlock() instanceof WardingPostBlock
        ) {
            if (exclude != null && check.equals(exclude)) {
                check = check.below();
                continue;
            }
            count++;
            check = check.below();
        }
        cachedColumnHeight = count;
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    @Override
    public void onLoad() {
        super.onLoad();
        recalcColumn();
    }
}
