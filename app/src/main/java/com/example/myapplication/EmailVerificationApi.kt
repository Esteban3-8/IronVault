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
    suspend fun request(email: String): String {
        ensureConfigured()
        val user = FirebaseAuth.getInstance().currentUser
        val bearer = if (user?.email.equals(email, ignoreCase = true)) user?.getIdToken(true)?.await()?.token else null
        return post("request", JSONObject().put("email", email), bearer).getString("token")
    }
    private fun ensureConfigured() {
        val url = runCatching { BuildConfig.EMAIL_SCRIPT_URL.toHttpUrl() }.getOrNull()
        if (url == null || url.scheme != "https" || url.host != "script.google.com" || !url.encodedPath.endsWith("/exec")) {
            throw EmailVerificationException("SERVICE_NOT_CONFIGURED")
        }
    }
    suspend fun verify(email: String, token: String, code: String) {
        check(post("verify", JSONObject().put("email", email).put("token", token).put("code", code)).getBoolean("verified"))
    }
    suspend fun complete(email: String, token: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: error("Inicia sesión nuevamente")
        val bearer = user.getIdToken(true).await().token ?: error("Sesión no disponible")
        check(post("complete", JSONObject().put("email", email).put("token", token), bearer).getBoolean("verified"))
        user.reload().await()
        user.getIdToken(true).await()
        check(user.isEmailVerified)
    }
    private suspend fun post(action: String, body: JSONObject, bearer: String? = null): JSONObject = withContext(Dispatchers.IO) {
        ensureConfigured()
        // Apps Script cannot read arbitrary incoming Authorization headers. Send the user's
        // short-lived Firebase token in the HTTPS POST body, never in a URL or preference.
        body.put("action", action).put("protocol", 1)
        bearer?.let { body.put("idToken", it) }
        val request = Request.Builder().url(BuildConfig.EMAIL_SCRIPT_URL).post(body.toString().toRequestBody("application/json".toMediaType()))
        client.newCall(request.build()).execute().use { response ->
            val source = response.body?.source() ?: error("Respuesta vacía")
            if (source.request(4097)) throw EmailVerificationException("SCRIPT_NEEDS_UPDATE")
            val result = try { JSONObject(source.readUtf8()) } catch (_: Exception) { throw EmailVerificationException("SCRIPT_NEEDS_UPDATE") }
            if (result.optInt("protocol") != 1) throw EmailVerificationException("SCRIPT_NEEDS_UPDATE")
            if (!response.isSuccessful || !result.optBoolean("ok")) throw EmailVerificationException(result.optString("error", "VERIFICATION_FAILED"))
            result
        }
    }
}
