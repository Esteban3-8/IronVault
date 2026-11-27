package com.example.myapplication

import org.bouncycastle.util.encoders.Base64
import org.json.JSONArray
import org.json.JSONObject

internal fun ByteArray.b64(): String = Base64.toBase64String(this)
internal fun String.unb64(): ByteArray {
    require(length <= 14000000)
    require(matches(Regex("[A-Za-z0-9+/]*={0,2}")))
    return Base64.decode(this).also { require(it.b64() == this) }
}
internal fun VaultCipher.Box.json() = JSONObject().put("nonce", nonce.b64()).put("ciphertext", ciphertext.b64())
internal fun JSONObject.box() = VaultCipher.Box(getString("nonce").unb64(), getString("ciphertext").unb64())

internal data class VaultEnvelope(val value: JSONObject) {
    val id get() = value.getString("id")
    val revision get() = value.getLong("revision")
    val keyRevision get() = value.getLong("keyRevision")
    fun validate() {
        require(value.getInt("version") == 2 && id.matches(Regex("[a-f0-9-]{36}")))
        require(revision > 0 && keyRevision > 0 && value.getLong("dataRevision") > 0)
        val k = value.getJSONObject("kdf")
        require(k.getString("name") == "Argon2id" && k.getInt("version") == 19)
        require(k.getInt("memoryKiB") == 65536 && k.getInt("iterations") == 3 && k.getInt("lanes") == 1)
        require(k.getString("salt").unb64().size == 16)
    }
    fun aad(uid: String, purpose: String): ByteArray = JSONArray()
        .put("ironvault").put(2).put(uid).put(id).put(purpose)
        .put(if (purpose == "dek") keyRevision else value.getLong("dataRevision")).toString().toByteArray(Charsets.UTF_8)
    fun unwrap(uid: String, password: CharArray): ByteArray {
        validate()
        val kek = VaultCipher.derive(password, value.getJSONObject("kdf").getString("salt").unb64())
        return try { VaultCipher.open(kek, value.getJSONObject("wrappedDek").box(), aad(uid, "dek"))
            .also { require(it.size == 32) } } finally { kek.fill(0) }
    }
    fun wrap(uid: String, password: CharArray, dek: ByteArray) {
        val salt = VaultCipher.randomBytes(16)
        val kek = VaultCipher.derive(password, salt)
        try {
            value.put("kdf", JSONObject().put("name", "Argon2id").put("version", 19)
                .put("memoryKiB", 65536).put("iterations", 3).put("lanes", 1).put("salt", salt.b64()))
            value.put("wrappedDek", VaultCipher.seal(kek, dek, aad(uid, "dek")).json())
        } finally { kek.fill(0) }
    }
}

internal object CredentialCodec {
    fun encode(list: List<Credential>): ByteArray = JSONArray().apply {
        require(list.size <= 10000)
        list.forEach { c -> put(JSONObject().put("id", c.id).put("serviceName", c.serviceName)
            .put("userOrEmail", c.userOrEmail).put("password", c.password).put("url", c.url)
            .put("notes", c.notes).put("category", c.category).put("updatedAt", c.updatedAt)
            .put("strengthStatus", c.strengthStatus)) }
    }.toString().toByteArray(Charsets.UTF_8)
    fun decode(bytes: ByteArray): List<Credential> {
        val a = JSONArray(String(bytes, Charsets.UTF_8))
        require(a.length() <= 10000)
        val result = (0 until a.length()).map { i -> val o = a.getJSONObject(i)
            Credential(o.getString("id"), o.getString("serviceName"), o.getString("userOrEmail"),
                o.getString("password"), o.optString("strengthStatus", "Segura"), o.getString("url"),
                o.getString("notes"), o.getString("category"), o.getLong("updatedAt")) }
        require(result.map { it.id }.distinct().size == result.size)
        return result
    }
}
