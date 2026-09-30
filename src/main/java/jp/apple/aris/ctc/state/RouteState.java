package jp.apple.aris.ctc.state;

import jp.apple.aris.ctc.config.LineConfig;

import java.util.ArrayList;
import java.util.List;

public class RouteState {
    private final String routeId;
    private final LineConfig.RouteConfig config;

    private final List<SectionState> sections = new ArrayList<>();
    private final List<SwitchState> normalPoints = new ArrayList<>();
    private final List<SwitchState> reversePoints = new ArrayList<>();

    public RouteState(String routeId, LineConfig.RouteConfig config,
                      List<SectionState> sections, List<SwitchState> normalPoints, List<SwitchState> reversePoints) {
        this.routeId = routeId;
        this.config = config;
        if (sections != null) this.sections.addAll(sections);
        if (normalPoints != null) this.normalPoints.addAll(normalPoints);
        if (reversePoints != null) this.reversePoints.addAll(reversePoints);
    }

    public String getRouteId() { return routeId; }
    public LineConfig.RouteConfig getConfig() { return config; }
    public List<SectionState> getSections() { return sections; }
    public List<SwitchState> getNormalPoints() { return normalPoints; }
    public List<SwitchState> getReversePoints() { return reversePoints; }
}
