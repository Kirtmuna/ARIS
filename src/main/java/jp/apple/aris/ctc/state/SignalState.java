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
    private int currentSignalLevel = 1;
    private int currentAspect = 0;
    // 灯数
    private final SignalType signalType;
    // 区間に扱われているかどうかのフラグ
    private boolean isControlled = true;
    
    public SignalState(String signalId, int[] pos, String type) {
        this.signalId = signalId;
        this.signalPositions = new BlockPos[]{ PositionUtil.toBlockPos(pos) };
        this.signalType = SignalType.fromKey(type);
    }
    public void setSignalByLevel(World world, int level) {
        if (level < 1) level = 1;
        if (level > 6) level = 6;

        this.currentSignalLevel = level;
        this.currentAspect = aspectOfLevel(level);

        if (world == null || world.isRemote) return;

        for (BlockPos pos : this.signalPositions) {
            if (!world.isBlockLoaded(pos)) continue;
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntitySignal) {
                ((TileEntitySignal) te).setElectricity(pos.getX(), pos.getY(), pos.getZ(), level);
            }
        }
    }
    public void setSignal(World world, int aspectIndex) {
        int level = this.signalType.getLevel(aspectIndex);
        setSignalByLevel(world, level);
    }
    private int aspectOfLevel(int level) {
        for (int a = 0; a < 6; a++) {
            if (this.signalType.getLevel(a) == level) {
                return a;
            }
        }
        return 0;
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
