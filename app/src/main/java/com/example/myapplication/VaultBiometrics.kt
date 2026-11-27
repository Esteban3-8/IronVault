package com.example.myapplication

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore
import java.security.MessageDigest
import org.json.JSONObject

object VaultBiometrics {
    var prompting = false
        private set
    private fun prefs(c: Context) = c.getSharedPreferences("vault_biometric_v2", Context.MODE_PRIVATE)
    private fun alias(uid: String) = "ironvault.bio." + MessageDigest.getInstance("SHA-256")
        .digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    fun available(c: Context) = BiometricManager.from(c).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    fun enabled(c: Context, uid: String): Boolean {
        if (!prefs(c).contains(uid)) return false
        return try {
            val record = JSONObject(prefs(c).getString(uid, null) ?: return false)
            if (VaultService.unlocked && record.getString("binding") != VaultService.binding) {
                revoke(c, uid); return false
            }
            val key = store().getKey(alias(uid), null) as? SecretKey ?: run { revoke(c, uid); return false }
            Cipher.getInstance("AES/GCM/NoPadding").init(Cipher.DECRYPT_MODE, key,
                GCMParameterSpec(128, record.getString("nonce").unb64()))
            true
        } catch (_: Exception) { revoke(c, uid); false }
    }
    fun revoke(c: Context, uid: String) {
        prefs(c).edit().remove(uid).commit()
        runCatching { store().deleteEntry(alias(uid)) }
    }
    private fun prompt(activity: FragmentActivity, cipher: Cipher, done: (Cipher) -> Unit, error: (String) -> Unit) {
        if (prompting) { error("Ya hay una autenticación biométrica en curso"); return }
        prompting = true
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    try { done(result.cryptoObject?.cipher ?: kotlin.error("Operación no autorizada")) }
                    catch (_: Exception) { error("No se pudo recuperar la clave. Usa tu contraseña maestra.") }
                }
                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    prompting = false
                    error(when (code) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> "Autenticación cancelada. Puedes usar tu contraseña maestra."
                        BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Biometría bloqueada. Usa tu contraseña maestra."
                        else -> "Biometría no disponible. Usa tu contraseña maestra."
                    })
                }
            })
        try {
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("IronVault · Desbloquear bóveda")
                .setSubtitle("Autoriza el acceso seguro a tu bóveda")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Usar contraseña maestra").build(), BiometricPrompt.CryptoObject(cipher))
        } catch (_: Exception) { prompting = false; error("No se pudo iniciar la biometría") }
    }
    fun enroll(activity: FragmentActivity, key: ByteArray, done: () -> Unit, error: (String) -> Unit) {
        val user = VaultService.uid; val binding = VaultService.binding; val epoch = VaultService.epoch()
        val candidate = key.copyOf()
        fun fail(message: String) { candidate.fill(0); revoke(activity, user); error(message) }
        try {
            VaultService.requireUnlocked()
            check(available(activity) == BiometricManager.BIOMETRIC_SUCCESS)
            revoke(activity, user)
            val spec = KeyGenParameterSpec.Builder(alias(user), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= 30) spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            else spec.setUserAuthenticationValidityDurationSeconds(-1)
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(spec.build())
            generator.generateKey()
            val deviceKey = store().getKey(alias(user), null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deviceKey)
            prompt(activity, cipher, { authorized ->
                try {
                    android.util.Log.d("VaultBiometrics", "prompt ok — unlocked=${VaultService.unlocked} sameUser=${VaultService.uid == user} sameEpoch=${VaultService.epoch() == epoch}")
                    check(VaultService.unlocked && VaultService.uid == user && VaultService.epoch() == epoch)
                    val encrypted = authorized.doFinal(candidate)
                    android.util.Log.d("VaultBiometrics", "doFinal ok, encrypted=${encrypted.size} bytes")
                    val nonce = authorized.iv
                    check(prefs(activity).edit().putString(user, JSONObject()
                        .put("binding", binding)
                        .put("nonce", nonce.b64())
                        .put("ciphertext", encrypted.b64())
                        .toString()).commit())
                    android.util.Log.d("VaultBiometrics", "prefs saved ok")
                    candidate.fill(0)
                    done()
                } catch (ex: Exception) { 
                    android.util.Log.e("VaultBiometrics", "enroll failed: ${ex.javaClass.name}: ${ex.message}", ex)
                    fail("La configuración biométrica no se completó. Inténtalo de nuevo.") 
                }
            }, ::fail)
        } catch (ex: Exception) { 
            android.util.Log.e("VaultBiometrics", "enroll outer failed: ${ex.javaClass.name}: ${ex.message}", ex)
            fail("Configura biometría fuerte en los ajustes del dispositivo y vuelve a intentar") 
        }
    }
    internal fun unlock(activity: FragmentActivity, e: VaultEnvelope, done: (ByteArray) -> Unit, error: (String) -> Unit) {
        val user = VaultService.uid
        try {
            check(available(activity) == BiometricManager.BIOMETRIC_SUCCESS)
            val record = JSONObject(prefs(activity).getString(user, null) ?: kotlin.error("Sin configuración"))
            val binding = "$user:${e.id}:${e.keyRevision}"
            if (record.getString("binding") != binding) { revoke(activity, user); kotlin.error("Configuración invalidada") }
            val key = store().getKey(alias(user), null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, record.getString("nonce").unb64()))
            prompt(activity, cipher, { authorized ->
                val dek = authorized.doFinal(record.getString("ciphertext").unb64())
                try { done(dek) } finally { dek.fill(0) }
            }, error)
        } catch (_: android.security.keystore.KeyPermanentlyInvalidatedException) {
            revoke(activity, user); error("La biometría cambió. Usa tu contraseña maestra y configúrala de nuevo.")
        } catch (_: Exception) { error("Biometría no disponible. Usa tu contraseña maestra.") }
    }
}
