package jp.apple.aris.ctc.config;

import java.util.Map;

public class LineConfig {
    public String name; // LineName
    
    public Map<String, RailConfig> rails; // 線路
    public Map<String, SignalConfig> signals; // 信号
    public Map<String, SectionConfig> sections; // 区間
    public Map<String, SwitchConfig> switches; // ポイント
    
    // 線路-config
    public static class RailConfig {
        public int[] position; // RailCoreの座標
    }
    // 信号-config
    public static class SignalConfig {
        public int[][] positions; // 座標-二重配列 [[x,y,z], [x,y,z]...]
        public String type;           // 信号の灯数-記述はInt
    }
    // 区間-config
    public static class SectionConfig {
        public String[] sectionRails; // その区間とするレールのID
        public String startSignal;    // 区間開始信号機のID
        public String endSignal;      // 区間終了信号機のID
    }
    // ポイント-config
    public static class SwitchConfig {
        public String rail;
        public Map<String, PointConfig> points;

        public static class PointConfig {
            public int index;      // SwitchType.getPoints()配列の何番目に対応するか
            public String nSignal; // NORMAL時の信号ID
            public String rSignal; // REVERSE時の信号ID
        }
    }
    }
}
