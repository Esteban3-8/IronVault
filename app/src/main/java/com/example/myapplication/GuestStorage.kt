package com.example.myapplication

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda las credenciales del invitado cifradas en el dispositivo.
 * La llave de cifrado vive en el Android Keystore y no se puede extraer.
 */
object GuestStorage {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "ironvault_guest_vault_key"
    private const val PREFS = "guest_vault"
    private const val KEY_DATA = "data"
    private const val IV_LENGTH = 12

    private fun obtenerClave(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generador.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generador.generateKey()
    }

    fun guardar(context: Context, lista: List<Credential>) {
        try {
            val arreglo = JSONArray()
            for (c in lista) {
                arreglo.put(
                    JSONObject()
                        .put("serviceName", c.serviceName)
                        .put("userOrEmail", c.userOrEmail)
                        .put("password", c.password)
                        .put("strengthStatus", c.strengthStatus)
                        .put("url", c.url)
                        .put("notes", c.notes)
                        .put("category", c.category)
                        .put("updatedAt", c.updatedAt)
                )
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, obtenerClave())
            val cifrado = cipher.doFinal(arreglo.toString().toByteArray(Charsets.UTF_8))
            val combinado = cipher.iv + cifrado

            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DATA, Base64.encodeToString(combinado, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            Log.e("GUEST_STORAGE", "No se pudo guardar: ${e.message}")
        }
    }

    fun cargar(context: Context): List<Credential> {
        val texto = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DATA, null) ?: return emptyList()

        return try {
            val combinado = Base64.decode(texto, Base64.NO_WRAP)
            val iv = combinado.copyOfRange(0, IV_LENGTH)
            val cifrado = combinado.copyOfRange(IV_LENGTH, combinado.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, obtenerClave(), GCMParameterSpec(128, iv))
            val json = String(cipher.doFinal(cifrado), Charsets.UTF_8)

            val arreglo = JSONArray(json)
            val lista = mutableListOf<Credential>()
            for (i in 0 until arreglo.length()) {
                val o = arreglo.getJSONObject(i)
                lista.add(
                    Credential(
                        serviceName = o.optString("serviceName"),
                        userOrEmail = o.optString("userOrEmail"),
                        password = o.optString("password"),
                        strengthStatus = o.optString("strengthStatus", "Segura"),
                        url = o.optString("url"),
                        notes = o.optString("notes"),
                        category = o.optString("category"),
                        updatedAt = o.optLong("updatedAt", 0L)
                    )
                )
            }
            lista
        } catch (e: Exception) {
            Log.e("GUEST_STORAGE", "No se pudo leer: ${e.message}")
            emptyList()
        }
    }
}