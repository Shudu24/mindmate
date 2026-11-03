# Mindmate

A private space for South African students to check in with how they are feeling.

Two halves that work together but do not depend on each other:

- a **progressive web app** whose core runs entirely on the phone, offline, with no account
- a **Java server** that adds accounts, sync and the referral directory for students who want them

## The two rules this is built around

Both come straight from the research in the project brief:

1. **It never diagnoses.** No scores, no labels, no "results". The mood wording is plain rather
   than clinical and nothing is added up. The job is to give a student space to reflect and to
   point them toward a person.
2. **The core works offline.** Check-in, journal and history never touch the network. Anything
   needing a connection lives on the Support screen, separate from the core.

Sync is **opt-in**. A student who never signs in keeps everything on their own device, which is
the default the research asked for.

## Layout

```
public/                  the web app - this is the only folder served to browsers
  index.html             app shell, onboarding, lock screen, five screens
  css/styles.css         styling, light and dark
  js/crypto.js           WebCrypto wrapper - keys and encryption
  js/api.js              talking to the server (optional account)
  js/db.js               IndexedDB wrapper (device storage, encrypts on write)
  js/app.js              UI, routing, rendering
  sw.js                  service worker, precaches the shell
  manifest.webmanifest   install metadata
  icons/

server/                  the Java backend
  src/mindmate/
    Main.java            entry point, starts the HTTP server
    db/Database.java     the only class that opens JDBC connections
    model/               User, Checkin, JournalEntry, SupportContact
    dao/                 UserDAO, SyncDAO, SupportDAO - all SQL lives here
    http/                Http, ApiHandler, StaticHandler
    util/                PasswordUtil, Json
  database/schema.sql    MySQL schema and seed rows
  db.properties          connection and port settings

lib/                     mysql-connector-j (the only jar needed)
docs/                    content that still needs verifying
```

`public/` is deliberately separate from `server/`. The web root contains only the app, so
source and configuration cannot be downloaded even by accident.

## Requirements

- JDK 17 or newer
- MySQL 8
- MySQL Connector/J (already in `lib/`)

## Setting it up

**1. Create the database**

```bash
mysql -u root -p < server/database/schema.sql
```

This drops and recreates `mindmate_db`.

**2. Point the server at your MySQL**

Edit `server/db.properties` and set `db.password` to your own MySQL password.

**3. Compile**

From the `server` folder:

```bash
javac -encoding UTF-8 -cp "../lib/*" -d out $(find src -name "*.java")
```

**4. Run**

From the `server` folder, so `db.properties` is found:

```bash
java -cp "out;../lib/*" mindmate.Main
```

Then open <http://localhost:8080>. One Java process serves both the web app and the API.

On macOS or Linux use a colon instead of a semicolon in the classpath.

## The API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | Liveness, and how many support contacts are unverified |
| POST | `/api/register` | `{username, password, displayName}` |
| POST | `/api/login` | `{username, password}` returns a bearer token |
| POST | `/api/logout` | Ends the session |
| GET | `/api/me` | The signed-in user |
| GET | `/api/sync?since=<ms>` | Everything changed since that time |
| POST | `/api/sync` | `{checkins:[...], journal:[...]}` |
| GET | `/api/support` | Verified referral contacts only |

Authenticated routes take `Authorization: Bearer <token>`.

### How sync resolves conflicts

- Rows match on `(user_id, client_id)` — the id the phone generated while offline. Pushing the
  same batch twice is harmless, which matters when a connection drops mid-sync.
- **Last write wins**, compared on `updatedAt`. A push older than what the server holds is
  ignored rather than overwriting newer text.
- Deletes are **tombstones**, never real deletions, otherwise a delete made offline on one
  device would be undone by the next pull.
- A whole push runs in one transaction — the batch lands or none of it does.

## On-device encryption

Journal titles and bodies, and check-in notes, are encrypted with **AES-GCM** before they are
written to IndexedDB. Mood values, tags and timestamps stay readable — the history chart needs
them, and they are the least revealing part of an entry.

There are two key modes, and the difference is worth being precise about:

