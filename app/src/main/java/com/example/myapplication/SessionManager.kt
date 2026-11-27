package com.example.myapplication

import android.content.Context

object SessionManager {
    private fun attemptPrefs(context: Context): android.content.SharedPreferences {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: error("Inicia sesión nuevamente")
        val name = java.security.MessageDigest.getInstance("SHA-256").digest(uid.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val scoped = context.getSharedPreferences("vault_attempts_$name", Context.MODE_PRIVATE)
        val legacy = context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
        if (legacy.contains(KEY_FAILED_ATTEMPTS) || legacy.contains(KEY_LOCK_UNTIL)) {
            if (scoped.edit().putInt(KEY_FAILED_ATTEMPTS, legacy.getInt(KEY_FAILED_ATTEMPTS, 0))
                    .putLong(KEY_LOCK_UNTIL, legacy.getLong(KEY_LOCK_UNTIL, 0)).commit()) {
                legacy.edit().remove(KEY_FAILED_ATTEMPTS).remove(KEY_LOCK_UNTIL).commit()
            }
        }
        return scoped
    }
    fun requireAccessAllowed(context: Context) {
        check(tiempoBloqueoRestanteMs(context) == 0L) { "Espera antes de volver a intentar" }
    }
    private fun boot(context: Context) = android.provider.Settings.Global.getInt(
        context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
    private const val PREF_SECURITY           = "security_prefs"
    private const val KEY_LAST_BACKGROUND_TIME = "last_background_time"
    private const val KEY_FAILED_ATTEMPTS      = "failed_attempts"
    private const val KEY_LOCK_UNTIL           = "lock_until"

    // ─────────────────────────────────────────────────────────────
    // Bloqueo por inactividad (funcionalidad existente)
    // ─────────────────────────────────────────────────────────────

    /** Marca que el usuario ya entró a la bóveda en esta sesión. */
    fun marcarBovedaDesbloqueada(context: Context) {
        context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
            .edit().remove("vault_unlocked").apply()
    }

    /** El estado depende de la clave en memoria, nunca de una preferencia persistida. */
    fun bovedaEstaDesbloqueada(context: Context): Boolean {
        return VaultService.unlocked
    }

    fun registrarSalida(context: Context) {
        val prefs = context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_BACKGROUND_TIME, System.currentTimeMillis()).apply()
    }

    fun haExpiradoInactividad(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
        val lastTime = prefs.getLong(KEY_LAST_BACKGROUND_TIME, 0L)
        if (lastTime == 0L) return false

        val autoLockSetting = prefs.getString("auto_lock", "5 minutos") ?: "5 minutos"
        val timeoutLimitMs = convertirTextoAMilisegundos(autoLockSetting)
        if (timeoutLimitMs == -1L) return false

        return System.currentTimeMillis() - lastTime > timeoutLimitMs
    }

    fun limpiarSesion(context: Context) {
        val prefs = context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_LAST_BACKGROUND_TIME)
            .remove("vault_unlocked")
            .apply()
    }

    // ─────────────────────────────────────────────────────────────
    // Bloqueo progresivo por intentos fallidos
    // ─────────────────────────────────────────────────────────────

    /**
     * Registra un intento fallido y, si se alcanza el límite configurado,
     * calcula y guarda el tiempo de bloqueo progresivo.
     *
     * @return el tiempo de bloqueo en ms que debe esperar el usuario,
     *         o 0L si aún no hay bloqueo.
     */
    fun registrarIntentoFallido(context: Context): Long {
        val prefs = attemptPrefs(context)
        val limiteTexto = context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
            .getString("attempts", "3 Intentos") ?: "3 Intentos"

        // Si el usuario desactivó el límite de intentos, no hay bloqueo
        val limite = parsearLimiteIntentos(limiteTexto)
        if (limite == -1) return 0L

        val fallidos = (prefs.getInt(KEY_FAILED_ATTEMPTS, 0).coerceAtLeast(0).toLong() + 1)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, fallidos).apply()

        // ¿Se llegó exactamente a un múltiplo del límite?
        if (fallidos % limite == 0) {
            // Duplica por cada ciclo: base * 2^(ciclo-1), tope 1 hora
            val bloqueoMs = AttemptLockPolicy.duration(limite, fallidos)
            val hasta = System.currentTimeMillis() + bloqueoMs
            prefs.edit().putLong(KEY_LOCK_UNTIL, hasta)
                .putLong("elapsed_until", android.os.SystemClock.elapsedRealtime() + bloqueoMs)
                .putInt("boot", boot(context)).putLong("duration", bloqueoMs).apply()
            return bloqueoMs
        }

        return 0L
    }

    /**
     * Milisegundos que faltan para que termine el bloqueo activo.
     * Devuelve 0 si no hay bloqueo o ya expiró.
     */
    fun tiempoBloqueoRestanteMs(context: Context): Long {
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) return 0L
        val prefs = attemptPrefs(context)
        val hasta = prefs.getLong(KEY_LOCK_UNTIL, 0L)
        val sameBoot = boot(context).let { it >= 0 && it == prefs.getInt("boot", -1) }
        return AttemptLockPolicy.remaining(
            if (sameBoot) prefs.getLong("elapsed_until", 0) else hasta,
            if (sameBoot) android.os.SystemClock.elapsedRealtime() else System.currentTimeMillis(),
            prefs.getLong("duration", 3600000L))
    }

    /** Resetea contador e instancia de bloqueo (tras desbloqueo exitoso). */
    fun resetearIntentos(context: Context) {
        val prefs = attemptPrefs(context)
        prefs.edit()
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCK_UNTIL)
            .remove("elapsed_until").remove("boot").remove("duration")
            .apply()
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers privados
    // ─────────────────────────────────────────────────────────────

    private fun convertirTextoAMilisegundos(texto: String): Long {
        return VaultTimeoutPolicy.delay(texto)
    }

    /** Devuelve el número de intentos permitidos, o -1 si es "Nunca". */
    private fun parsearLimiteIntentos(texto: String): Int {
        return when (texto) {
            "3 Intentos" -> 3
            "5 Intentos" -> 5
            "7 Intentos" -> 7
            "Nunca"      -> -1
            else         -> 3
        }
    }

}
