package jp.apple.aris.ctc.tool;

import jp.apple.aris.common.util.PositionUtil;
import jp.apple.aris.ctc.config.LineConfig;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

public class LineLookup {
    /** CoreのBlockPosから登録済みレールIDを逆引きする */
    public static String findRailId(LineConfig config, TileEntityLargeRailCore core) {
        if (config.rails == null || core == null) return null;

        List<int[]> groupPositions = core.getRailGroupCorePositions();
        for (int[] gp : groupPositions) {
            for (Map.Entry<String, LineConfig.RailConfig> e : config.rails.entrySet()) {
                int[] p = e.getValue().position;
                if (p != null && p.length >= 3 && p[0] == gp[0] && p[1] == gp[1] && p[2] == gp[2]) {
                    return e.getKey();
                }
            }
        }
        return null;
    }
    /** クリックされた座標が、登録済みのどの信号機のpositionsに含まれるか逆引きする */
    public static String findSignalId(LineConfig config, BlockPos pos) {
        if (config.signals == null) return null;
        for (Map.Entry<String, LineConfig.SignalConfig> e : config.signals.entrySet()) {
            for (BlockPos sp : PositionUtil.toBlockPosArray(e.getValue().positions)) {
                if (sp.equals(pos)) {
                    return e.getKey();
                }
            }
        }
        return null;
    }
}
