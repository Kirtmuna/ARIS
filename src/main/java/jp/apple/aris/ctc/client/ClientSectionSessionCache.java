package jp.apple.aris.ctc.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClientSectionSessionCache {
    private static String lineId = "";
    private static List<String> selectedRailIds = new ArrayList<>();

    public static void set(String lineId, List<String> ids) {
        ClientSectionSessionCache.lineId = lineId;
        ClientSectionSessionCache.selectedRailIds = new ArrayList<>(ids);
    }

    public static void clear() {
        lineId = "";
        selectedRailIds = new ArrayList<>();
    }

    public static String getLineId() { return lineId; }
    public static List<String> getSelectedRailIds() { return Collections.unmodifiableList(selectedRailIds); }
}
