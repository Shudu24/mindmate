package mindmate.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import mindmate.dao.SupportDAO;
import mindmate.dao.SyncDAO;
import mindmate.dao.UserDAO;
import mindmate.model.Checkin;
import mindmate.model.JournalEntry;
import mindmate.model.SupportContact;
import mindmate.model.User;
import mindmate.util.Json;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Every /api route.
 *
 *   POST /api/register   {username, password, displayName}
 *   POST /api/login      {username, password}          -> {token, user}
 *   POST /api/logout     Authorization: Bearer <token>
 *   GET  /api/me         Authorization: Bearer <token>
 *   GET  /api/sync?since=<ms>                          -> changes since then
 *   POST /api/sync       {checkins:[...], journal:[...]}
 *   GET  /api/support                                  -> verified contacts only
 *   GET  /api/health
 *
 * Sync is opt-in on the client. A student who never signs in keeps everything
 * on their own device, which is the default the research asked for.
 */
public class ApiHandler implements HttpHandler {

    private final UserDAO users = new UserDAO();
    private final SyncDAO sync = new SyncDAO();
    private final SupportDAO support = new SupportDAO();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        try {
            switch (path) {
                case "/api/health":   health(ex);              return;
                case "/api/register": if (post(ex, method)) register(ex); return;
                case "/api/login":    if (post(ex, method)) login(ex);    return;
                case "/api/logout":   if (post(ex, method)) logout(ex);   return;
                case "/api/me":       if (get(ex, method))  me(ex);       return;
                case "/api/support":  if (get(ex, method))  supportList(ex); return;
                case "/api/sync":
                    if ("GET".equals(method))  { pull(ex); return; }
                    if ("POST".equals(method)) { push(ex); return; }
                    Http.error(ex, 405, "Use GET or POST");
                    return;
                default:
                    Http.error(ex, 404, "No such endpoint: " + path);
            }
        } catch (SQLException sqlEx) {
            System.err.println("Database error on " + path + ": " + sqlEx.getMessage());
            Http.error(ex, 500, "The server could not reach its database.");
        } catch (IllegalArgumentException badInput) {
            Http.error(ex, 400, badInput.getMessage());
        } catch (Exception other) {
            System.err.println("Unhandled error on " + path + ": " + other);
            Http.error(ex, 500, "Something went wrong on the server.");
        } finally {
            ex.close();
        }
    }

    private boolean post(HttpExchange ex, String m) throws IOException {
        if ("POST".equals(m)) return true;
        Http.error(ex, 405, "Use POST");
        return false;
    }

    private boolean get(HttpExchange ex, String m) throws IOException {
        if ("GET".equals(m)) return true;
        Http.error(ex, 405, "Use GET");
        return false;
    }

    // ------------------------------------------------------------ routes

    private void health(HttpExchange ex) throws IOException, SQLException {
        Map<String, Object> m = Json.map();
        m.put("status", "ok");
        m.put("supportContactsAwaitingVerification", support.unverifiedCount());
        Http.ok(ex, m);
    }

    private void register(HttpExchange ex) throws IOException, SQLException {
        Map<String, Object> in = Json.parseObject(Http.readBody(ex));
        String username = Json.str(in, "username", "").trim();
        String password = Json.str(in, "password", "");
        String display = Json.str(in, "displayName", "").trim();

        if (username.length() < 3 || username.length() > 50) {
            Http.error(ex, 400, "Username must be 3 to 50 characters."); return;
        }
        if (!username.matches("[A-Za-z0-9._-]+")) {
            Http.error(ex, 400, "Username may use letters, numbers, dot, dash and underscore."); return;
        }
        if (password.length() < 8) {
            Http.error(ex, 400, "Password must be at least 8 characters."); return;
        }
        if (display.isEmpty()) display = username;
        if (display.length() > 100) {
            Http.error(ex, 400, "Display name is too long."); return;
        }
        if (users.usernameTaken(username)) {
            Http.error(ex, 409, "That username is already taken."); return;
        }

        User u = users.register(username, password, display);
        String token = users.startSession(u.getUserId());

        Map<String, Object> out = Json.map();
        out.put("token", token);
        out.put("user", userJson(u));
        Http.json(ex, 201, out);
    }

    private void login(HttpExchange ex) throws IOException, SQLException {
        Map<String, Object> in = Json.parseObject(Http.readBody(ex));
        String username = Json.str(in, "username", "").trim();
        String password = Json.str(in, "password", "");

        User u = users.authenticate(username, password);
        if (u == null) {
            /* One message for both cases, so the response never reveals which
               usernames exist. */
            Http.error(ex, 401, "Incorrect username or password.");
            return;
        }
        String token = users.startSession(u.getUserId());

        Map<String, Object> out = Json.map();
        out.put("token", token);
        out.put("user", userJson(u));
        Http.ok(ex, out);
    }

    private void logout(HttpExchange ex) throws IOException, SQLException {
        users.endSession(Http.bearer(ex));
        Map<String, Object> out = Json.map();
        out.put("ok", true);
        Http.ok(ex, out);
    }

    private void me(HttpExchange ex) throws IOException, SQLException {
        User u = requireUser(ex);
        if (u == null) return;
        Http.ok(ex, userJson(u));
    }

    private void supportList(HttpExchange ex) throws IOException, SQLException {
        List<SupportContact> rows = support.verifiedOnly();
        List<Object> items = new ArrayList<>();
        for (SupportContact s : rows) items.add(s.toJson());

        Map<String, Object> out = Json.map();
        out.put("contacts", items);
        out.put("awaitingVerification", support.unverifiedCount());
        /* Said plainly so the client can show an honest message rather than an
           empty screen that looks broken. */
        out.put("note", items.isEmpty()
                ? "No contact has been verified yet, so none can be shown."
                : null);
        Http.ok(ex, out);
    }

    private void pull(HttpExchange ex) throws IOException, SQLException {
        User u = requireUser(ex);
        if (u == null) return;

        long since = 0L;
        String raw = Http.queryParams(ex).get("since");
        if (raw != null && !raw.isEmpty()) {
            try { since = Long.parseLong(raw.trim()); }
            catch (NumberFormatException ignored) { since = 0L; }
        }

        List<Object> checkins = new ArrayList<>();
        for (Checkin c : sync.checkinsSince(u.getUserId(), since)) checkins.add(c.toJson());

        List<Object> journal = new ArrayList<>();
        for (JournalEntry e : sync.journalSince(u.getUserId(), since)) journal.add(e.toJson());

        Map<String, Object> out = Json.map();
        out.put("checkins", checkins);
        out.put("journal", journal);
        out.put("serverTime", System.currentTimeMillis());
        Http.ok(ex, out);
    }

    private void push(HttpExchange ex) throws IOException, SQLException {
        User u = requireUser(ex);
        if (u == null) return;

        Map<String, Object> in = Json.parseObject(Http.readBody(ex));

        List<Checkin> checkins = new ArrayList<>();
        for (Object o : Json.list(in, "checkins")) {
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) o;
                checkins.add(Checkin.fromJson(row));
            }
        }

        List<JournalEntry> journal = new ArrayList<>();
        for (Object o : Json.list(in, "journal")) {
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) o;
                journal.add(JournalEntry.fromJson(row));
            }
        }

        SyncDAO.Result cr = sync.pushCheckins(u.getUserId(), checkins);
        SyncDAO.Result jr = sync.pushJournal(u.getUserId(), journal);

        Map<String, Object> out = Json.map();
        out.put("checkinsApplied", cr.applied);
        out.put("checkinsSkipped", cr.skipped);
        out.put("journalApplied", jr.applied);
        out.put("journalSkipped", jr.skipped);
        out.put("serverTime", System.currentTimeMillis());
        Http.ok(ex, out);
    }

    // ------------------------------------------------------------ helpers

    /** Sends 401 and returns null when the caller is not signed in. */
    private User requireUser(HttpExchange ex) throws IOException, SQLException {
        User u = users.userForToken(Http.bearer(ex));
        if (u == null) {
            Http.error(ex, 401, "Sign in first.");
            return null;
        }
        return u;
    }

    private Map<String, Object> userJson(User u) {
        Map<String, Object> m = Json.map();
        m.put("userId", u.getUserId());
        m.put("username", u.getUsername());
        m.put("displayName", u.getDisplayName());
        return m;
    }
}
