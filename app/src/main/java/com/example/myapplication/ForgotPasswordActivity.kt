package com.example.myapplication

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class ForgotPasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val btnContinue = findViewById<AppCompatButton>(R.id.btnContinue)
        val tvLoginRedirect = findViewById<TextView>(R.id.tvLoginRedirect)

        // Regresar a la pantalla anterior
        btnBack?.setOnClickListener {
            finish()
        }

        // Acción del botón continuar
        btnContinue?.setOnClickListener {
            val email = etEmail?.text.toString().trim()
            if (email.isEmpty()) {
                etEmail?.error = "Ingresa tu correo electrónico"
            } else {
                Toast.makeText(this, "Código de recuperación enviado", Toast.LENGTH_SHORT).show()
            }
        }

        // Volver al login directamente
        tvLoginRedirect?.setOnClickListener {
            finish()
        }
    }
}