import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign, randomBytes } from 'node:crypto';
import { authorize, validate, signedText } from './protocol.js';
const pair = () => generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const writer = pair(), owner = pair(), attacker = pair();
const pub = p => p.publicKey.export({format:'der',type:'spki'}).toString('base64');
const box = n => ({nonce: randomBytes(12).toString('base64'),ciphertext: randomBytes(n).toString('base64')});
function base() {
  return {version:2,id:'b0350d74-ff65-49b3-a02a-ff55cab63218',revision:1,dataRevision:1,keyRevision:1,
    kdf:{name:'Argon2id',version:19,memoryKiB:65536,iterations:3,lanes:1,salt:randomBytes(16).toString('base64')},
    wrappedDek:box(48),payload:box(64),writerPrivate:box(160),ownerPrivate:box(160),writerPublic:pub(writer),ownerPublic:pub(owner)};
}
function request(next, op='write', key=writer, uid='alice', previous=1) {
  const envelope=JSON.stringify(next);
  return {previous,operation:op,envelope,signature:sign('sha256',Buffer.from(signedText(uid,previous,op,envelope)),key.privateKey).toString('base64')};
}
test('a Firebase identity without a vault signing key cannot write', () => {
  const old=base(), next={...old,revision:2,dataRevision:2,payload:box(64)};
  assert.throws(()=>authorize('alice',request(next,'write',attacker),JSON.stringify(old)));
  authorize('alice',request(next),JSON.stringify(old));
});
test('signatures bind account, payload, operation and expected revision', () => {
  const old=base(), next={...old,revision:2,dataRevision:2,payload:box(64)}, r=request(next);
  assert.throws(()=>authorize('bob',r,JSON.stringify(old)));
  assert.throws(()=>authorize('alice',{...r,operation:'delete'},JSON.stringify(old)));
  assert.throws(()=>authorize('alice',{...r,previous:2},JSON.stringify(old)));
  assert.throws(()=>authorize('alice',{...r,envelope:JSON.stringify({...next,payload:box(64)})},JSON.stringify(old)));
});
test('biometric-accessible writer cannot authorize privileged operations', () => {
  for (const op of ['import','delete']) {
    const old=base(), next={...old,revision:2,dataRevision:2,payload:box(64)};
    assert.throws(()=>authorize('alice',request(next,op,writer),JSON.stringify(old)));
    authorize('alice',request(next,op,owner),JSON.stringify(old));
  }
});
test('rekey needs owner, increments key revision, and preserves payload', () => {
  const old=base(), next={...old,revision:2,keyRevision:2,kdf:{...old.kdf,salt:randomBytes(16).toString('base64')},wrappedDek:box(48),ownerPrivate:box(160)};
  authorize('alice',request(next,'rekey',owner),JSON.stringify(old));
  assert.throws(()=>authorize('alice',request(next,'rekey',writer),JSON.stringify(old)));
  assert.throws(()=>authorize('alice',request({...next,payload:box(64)},'rekey',owner),JSON.stringify(old)));
});
test('replay and rollback are rejected', () => {
  const old=base(), next={...old,revision:2,dataRevision:2,payload:box(64)};
  assert.throws(()=>authorize('alice',request(next),JSON.stringify(next)));
  assert.throws(()=>authorize('alice',request(old,'write',writer,'alice',2),JSON.stringify(next)));
});
test('malicious profiles, plaintext fields and replacement public keys are rejected', () => {
  const old=base();
  assert.throws(()=>validate(JSON.stringify({...old,kdf:{...old.kdf,memoryKiB:2147483647}})));
  assert.throws(()=>validate(JSON.stringify({...old,password:'plaintext'})));
  const next={...old,revision:2,dataRevision:2,ownerPublic:pub(attacker)};
  assert.throws(()=>authorize('alice',request(next),JSON.stringify(old)));
});
