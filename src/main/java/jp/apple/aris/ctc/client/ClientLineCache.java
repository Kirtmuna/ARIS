package jp.apple.aris.ctc.client;

import jp.apple.aris.ctc.config.LineConfig;

public class ClientLineCache {
    private static LineConfig CONFIG = new LineConfig();

    public static void setConfig(LineConfig config) {
        CONFIG = (config != null) ? config : new LineConfig();
    }

    public static LineConfig getConfig() {
        return CONFIG;
    }
}
