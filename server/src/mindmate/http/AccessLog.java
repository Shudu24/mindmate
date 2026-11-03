package mindmate.http;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Prints one line per request so the server's activity is visible while it
 * runs.
 *
 * What is deliberately NOT logged:
 *
 *   - request bodies, so a journal entry or a check-in note can never end up
 *     in a terminal, a scrollback buffer or a screenshot
 *   - passwords, for the same reason
 *   - tokens, which would let anyone reading the log take over a session
 *   - usernames and user ids, so the log cannot be used to reconstruct who was
 *     doing what and when
 *   - query strings, which are printed as "?..." rather than their contents
 *
 * That leaves method, path, status and how long it took, which is what is
 * actually useful when something is broken. The Phase One document commits to
 * POPIA, and a log that quietly accumulates sensitive data is one of the
 * easier ways to break that promise without noticing.
 */
public class AccessLog extends Filter {

    /** api = API calls plus failed static requests, all = everything. */
    private final String level;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    public AccessLog(String level) {
        this.level = (level == null ? "api" : level.trim().toLowerCase());
    }

    @Override
    public String description() {
        return "Logs one line per request, without request contents";
    }

    @Override
    public void doFilter(HttpExchange ex, Chain chain) throws IOException {
        long started = System.nanoTime();
        try {
            chain.doFilter(ex);
        } finally {
            long ms = (System.nanoTime() - started) / 1_000_000L;
            int status = ex.getResponseCode();
            String path = ex.getRequestURI().getPath();
            boolean api = path.startsWith("/api/");

            if (shouldLog(api, status)) {
                String query = ex.getRequestURI().getRawQuery() == null ? "" : "?...";
                System.out.printf("  %s  %-4s %-26s %s %4d ms%n",
                        LocalTime.now().format(CLOCK),
                        ex.getRequestMethod(),
                        trim(path + query, 26),
                        badge(status),
                        ms);
            }
        }
    }

    private boolean shouldLog(boolean api, int status) {
        if ("off".equals(level)) return false;
        if ("all".equals(level)) return true;
        /* Default: every API call, but only the static requests that failed.
           Logging each icon and stylesheet buries the line that matters. */
        return api || status <= 0 || status >= 400;
    }

    /** Status codes are easier to scan when the failures stand out. */
    private String badge(int status) {
        if (status <= 0) return "---";
        if (status >= 500) return status + " !!";
        if (status >= 400) return status + " ! ";
        return status + "   ";
    }

    private String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }
}
