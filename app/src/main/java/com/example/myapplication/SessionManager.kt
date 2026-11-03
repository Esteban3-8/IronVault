package com.example.myapplication

import android.content.Context

object SessionManager {
    private const val PREF_NAME = "settings_prefs"
    private const val KEY_LAST_BACKGROUND_TIME = "last_background_time"
    private const val TIMEOUT_LIMIT_MS = 5 * 60 * 1000L // 30 segundos de prueba (cámbialo a 5 * 60 * 1000L cuando termines de probar)

    fun registrarSalida(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_BACKGROUND_TIME, System.currentTimeMillis()).apply()
    }

    fun haExpiradoInactividad(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastTime = prefs.getLong(KEY_LAST_BACKGROUND_TIME, 0L)

        if (lastTime == 0L) return false

        val tiempoTranscurrido = System.currentTimeMillis() - lastTime
        return tiempoTranscurrido > TIMEOUT_LIMIT_MS
    }

    fun limpiarSesion(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // Elimina el registro de tiempo para reiniciar el temporizador al entrar a la bóveda
        prefs.edit().remove(KEY_LAST_BACKGROUND_TIME).apply()
    }
}