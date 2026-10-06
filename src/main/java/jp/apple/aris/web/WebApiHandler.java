package jp.apple.aris.web;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import jp.apple.aris.ctc.config.LineConfig;
import jp.apple.aris.ctc.config.LineManager;
import jp.apple.aris.ctc.network.ServerLineSyncHandler;
import jp.apple.aris.ctc.state.LineStateManager;
import jp.apple.aris.ctc.state.SignalState;
import jp.ngt.rtm.electric.TileEntitySignal;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore;
import jp.ngt.rtm.rail.util.Point;
import jp.ngt.rtm.rail.util.SwitchType;
import jp.ngt.rtm.rail.util.RailMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WebApiHandler implements HttpHandler {
    private static final Gson GSON = new Gson();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        try {
            if ("POST".equals(method)) {
                handlePost(ex, path);
                return;
            }

            Object result;
            switch (path) {
                case "/api/health":
                    result = health();
                    break;
                case "/api/refresh":
                    result = refresh();
                    break;
                case "/api/map/version":
                    result = mapVersion();
                    break;
                case "/api/map":
                    result = mapSnapshot();
                    break;
                default:
                    sendJson(ex, 404, err("unknown endpoint"));
                    return;
            }
            sendJson(ex, 200, result);
        } catch (Exception e) {
            sendJson(ex, 500, err(e.toString()));
        }
    }

    private void handlePost(HttpExchange ex, String path) throws IOException {
        String body = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8);
        Map<String, Object> req;
        try {
            req = GSON.fromJson(body, Map.class);
        } catch (Exception e) {
            sendJson(ex, 400, err("invalid json"));
            return;
        }

        Object result;
        switch (path) {
            case "/api/signal/update":
                result = updateSignal(req);
                break;
            default:
                sendJson(ex, 404, err("unknown endpoint"));
                return;
        }
        sendJson(ex, 200, result);
    }

    private byte[] readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private Map<String, Object> health() {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        return m;
    }

    private Map<String, Object> refresh() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        List<List<double[]>> rails = new ArrayList<>();
        List<Map<String, Object>> signals = new ArrayList<>();

        if (server == null) {
            Map<String, Object> r = new HashMap<>();
            r.put("rails", rails);
            r.put("signals", signals);
            return r;
        }

        WorldServer world = server.getWorld(0);
        List<TileEntity> tes = new ArrayList<>(world.loadedTileEntityList);

        for (TileEntity te : tes) {
            if (te instanceof TileEntityLargeRailCore) {
                TileEntityLargeRailCore core = (TileEntityLargeRailCore) te;
                RailMap[] maps = core.getAllRailMaps();
                if (maps == null) continue;
                for (RailMap rm : maps) {
                    if (rm == null) continue;
                    List<double[]> poly = new ArrayList<>();
                    int max = (int) (rm.getLength() * 2.0);
                    if (max <= 0) continue;
                    for (int i = 0; i <= max; i++) {
                        double[] p = rm.getRailPos(max, i);
                        double h = rm.getRailHeight(max, i);
                        poly.add(new double[]{p[1], h, p[0]});
                    }
                    rails.add(poly);
                }
            }
        }

        for (TileEntity te : tes) {
            if (!(te instanceof TileEntitySignal)) continue;
            BlockPos pos = te.getPos();
            int aspect = findAspectAt(world, pos);
            Map<String, Object> s = new HashMap<>();
            s.put("pos", new double[]{pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5});
            s.put("aspect", aspect);
            signals.add(s);
        }

        Map<String, Object> r = new HashMap<>();
        r.put("rails", rails);
        r.put("signals", signals);
        return r;
    }

    private Map<String, Object> mapVersion() {
        Map<String, Object> m = new HashMap<>();
        m.put("version", ArisMapCache.get().getVersion());
        return m;
    }

    private Map<String, Object> mapSnapshot() {
        Map<String, Object> m = ArisMapCache.get().snapshot();

        List<String> sectionIds = new ArrayList<>();
        List<String> routeIds = new ArrayList<>();
        Map<String, Object> lineConfigs = new HashMap<>();
        List<Map<String, Object>> pointMarks = new ArrayList<>();

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        WorldServer world = server != null ? server.getWorld(0) : null;

        for (String lineId : LineManager.getAllLines().keySet()) {
            LineConfig cfg = LineManager.getLine(lineId);
            if (cfg == null) continue;
            if (cfg.sections != null) sectionIds.addAll(cfg.sections.keySet());
            if (cfg.routes != null) routeIds.addAll(cfg.routes.keySet());
            lineConfigs.put(lineId, cfg);
            
            if (cfg.switches == null) cfg.switches = new HashMap<>();

            Set<String> handledKeys = new LinkedHashSet<>(cfg.switches.keySet());
            for (Map<String, Object> sw : ArisMapCache.get().getSwitchKeys()) {
                handledKeys.add((String) sw.get("key"));
            }

            for (String switchKey : handledKeys) {
                int[] swPos = parseCoordKey(switchKey);
                if (swPos == null) continue;

                BlockPos swBlockPos = new BlockPos(swPos[0], swPos[1], swPos[2]);
                if (world == null || !world.isBlockLoaded(swBlockPos)) continue;
                TileEntity te = world.getTileEntity(swBlockPos);
                if (!(te instanceof TileEntityLargeRailSwitchCore)) continue;

                TileEntityLargeRailSwitchCore core =
                        (TileEntityLargeRailSwitchCore) te;
                SwitchType st = core.getSwitch();
                if (st == null) continue;
                Point[] points = st.getPoints();
                if (points == null) continue;

                for (int i = 0; i < points.length; i++) {
                    Point p = points[i];
                    if (p == null || p.rpRoot == null) continue;
                    Map<String, Object> pm = new HashMap<>();
                    pm.put("switchId", switchKey);
                    pm.put("pointKey", "P" + i);
                    pm.put("index", i);
                    pm.put("pos", new double[]{
                            p.rpRoot.posX,
                            p.rpRoot.posY,
                            p.rpRoot.posZ
                    });
                    pointMarks.add(pm);
                }
            }
        }
        m.put("sections", sectionIds);
        m.put("routes", routeIds);
        m.put("lineConfigs", lineConfigs);
        m.put("points", pointMarks);
        return m;
    }
    
    private Map<String, Object> updateSignal(Map<String, Object> req) {
        String lineId = (String) req.get("lineId");
        String key = (String) req.get("key");
        String type = (String) req.get("type");

        Map<String, Object> r = new HashMap<>();
        if (lineId == null || key == null) {
            r.put("ok", false);
            r.put("error", "lineId/key required");
            return r;
        }

        LineManager.reloadLine(lineId);
        LineConfig cfg = LineManager.getLine(lineId);
        if (cfg == null) {
            r.put("ok", false);
            r.put("error", "line not found: " + lineId);
            return r;
        }

        if (type == null || type.isEmpty()) {
            if (cfg.signals != null) {
                cfg.signals.remove(key);
            }
        } else {
            if (cfg.signals == null) cfg.signals = new HashMap<>();
            LineConfig.SignalConfig sc = new LineConfig.SignalConfig();
            sc.type = type;
            cfg.signals.put(key, sc);
        }

        LineManager.saveLine(lineId);
        LineStateManager.initializeStates();
        ServerLineSyncHandler.broadcastLineList(
                FMLCommonHandler.instance().getMinecraftServerInstance());

        r.put("ok", true);
        return r;
    }
    
    private int findAspectAt(WorldServer world, BlockPos pos) {
        for (String lineId : LineManager.getAllLines().keySet()) {
            Map<String, SignalState> sigs =
                    LineStateManager.getSignals(lineId);
            if (sigs == null) continue;
            for (SignalState sig : sigs.values()) {
                for (BlockPos sp : sig.getSignalPositions()) {
                    if (sp.equals(pos)) {
                        return sig.getCurrentAspect();
                    }
                }
            }
        }
        return -1;
    }

    private int[] parseCoordKey(String key) {
        String[] parts = key.split(",");
        if (parts.length != 3) return null;
        try {
            return new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> err(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("ok", false);
        m.put("error", msg);
        return m;
    }

    private void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] data = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }
}
