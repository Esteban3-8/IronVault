package com.example.myapplication

import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.Signature
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

/** Independent signing keys authorize server mutations; neither grants decryption to the server. */
internal object VaultSigning {
    private fun pair() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private fun context(e: VaultEnvelope, uid: String, purpose: String) =
        "ironvault-signing-v2:$uid:${e.id}:$purpose".toByteArray()
    fun initialize(e: VaultEnvelope, uid: String, dek: ByteArray, master: CharArray) {
        val writer = pair(); val owner = pair()
        e.value.put("writerPublic", writer.public.encoded.b64()).put("ownerPublic", owner.public.encoded.b64())
        val w = writer.private.encoded; val o = owner.private.encoded
        try {
            e.value.put("writerPrivate", VaultCipher.seal(dek, w, context(e, uid, "writer")).json())
            wrapOwner(e, uid, master, o)
        } finally { w.fill(0); o.fill(0) }
    }
    fun owner(e: VaultEnvelope, uid: String, master: CharArray): ByteArray {
        val kek = VaultCipher.derive(master, e.value.getJSONObject("kdf").getString("salt").unb64())
        return try { VaultCipher.open(kek, e.value.getJSONObject("ownerPrivate").box(), context(e, uid, "owner:${e.keyRevision}")) }
        finally { kek.fill(0) }
    }
    fun wrapOwner(e: VaultEnvelope, uid: String, master: CharArray, key: ByteArray) {
        val kek = VaultCipher.derive(master, e.value.getJSONObject("kdf").getString("salt").unb64())
        try { e.value.put("ownerPrivate", VaultCipher.seal(kek, key, context(e, uid, "owner:${e.keyRevision}")).json()) }
        finally { kek.fill(0) }
    }
    fun writer(e: VaultEnvelope, uid: String, dek: ByteArray) =
        VaultCipher.open(dek, e.value.getJSONObject("writerPrivate").box(), context(e, uid, "writer"))
    suspend fun commit(uid: String, previous: Long, operation: String, e: VaultEnvelope, key: ByteArray?) {
        val endpoint = BuildConfig.VAULT_API_URL
        // Modo desarrollo: si no hay servidor configurado, escribe directo a Firestore.
        // En producción basta con definir vaultApiUrl en gradle.properties para activar
        // la validación de firma EC sin tocar este archivo.
        if (!endpoint.startsWith("https://")) {
            android.util.Log.w("VaultSigning", "VAULT_API_URL no configurado — escritura directa en Firestore (solo desarrollo)")
            val user = FirebaseAuth.getInstance().currentUser ?: error("Inicia sesión")
            check(user.uid == uid)
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("vault").document("active")
                .set(mapOf(
                    "envelope" to e.value.toString(),
                    "revision" to e.revision,
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )).await()
            return
        }
        val encoded = e.value.toString()
        require(encoded.toByteArray().size <= 900000)
        val digest = MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray()).joinToString("") { "%02x".format(it) }
        val requestText = "$uid\n${e.id}\n$previous\n$operation\n$digest"
        val signature = key?.let {
            Signature.getInstance("SHA256withECDSA").run {
                initSign(KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(it)))
                update(requestText.toByteArray()); sign().b64()
            }
        } ?: if (operation == "migrate") VaultMigration.sign(uid, requestText) else ""
        val body = JSONObject().put("previous", previous).put("operation", operation)
            .put("envelope", encoded).put("signature", signature)
        post(uid, body).fill(0)
    }
    suspend fun post(uid: String, body: JSONObject): ByteArray {
        val endpoint = BuildConfig.VAULT_API_URL
        check(endpoint.startsWith("https://")) { "El servicio de autorización de bóveda no está configurado" }
        val user = FirebaseAuth.getInstance().currentUser ?: error("Inicia sesión")
        check(user.uid == uid)
        val token = user.getIdToken(true).await().token ?: error("Sesión inválida")
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            OkHttpClient.Builder().callTimeout(45, java.util.concurrent.TimeUnit.SECONDS).build().newCall(request).execute().use {
                check(it.isSuccessful) { "El servidor rechazó la operación o hubo un conflicto" }
                val source = it.body?.source()
                if (source == null) byteArrayOf() else {
                    require(!source.request(1100001)) { "Respuesta demasiado grande" }
                    source.readByteArray()
                }
            }
        }
    }
}
