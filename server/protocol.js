import { createHash, createPublicKey, verify } from 'node:crypto';
import assert from 'node:assert/strict';

export const digest = value => createHash('sha256').update(value).digest('hex');
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const b64 = (value, size) => {
  assert.equal(typeof value, 'string');
  assert.match(value, /^[A-Za-z0-9+/]*={0,2}$/);
  const bytes = Buffer.from(value, 'base64');
  assert.equal(bytes.toString('base64'), value);
  if (size !== undefined) assert.equal(bytes.length, size);
  return bytes;
};
function box(value, max) {
  assert.deepEqual(Object.keys(value).sort(), ['ciphertext', 'nonce']);
  b64(value.nonce, 12);
  const bytes = b64(value.ciphertext);
  assert(bytes.length >= 16 && bytes.length <= max);
}
export function publicKey(encoded) {
  const key = createPublicKey({ key: b64(encoded), format: 'der', type: 'spki' });
  assert.equal(key.asymmetricKeyType, 'ec');
  assert.equal(key.asymmetricKeyDetails.namedCurve, 'prime256v1');
  return key;
}
export function validate(encoded) {
  assert.equal(typeof encoded, 'string');
  assert(Buffer.byteLength(encoded) <= 900000);
  const e = JSON.parse(encoded);
  assert.deepEqual(Object.keys(e).sort(), ['version','id','revision','dataRevision','keyRevision','kdf','wrappedDek','payload','writerPublic','ownerPublic','writerPrivate','ownerPrivate'].sort());
  assert.equal(e.version, 2);
  assert.match(e.id, /^[a-f0-9-]{36}$/);
  for (const field of ['revision','dataRevision','keyRevision']) assert(Number.isSafeInteger(e[field]) && e[field] > 0);
  assert(e.dataRevision <= 1000000, 'Rotate the data key before exceeding the per-key write budget');
  assert.deepEqual(Object.keys(e.kdf).sort(), ['name','version','memoryKiB','iterations','lanes','salt'].sort());
  assert.equal(e.kdf.name, 'Argon2id'); assert.equal(e.kdf.version, 19);
  assert.equal(e.kdf.memoryKiB, 65536); assert.equal(e.kdf.iterations, 3); assert.equal(e.kdf.lanes, 1);
  b64(e.kdf.salt, 16); box(e.wrappedDek, 48); assert.equal(b64(e.wrappedDek.ciphertext).length, 48);
  box(e.payload, 670000); box(e.writerPrivate, 1024); box(e.ownerPrivate, 1024);
  publicKey(e.writerPublic); publicKey(e.ownerPublic);
  return e;
}
export function signedText(uid, previous, operation, encoded) {
  return `${uid}\n${JSON.parse(encoded).id}\n${previous}\n${operation}\n${digest(encoded)}`;
}
export function authorize(uid, request, previousEncoded) {
  const e = validate(request.envelope);
  const old = validate(previousEncoded);
  assert.equal(request.previous, old.revision);
  assert.equal(e.revision, old.revision + 1);
  for (const field of ['id','writerPublic','ownerPublic','writerPrivate']) assert(same(e[field], old[field]));
  assert(['write','import','delete','rekey'].includes(request.operation));
  if (request.operation === 'rekey') {
    assert.equal(e.keyRevision, old.keyRevision + 1);
    assert.equal(e.dataRevision, old.dataRevision);
    assert(same(e.payload, old.payload));
    assert.notEqual(e.kdf.salt, old.kdf.salt);
  } else {
    assert.equal(e.keyRevision, old.keyRevision);
    assert.equal(e.dataRevision, old.dataRevision + 1);
    assert.notEqual(e.payload.nonce, old.payload.nonce);
    for (const field of ['kdf','wrappedDek','ownerPrivate']) assert(same(e[field], old[field]));
  }
  const key = publicKey(request.operation === 'write' ? old.writerPublic : old.ownerPublic);
  assert(verify('sha256', Buffer.from(signedText(uid, request.previous, request.operation, request.envelope)), key, b64(request.signature)));
  return e;
}
