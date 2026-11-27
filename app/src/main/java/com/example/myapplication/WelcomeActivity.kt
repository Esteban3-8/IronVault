package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth

class WelcomeActivity : BaseActivity() {
    override fun onResume() {
        super.onResume()
        val account = FirebaseAuth.getInstance().currentUser
        val visibility = if (account != null) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<TextView>(R.id.welcomeAccount).apply {
            this.visibility = visibility
            text = account?.email ?: "Sesión iniciada"
        }
        findViewById<TextView>(R.id.welcomeOtherAccount).visibility = visibility
        findViewById<Button>(R.id.btnRegister).visibility = if (account == null) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<Button>(R.id.btnLogin).text = if (account != null) "Continuar" else getString(R.string.welcome_btn_login)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return

        // Firebase conserva una sola cuenta. Continuar todavía requiere desbloquear la bóveda.
        setContentView(R.layout.activity_welcome)

        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val txtAbout = findViewById<TextView>(R.id.txtAbout)
        val tvGuestMode = findViewById<TextView>(R.id.tvGuestMode)
        findViewById<TextView>(R.id.welcomeOtherAccount).apply {
            setOnClickListener {
                VaultService.logout()
                getSharedPreferences("IronVaultPrefs", MODE_PRIVATE).edit().remove("last_email").apply()
                startActivity(Intent(this@WelcomeActivity, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        }

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
            VaultService.logout()
            startActivity(Intent(this, GuestModeActivity::class.java))
        }
    }
}
