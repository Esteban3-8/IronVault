package com.example.myapplication

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Protocol v2. UTF-8 passwords are used exactly as supplied, without normalization. */
object VaultCipher {
    const val VERSION = 2
    const val MEMORY_KIB = 65536
    const val ITERATIONS = 3
    const val LANES = 1
    private val random = SecureRandom()
    data class Box(val nonce: ByteArray, val ciphertext: ByteArray)
    fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)

    @Synchronized
    fun derive(password: CharArray, salt: ByteArray, memory: Int = MEMORY_KIB,
               iterations: Int = ITERATIONS, lanes: Int = LANES): ByteArray {
        require(salt.size == 16 && memory == MEMORY_KIB && iterations == ITERATIONS && lanes == LANES) {
            "Perfil criptográfico no compatible"
        }
        require(password.size in 1..4096) { "Longitud de contraseña no compatible" }
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13).withSalt(salt)
            .withMemoryAsKB(memory).withIterations(iterations).withParallelism(lanes).build()
        return try {
            ByteArray(32).also { out -> Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password, out) }
        } finally { parameters.clear() }
    }

    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray): Box {
        require(key.size == 32)
        val nonce = randomBytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return Box(nonce, cipher.doFinal(plain))
    }

    fun open(key: ByteArray, box: Box, aad: ByteArray): ByteArray {
        require(key.size == 32 && box.nonce.size == 12 && box.ciphertext.size in 16..(16 * 1024 * 1024))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, box.nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(box.ciphertext)
    }
}
