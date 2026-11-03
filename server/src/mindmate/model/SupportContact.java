package mindmate.model;

import mindmate.util.Json;

import java.util.Map;

/**
 * One entry in the referral directory.
 *
 * verifiedOn is the whole point of this class. A contact with no verification
 * date has never been checked against the organisation's own website, and the
 * API refuses to publish it - a wrong crisis number is worse than none.
 */
public class SupportContact {

    private int contactId;
    private String category;
    private String name;
    private String phone;
    private String smsOrWhatsApp;
    private String website;
    private String hours;
    private String costNote;
    private String languages;
    private String campus;
    private String detail;
    private String verifiedOn;
    private String verifiedBy;

    public boolean isVerified() {
        return verifiedOn != null && !verifiedOn.isEmpty();
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = Json.map();
        m.put("id", contactId);
        m.put("category", category);
        m.put("name", name);
        m.put("phone", phone);
        m.put("smsOrWhatsApp", smsOrWhatsApp);
        m.put("website", website);
        m.put("hours", hours);
        m.put("costNote", costNote);
        m.put("languages", languages);
        m.put("campus", campus);
        m.put("detail", detail);
        m.put("verifiedOn", verifiedOn);
        m.put("verifiedBy", verifiedBy);
        return m;
    }

    public int getContactId() { return contactId; }
    public void setContactId(int v) { this.contactId = v; }

    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }

    public String getSmsOrWhatsApp() { return smsOrWhatsApp; }
    public void setSmsOrWhatsApp(String v) { this.smsOrWhatsApp = v; }

    public String getWebsite() { return website; }
    public void setWebsite(String v) { this.website = v; }

    public String getHours() { return hours; }
    public void setHours(String v) { this.hours = v; }

    public String getCostNote() { return costNote; }
    public void setCostNote(String v) { this.costNote = v; }

    public String getLanguages() { return languages; }
    public void setLanguages(String v) { this.languages = v; }

    public String getCampus() { return campus; }
    public void setCampus(String v) { this.campus = v; }

    public String getDetail() { return detail; }
    public void setDetail(String v) { this.detail = v; }

    public String getVerifiedOn() { return verifiedOn; }
    public void setVerifiedOn(String v) { this.verifiedOn = v; }

    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String v) { this.verifiedBy = v; }
}
