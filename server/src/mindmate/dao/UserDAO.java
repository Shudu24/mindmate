package mindmate.dao;

import mindmate.db.Database;
import mindmate.model.User;
import mindmate.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Accounts and sessions.
 *
 * Every statement here is a PreparedStatement with bound parameters. No user
 * input is ever concatenated into SQL, so the API is not open to injection.
 */
public class UserDAO {

    /** How long a login stays valid. */
    private static final int SESSION_DAYS = 30;

    public boolean usernameTaken(String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public User register(String username, String plainPassword, String displayName) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, display_name) VALUES (?, ?, ?)";
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, PasswordUtil.hash(plainPassword));
            ps.setString(3, displayName);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                return new User(id, username, displayName);
            }
        }
    }

    /** Returns null when the username is unknown or the password is wrong. */
    public User authenticate(String username, String plainPassword) throws SQLException {
        String sql = "SELECT user_id, username, password_hash, display_name FROM users WHERE username = ?";
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    /* Hash anyway so a missing user and a wrong password take a
                       similar amount of time, rather than the response time
                       revealing which usernames exist. */
                    PasswordUtil.matches(plainPassword, PasswordUtil.hash("x"));
                    return null;
                }
                if (!PasswordUtil.matches(plainPassword, rs.getString("password_hash"))) {
                    return null;
                }
                return new User(rs.getInt("user_id"),
                                rs.getString("username"),
                                rs.getString("display_name"));
            }
        }
    }

    /** Creates a session and returns the raw token. Only its hash is stored. */
    public String startSession(int userId) throws SQLException {
        String token = PasswordUtil.newToken();
        String sql = "INSERT INTO sessions (user_id, token_hash, expires_at) VALUES (?, ?, ?)";
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, PasswordUtil.sha256Hex(token));
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now().plusDays(SESSION_DAYS)));
            ps.executeUpdate();
        }
        return token;
    }

    /** Returns the signed-in user for a bearer token, or null. */
    public User userForToken(String token) throws SQLException {
        if (token == null || token.isEmpty()) return null;
        String sql =
            "SELECT u.user_id, u.username, u.display_name " +
            "FROM sessions s JOIN users u ON u.user_id = s.user_id " +
            "WHERE s.token_hash = ? AND s.expires_at > NOW()";
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, PasswordUtil.sha256Hex(token));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new User(rs.getInt("user_id"),
                                rs.getString("username"),
                                rs.getString("display_name"));
            }
        }
    }

    public void endSession(String token) throws SQLException {
        if (token == null || token.isEmpty()) return;
        String sql = "DELETE FROM sessions WHERE token_hash = ?";
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, PasswordUtil.sha256Hex(token));
            ps.executeUpdate();
        }
    }

    /** Removes everything for a user. Called when someone deletes their account. */
    public void deleteAccount(int userId) throws SQLException {
        String sql = "DELETE FROM users WHERE user_id = ?";
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }
}
