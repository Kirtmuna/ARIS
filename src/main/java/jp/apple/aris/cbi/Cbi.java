package jp.apple.aris.cbi;

import jp.apple.aris.ArisCore;
import jp.apple.aris.ctc.enums.SwitchPosition;
import jp.apple.aris.ctc.state.RailState;
import jp.apple.aris.ctc.state.RouteState;
import jp.apple.aris.ctc.state.SectionState;
import jp.apple.aris.ctc.state.SwitchState;
import net.minecraft.world.World;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * CBI: 進路の予約可否を判定し、
 * 予約テーブルへの反映だけを行う。
 * 「今の状態から鎖錠可否を都度計算する」のではなく、
 * 「予約した時点でチェックし、予約されている間は他から触れない」という排他制御。
 */
public class Cbi {
    private final ReservationTable table = new ReservationTable();
    /**
     * 進路の予約を試みる。
     * @return 予約できたらtrue。既に他の進路が資源を使っている場合はfalse
     * 資源：レール/ポイント
     */
    public boolean requestRoute(World world, RouteState route) {
        if (route == null) return false;

        String routeId = route.getRouteId();

        // 進路が通過する全レールを集める（区間の重複は除く）
        Set<String> railsInRoute = collectRails(route);
        
        // 1. 全資源が空いているか、自分が既に持っているかを確認する
        for (String railId : railsInRoute) {
            String owner = table.getRailOwner(railId);
            if (owner != null && !owner.equals(routeId)) {
                ArisCore.LOGGER.info("ARIS: 進路 '{}' 予約失敗: レール '{}' は進路 '{}' が使用中", routeId, railId, owner);
                return false;
            }
        }
        for (SwitchState point : route.getNormalPoints()) {
            if (isPointTaken(routeId, point)) return false;
        }
        for (SwitchState point : route.getReversePoints()) {
            if (isPointTaken(routeId, point)) return false;
        }
        // 2. ここまで来れば全資源が確保可能なので予約する
        for (String railId : railsInRoute) {
            table.setRailOwner(railId, routeId);
        }
        for (SwitchState point : route.getNormalPoints()) {
            table.setPointOwner(point.getPointId(), routeId);
        }
        for (SwitchState point : route.getReversePoints()) {
            table.setPointOwner(point.getPointId(), routeId);
        }
        // 3. API からポイント転換
        for (SwitchState point : route.getNormalPoints()) {
            point.setPosition(world, SwitchPosition.NORMAL);
        }
        for (SwitchState point : route.getReversePoints()) {
            point.setPosition(world, SwitchPosition.REVERSE);
        }

        ArisCore.LOGGER.info("ARIS: 進路 '{}' を予約しました (レール{}本)", routeId, railsInRoute.size());
        return true;
    }
    /**
     * 進路の予約を解放する。その進路が持っていた資源だけを外す。
     */
    public void releaseRoute(World world, RouteState route) {
        if (route == null) return;
        String routeId = route.getRouteId();

        Set<String> railsInRoute = collectRails(route);
        for (String railId : railsInRoute) {
            if (routeId.equals(table.getRailOwner(railId))) {
                table.clearRail(railId);
            }
        }
        for (SwitchState point : route.getNormalPoints()) {
            if (routeId.equals(table.getPointOwner(point.getPointId()))) {
                table.clearPoint(point.getPointId());
            }
        }
        for (SwitchState point : route.getReversePoints()) {
            if (routeId.equals(table.getPointOwner(point.getPointId()))) {
                table.clearPoint(point.getPointId());
            }
        }
        // 誰にも予約されてないポイントの API 制御を外す
        for (SwitchState p : route.getNormalPoints()) {
            if (table.getPointOwner(p.getPointId()) == null) p.releaseApiControl(world);
        }
        for (SwitchState p : route.getReversePoints()) {
            if (table.getPointOwner(p.getPointId()) == null) p.releaseApiControl(world);
        }

        ArisCore.LOGGER.info("ARIS: 進路 '{}' を解放しました", routeId);
    }
    /**
     * 進路が通過する区間から、含まれる全レールIDを集めて重複を除く。
     */
    private Set<String> collectRails(RouteState route) {
        Set<String> rails = new LinkedHashSet<>();
        for (SectionState section : route.getSections()) {
            for (RailState rail : section.getSectionRails()) {
                if (rail != null) {
                    rails.add(rail.getRailId());
                }
            }
        }
        return rails;
    }
    /** 指定ポイントが、自分以外の進路に予約されているか */
    private boolean isPointTaken(String routeId, SwitchState point) {
        String owner = table.getPointOwner(point.getPointId());
        if (owner != null && !owner.equals(routeId)) {
            ArisCore.LOGGER.info("ARIS: 進路 '{}' 予約失敗: ポイント '{}' は進路 '{}' が使用中", routeId, point.getPointId(), owner);
            return true;
        }
        return false;
    }
    public ReservationTable getTable() { return table; }
}
