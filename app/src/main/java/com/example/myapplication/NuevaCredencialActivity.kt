package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class NuevaCredencialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nueva_credencial)

        // Clic en la flecha atrás -> Regresa al Home
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack?.setOnClickListener {
            finish() // Cierra esta pantalla y vuelve a MainActivity
        }

        // Clic en el icono de la llave interna -> Abre el Generador de contraseñas
        val btnGeneratePassword = findViewById<ImageView>(R.id.btnGeneratePassword)
        btnGeneratePassword?.setOnClickListener {
            val intent = Intent(this, PasswordGeneratorActivity::class.java)
            startActivity(intent)
        }
    }
}