package jp.apple.aris.cbi;

import java.util.HashMap;
import java.util.Map;

/**
 * 「今どの区間/ポイントを、どの進路が予約しているか」を持つ
 */
public class ReservationTable {
    private final Map<String, String> sectionOwner = new HashMap<>(); // sectionId -> routeId
    private final Map<String, String> pointOwner = new HashMap<>();   // pointId -> routeId

    public String getSectionOwner(String sectionId) { return sectionOwner.get(sectionId); }
    public String getPointOwner(String pointId) { return pointOwner.get(pointId); }

    public void setSectionOwner(String sectionId, String routeId) { sectionOwner.put(sectionId, routeId); }
    public void setPointOwner(String pointId, String routeId) { pointOwner.put(pointId, routeId); }

    public void clearSection(String sectionId) { sectionOwner.remove(sectionId); }
    public void clearPoint(String pointId) { pointOwner.remove(pointId); }
}
