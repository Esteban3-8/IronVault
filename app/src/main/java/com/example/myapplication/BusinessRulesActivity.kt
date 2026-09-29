package com.example.myapplication

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class BusinessRulesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_business_rules)

        // Referencia a la flecha de retroceso (asegúrate de que el ID en tu XML sea btnBack o cámbialo por el que tenga)
        val btnBack = findViewById<ImageView>(R.id.btnBack)

        btnBack.setOnClickListener {
            finish() // Cierra esta pantalla y regresa a la anterior
        }
    }
}