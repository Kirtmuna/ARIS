package jp.apple.aris.ctc.logic;

import jp.apple.aris.ctc.state.LineStateManager;
import jp.apple.aris.ctc.state.RailState;
import jp.apple.aris.ctc.state.SectionState;
import jp.apple.aris.ctc.state.SignalState;
import net.minecraft.world.World;
import java.util.Map;

public class BlockSystem {
    /**
     * 閉塞信号を自動で管理する
     */
    public static void runBlockSystem(World world) {
        if (world == null || world.isRemote) return;

        for (Map.Entry<String, Map<String, SectionState>> lineEntry : LineStateManager.getAllLineSections().entrySet()) {
            Map<String, SectionState> sectionMap = lineEntry.getValue();
            if (sectionMap == null) continue;

            // 1. 全線レールの状態を最新にし、区間の基本ステータスを確定させる
            for (SectionState section : sectionMap.values()) {
                if (section == null) continue;

                for (RailState rail : section.getSectionRails()) {
                    if (rail != null) {
                        rail.updateOccupancy(world);
                    }
                }
                
                if (section.checkAnyRailOccupied()) {
                    section.setStatus(1); // 在線
                } else {
                    section.setStatus(0); // 空き
                }
            }

            // 2. 現示の出力
            for (SectionState section : sectionMap.values()) {
                if (section == null || section.getStartSignal() == null) continue;
                // 開始信号と終了信号を取得
                SignalState startSig = section.getStartSignal();
                SignalState endSig = section.getEndSignal();
                // 共通現示インデックス
                int idealAspect = 5;
                // 自区間に車両がいるなら停止現示
                if (section.getStatus() == 1) {
                    idealAspect = 0;
                }
                // 自区間に車両がいないなら、次の信号(endSignal)を読んで1つ緩い現示にする
                else {
                    if (endSig != null) {
                        int nextAspect = endSig.getCurrentAspect();
                        // 原則：endSigが N の場合startSigは N + 1 
                        if (nextAspect == 0) {
                            idealAspect = 1;
                        } else {
                            idealAspect = nextAspect + 1;
                        }
                        if (idealAspect > 5) idealAspect = 5;
                    } else {
                        idealAspect = 1;
                    }
                }
                int targetAspect = idealAspect;
                if (section.getStatus() == 0 && targetAspect > 0) {
                    while (targetAspect < 5 && startSig.getSignalType().getLevel(targetAspect) == startSig.getSignalType().getLevel(0)) {
                        targetAspect++;
                    }
                }
                startSig.setSignal(world, targetAspect);
            }
        }
    }
}
