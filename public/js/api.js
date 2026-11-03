/* Talking to the Mindmate server.
 *
 * An account is optional and always will be. Phase One, section 4: the app
 * should work as a guest, with only the connected features held behind signing
 * in. So nothing in here is allowed to block the offline core - if the server
 * is unreachable, the check-in and journal screens carry on exactly as before.
 *
 * The token is kept in IndexedDB rather than localStorage so it sits with the
 * rest of the app's data and is removed by the same "delete everything".
 */
const Api = (function () {
  'use strict';

  let token = null;
  let user = null;

  function url(path) {
    return path;                    // same origin: the Java server serves this app
  }

  function request(path, options) {
    const opts = options || {};
    const headers = opts.headers || {};
    if (token) headers['Authorization'] = 'Bearer ' + token;
    if (opts.body) headers['Content-Type'] = 'application/json';

    return fetch(url(path), {
      method: opts.method || 'GET',
      headers: headers,
      body: opts.body ? JSON.stringify(opts.body) : undefined,
      cache: 'no-store'
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (data) {
        if (!res.ok) {
          const err = new Error(data.error || ('Server said ' + res.status));
          err.status = res.status;
          throw err;
        }
        return data;
      });
    }).catch(function (err) {
      /* fetch rejects for network failures, which offline is one of. Say so in
         words a student can act on rather than "Failed to fetch". */
      if (err instanceof TypeError) {
        const offline = new Error('No connection to the server.');
        offline.offline = true;
        throw offline;
      }
      throw err;
    });
  }

  return {
    isSignedIn: function () { return !!token; },
    currentUser: function () { return user; },

    /** Restores a saved session. Never throws - a failure just means guest. */
    restore: function (savedToken) {
      if (!savedToken) return Promise.resolve(null);
      token = savedToken;
      if (!navigator.onLine) {
        /* Offline: trust the saved token rather than signing the student out
           for being on a train. It is checked next time there is a signal. */
        return Promise.resolve(null);
      }
      return request('/api/me').then(function (u) {
        user = u;
        return u;
      }).catch(function (err) {
        if (err.offline) return null;       // keep the token, try again later
        token = null;                        // expired or rejected
        user = null;
        return null;
      });
    },

    register: function (username, password, displayName) {
      return request('/api/register', {
        method: 'POST',
        body: { username: username, password: password, displayName: displayName }
      }).then(function (data) {
        token = data.token;
        user = data.user;
        return data;
      });
    },

    signIn: function (username, password) {
      return request('/api/login', {
        method: 'POST',
        body: { username: username, password: password }
      }).then(function (data) {
        token = data.token;
        user = data.user;
        return data;
      });
    },

    signOut: function () {
      const had = token;
      token = null;
      user = null;
      if (!had) return Promise.resolve();
      /* Tell the server so the session row goes away, but never let a failure
         here keep someone signed in on their own device. */
      return request('/api/logout', { method: 'POST' }).catch(function () { });
    },

    token: function () { return token; }
  };
})();
