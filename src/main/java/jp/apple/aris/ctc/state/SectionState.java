package jp.apple.aris.ctc.state;

import jp.apple.aris.ctc.config.LineConfig;
import java.util.ArrayList;
import java.util.List;

public class SectionState {
    private final String sectionId;
    private final LineConfig.SectionConfig config;
    
    private final List<RailState> sectionRails = new ArrayList<>();
    private final SignalState startSignal;
    private final SignalState endSignal;
    
    private int sectionStatus = 0;

    /**
     * コンストラクタ
     */
    public SectionState(String id, LineConfig.SectionConfig config, List<RailState> rails, SignalState start, SignalState end) {
        this.sectionId = id;
        this.config = config;
        
        if (rails != null) {
            this.sectionRails.addAll(rails);
        }

        this.startSignal = start;
        this.endSignal = end;
    }
    /**
     * この区間に所属しているすべてのレールのうち、1つでも列車が乗っているかチェックする
     * BlockSystem側から呼び出す
     */
    public boolean checkAnyRailOccupied() {
        for (RailState rail : this.sectionRails) {
            if (rail != null && rail.isOccupied()) {
                return true;
            }
        }
        return false;
    }
    // ゲッター
    public void setStatus(int status) { this.sectionStatus = status; }
    public boolean isOccupied() { return this.sectionStatus == 1; }
    public String getSectionId() { return sectionId; }
    public LineConfig.SectionConfig getConfig() { return config; }
    public List<RailState> getSectionRails() { return sectionRails; }
    public SignalState getStartSignal() { return startSignal; }
    public SignalState getEndSignal() { return endSignal; }
    public int getStatus() { return sectionStatus; }
}
