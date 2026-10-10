package jp.apple.aris.ctc.state;

import jp.apple.aris.ArisCore;
import jp.apple.aris.ctc.config.LineConfig;
import jp.apple.aris.ctc.enums.SwitchPosition;
import jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore;
import jp.ngt.rtm.rail.util.Point;
import jp.ngt.rtm.rail.util.RailDir;
import jp.ngt.rtm.rail.util.SwitchType;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class SwitchState {
    private final String pointId;          // 合成ID ("SW001.P0")
    private final String switchId;         // 親Core側のID ("SW001")
    private final RailState rail;          // 親Coreのレール参照
    private final int pointIndex;          // SwitchType.getPoints()内でのindex
    private final SignalState normalSignal;
    private final SignalState reverseSignal;

    private SwitchPosition currentPosition = SwitchPosition.UNKNOWN;

    public SwitchState(String pointId, String switchId, RailState rail, int pointIndex,
                      SignalState nSig, SignalState rSig) {
        this.pointId = pointId;
        this.switchId = switchId;
        this.rail = rail;
        this.pointIndex = pointIndex;
        this.normalSignal = nSig;
        this.reverseSignal = rSig;
    }
    /** RTM側の転換状態を読む */
    public void updateFromWorld(World world) {
        SwitchPosition prev = this.currentPosition;
        this.currentPosition = readPosition(world);
        if (prev != this.currentPosition) {
            ArisCore.LOGGER.info("ARIS: ポイント '{}' : {} -> {}", pointId, prev, this.currentPosition);
        }
    }

    private SwitchPosition readPosition(World world) {
        if (world == null || rail == null || !world.isBlockLoaded(rail.getRailPosition())) {
            return this.currentPosition;
        }

        TileEntity te = world.getTileEntity(rail.getRailPosition());
        if (!(te instanceof TileEntityLargeRailSwitchCore)) {
            return SwitchPosition.UNKNOWN;
        }

        TileEntityLargeRailSwitchCore core = (TileEntityLargeRailSwitchCore) te;

        Boolean api = core.getApiPointPosition(pointIndex);
        if (api != null) {
            return api ? SwitchPosition.REVERSE : SwitchPosition.NORMAL;
        }

        SwitchType st = ((TileEntityLargeRailSwitchCore) te).getSwitch();
        if (st == null) return SwitchPosition.UNKNOWN;

        Point[] points = st.getPoints();
        if (points == null || pointIndex < 0 || pointIndex >= points.length) {
            return SwitchPosition.UNKNOWN;
        }

        Point p = points[pointIndex];
        return p.rpRoot.checkRSInput(world) ? SwitchPosition.REVERSE : SwitchPosition.NORMAL;
    }
    /** 現在の向きに対応する信号 */
    public SignalState getActiveSignal() {
        switch (currentPosition) {
            case NORMAL: return normalSignal;
            case REVERSE: return reverseSignal;
            default: return null;
        }
    }
    /** 現在の向きと逆側の信号 */
    public SignalState getClosedSignal() {
        switch (currentPosition) {
            case NORMAL: return reverseSignal;
            case REVERSE: return normalSignal;
            default: return null;
        }
    }

    /**
     * 分岐をApiから扱う
     */
    public void setPosition(World world, SwitchPosition pos) {
        if (rail == null || pos == SwitchPosition.UNKNOWN) return;
        TileEntity te = world.getTileEntity(rail.getRailPosition());
        if (!(te instanceof TileEntityLargeRailSwitchCore)) return;

        boolean reversed = (pos == SwitchPosition.REVERSE);
        ((TileEntityLargeRailSwitchCore) te).setApiPointPosition(pointIndex, reversed);
    }

    public void releaseApiControl(World world) {
        if (rail == null) return;
        TileEntity te = world.getTileEntity(rail.getRailPosition());
        if (!(te instanceof TileEntityLargeRailSwitchCore)) return;

        ((TileEntityLargeRailSwitchCore) te).clearApiPointPosition(pointIndex);
    }

    public String getPointId() { return pointId; }
    public String getSwitchId() { return switchId; }
    public SignalState getNormalSignal() { return normalSignal; }
    public SignalState getReverseSignal() { return reverseSignal; }
    public SwitchPosition getCurrentPosition() { return currentPosition; }
}
