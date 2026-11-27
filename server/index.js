import { createServer } from 'node:http';
import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import assert from 'node:assert/strict';
import { validate, authorize, digest, publicKey, signedText } from './protocol.js';
import { verify } from 'node:crypto';
import { createEmailVerification, sendViaAppsScript, EmailVerificationError } from './email-verification.js';

// Deploy behind HTTPS with workload identity / ADC. Never package service-account keys in the APK.
initializeApp({ credential: applicationDefault() });
const db = getFirestore();
const emailVerification = createEmailVerification({ db, auth: getAuth(), sendMail: sendViaAppsScript,
  secret: process.env.EMAIL_VERIFICATION_SECRET });
const server = createServer(async (req, res) => {
  res.setHeader('Cache-Control', 'no-store');
  if (req.method === 'POST' && ['/registration/request', '/registration/verify', '/registration/complete'].includes(req.url)) {
    try {
      const chunks = []; let length = 0;
      for await (const chunk of req) { length += chunk.length; assert(length <= 4096); chunks.push(chunk); }
      const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      let result;
      if (req.url === '/registration/request') {
        const token = req.headers.authorization?.match(/^Bearer (\S+)$/)?.[1];
        const user = token ? await getAuth().verifyIdToken(token, true) : null;
        result = await emailVerification.request(body, req.socket.remoteAddress, user);
      }
      else if (req.url === '/registration/verify') result = await emailVerification.verifyCode(body);
      else {
        const token = req.headers.authorization?.match(/^Bearer (\S+)$/)?.[1]; assert(token);
        result = await emailVerification.complete(body, await getAuth().verifyIdToken(token, true));
      }
      res.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify(result));
    } catch (error) {
      const code = error instanceof EmailVerificationError ? error.code : 'VERIFICATION_FAILED';
      const status = code === 'EMAIL_ALREADY_REGISTERED' ? 409 : code === 'RATE_LIMITED' ? 429
        : ['SERVICE_NOT_CONFIGURED', 'SERVICE_UNAVAILABLE', 'MAIL_DELIVERY_FAILED'].includes(code) ? 503 : 400;
      res.writeHead(status, { 'Content-Type': 'application/json' }).end(JSON.stringify({ error: code }));
    }
    return;
  }
  if (req.method !== 'POST' || req.url !== '/vault') { res.writeHead(404).end(); return; }
  try {
    const token = req.headers.authorization?.match(/^Bearer (\S+)$/)?.[1];
    assert(token);
    const user = await getAuth().verifyIdToken(token, true);
    assert(user.uid && !user.uid.includes('\n'));
    const chunks = []; let length = 0;
    for await (const chunk of req) {
      length += chunk.length; assert(length <= 1100000); chunks.push(chunk);
    }
    const request = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    if (request.operation === 'legacy-read') {
      assert(Number.isSafeInteger(request.timestamp) && Math.abs(Date.now() - request.timestamp) < 60000);
      const result = await db.runTransaction(async tx => {
        const active = await tx.get(db.doc(`users/${user.uid}/vault/active`)); assert(!active.exists);
        const grant = await tx.get(db.doc(`users/${user.uid}/migration/approval`));
        assert(grant.get('approved') === true && grant.get('expiresAt').toMillis() > Date.now());
        assert(verify('sha256', Buffer.from(`${user.uid}\nlegacy-read\n${request.timestamp}`), publicKey(grant.get('devicePublic')), Buffer.from(request.signature, 'base64')));
        const records = await tx.get(db.collection(`users/${user.uid}/credentials`).limit(201)); assert(records.size <= 200);
        const snapshot = records.docs.map(d => [d.id,d.updateTime.toMillis()]).sort((a,b)=>a[0].localeCompare(b[0]));
        assert.equal(grant.get('legacyDigest'), digest(JSON.stringify(snapshot)));
        return records.docs.map(d => {
          const c = d.data();
          for (const field of ['serviceName','userOrEmail','password']) assert.equal(typeof c[field], 'string');
          return {id:d.id,serviceName:c.serviceName,userOrEmail:c.userOrEmail,password:c.password,
            strengthStatus:c.strengthStatus || 'Segura',url:c.url || '',notes:c.notes || '',category:c.category || '',updatedAt:c.updatedAt || 0};
        });
      });
      const encoded = JSON.stringify(result); assert(Buffer.byteLength(encoded) <= 900000);
      res.writeHead(200, {'Content-Type':'application/json'}).end(encoded); return;
    }
    const next = validate(request.envelope);
    const ref = db.doc(`users/${user.uid}/vault/active`);
    await db.runTransaction(async tx => {
      const current = await tx.get(ref);
      if (!current.exists) {
        assert(['create','migrate'].includes(request.operation));
        assert.equal(request.previous, 0);
        assert.equal(next.revision, 1); assert.equal(next.keyRevision, 1); assert.equal(next.dataRevision, 1);
        const profile = await tx.get(db.doc(`users/${user.uid}`));
        const legacy = await tx.get(db.collection(`users/${user.uid}/credentials`).limit(201));
        if (request.operation === 'create') {
          assert(user.email_verified === true);
          assert(!profile.get('hasMasterPassword') && legacy.empty);
        } else {
          // Grant can only be provisioned out of band after independent identity review.
          const grantRef = db.doc(`users/${user.uid}/migration/approval`);
          const grant = await tx.get(grantRef);
          assert(grant.exists && grant.get('approved') === true && grant.get('expiresAt').toMillis() > Date.now());
          assert(verify('sha256', Buffer.from(signedText(user.uid, 0, 'migrate', request.envelope)),
            publicKey(grant.get('devicePublic')), Buffer.from(request.signature, 'base64')));
          assert(legacy.size <= 200);
          const snapshot = legacy.docs.map(d => [d.id, d.updateTime.toMillis()]).sort((a,b) => a[0].localeCompare(b[0]));
          assert.equal(grant.get('legacyDigest'), digest(JSON.stringify(snapshot)));
          tx.delete(grantRef);
          // Legacy originals remain inaccessible to clients; removal needs a separate approved cleanup.
        }
      } else {
        authorize(user.uid, request, current.get('envelope'));
      }
      tx.set(ref, { version: 2, revision: next.revision, envelope: request.envelope });
    });
    res.writeHead(204).end();
  } catch (_) {
    // Deliberately omit request bodies, tokens, ciphertext and internal diagnostics from logs/responses.
    res.writeHead(409, { 'Content-Type': 'application/json' }).end('{"error":"Operation rejected. Refresh the vault or sign in again."}');
  }
});
server.requestTimeout = 30000;
server.headersTimeout = 10000;
server.listen(Number(process.env.PORT || 8080), '127.0.0.1');
