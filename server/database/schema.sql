-- =====================================================================
--  Mindmate server schema
--
--  Run with:  mysql -u root -p < schema.sql
--
--  WARNING: this drops and recreates the database.
-- =====================================================================

DROP DATABASE IF EXISTS mindmate_db;
CREATE DATABASE mindmate_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE mindmate_db;

-- ---------------------------------------------------------------------
--  users
--
--  Passwords are stored as PBKDF2-HMAC-SHA256 with a per-user random
--  salt, written as  iterations:salt:hash  (both parts Base64). PBKDF2
--  is deliberately slow, which is what makes a stolen table expensive to
--  attack. The column is wide because that whole string is stored.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id       INT          NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_users_username (username)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  sessions
--
--  A login hands back an opaque random token. Only the SHA-256 of that
--  token is kept, so a leaked database still does not let anyone log in.
-- ---------------------------------------------------------------------
CREATE TABLE sessions (
    session_id INT          NOT NULL AUTO_INCREMENT,
    user_id    INT          NOT NULL,
    token_hash CHAR(64)     NOT NULL,
    expires_at DATETIME     NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id),
    UNIQUE KEY uq_sessions_token (token_hash),
    KEY ix_sessions_user (user_id),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  checkins
--
--  client_id is the id the phone generated while offline. Sync upserts
--  on (user_id, client_id), so pushing the same entry twice cannot
--  create duplicates - which matters when a connection drops mid-sync.
-- ---------------------------------------------------------------------
CREATE TABLE checkins (
    checkin_id  INT          NOT NULL AUTO_INCREMENT,
    user_id     INT          NOT NULL,
    client_id   VARCHAR(64)  NOT NULL,
    mood        TINYINT      NOT NULL,
    tags        VARCHAR(255) NOT NULL DEFAULT '',
    note        TEXT,
    recorded_at DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (checkin_id),
    UNIQUE KEY uq_checkins_client (user_id, client_id),
    KEY ix_checkins_updated (user_id, updated_at),
    CONSTRAINT fk_checkins_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_checkins_mood CHECK (mood BETWEEN 1 AND 5)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  journal_entries
-- ---------------------------------------------------------------------
CREATE TABLE journal_entries (
    entry_id    INT          NOT NULL AUTO_INCREMENT,
    user_id     INT          NOT NULL,
    client_id   VARCHAR(64)  NOT NULL,
    title       VARCHAR(120) NOT NULL DEFAULT '',
    body        MEDIUMTEXT   NOT NULL,
    recorded_at DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (entry_id),
    UNIQUE KEY uq_entries_client (user_id, client_id),
    KEY ix_entries_updated (user_id, updated_at),
    CONSTRAINT fk_entries_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  support_contacts
--
--  The referral directory. This is the one part of Mindmate that needs a
--  connection, which is why it lives on the server and not on the phone:
--  a number can be corrected centrally without shipping a new build.
--
--  Every row starts unverified. The app must not display a contact whose
--  verified_on is NULL - see docs/SUPPORT-CONTENT.md.
-- ---------------------------------------------------------------------
CREATE TABLE support_contacts (
    contact_id   INT          NOT NULL AUTO_INCREMENT,
    category     ENUM('Crisis','Campus','National','Online') NOT NULL,
    name         VARCHAR(120) NOT NULL,
    phone        VARCHAR(60),
    sms_or_wa    VARCHAR(60),
    website      VARCHAR(255),
    hours        VARCHAR(120),
    cost_note    VARCHAR(120),
    languages    VARCHAR(120),
    campus       VARCHAR(120),
    detail       TEXT,
    verified_on  DATE         DEFAULT NULL,
    verified_by  VARCHAR(100) DEFAULT NULL,
    sort_order   INT          NOT NULL DEFAULT 100,
    PRIMARY KEY (contact_id),
    KEY ix_contacts_category (category, sort_order)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  Seed rows.
--
--  These are STRUCTURE ONLY. Every phone/website field is intentionally
--  left NULL and verified_on is NULL, so nothing is displayed until a
--  person has checked the details against the organisation's own site
--  and filled them in. Do not invent numbers here.
-- ---------------------------------------------------------------------
INSERT INTO support_contacts
    (category, name, hours, cost_note, detail, sort_order) VALUES
('Crisis',   'Suicide crisis line',            NULL, NULL,
    'TO VERIFY: number, hours, cost, languages. Source: the organisation''s own website.', 10),
('Crisis',   'Emergency services',             NULL, NULL,
    'TO VERIFY: the number to call when someone is in immediate physical danger.', 20),
('National', 'Depression and anxiety helpline', NULL, NULL,
    'TO VERIFY: SADAG lines differ by purpose. Record which line is which.', 30),
('Campus',   'Student counselling service',     NULL, NULL,
    'TO VERIFY: building, room, hours, how to book, whether it is free.', 40);

SELECT 'Tables created' AS info;
SELECT COUNT(*) AS support_rows_awaiting_verification FROM support_contacts;
