package mindmate.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves the progressive web app itself - the HTML, CSS, JavaScript, icons and
 * the service worker - so one Java process serves the whole thing and the
 * front end is same-origin with the API.
 */
public class StaticHandler implements HttpHandler {

    private final Path root;

    public StaticHandler(String webroot) throws IOException {
        this.root = new File(webroot).getCanonicalFile().toPath();
    }

    public Path root() { return root; }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod()) && !"HEAD".equals(ex.getRequestMethod())) {
                Http.error(ex, 405, "Use GET");
                return;
            }

            String urlPath = ex.getRequestURI().getPath();
            if (urlPath.endsWith("/")) urlPath += "index.html";

            Path target = root.resolve(urlPath.substring(1)).normalize();

            /* Path traversal guard. Without this, a request for
               /../../db.properties would hand out the database password. */
            if (!target.startsWith(root)) {
                Http.error(ex, 403, "Forbidden");
                return;
            }

            /* Defence in depth. The web root holds only the app, but an
               allow-list means that even if source or config were ever copied
               in by mistake, it still could not be downloaded. */
            if (Files.isRegularFile(target) && !servable(target)) {
                Http.error(ex, 403, "Forbidden");
                return;
            }

            if (!Files.isRegularFile(target)) {
                /* Unknown path: hand back the app shell so hash routes still
                   work if someone refreshes on one. */
                Path shell = root.resolve("index.html");
                if (Files.isRegularFile(shell)) {
                    send(ex, shell);
                } else {
                    Http.error(ex, 404, "Not found");
                }
                return;
            }

            send(ex, target);
        } finally {
            ex.close();
        }
    }

    private void send(HttpExchange ex, Path file) throws IOException {
        byte[] body = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", contentType(file));
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");

        /* The service worker must never be served stale, or a phone keeps an
           old cache forever. Everything else may be revalidated normally. */
        if (file.getFileName().toString().equals("sw.js")) {
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
        }

        if ("HEAD".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(200, -1);
            return;
        }
        ex.sendResponseHeaders(200, body.length);
        try (java.io.OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    /** Only the file types the app itself is made of may be served. */
    private boolean servable(Path file) {
        String n = file.getFileName().toString().toLowerCase();
        if (n.startsWith(".")) return false;
        return n.endsWith(".html") || n.endsWith(".css") || n.endsWith(".js")
            || n.endsWith(".json") || n.endsWith(".webmanifest")
            || n.endsWith(".svg") || n.endsWith(".png")
            || n.endsWith(".jpg") || n.endsWith(".jpeg")
            || n.endsWith(".ico") || n.endsWith(".woff2") || n.endsWith(".txt");
    }

    private String contentType(Path file) {
        String n = file.getFileName().toString().toLowerCase();
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".css"))  return "text/css; charset=utf-8";
        if (n.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (n.endsWith(".json") || n.endsWith(".webmanifest")) return "application/manifest+json; charset=utf-8";
        if (n.endsWith(".svg"))  return "image/svg+xml";
        if (n.endsWith(".png"))  return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".md"))   return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }
}
