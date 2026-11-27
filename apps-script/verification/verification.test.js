// Node's standard test runner only: no Firebase credentials and no real email.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createHmac } = require('node:crypto');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { runInNewContext } = require('node:vm');
function fixture(options = {}) {
  const props = { OTP_SECRET: 'ab'.repeat(32) }, sent = [], updates = [], calls = [];
  let clock = 1700000000000;
  const user = { localId: 'alice', email: 'alice@example.test', emailVerified: false };
  const jwt = 'header.' + Buffer.from(JSON.stringify({ sub: 'alice', aud: 'test-project', iss: 'https://securetoken.google.com/test-project' })).toString('base64url') + '.signature';
  const context = {
    FIREBASE_PROJECT_ID: 'test-project', FIREBASE_WEB_API_KEY: 'test-key',
    Date: { now: () => clock },
    PropertiesService: { getScriptProperties: () => ({
      getProperty: key => props[key] || null, setProperty: (key, value) => { props[key] = value; },
      getProperties: () => ({ ...props }), deleteProperty: key => { delete props[key]; }
    }) },
    LockService: { getScriptLock: () => ({ tryLock: () => !options.busy, releaseLock() {} }) },
    Utilities: { Charset: { UTF_8: 'utf8' },
      computeHmacSha256Signature: (value, secret) => [...createHmac('sha256', secret).update(value).digest()],
      base64DecodeWebSafe: value => Buffer.from(value, 'base64url'),
      newBlob: value => ({ getDataAsString: () => value.toString('utf8') }) },
    ScriptApp: { getOAuthToken: () => 'server-oauth-not-client-visible' },
    MailApp: { sendEmail: value => { if (options.mailFails) throw Error('offline'); sent.push(value); }, getRemainingDailyQuota: () => 100 },
    ContentService: { MimeType: { JSON: 'application/json' }, createTextOutput: text => ({ text, setMimeType() { return this; } }) },
    UrlFetchApp: { fetch: (url, request) => {
      const body = JSON.parse(request.payload); calls.push({ url, body });
      let data = {}, status = 200;
      if (options.firebaseFails) status = 503;
      else if (url.includes('accounts:lookup?')) {
        if (body.idToken !== jwt) status = 400;
        else data = { users: [user] };
      } else if (url.endsWith('accounts:lookup')) data = { users: options.registered || options.afterSignup ? [user] : [] };
      else if (url.endsWith('accounts:update')) { updates.push(body); user.emailVerified = true; }
      else throw Error('Unexpected API');
      return { getResponseCode: () => status, getContentText: () => JSON.stringify(data) };
    } }
  };
  for (const name of ['Code.gs', 'EmailTemplate.gs']) runInNewContext(readFileSync(join(__dirname, name), 'utf8'), context);
  const call = (action, values = {}) => JSON.parse(context.doPost({ postData: { contents: JSON.stringify({ protocol: 1, action, email: user.email, ...values }) } }).text);
  const start = () => {
    const result = call('request'); assert.equal(result.ok, true);
    return { token: result.token, code: sent.at(-1).body.match(/\d{6}/)[0] };
  };
  return { props, sent, updates, calls, call, start, context, jwt, user, tick: ms => { clock += ms; } };
}
test('direct Apps Script verification sends branded mail and completes Firebase verification', () => {
  const options = {}, f = fixture(options), challenge = f.start();
  assert.match(f.sent[0].htmlBody, /VERIFICACIÓN DE CORREO/);
  assert.equal(f.call('complete', { ...challenge, idToken: f.jwt }).ok, false);
  assert.equal(f.call('verify', challenge).verified, true);
  options.afterSignup = true;
  assert.equal(f.call('complete', { ...challenge, idToken: f.jwt }).verified, true);
  assert.equal(f.updates.length, 1);
  assert.deepEqual(f.updates[0], { localId: 'alice', emailVerified: true });
  assert.equal(f.call('complete', { ...challenge, idToken: f.jwt }).ok, true);
  assert.equal(JSON.stringify(f.props).includes(challenge.token), false);
  const saved = JSON.parse(Object.entries(f.props).find(([k]) => k.startsWith('otp:'))[1]);
  assert.equal(saved.code, undefined);
});
test('registered account is rejected before sending, but its authenticated unverified owner can resume', () => {
  const f = fixture({ registered: true });
  assert.equal(f.call('request').error, 'EMAIL_ALREADY_REGISTERED');
  assert.equal(f.sent.length, 0);
  f.tick(60001);
  assert.equal(f.call('request', { idToken: f.jwt }).ok, true);
  f.tick(60001); f.user.emailVerified = true;
  assert.equal(f.call('request', { idToken: f.jwt }).error, 'EMAIL_ALREADY_REGISTERED');
});
test('five wrong codes exhaust attempts, even if the correct code follows', () => {
  const f = fixture(), c = f.start(), wrong = c.code === '000000' ? '000001' : '000000';
  for (let i = 0; i < 5; i++) assert.equal(f.call('verify', { ...c, code: wrong }).ok, false);
  assert.equal(f.call('verify', c).ok, false);
});
test('challenge is bound to email and token, and expires after ten minutes', () => {
  const f = fixture(), c = f.start();
  assert.equal(f.call('verify', { ...c, email: 'other@example.test' }).ok, false);
  assert.equal(f.call('verify', { ...c, token: '00'.repeat(32) }).ok, false);
  f.tick(600001);
  assert.equal(f.call('verify', c).ok, false);
});
test('resending is throttled and invalidates the old challenge', () => {
  const f = fixture(), first = f.start();
  assert.equal(f.call('request').error, 'RATE_LIMITED');
  f.tick(60001); const next = f.start();
  assert.notEqual(next.token, first.token);
  assert.equal(f.call('verify', first).ok, false);
  assert.equal(f.call('verify', next).ok, true);
});
test('invalid Firebase tokens cannot complete a verified challenge', () => {
  const f = fixture({ afterSignup: false }), c = f.start();
  f.call('verify', c);
  assert.equal(f.call('complete', { ...c, idToken: 'forged' }).ok, false);
  assert.equal(f.updates.length, 0);
});
test('Firebase and delivery failures do not claim a code was sent', () => {
  const f = fixture({ firebaseFails: true });
  assert.equal(f.call('request').error, 'SERVICE_UNAVAILABLE');
  assert.equal(f.sent.length, 0);
  const mail = fixture({ mailFails: true });
  assert.equal(mail.call('request').error, 'MAIL_DELIVERY_FAILED');
  assert.equal(mail.sent.length, 0);
});
test('missing configuration, lock contention and unsafe recipient fail closed', () => {
  const f = fixture(); delete f.props.OTP_SECRET;
  assert.equal(f.call('request').error, 'SERVICE_NOT_CONFIGURED');
  assert.equal(fixture({ busy: true }).call('request').error, 'SERVICE_BUSY');
  assert.equal(fixture().call('request', { email: 'a@example.test,b@example.test' }).ok, false);
});
test('per-email daily quota survives code expiry and global quota bounds requests', () => {
  const f = fixture();
  for (let i = 0; i < 5; i++) { f.start(); f.tick(600001); }
  assert.equal(f.call('request').error, 'RATE_LIMITED');
  const g = fixture();
  for (let i = 0; i < 60; i++) {
    assert.equal(g.call('request', { email: 'user' + i + '@example.test' }).ok, true); g.tick(60001);
  }
  assert.equal(g.call('request', { email: 'last@example.test' }).error, 'RATE_LIMITED');
});
