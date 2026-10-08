package jp.apple.aris.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jp.apple.aris.ArisCore;
import jp.apple.aris.ctc.config.LineManager;
import jp.apple.aris.ctc.state.LineStateManager;
import jp.apple.aris.ctc.state.SignalState;
import jp.apple.aris.util.ArisDir;
import jp.ngt.rtm.electric.TileEntitySignal;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore;
import jp.ngt.rtm.rail.util.Point;
import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.rail.util.SwitchType;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ArisMapCache {
    private static ArisMapCache INSTANCE;
    public static ArisMapCache get() {
        if (INSTANCE == null) INSTANCE = new ArisMapCache();
        return INSTANCE;
    }

    private static final Gson GSON = new GsonBuilder().create();

    // rails
    private final Map<String, List<List<double[]>>> rails = new LinkedHashMap<>();
    // railKey -> groupKey
    private final Map<String, String> railGroups = new HashMap<>();
    // signals
    private final Map<String, double[]> signals = new LinkedHashMap<>();
    private final Map<String, Integer> signalAspects = new HashMap<>();
    // switches
    private final Map<String, SwitchEntry> switches = new LinkedHashMap<>();
    public static class SwitchEntry {
        public double[] pos;
        public int pointCount;
        public List<PointEntry> points = new ArrayList<>();
        public SwitchEntry() {}
        public SwitchEntry(double[] pos, int pointCount) {
            this.pos = pos;
            this.pointCount = pointCount;
        }
    }
    public static class PointEntry {
        public String key;
        public int index;
        public double[] pos;
        public double yaw;
        public PointEntry() {}
        public PointEntry(String key, int index, double[] pos, double yaw) {
            this.key = key;
            this.index = index;
            this.pos = pos;
            this.yaw = yaw;
        }
    }
    
    private int version = 0;

    public synchronized int getVersion() { return version; }
    
    public synchronized List<Map<String, Object>> getSwitchKeys() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, SwitchEntry> e : switches.entrySet()) {
            Map<String, Object> m = new HashMap<>();
            m.put("key", e.getKey());
            m.put("pos", e.getValue().pos);
            m.put("pointCount", e.getValue().pointCount);
            List<Map<String, Object>> ptsOut = new ArrayList<>();
            for (PointEntry pe : e.getValue().points) {
                Map<String, Object> pm = new HashMap<>();
                pm.put("pointKey", pe.key);
                pm.put("index", pe.index);
                pm.put("pos", pe.pos);
                pm.put("yaw", pe.yaw);
                ptsOut.add(pm);
            }
            m.put("points", ptsOut);
            list.add(m);
        }
        return list;
    }

    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> m = new HashMap<>();
        m.put("version", version);

        List<Map<String, Object>> railsOut = new ArrayList<>();
        for (Map.Entry<String, List<List<double[]>>> e : rails.entrySet()) {
            Map<String, Object> r = new HashMap<>();
            r.put("key", e.getKey());
            r.put("groupKey", railGroups.getOrDefault(e.getKey(), e.getKey()));
            r.put("polylines", e.getValue());
            railsOut.add(r);
        }
        m.put("rails", railsOut);

        List<Map<String, Object>> sigs = new ArrayList<>();
        for (Map.Entry<String, double[]> e : signals.entrySet()) {
            Map<String, Object> s = new HashMap<>();
            s.put("pos", e.getValue());
            Integer a = signalAspects.get(e.getKey());
            s.put("aspect", a != null ? a : -1);
            sigs.add(s);
        }
        m.put("signals", sigs);

        List<Map<String, Object>> sws = new ArrayList<>();
        for (Map.Entry<String, SwitchEntry> e : switches.entrySet()) {
            Map<String, Object> s = new HashMap<>();
            s.put("key", e.getKey());
            s.put("pos", e.getValue().pos);
            s.put("pointCount", e.getValue().pointCount);

            List<Map<String, Object>> ptsOut = new ArrayList<>();
            for (PointEntry pe : e.getValue().points) {
                Map<String, Object> pm = new HashMap<>();
                pm.put("pointKey", pe.key);
                pm.put("index", pe.index);
                pm.put("pos", pe.pos);
                pm.put("yaw", pe.yaw);
                ptsOut.add(pm);
            }
            s.put("points", ptsOut);
            sws.add(s);
        }
        m.put("switches", sws);
        return m;
    }

    public synchronized void updateFromWorld(WorldServer world) {
        boolean changed = false;

        Set<String> scannedRailKeys = new HashSet<>();
        Set<String> scannedSignalKeys = new HashSet<>();
        Set<String> scannedSwitchKeys = new HashSet<>();

        List<TileEntity> tes = new ArrayList<>(world.loadedTileEntityList);
        for (TileEntity te : tes) {
            if (te instanceof TileEntityLargeRailSwitchCore) {
                BlockPos pos = te.getPos();
                String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();
                scannedSwitchKeys.add(key);
                scannedRailKeys.add(key);

                TileEntityLargeRailSwitchCore core = (TileEntityLargeRailSwitchCore) te;
                SwitchType st = core.getSwitch();

                SwitchEntry prev = switches.get(key);
                SwitchEntry entry = (prev != null) ? prev : new SwitchEntry(
                        new double[]{pos.getX(), pos.getY(), pos.getZ()}, 0);
                entry.pos = new double[]{pos.getX(), pos.getY(), pos.getZ()};

                if (st != null && st.getPoints() != null) {
                    Point[] pts = st.getPoints();
                    List<PointEntry> newPoints = new ArrayList<>();
                    for (int i = 0; i < pts.length; i++) {
                        Point p = pts[i];
                        if (p == null || p.rpRoot == null) continue;
                        if (p.branchDir == jp.ngt.rtm.rail.util.RailDir.NONE) continue;
                        newPoints.add(new PointEntry(
                                "P" + i, i,
                                new double[]{p.rpRoot.posX, p.rpRoot.posY, p.rpRoot.posZ},
                                getTangentYaw(p)));
                    }
                    entry.pointCount = newPoints.size();
                    if (!deepEqualsPoints(entry.points, newPoints)) {
                        entry.points = newPoints;
                        changed = true;
                    }
                }
                if (prev == null) {
                    switches.put(key, entry);
                    changed = true;
                }
                
                String groupKey = computeGroupKey(core, key);
                String prevGroupKey = railGroups.get(key);
                if (!groupKey.equals(prevGroupKey)) {
                    railGroups.put(key, groupKey);
                    changed = true;
                }

                List<List<double[]>> polys = new ArrayList<>();
                RailMap[] maps = core.getAllRailMaps();
                if (maps != null) {
                    for (RailMap rm : maps) {
                        if (rm == null) continue;
                        int max = (int) (rm.getLength() * 2.0);
                        if (max <= 0) continue;
                        List<double[]> poly = new ArrayList<>();
                        for (int i = 0; i <= max; i++) {
                            double[] p = rm.getRailPos(max, i);
                            double h = rm.getRailHeight(max, i);
                            poly.add(new double[]{p[1], h, p[0]});
                        }
                        polys.add(poly);
                    }
                }
                List<List<double[]>> prevR = rails.get(key);
                if (!deepEquals(prevR, polys)) {
                    rails.put(key, polys);
                    changed = true;
                }
            } else if (te instanceof TileEntityLargeRailCore) {
                TileEntityLargeRailCore core = (TileEntityLargeRailCore) te;
                BlockPos pos = te.getPos();
                String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();
                scannedRailKeys.add(key);
                String groupKey = computeGroupKey(core, key);
                String prevGroupKey = railGroups.get(key);
                if (!groupKey.equals(prevGroupKey)) {
                    railGroups.put(key, groupKey);
                    changed = true;
                }

                List<List<double[]>> polys = new ArrayList<>();
                RailMap[] maps = core.getAllRailMaps();
                if (maps != null) {
                    for (RailMap rm : maps) {
                        if (rm == null) continue;
                        int max = (int) (rm.getLength() * 2.0);
                        if (max <= 0) continue;
                        List<double[]> poly = new ArrayList<>();
                        for (int i = 0; i <= max; i++) {
                            double[] p = rm.getRailPos(max, i);
                            double h = rm.getRailHeight(max, i);
                            poly.add(new double[]{p[1], h, p[0]});
                        }
                        polys.add(poly);
                    }
                }
                List<List<double[]>> prev = rails.get(key);
                if (!deepEquals(prev, polys)) {
                    rails.put(key, polys);
                    changed = true;
                }
            } else if (te instanceof TileEntitySignal) {
                BlockPos pos = te.getPos();
                String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();
                scannedSignalKeys.add(key);
                double[] p = {pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5};
                if (!signals.containsKey(key)) {
                    signals.put(key, p);
                    changed = true;
                }
            }
        }
        
        Iterator<Map.Entry<String, List<List<double[]>>>> railIt = rails.entrySet().iterator();
        while (railIt.hasNext()) {
            Map.Entry<String, List<List<double[]>>> e = railIt.next();
            if (scannedRailKeys.contains(e.getKey())) continue;
            BlockPos pos = parseKey(e.getKey());
            if (pos != null && world.isBlockLoaded(pos)) {
                railGroups.remove(e.getKey());
                railIt.remove();
                changed = true;
            }
        }

        Iterator<Map.Entry<String, double[]>> sigIt = signals.entrySet().iterator();
        while (sigIt.hasNext()) {
            Map.Entry<String, double[]> e = sigIt.next();
            if (scannedSignalKeys.contains(e.getKey())) continue;
            BlockPos pos = parseKey(e.getKey());
            if (pos != null && world.isBlockLoaded(pos)) {
                sigIt.remove();
                signalAspects.remove(e.getKey());
                changed = true;
            }
        }
        Iterator<Map.Entry<String, SwitchEntry>> swIt = switches.entrySet().iterator();
        while (swIt.hasNext()) {
            Map.Entry<String, SwitchEntry> e = swIt.next();
            if (scannedSwitchKeys.contains(e.getKey())) continue;
            BlockPos pos = parseKey(e.getKey());
            if (pos != null && world.isBlockLoaded(pos)) {
                swIt.remove();
                changed = true;
            }
        }

        // 現示の反映
        for (String lineId : LineManager.getAllLines().keySet()) {
            Map<String, SignalState> sigs =
                    LineStateManager.getSignals(lineId);
            if (sigs == null) continue;
            for (SignalState sig : sigs.values()) {
                for (BlockPos sp : sig.getSignalPositions()) {
                    String key = sp.getX() + "," + sp.getY() + "," + sp.getZ();
                    Integer prev = signalAspects.get(key);
                    int cur = sig.getCurrentAspect();
                    if (prev == null || prev != cur) {
                        signalAspects.put(key, cur);
                        changed = true;
                    }
                }
            }
        }

        if (changed) {
            version++;
            save();
        }
    }

    private BlockPos parseKey(String key) {
        String[] parts = key.split(",");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean deepEquals(List<List<double[]>> a, List<List<double[]>> b) {
        if (a == null || b == null) return a == b;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).size() != b.get(i).size()) return false;
            for (int j = 0; j < a.get(i).size(); j++) {
                double[] p = a.get(i).get(j);
                double[] q = b.get(i).get(j);
                if (p[0] != q[0] || p[1] != q[1] || p[2] != q[2]) return false;
            }
        }
        return true;
    }

    private static boolean deepEqualsPoints(List<PointEntry> a, List<PointEntry> b) {
        if (a == null || b == null) return a == b;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            PointEntry p = a.get(i);
            PointEntry q = b.get(i);
            if (!p.key.equals(q.key)) return false;
            if (p.index != q.index) return false;
            if (p.pos[0] != q.pos[0] || p.pos[1] != q.pos[1] || p.pos[2] != q.pos[2]) return false;
            if (p.yaw != q.yaw) return false;
        }
        return true;
    }
    /**
     * Point が属するレールの接線方向を返す
     */
    private static double getTangentYaw(Point p) {
        if (p.rmMain == null) {
            jp.apple.aris.ArisCore.LOGGER.info("[TANGENT] rmMain null, fallback anchorYaw={}", p.rpRoot.anchorYaw);
            return p.rpRoot.anchorYaw;
        }
        int max = (int) (p.rmMain.getLength() * 2.0);
        if (max <= 0) {
            jp.apple.aris.ArisCore.LOGGER.info("[TANGENT] max<=0, fallback anchorYaw={}", p.rpRoot.anchorYaw);
            return p.rpRoot.anchorYaw;
        }
        try {
            double[] start = p.rmMain.getRailPos(max, 0);
            double[] end   = p.rmMain.getRailPos(max, max);
            double dx = end[1] - start[1];
            double dz = end[0] - start[0];
            double yaw = Math.toDegrees(Math.atan2(-dx, dz));
            jp.apple.aris.ArisCore.LOGGER.info(
                    "[TANGENT] start=({}, {}) end=({}, {}) dx={} dz={} yaw={}",
                    start[1], start[0], end[1], end[0], dx, dz, yaw);
            if (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6) return p.rpRoot.anchorYaw;
            return yaw;
        } catch (Exception e) {
            jp.apple.aris.ArisCore.LOGGER.warn("[TANGENT] exception, fallback anchorYaw", e);
            return p.rpRoot.anchorYaw;
        }
    }
    /**
     * RailCore が属する論理グループの代表座標キーを返す
     */
    private static String computeGroupKey(TileEntityLargeRailCore core, String fallbackKey) {
        try {
            java.util.List<int[]> groupPositions = core.getRailGroupCorePositions();
            if (groupPositions != null && !groupPositions.isEmpty()) {
                int[] gp = groupPositions.get(0);
                return gp[0] + "," + gp[1] + "," + gp[2];
            }
        } catch (Exception ignored) {}
        return fallbackKey;
    }
    
    private File getCacheFile() {
        File dir = new File(ArisDir.lineDirectory.getParentFile(), "cache");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "map.json");
    }
    
    public synchronized void load() {
        File f = getCacheFile();
        if (!f.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Map<String, Object> data = GSON.fromJson(r, Map.class);
            if (data == null) return;

            rails.clear();
            signals.clear();
            signalAspects.clear();
            switches.clear();
            railGroups.clear();

            Object railsObj = data.get("rails");
            if (railsObj instanceof Map) {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) railsObj).entrySet()) {
                    rails.put(e.getKey(), parsePolys(e.getValue()));
                }
            }
            Object railsListObj = data.get("railsList");
            if (railsListObj instanceof List) {
                for (Object o : (List<?>) railsListObj) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    String key = (String) m.get("key");
                    String gk = (String) m.get("groupKey");
                    if (key != null && gk != null) {
                        railGroups.put(key, gk);
                    }
                }
            }
            Object sigObj = data.get("signals");
            if (sigObj instanceof List) {
                for (Object o : (List<?>) sigObj) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    List<Double> p = (List<Double>) m.get("pos");
                    double[] arr = {p.get(0), p.get(1), p.get(2)};
                    String key = ((int) (double) arr[0]) + "," + ((int) (double) arr[1]) + "," + ((int) (double) arr[2]);
                    signals.put(key, arr);
                    if (m.containsKey("aspect")) {
                        signalAspects.put(key, ((Number) m.get("aspect")).intValue());
                    }
                }
            }
            Object swObj = data.get("switches");
            if (swObj instanceof List) {
                for (Object o : (List<?>) swObj) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    String key = (String) m.get("key");
                    List<Double> p = (List<Double>) m.get("pos");
                    double[] arr = {p.get(0), p.get(1), p.get(2)};
                    int pc = m.containsKey("pointCount")
                            ? ((Number) m.get("pointCount")).intValue() : 0;
                    SwitchEntry entry = new SwitchEntry(arr, pc);

                    Object ptsObj = m.get("points");
                    if (ptsObj instanceof List) {
                        for (Object po : (List<?>) ptsObj) {
                            Map<String, Object> pm = (Map<String, Object>) po;
                            String pk = (String) pm.get("pointKey");
                            int pi = pm.containsKey("index")
                                    ? ((Number) pm.get("index")).intValue() : 0;
                            List<Double> pp = (List<Double>) pm.get("pos");
                            double yaw = pm.containsKey("yaw")
                                    ? ((Number) pm.get("yaw")).doubleValue() : 0.0;
                            entry.points.add(new PointEntry(pk, pi,
                                    new double[]{pp.get(0), pp.get(1), pp.get(2)}, yaw));
                        }
                    }
                    switches.put(key, entry);
                }
            }
            ArisCore.LOGGER.info("ARIS: Map cache loaded ({} rails, {} signals, {} switches)",
                    rails.size(), signals.size(), switches.size());
        } catch (Exception e) {
            ArisCore.LOGGER.error("ARIS: Failed to load map cache", e);
        }
    }
    
    private List<List<double[]>> parsePolys(Object o) {
        List<List<double[]>> result = new ArrayList<>();
        if (!(o instanceof List)) return result;
        for (Object polyObj : (List<?>) o) {
            List<double[]> poly = new ArrayList<>();
            for (Object ptObj : (List<?>) polyObj) {
                List<Double> pt = (List<Double>) ptObj;
                poly.add(new double[]{pt.get(0), pt.get(1), pt.get(2)});
            }
            result.add(poly);
        }
        return result;
    }

    public synchronized void save() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(getCacheFile()), StandardCharsets.UTF_8)) {
            Map<String, Object> data = new HashMap<>();
            data.put("rails", rails);
            
            List<Map<String, Object>> railsList = new ArrayList<>();
            for (Map.Entry<String, String> e : railGroups.entrySet()) {
                Map<String, Object> m = new HashMap<>();
                m.put("key", e.getKey());
                m.put("groupKey", e.getValue());
                railsList.add(m);
            }
            data.put("railsList", railsList);

            List<Map<String, Object>> sigs = new ArrayList<>();
            for (Map.Entry<String, double[]> e : signals.entrySet()) {
                Map<String, Object> m = new HashMap<>();
                m.put("pos", e.getValue());
                Integer a = signalAspects.get(e.getKey());
                m.put("aspect", a != null ? a : -1);
                sigs.add(m);
            }
            data.put("signals", sigs);

            List<Map<String, Object>> sws = new ArrayList<>();
            for (Map.Entry<String, SwitchEntry> e : switches.entrySet()) {
                Map<String, Object> m = new HashMap<>();
                m.put("key", e.getKey());
                m.put("pos", e.getValue().pos);
                m.put("pointCount", e.getValue().pointCount);

                List<Map<String, Object>> ptsOut = new ArrayList<>();
                for (PointEntry pe : e.getValue().points) {
                    Map<String, Object> pm = new HashMap<>();
                    pm.put("pointKey", pe.key);
                    pm.put("index", pe.index);
                    pm.put("pos", pe.pos);
                    pm.put("yaw", pe.yaw);
                    ptsOut.add(pm);
                }
                m.put("points", ptsOut);
                sws.add(m);
            }
            data.put("switches", sws);

            GSON.toJson(data, w);
        } catch (IOException e) {
            ArisCore.LOGGER.error("ARIS: Failed to save map cache", e);
        }
    }
}
