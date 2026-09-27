package jp.apple.aris.ctc.tool;

import java.util.ArrayList;
import java.util.List;

public class SectionRegisterSession {
    public final String lineId;
    public final String namePattern;
    public final String startSignalId;
    public final List<String> railIds = new ArrayList<>();

    public SectionRegisterSession(String lineId, String namePattern, String startSignalId) {
        this.lineId = lineId;
        this.namePattern = namePattern;
        this.startSignalId = startSignalId;
    }
}
