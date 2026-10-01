package jp.apple.aris.cbi;

import java.util.HashMap;
import java.util.Map;

/**
 * 「今どのレール/ポイントを、どの進路が予約しているか」を持つ
 */
public class ReservationTable {
    private final Map<String, String> railOwner = new HashMap<>();    // railId -> routeId
    private final Map<String, String> pointOwner = new HashMap<>();   // pointId -> routeId

    public String getRailOwner(String railId) { return railOwner.get(railId); }
    public String getPointOwner(String pointId) { return pointOwner.get(pointId); }

    public void setRailOwner(String railId, String routeId) { railOwner.put(railId, routeId); }
    public void setPointOwner(String pointId, String routeId) { pointOwner.put(pointId, routeId); }

    public void clearRail(String railId) { railOwner.remove(railId); }
    public void clearPoint(String pointId) { pointOwner.remove(pointId); }
}
