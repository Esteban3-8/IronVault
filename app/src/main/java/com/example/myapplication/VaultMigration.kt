package com.example.myapplication

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.security.PrivateKey
import org.json.JSONObject

/** Legacy enrollment is disabled unless a separately reviewed server grant exists. */
internal object VaultMigration {
    private fun alias(user: String) = "ironvault.migration." + MessageDigest.getInstance("SHA-256")
        .digest(user.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun store(user: String): KeyStore {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias(user))) {
            KeyPairGenerator.getInstance("EC", "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias(user), KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256).build())
            }.generateKeyPair()
        }
        return store
    }
    fun publicKey(user: String): String = store(user).getCertificate(alias(user)).publicKey.encoded.b64()
    fun sign(user: String, text: String): String = Signature.getInstance("SHA256withECDSA").run {
        initSign(store(user).getKey(alias(user), null) as PrivateKey); update(text.toByteArray()); sign().b64()
    }
    suspend fun approved(user: String): Boolean {
        val grant = FirebaseFirestore.getInstance().document("users/$user/migration/approval").get(Source.SERVER).await()
        return grant.getBoolean("approved") == true && grant.getString("devicePublic") == publicKey(user) &&
            (grant.getTimestamp("expiresAt")?.toDate()?.time ?: 0) > System.currentTimeMillis()
    }
    suspend fun read(user: String): List<Credential> {
        check(approved(user)) { "La migración no está autorizada" }
        val timestamp = System.currentTimeMillis()
        val body = JSONObject().put("operation", "legacy-read").put("timestamp", timestamp)
            .put("signature", sign(user, "$user\nlegacy-read\n$timestamp"))
        val bytes = VaultSigning.post(user, body)
        return try { CredentialCodec.decode(bytes).also { require(it.size <= 200) } } finally { bytes.fill(0) }
    }
}
