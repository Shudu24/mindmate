/* Mindmate on-device encryption.
 *
 * Phase One, section 4: journal entries should be encrypted at rest so that a
 * person holding the device - or a developer holding a database - cannot read
 * them. This wraps the browser's built-in WebCrypto so the storage layer can
 * encrypt without knowing the details.
 *
 * Two modes, and the difference matters:
 *
 *   Device key (the default)
 *     A random AES-GCM key generated once and stored as a non-extractable
 *     CryptoKey. The browser will not hand the raw key material back to any
 *     script, so an exported database, a copied profile folder or a look
 *     through DevTools storage shows ciphertext only.
 *     It does NOT protect against someone using this browser on this device:
 *     the app can still decrypt, so anyone holding an unlocked phone can read
 *     the entries through the app itself.
 *
 *   Passphrase
 *     The key is derived from a passphrase with PBKDF2 and never stored. The
 *     entries cannot be read without it, not even on this device.
 *     The trade-off is real: forget the passphrase and the entries are gone.
 *     Nothing can recover them, which is the point.
 */
const Crypto = (function () {
  'use strict';

  const PBKDF2_ITERATIONS = 250000;
  const ALGO = 'AES-GCM';
  const IV_BYTES = 12;

  /* Held in memory only. Cleared on lock and when the tab closes. */
  let activeKey = null;
  let activeMode = null;

  const enc = new TextEncoder();
  const dec = new TextDecoder();

  function supported() {
    return !!(window.crypto && window.crypto.subtle);
  }

  function toB64(buf) {
    const bytes = new Uint8Array(buf);
    let s = '';
    for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
    return btoa(s);
  }

  function fromB64(b64) {
    const s = atob(b64);
    const out = new Uint8Array(s.length);
    for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
    return out;
  }

  function randomBytes(n) {
    return window.crypto.getRandomValues(new Uint8Array(n));
  }

  // ------------------------------------------------------------ key setup

  function generateDeviceKey() {
    /* extractable = false. The raw bytes never become visible to script, so
       they cannot be copied out of storage or logged by mistake. */
    return window.crypto.subtle.generateKey(
      { name: ALGO, length: 256 }, false, ['encrypt', 'decrypt']);
  }

  function deriveFromPassphrase(passphrase, salt) {
    return window.crypto.subtle.importKey(
      'raw', enc.encode(passphrase), 'PBKDF2', false, ['deriveKey']
    ).then(function (base) {
      return window.crypto.subtle.deriveKey(
        { name: 'PBKDF2', salt: salt, iterations: PBKDF2_ITERATIONS, hash: 'SHA-256' },
        base,
        { name: ALGO, length: 256 },
        false,
        ['encrypt', 'decrypt']);
    });
  }

  // ---------------------------------------------------------- encrypting

  /** Returns {iv, ct} with both Base64, or the value unchanged if empty. */
  function encrypt(plainText) {
    if (plainText == null || plainText === '') return Promise.resolve('');
    if (!activeKey) return Promise.reject(new Error('Storage is locked'));
    const iv = randomBytes(IV_BYTES);
    return window.crypto.subtle.encrypt(
      { name: ALGO, iv: iv }, activeKey, enc.encode(plainText)
    ).then(function (ct) {
      return { v: 1, iv: toB64(iv), ct: toB64(ct) };
    });
  }

  /**
   * Accepts either a {iv, ct} object or a plain string. Strings are returned
   * untouched, which is what lets entries written before encryption was added
   * keep working instead of appearing as corrupt.
   */
  function decrypt(value) {
    if (value == null || value === '') return Promise.resolve('');
    if (typeof value === 'string') return Promise.resolve(value);
    if (!value.ct) return Promise.resolve('');
    if (!activeKey) return Promise.reject(new Error('Storage is locked'));

    return window.crypto.subtle.decrypt(
      { name: ALGO, iv: fromB64(value.iv) }, activeKey, fromB64(value.ct)
    ).then(function (buf) {
      return dec.decode(buf);
    }).catch(function () {
      /* Wrong key, or the ciphertext was tampered with - AES-GCM checks
         integrity, so this is a real signal rather than garbled output. */
      return '[could not be decrypted]';
    });
  }

  return {
    supported: supported,
    mode: function () { return activeMode; },
    isUnlocked: function () { return activeKey !== null; },

    /** Called once at startup with whatever the storage layer has saved. */
    start: function (saved) {
      if (!supported()) { activeMode = 'none'; return Promise.resolve('none'); }

      if (saved && saved.mode === 'passphrase') {
        activeMode = 'passphrase';
        return Promise.resolve('passphrase');      // stays locked until unlocked
      }
      if (saved && saved.deviceKey) {
        activeKey = saved.deviceKey;
        activeMode = 'device';
        return Promise.resolve('device');
      }
      return generateDeviceKey().then(function (k) {
        activeKey = k;
        activeMode = 'device';
        return { created: true, deviceKey: k };
      });
    },

    unlock: function (passphrase, salt, verifier) {
      return deriveFromPassphrase(passphrase, fromB64(salt)).then(function (k) {
        const prev = activeKey;
        activeKey = k;
        return decrypt(verifier).then(function (text) {
          if (text !== 'mindmate') {          // wrong passphrase
            activeKey = prev;
            return false;
          }
          activeMode = 'passphrase';
          return true;
        });
      });
    },

    lock: function () {
      if (activeMode === 'passphrase') activeKey = null;
    },

    /** Builds the material the storage layer must save for a new passphrase. */
    preparePassphrase: function (passphrase) {
      const salt = randomBytes(16);
      return deriveFromPassphrase(passphrase, salt).then(function (k) {
        activeKey = k;
        activeMode = 'passphrase';
        return encrypt('mindmate').then(function (verifier) {
          return { mode: 'passphrase', salt: toB64(salt), verifier: verifier };
        });
      });
    },

    encrypt: encrypt,
    decrypt: decrypt
  };
})();
