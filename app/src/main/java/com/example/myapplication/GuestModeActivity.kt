package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class GuestModeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guest_mode)

        // 1. Flecha de retroceso (cierra esta actividad)
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        // 2. Botón: Entiendo, continuar sin cuenta
        val btnContinueGuest = findViewById<AppCompatButton>(R.id.btnContinueGuest)
        btnContinueGuest.setOnClickListener {
            finish()
        }

        // 3. Botón: Mejor crear una cuenta (va directo al registro)
        val btnCreateAccount = findViewById<AppCompatButton>(R.id.btnCreateAccount)
        btnCreateAccount.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}