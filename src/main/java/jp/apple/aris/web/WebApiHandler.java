package jp.apple.aris.web;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import jp.apple.aris.cbi.Cbi;
import jp.apple.aris.ctc.config.LineConfig;
import jp.apple.aris.ctc.config.LineManager;
import jp.apple.aris.ctc.network.ServerLineSyncHandler;
import jp.apple.aris.ctc.state.*;
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
                case "/api/state":
                    result = stateSnapshot();
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
            case "/api/switch/update":
                result = updateSwitchPoint(req);
                break;
            case "/api/section/update":
                result = updateSection(req);
                break;
            case "/api/section/create":
                result = createSection(req);
                break;
            case "/api/section/rename":
                result = renameSection(req);
                break;
            case "/api/section/delete":
                result = deleteSection(req);
                break;
            case "/api/route/update":
                result = updateRoute(req);
                break;
            case "/api/route/create":
                result = createRoute(req);
                break;
            case "/api/route/rename":
                result = renameRoute(req);
                break;
            case "/api/route/delete":
                result = deleteRoute(req);
                break;
            case "/api/route/request":
                result = requestRoute(req);
                break;
            case "/api/route/release":
                result = releaseRoute(req);
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
        List<Map<String, Object>> pointMarks = new ArrayList<>();

        for (Map<String, Object> sw : ArisMapCache.get().getSwitchKeys()) {
            String switchKey = (String) sw.get("key");
            Object ptsObj = sw.get("points");
            if (!(ptsObj instanceof List)) continue;
            for (Object po : (List<?>) ptsObj) {
                Map<String, Object> pm = (Map<String, Object>) po;
                Map<String, Object> out = new HashMap<>();
                out.put("switchId", switchKey);
                out.put("pointKey", pm.get("pointKey"));
                out.put("index", pm.get("index"));
                out.put("pos", pm.get("pos"));
                out.put("yaw", pm.get("yaw"));
                pointMarks.add(out);
            }
        }

        LineConfig cfg = LineManager.getConfig();
        if (cfg != null) {
            if (cfg.sections != null) sectionIds.addAll(cfg.sections.keySet());
            if (cfg.routes != null) routeIds.addAll(cfg.routes.keySet());
        }
        m.put("sections", sectionIds);
        m.put("routes", routeIds);
        m.put("lineConfig", cfg);
        m.put("points", pointMarks);
        return m;
    }

    private Map<String, Object> updateSignal(Map<String, Object> req) {
        String key = (String) req.get("key");
        String type = (String) req.get("type");

        Map<String, Object> r = new HashMap<>();
        if (key == null) {
            r.put("ok", false);
            r.put("error", "key required");
            return r;
        }

        LineManager.reload();
        LineConfig cfg = LineManager.getConfig();
        if (cfg.signals == null) cfg.signals = new HashMap<>();

        if (type == null || type.isEmpty()) {
            cfg.signals.remove(key);
        } else {
            LineConfig.SignalConfig sc = new LineConfig.SignalConfig();
            sc.type = type;
            cfg.signals.put(key, sc);
        }

        LineManager.save();
        LineStateManager.initializeStates();
        ServerLineSyncHandler.broadcastLine(
                FMLCommonHandler.instance().getMinecraftServerInstance());

        r.put("ok", true);
        return r;
    }

    private Map<String, Object> updateSwitchPoint(Map<String, Object> req) {
        String switchId = (String) req.get("switchId");
        String pointKey = (String) req.get("pointKey");
        String nSignal = (String) req.get("nSignal");
        String rSignal = (String) req.get("rSignal");

        Map<String, Object> r = new HashMap<>();
        if (switchId == null || pointKey == null) {
            r.put("ok", false);
            r.put("error", "switchId/pointKey required");
            return r;
        }

        LineManager.reload();
        LineConfig cfg = LineManager.getConfig();
        if (cfg.switches == null) cfg.switches = new HashMap<>();
        if (!cfg.switches.containsKey(switchId)) {
            cfg.switches.put(switchId, new LineConfig.SwitchConfig());
            cfg.switches.get(switchId).points = new HashMap<>();
        }
        LineConfig.SwitchConfig sc = cfg.switches.get(switchId);
        if (sc.points == null) sc.points = new HashMap<>();
        if (!sc.points.containsKey(pointKey)) {
            sc.points.put(pointKey, new LineConfig.SwitchConfig.PointConfig());
        }
        LineConfig.SwitchConfig.PointConfig pc = sc.points.get(pointKey);
        pc.nSignal = (nSignal == null || nSignal.isEmpty()) ? null : nSignal;
        pc.rSignal = (rSignal == null || rSignal.isEmpty()) ? null : rSignal;

        LineManager.save();
        LineStateManager.initializeStates();
        ServerLineSyncHandler.broadcastLine(
                FMLCommonHandler.instance().getMinecraftServerInstance());

        r.put("ok", true);
        return r;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> updateSection(Map<String, Object> req) {
        String sectionId = (String) req.get("sectionId");
        String startSignal = (String) req.get("startSignal");
        String endSignal = (String) req.get("endSignal");
        Object railsObj = req.get("rails");

        Map<String, Object> r = new HashMap<>();
        if (sectionId == null) {
            r.put("ok", false);
            r.put("error", "sectionId required");
            return r;
        }

        LineManager.reload();
        LineConfig cfg = LineManager.getConfig();
        if (cfg.sections == null || !cfg.sections.containsKey(sectionId)) {
            r.put("ok", false);
            r.put("error", "section not found: " + sectionId);
            return r;
        }
        LineConfig.SectionConfig sc = cfg.sections.get(sectionId);
        sc.startSignal = (startSignal == null || startSignal.isEmpty()) ? null : startSignal;
        sc.endSignal   = (endSignal   == null || endSignal.isEmpty())   ? null : endSignal;

        if (railsObj instanceof List) {
            List<int[]> newRails = new ArrayList<>();
            for (Object o : (List<?>) railsObj) {
                List<Object> arr = (List<Object>) o;
                if (arr.size() < 3) continue;
                int x = ((Number) arr.get(0)).intValue();
                int y = ((Number) arr.get(1)).intValue();
                int z = ((Number) arr.get(2)).intValue();
                newRails.add(new int[]{x, y, z});
            }
            sc.rails = newRails.toArray(new int[0][]);
        }

        LineManager.save();
        LineStateManager.initializeStates();
        ServerLineSyncHandler.broadcastLine(
                FMLCommonHandler.instance().getMinecraftServerInstance());

        r.put("ok", true);
        return r;
    }

    private Map<String, Object> createSection(Map<String, Object> req) {
        String sectionId = (String) req.get("sectionId");

        Map<String, Object> r = new HashMap<>();
        if (sectionId == null || sectionId.isEmpty()) {
            r.put("ok", false);
            r.put("error", "sectionId required");
            return r;
        }

        LineManager.reload();
        LineConfig cfg = LineManager.getConfig();
        if (cfg.sections == null) cfg.sections = new HashMap<>();
        if (cfg.sections.containsKey(sectionId)) {
            r.put("ok", false);
            r.put("error", "section already exists: " + sectionId);
            return r;
        }

        LineConfig.SectionConfig sc = new LineConfig.SectionConfig();
        sc.rails = new int[0][];
        cfg.sections.put(sectionId, sc);

        LineManager.save();
        LineStateManager.initializeStates();
        ServerLineSyncHandler.broadcastLine(
                FMLCommonHandler.instance().getMinecraftServerInstance());

        r.put("ok", true);
        return r;
    }

    private Map<String, Object> deleteSection(Map<String, Object> req) {
        String sectionId = (String) req.get("sectionId");

        Map<String, Object> r = new HashMap<>();
        if (sectionId == null) {
            r.put("ok", false);
            r.put("error", "sectionId required");
            return r;
        }

        LineManager.reload();
        LineConfig cfg = LineManager.getConfig();
        if (cfg.sections == null || !cfg.sections.containsKey(sectionId)) {
            r.put("ok", false);
            r.put("error", "section not found: " + sectionId);
            return r;
        }
        cfg.sections.remove(sectionId);

        LineManager.save();
        LineStateManager.initializeStates();
        ServerLineSyncHandler.broadcastLine(
                FMLCommonHandler.instance().getMinecraftServerInstance());

        r.put("ok", true);
        return r;
    }

    private Map<String, Object> renameSection(Map<String, Object> req) {
        String oldId = (String) req.get("oldId");
        String newId = (String) req.get("newId");

        Map<String, Object> r = new HashMap<>();
        if (oldId == null || newId == null || newId.isEmpty()) {
            r.put("ok", false);
            r.put("error", "oldId/newId required");
            return r;
        }
        if (oldId.equals(newId)) {
            r.put("ok", true);
            return r;
        }

        LineManager.reload();
        LineConfig cfg = LineManager.getConfig();
        if (cfg.sections == null || !cfg.sections.containsKey(oldId)) {
            r.put("ok", false);
            r.put("error", "section not found: " + oldId);
            return r;
        }
        if (cfg.sections.containsKey(newId)) {
            r.put("ok", false);
            r.put("error", "section already exists: " + newId);
            return r;
        }
        LineConfig.SectionConfig sc = cfg.sections.remove(oldId);
        cfg.sections.put(newId, sc);

        LineManager.save();
        LineStateManager.initializeStates();
        ServerLineSyncHandler.broadcastLine(
                FMLCommonHandler.instance().getMinecraftServerInstance());

        r.put("ok", true);
        return r;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> updateRoute(Map<String, Object> req) {
        String routeId = (String) req.get("routeId");
        Object sectionsObj = req.get("sections");
        Object nPointObj = req.get("nPoint");
        Object rPointObj = req.get("rPoint");

        Map<String, Object> r = new HashMap<>();
        if (routeId == null) {
            r.put("ok", false);
            r.put("error", "routeId required");
            return r;
        }

        LineManager.reload();
        LineConfig cfg = LineManager.getConfig();
        if (cfg.routes == null || !cfg.routes.containsKey(routeId)) {
            r.put("ok", false);
            r.put("error", "route not found: " + routeId);
            return r;
        }
        LineConfig.RouteConfig rc = cfg.routes.get(routeId);

        if (sectionsObj instanceof List) {
            List<String> list = new ArrayList<>();
            for (Object o : (List<?>) sectionsObj) {
                if (o instanceof String) list.add((String) o);
            }
            rc.sections = list.toArray(new String[0]);
        }
        if (rc.route == null) rc.route = new LineConfig.RouteConfig.RouteSwitchConfig();
        if (nPointObj instanceof List) {
            List<String> list = new ArrayList<>();
            for (Object o : (List<?>) nPointObj) {
                if (o instanceof String) list.add((String) o);
            }
            rc.route.nPoint = list.toArray(new String[0]);
        }
        if (rPointObj instanceof List) {
            List<String> list = new ArrayList<>();
            for (Object o : (List<?>) rPointObj) {
                if (o instanceof String) list.add((String) o);
            }
            rc.route.rPoint = list.toArray(new String[0]);
        }

        LineManager.save();
        LineStateManager.initializeStates();
        ServerLineSyncHandler.broadcastLine(
                FMLCommonHandler.instance().getMinecraftServerInstance());

        r.put("ok", true);
        return r;
    }

    private Map<String, Object> createRoute(Map<String, Object> req) {
        String routeId = (String) req.get("routeId");

        Map<String, Object> r = new HashMap<>();
        if (routeId == null || routeId.isEmpty()) {
            r.put("ok", false);
            r.put("error", "routeId required");
            return r;
        }

        LineManager.reload();
        LineConfig cfg = LineManager.getConfig();
        if (cfg.routes == null) cfg.routes = new HashMap<>();
        if (cfg.routes.containsKey(routeId)) {
            r.put("ok", false);
            r.put("error", "route already exists: " + routeId);
            return r;
        }

        LineConfig.RouteConfig rc = new LineConfig.RouteConfig();
        rc.sections = new String[0];
        rc.route = new LineConfig.RouteConfig.RouteSwitchConfig();
        rc.route.nPoint = new String[0];
        rc.route.rPoint = new String[0];
        cfg.routes.put(routeId, rc);

        LineManager.save();
        LineStateManager.initializeStates();
        ServerLineSyncHandler.broadcastLine(
                FMLCommonHandler.instance().getMinecraftServerInstance());

        r.put("ok", true);
        return r;
    }

    private Map<String, Object> renameRoute(Map<String, Object> req) {
        String oldId = (String) req.get("oldId");
        String newId = (String) req.get("newId");

        Map<String, Object> r = new HashMap<>();
        if (oldId == null || newId == null || newId.isEmpty()) {
            r.put("ok", false);
            r.put("error", "oldId/newId required");
            return r;
        }
        if (oldId.equals(newId)) {
            r.put("ok", true);
            return r;
        }

        LineManager.reload();
        LineConfig cfg = LineManager.getConfig();
        if (cfg.routes == null || !cfg.routes.containsKey(oldId)) {
            r.put("ok", false);
            r.put("error", "route not found: " + oldId);
            return r;
        }
        if (cfg.routes.containsKey(newId)) {
            r.put("ok", false);
            r.put("error", "route already exists: " + newId);
            return r;
        }
        LineConfig.RouteConfig rc = cfg.routes.remove(oldId);
        cfg.routes.put(newId, rc);

        LineManager.save();
        LineStateManager.initializeStates();
        ServerLineSyncHandler.broadcastLine(
                FMLCommonHandler.instance().getMinecraftServerInstance());

        r.put("ok", true);
        return r;
    }

    private Map<String, Object> deleteRoute(Map<String, Object> req) {
        String routeId = (String) req.get("routeId");

        Map<String, Object> r = new HashMap<>();
        if (routeId == null) {
            r.put("ok", false);
            r.put("error", "routeId required");
            return r;
        }

        LineManager.reload();
        LineConfig cfg = LineManager.getConfig();
        if (cfg.routes == null || !cfg.routes.containsKey(routeId)) {
            r.put("ok", false);
            r.put("error", "route not found: " + routeId);
            return r;
        }
        cfg.routes.remove(routeId);

        LineManager.save();
        LineStateManager.initializeStates();
        ServerLineSyncHandler.broadcastLine(
                FMLCommonHandler.instance().getMinecraftServerInstance());

        r.put("ok", true);
        return r;
    }
    
    private Map<String, Object> stateSnapshot() {
        Map<String, Object> m = new HashMap<>();
        // 進路
        Map<String, Object> routes = new HashMap<>();
        Map<String, RouteState> routeMap = LineStateManager.getRoutes();
        if (routeMap != null) {
            for (Map.Entry<String, RouteState> e : routeMap.entrySet()) {
                Map<String, Object> r = new HashMap<>();
                r.put("status", e.getValue().getStatus().name());
                routes.put(e.getKey(), r);
            }
        }
        m.put("routes", routes);
        // 区間
        Map<String, Object> sections = new HashMap<>();
        Map<String, SectionState> sectionMap = LineStateManager.getSections();
        if (sectionMap != null) {
            for (Map.Entry<String, SectionState> e : sectionMap.entrySet()) {
                Map<String, Object> s = new HashMap<>();
                s.put("occupied", e.getValue().isOccupied());
                sections.put(e.getKey(), s);
            }
        }
        m.put("sections", sections);
        // 信号
        Map<String, Object> signals = new HashMap<>();
        Map<String, SignalState> signalMap = LineStateManager.getSignals();
        if (signalMap != null) {
            for (Map.Entry<String, SignalState> e : signalMap.entrySet()) {
                Map<String, Object> s = new HashMap<>();
                s.put("aspect", e.getValue().getCurrentAspect());
                signals.put(e.getKey(), s);
            }
        }
        m.put("signals", signals);
        // ポイント
        Map<String, Object> points = new HashMap<>();
        Map<String, SwitchState> pointMap = LineStateManager.getPoints();
        if (pointMap != null) {
            for (Map.Entry<String, SwitchState> e : pointMap.entrySet()) {
                Map<String, Object> p = new HashMap<>();
                p.put("switchId", e.getValue().getSwitchId());
                p.put("position", e.getValue().getCurrentPosition().name());
                points.put(e.getKey(), p);
            }
        }
        m.put("points", points);

        return m;
    }

    private Map<String, Object> requestRoute(Map<String, Object> req) {
        String routeId = (String) req.get("routeId");
        Map<String, Object> r = new HashMap<>();
        if (routeId == null) {
            r.put("ok", false);
            r.put("error", "routeId required");
            return r;
        }

        RouteState route = LineStateManager.getRoutes().get(routeId);
        if (route == null) {
            r.put("ok", false);
            r.put("error", "route not found: " + routeId);
            return r;
        }

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        WorldServer world = server != null ? server.getWorld(0) : null;
        if (world == null) {
            r.put("ok", false);
            r.put("error", "server not ready");
            return r;
        }

        boolean ok = Cbi.get().requestRoute(world, route);
        if (ok) {
            route.setStatus(RouteState.Status.SET);
            r.put("ok", true);
        } else {
            r.put("ok", false);
            r.put("error", "route busy");
        }
        return r;
    }

    private Map<String, Object> releaseRoute(Map<String, Object> req) {
        String routeId = (String) req.get("routeId");
        Map<String, Object> r = new HashMap<>();
        if (routeId == null) {
            r.put("ok", false);
            r.put("error", "routeId required");
            return r;
        }

        RouteState route = LineStateManager.getRoutes().get(routeId);
        if (route == null) {
            r.put("ok", false);
            r.put("error", "route not found: " + routeId);
            return r;
        }
        if (route.getStatus() == RouteState.Status.IDLE) {
            r.put("ok", true);
            return r;
        }

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        WorldServer world = server != null ? server.getWorld(0) : null;
        if (world == null) {
            r.put("ok", false);
            r.put("error", "server not ready");
            return r;
        }

        Cbi.get().releaseRoute(world, route);
        route.setStatus(RouteState.Status.IDLE);
        r.put("ok", true);
        return r;
    }
    
    private int findAspectAt(WorldServer world, BlockPos pos) {
        Map<String, SignalState> sigs = LineStateManager.getSignals();
        if (sigs == null) return -1;
        for (SignalState sig : sigs.values()) {
            for (BlockPos sp : sig.getSignalPositions()) {
                if (sp.equals(pos)) {
                    return sig.getCurrentAspect();
                }
            }
        }
        return -1;
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
