package jp.apple.aris.web;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class WebLoginHandler implements HttpHandler {
    private static final Gson GSON = new Gson();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if ("POST".equals(ex.getRequestMethod())) {
                if ("/api/login".equals(path))  { handleLogin(ex);  return; }
                if ("/api/logout".equals(path)) { handleLogout(ex); return; }
            } else if ("GET".equals(ex.getRequestMethod())) {
                if ("/api/me".equals(path)) { handleMe(ex); return; }
            }
            sendJson(ex, 404, err("unknown"));
        } catch (Exception e) {
            sendJson(ex, 500, err(e.toString()));
        }
    }

    private void handleLogin(HttpExchange ex) throws IOException {
        String body = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> req = GSON.fromJson(body, Map.class);
        if (req == null) { sendJson(ex, 400, err("invalid json")); return; }

        Object mcidObj = req.get("mcid");
        Object codeObj = req.get("code");
        if (mcidObj == null || codeObj == null) {
            sendJson(ex, 400, err("mcid/code required"));
            return;
        }
        String mcid = String.valueOf(mcidObj).trim();
        String code = String.valueOf(codeObj).trim();

        String sid = ArisAuth.login(mcid, code);
        if (sid == null) { sendJson(ex, 401, err("invalid mcid or code")); return; }

        ArisAuth.setSessionCookie(ex, sid);
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        r.put("mcid", mcid);
        sendJson(ex, 200, r);
    }

    private void handleLogout(HttpExchange ex) throws IOException {
        String sid = ArisAuth.extractCookie(ex);
        ArisAuth.logout(sid);
        ArisAuth.clearSessionCookie(ex);
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        sendJson(ex, 200, r);
    }

    private void handleMe(HttpExchange ex) throws IOException {
        String mcid = ArisAuth.checkSession(ArisAuth.extractCookie(ex));
        if (mcid == null) { sendJson(ex, 401, err("not logged in")); return; }
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        r.put("mcid", mcid);
        sendJson(ex, 200, r);
    }

    private byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
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
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }
}
