package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {

    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Referencias de UI basadas en tu XML
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val ivTogglePassword = findViewById<ImageView>(R.id.ivTogglePassword)
        val btnContinue = findViewById<MaterialCardView>(R.id.btnContinue)
        val btnForgotPassword = findViewById<TextView>(R.id.btnForgotPassword)
        val btnGoToRegister = findViewById<TextView>(R.id.btnGoToRegister)

        // 1. Botón para regresar
        btnBack.setOnClickListener {
            finish()
        }

        // 2. Mostrar u Ocultar la contraseña con el icono del ojo
        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            val selection = etPassword.selectionStart
            if (isPasswordVisible) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivTogglePassword.setImageResource(R.drawable.ojo) // Asegúrate de tener tu icono de ojo abierto
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivTogglePassword.setImageResource(R.drawable.ojo_cerrado) // Icono de ojo cerrado
            }
            etPassword.setSelection(selection)
        }

        // 3. Botón principal "Continuar" con autenticación de Firebase
        btnContinue.setOnClickListener {
            val password = etPassword.text.toString().trim()

            if (password.isEmpty()) {
                Toast.makeText(this, "Por favor ingresa tu contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Recuperar el correo guardado previamente en SharedPreferences
            val sharedPreferences = getSharedPreferences("IronVaultPrefs", Context.MODE_PRIVATE)
            val savedEmail = sharedPreferences.getString("user_email", null)

            if (savedEmail.isNullOrEmpty()) {
                Toast.makeText(this, "No se encontró un usuario registrado en este dispositivo", Toast.LENGTH_LONG).show()
                // Opcional: Redirigir al registro si no hay correo guardado
                val intent = Intent(this, RegisterActivity::class.java)
                startActivity(intent)
                return@setOnClickListener
            }

            // Autenticar con Firebase Auth usando el correo guardado y la contraseña ingresada
            val auth = Firebase.auth
            auth.signInWithEmailAndPassword(savedEmail, password)
                .addOnSuccessListener {
                    Toast.makeText(this, "¡Inicio de sesión exitoso!", Toast.LENGTH_SHORT).show()

                    // Pasar a la pantalla de desbloqueo de bóveda (UnlockVaultActivity)
                    val intent = Intent(this, UnlockVaultActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error de autenticación: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // 4. Abrir la pantalla de recuperación de contraseña
        btnForgotPassword.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }

        // 5. Abrir la pantalla de registro
        btnGoToRegister?.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}