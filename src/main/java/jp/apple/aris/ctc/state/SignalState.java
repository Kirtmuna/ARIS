package jp.apple.aris.ctc.state;

import jp.apple.aris.common.util.PositionUtil;
import jp.apple.aris.ctc.config.LineConfig;
import jp.ngt.rtm.electric.TileEntitySignal;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SignalState {
    private final String signalId;
    private final LineConfig.SignalConfig config;
    // 全信号機座標を入れる
    private final BlockPos[] signalPositions;
    // 現在の信号レベルの状態
    private int currentSignalLevel = 0;
    
    public SignalState(String id, LineConfig.SignalConfig config) {
        this.signalId = id;
        this.config = config;
        this.signalPositions = PositionUtil.toBlockPosArray(config.positions);
    }
    /**
     * 現示の設定、RTM信号の同期
     * @param world 信号機が存在するワールドのインスタンス
     * @param level 設定したい信号レベル
     */
    public void setSignal(World world, int level) {
        this.currentSignalLevel = level;
        
        if (world == null || world.isRemote) {
            return;
        }
        for (BlockPos pos : this.signalPositions) {
            if (!world.isBlockLoaded(pos)) {
                continue;
            }
            
            TileEntity te = world.getTileEntity(pos);
            
            if (te instanceof TileEntitySignal) {
                TileEntitySignal rtmSignal = (TileEntitySignal) te;
                rtmSignal.setElectricity(pos.getX(), pos.getY(), pos.getZ(), level);
            }
        }
    }
    // ゲッター
    public String getSignalId() { return signalId; }
    public LineConfig.SignalConfig getConfig() { return config; }
    public BlockPos[] getSignalPositions() { return signalPositions; }
    public int getCurrentLevel() { return currentSignalLevel; }
}
