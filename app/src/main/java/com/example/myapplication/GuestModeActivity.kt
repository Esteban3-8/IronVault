package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatButton

class GuestModeActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guest_mode)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // Modo invitado para probar la app: sin clave maestra, directo a la bóveda
        findViewById<AppCompatButton>(R.id.btnContinueGuest).setOnClickListener {
            GuestUtils.marcarInvitado(this, true)

            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }

        // Mejor crear una cuenta
        findViewById<AppCompatButton>(R.id.btnCreateAccount).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }
}