package jp.apple.aris.cbi;

import jp.apple.aris.ArisCore;
import jp.apple.aris.ctc.state.RouteState;
import jp.apple.aris.ctc.state.SectionState;
import jp.apple.aris.ctc.state.SwitchState;

import java.util.List;

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
     * 資源：区間/ポイント
     */
    public boolean requestRoute(RouteState route) {
        if (route == null) return false;

        String routeId = route.getRouteId();

        // 1. まず全資源が空いているか、自分が既に持っているかを確認する
        for (SectionState section : route.getSections()) {
            String owner = table.getSectionOwner(section.getSectionId());
            if (owner != null && !owner.equals(routeId)) {
                ArisCore.LOGGER.info("ARIS: 進路 '{}' 予約失敗: 区間 '{}' は進路 '{}' が使用中", routeId, section.getSectionId(), owner);
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
        for (SectionState section : route.getSections()) {
            table.setSectionOwner(section.getSectionId(), routeId);
        }
        for (SwitchState point : route.getNormalPoints()) {
            table.setPointOwner(point.getPointId(), routeId);
        }
        for (SwitchState point : route.getReversePoints()) {
            table.setPointOwner(point.getPointId(), routeId);
        }

        ArisCore.LOGGER.info("ARIS: 進路 '{}' を予約しました", routeId);
        return true;
    }
    /**
     * 進路の予約を解放する。その進路が持っていた資源だけを外す。
     */
    public void releaseRoute(RouteState route) {
        if (route == null) return;
        String routeId = route.getRouteId();

        for (SectionState section : route.getSections()) {
            if (routeId.equals(table.getSectionOwner(section.getSectionId()))) {
                table.clearSection(section.getSectionId());
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

        ArisCore.LOGGER.info("ARIS: 進路 '{}' を解放しました", routeId);
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
