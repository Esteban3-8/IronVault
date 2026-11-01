package com.example.myapplication

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseManager {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // ==========================================
    // 1. SECCIÓN DE AUTENTICACIÓN (Login y Registro)
    // ==========================================

    fun registrarUsuario(
        email: String,
        pass: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Error al registrarse") }
    }

    fun iniciarSesion(
        email: String,
        pass: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Correo o contraseña incorrectos") }
    }

    // ==========================================
    // 2. SECCIÓN DE BASE DE DATOS (Firestore con Cifrado Local E2EE)
    // ==========================================

    fun guardarCredencial(
        servicio: String,
        usuario: String,
        passPlana: String,
        url: String,
        categoria: String,
        notas: String,
        masterKeyBytes: ByteArray,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // Ciframos la contraseña localmente antes de enviarla a Firebase
            val passCifrada = EncryptionHelper.encrypt(passPlana, masterKeyBytes)

            val credencialMap = hashMapOf(
                "service" to servicio,
                "user" to usuario,
                "password" to passCifrada,
                "url" to url,
                "category" to categoria,
                "notas" to notas,
                "timestamp" to System.currentTimeMillis()
            )

            db.collection("credentials")
                .add(credencialMap)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { e -> onError(e.message ?: "Error desconocido") }

        } catch (e: Exception) {
            onError("Error al cifrar: ${e.message}")
        }
    }
}