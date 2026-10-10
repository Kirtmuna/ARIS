package jp.apple.aris.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class WebStaticHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) path = "/index.html";

        String resourcePath = "/assets/aris/web" + path;
        InputStream in = WebStaticHandler.class.getResourceAsStream(resourcePath);
        if (in == null) {
            byte[] body = "Not Found".getBytes("UTF-8");
            ex.sendResponseHeaders(404, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
            return;
        }

        byte[] data;
        try (InputStream is = in; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            data = bos.toByteArray();
        }

        ex.getResponseHeaders().set("Content-Type", contentType(path));
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }
}
