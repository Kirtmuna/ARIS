package jp.apple.aris.ctc.state;

import jp.apple.aris.ctc.config.LineConfig;

public class SectionState {
    private final String sectionId;
    private final LineConfig.SectionConfig config;
    
    private final RailState sectionRail;
    private final SignalState startSignal;
    private final SignalState endSignal;
    
    private int sectionStatus = 0;

    /**
     * コンストラクタ
     */
    public SectionState(String id, LineConfig.SectionConfig config, RailState rail, SignalState start, SignalState end) {
        this.sectionId = id;
        this.config = config;
        this.sectionRail = rail;
        this.startSignal = start;
        this.endSignal = end;
    }
    /**
     * この区間の状態を更新する
     * @param status Intで状態を表す（0=空, 1=在線）
     */
    public void setStatus(int status) {
        this.sectionStatus = status;
    }
    // ゲッター
    public String getSectionId() { return sectionId; }
    public LineConfig.SectionConfig getConfig() { return config; }
    public RailState getSectionRail() { return sectionRail; }
    public SignalState getStartSignal() { return startSignal; }
    public SignalState getEndSignal() { return endSignal; }
    public int getStatus() { return sectionStatus; }
}
