package com.example.myapplication

import android.content.Context

object SessionManager {
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
            .edit().putBoolean("vault_unlocked", true).apply()
    }

    /** True si el usuario llegó al Home al menos una vez en esta sesión. */
    fun bovedaEstaDesbloqueada(context: Context): Boolean {
        return context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
            .getBoolean("vault_unlocked", false)
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
        val prefs = context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
        val limiteTexto = prefs.getString("attempts", "3 Intentos") ?: "3 Intentos"

        // Si el usuario desactivó el límite de intentos, no hay bloqueo
        val limite = parsearLimiteIntentos(limiteTexto)
        if (limite == -1) return 0L

        val fallidos = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, fallidos).apply()

        // ¿Se llegó exactamente a un múltiplo del límite?
        if (fallidos % limite == 0) {
            val ciclo = fallidos / limite          // 1, 2, 3 …
            val baseMs = tiempoBaseMs(limite)
            // Duplica por cada ciclo: base * 2^(ciclo-1), tope 1 hora
            val bloqueoMs = minOf(baseMs * (1L shl (ciclo - 1)), 60 * 60 * 1000L)
            val hasta = System.currentTimeMillis() + bloqueoMs
            prefs.edit().putLong(KEY_LOCK_UNTIL, hasta).apply()
            return bloqueoMs
        }

        return 0L
    }

    /**
     * Milisegundos que faltan para que termine el bloqueo activo.
     * Devuelve 0 si no hay bloqueo o ya expiró.
     */
    fun tiempoBloqueoRestanteMs(context: Context): Long {
        val prefs = context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
        val hasta = prefs.getLong(KEY_LOCK_UNTIL, 0L)
        return maxOf(0L, hasta - System.currentTimeMillis())
    }

    /** Resetea contador e instancia de bloqueo (tras desbloqueo exitoso). */
    fun resetearIntentos(context: Context) {
        val prefs = context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCK_UNTIL)
            .apply()
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers privados
    // ─────────────────────────────────────────────────────────────

    private fun convertirTextoAMilisegundos(texto: String): Long {
        return when (texto) {
            "15 segundos"         -> 15 * 1000L
            "30 segundos"         -> 30 * 1000L
            "1 minuto"            -> 60 * 1000L
            "3 minutos"           -> 3 * 60 * 1000L
            "5 minutos"           -> 5 * 60 * 1000L
            "10 minutos"          -> 10 * 60 * 1000L
            "Desactivar", "Nunca" -> -1L
            else                  -> 5 * 60 * 1000L
        }
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

    /**
     * Tiempo de bloqueo base (primer ciclo) según la tolerancia configurada.
     * Más tolerante al inicio → penalización base más alta.
     */
    private fun tiempoBaseMs(limite: Int): Long = when (limite) {
        3    -> 30 * 1000L          // 30 s → 1 min → 2 min → … → 1 h
        5    -> 60 * 1000L          // 1 min → 2 min → …
        7    -> 2 * 60 * 1000L      // 2 min → 4 min → …
        else -> 30 * 1000L
    }
}