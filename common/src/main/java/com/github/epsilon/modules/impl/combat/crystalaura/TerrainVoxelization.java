package com.github.epsilon.modules.impl.combat.crystalaura;

import com.github.epsilon.graphics.vulkan.buffer.Std430Writer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Arrays;

/**
 * <h3>数据布局（std430 SSBO）</h3>
 * <pre>
 *   Header (16 bytes):
 *     int gridOriginX
 *     int gridOriginY
 *     int gridOriginZ
 *     int gridSize
 *
 *   Body:
 *     uint voxelBits[BITMAP_UINT_COUNT]
 *     — 每 uint 存 32 个体素
 *     — flatIndex(x,y,z) = (z * GRID_SIZE + y) * GRID_SIZE + x
 * </pre>
 *
 * <h3>写入字节数</h3>
 * Header: 4 × int = 16 bytes（Std430Writer 的 putInt 按 4 字节对齐，连续写无间隙）。
 * Body:   BITMAP_UINT_COUNT × 4 bytes = 4096 bytes。
 * 合计:   {@link #BUFFER_SIZE} = 4112 bytes。
 */
public class TerrainVoxelization {

    private static final Minecraft mc = Minecraft.getInstance();

    /** 玩家中心两侧各保留 16 格，总体素边长为 32。 */
    public static final int HALF_EXTENT = 16;

    public static final int GRID_SIZE = HALF_EXTENT * 2;

    public static final int TOTAL_VOXELS = GRID_SIZE * GRID_SIZE * GRID_SIZE;

    public static final int BITMAP_UINT_COUNT = (TOTAL_VOXELS + 31) / 32;

    public static final int HEADER_BYTES = 4 * Integer.BYTES;

    public static final int BODY_BYTES = BITMAP_UINT_COUNT * Integer.BYTES;

    public static final int BUFFER_SIZE = HEADER_BYTES + BODY_BYTES;

    /** 每个 Z 切片占用的 uint 数：GRID_SIZE² / 32 = 32 */
    private static final int SLICE_UINTS = GRID_SIZE * GRID_SIZE / 32;

    /** 每个 Y 行（固定 y, z）占用的 uint 数。 */
    private static final int ROW_UINTS = (GRID_SIZE + 31) / 32;

    private int originX, originY, originZ;
    private final int[] voxelBits = new int[BITMAP_UINT_COUNT];
    private boolean needsFullRebuild = true;

    /**
     * 每帧调用：以玩家位置为中心更新体素网格。
     * <p>
     * 当网格原点发生偏移时，采用滚动窗口增量更新策略：先对 {@code voxelBits}
     * 做各轴位移以保留不变体素，再仅对新进入视野的切片/行/列做世界查询重建。
     * 对于位移 ≥ GRID_SIZE 的大跳跃，回退到全量重建。
     * <p>
     * 典型情况（三轴各偏移 1 块）：约 3,000 次 getBlockState 调用，
     * 相较全量重建的 32,768 次节省约 90%。
     *
     * @return true 表示数据已变更
     */
    public boolean update() {
        if (mc.player == null || mc.level == null) return false;

        int cx = (int) Math.floor(mc.player.getX()) - HALF_EXTENT;
        int cy = (int) Math.floor(mc.player.getY()) - HALF_EXTENT;
        int cz = (int) Math.floor(mc.player.getZ()) - HALF_EXTENT;

        if (needsFullRebuild) {
            originX = cx;
            originY = cy;
            originZ = cz;
            rebuildFull();
            needsFullRebuild = false;
            return true;
        }

        int dx = cx - originX;
        int dy = cy - originY;
        int dz = cz - originZ;

        if (dx == 0 && dy == 0 && dz == 0) return false;

        originX = cx;
        originY = cy;
        originZ = cz;

        if (Math.abs(dx) >= GRID_SIZE || Math.abs(dy) >= GRID_SIZE || Math.abs(dz) >= GRID_SIZE) {
            rebuildFull();
        } else {
            rebuildIncremental(dx, dy, dz);
        }
        return true;
    }

