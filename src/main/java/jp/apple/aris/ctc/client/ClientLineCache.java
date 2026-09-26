package jp.apple.aris.ctc.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClientLineCache {
    private static List<String> lineIds = new ArrayList<>();

    public static void setLineIds(List<String> ids) {
        lineIds = new ArrayList<>(ids);
        Collections.sort(lineIds);
    }

    public static List<String> getLineIds() {
        return lineIds;
    }
}
