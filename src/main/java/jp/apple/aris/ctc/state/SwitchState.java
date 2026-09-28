package jp.apple.aris.ctc.state;

import jp.apple.aris.ctc.config.LineConfig;
import jp.apple.aris.ctc.enums.SwitchPosition;
import jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore;
import jp.ngt.rtm.rail.util.Point;
import jp.ngt.rtm.rail.util.RailDir;
import jp.ngt.rtm.rail.util.SwitchType;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class SwitchState {
    private final String switchId;
    private final LineConfig.SwitchConfig config;
    private final RailState rail;
    private final SignalState normalSignal;
    private final SignalState reverseSignal;

    private SwitchPosition currentPosition = SwitchPosition.UNKNOWN;

    public SwitchState(String id, LineConfig.SwitchConfig config,
                       RailState rail, SignalState nSig, SignalState rSig) {
        this.switchId = id;
        this.config = config;
        this.rail = rail;
        this.normalSignal = nSig;
        this.reverseSignal = rSig;
    }
    /** RTM側の転換状態を読む */
    public void updateFromWorld(World world) {
        SwitchPosition prev = this.currentPosition;
        this.currentPosition = readPosition(world, prev);
    }

    private SwitchPosition readPosition(World world, SwitchPosition fallback) {
        if (world == null || rail == null || !world.isBlockLoaded(rail.getRailPosition())) {
            return fallback;
        }

        TileEntity te = world.getTileEntity(rail.getRailPosition());
        if (!(te instanceof TileEntityLargeRailSwitchCore)) {
            return SwitchPosition.UNKNOWN;
        }

        SwitchType st = ((TileEntityLargeRailSwitchCore) te).getSwitch();
        if (!(st instanceof SwitchType.SwitchBasic)) {
            return SwitchPosition.UNKNOWN;
        }

        Point[] points = st.getPoints();
        if (points == null || points.length == 0 || points[0].branchDir == RailDir.NONE) {
            return SwitchPosition.UNKNOWN;
        }

        return points[0].rpRoot.checkRSInput(world)
                ? SwitchPosition.REVERSE
                : SwitchPosition.NORMAL;
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

    public String getSwitchId() { return switchId; }
    public RailState getRail() { return rail; }
    public SignalState getNormalSignal() { return normalSignal; }
    public SignalState getReverseSignal() { return reverseSignal; }
    public SwitchPosition getCurrentPosition() { return currentPosition; }
}
