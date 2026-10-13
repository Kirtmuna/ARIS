package jp.apple.aris.ctc.logic;

import jp.apple.aris.ArisCore;
import jp.apple.aris.cbi.Cbi;
import jp.apple.aris.ctc.enums.SwitchPosition;
import jp.apple.aris.ctc.state.*;
import net.minecraft.world.World;

import java.util.*;

public class BlockSystem {
    /**
     * 閉塞信号を自動で管理する
     */
    public static void runBlockSystem(World world) {
        if (world == null || world.isRemote) return;

        Map<String, SectionState> sectionMap = LineStateManager.getSections();
        if (sectionMap == null || sectionMap.isEmpty()) return;

        // 0. ポイントの向きを最新にする
        Map<String, SwitchState> points = LineStateManager.getPoints();
        if (points != null) {
            for (SwitchState p : points.values()) p.updateFromWorld(world);
        }

        // 1. 全線レールの状態を最新にし、区間の基本ステータスを確定させる
        for (SectionState section : sectionMap.values()) {
            if (section == null) continue;
            for (RailState rail : section.getSectionRails()) {
                if (rail != null) rail.updateOccupancy(world);
            }
            section.setStatus(section.checkAnyRailOccupied() ? 1 : 0);
        }

        // 2. 各信号の最終現示を調停方式で決める
        Map<SignalState, List<Integer>> claims = new HashMap<>();
        // 2a. 区間制御の主張を集める
        for (SectionState section : sectionMap.values()) {
            if (section == null || section.getStartSignal() == null) continue;
            SignalState startSig = section.getStartSignal();

            int idealLevel;
            if (section.getStatus() == 1) {
                idealLevel = 1;
            } else if (section.hasEndpoint()) {
                SignalState next = section.resolveNextSignal();
                int nextLevel = (next != null && next.isControlled())
                        ? next.getCurrentLevel() : 1;
                idealLevel = startSig.getSignalType().upLevel(nextLevel);
            } else {
                idealLevel = 2;
            }
            claims.computeIfAbsent(startSig, k -> new ArrayList<>()).add(idealLevel);
        }
        // 2b. ポイント鎖錠の主張を集める
        if (points != null) {
            for (SwitchState p : points.values()) {
                for (SignalState closed : p.getClosedSignals()) {
                    if (closed != null) {
                        claims.computeIfAbsent(closed, k -> new ArrayList<>()).add(1);
                    }
                }
                if (p.getCurrentPosition() == SwitchPosition.UNKNOWN) {
                    for (SignalState s : p.getNormalSignals()) {
                        if (s != null) claims.computeIfAbsent(s, k -> new ArrayList<>()).add(1);
                    }
                    for (SignalState s : p.getReverseSignals()) {
                        if (s != null) claims.computeIfAbsent(s, k -> new ArrayList<>()).add(1);
                    }
                }
            }
            Map<String, RouteState> routes = LineStateManager.getRoutes();
            Set<String> activeRoutePoints = new HashSet<>();
            Set<String> activeRouteSwitches = new HashSet<>();
            if (routes != null) {
                for (RouteState route : routes.values()) {
                    if (!route.isActive()) continue;
                    for (SwitchState p : route.getNormalPoints()) {
                        activeRoutePoints.add(p.getPointId());
                        activeRouteSwitches.add(p.getSwitchId());
                    }
                    for (SwitchState p : route.getReversePoints()) {
                        activeRoutePoints.add(p.getPointId());
                        activeRouteSwitches.add(p.getSwitchId());
                    }
                }
            }
            for (SwitchState p : points.values()) {
                if (activeRouteSwitches.contains(p.getSwitchId())
                        && !activeRoutePoints.contains(p.getPointId())) {
                    for (SignalState s : p.getNormalSignals()) {
                        if (s != null) claims.computeIfAbsent(s, k -> new ArrayList<>()).add(1);
                    }
                    for (SignalState s : p.getReverseSignals()) {
                        if (s != null) claims.computeIfAbsent(s, k -> new ArrayList<>()).add(1);
                    }
                }
            }
        }
        // 2c. 無灯信号の主張
        for (SignalState sig : LineStateManager.getSignals().values()) {
            if (!sig.isControlled()) {
                claims.computeIfAbsent(sig, k -> new ArrayList<>()).add(1);
            }
        }
        // 2d. 最小値を採用
        for (Map.Entry<SignalState, List<Integer>> e : claims.entrySet()) {
            int finalLevel = 6;
            for (int v : e.getValue()) {
                if (v < finalLevel) finalLevel = v;
            }
            e.getKey().setSignalByLevel(world, finalLevel);
        }
        // 3. 進路の自動解放
        runRouteAutoRelease(world);
    }

    private static void runRouteAutoRelease(World world) {
        Map<String, RouteState> routes = LineStateManager.getRoutes();
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
                Cbi.get().releaseRoute(world, route);
                route.setStatus(RouteState.Status.IDLE);
                ArisCore.LOGGER.info("ARIS: 進路 '{}' を自動解放しました", route.getRouteId());
            }
        }
    }
}
