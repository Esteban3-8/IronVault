// Run only with: firebase emulators:exec --only firestore --project demo-ironvault-history "node --test tests/access-history-rules.test.mjs"
import test from 'node:test';
import assert from 'node:assert/strict';

const host = process.env.FIRESTORE_EMULATOR_HOST;
if (!host || !/^(localhost|127\.0\.0\.1):\d+$/.test(host)) throw new Error('A local Firestore emulator is required.');
const project = 'demo-ironvault-history';
const root = `projects/${project}/databases/(default)/documents`;
const base = `http://${host}/v1/${root}`;
const authTime = Math.floor(Date.now() / 1000);
const device = 'a'.repeat(64);
const alice = `history-alice-${Date.now()}`;
const bob = `history-bob-${Date.now()}`;
const id = `${device}_${authTime}`;
const path = `users/${alice}/accessEvents/${id}`;

function token(uid) {
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'none', typ: 'JWT' })}.${encode({
    iss: `https://securetoken.google.com/${project}`, aud: project, sub: uid, user_id: uid,
    iat: authTime, exp: authTime + 3600, auth_time: authTime,
    firebase: { identities: {}, sign_in_provider: 'password' }
  })}.`;
}
async function request(suffix, uid, body, method = body ? 'POST' : 'GET') {
  return fetch(`${base}${suffix}`, {
    method, headers: { 'Content-Type': 'application/json', ...(uid ? { Authorization: `Bearer ${token(uid)}` } : {}) },
    body: body ? JSON.stringify(body) : undefined
  });
}
function fields() {
  return {
    version: { integerValue: '1' }, deviceId: { stringValue: device },
    deviceName: { stringValue: 'Test Android' }, androidVersion: { stringValue: '15' },
    authTime: { integerValue: String(authTime) }
  };
}
function create(target = path, values = fields()) {
  return { writes: [{ update: { name: `${root}/${target}`, fields: values },
    updateTransforms: [{ fieldPath: 'recordedAt', setToServerValue: 'REQUEST_TIME' }],
    currentDocument: { exists: false } }] };
}
async function expectStatus(response, expected) {
  const body = await response.text();
  assert.equal(response.status, expected, body);
}
test('access history rules isolate accounts and preserve immutable records', async t => {
  await t.test('owner creates with the authenticated time and server receipt time', async () => {
    await expectStatus(await request(':commit', alice, create()), 200);
  });
  await t.test('owner reads own event', async () => expectStatus(await request(`/${path}`, alice), 200));
  await t.test('other account cannot read event', async () => expectStatus(await request(`/${path}`, bob), 403));
  await t.test('anonymous cannot read event', async () => expectStatus(await request(`/${path}`, null), 403));
  await t.test('other account cannot create for owner', async () => {
    await expectStatus(await request(':commit', bob, create(`users/${alice}/accessEvents/other`)), 403);
  });
  await t.test('authentication time cannot be forged', async () => {
    const values = fields(); values.authTime = { integerValue: String(authTime - 100) };
    await expectStatus(await request(':commit', alice, create(`users/${alice}/accessEvents/${device}_${authTime - 100}`, values)), 403);
  });
  await t.test('oversized model and extra sensitive fields are rejected', async () => {
    const alternate = 'b'.repeat(64);
    const values = fields(); values.deviceId = { stringValue: alternate };
    values.deviceName = { stringValue: 'x'.repeat(81) };
    const target = `users/${alice}/accessEvents/${alternate}_${authTime}`;
    await expectStatus(await request(':commit', alice, create(target, values)), 403);
    values.deviceName = { stringValue: 'Test' }; values.email = { stringValue: 'synthetic@example.test' };
    await expectStatus(await request(':commit', alice, create(target, values)), 403);
  });
  await t.test('receipt time must come from server', async () => {
    const alternate = 'c'.repeat(64);
    const values = fields(); values.deviceId = { stringValue: alternate };
    values.recordedAt = { timestampValue: '2020-01-01T00:00:00Z' };
    const payload = create(`users/${alice}/accessEvents/${alternate}_${authTime}`, values);
    delete payload.writes[0].updateTransforms;
    await expectStatus(await request(':commit', alice, payload), 403);
  });
  await t.test('owner cannot rewrite event', async () => {
    const payload = create(); delete payload.writes[0].currentDocument;
    await expectStatus(await request(':commit', alice, payload), 403);
  });
  await t.test('owner cannot delete event', async () => {
    await expectStatus(await request(':commit', alice, { writes: [{ delete: `${root}/${path}` }] }), 403);
  });
  await t.test('bounded owner query works; unbounded and cross-account queries fail', async () => {
    const query = { structuredQuery: { from: [{ collectionId: 'accessEvents' }],
      orderBy: [{ field: { fieldPath: 'authTime' }, direction: 'DESCENDING' }], limit: 30 } };
    await expectStatus(await request(`/users/${alice}:runQuery`, alice, query), 200);
    await expectStatus(await request(`/users/${alice}:runQuery`, bob, query), 403);
    delete query.structuredQuery.limit;
    await expectStatus(await request(`/users/${alice}:runQuery`, alice, query), 403);
  });
  await t.test('existing vault write restrictions remain enforced', async () => {
    await expectStatus(await request(':commit', alice, { writes: [{ update: {
      name: `${root}/users/${alice}/vault/active`, fields: { envelope: { stringValue: 'synthetic' } }
    } }] }), 403);
  });
});
