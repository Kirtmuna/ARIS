package jp.apple.aris.ctc.logic;

import jp.apple.aris.ArisCore;
import jp.apple.aris.cbi.CbiManager;
import jp.apple.aris.ctc.enums.SwitchPosition;
import jp.apple.aris.ctc.state.*;
import net.minecraft.world.World;

import java.util.Map;

public class BlockSystem {
    /**
     * 閉塞信号を自動で管理する
     */
    public static void runBlockSystem(World world) {
        if (world == null || world.isRemote) return;

        for (Map.Entry<String, Map<String, SectionState>> lineEntry : LineStateManager.getAllLineSections().entrySet()) {
            String lineId = lineEntry.getKey();
            Map<String, SectionState> sectionMap = lineEntry.getValue();
            if (sectionMap == null) continue;

            // 0. ポイントの向きを最新にする
            Map<String, SwitchState> points = LineStateManager.getPoints(lineEntry.getKey());
            if (points != null) {
                for (SwitchState p : points.values()) p.updateFromWorld(world);
            }

            // 1. 全線レールの状態を最新にし、区間の基本ステータスを確定させる
            for (SectionState section : sectionMap.values()) {
                if (section == null) continue;

                for (RailState rail : section.getSectionRails()) {
                    if (rail != null) {
                        rail.updateOccupancy(world);
                    }
                }

                section.setStatus(section.checkAnyRailOccupied() ? 1 : 0);
            }

            // 2. 現示の出力
            for (SectionState section : sectionMap.values()) {
                if (section == null || section.getStartSignal() == null) continue;
                SignalState startSig = section.getStartSignal();

                int idealAspect;
                if (section.getStatus() == 1) {
                    // 自区間に車両がいるなら停止現示
                    idealAspect = 0;
                } else if (section.hasEndpoint()) {
                    // 終端が信号ならその信号、ポイントなら現在の向きに対応する信号を見る
                    SignalState next = section.resolveNextSignal();
                    // 無灯は停止扱い
                    int nextAspect = (next != null && next.isControlled()) ? next.getCurrentAspect() : 0;
                    idealAspect = (nextAspect == 0) ? 1 : Math.min(nextAspect + 1, 5);
                } else {
                    idealAspect = 1;
                }
                int targetAspect = idealAspect;
                if (section.getStatus() == 0 && targetAspect > 0) {
                    while (targetAspect < 5 && startSig.getSignalType().getLevel(targetAspect) == startSig.getSignalType().getLevel(0)) {
                        targetAspect++;
                    }
                    if (startSig.getSignalType().getLevel(targetAspect) == startSig.getSignalType().getLevel(0)) {
                        while (targetAspect > 0 && startSig.getSignalType().getLevel(targetAspect) == startSig.getSignalType().getLevel(0)) {
                            targetAspect--;
                        }
                    }
                }
                startSig.setSignal(world, targetAspect);
            }
            // 2.5 ポイントの鎖錠: 開通していない側の信号を停止にする
            if (points != null) {
                for (SwitchState p : points.values()) {
                    SignalState closed = p.getClosedSignal();
                    if (closed != null) closed.setSignal(world, 0);
                    if (p.getCurrentPosition() == SwitchPosition.UNKNOWN) {
                        if (p.getNormalSignal() != null) p.getNormalSignal().setSignal(world, 0);
                        if (p.getReverseSignal() != null) p.getReverseSignal().setSignal(world, 0);
                    }
                }
            }
            // 3. 無灯信号を停止現示にする
            for (SignalState sig : LineStateManager.getSignals(lineEntry.getKey()).values()) {
                if (!sig.isControlled()) {
                    sig.setSignal(world, 0);
                }
            }
            // 4. 進路の自動解放
            runRouteAutoRelease(world, lineId);
        }
    }
    /**
     * 進路の状態を監視し、列車が進路内を通過し終えたら自動解放する。
     * - SET      : 予約済み・まだ進入なし
     * - OCCUPIED : 一度でも区間に進入した
     * - 全區間が無在線になったら解放
     */
    private static void runRouteAutoRelease(World world, String lineId) {
        Map<String, RouteState> routes = LineStateManager.getRoutes(lineId);
        if (routes == null) return;

        for (RouteState route : routes.values()) {
            if (route.getStatus() == RouteState.Status.IDLE) continue;

            boolean anyOccupied = false;
            for (SectionState s : route.getSections()) {
                if (s.isOccupied()) {
                    anyOccupied = true;
                    break;
                }
            }

            if (route.getStatus() == RouteState.Status.SET && anyOccupied) {
                route.setStatus(RouteState.Status.OCCUPIED);
                ArisCore.LOGGER.info("ARIS: 進路 '{}' に列車が進入しました", route.getRouteId());
            } else if (route.getStatus() == RouteState.Status.OCCUPIED && !anyOccupied) {
                CbiManager.get(lineId).releaseRoute(world, route);
                route.setStatus(RouteState.Status.IDLE);
                ArisCore.LOGGER.info("ARIS: 進路 '{}' を自動解放しました", route.getRouteId());
            }
        }
    }
}
