import { randomInt, randomBytes, createHmac, timingSafeEqual } from 'node:crypto';
import assert from 'node:assert/strict';

const TTL = 10 * 60 * 1000;
export class EmailVerificationError extends Error {
  constructor(code) { super(code); this.code = code; }
}
const fail = code => { throw new EmailVerificationError(code); };
export function normalizeEmail(value) {
  assert(typeof value === 'string' && value.length <= 254);
  const email = value.trim().toLowerCase();
  assert(/^[^\s@<>;,]+@[^\s@<>;,]+\.[^\s@<>;,]+$/.test(email));
  return email;
}
export function createEmailVerification({ db, auth, sendMail, secret, now = Date.now }) {
  const mac = value => {
    if (typeof secret !== 'string' || secret.length < 32) fail('SERVICE_NOT_CONFIGURED');
    return createHmac('sha256', secret).update(value).digest('hex');
  };
  const same = (a, b) => typeof a === 'string' && a.length === b.length && timingSafeEqual(Buffer.from(a), Buffer.from(b));
  const refFor = email => db.doc(`registrationChallenges/${mac(`email:${email}`)}`);
  const capability = body => {
    assert(typeof body.token === 'string' && /^[a-f0-9]{64}$/.test(body.token));
    return mac(`token:${body.token}`);
  };
  async function request(body, address, user = null) {
    const email = normalizeEmail(body.email), time = now();
    const token = randomBytes(32).toString('hex');
    const code = String(randomInt(0, 1000000)).padStart(6, '0');
    const ref = refFor(email);
    const ipRef = db.doc(`registrationRateLimits/${mac(`ip:${address}`)}`);
    const day = Math.floor(time / 86400000);
    await db.runTransaction(async tx => {
      const [snapshot, ipSnapshot] = await Promise.all([tx.get(ref), tx.get(ipRef)]);
      const old = snapshot.data() || {}, ip = ipSnapshot.data() || {};
      if (old.sentAt && time - old.sentAt < 60000) fail('RATE_LIMITED');
      const emailCount = old.day === day ? old.count : 0;
      const ipCount = ip.day === day ? ip.count : 0;
      if (emailCount >= 5 || ipCount >= 20) fail('RATE_LIMITED');
      tx.set(ipRef, { day, count: ipCount + 1, expiresAt: new Date(time + 86400000) });
      tx.set(ref, { email, tokenHash: mac(`token:${token}`), codeHash: mac(`code:${email}:${token}:${code}`),
        expires: time + TTL, expiresAt: new Date(time + 86400000), sentAt: time, day, count: emailCount + 1,
        attempts: 0, verified: false, delivered: false });
    });
    // Keep the lookup behind the same rate limit as mail requests.
    try {
      const existing = await auth.getUserByEmail(email);
      // An authenticated owner may resume an interrupted, unverified registration.
      if (!user || existing.uid !== user.uid || existing.emailVerified) fail('EMAIL_ALREADY_REGISTERED');
    } catch (error) {
      if (error instanceof EmailVerificationError) throw error;
      if (error.code !== 'auth/user-not-found') fail('SERVICE_UNAVAILABLE');
    }
    // A failed/ambiguous send never yields a usable challenge to the client.
    try { await sendMail(email, code); }
    catch (error) {
      if (error instanceof EmailVerificationError) throw error;
      fail('MAIL_DELIVERY_FAILED');
    }
    await db.runTransaction(async tx => {
      const state = (await tx.get(ref)).data();
      assert(state && same(state.tokenHash, mac(`token:${token}`)));
      tx.update(ref, { delivered: true });
    });
    return { token, expiresIn: TTL / 1000 };
  }
  async function verifyCode(body) {
    const email = normalizeEmail(body.email), tokenHash = capability(body);
    assert(typeof body.code === 'string' && /^\d{6}$/.test(body.code));
    const ref = refFor(email);
    const valid = await db.runTransaction(async tx => {
      const state = (await tx.get(ref)).data();
      assert(state && state.delivered && state.expires > now() && same(state.tokenHash, tokenHash));
      assert(!state.claimedUid && state.attempts < 5);
      if (state.verified) return true; // Idempotent response if the first success was lost.
      const matches = same(state.codeHash, mac(`code:${email}:${body.token}:${body.code}`));
      tx.update(ref, { attempts: state.attempts + 1, verified: matches });
      return matches;
    });
    assert(valid, 'Invalid code');
    return { verified: true };
  }
  async function complete(body, user) {
    const email = normalizeEmail(body.email), tokenHash = capability(body);
    assert(user && normalizeEmail(user.email) === email);
    const ref = refFor(email);
    await db.runTransaction(async tx => {
      const state = (await tx.get(ref)).data();
      assert(state && state.verified && state.expires > now() && same(state.tokenHash, tokenHash));
      assert(!state.claimedUid || state.claimedUid === user.uid);
      tx.update(ref, { claimedUid: user.uid });
    });
    const current = await auth.getUser(user.uid);
    assert(normalizeEmail(current.email) === email);
    // Only the trusted server can persist emailVerified in Firebase Authentication.
    await auth.updateUser(user.uid, { emailVerified: true });
    return { verified: true };
  }
  return { request, verifyCode, complete };
}

export async function sendViaAppsScript(email, code) {
  const endpoint = process.env.MAIL_SCRIPT_URL, key = process.env.MAIL_SCRIPT_SECRET;
  if (!endpoint || !/^https:\/\/script\.google\.com\/macros\/s\/[^/]+\/exec$/.test(endpoint) || !key || key.length < 32) fail('SERVICE_NOT_CONFIGURED');
  const response = await fetch(endpoint, { method: 'POST', signal: AbortSignal.timeout(20000),
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ secret: key, email, code }) });
  assert(response.ok && (await response.json()).ok === true, 'Mail not confirmed');
}
