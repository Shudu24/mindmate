package mindmate;

import com.sun.net.httpserver.HttpServer;
import mindmate.dao.SupportDAO;
import mindmate.db.Database;
import mindmate.http.AccessLog;
import mindmate.http.ApiHandler;
import mindmate.http.StaticHandler;

import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.concurrent.Executors;


public final class Main {

    public static void main(String[] args) {
        System.out.println("================================================");
        System.out.println(" Mindmate server");
        System.out.println("================================================");

        Database.loadSettings();

        try {
            Database.verify();
        } catch (SQLException ex) {
            System.err.println();
            System.err.println("  Could not reach the database: " + ex.getMessage());
            System.err.println();
            System.err.println("  Check that MySQL is running, that schema.sql has been");
            System.err.println("  run, and that db.properties has the right password.");
            System.exit(1);
            return;
        }

        int port = Database.serverPort();
        String webroot = Database.webroot();

        try {
            StaticHandler statics = new StaticHandler(webroot);
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            AccessLog log = new AccessLog(Database.logLevel());
            server.createContext("/api", new ApiHandler()).getFilters().add(log);
            server.createContext("/", statics).getFilters().add(log);

            /* A small pool is plenty here and keeps one slow database call from
               blocking every other request, which a single thread would. */
            server.setExecutor(Executors.newFixedThreadPool(8));
            server.start();

            System.out.println("  Web root  : " + statics.root());
            System.out.println("  Listening : http://localhost:" + port);

            try {
                int pending = new SupportDAO().unverifiedCount();
                if (pending > 0) {
                    System.out.println();
                    System.out.println("  NOTE: " + pending + " support contact(s) are not verified yet and");
                    System.out.println("        will not be shown in the app. See docs/SUPPORT-CONTENT.md.");
                }
            } catch (SQLException ignored) {
                // not worth failing startup over a warning
            }

            System.out.println();
            System.out.println("  Request log: " + Database.logLevel()
                    + "  (no bodies, passwords, tokens or usernames are logged)");
            System.out.println("  Press Ctrl+C to stop.");
            System.out.println();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n  Stopping...");
                server.stop(1);
            }));

        } catch (Exception ex) {
            System.err.println("  Could not start the server: " + ex.getMessage());
            System.exit(1);
        }
    }
}
