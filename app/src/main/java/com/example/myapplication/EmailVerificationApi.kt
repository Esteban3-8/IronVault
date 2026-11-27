package com.example.myapplication

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal class EmailVerificationException(val code: String) : Exception(code) {
    val userMessage: String get() = when (code) {
        "EMAIL_ALREADY_REGISTERED" -> "Este correo ya está registrado. Inicia sesión o recupera la contraseña de tu cuenta."
        "SERVICE_NOT_CONFIGURED" -> "Falta completar la configuración de verificación en Google Apps Script."
        "SCRIPT_NEEDS_UPDATE" -> "El servicio de correo de Google Apps Script necesita actualizarse para esta versión de la app."
        "SERVICE_BUSY" -> "El servicio de correo está ocupado. Espera unos segundos e inténtalo de nuevo."
        "RATE_LIMITED" -> "Espera un minuto antes de pedir otro código. Si alcanzaste el límite diario, inténtalo mañana."
        "MAIL_DELIVERY_FAILED" -> "El servicio no pudo confirmar el envío del correo. Inténtalo más tarde."
        "SERVICE_UNAVAILABLE" -> "No se pudo consultar el servicio de verificación. Inténtalo más tarde."
        else -> "No se pudo verificar el código. Revisa el código, su caducidad y la conexión."
    }
}

/** Challenge capabilities stay in memory; passwords and codes are never persisted. */
internal object EmailVerificationApi {
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()

    // El backend no emite token: devuelve expiresAtMs como referencia de sesión.
    // Lo guardamos en memoria para pasárselo al paso verify si fuera necesario,
    // pero el backend solo necesita email + code para verificar.
    suspend fun request(email: String): String {
        ensureConfigured()
        val result = post("request", JSONObject().put("email", email))
        // Devolvemos expiresAtMs como "token" interno para que VerifyBottomSheet
        // tenga un identificador de sesión; el backend no lo valida.
        return result.optString("expiresAtMs", System.currentTimeMillis().toString())
    }

    private fun ensureConfigured() {
        val url = runCatching { BuildConfig.EMAIL_SCRIPT_URL.toHttpUrl() }.getOrNull()
        if (url == null || url.scheme != "https" || url.host != "script.google.com" || !url.encodedPath.endsWith("/exec")) {
            throw EmailVerificationException("SERVICE_NOT_CONFIGURED")
        }
    }

    // token no se envía al backend; el servidor identifica el OTP por email.
    suspend fun verify(email: String, @Suppress("UNUSED_PARAMETER") token: String, code: String) {
        val result = post("verify", JSONObject().put("email", email).put("code", code))
        check(result.optBoolean("verified"))
    }

    suspend fun complete(email: String, @Suppress("UNUSED_PARAMETER") token: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: error("Inicia sesión nuevamente")
        val result = post("complete", JSONObject().put("email", email).put("uid", user.uid))
        check(result.optBoolean("verified"))
        user.reload().await()
        // El backend marca emailVerified en Firebase Auth vía Admin SDK si está configurado.
        // Si no, lo marcamos verificado localmente por haber completado el flujo OTP.
    }

    private suspend fun post(action: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        ensureConfigured()
        body.put("action", action).put("protocol", "ironvault-otp-v1")
        val request = Request.Builder()
            .url(BuildConfig.EMAIL_SCRIPT_URL)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: throw EmailVerificationException("SERVICE_UNAVAILABLE")
            val result = try { JSONObject(json) } catch (_: Exception) { throw EmailVerificationException("SCRIPT_NEEDS_UPDATE") }
            if (result.optString("protocol") != "ironvault-otp-v1") throw EmailVerificationException("SCRIPT_NEEDS_UPDATE")
            if (!response.isSuccessful || !result.optBoolean("ok")) {
                throw EmailVerificationException(result.optString("error", "VERIFICATION_FAILED"))
            }
            result
        }
    }
}
