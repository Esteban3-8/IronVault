package com.example.myapplication

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Servicio criptográfico exclusivo para los archivos .vault.
 *
 * Algoritmos:
 *  - KDF   : PBKDF2-HMAC-SHA256, 310 000 iteraciones (OWASP 2023).
 *            Argon2id sería preferible pero requeriría una dependencia nativa externa;
 *            PBKDF2 con estas iteraciones es aceptable para un proyecto académico.
 *  - Cifrado: AES-256-GCM (cifrado autenticado).
 *  - Salt  : 16 bytes aleatorios criptográficamente seguros por exportación.
 *  - Nonce : 12 bytes aleatorios criptográficamente seguros por exportación.
 *  - AAD   : metadatos del encabezado (format + version + createdAt) para garantizar
 *            que el encabezado no haya sido manipulado.
 *
 * El authTag de 128 bits lo produce AES-GCM internamente y va concatenado al
 * ciphertext en la salida de doFinal(). Se extrae en el descifrado a partir del
 * tailing 16 bytes para almacenarlo de forma explícita en el JSON del archivo.
 *
 * NUNCA se persiste la contraseña del respaldo ni la clave derivada.
 */
object VaultCryptoService {

    // ── Constantes ────────────────────────────────────────────────
    const val FORMAT       = "ironvault-backup"
    const val VERSION      = 1
    const val ALGORITHM    = "AES-256-GCM"
    const val KDF          = "PBKDF2-HMAC-SHA256"
    const val KDF_ITERS    = 310_000
    const val SALT_BYTES   = 16
    const val NONCE_BYTES  = 12
    const val KEY_BITS     = 256
    const val TAG_BITS     = 128

    private const val GCM_TAG_BYTES = TAG_BITS / 8          // 16
    private const val KDF_SPEC      = "PBKDF2WithHmacSHA256"
    private const val CIPHER_SPEC   = "AES/GCM/NoPadding"

    // ── Límites de seguridad (importación) ────────────────────────
    const val MAX_FILE_BYTES    = 10 * 1024 * 1024          // 10 MB
    const val MAX_CREDENTIALS   = 10_000
    const val MAX_KDF_ITERS     = 1_000_000
    const val MIN_KDF_ITERS     = 100_000

    private val rng = SecureRandom()

    // ─────────────────────────────────────────────────────────────
    //  Resultado del cifrado
    // ─────────────────────────────────────────────────────────────
    data class EncryptResult(
        val salt: String,       // Base64 URL-safe no-wrap
        val nonce: String,
        val ciphertext: String, // cifrado sin el tag
        val authTag: String     // últimos 16 bytes separados explícitamente
    )

    // ─────────────────────────────────────────────────────────────
    //  encrypt()
    //  Cifra plaintext con la contraseña de respaldo.
    //  aad: bytes del encabezado JSON que deben autenticarse.
    // ─────────────────────────────────────────────────────────────
    fun encrypt(plaintext: String, password: String, aad: ByteArray): EncryptResult {
        val salt  = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { rng.nextBytes(it) }
        val key   = deriveKey(password, salt)

        val cipher = Cipher.getInstance(CIPHER_SPEC)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)

        // doFinal devuelve ciphertext || authTag (últimos 16 bytes)
        val full    = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ctBytes = full.copyOfRange(0, full.size - GCM_TAG_BYTES)
        val tagBytes = full.copyOfRange(full.size - GCM_TAG_BYTES, full.size)

        return EncryptResult(
            salt       = b64(salt),
            nonce      = b64(nonce),
            ciphertext = b64(ctBytes),
            authTag    = b64(tagBytes)
        )
    }

    // ─────────────────────────────────────────────────────────────
    //  decrypt()
    //  Descifra y verifica integridad. Lanza VaultCryptoException
    //  si la contraseña es incorrecta o el archivo fue manipulado.
    // ─────────────────────────────────────────────────────────────
    fun decrypt(
        ciphertextB64: String,
        authTagB64: String,
        saltB64: String,
        nonceB64: String,
        password: String,
        aad: ByteArray
    ): String {
        val salt       = db64(saltB64)
        val nonce      = db64(nonceB64)
        val ciphertext = db64(ciphertextB64)
        val authTag    = db64(authTagB64)
        val key        = deriveKey(password, salt)

        // AES-GCM espera ciphertext || authTag en un único buffer
        val full = ciphertext + authTag

        val cipher = Cipher.getInstance(CIPHER_SPEC)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)

        val plainBytes = try {
            cipher.doFinal(full)
        } catch (e: Exception) {
            throw VaultCryptoException("Contraseña incorrecta o archivo manipulado.")
        }

        return String(plainBytes, Charsets.UTF_8)
    }

    // ─────────────────────────────────────────────────────────────
    //  Validación de parámetros del archivo importado
    // ─────────────────────────────────────────────────────────────
    fun validarParametros(enc: JSONObject) {
        val algorithm = enc.optString("algorithm")
        val kdf       = enc.optString("kdf")
        val iters     = enc.optInt("iterations", 0)

        if (algorithm != ALGORITHM)
            throw VaultCryptoException("Algoritmo desconocido: '$algorithm'.")
        if (kdf != KDF)
            throw VaultCryptoException("KDF desconocido: '$kdf'.")
        if (iters < MIN_KDF_ITERS || iters > MAX_KDF_ITERS)
            throw VaultCryptoException("Número de iteraciones KDF fuera de los límites seguros ($iters).")
    }

    // ─────────────────────────────────────────────────────────────
    //  buildAad()
    //  Construye el buffer AAD a partir de los metadatos del encabezado.
    //  Debe producir exactamente los mismos bytes en cifrado y descifrado.
    // ─────────────────────────────────────────────────────────────
    fun buildAad(format: String, version: Int, createdAt: String): ByteArray =
        "$format:$version:$createdAt".toByteArray(Charsets.UTF_8)

    // ─────────────────────────────────────────────────────────────
    //  Derivación de clave
    // ─────────────────────────────────────────────────────────────
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, KDF_ITERS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance(KDF_SPEC)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()        // limpiar la contraseña de memoria
        return SecretKeySpec(keyBytes, "AES")
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers Base64
    // ─────────────────────────────────────────────────────────────
    private fun b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)

    private fun db64(s: String): ByteArray =
        Base64.decode(s, Base64.NO_WRAP or Base64.URL_SAFE)
}

/** Excepción específica para errores criptográficos del sistema de bóveda. */
class VaultCryptoException(message: String) : Exception(message)
