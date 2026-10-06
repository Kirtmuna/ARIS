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
    // signals
    private final Map<String, double[]> signals = new LinkedHashMap<>();
    private final Map<String, Integer> signalAspects = new HashMap<>();
    // switches
    private final Map<String, SwitchEntry> switches = new LinkedHashMap<>();
    public static class SwitchEntry {
        public double[] pos;
        public int pointCount;
        public SwitchEntry() {}
        public SwitchEntry(double[] pos, int pointCount) {
            this.pos = pos;
            this.pointCount = pointCount;
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
                int pointCount = st != null && st.getPoints() != null ? st.getPoints().length : 0;

                SwitchEntry prev = switches.get(key);
                if (prev == null || prev.pointCount != pointCount) {
                    switches.put(key, new SwitchEntry(
                            new double[]{pos.getX(), pos.getY(), pos.getZ()},
                            pointCount));
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

            Object railsObj = data.get("rails");
            if (railsObj instanceof Map) {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) railsObj).entrySet()) {
                    rails.put(e.getKey(), parsePolys(e.getValue()));
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
                    switches.put(key, new SwitchEntry(arr, pc));
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
                sws.add(m);
            }
            data.put("switches", sws);

            GSON.toJson(data, w);
        } catch (IOException e) {
            ArisCore.LOGGER.error("ARIS: Failed to save map cache", e);
        }
    }
}
