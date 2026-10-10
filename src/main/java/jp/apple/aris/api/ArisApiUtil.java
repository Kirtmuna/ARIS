package jp.apple.aris.api;

import jp.apple.aris.ctc.state.LineStateManager;
import jp.apple.aris.ctc.state.RailState;
import jp.apple.aris.ctc.state.SectionState;
import jp.apple.aris.ctc.state.SignalState;
import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationEntry;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.WeakHashMap;

public class ArisApiUtil {
    private static final Map<EntityTrainBase, SectionState> CURRENT_SECTION = new WeakHashMap<>();
    /**
     * 車両が今乗っているレール（currentRailObj）から、
     * それが属する区間を逆引きする。
     */
    public static SectionState getSection(EntityTrainBase train) {
        if (train == null) return null;

        EntityTrainBase head = getFormationHead(train);
        if (head == null) head = train;
        
        EntityBogie bogie = head.getBogie(head.getTrainDirection());
        if (bogie == null) return CURRENT_SECTION.get(train);

        TileEntityLargeRailCore core = bogie.getCurrentRailObj();
        if (core == null) return CURRENT_SECTION.get(train);

        BlockPos corePos = core.getPos();
        String key = corePos.getX() + "," + corePos.getY() + "," + corePos.getZ();
        
        SectionState found = findSectionByRailKey(key);
        if (found != null) {
            CURRENT_SECTION.put(train, found);
        }
        return CURRENT_SECTION.get(train);
    }

    private static SectionState findSectionByRailKey(String railKey) {
        for (SectionState sec : LineStateManager.getSections().values()) {
            for (RailState rail : sec.getSectionRails()) {
                if (rail == null) continue;
                BlockPos p = rail.getRailPosition();
                String k = p.getX() + "," + p.getY() + "," + p.getZ();
                if (k.equals(railKey)) {
                    return sec;
                }
            }
        }
        return null;
    }

    private static EntityTrainBase getFormationHead(EntityTrainBase train) {
        Formation formation = train.getFormation();
        if (formation == null) return train;
        for (FormationEntry entry : formation.entries) {
            if (entry == null || entry.train == null) continue;
            if (formation.isFrontCar(entry.train)) return entry.train;
        }
        return train;
    }

    public static SignalState getNextSignal(EntityTrainBase train) {
        SectionState sec = getSection(train);
        if (sec == null) return null;
        return sec.resolveNextSignal();
    }

    public static int getNextSignalAspect(EntityTrainBase train) {
        SignalState sig = getNextSignal(train);
        return sig != null ? sig.getCurrentAspect() : -1;
    }
}
