package mindmate.model;

import mindmate.util.Json;

import java.util.Map;

public class JournalEntry {

    private String clientId;
    private String title = "";
    private String body = "";
    private long recordedAt;
    private long updatedAt;
    private boolean deleted;

    public static JournalEntry fromJson(Map<String, Object> o) {
        JournalEntry e = new JournalEntry();
        e.clientId = Json.str(o, "clientId", null);
        e.title = Json.str(o, "title", "");
        e.body = Json.str(o, "body", "");
        e.recordedAt = Json.longOf(o, "recordedAt", 0L);
        e.updatedAt = Json.longOf(o, "updatedAt", e.recordedAt);
        e.deleted = Json.boolOf(o, "deleted", false);
        return e;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = Json.map();
        m.put("clientId", clientId);
        m.put("title", title);
        m.put("body", body);
        m.put("recordedAt", recordedAt);
        m.put("updatedAt", updatedAt);
        m.put("deleted", deleted);
        return m;
    }

    public String validationError() {
        if (clientId == null || clientId.isEmpty()) return "clientId is required";
        if (clientId.length() > 64) return "clientId is too long";
        if (recordedAt <= 0) return "recordedAt is required";
        if (title.length() > 120) return "title is too long";
        if (!deleted && body.trim().isEmpty()) return "body cannot be empty";
        return null;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String v) { this.clientId = v; }

    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v == null ? "" : v; }

    public String getBody() { return body; }
    public void setBody(String v) { this.body = v == null ? "" : v; }

    public long getRecordedAt() { return recordedAt; }
    public void setRecordedAt(long v) { this.recordedAt = v; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long v) { this.updatedAt = v; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean v) { this.deleted = v; }
}
