package mindmate.db;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * The only class that opens JDBC connections. Settings come from
 * db.properties sitting beside the program, so the database can move or
 * the password change without recompiling anything.
 */
public final class Database {

    private static String host = "localhost";
    private static String port = "3306";
    private static String database = "mindmate_db";
    private static String user = "root";
    private static String password = "";

    private static int serverPort = 8080;
    private static String webroot = "..";
    private static String logLevel = "api";

    private static boolean loaded = false;

    private Database() { }

    public static synchronized void loadSettings() {
        if (loaded) return;
        loaded = true;

        File file = new File("db.properties");
        if (!file.exists()) return;

        try (InputStream in = new FileInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            host = p.getProperty("db.host", host);
            port = p.getProperty("db.port", port);
            database = p.getProperty("db.name", database);
            user = p.getProperty("db.user", user);
            password = p.getProperty("db.password", password);
            webroot = p.getProperty("server.webroot", webroot);
            logLevel = p.getProperty("server.log", logLevel);
            try {
                serverPort = Integer.parseInt(p.getProperty("server.port", "8080").trim());
            } catch (NumberFormatException ignored) {
                // keep the default rather than refusing to start
            }
        } catch (Exception e) {
            System.err.println("Could not read db.properties, using defaults: " + e.getMessage());
        }
    }

    public static int serverPort() { loadSettings(); return serverPort; }

    public static String webroot() { loadSettings(); return webroot; }

    public static String logLevel() { loadSettings(); return logLevel; }

    private static String url() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
             + "?useSSL=false&allowPublicKeyRetrieval=true"
             + "&serverTimezone=Africa/Johannesburg";
    }

    /** Callers are expected to use try-with-resources. */
    public static Connection get() throws SQLException {
        loadSettings();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("MySQL Connector/J is not on the classpath", ex);
        }
        return DriverManager.getConnection(url(), user, password);
    }

    /** Checked once at startup so the server fails loudly rather than per request. */
    public static void verify() throws SQLException {
        loadSettings();
        try (Connection c = get()) {
            System.out.println("  Database  : " + url());
            System.out.println("  Server    : MySQL " + c.getMetaData().getDatabaseProductVersion());
        }
    }
}
