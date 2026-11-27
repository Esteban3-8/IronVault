package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.json.JSONObject

class VaultCipherTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    @Test fun rfc9106Argon2idVector() {
        val p = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(19)
            .withMemoryAsKB(32).withIterations(3).withParallelism(4)
            .withSalt(ByteArray(16) { 2 }).withSecret(ByteArray(8) { 3 }).withAdditional(ByteArray(12) { 4 }).build()
        val actual = ByteArray(32)
        Argon2BytesGenerator().apply { init(p) }.generateBytes(ByteArray(32) { 1 }, actual)
        assertArrayEquals(hex("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659"), actual)
    }
    @Test fun nistAes256GcmVector() {
        val box = VaultCipher.Box(ByteArray(12), hex("cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919"))
        assertArrayEquals(ByteArray(16), VaultCipher.open(ByteArray(32), box, byteArrayOf()))
    }
    @Test fun tamperingWrongKeyAndContextAreRejected() {
        val key = VaultCipher.randomBytes(32); val aad = "user:vault:record".toByteArray()
        val box = VaultCipher.seal(key, "private credential".toByteArray(), aad)
        assertThrows(Exception::class.java) { VaultCipher.open(VaultCipher.randomBytes(32), box, aad) }
        assertThrows(Exception::class.java) { VaultCipher.open(key, box, "another-user".toByteArray()) }
        box.ciphertext[0] = (box.ciphertext[0].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { VaultCipher.open(key, box, aad) }
    }
    @Test fun profileRejectsMaliciousParametersBeforeAllocation() {
        for (m in listOf(1, 32768, Int.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) {
            VaultCipher.derive("long master password".toCharArray(), ByteArray(16), memory = m)
        }
        assertThrows(IllegalArgumentException::class.java) { VaultCipher.derive(charArrayOf('x'), ByteArray(1)) }
    }
    @Test fun randomizedNoncesAndSalts() {
        val key = VaultCipher.randomBytes(32)
        val nonces = (0 until 1000).map { VaultCipher.seal(key, byteArrayOf(1), byteArrayOf()).nonce.b64() }
        assertEquals(1000, nonces.toSet().size)
        assertFalse(VaultCipher.randomBytes(16).contentEquals(VaultCipher.randomBytes(16)))
    }
    @Test fun masterRewrapDoesNotChangeDataOrDek() {
        val e = VaultEnvelope(JSONObject().put("version", 2).put("id", "b0350d74-ff65-49b3-a02a-ff55cab63218")
            .put("revision", 1).put("keyRevision", 1).put("dataRevision", 1))
        val key = VaultCipher.randomBytes(32)
        val original = "  una frase maestra larga 🔐  ".toCharArray()
        e.wrap("alice", original, key)
        val payload = VaultCipher.seal(key, "secret".toByteArray(), e.aad("alice", "records"))
        assertArrayEquals(key, e.unwrap("alice", original))
        assertThrows(Exception::class.java) { e.unwrap("alice", "una frase maestra larga 🔐".toCharArray()) }
        assertThrows(Exception::class.java) { e.unwrap("bob", original) }
        e.value.put("keyRevision", 2).put("revision", 2)
        val replacement = "una frase nueva diferente".toCharArray()
        e.wrap("alice", replacement, key)
        assertArrayEquals(key, e.unwrap("alice", replacement))
        assertThrows(Exception::class.java) { e.unwrap("alice", original) }
        assertEquals("secret", String(VaultCipher.open(key, payload, e.aad("alice", "records"))))
    }
    @Test fun backupsPreserveUnicodeSpacesAndControlCharacters() {
        val list = listOf(Credential(id="one", serviceName="privado", password="  🔑\n\u0000  ", notes="áé中"))
        val bytes = VaultBackupService.exportToBytes(list, "a separate backup passphrase")
        assertFalse(String(bytes).contains("privado"))
        val envelope = VaultBackupService.validateAndParse(bytes)
        assertEquals(list, VaultBackupService.decrypt(envelope, "a separate backup passphrase"))
        assertThrows(Exception::class.java) { VaultBackupService.decrypt(envelope, "incorrect passphrase") }
        val meta = JSONObject(envelope.getString("metadata")).put("memoryKiB", Int.MAX_VALUE)
        envelope.put("metadata", meta.toString())
        assertThrows(Exception::class.java) { VaultBackupService.decrypt(envelope, "a separate backup passphrase") }
    }
    @Test fun duplicateRecordIdsAreRejected() {
        val credential = Credential(id="duplicate")
        assertThrows(Exception::class.java) { CredentialCodec.decode(CredentialCodec.encode(listOf(credential, credential))) }
    }
}
