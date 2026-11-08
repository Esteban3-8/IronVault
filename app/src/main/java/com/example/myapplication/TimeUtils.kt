package com.example.myapplication

object TimeUtils {

    /**
     * Convierte una fecha (en milisegundos) en un texto como
     * "Actualizada hace 3 días". Si no hay fecha guardada, lo indica.
     */
    fun tiempoRelativo(timestamp: Long): String {
        if (timestamp <= 0L) return "Sin fecha de actualización"

        val diff = System.currentTimeMillis() - timestamp
        if (diff < 0L) return "Actualizada hace un momento"

        val minutos = diff / (60L * 1000L)
        val horas = diff / (60L * 60L * 1000L)
        val dias = diff / (24L * 60L * 60L * 1000L)
        val meses = dias / 30L
        val anios = dias / 365L

        return when {
            minutos < 1L -> "Actualizada hace un momento"
            minutos < 60L -> "Actualizada hace $minutos min"
            horas < 24L -> if (horas == 1L) "Actualizada hace 1 hora" else "Actualizada hace $horas horas"
            dias < 30L -> if (dias == 1L) "Actualizada hace 1 día" else "Actualizada hace $dias días"
            dias < 365L -> if (meses == 1L) "Actualizada hace 1 mes" else "Actualizada hace $meses meses"
            else -> if (anios == 1L) "Actualizada hace 1 año" else "Actualizada hace $anios años"
        }
    }
}