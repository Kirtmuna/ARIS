package jp.apple.aris.ctc.state;

import jp.apple.aris.common.util.PositionUtil;
import jp.apple.aris.ctc.config.LineConfig;
import jp.apple.aris.ctc.config.LineManager;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class RailState {
    private final String lineId;
    
    private final String railId;
    private final LineConfig.RailConfig config;
    // RailCoreの座標
    private BlockPos railPosition;
    // 上に車両がいるかのフラグ
    private boolean isOccupied = false;

    public RailState(String lineId, String id, LineConfig.RailConfig config) {
        this.lineId = lineId;
        this.railId = id;
        this.config = config;
        this.railPosition = PositionUtil.toBlockPos(config.position);
    }
    /**
     * RTM側のRailCoreを参照して在線状態を更新する
     */
    public void updateOccupancy(World world) {
        if (world == null || !world.isBlockLoaded(this.railPosition)) {
            return;
        }

        TileEntity te = world.getTileEntity(this.railPosition);
        
        if (te instanceof TileEntityLargeRailCore) {
            this.isOccupied = ((TileEntityLargeRailCore) te).isLogicalRailOccupied();
            return;
        }
        
        if (te instanceof TileEntityLargeRailBase) {
            TileEntityLargeRailCore core = ((TileEntityLargeRailBase) te).getRailCore();

            if (core != null) {
                BlockPos corePos = core.getPos();
                
                this.config.position[0] = corePos.getX();
                this.config.position[1] = corePos.getY();
                this.config.position[2] = corePos.getZ();
                
                LineManager.saveLine(this.lineId);
                
                this.railPosition = corePos;
                this.isOccupied = core.isLogicalRailOccupied();
                return;
            }
        }
    }
    /**
     * 一応上書きできるようにしておく
     */
    public void setOccupied(boolean occupied) {
        this.isOccupied = occupied;
    }
    // ゲッター
    public String getRailId() { return railId; }
    public LineConfig.RailConfig getConfig() { return config; }
    public BlockPos getRailPosition() { return railPosition; }
    public boolean isOccupied() { return isOccupied; }
}