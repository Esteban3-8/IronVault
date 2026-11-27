// Direct Android -> Apps Script -> Firebase verification, without a Node server.
// Install Code.gs + EmailTemplate.gs + FirebaseConfig.gs + appsscript.json together.
// Set OTP_SECRET in Script Properties to 32 CSPRNG bytes encoded as 64 hex characters.
// Never log request bodies, codes, ID tokens, OAuth tokens or Script Properties.

function response_(data) {
  data.protocol = 1;
  return ContentService.createTextOutput(JSON.stringify(data)).setMimeType(ContentService.MimeType.JSON);
}
function fail_(code) { var error = new Error(code); error.publicCode = code; throw error; }
function doGet() { return response_({ ok: false, error: 'USE_POST' }); }

function doPost(e) {
  var lock = LockService.getScriptLock(), locked = false;
  try {
    if (!e || !e.postData || e.postData.contents.length > 12000) fail_('VERIFICATION_FAILED');
    var body = JSON.parse(e.postData.contents);
    if (body.protocol !== 1 || ['request', 'verify', 'complete'].indexOf(body.action) < 0) fail_('SCRIPT_NEEDS_UPDATE');
    locked = lock.tryLock(5000);
    if (!locked) fail_('SERVICE_BUSY');
    var store = PropertiesService.getScriptProperties();
    var secret = store.getProperty('OTP_SECRET');
    if (!secret || !/^[a-fA-F0-9]{64}$/.test(secret) || !FIREBASE_PROJECT_ID || !FIREBASE_WEB_API_KEY) fail_('SERVICE_NOT_CONFIGURED');
    var email = email_(body.email);
    var key = 'otp:' + mac_('email:' + email, secret);
    var state = JSON.parse(store.getProperty(key) || 'null');
    var now = Date.now();
    if (body.action === 'request') return response_(requestCode_(body, email, key, state, store, secret, now));
    if (!state || state.expires <= now || !state.delivered || typeof body.token !== 'string' || !/^[a-f0-9]{64}$/.test(body.token)
        || !equal_(state.tokenHash, mac_('token:' + body.token, secret))) fail_('VERIFICATION_FAILED');
    if (body.action === 'verify') {
      if (state.claimedUid || state.attempts >= 5 || typeof body.code !== 'string' || !/^\d{6}$/.test(body.code)) fail_('VERIFICATION_FAILED');
      if (!state.verified) {
        state.attempts++;
        state.verified = equal_(state.codeHash, mac_('code:' + email + ':' + body.token + ':' + body.code, secret));
        store.setProperty(key, JSON.stringify(state)); // Commit failed attempts as well.
      }
      if (!state.verified) fail_('VERIFICATION_FAILED');
      return response_({ ok: true, verified: true });
    }
    if (!state.verified) fail_('VERIFICATION_FAILED');
    var user = authenticatedUser_(body.idToken);
    if (email_(user.email) !== email || user.disabled || (state.claimedUid && state.claimedUid !== user.localId)) fail_('VERIFICATION_FAILED');
    var account = lookupEmail_(email);
    if (!account || account.localId !== user.localId || account.disabled) fail_('VERIFICATION_FAILED');
    state.claimedUid = user.localId;
    store.setProperty(key, JSON.stringify(state)); // Same UID may retry an interrupted update.
    adminCall_('accounts:update', { localId: user.localId, emailVerified: true });
    return response_({ ok: true, verified: true });
  } catch (error) {
    return response_({ ok: false, error: error.publicCode || 'SERVICE_UNAVAILABLE' });
  } finally { if (locked) lock.releaseLock(); }
}

