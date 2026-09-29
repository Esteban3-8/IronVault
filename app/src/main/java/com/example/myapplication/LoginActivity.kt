package com.example.myapplication

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
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
            }
            etPassword.setSelection(selection)
        }

        // 3. Botón principal "Continuar"
        btnContinue.setOnClickListener {
            val password = etPassword.text.toString().trim()

            if (password.isEmpty()) {
                Toast.makeText(this, "Por favor ingresa tu contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Si todo está bien, pasa a la pantalla de la contraseña maestra (UnlockVaultActivity)
            val intent = Intent(this, UnlockVaultActivity::class.java)
            startActivity(intent)
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