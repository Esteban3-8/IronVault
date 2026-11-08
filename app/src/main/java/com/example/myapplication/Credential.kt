package com.example.myapplication

data class Credential(
    val serviceName: String = "",
    val userOrEmail: String = "",
    val password: String = "",
    val strengthStatus: String = "Segura", // "Segura", "Débil", "Repetida"
    val url: String = "",
    val notes: String = "",
    val category: String = "",             // categoría elegida
    val updatedAt: Long = 0L               // NUEVO: fecha de última actualización (milisegundos)
)