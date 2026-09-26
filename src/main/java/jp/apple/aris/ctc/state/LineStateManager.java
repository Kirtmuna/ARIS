package jp.apple.aris.ctc.state;

import jp.apple.aris.ArisCore;
import jp.apple.aris.ctc.config.LineConfig;
import jp.apple.aris.ctc.config.LineManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LineStateManager {
    private static final Map<String, Map<String, RailState>> LINE_RAILS = new HashMap<>();
    private static final Map<String, Map<String, SignalState>> LINE_SIGNALS = new HashMap<>();
    private static final Map<String, Map<String, SectionState>> LINE_SECTIONS = new HashMap<>();

    /**
     * LineConfigを元にすべてのStateインスタンスを生成する
     */
    public static void initializeStates() {
        LINE_RAILS.clear();
        LINE_SIGNALS.clear();
        LINE_SECTIONS.clear();
        // LineManagerに保管されているすべての路線をループ処理
        for (Map.Entry<String, LineConfig> entry : LineManager.getAllLines().entrySet()) {
            String lineId = entry.getKey();
            LineConfig config = entry.getValue();
            
            ArisCore.LOGGER.info("ARIS: 路線をインスタンス化: {}", lineId);
            
            Map<String, RailState> railMap = new HashMap<>();
            Map<String, SignalState> signalMap = new HashMap<>();
            Map<String, SectionState> sectionMap = new HashMap<>();
            
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
                    
                    SectionState sectionState = new SectionState(secId, secConfig, targetRails, startSig, endSig);
                    sectionMap.put(secId, sectionState);
                }
            }
            LINE_RAILS.put(lineId, railMap);
            LINE_SIGNALS.put(lineId, signalMap);
            LINE_SECTIONS.put(lineId, sectionMap);

            ArisCore.LOGGER.info("ARIS: 路線のインスタンス化が完了しました: {} (区間数: {})", lineId, sectionMap.size());
        }
    }
    //ゲッター
    public static Map<String, SectionState> getSections(String lineId) { return LINE_SECTIONS.get(lineId); }
    public static Map<String, RailState> getRails(String lineId) { return LINE_RAILS.get(lineId); }
    public static Map<String, SignalState> getSignals(String lineId) { return LINE_SIGNALS.get(lineId); }

    public static Map<String, Map<String, SectionState>> getAllLineSections() { return LINE_SECTIONS; }
}
