package jp.apple.aris.ctc.client;

import jp.apple.aris.ctc.config.LineConfig;

import java.util.*;

public class ClientLineCache {
    private static Map<String, LineConfig> LINES = new HashMap<>();

    public static void setLines(Map<String, LineConfig> lines) {
        LINES = new HashMap<>(lines);
    }

    public static List<String> getLineIds() {
        List<String> ids = new ArrayList<>(LINES.keySet());
        Collections.sort(ids);
        return ids;
    }

    public static LineConfig getConfig(String lineId) {
        return LINES.get(lineId);
    }
}
