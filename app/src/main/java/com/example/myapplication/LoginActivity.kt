package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity : BaseActivity() { // 👈 Hereda de BaseActivity

    private var isPasswordVisible = true // Inicia visible por defecto

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 0. Verificación automática si ya hay una sesión activa en Firebase
        if (Firebase.auth.currentUser != null) {
            val intent = Intent(this, UnlockVaultActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        // Referencias de UI
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val ivTogglePassword = findViewById<ImageView>(R.id.ivTogglePassword)
        val cbRemember = findViewById<CheckBox>(R.id.cbRemember)
        val btnContinue = findViewById<MaterialCardView>(R.id.btnContinue)
        val btnForgotPassword = findViewById<TextView>(R.id.btnForgotPassword)
        val btnGoToRegister = findViewById<TextView>(R.id.btnGoToRegister)

        // Sincronizar estado inicial: Contraseña visible y ojo abierto
        isPasswordVisible = true
        etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
        ivTogglePassword.setImageResource(R.drawable.ojo)

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
                ivTogglePassword.setImageResource(R.drawable.ojo)
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivTogglePassword.setImageResource(R.drawable.ojo_cerrado)
            }
            etPassword.setSelection(selection.coerceAtMost(etPassword.text.length))
        }

        // 3. Botón "Continuar" con autenticación de Firebase y validaciones
        btnContinue.setOnClickListener {
            val password = etPassword.text.toString().trim()

            // Validación si el campo está vacío
            if (password.isEmpty()) {
                Toast.makeText(this, "Por favor ingresa tu contraseña para continuar", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Validación si la contraseña es muy corta
            if (password.length < 6) {
                Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val sharedPreferences = getSharedPreferences("IronVaultPrefs", Context.MODE_PRIVATE)
            val savedEmail = sharedPreferences.getString("user_email", null)?.trim()

            // Validación si no hay un correo válido guardado en el dispositivo
            if (savedEmail.isNullOrEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(savedEmail).matches()) {
                Toast.makeText(this, "Error: No hay una cuenta vinculada en este dispositivo. Regístrate primero.", Toast.LENGTH_LONG).show()
                val intent = Intent(this, RegisterActivity::class.java)
                startActivity(intent)
                finish()
                return@setOnClickListener
            }

            // Guardar o limpiar la preferencia de la casilla "Recordarme"
            val editor = sharedPreferences.edit()
            if (cbRemember.isChecked) {
                editor.putBoolean("remember_user", true)
                editor.putString("last_email", savedEmail)
            } else {
                editor.putBoolean("remember_user", false)
                editor.remove("last_email")
            }
            editor.apply()

            Log.d("LOGIN_DEBUG", "Intentando iniciar sesión con el correo: $savedEmail")

            // Autenticación segura con Firebase Auth
            val auth = Firebase.auth
            auth.signInWithEmailAndPassword(savedEmail, password)
                .addOnSuccessListener {
                    Toast.makeText(this, "¡Inicio de sesión exitoso!", Toast.LENGTH_SHORT).show()

                    // ── GUARDAR SESIÓN PERSISTENTE PARA WELCOMEACTIVITY ──
                    val settingsPrefs = getSharedPreferences("settings_prefs", MODE_PRIVATE)
                    settingsPrefs.edit().putBoolean("is_logged_in", true).apply()
                    // ----------------------------------------------------

                    val intent = Intent(this, UnlockVaultActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    // Mensaje claro de advertencia en pantalla si la contraseña es incorrecta
                    Toast.makeText(this, "Contraseña incorrecta o usuario no encontrado", Toast.LENGTH_LONG).show()
                }
        }

        // 4. Recuperación de contraseña
        btnForgotPassword.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }

        // 5. Ir al registro
        btnGoToRegister?.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}