package jp.apple.aris.ctc.state;

import jp.apple.aris.ArisCore;
import jp.apple.aris.ctc.config.LineConfig;
import jp.apple.aris.ctc.config.LineManager;

import java.util.*;

public class LineStateManager {
    private static Map<String, RailState> RAILS = new HashMap<>();
    private static Map<String, SignalState> SIGNALS = new HashMap<>();
    private static Map<String, SectionState> SECTIONS = new HashMap<>();
    private static Map<String, SwitchState> POINTS = new HashMap<>();
    private static Map<String, RouteState> ROUTES = new HashMap<>();

    /**
     * LineConfigを元にすべてのStateインスタンスを生成する
     */
    public static void initializeStates() {
        RAILS = new HashMap<>();
        SIGNALS = new HashMap<>();
        SECTIONS = new HashMap<>();
        POINTS = new HashMap<>();
        ROUTES = new HashMap<>();

        LineConfig config = LineManager.getConfig();
        if (config == null) config = new LineConfig();

        ArisCore.LOGGER.info("ARIS: 路線をインスタンス化");

        Set<String> usedAsStart = new HashSet<>();

        // 1. 信号
        if (config.signals != null) {
            for (Map.Entry<String, LineConfig.SignalConfig> e : config.signals.entrySet()) {
                String key = e.getKey();
                int[] pos = parseKey(key);
                if (pos == null) {
                    ArisCore.LOGGER.warn("ARIS: 信号 '{}' の座標キーが不正です", key);
                    continue;
                }
                SIGNALS.put(key, new SignalState(key, pos, e.getValue().type));
            }
        }
        // 2. 分岐
        if (config.switches != null) {
            for (Map.Entry<String, LineConfig.SwitchConfig> swEntry : config.switches.entrySet()) {
                String switchKey = swEntry.getKey();
                LineConfig.SwitchConfig sc = swEntry.getValue();
                int[] swPos = parseKey(switchKey);
                if (swPos == null) {
                    ArisCore.LOGGER.warn("ARIS: スイッチ '{}' の座標キーが不正です", switchKey);
                    continue;
                }
                RailState r = RAILS.computeIfAbsent(switchKey,
                        k -> new RailState(switchKey, swPos));
                if (sc.points != null) {
                    for (Map.Entry<String, LineConfig.SwitchConfig.PointConfig> ptEntry : sc.points.entrySet()) {
                        String pKey = ptEntry.getKey();
                        String pointKey = switchKey + "." + pKey;
                        int index = parsePointIndex(pKey);
                        if (index < 0) {
                            ArisCore.LOGGER.warn("ARIS: ポイントキー '{}' の形式が不正です (P<n> である必要があります)", pKey);
                            continue;
                        }
                        LineConfig.SwitchConfig.PointConfig pc = ptEntry.getValue();
                        POINTS.put(pointKey, new SwitchState(pointKey, switchKey, r, index,
                                SIGNALS.get(pc.nSignal), SIGNALS.get(pc.rSignal)));
                    }
                }
            }
        }
        // 3. 区間
        if (config.sections != null) {
            for (Map.Entry<String, LineConfig.SectionConfig> secEntry : config.sections.entrySet()) {
                String secId = secEntry.getKey();
                LineConfig.SectionConfig secConfig = secEntry.getValue();

                List<RailState> targetRails = new ArrayList<>();
                if (secConfig.rails != null) {
                    for (int[] pos : secConfig.rails) {
                        String railKey = key(pos);
                        RailState rState = RAILS.computeIfAbsent(railKey,
                                k -> new RailState(railKey, pos));
                        targetRails.add(rState);
                    }
                }

                SignalState startSig = SIGNALS.get(secConfig.startSignal);
                SignalState endSig = SIGNALS.get(secConfig.endSignal);
                SwitchState endPt = null;
                if (endSig == null && secConfig.endSignal != null) {
                    endPt = POINTS.get(secConfig.endSignal);
                    if (endPt == null) {
                        ArisCore.LOGGER.warn("ARIS: 区間 '{}' の endSignal '{}' は信号にもポイントにも見つかりません",
                                secId, secConfig.endSignal);
                    }
                } else if (endSig != null && POINTS.containsKey(secConfig.endSignal)) {
                    ArisCore.LOGGER.warn("ARIS: ID '{}' が信号とポイントで重複しています。信号を優先します", secConfig.endSignal);
                }
                SECTIONS.put(secId, new SectionState(secId, secConfig, targetRails, startSig, endSig, endPt));

                if (secConfig.startSignal != null) {
                    usedAsStart.add(secConfig.startSignal);
                }
            }
        }
        // 4. 進路
        if (config.routes != null) {
            for (Map.Entry<String, LineConfig.RouteConfig> rtEntry : config.routes.entrySet()) {
                String routeId = rtEntry.getKey();
                LineConfig.RouteConfig rc = rtEntry.getValue();

                List<SectionState> sections = new ArrayList<>();
                if (rc.sections != null) {
                    for (String secId : rc.sections) {
                        SectionState s = SECTIONS.get(secId);
                        if (s == null) {
                            ArisCore.LOGGER.warn("ARIS: 進路 '{}' のsection '{}' が見つかりません", routeId, secId);
                            continue;
                        }
                        sections.add(s);
                    }
                }

                List<SwitchState> nPoints = new ArrayList<>();
                List<SwitchState> rPoints = new ArrayList<>();
                if (rc.route != null) {
                    if (rc.route.nPoint != null) {
                        for (String pid : rc.route.nPoint) {
                            SwitchState p = POINTS.get(pid);
                            if (p == null) {
                                ArisCore.LOGGER.warn("ARIS: 進路 '{}' のpoint(N) '{}' が見つかりません", routeId, pid);
                                continue;
                            }
                            nPoints.add(p);
                        }
                    }
                    if (rc.route.rPoint != null) {
                        for (String pid : rc.route.rPoint) {
                            SwitchState p = POINTS.get(pid);
                            if (p == null) {
                                ArisCore.LOGGER.warn("ARIS: 進路 '{}' のpoint(R) '{}' が見つかりません", routeId, pid);
                                continue;
                            }
                            rPoints.add(p);
                        }
                    }
                }

                ROUTES.put(routeId, new RouteState(routeId, rc, sections, nPoints, rPoints));
            }
        }

        for (Map.Entry<String, SignalState> sigEntry : SIGNALS.entrySet()) {
            if (!usedAsStart.contains(sigEntry.getKey())) {
                sigEntry.getValue().setControlled(false);
                ArisCore.LOGGER.info("ARIS: 信号 '{}' はどの区間からも制御されません(停止現示固定)", sigEntry.getKey());
            }
        }

        ArisCore.LOGGER.info("ARIS: 路線のインスタンス化が完了 (区間数: {})", SECTIONS.size());
    }

    public static Map<String, SectionState> getSections() { return SECTIONS; }
    public static Map<String, RailState> getRails() { return RAILS; }
    public static Map<String, SignalState> getSignals() { return SIGNALS; }
    public static Map<String, SwitchState> getPoints() { return POINTS; }
    public static Map<String, RouteState> getRoutes() { return ROUTES; }

    private static String key(int[] pos) {
        return pos[0] + "," + pos[1] + "," + pos[2];
    }

    private static int[] parseKey(String key) {
        String[] p = key.split(",");
        if (p.length != 3) return null;
        try {
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }
    private static int parsePointIndex(String pKey) {
        if (pKey == null || pKey.length() < 2 || pKey.charAt(0) != 'P') return -1;
        try {
            return Integer.parseInt(pKey.substring(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
