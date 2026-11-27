import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createEmailVerification, normalizeEmail } from './email-verification.js';

function fixture({ failMail = false, existing = null, lookupFailure = false } = {}) {
  let clock = 1700000000000, updates = 0;
  const documents = new Map(), sent = [];
  // Isolated transaction fixture; integration with the Firestore emulator remains separate.
  const db = { doc: path => path, async runTransaction(work) {
    const changes = new Map();
    const tx = {
      get: async ref => ({ data: () => structuredClone(changes.get(ref) || documents.get(ref)) }),
      set: (ref, data) => changes.set(ref, structuredClone(data)),
      update: (ref, data) => changes.set(ref, { ...(changes.get(ref) || documents.get(ref)), ...structuredClone(data) })
    };
    const result = await work(tx);
    for (const [ref, data] of changes) documents.set(ref, data);
    return result;
  } };
  const auth = { getUserByEmail: async () => {
    if (lookupFailure) throw Object.assign(Error('unavailable'), { code: 'auth/internal-error' });
    if (existing) return existing;
    throw Object.assign(Error('missing'), { code: 'auth/user-not-found' });
  }, getUser: async uid => ({ uid, email: 'alice@example.test' }),
    updateUser: async (uid, data) => { assert.equal(uid, 'alice'); assert.equal(data.emailVerified, true); updates++; } };
  const api = createEmailVerification({ db, auth, secret: 'a-test-only-secret-of-at-least-32-characters', now: () => clock,
    sendMail: async (email, code) => { if (failMail) throw Error('offline'); sent.push({ email, code }); } });
  return { api, documents, sent, tick: ms => { clock += ms; }, updates: () => updates };
}
const email = 'alice@example.test';
async function challenge(f) {
  const r = await f.api.request({ email }, 'test-address');
  return { email, token: r.token, code: f.sent.at(-1).code };
}
test('email verification requires a server code before completing Firebase verification', async () => {
  const f = fixture(), body = await challenge(f), user = { uid: 'alice', email };
  await assert.rejects(f.api.complete(body, user));
  assert.deepEqual(await f.api.verifyCode(body), { verified: true });
  await f.api.complete(body, user);
  await f.api.complete(body, user); // Safe retry after a lost response.
  assert.equal(f.updates(), 2);
  const state = [...f.documents.values()].find(x => x.email);
  assert.equal(state.code, undefined); assert.notEqual(state.codeHash, body.code);
  assert.equal(state.token, undefined);
});
test('wrong codes consume attempts and the correct code is rejected after five failures', async () => {
  const f = fixture(), body = await challenge(f);
  const wrong = body.code === '000000' ? '000001' : '000000';
  for (let i = 0; i < 5; i++) await assert.rejects(f.api.verifyCode({ ...body, code: wrong }));
  await assert.rejects(f.api.verifyCode(body));
  assert.equal(f.updates(), 0);
});
test('a challenge is bound to its email, capability, expiry and authenticated account', async () => {
  const f = fixture(), body = await challenge(f);
  await assert.rejects(f.api.verifyCode({ ...body, email: 'other@example.test' }));
  await assert.rejects(f.api.verifyCode({ ...body, token: '0'.repeat(64) }));
  await f.api.verifyCode(body);
  await assert.rejects(f.api.complete(body, { uid: 'other', email: 'other@example.test' }));
  f.tick(600001);
  await assert.rejects(f.api.verifyCode(body));
  await assert.rejects(f.api.complete(body, { uid: 'alice', email }));
});
test('resending is throttled and invalidates the previous challenge', async () => {
  const f = fixture(), old = await challenge(f);
  await assert.rejects(challenge(f));
  f.tick(60001);
  const next = await challenge(f);
  await assert.rejects(f.api.verifyCode(old));
  await f.api.verifyCode(next);
});
test('daily email limit persists past the ten-minute code lifetime', async () => {
  const f = fixture();
  for (let i = 0; i < 5; i++) { await challenge(f); f.tick(600001); }
  await assert.rejects(challenge(f));
});
test('a failed mail delivery cannot be verified or complete registration', async () => {
  const f = fixture({ failMail: true });
  await assert.rejects(challenge(f));
  assert.equal([...f.documents.values()].find(x => x.email).delivered, false);
  assert.equal(f.updates(), 0);
});
test('untrusted addresses cannot inject additional recipients', () => {
  assert.equal(normalizeEmail(' Alice@Example.test '), email);
  for (const value of ['a@example.test,b@example.test', 'a@example.test\nb@example.test', null, 'invalid']) {
    assert.throws(() => normalizeEmail(value));
  }
});
test('registered email is reported without sending a code', async () => {
  const f = fixture({ existing: { uid: 'alice', emailVerified: true } });
  await assert.rejects(challenge(f), { code: 'EMAIL_ALREADY_REGISTERED' });
  assert.equal(f.sent.length, 0);
});
test('an unverified existing account also requires its authenticated owner to resume', async () => {
  const f = fixture({ existing: { uid: 'alice', emailVerified: false } });
  await assert.rejects(challenge(f), { code: 'EMAIL_ALREADY_REGISTERED' });
  f.tick(60001);
  await f.api.request({ email }, 'test-address', { uid: 'alice' });
  assert.equal(f.sent.length, 1);
});
test('Firebase lookup failure is not interpreted as a new email', async () => {
  const f = fixture({ lookupFailure: true });
  await assert.rejects(challenge(f), { code: 'SERVICE_UNAVAILABLE' });
  assert.equal(f.sent.length, 0);
});
