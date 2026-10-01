package jp.apple.aris.cbi;

import java.util.HashMap;
import java.util.Map;

public class CbiManager {
    private static final Map<String, Cbi> LINE_CBI = new HashMap<>();

    public static Cbi get(String lineId) {
        return LINE_CBI.computeIfAbsent(lineId, k -> new Cbi());
    }
}
