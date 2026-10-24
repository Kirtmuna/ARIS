package jp.apple.aris.web;

import com.sun.net.httpserver.HttpServer;
import jp.apple.aris.ArisCore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class ArisWebServer {
    private static final int DEFAULT_PORT = 8080;
    private static HttpServer server;
    private static int boundPort = DEFAULT_PORT;

    public static void start() { start(DEFAULT_PORT); }

    public static void start(int port) {
        if (server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            // 認証系は先に登録（HttpServer は最長一致プレフィックスで解決）
            server.createContext("/api/login",  new WebLoginHandler());
            server.createContext("/api/logout", new WebLoginHandler());
            server.createContext("/api/me",     new WebLoginHandler());
            server.createContext("/api/",       new WebApiHandler());
            server.createContext("/",           new WebStaticHandler());
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            boundPort = port;
            ArisCore.LOGGER.info("ARIS: Web server started on port {}", port);
            ArisCore.LOGGER.info("ARIS: Web UI -> http://<server-ip>:{}/  (ゲーム内 /aris web でログインコード取得)", port);
        } catch (IOException e) {
            ArisCore.LOGGER.error("ARIS: Failed to start web server", e);
        }
    }

    public static int getPort() { return boundPort; }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
}
