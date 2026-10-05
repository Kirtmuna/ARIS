package jp.apple.aris.ctc.state;

import jp.apple.aris.common.util.PositionUtil;
import jp.apple.aris.ctc.enums.SignalType;
import jp.ngt.rtm.electric.TileEntitySignal;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SignalState {
    private final String signalId;
    // 全信号機座標を入れる
    private final BlockPos[] signalPositions;
    // 現在の信号レベルの状態
    private int currentSignalLevel = 0;
    // 共通現示インデックス (0=停止, 1=警戒, 2=注意, 3=減速, 4=進行, 5=高速進行)
    private int currentAspect = 0; // 最初は停止で初期化
    // 灯数
    private final SignalType signalType;
    // 区間に扱われているかどうかのフラグ
    private boolean isControlled = true;
    
    public SignalState(String signalId, int[] pos, String type) {
        this.signalId = signalId;
        this.signalPositions = new BlockPos[]{ PositionUtil.toBlockPos(pos) };
        this.signalType = SignalType.fromKey(type);
    }
    /**
     * 現示の設定、RTM信号の同期
     * @param world 信号機が存在するワールドのインスタンス
     * @param aspectIndex 設定したい共通現示インデックス (0=停止〜5=高速進行)
     */
    public void setSignal(World world, int aspectIndex) {
        this.currentAspect = aspectIndex;
        int level = this.signalType.getLevel(aspectIndex);
        
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
    public void setControlled(boolean controlled) {
        this.isControlled = controlled;
    }

    public boolean isControlled() {
        return this.isControlled;
    }
    // ゲッター
    public String getSignalId() { return signalId; }
    public BlockPos[] getSignalPositions() { return signalPositions; }
    public int getCurrentLevel() { return currentSignalLevel; }
    public SignalType getSignalType() { return signalType; }
    public int getCurrentAspect() { return currentAspect; }
}
