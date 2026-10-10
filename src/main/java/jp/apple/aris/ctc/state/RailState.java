package jp.apple.aris.ctc.state;

import jp.apple.aris.common.util.PositionUtil;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class RailState {
    private final String railId;
    private BlockPos railPosition;
    private boolean isOccupied = false;
    private String logicalRailId;

    public RailState(String railId, int[] position) {
        this.railId = railId;
        this.logicalRailId = railId;
        this.railPosition = PositionUtil.toBlockPos(position);
    }

    public void updateOccupancy(World world) {
        if (world == null || !world.isBlockLoaded(this.railPosition)) return;
        TileEntity te = world.getTileEntity(this.railPosition);
        if (te instanceof TileEntityLargeRailCore) {
            TileEntityLargeRailCore core = (TileEntityLargeRailCore) te;
            this.isOccupied = core.isLogicalRailOccupied();
            this.logicalRailId = resolveLogicalRailId(core, this.railId);
            return;
        }
        if (te instanceof TileEntityLargeRailBase) {
            TileEntityLargeRailBase base = (TileEntityLargeRailBase) te;
            TileEntityLargeRailCore core = base.getRailCore();
            if (core != null) {
                this.railPosition = core.getPos();
                this.isOccupied = core.isLogicalRailOccupied();
                this.logicalRailId = resolveLogicalRailId(core, this.railId);
            }
        }
    }
    private static String resolveLogicalRailId(TileEntityLargeRailCore core, String fallback) {
        try {
            java.util.List<int[]> group = core.getRailGroupCorePositions();
            if (group != null && !group.isEmpty()) {
                int[] gp = group.get(0);
                return gp[0] + "," + gp[1] + "," + gp[2];
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    // ゲッター
    public String getRailId() {
        return railId;
    }

    public BlockPos getRailPosition() {
        return railPosition;
    }

    public String getLogicalRailId() {
        return logicalRailId;
    }

    public boolean isOccupied() {
        return isOccupied;
    }

    /**
     * 一応上書きできるようにしておく
     */
    public void setOccupied(boolean occupied) {
        this.isOccupied = occupied;
    }
}