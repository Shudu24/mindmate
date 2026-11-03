package mindmate.model;

import mindmate.util.Json;

import java.util.List;
import java.util.Map;

/**
 * One check-in. clientId is the id the phone assigned while offline; the
 * server upserts on it so a retried sync cannot create duplicates.
 */
public class Checkin {

    private String clientId;
    private int mood;
    private String tags = "";
    private String note = "";
    private long recordedAt;
    private long updatedAt;
    private boolean deleted;

    public static Checkin fromJson(Map<String, Object> o) {
        Checkin c = new Checkin();
        c.clientId = Json.str(o, "clientId", null);
        c.mood = Json.intOf(o, "mood", 0);
        c.note = Json.str(o, "note", "");
        c.recordedAt = Json.longOf(o, "recordedAt", 0L);
        c.updatedAt = Json.longOf(o, "updatedAt", c.recordedAt);
        c.deleted = Json.boolOf(o, "deleted", false);

        List<Object> tagList = Json.list(o, "tags");
        StringBuilder sb = new StringBuilder();
        for (Object t : tagList) {
            if (sb.length() > 0) sb.append('|');
            sb.append(String.valueOf(t).replace('|', ' '));
        }
        c.tags = sb.toString();
        return c;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = Json.map();
        m.put("clientId", clientId);
        m.put("mood", mood);
        m.put("tags", tags.isEmpty() ? java.util.Collections.emptyList()
                                     : java.util.Arrays.asList(tags.split("\\|")));
        m.put("note", note);
        m.put("recordedAt", recordedAt);
        m.put("updatedAt", updatedAt);
        m.put("deleted", deleted);
        return m;
    }

    /** Rejects anything the schema would refuse, with a readable reason. */
    public String validationError() {
        if (clientId == null || clientId.isEmpty()) return "clientId is required";
        if (clientId.length() > 64) return "clientId is too long";
        if (mood < 1 || mood > 5) return "mood must be between 1 and 5";
        if (recordedAt <= 0) return "recordedAt is required";
        if (tags.length() > 255) return "too many tags";
        return null;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String v) { this.clientId = v; }

    public int getMood() { return mood; }
    public void setMood(int v) { this.mood = v; }

    public String getTags() { return tags; }
    public void setTags(String v) { this.tags = v == null ? "" : v; }

    public String getNote() { return note; }
    public void setNote(String v) { this.note = v == null ? "" : v; }

    public long getRecordedAt() { return recordedAt; }
    public void setRecordedAt(long v) { this.recordedAt = v; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long v) { this.updatedAt = v; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean v) { this.deleted = v; }
}
