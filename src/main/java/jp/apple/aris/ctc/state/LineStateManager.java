package jp.apple.aris.ctc.state;

import jp.apple.aris.ArisCore;
import jp.apple.aris.ctc.config.LineConfig;
import jp.apple.aris.ctc.config.LineManager;

import java.util.*;

public class LineStateManager {
    private static final Map<String, Map<String, RailState>> LINE_RAILS = new HashMap<>();
    private static final Map<String, Map<String, SignalState>> LINE_SIGNALS = new HashMap<>();
    private static final Map<String, Map<String, SectionState>> LINE_SECTIONS = new HashMap<>();
    private static final Map<String, Map<String, SwitchState>> LINE_POINTS = new HashMap<>();

    /**
     * LineConfigを元にすべてのStateインスタンスを生成する
     */
    public static void initializeStates() {
        LINE_RAILS.clear();
        LINE_SIGNALS.clear();
        LINE_SECTIONS.clear();
        LINE_POINTS.clear();
        // LineManagerに保管されているすべての路線をループ処理
        for (Map.Entry<String, LineConfig> entry : LineManager.getAllLines().entrySet()) {
            String lineId = entry.getKey();
            LineConfig config = entry.getValue();
            
            ArisCore.LOGGER.info("ARIS: 路線をインスタンス化: {}", lineId);
            
            Map<String, RailState> railMap = new HashMap<>();
            Map<String, SignalState> signalMap = new HashMap<>();
            Map<String, SectionState> sectionMap = new HashMap<>();
            Map<String, SwitchState> pointMap = new HashMap<>();
            Set<String> usedAsStart = new HashSet<>();
            
            // 1. レールのインスタンス化
            if (config.rails != null) {
                for (Map.Entry<String, LineConfig.RailConfig> railEntry : config.rails.entrySet()) {
                    String railId = railEntry.getKey();
                    
                    RailState railState = new RailState(lineId, railId, railEntry.getValue());
                    railMap.put(railId, railState);
                }
            }
            // 2. 信号機のインスタンス化
            if (config.signals != null) {
                for (Map.Entry<String, LineConfig.SignalConfig> sigEntry : config.signals.entrySet()) {
                    String sigId = sigEntry.getKey();
                    
                    SignalState signalState = new SignalState(sigId, sigEntry.getValue());
                    signalMap.put(sigId, signalState);
                }
            }
            // 3. ポイント(区間より先に作る)
            if (config.switches != null) {
                for (Map.Entry<String, LineConfig.SwitchConfig> swEntry : config.switches.entrySet()) {
                    String switchId = swEntry.getKey();
                    LineConfig.SwitchConfig sc = swEntry.getValue();

                    RailState r = railMap.get(sc.rail);
                    if (r == null) {
                        ArisCore.LOGGER.warn("ARIS: ポイント '{}' の rail '{}' が見つかりません", switchId, sc.rail);
                    }

                    if (sc.points != null) {
                        for (Map.Entry<String, LineConfig.SwitchConfig.PointConfig> ptEntry : sc.points.entrySet()) {
                            String pointKey = switchId + "." + ptEntry.getKey();
                            LineConfig.SwitchConfig.PointConfig pc = ptEntry.getValue();

                            pointMap.put(pointKey, new SwitchState(pointKey, switchId, r, pc.index,
                                    signalMap.get(pc.nSignal), signalMap.get(pc.rSignal)));
                        }
                    }
                }
            }
            // 3. 区間のインスタンス化
            if (config.sections != null) {
                for (Map.Entry<String, LineConfig.SectionConfig> secEntry : config.sections.entrySet()) {
                    String secId = secEntry.getKey();
                    LineConfig.SectionConfig secConfig = secEntry.getValue();
                    
                    List<RailState> targetRails = new ArrayList<>();
                    
                    if (secConfig.sectionRails != null) {
                        for (String railId : secConfig.sectionRails) {
                            RailState rState = railMap.get(railId);
                            if (rState != null) {
                                targetRails.add(rState);
                            }
                        }
                    }

                    SignalState startSig = signalMap.get(secConfig.startSignal);
                    SignalState endSig = signalMap.get(secConfig.endSignal);
                    SwitchState endPt = null;
                    if (endSig == null && secConfig.endSignal != null) {
                        endPt = pointMap.get(secConfig.endSignal);
                        if (endPt == null) {
                            ArisCore.LOGGER.warn("ARIS: 区間 '{}' の endSignal '{}' は信号にもポイントにも見つかりません", secId, secConfig.endSignal);
                        }
                    } else if (endSig != null && pointMap.containsKey(secConfig.endSignal)) {
                        ArisCore.LOGGER.warn("ARIS: ID '{}' が信号とポイントで重複しています。信号を優先します", secConfig.endSignal);
                    }
                    sectionMap.put(secId, new SectionState(secId, secConfig, targetRails, startSig, endSig, endPt));

                    if (secConfig.startSignal != null) {
                        usedAsStart.add(secConfig.startSignal);
                    }
                }
            }
            for (Map.Entry<String, SignalState> sigEntry : signalMap.entrySet()) {
                if (!usedAsStart.contains(sigEntry.getKey())) {
                    sigEntry.getValue().setControlled(false);
                    ArisCore.LOGGER.info("ARIS: 信号 '{}' はどの区間からも制御されません(無灯として停止現示に固定)", sigEntry.getKey());
                }
            }

            LINE_RAILS.put(lineId, railMap);
            LINE_SIGNALS.put(lineId, signalMap);
            LINE_SECTIONS.put(lineId, sectionMap);
            LINE_POINTS.put(lineId, pointMap);

            ArisCore.LOGGER.info("ARIS: 路線のインスタンス化が完了しました: {} (区間数: {})", lineId, sectionMap.size());
        }
    }
    //ゲッター
    public static Map<String, SectionState> getSections(String lineId) { return LINE_SECTIONS.get(lineId); }
    public static Map<String, RailState> getRails(String lineId) { return LINE_RAILS.get(lineId); }
    public static Map<String, SignalState> getSignals(String lineId) { return LINE_SIGNALS.get(lineId); }

    public static Map<String, Map<String, SectionState>> getAllLineSections() { return LINE_SECTIONS; }
    public static Map<String, SwitchState> getPoints(String lineId) { return LINE_POINTS.get(lineId); }
}