function email_(value) {
  if (typeof value !== 'string' || value.length > 254) fail_('VERIFICATION_FAILED');
  var email = value.trim().toLowerCase();
  if (!/^[^\s@<>;,]+@[^\s@<>;,]+\.[^\s@<>;,]+$/.test(email)) fail_('VERIFICATION_FAILED');
  return email;
}
function mac_(value, secret) {
  return Utilities.computeHmacSha256Signature(value, secret, Utilities.Charset.UTF_8)
    .map(function (b) { return ('0' + ((b + 256) % 256).toString(16)).slice(-2); }).join('');
}
function equal_(a, b) {
  if (typeof a !== 'string' || a.length !== b.length) return false;
  var diff = 0;
  for (var i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function requestCode_(body, email, key, old, store, secret, now) {
  // Apps Script does not expose a trustworthy client IP: use conservative global
  // and per-email limits. A public production deployment needs stronger abuse controls.
  var day = Math.floor(now / 86400000), minute = Math.floor(now / 60000);
  var rate = JSON.parse(store.getProperty('OTP_RATE') || '{}');
  var daily = rate.day === day ? rate.daily : 0;
  var perMinute = rate.minute === minute ? rate.perMinute : 0;
  var emailCount = old && old.day === day ? old.count : 0;
  if (daily >= 60 || perMinute >= 10 || emailCount >= 5 || (old && now - old.sentAt < 60000)) fail_('RATE_LIMITED');
  store.setProperty('OTP_RATE', JSON.stringify({ day: day, daily: daily + 1, minute: minute, perMinute: perMinute + 1 }));
  // Remove only expired rate records; preserve today's counters after code expiry.
  var all = store.getProperties();
  Object.keys(all).forEach(function (name) {
    if (name.indexOf('otp:') === 0) {
      var record = JSON.parse(all[name]);
      if (record.sentAt + 86400000 < now) store.deleteProperty(name);
    }
  });
  // Native HMAC-SHA256 PRF with an independently generated 256-bit secret,
  // a persistent monotonic counter and distinct labels. No Math.random or client code.
  var seq = Number(store.getProperty('OTP_SEQUENCE') || '0') + 1;
  if (!Number.isSafeInteger(seq)) fail_('SERVICE_NOT_CONFIGURED');
  store.setProperty('OTP_SEQUENCE', String(seq));
  var nonce = String(seq) + ':' + String(now);
  var token = mac_('capability:' + nonce, secret);
  var code;
  for (var n = 0; n < 100; n++) {
    var sample = parseInt(mac_('digits:' + nonce + ':' + n, secret).slice(0, 8), 16);
    if (sample < 4294000000) { code = ('000000' + (sample % 1000000)).slice(-6); break; }
  }
  if (!code) fail_('SERVICE_UNAVAILABLE');
  var state = { tokenHash: mac_('token:' + token, secret), codeHash: mac_('code:' + email + ':' + token + ':' + code, secret),
    expires: now + 600000, sentAt: now, day: day, count: emailCount + 1, attempts: 0, verified: false, delivered: false };
  store.setProperty(key, JSON.stringify(state));
  var existing = lookupEmail_(email);
  if (existing) {
    if (existing.emailVerified || !body.idToken) fail_('EMAIL_ALREADY_REGISTERED');
    var owner = authenticatedUser_(body.idToken);
    if (owner.localId !== existing.localId || email_(owner.email) !== email || existing.disabled) fail_('EMAIL_ALREADY_REGISTERED');
  }
  try {
    MailApp.sendEmail({ to: email, name: 'IronVault', subject: 'Código de verificación - IronVault',
      body: 'Tu código de verificación es: ' + code + '\nCaduca en 10 minutos desde la solicitud. No lo compartas.',
      htmlBody: verificationEmailHtml(code) });
  } catch (_) { fail_('MAIL_DELIVERY_FAILED'); }
  state.delivered = true;
  store.setProperty(key, JSON.stringify(state));
  return { ok: true, token: token, expiresIn: 600 };
}

function adminCall_(operation, body) {
  return fetchJson_('https://identitytoolkit.googleapis.com/v1/projects/' + encodeURIComponent(FIREBASE_PROJECT_ID) + '/' + operation,
    body, ScriptApp.getOAuthToken());
}
function lookupEmail_(email) {
  var result = adminCall_('accounts:lookup', { email: [email] });
  if (!result.users || result.users.length === 0) return null;
  if (result.users.length !== 1) fail_('SERVICE_UNAVAILABLE');
  return result.users[0];
}
function authenticatedUser_(idToken) {
  if (typeof idToken !== 'string' || idToken.length > 8192) fail_('VERIFICATION_FAILED');
  // Firebase validates the ID token. The additional aud/iss check binds the project.
  var result = fetchJson_('https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=' + encodeURIComponent(FIREBASE_WEB_API_KEY), { idToken: idToken });
  var payload;
  try { payload = JSON.parse(Utilities.newBlob(Utilities.base64DecodeWebSafe(idToken.split('.')[1])).getDataAsString()); }
  catch (_) { fail_('VERIFICATION_FAILED'); }
  if (payload.aud !== FIREBASE_PROJECT_ID || payload.iss !== 'https://securetoken.google.com/' + FIREBASE_PROJECT_ID
      || !result.users || result.users.length !== 1 || payload.sub !== result.users[0].localId) fail_('VERIFICATION_FAILED');
  return result.users[0];
}
function fetchJson_(url, body, bearer) {
  var options = { method: 'post', contentType: 'application/json', payload: JSON.stringify(body), muteHttpExceptions: true };
  if (bearer) options.headers = { Authorization: 'Bearer ' + bearer };
  var response = UrlFetchApp.fetch(url, options);
  if (response.getResponseCode() !== 200) fail_('SERVICE_UNAVAILABLE');
  return JSON.parse(response.getContentText());
}

// Run manually once in the Apps Script editor to authorize scopes/check Firebase access.
// This sends no mail and does not change users. Do not expose this as a web action.
function comprobarConfiguracion() {
  var secret = PropertiesService.getScriptProperties().getProperty('OTP_SECRET');
  if (!secret || !/^[a-fA-F0-9]{64}$/.test(secret)) throw new Error('Configura OTP_SECRET en las propiedades del script');
  adminCall_('accounts:lookup', { email: ['configuration-check@example.invalid'] });
  MailApp.getRemainingDailyQuota();
}