**Device key (the default).** A random AES-GCM key generated once and stored as a
**non-extractable** `CryptoKey`. The browser will not return the raw key material to any script,
so an exported database or a look through DevTools storage shows ciphertext only. It does *not*
protect against someone who can already unlock the phone and open the app.

**Passphrase (optional).** The key is derived with **PBKDF2-HMAC-SHA256, 250,000 iterations**, and
never stored. Entries cannot be read without it, on this device or anywhere else. Forget it and
the entries are unrecoverable — which is the point, and the UI says so before you turn it on.

Switching to a passphrase re-encrypts existing entries: everything is read under the old key,
the key is swapped, then it is written back.

Verified by writing a known string and reading the raw IndexedDB records: the plaintext never
appears on disk, each field carries its own IV, and entries survive the passphrase upgrade intact.

## Onboarding

First run walks through four short steps, shaped by section 4 of the Phase One document:

1. What the app is, and plainly what it is not — it does not diagnose
2. Where the writing lives, and that it is encrypted
3. The ten topic groups from section 10, optional and multi-select
4. An optional age band — no name, no student number, no date of birth

Choices are stored on the device and never sent anywhere. Topics can be changed later from the
Data tab.

## Accounts

An account is **optional and stays optional**. Phase One, section 4: the app works as a guest,
with only connected features behind signing in. Nothing in the account layer is allowed to block
the offline core — if the server is unreachable, check-in and journal carry on unchanged.

- Sign in and register live behind the **Data** tab, never in the way of a check-in
- The sign-up form asks for a username, a password and an optional display name. No real name,
  no student number, no date of birth — and it says so on the form
- The token is kept in IndexedDB with the rest of the app's data, so "delete all my data" clears
  it and signs out too
- On startup a saved session is confirmed against `/api/me` in the background. While offline the
  token is trusted rather than signing a student out for being on a train
- **Signing in does not upload anything.** Sync is not wired up, and the account card says so
  in as many words

The service worker deliberately never caches `/api/` — those are same-origin GETs, so without
that exclusion a cached `/api/me` would show a signed-in state that was not real.

## Security notes

- Passwords use **PBKDF2-HMAC-SHA256**, 120,000 iterations, per-user random salt. Plain SHA-256
  can be tried billions of times a second on a GPU; PBKDF2 is deliberately slow.
- Session tokens are random 32-byte values, and only their **SHA-256 is stored**, so a leaked
  database cannot be replayed as a login.
- Every SQL statement is a `PreparedStatement` with bound parameters — no user input is ever
  concatenated into a query.
- Login returns the same message for an unknown user and a wrong password, and hashes either
  way, so responses do not reveal which usernames exist.
- The static handler blocks path traversal and serves only web file types.

**Not production ready.** It serves plain HTTP; anything real needs TLS, rate limiting on login,
and a decision about whether journal text should be encrypted before it ever reaches the server.

## Before anyone real uses this

The Support screen shows placeholders, and `support_contacts` rows start unverified — the API
will not publish a contact with no `verified_on` date. Crisis line and campus counselling details
must be checked against official sources first. See [docs/SUPPORT-CONTENT.md](docs/SUPPORT-CONTENT.md).

## Where this sits against the increment plan

| Increment | Item | Status |
|---|---|---|
| 1 | Onboarding | done |
| 1 | Mood tracking | done |
| 1 | Journaling | done |
| 1 | Encrypted local database | done |
| 2 | Text-first information content | not started |
| 3 | Referral directory | API built, content unverified |
| 3 | Crisis escalation path | help link only |
| 3 | Optional background sync | server done, client not wired |
| 4 | Hardening | not started |

**Increment 1 is complete.**

## Not built yet

- Support screen content, and the client code that reads `/api/support`
- The client side of sync — the app does not call the API yet; it still stores only locally.
  Note that server-side sync currently stores journal text in plain form, which the Phase One
  document says it should not. End-to-end encryption has to be settled before sync is wired up.
- The comparison table of existing apps (section 3 of the brief)
#   m i n d m a t e  
 