    /**
     * 完整重建体素网格（GRID_SIZE³ 次 getBlockState 调用）。
     */
    private void rebuildFull() {
        Arrays.fill(voxelBits, 0);

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < GRID_SIZE; lz++) {
            int wz = originZ + lz;
            for (int ly = 0; ly < GRID_SIZE; ly++) {
                int wy = originY + ly;
                for (int lx = 0; lx < GRID_SIZE; lx++) {
                    int wx = originX + lx;
                    mutable.set(wx, wy, wz);

                    BlockState state = mc.level.getBlockState(mutable);
                    if (!state.isAir()
                            && !state.getCollisionShape(mc.level, mutable, CollisionContext.empty()).isEmpty()) {
                        int idx = flatIndex(lx, ly, lz);
                        voxelBits[idx >> 5] |= (1 << (idx & 31));
                    }
                }
            }
        }
    }

    /**
     * 滚动窗口增量重建：先对 voxelBits 做各轴位移（保留不变体素），
     * 再仅查询新进入视野的切片/行/列。
     * 调用时 originX/Y/Z 已更新至新位置。
     *
     * @param dx 原点在 X 轴的位移（正 = 向 +X 移动）
     * @param dy 原点在 Y 轴的位移
     * @param dz 原点在 Z 轴的位移
     */
    private void rebuildIncremental(int dx, int dy, int dz) {
        shiftZ(dz);
        shiftY(dy);
        shiftX(dx);

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        // 新进入的 Z 切片范围（已被 shiftZ 清零）
        int zSurviveStart = dz > 0 ? 0 : -dz;
        int zSurviveEnd   = dz > 0 ? GRID_SIZE - dz : GRID_SIZE;

        if (dz > 0) {
            for (int lz = GRID_SIZE - dz; lz < GRID_SIZE; lz++)
                rebuildZSlice(lz, mutable);
        } else if (dz < 0) {
            for (int lz = 0; lz < -dz; lz++)
                rebuildZSlice(lz, mutable);
        }

        // 新进入的 Y 行（仅在存活 Z 范围内，已被 shiftY 清零）
        int ySurviveStart = dy > 0 ? 0 : -dy;
        int ySurviveEnd   = dy > 0 ? GRID_SIZE - dy : GRID_SIZE;

        if (dy > 0) {
            for (int lz = zSurviveStart; lz < zSurviveEnd; lz++)
                for (int ly = GRID_SIZE - dy; ly < GRID_SIZE; ly++)
                    rebuildYRow(lz, ly, mutable);
        } else if (dy < 0) {
            for (int lz = zSurviveStart; lz < zSurviveEnd; lz++)
                for (int ly = 0; ly < -dy; ly++)
                    rebuildYRow(lz, ly, mutable);
        }

        // 新进入的 X 列（仅在存活 Z/Y 范围内，已被 shiftX 清零）
        if (dx > 0) {
            for (int lz = zSurviveStart; lz < zSurviveEnd; lz++)
                for (int ly = ySurviveStart; ly < ySurviveEnd; ly++)
                    for (int lx = GRID_SIZE - dx; lx < GRID_SIZE; lx++)
                        rebuildVoxel(lx, ly, lz, mutable);
        } else if (dx < 0) {
            for (int lz = zSurviveStart; lz < zSurviveEnd; lz++)
                for (int ly = ySurviveStart; ly < ySurviveEnd; ly++)
                    for (int lx = 0; lx < -dx; lx++)
                        rebuildVoxel(lx, ly, lz, mutable);
        }
    }

    /**
     * 将 voxelBits 在 Z 轴方向平移 dz 个切片。
     * dz > 0：原点向 +Z 移动，保留高索引切片（整体向低索引平移），低端清零。
     * dz < 0：原点向 -Z 移动，保留低索引切片（整体向高索引平移），高端清零。
     */
    private void shiftZ(int dz) {
        if (dz == 0) return;
        if (dz > 0) {
            System.arraycopy(voxelBits, dz * SLICE_UINTS, voxelBits, 0, (GRID_SIZE - dz) * SLICE_UINTS);
            Arrays.fill(voxelBits, (GRID_SIZE - dz) * SLICE_UINTS, BITMAP_UINT_COUNT, 0);
        } else {
            int adz = -dz;
            System.arraycopy(voxelBits, 0, voxelBits, adz * SLICE_UINTS, (GRID_SIZE - adz) * SLICE_UINTS);
            Arrays.fill(voxelBits, 0, adz * SLICE_UINTS, 0);
        }
    }

    /**
     * 将每个 Z 切片内的 Y 行在 Y 轴方向平移 dy 行。
     * dy > 0：每切片内保留低 Y 行（向低索引平移），高端清零。
     * dy < 0：每切片内保留高 Y 行（向高索引平移），低端清零。
     */
    private void shiftY(int dy) {
        if (dy == 0) return;
        for (int lz = 0; lz < GRID_SIZE; lz++) {
            int sliceBase = lz * SLICE_UINTS;
            if (dy > 0) {
                System.arraycopy(voxelBits, sliceBase + dy * ROW_UINTS, voxelBits, sliceBase,
                        (GRID_SIZE - dy) * ROW_UINTS);
                Arrays.fill(voxelBits, sliceBase + (GRID_SIZE - dy) * ROW_UINTS, sliceBase + SLICE_UINTS, 0);
            } else {
                int ady = -dy;
                System.arraycopy(voxelBits, sliceBase, voxelBits, sliceBase + ady * ROW_UINTS,
                        (GRID_SIZE - ady) * ROW_UINTS);
                Arrays.fill(voxelBits, sliceBase, sliceBase + ady * ROW_UINTS, 0);
            }
        }
    }

    /**
     * 将每个 (y, z) 行的体素比特在 X 轴方向平移 dx 位。
     * 支持任意 {@link #ROW_UINTS}，不再假定一行恰好是 64 bit。
     * dx > 0：右移（低 bit 方向），高 dx bit 清零供重建。
     * dx < 0：左移（高 bit 方向），低 |dx| bit 清零供重建。
     */
    private void shiftX(int dx) {
        if (dx == 0) return;
        int totalRows = GRID_SIZE * GRID_SIZE;

        for (int row = 0; row < totalRows; row++) {
            int rowBase = row * ROW_UINTS;
            int rowBits = voxelBits[rowBase];
            voxelBits[rowBase] = dx > 0 ? rowBits >>> dx : rowBits << -dx;
        }
    }

    /** 重建整个 Z 切片（lz 为本地坐标）。 */
    private void rebuildZSlice(int lz, BlockPos.MutableBlockPos mutable) {
        int wz = originZ + lz;
        for (int ly = 0; ly < GRID_SIZE; ly++) {
            int wy = originY + ly;
            for (int lx = 0; lx < GRID_SIZE; lx++) {
                mutable.set(originX + lx, wy, wz);
                if (isSolid(mutable)) setBit(lx, ly, lz);
            }
        }
    }

    /** 重建单行（固定 lz, ly，遍历所有 lx）。 */
    private void rebuildYRow(int lz, int ly, BlockPos.MutableBlockPos mutable) {
        int wy = originY + ly;
        int wz = originZ + lz;
        for (int lx = 0; lx < GRID_SIZE; lx++) {
            mutable.set(originX + lx, wy, wz);
            if (isSolid(mutable)) setBit(lx, ly, lz);
        }
    }

    /** 重建单个体素。 */
    private void rebuildVoxel(int lx, int ly, int lz, BlockPos.MutableBlockPos mutable) {
        mutable.set(originX + lx, originY + ly, originZ + lz);
        if (isSolid(mutable)) setBit(lx, ly, lz);
    }

    private boolean isSolid(BlockPos.MutableBlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(mc.level, pos, CollisionContext.empty()).isEmpty();
    }

    private void setBit(int lx, int ly, int lz) {
        int idx = flatIndex(lx, ly, lz);
        voxelBits[idx >> 5] |= (1 << (idx & 31));
    }

    /**
     * Z-major 线性索引，与 shader 中 sampleVoxel 一致。
     */
    private static int flatIndex(int x, int y, int z) {
        return (z * GRID_SIZE + y) * GRID_SIZE + x;
    }

    /**
     * 将体素数据写入 {@link Std430Writer}。
     * <p>
     * 写入精确 {@link #BUFFER_SIZE} 字节（16 header + 4096 body）。
     * 使用 {@code putInt} 而非 {@code putUInt}，二者等价（4 字节对齐 + 4 字节写入）。
     * 由于连续写 int 且起始 position 为 0（由调用方 clear），不会产生对齐填充。
     */
    public void writeTo(Std430Writer writer) {
        // Header: 4 × int = 16 bytes
        writer.putInt(originX);
        writer.putInt(originY);
        writer.putInt(originZ);
        writer.putInt(GRID_SIZE);

        // Body: BITMAP_UINT_COUNT × int = 32768 bytes
        for (int i = 0; i < BITMAP_UINT_COUNT; i++) {
            writer.putInt(voxelBits[i]);
        }
        // 此时 writer.writtenBytes() == BUFFER_SIZE
    }

    /**
     * 标记需要在下一次进行完整重建。
     */
    public void invalidate() {
        needsFullRebuild = true;
    }

    public int getOriginX() { return originX; }
    public int getOriginY() { return originY; }
    public int getOriginZ() { return originZ; }
}
