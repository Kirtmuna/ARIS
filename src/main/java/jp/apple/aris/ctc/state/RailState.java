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

    public RailState(String railId, int[] position) {
        this.railId = railId;
        this.railPosition = PositionUtil.toBlockPos(position);
    }

    public void updateOccupancy(World world) {
        if (world == null || !world.isBlockLoaded(this.railPosition)) return;
        TileEntity te = world.getTileEntity(this.railPosition);
        if (te instanceof TileEntityLargeRailCore) {
            this.isOccupied = ((TileEntityLargeRailCore) te).isLogicalRailOccupied();
            return;
        }
        if (te instanceof TileEntityLargeRailBase) {
            TileEntityLargeRailCore core = ((TileEntityLargeRailBase) te).getRailCore();
            if (core != null) {
                this.railPosition = core.getPos();
                this.isOccupied = core.isLogicalRailOccupied();
            }
        }
    }

    // ゲッター
    public String getRailId() {
        return railId;
    }

    public BlockPos getRailPosition() {
        return railPosition;
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