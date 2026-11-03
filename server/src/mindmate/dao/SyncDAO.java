package mindmate.dao;

import mindmate.db.Database;
import mindmate.model.Checkin;
import mindmate.model.JournalEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Sync between a phone's offline store and the server.
 *
 * The rules are deliberately simple:
 *
 *   - Rows are matched on (user_id, client_id), the id the phone generated
 *     while it had no connection. Pushing the same batch twice is harmless,
 *     which matters because a student's connection drops mid-sync.
 *   - Last write wins, compared on updated_at. A push older than what the
 *     server already holds is ignored rather than overwriting newer text.
 *   - Deletes are tombstones, never real deletions, otherwise a delete made
 *     offline on one device would be undone by the next pull.
 *
 * A whole push runs inside one transaction: either the batch lands or none of
 * it does, so a half-synced journal cannot happen.
 */
public class SyncDAO {

    public static class Result {
        public int applied;
        public int skipped;
    }

    // ------------------------------------------------------------ push

    public Result pushCheckins(int userId, List<Checkin> rows) throws SQLException {
        String sql =
            "INSERT INTO checkins (user_id, client_id, mood, tags, note, recorded_at, updated_at, deleted) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE " +
            "  mood       = IF(VALUES(updated_at) > updated_at, VALUES(mood), mood), " +
            "  tags       = IF(VALUES(updated_at) > updated_at, VALUES(tags), tags), " +
            "  note       = IF(VALUES(updated_at) > updated_at, VALUES(note), note), " +
            "  deleted    = IF(VALUES(updated_at) > updated_at, VALUES(deleted), deleted), " +
            "  updated_at = GREATEST(updated_at, VALUES(updated_at))";

        Result r = new Result();
        try (Connection c = Database.get()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (Checkin row : rows) {
                    if (row.validationError() != null) { r.skipped++; continue; }
                    ps.setInt(1, userId);
                    ps.setString(2, row.getClientId());
                    ps.setInt(3, row.getMood());
                    ps.setString(4, row.getTags());
                    ps.setString(5, row.getNote());
                    ps.setTimestamp(6, new Timestamp(row.getRecordedAt()));
                    ps.setTimestamp(7, new Timestamp(row.getUpdatedAt()));
                    ps.setBoolean(8, row.isDeleted());
                    ps.addBatch();
                    r.applied++;
                }
                ps.executeBatch();
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
        return r;
    }

    public Result pushJournal(int userId, List<JournalEntry> rows) throws SQLException {
        String sql =
            "INSERT INTO journal_entries (user_id, client_id, title, body, recorded_at, updated_at, deleted) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE " +
            "  title      = IF(VALUES(updated_at) > updated_at, VALUES(title), title), " +
            "  body       = IF(VALUES(updated_at) > updated_at, VALUES(body), body), " +
            "  deleted    = IF(VALUES(updated_at) > updated_at, VALUES(deleted), deleted), " +
            "  updated_at = GREATEST(updated_at, VALUES(updated_at))";

        Result r = new Result();
        try (Connection c = Database.get()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (JournalEntry row : rows) {
                    if (row.validationError() != null) { r.skipped++; continue; }
                    ps.setInt(1, userId);
                    ps.setString(2, row.getClientId());
                    ps.setString(3, row.getTitle());
                    ps.setString(4, row.getBody());
                    ps.setTimestamp(5, new Timestamp(row.getRecordedAt()));
                    ps.setTimestamp(6, new Timestamp(row.getUpdatedAt()));
                    ps.setBoolean(7, row.isDeleted());
                    ps.addBatch();
                    r.applied++;
                }
                ps.executeBatch();
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
        return r;
    }

    // ------------------------------------------------------------ pull

    /** Everything changed since the given timestamp, tombstones included. */
    public List<Checkin> checkinsSince(int userId, long since) throws SQLException {
        String sql =
            "SELECT client_id, mood, tags, note, recorded_at, updated_at, deleted " +
            "FROM checkins WHERE user_id = ? AND updated_at > ? ORDER BY updated_at";
        List<Checkin> out = new ArrayList<>();
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setTimestamp(2, new Timestamp(since));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Checkin m = new Checkin();
                    m.setClientId(rs.getString("client_id"));
                    m.setMood(rs.getInt("mood"));
                    m.setTags(rs.getString("tags"));
                    m.setNote(rs.getString("note"));
                    m.setRecordedAt(rs.getTimestamp("recorded_at").getTime());
                    m.setUpdatedAt(rs.getTimestamp("updated_at").getTime());
                    m.setDeleted(rs.getBoolean("deleted"));
                    out.add(m);
                }
            }
        }
        return out;
    }

    public List<JournalEntry> journalSince(int userId, long since) throws SQLException {
        String sql =
            "SELECT client_id, title, body, recorded_at, updated_at, deleted " +
            "FROM journal_entries WHERE user_id = ? AND updated_at > ? ORDER BY updated_at";
        List<JournalEntry> out = new ArrayList<>();
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setTimestamp(2, new Timestamp(since));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JournalEntry e = new JournalEntry();
                    e.setClientId(rs.getString("client_id"));
                    e.setTitle(rs.getString("title"));
                    e.setBody(rs.getString("body"));
                    e.setRecordedAt(rs.getTimestamp("recorded_at").getTime());
                    e.setUpdatedAt(rs.getTimestamp("updated_at").getTime());
                    e.setDeleted(rs.getBoolean("deleted"));
                    out.add(e);
                }
            }
        }
        return out;
    }
}
