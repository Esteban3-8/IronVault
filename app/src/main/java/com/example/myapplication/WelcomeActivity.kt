package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val txtAbout = findViewById<TextView>(R.id.txtAbout) // ¡Ahora apunta al TextView correcto!
        val tvGuestMode = findViewById<TextView>(R.id.tvGuestMode)
        // Ir a Registro
        btnRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // Ir a Iniciar Sesión
        btnLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        // Abrir Reglas de Negocio
        txtAbout.setOnClickListener {
            val intent = Intent(this, BusinessRulesActivity::class.java)
            startActivity(intent)
        }

        tvGuestMode.setOnClickListener {
            val intent = Intent(this, GuestModeActivity::class.java)
            startActivity(intent)
        }
    }
}