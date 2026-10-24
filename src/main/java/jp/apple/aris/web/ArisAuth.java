package jp.apple.aris.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ワンタイムコード方式の認証。
 * - プレイヤーがゲーム内で /aris web → 6桁コード発行（5分・1回限り）
 * - ブラウザで MCID + コードを入力 → セッション Cookie 発行（24時間）
 * - 以降は Cookie で認証
 * - セッションはメモリ保持（サーバー再起動で全員ログアウト → /aris web 再取得）
 */
public final class ArisAuth {
    private static final SecureRandom RNG = new SecureRandom();
    private static final String COOKIE_NAME = "aris_sid";
    private static final long CODE_TTL_MS    = 5L * 60 * 1000;         // 5分
    private static final long SESSION_TTL_MS = 24L * 60 * 60 * 1000;  // 24時間

    private static final class Entry {
        final String value;
        final long expiresAt;
        Entry(String v, long e) { value = v; expiresAt = e; }
        boolean expired() { return System.currentTimeMillis() > expiresAt; }
    }

    // mcid.toLowerCase() -> (code, expiresAt)
    private static final Map<String, Entry> CODES = new ConcurrentHashMap<>();
    // sid -> (mcid, expiresAt)
    private static final Map<String, Entry> SESSIONS = new ConcurrentHashMap<>();

    private ArisAuth() {}

    /** ゲーム内 /aris web から呼ぶ。MCID 用の使い捨てコードを発行 */
    public static String issueCode(String mcid) {
        String code = String.format("%06d", RNG.nextInt(1_000_000));
        CODES.put(mcid.toLowerCase(), new Entry(code, System.currentTimeMillis() + CODE_TTL_MS));
        return code;
    }

    /** ログイン試行。成功時 sid、失敗時 null */
    public static String login(String mcid, String code) {
        if (mcid == null || code == null) return null;
        String key = mcid.trim().toLowerCase();
        Entry e = CODES.get(key);
        if (e == null || e.expired()) {
            CODES.remove(key);
            return null;
        }
        if (!MessageDigest.isEqual(e.value.getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        CODES.remove(key);
        String sid = randomHex(32);
        SESSIONS.put(sid, new Entry(mcid, System.currentTimeMillis() + SESSION_TTL_MS));
        saveSessions();
        return sid;
    }

    public static void logout(String sid) {
        if (sid != null) {
            SESSIONS.remove(sid);
            saveSessions();
        }
    }

    /** sid から mcid を返す。無効なら null */
    public static String checkSession(String sid) {
        if (sid == null) return null;
        Entry e = SESSIONS.get(sid);
        if (e == null) return null;
        if (e.expired()) { SESSIONS.remove(sid); return null; }
        return e.value;
    }

    /** 期限切れエントリの掃除（任意） */
    public static void cleanup() {
        CODES.entrySet().removeIf(en -> en.getValue().expired());
        SESSIONS.entrySet().removeIf(en -> en.getValue().expired());
    }

    /** 認証必須ハンドラ用。成功時 mcid、失敗時 401 を返して null */
    public static String requireAuth(HttpExchange ex) throws IOException {
        String sid = extractCookie(ex);
        String mcid = checkSession(sid);
        if (mcid != null) return mcid;
        deny(ex);
        return null;
    }

    public static String extractCookie(HttpExchange ex) {
        List<String> headers = ex.getRequestHeaders().get("Cookie");
        if (headers == null) return null;
        for (String h : headers) {
            for (String part : h.split(";")) {
                String p = part.trim();
                if (p.startsWith(COOKIE_NAME + "=")) {
                    return p.substring(COOKIE_NAME.length() + 1);
                }
            }
        }
        return null;
    }

    public static void setSessionCookie(HttpExchange ex, String sid) {
        ex.getResponseHeaders().add("Set-Cookie",
                COOKIE_NAME + "=" + sid + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=86400");
    }

    public static void clearSessionCookie(HttpExchange ex) {
        ex.getResponseHeaders().add("Set-Cookie",
                COOKIE_NAME + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RNG.nextBytes(b);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static File sessionFile() {
        return new File(jp.apple.aris.ArisDir.rootDirectory, "sessions.json");
    }

    public static synchronized void loadSessions() {
        File f = sessionFile();
        if (!f.exists()) return;
        try (java.io.Reader r = new java.io.InputStreamReader(
                new java.io.FileInputStream(f), StandardCharsets.UTF_8)) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> raw = new com.google.gson.Gson().fromJson(r, java.util.Map.class);
            if (raw == null) return;
            long now = System.currentTimeMillis();
            for (java.util.Map.Entry<String, String> e : raw.entrySet()) {
                String[] parts = e.getValue().split("\\|", 2);
                if (parts.length != 2) continue;
                long exp;
                try { exp = Long.parseLong(parts[1]); } catch (NumberFormatException ex) { continue; }
                if (exp > now) SESSIONS.put(e.getKey(), new Entry(parts[0], exp));
            }
        } catch (Exception ignored) {}
    }

    private static synchronized void saveSessions() {
        java.util.Map<String, String> raw = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, Entry> e : SESSIONS.entrySet()) {
            raw.put(e.getKey(), e.getValue().value + "|" + e.getValue().expiresAt);
        }
        try (java.io.Writer w = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(sessionFile()), StandardCharsets.UTF_8)) {
            new com.google.gson.Gson().toJson(raw, w);
        } catch (Exception ignored) {}
    }

    private static void deny(HttpExchange ex) throws IOException {
        byte[] body = "Unauthorized".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(401, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }
}
