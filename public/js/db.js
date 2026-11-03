/* Mindmate storage layer.
 *
 * Everything lives in IndexedDB on the user's own device. There is no server
 * and no account, which is what makes the offline requirement straightforward:
 * there is nothing to be offline *from*.
 *
 * Free text - journal titles and bodies, and check-in notes - is encrypted
 * before it is written, and decrypted on the way out, so callers never handle
 * ciphertext. Mood values, tags and timestamps stay readable: the history
 * chart needs them, and they are the least revealing part of an entry.
 */
const DB = (function () {
  const NAME = 'mindmate';
  const VERSION = 2;
  const CHECKINS = 'checkins';
  const JOURNAL = 'journal';
  const PREFS = 'prefs';
  const VAULT = 'vault';          // crypto material, never synced

  let dbp = null;

  function open() {
    if (dbp) return dbp;
    dbp = new Promise(function (resolve, reject) {
      const req = indexedDB.open(NAME, VERSION);

      req.onupgradeneeded = function (e) {
        const db = e.target.result;
        if (!db.objectStoreNames.contains(CHECKINS)) {
          db.createObjectStore(CHECKINS, { keyPath: 'id', autoIncrement: true })
            .createIndex('ts', 'ts');
        }
        if (!db.objectStoreNames.contains(JOURNAL)) {
          db.createObjectStore(JOURNAL, { keyPath: 'id', autoIncrement: true })
            .createIndex('ts', 'ts');
        }
        if (!db.objectStoreNames.contains(PREFS)) {
          db.createObjectStore(PREFS, { keyPath: 'key' });
        }
        if (!db.objectStoreNames.contains(VAULT)) {
          db.createObjectStore(VAULT, { keyPath: 'id' });
        }
      };

      req.onsuccess = function () { resolve(req.result); };
      req.onerror = function () { reject(req.error); };
    });
    return dbp;
  }

  function tx(store, mode, fn) {
    return open().then(function (db) {
      return new Promise(function (resolve, reject) {
        const t = db.transaction(store, mode);
        const req = fn(t.objectStore(store));
        t.oncomplete = function () { resolve(req && req.result); };
        t.onerror = function () { reject(t.error); };
        t.onabort = function () { reject(t.error); };
      });
    });
  }

  /** Newest first. */
  function allRaw(store) {
    return open().then(function (db) {
      return new Promise(function (resolve, reject) {
        const out = [];
        const t = db.transaction(store, 'readonly');
        const cur = t.objectStore(store).index('ts').openCursor(null, 'prev');
        cur.onsuccess = function (e) {
          const c = e.target.result;
          if (c) { out.push(c.value); c.continue(); } else { resolve(out); }
        };
        cur.onerror = function () { reject(cur.error); };
      });
    });
  }

  // ------------------------------------------------------------- vault

  function vaultGet() {
    return tx(VAULT, 'readonly', function (s) { return s.get('crypto'); });
  }

  function vaultPut(rec) {
    rec.id = 'crypto';
    return tx(VAULT, 'readwrite', function (s) { return s.put(rec); });
  }

  // ------------------------------------------------------------- prefs

  function prefGet(key, fallback) {
    return tx(PREFS, 'readonly', function (s) { return s.get(key); })
      .then(function (row) { return row ? row.value : fallback; });
  }

  function prefSet(key, value) {
    return tx(PREFS, 'readwrite', function (s) { return s.put({ key: key, value: value }); });
  }

  // -------------------------------------------------------- encryption

  function encCheckin(rec) {
    return Crypto.encrypt(rec.note || '').then(function (note) {
      return { ts: rec.ts, mood: rec.mood, tags: rec.tags || [], note: note };
    });
  }

  function decCheckin(row) {
    return Crypto.decrypt(row.note).then(function (note) {
      return { id: row.id, ts: row.ts, mood: row.mood, tags: row.tags || [], note: note };
    });
  }

  function encEntry(rec) {
    return Promise.all([
      Crypto.encrypt(rec.title || ''),
      Crypto.encrypt(rec.body || '')
    ]).then(function (r) {
      return { ts: rec.ts, title: r[0], body: r[1] };
    });
  }

  function decEntry(row) {
    return Promise.all([
      Crypto.decrypt(row.title),
      Crypto.decrypt(row.body)
    ]).then(function (r) {
      return { id: row.id, ts: row.ts, title: r[0], body: r[1] };
    });
  }

  return {
    /**
     * Opens the database and brings encryption up. Must be awaited before any
     * read or write, so nothing is ever written in the clear by accident.
     */
    init: function () {
      return open()
        .then(vaultGet)
        .then(function (saved) { return Crypto.start(saved); })
        .then(function (result) {
          if (result && result.created) {
            return vaultPut({ mode: 'device', deviceKey: result.deviceKey })
              .then(function () { return 'device'; });
          }
          return result;
        });
    },

    vault: vaultGet,
    saveVault: vaultPut,

    getPref: prefGet,
    setPref: prefSet,

    addCheckin: function (rec) {
      return encCheckin(rec).then(function (safe) {
        return tx(CHECKINS, 'readwrite', function (s) { return s.add(safe); });
      });
    },

    addEntry: function (rec) {
      return encEntry(rec).then(function (safe) {
        return tx(JOURNAL, 'readwrite', function (s) { return s.add(safe); });
      });
    },

    checkins: function () {
      return allRaw(CHECKINS).then(function (rows) {
        return Promise.all(rows.map(decCheckin));
      });
    },

    entries: function () {
      return allRaw(JOURNAL).then(function (rows) {
        return Promise.all(rows.map(decEntry));
      });
    },

    deleteCheckin: function (id) { return tx(CHECKINS, 'readwrite', function (s) { return s.delete(id); }); },
    deleteEntry:   function (id) { return tx(JOURNAL,  'readwrite', function (s) { return s.delete(id); }); },

    clearAll: function () {
      return Promise.all([
        tx(CHECKINS, 'readwrite', function (s) { return s.clear(); }),
        tx(JOURNAL,  'readwrite', function (s) { return s.clear(); })
      ]);
    },

    /** Export is deliberately readable - it is the user asking for their own data. */
    exportAll: function () {
      return Promise.all([
        allRaw(CHECKINS).then(function (r) { return Promise.all(r.map(decCheckin)); }),
        allRaw(JOURNAL).then(function (r) { return Promise.all(r.map(decEntry)); })
      ]).then(function (r) {
        return {
          app: 'Mindmate',
          exportedAt: new Date().toISOString(),
          note: 'This file is NOT encrypted. Keep it somewhere you trust.',
          checkins: r[0],
          journal: r[1]
        };
      });
    }
  };
})();
