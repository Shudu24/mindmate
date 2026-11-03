package mindmate.dao;

import mindmate.db.Database;
import mindmate.model.SupportContact;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The referral directory.
 *
 * verifiedOnly() is what the app calls. It returns nothing that has not been
 * checked against the organisation's own website and dated, because a wrong
 * crisis number is worse than no number at all. all() is for the admin view,
 * where unverified rows must be visible so somebody can go and verify them.
 */
public class SupportDAO {

    private static final String COLUMNS =
        "contact_id, category, name, phone, sms_or_wa, website, hours, " +
        "cost_note, languages, campus, detail, verified_on, verified_by";

    public List<SupportContact> verifiedOnly() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM support_contacts " +
                     "WHERE verified_on IS NOT NULL ORDER BY category, sort_order, name";
        return query(sql);
    }

    public List<SupportContact> all() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM support_contacts " +
                     "ORDER BY category, sort_order, name";
        return query(sql);
    }

    /** How many entries are still waiting for somebody to verify them. */
    public int unverifiedCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM support_contacts WHERE verified_on IS NULL";
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private List<SupportContact> query(String sql) throws SQLException {
        List<SupportContact> out = new ArrayList<>();
        try (Connection c = Database.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                SupportContact s = new SupportContact();
                s.setContactId(rs.getInt("contact_id"));
                s.setCategory(rs.getString("category"));
                s.setName(rs.getString("name"));
                s.setPhone(rs.getString("phone"));
                s.setSmsOrWhatsApp(rs.getString("sms_or_wa"));
                s.setWebsite(rs.getString("website"));
                s.setHours(rs.getString("hours"));
                s.setCostNote(rs.getString("cost_note"));
                s.setLanguages(rs.getString("languages"));
                s.setCampus(rs.getString("campus"));
                s.setDetail(rs.getString("detail"));
                java.sql.Date v = rs.getDate("verified_on");
                s.setVerifiedOn(v == null ? null : v.toString());
                s.setVerifiedBy(rs.getString("verified_by"));
                out.add(s);
            }
        }
        return out;
    }
}
