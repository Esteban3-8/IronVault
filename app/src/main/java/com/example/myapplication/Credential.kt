package com.example.myapplication

import java.util.UUID

data class Credential(
    // Identificador único — se genera automáticamente al crear cada credencial.
    // Permite detectar duplicados exactos en la importación sin depender del título.
    val id: String = UUID.randomUUID().toString(),

    val serviceName: String = "",
    val userOrEmail: String = "",
    val password: String = "",
    val strengthStatus: String = "Segura", // "Segura", "Débil", "Repetida"
    val url: String = "",
    val notes: String = "",
    val category: String = "",
    val updatedAt: Long = 0L
)
