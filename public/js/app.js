/* Mindmate - UI and behaviour.
 *
 * Design rules taken from the research in the project brief:
 *   - Never diagnose, score or label. A check-in is a reflection, not an assessment.
 *   - The offline core (check-in, journal, history) never needs a connection.
 *   - User text is rendered with textContent only, never innerHTML.
 */
(function () {
  'use strict';

  /* Wording matters here. These are plain descriptions of a moment, deliberately
     not a clinical scale, and the app never adds them up into a "result". */
  const MOODS = [
    { v: 1, label: 'Really struggling', tone: '#a23b32' },
    { v: 2, label: 'Low',               tone: '#c4722f' },
    { v: 3, label: 'Okay',              tone: '#b09a2e' },
    { v: 4, label: 'Good',              tone: '#5d9a48' },
    { v: 5, label: 'Really good',       tone: '#2f6f62' }
  ];

  const TAGS = ['Academics', 'Sleep', 'Money', 'Family', 'Friends',
                'Relationship', 'Health', 'Loneliness', 'The future'];

  /* The ten groups from section 10 of the Phase One document. Chosen at
     onboarding, kept on the device, and never sent anywhere - they exist to
     make what the app offers specific rather than generic. */
  const TOPICS = [
    'Academic pressure',
    'Anxiety or overthinking',
    'Low mood or loneliness',
    'Financial stress',
    'Friends and relationships',
    'Family',
    'First-year adjustment',
    'Being far from home',
    'Final year and what comes next',
    'Accessibility needs'
  ];

  const $  = function (sel) { return document.querySelector(sel); };
  const el = function (tag, cls, text) {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  };

  let mood = null;
  const chosenTags = new Set();

  /* ------------------------------------------------------------- helpers */

  function moodOf(v) {
    for (let i = 0; i < MOODS.length; i++) { if (MOODS[i].v === v) return MOODS[i]; }
    return MOODS[2];
  }

  function when(ts) {
    const d = new Date(ts);
    const now = new Date();
    const sameDay = d.toDateString() === now.toDateString();
    const time = d.toLocaleTimeString('en-ZA', { hour: '2-digit', minute: '2-digit' });
    if (sameDay) return 'Today ' + time;
    const yst = new Date(now); yst.setDate(now.getDate() - 1);
    if (d.toDateString() === yst.toDateString()) return 'Yesterday ' + time;
    return d.toLocaleDateString('en-ZA', { day: 'numeric', month: 'short' }) + ' ' + time;
  }

  let toastTimer = null;
  function toast(msg) {
    const t = $('#toast');
    t.textContent = msg;
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.hidden = true; }, 2600);
  }

  function greet() {
    const h = new Date().getHours();
    if (h < 12) return 'Good morning. How are you doing?';
    if (h < 18) return 'Good afternoon. How are you doing?';
    return 'Good evening. How are you doing?';
  }

  /* --------------------------------------------------------------- build */

  function buildMoods() {
    const row = $('.mood-row');
    MOODS.forEach(function (m) {
      const b = el('button', 'mood');
      b.type = 'button';
      b.setAttribute('role', 'radio');
      b.setAttribute('aria-checked', 'false');
      b.dataset.v = String(m.v);

      const dot = el('span', 'dot');
      dot.style.background = 'var(--line)';
      b.appendChild(dot);
      b.appendChild(el('span', null, m.label));

      b.addEventListener('click', function () {
        mood = m.v;
        row.querySelectorAll('.mood').forEach(function (x) {
          const on = x.dataset.v === String(m.v);
          x.setAttribute('aria-checked', on ? 'true' : 'false');
          x.querySelector('.dot').style.background = on ? moodOf(Number(x.dataset.v)).tone : 'var(--line)';
        });
        $('#checkin-save').disabled = false;
        $('#checkin-hint').textContent = 'You can add more, or just save.';
      });

      row.appendChild(b);
    });
  }

  function buildTags() {
    const row = $('#checkin-tags');
    TAGS.forEach(function (name) {
      const b = el('button', 'tag', name);
      b.type = 'button';
      b.setAttribute('aria-pressed', 'false');
      b.addEventListener('click', function () {
        const on = chosenTags.has(name);
        if (on) { chosenTags.delete(name); } else { chosenTags.add(name); }
        b.setAttribute('aria-pressed', on ? 'false' : 'true');
      });
      row.appendChild(b);
    });
  }

  /* ------------------------------------------------------------ check-in */

  function resetCheckin() {
    mood = null;
    chosenTags.clear();
    $('#checkin-note').value = '';
    document.querySelectorAll('.mood').forEach(function (x) {
      x.setAttribute('aria-checked', 'false');
      x.querySelector('.dot').style.background = 'var(--line)';
    });
    document.querySelectorAll('#checkin-tags .tag').forEach(function (x) {
      x.setAttribute('aria-pressed', 'false');
    });
    $('#checkin-save').disabled = true;
    $('#checkin-hint').textContent = "Pick how you're feeling to save.";
    $('#checkin-form').hidden = false;
    $('#checkin-done').hidden = true;
  }

  /* A nudge, not an intervention. Shown only after a low check-in, worded so it
     never tells the user what is wrong with them. */
  function followUp(v) {
    const box = $('#checkin-followup');
    box.textContent = '';
    if (v > 2) {
      box.appendChild(el('p', null, 'It helps to keep a record of the steadier days too.'));
      return;
    }
    box.appendChild(el('p', null,
      'That sounds like a hard day. Writing it down counts for something, and so does telling one person.'));
    const a = el('a', 'btn btn-ghost btn-sm', 'See where to get support');
    a.href = '#support';
    box.appendChild(a);
  }

  function saveCheckin(e) {
    e.preventDefault();
    if (mood == null) return;
    const rec = {
      ts: Date.now(),
      mood: mood,
      tags: Array.from(chosenTags),
      note: $('#checkin-note').value.trim()
    };
    const v = mood;
    DB.addCheckin(rec).then(function () {
      $('#checkin-form').hidden = true;
      $('#checkin-done').hidden = false;
      followUp(v);
      renderHistory();
    }).catch(function (err) {
      toast('Could not save: ' + err.message);
    });
  }

  /* ------------------------------------------------------------- journal */

  function saveEntry(e) {
    e.preventDefault();
    const body = $('#journal-body').value.trim();
    if (!body) return;
    DB.addEntry({
      ts: Date.now(),
      title: $('#journal-title').value.trim(),
      body: body
    }).then(function () {
      $('#journal-title').value = '';
      $('#journal-body').value = '';
      $('#journal-save').disabled = true;
      toast('Entry saved on this device');
      renderJournal();
    }).catch(function (err) {
      toast('Could not save: ' + err.message);
    });
  }

  function renderJournal() {
    return DB.entries().then(function (rows) {
      const list = $('#journal-list');
      list.textContent = '';
      if (!rows.length) {
        list.appendChild(el('p', 'empty', 'Nothing written yet.'));
        return;
      }
      rows.forEach(function (r) {
        const card = el('article', 'entry');
        const head = el('div', 'entry-head');
        head.appendChild(el('span', 'entry-title', r.title || 'Untitled'));
        head.appendChild(el('span', 'entry-when', when(r.ts)));
        card.appendChild(head);
        card.appendChild(el('p', 'entry-body', r.body));

        const act = el('div', 'entry-actions');
        const del = el('button', 'btn btn-ghost btn-sm', 'Delete');
        del.type = 'button';
        del.addEventListener('click', function () {
          if (!confirm('Delete this entry? This cannot be undone.')) return;
          DB.deleteEntry(r.id).then(function () { toast('Entry deleted'); renderJournal(); });
        });
        act.appendChild(del);
        card.appendChild(act);
        list.appendChild(card);
      });
    });
  }

  /* ------------------------------------------------------------- history */

  function renderSpark(rows) {
    const spark = $('#spark');
    spark.textContent = '';
    const days = [];
    const today = new Date(); today.setHours(0, 0, 0, 0);

    for (let i = 13; i >= 0; i--) {
      const start = new Date(today); start.setDate(today.getDate() - i);
      const end = new Date(start); end.setDate(start.getDate() + 1);
      const inDay = rows.filter(function (r) { return r.ts >= start.getTime() && r.ts < end.getTime(); });
      let avg = null;
      if (inDay.length) {
        avg = inDay.reduce(function (s, r) { return s + r.mood; }, 0) / inDay.length;
      }
      days.push({ date: start, avg: avg });
    }

    days.forEach(function (d) {
      const col = el('div', 'spark-col');
      const bar = el('div', 'spark-bar');
      if (d.avg == null) {
        bar.classList.add('none');
        col.title = d.date.toLocaleDateString('en-ZA', { day: 'numeric', month: 'short' }) + ': no check-in';
      } else {
        bar.style.height = Math.round((d.avg / 5) * 100) + '%';
        bar.style.background = moodOf(Math.round(d.avg)).tone;
        col.title = d.date.toLocaleDateString('en-ZA', { day: 'numeric', month: 'short' })
                  + ': ' + moodOf(Math.round(d.avg)).label;
      }
      col.appendChild(bar);
      spark.appendChild(col);
    });

    const withData = days.filter(function (d) { return d.avg != null; }).length;
    $('#history-summary').textContent = withData
      ? 'You checked in on ' + withData + ' of the last 14 days.'
      : 'No check-ins in the last 14 days.';
  }

  function renderHistory() {
    return DB.checkins().then(function (rows) {
      renderSpark(rows);

      const list = $('#history-list');
      list.textContent = '';
      if (!rows.length) {
        list.appendChild(el('p', 'empty', 'No check-ins yet.'));
        return;
      }
      rows.forEach(function (r) {
        const m = moodOf(r.mood);
        const card = el('article', 'entry');

        const head = el('div', 'entry-head');
        const label = el('span', 'entry-mood');
        const dot = el('span', 'dot');
        dot.style.background = m.tone;
        label.appendChild(dot);
        label.appendChild(el('span', null, m.label));
        head.appendChild(label);
        head.appendChild(el('span', 'entry-when', when(r.ts)));
        card.appendChild(head);

        if (r.note) card.appendChild(el('p', 'entry-body', r.note));

        if (r.tags && r.tags.length) {
          const tags = el('div', 'entry-tags');
          r.tags.forEach(function (t) { tags.appendChild(el('span', 'chip', t)); });
          card.appendChild(tags);
        }

        const act = el('div', 'entry-actions');
        const del = el('button', 'btn btn-ghost btn-sm', 'Delete');
        del.type = 'button';
        del.addEventListener('click', function () {
          if (!confirm('Delete this check-in? This cannot be undone.')) return;
          DB.deleteCheckin(r.id).then(function () { toast('Check-in deleted'); renderHistory(); });
        });
        act.appendChild(del);
        card.appendChild(act);

        list.appendChild(card);
      });
    });
  }

  /* ------------------------------------------------------------ settings */

  function renderStorage() {
    const line = $('#storage-line');
    Promise.all([DB.checkins(), DB.entries()]).then(function (r) {
      line.textContent = r[0].length + ' check-in(s) and ' + r[1].length + ' journal entry(ies) stored here.';
    });

    DB.getPref('topics', []).then(function (t) {
      $('#topics-line').textContent = (t && t.length)
        ? 'You said these are on your mind: ' + t.join(', ') + '.'
        : 'You have not picked anything yet.';
    });

    renderCryptoState();
    renderAccount();
  }

  /* Says plainly what the encryption does and does not protect against. An
     app that overstates this is worse than one that says nothing. */
  function renderCryptoState() {
    const state = $('#crypto-state');
    const explain = $('#crypto-explain');
    explain.textContent = '';

    if (!Crypto.supported()) {
      state.textContent = 'This browser cannot encrypt storage.';
      explain.appendChild(el('p', null,
        'Your entries are saved in plain form. A newer browser will encrypt them.'));
      $('#passphrase-form').hidden = true;
      return;
    }

    if (Crypto.mode() === 'passphrase') {
      state.textContent = 'Protected by your passphrase.';
      explain.appendChild(el('p', null,
        'Your entries cannot be read without it, on this device or anywhere else.'));
      $('#passphrase-form').hidden = true;
      $('#lock-now').hidden = false;
      return;
    }

    state.textContent = 'Encrypted with a key held by this device.';
    explain.appendChild(el('p', null,
      'Copying the database off this device gives someone ciphertext they cannot read. '
      + 'It does not stop anyone who can already unlock this phone and open the app.'));
    explain.appendChild(el('p', null,
      'A passphrase closes that gap.'));
    $('#passphrase-form').hidden = false;
    $('#lock-now').hidden = true;
  }

  function setPassphrase(e) {
    e.preventDefault();
    const a = $('#pass-new').value;
    const b = $('#pass-again').value;
    const hint = $('#pass-hint');

    if (a.length < 8) { hint.textContent = 'Use at least 8 characters.'; return; }
    if (a !== b) { hint.textContent = 'The two do not match.'; return; }

    hint.textContent = 'Re-encrypting your entries...';

    /* Read everything under the old key first, switch the key, then write it
       all back. Done in this order so nothing is left unreadable if the tab
       is closed halfway. */
    Promise.all([DB.checkins(), DB.entries()]).then(function (existing) {
      return Crypto.preparePassphrase(a).then(function (material) {
        return DB.saveVault(material).then(function () {
          return DB.clearAll().then(function () {
            const writes = [];
            existing[0].forEach(function (c) {
              writes.push(DB.addCheckin({ ts: c.ts, mood: c.mood, tags: c.tags, note: c.note }));
            });
            existing[1].forEach(function (j) {
              writes.push(DB.addEntry({ ts: j.ts, title: j.title, body: j.body }));
            });
            return Promise.all(writes);
          });
        });
      });
    }).then(function () {
      $('#pass-new').value = '';
      $('#pass-again').value = '';
      hint.textContent = 'If you forget it, your entries cannot be recovered by anyone.';
      toast('Passphrase set');
      renderStorage();
    }).catch(function (err) {
      hint.textContent = 'Could not set a passphrase: ' + err.message;
    });
  }

  function lockNow() {
    Crypto.lock();
    $('#lockscreen').hidden = false;
    $('#unlock-pass').value = '';
    $('#unlock-pass').focus();
  }

  function unlock(e) {
    e.preventDefault();
    const hint = $('#unlock-hint');
    hint.textContent = 'Checking...';
    DB.vault().then(function (v) {
      return Crypto.unlock($('#unlock-pass').value, v.salt, v.verifier);
    }).then(function (ok) {
      if (!ok) { hint.textContent = 'That passphrase did not work.'; return; }
      $('#lockscreen').hidden = true;
      $('#unlock-pass').value = '';
      hint.textContent = 'There is no way to reset this. Only you have it.';
      route();
    }).catch(function (err) {
      hint.textContent = 'Could not unlock: ' + err.message;
    });
  }

  /* ------------------------------------------------------------ account */

  let authMode = 'register';        // or 'signin'

  function openAuth(mode) {
    authMode = mode;
    const registering = (mode === 'register');

    $('#auth-title').textContent = registering ? 'Create an account' : 'Sign in';
    $('#auth-submit').textContent = registering ? 'Create account' : 'Sign in';
    $('#auth-display-field').hidden = !registering;
    $('#auth-pw-note').textContent = registering
      ? 'At least 8 characters.'
      : 'The password you chose when you signed up.';
    $('#auth-password').setAttribute('autocomplete', registering ? 'new-password' : 'current-password');
    $('#auth-switch-text').textContent = registering
      ? 'Already have an account?' : 'No account yet?';
    $('#auth-switch').textContent = registering ? 'Sign in instead' : 'Create one instead';

    $('#auth-hint').textContent = navigator.onLine
      ? '' : 'You are offline. Signing in needs a connection.';
    $('#auth-username').value = '';
    $('#auth-password').value = '';
    $('#auth-display').value = '';
    $('#auth').hidden = false;
    $('#auth-username').focus();
  }

  function submitAuth(e) {
    e.preventDefault();
    const hint = $('#auth-hint');
    const username = $('#auth-username').value.trim();
    const password = $('#auth-password').value;
    const display = $('#auth-display').value.trim();

    if (username.length < 3 || username.length > 50) {
      hint.textContent = 'Username must be 3 to 50 characters.'; return;
    }
    if (!/^[A-Za-z0-9._-]+$/.test(username)) {
      hint.textContent = 'Username may use letters, numbers, dot, dash and underscore.'; return;
    }
    if (authMode === 'register' && password.length < 8) {
      hint.textContent = 'Password must be at least 8 characters.'; return;
    }
    if (!password) { hint.textContent = 'Enter your password.'; return; }

    hint.textContent = authMode === 'register' ? 'Creating your account...' : 'Signing in...';
    $('#auth-submit').disabled = true;

    const call = authMode === 'register'
      ? Api.register(username, password, display || username)
      : Api.signIn(username, password);

    call.then(function (data) {
      return DB.setPref('authToken', data.token).then(function () {
        $('#auth').hidden = true;
        toast('Signed in as ' + data.user.displayName);
        renderAccount();
      });
    }).catch(function (err) {
      hint.textContent = err.offline
        ? 'No connection to the server. You can keep using Mindmate as a guest.'
        : err.message;
    }).then(function () {
      $('#auth-submit').disabled = false;
    });
  }

  function signOut() {
    if (!confirm('Sign out? Your check-ins and journal stay on this device.')) return;
    Api.signOut().then(function () {
      return DB.setPref('authToken', null);
    }).then(function () {
      toast('Signed out');
      renderAccount();
    });
  }

  /* The Data tab's account card. Says what an account does and does not do,
     because "sign in" with no reason given is exactly what students are wary
     of in an app like this. */
  function renderAccount() {
    const state = $('#account-state');
    const explain = $('#account-explain');
    const actions = $('#account-actions');
    explain.textContent = '';
    actions.textContent = '';

    if (Api.isSignedIn() && Api.currentUser()) {
      const badge = el('span', 'account-badge', 'Signed in');
      explain.appendChild(badge);
      state.textContent = 'Signed in as ' + Api.currentUser().displayName
                        + ' (' + Api.currentUser().username + ').';
      explain.appendChild(el('p', null,
        'Nothing has been uploaded. Sync is not switched on yet, so your check-ins and '
        + 'journal are still only on this device.'));
      const out = el('button', 'btn btn-ghost', 'Sign out');
      out.type = 'button';
      out.addEventListener('click', signOut);
      actions.appendChild(out);
      return;
    }

    if (Api.isSignedIn()) {
      state.textContent = 'Signed in. Waiting for a connection to confirm.';
      const out = el('button', 'btn btn-ghost', 'Sign out');
      out.type = 'button';
      out.addEventListener('click', signOut);
      actions.appendChild(out);
      return;
    }

    state.textContent = "You're using Mindmate as a guest.";
    explain.appendChild(el('p', null,
      'Everything works as a guest. An account is only for the parts that need a connection, '
      + 'like backing your entries up to another device.'));

    const create = el('button', 'btn btn-primary', 'Create an account');
    create.type = 'button';
    create.addEventListener('click', function () { openAuth('register'); });

    const signin = el('button', 'btn btn-ghost', 'Sign in');
    signin.type = 'button';
    signin.addEventListener('click', function () { openAuth('signin'); });

    actions.appendChild(create);
    actions.appendChild(signin);
  }

  /* --------------------------------------------------------- onboarding */

  const picked = { topics: new Set(), age: null };
  let editingTopics = false;

  function buildOnboarding() {
    const row = $('#onboard-topics');
    TOPICS.forEach(function (name) {
      const b = el('button', 'tag', name);
      b.type = 'button';
      b.setAttribute('aria-pressed', 'false');
      b.addEventListener('click', function () {
        const on = picked.topics.has(name);
        if (on) { picked.topics.delete(name); } else { picked.topics.add(name); }
        b.setAttribute('aria-pressed', on ? 'false' : 'true');
      });
      row.appendChild(b);
    });

    $('#onboard-age').querySelectorAll('[data-age]').forEach(function (b) {
      b.addEventListener('click', function () {
        const already = picked.age === b.dataset.age;
        picked.age = already ? null : b.dataset.age;
        $('#onboard-age').querySelectorAll('[data-age]').forEach(function (x) {
          x.setAttribute('aria-pressed', (!already && x === b) ? 'true' : 'false');
        });
      });
    });

    document.querySelectorAll('#onboarding [data-next]').forEach(function (b) {
      b.addEventListener('click', function () { moveStep(1); });
    });
    document.querySelectorAll('#onboarding [data-back]').forEach(function (b) {
      b.addEventListener('click', function () { moveStep(-1); });
    });

    $('#onboard-finish').addEventListener('click', finishOnboarding);
  }

  function moveStep(delta) {
    /* Reopened from the Data tab to change topics only: Continue saves and
       closes rather than walking on through the rest of first-run. */
    if (editingTopics) {
      editingTopics = false;
      DB.setPref('topics', Array.from(picked.topics)).then(function () {
        $('#onboarding').hidden = true;
        toast('Saved');
        renderStorage();
      });
      return;
    }
    const steps = Array.prototype.slice.call(document.querySelectorAll('#onboarding .step'));
    const i = steps.findIndex(function (s) { return !s.hidden; });
    const next = Math.min(Math.max(i + delta, 0), steps.length - 1);
    steps.forEach(function (s, n) { s.hidden = (n !== next); });
    $('#onboarding').scrollTop = 0;
  }

  function finishOnboarding() {
    Promise.all([
      DB.setPref('onboarded', true),
      DB.setPref('topics', Array.from(picked.topics)),
      DB.setPref('ageBand', picked.age)
    ]).then(function () {
      $('#onboarding').hidden = true;
      route();
    });
  }

  function reopenTopics() {
    picked.topics.clear();
    editingTopics = true;
    DB.getPref('topics', []).then(function (saved) {
      (saved || []).forEach(function (t) { picked.topics.add(t); });
      $('#onboard-topics').querySelectorAll('.tag').forEach(function (b) {
        b.setAttribute('aria-pressed', picked.topics.has(b.textContent) ? 'true' : 'false');
      });
      const steps = document.querySelectorAll('#onboarding .step');
      steps.forEach(function (s) { s.hidden = (s.dataset.step !== '3'); });
      $('#onboarding').hidden = false;
    });
  }

  function exportData() {
    DB.exportAll().then(function (data) {
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'mindmate-export-' + new Date().toISOString().slice(0, 10) + '.json';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
    });
  }

  function wipe() {
    if (!confirm('Delete every check-in and journal entry on this device? This cannot be undone.')) return;
    if (!confirm('Last check. Everything will be permanently deleted.')) return;
    /* Signing out too. On a shared or borrowed phone, "delete all my data"
       that leaves a live session behind would not mean much. */
    DB.clearAll()
      .then(function () { return Api.signOut(); })
      .then(function () { return DB.setPref('authToken', null); })
      .then(function () {
        toast('All data deleted');
        renderHistory(); renderJournal(); renderStorage();
      });
  }

  /* ------------------------------------------------------------- routing */

  const VIEWS = ['home', 'journal', 'history', 'support', 'settings'];

  function route() {
    let name = (location.hash || '#home').slice(1);
    if (VIEWS.indexOf(name) === -1) name = 'home';

    VIEWS.forEach(function (v) {
      $('#view-' + v).hidden = (v !== name);
    });
    document.querySelectorAll('.tabbar a').forEach(function (a) {
      if (a.dataset.tab === name) { a.setAttribute('aria-current', 'page'); }
      else { a.removeAttribute('aria-current'); }
    });

    if (name === 'history')  renderHistory();
    if (name === 'journal')  renderJournal();
    if (name === 'settings') renderStorage();

    window.scrollTo(0, 0);
    document.title = name === 'home' ? 'Mindmate' : 'Mindmate - ' + name;
  }

  /* ------------------------------------------------------- connectivity */

  function netState() {
    const n = $('#net-state');
    if (navigator.onLine) {
      n.dataset.state = 'online';
      n.textContent = 'Offline ready';
    } else {
      n.dataset.state = 'offline';
      n.textContent = 'Offline - still working';
    }
  }

  /* ----------------------------------------------------------------- go */

  function init() {
    $('#greeting').textContent = greet();
    buildMoods();
    buildTags();
    buildOnboarding();

    $('#checkin-form').addEventListener('submit', saveCheckin);
    $('#checkin-again').addEventListener('click', resetCheckin);

    $('#journal-form').addEventListener('submit', saveEntry);
    $('#journal-body').addEventListener('input', function () {
      $('#journal-save').disabled = !this.value.trim();
    });

    $('#export-btn').addEventListener('click', exportData);
    $('#wipe-btn').addEventListener('click', wipe);
    $('#passphrase-form').addEventListener('submit', setPassphrase);
    $('#lock-now').addEventListener('click', lockNow);
    $('#unlock-form').addEventListener('submit', unlock);
    $('#edit-topics').addEventListener('click', reopenTopics);
    $('#auth-form').addEventListener('submit', submitAuth);
    $('#auth-cancel').addEventListener('click', function () { $('#auth').hidden = true; });
    $('#auth-switch').addEventListener('click', function () {
      openAuth(authMode === 'register' ? 'signin' : 'register');
    });

    window.addEventListener('hashchange', route);
    window.addEventListener('online', netState);
    window.addEventListener('offline', netState);

    netState();

    /* Storage must be open and its key ready before anything is read or
       written, so the first render waits on it. */
    DB.init().then(function () {
      if (Crypto.mode() === 'passphrase' && !Crypto.isUnlocked()) {
        $('#lockscreen').hidden = false;
        $('#unlock-pass').focus();
        return;
      }
      /* Restoring a session must never hold the app up: if the server is slow
         or unreachable the student still gets straight to their check-in. */
      DB.getPref('authToken', null)
        .then(function (t) { return Api.restore(t); })
        .then(renderAccount)
        .catch(function () { });

      return DB.getPref('onboarded', false).then(function (done) {
        if (!done) {
          $('#onboarding').hidden = false;
          return;
        }
        route();
      });
    }).catch(function (err) {
      console.error('Storage failed to open:', err);
      toast('Storage could not be opened on this device.');
      route();
    });

    /* Registering on 'load' keeps the first paint clear of the cache write, but
       if the document has already finished loading that event will never fire
       again, so cover both cases. */
    if ('serviceWorker' in navigator) {
      const register = function () {
        navigator.serviceWorker.register('sw.js').catch(function (err) {
          console.warn('Service worker did not register:', err);
        });
      };
      if (document.readyState === 'complete') { register(); }
      else { window.addEventListener('load', register); }
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
