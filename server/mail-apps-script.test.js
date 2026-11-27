import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';

function fixture() {
  const sent = [], secret = 'test-only-script-secret-not-for-deployment';
  const context = {
    PropertiesService: { getScriptProperties: () => ({ getProperty: () => secret }) },
    MailApp: { sendEmail: value => sent.push(value) },
    ContentService: { MimeType: { JSON: 'application/json' },
      createTextOutput: text => ({ text, setMimeType() { return this; } }) }
  };
  runInNewContext(readFileSync(new URL('./mail-apps-script.gs', import.meta.url), 'utf8'), context);
  const post = body => JSON.parse(context.doPost({ postData: { contents: JSON.stringify(body) } }).text);
  return { context, sent, secret, post };
}
test('mail adapter preserves branded HTML and matching ten-minute expiry without real delivery', () => {
  const f = fixture();
  assert.equal(f.post({ secret: f.secret, email: 'test@example.test', code: '123456' }).ok, true);
  assert.equal(f.sent.length, 1);
  assert.match(f.sent[0].htmlBody, /VERIFICACIÓN DE CORREO/);
  assert.match(f.sent[0].htmlBody, /#C97D3E/);
  assert.match(f.sent[0].htmlBody, /123456/);
  assert.match(f.sent[0].htmlBody, /10 minutos/);
  assert.match(f.sent[0].body, /10 minutos/);
  assert.equal(f.sent[0].htmlBody.includes(f.secret), false);
});
test('mail adapter rejects old GET, missing secret, recipient and HTML injection', () => {
  const f = fixture();
  assert.equal(JSON.parse(f.context.doGet().text).ok, false);
  for (const body of [
    { email: 'test@example.test', code: '123456' },
    { secret: f.secret, email: 'test@example.test', code: '<script>' },
    { secret: f.secret, email: 'a@example.test,b@example.test', code: '123456' }
  ]) assert.equal(f.post(body).ok, false);
  assert.equal(f.sent.length, 0);
});
