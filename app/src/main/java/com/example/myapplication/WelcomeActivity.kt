package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth

class WelcomeActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Verificación de sesión persistente ────────────────────
        val prefs = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)

        if (isLoggedIn) {
            // Si la sesión está activa, saltamos la bienvenida y vamos directo a la app principal
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        // Si no hay sesión activa, cargamos la pantalla de bienvenida con normalidad
        setContentView(R.layout.activity_welcome)

        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val txtAbout = findViewById<TextView>(R.id.txtAbout)
        val tvGuestMode = findViewById<TextView>(R.id.tvGuestMode)

        // Ir a Registro
        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Ir a Iniciar Sesión
        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Abrir Reglas de Negocio
        txtAbout.setOnClickListener {
            startActivity(Intent(this, BusinessRulesActivity::class.java))
        }

        // Modo invitado: cerramos cualquier sesión de Firebase para no mezclar datos
        tvGuestMode.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, GuestModeActivity::class.java))
        }
    }
}