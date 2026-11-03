package mindmate.http;

import com.sun.net.httpserver.HttpExchange;
import mindmate.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Small helpers shared by the request handlers. */
public final class Http {

    /** Refuse oversized bodies rather than reading them into memory. */
    private static final int MAX_BODY = 2 * 1024 * 1024;

    private Http() { }

    public static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] bytes = in.readNBytes(MAX_BODY + 1);
            if (bytes.length > MAX_BODY) {
                throw new IOException("Request body too large");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    public static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    public static void json(HttpExchange ex, int status, Object payload) throws IOException {
        send(ex, status, "application/json; charset=utf-8",
             Json.write(payload).getBytes(StandardCharsets.UTF_8));
    }

    public static void ok(HttpExchange ex, Object payload) throws IOException {
        json(ex, 200, payload);
    }

    public static void error(HttpExchange ex, int status, String message) throws IOException {
        Map<String, Object> m = Json.map();
        m.put("error", message);
        json(ex, status, m);
    }

    /** The bearer token from the Authorization header, or null. */
    public static String bearer(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h == null) return null;
        h = h.trim();
        if (!h.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = h.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    public static Map<String, String> queryParams(HttpExchange ex) {
        Map<String, String> out = new HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q == null || q.isEmpty()) return out;
        for (String pair : q.split("&")) {
            int eq = pair.indexOf('=');
            try {
                if (eq < 0) {
                    out.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                } else {
                    out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                }
            } catch (IllegalArgumentException ignored) {
                // a malformed pair is simply not offered to the handler
            }
        }
        return out;
    }
}
