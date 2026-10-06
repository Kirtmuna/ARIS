package jp.apple.aris.api;

import jp.apple.aris.ctc.state.LineStateManager;
import jp.apple.aris.ctc.state.RailState;
import jp.apple.aris.ctc.state.SectionState;
import jp.apple.aris.ctc.state.SignalState;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.util.math.BlockPos;

import java.util.Map;


public class ArisApiUtil {
    /**
     * 全路線を走査して、引数の車両がいる区間を返す
     * 見つからない場合は null
     */
    public static SectionState getSectionOfTrain(EntityTrainBase train) {
        if (train == null) return null;

        double tx = train.posX;
        double tz = train.posZ;

        SectionState best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Map<String, SectionState> sections : LineStateManager.getAllLineSections().values()) {
            if (sections == null) continue;
            for (SectionState sec : sections.values()) {
                for (RailState rail : sec.getSectionRails()) {
                    if (rail == null || !rail.isOccupied()) continue;
                    BlockPos p = rail.getRailPosition();
                    double dx = (p.getX() + 0.5) - tx;
                    double dz = (p.getZ() + 0.5) - tz;
                    double d2 = dx * dx + dz * dz;
                    if (d2 < bestDistSq) {
                        bestDistSq = d2;
                        best = sec;
                    }
                }
            }
        }
        return best;
    }
    /**
     * 指定車両がいる区間の次の信号を返す
     */
    public static SignalState getNextSignalForTrain(EntityTrainBase train) {
        SectionState sec = getSectionOfTrain(train);
        if (sec == null) return null;
        return sec.resolveNextSignal();
    }
    /**
     * 指定車両がいる区間の次の信号の現示値を返す。
     * 0=停止, 1=警戒, 2=注意, 3=減速, 4=進行, 5=高速進行
     * -1=err
     */
    public static int getNextSignalAspectForTrain(EntityTrainBase train) {
        SignalState sig = getNextSignalForTrain(train);
        return sig != null ? sig.getCurrentAspect() : -1;
    }
}
