package jp.apple.aris.ctc.config;

import java.util.Map;

public class LineConfig {
    public Map<String, SignalConfig> signals; // 信号
    public Map<String, SwitchConfig> switches; // ポイント
    public Map<String, SectionConfig> sections; // 区間
    public Map<String, RouteConfig> routes; // 進路
    
    // 信号-config
    public static class SignalConfig {
        public String type;
    }
    // ポイント-config
    public static class SwitchConfig {
        public Map<String, PointConfig> points;

        public static class PointConfig {
            public String nSignal;
            public String rSignal;
        }
    }
    // 区間-config
    public static class SectionConfig {
        public int[][] rails;
        public String startSignal;
        public String endSignal;
    }
    // 進路-config
    public static class RouteConfig {
        public String[] sections;
        public RouteSwitchConfig route;

        public static class RouteSwitchConfig {
            public String[] nPoint;
            public String[] rPoint;
        }
    }
}
