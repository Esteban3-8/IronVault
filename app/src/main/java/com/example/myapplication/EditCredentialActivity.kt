package com.example.myapplication

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class EditCredentialActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_credential)

        // 1. Botón de retroceso (flecha superior izquierda)
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack?.setOnClickListener {
            finish()
        }

        // 2. Botón Guardar
        val btnSave = findViewById<AppCompatButton>(R.id.btnSave)
        btnSave?.setOnClickListener {
            // Aquí puedes agregar la lógica para guardar los cambios
            finish()
        }

        // 3. Botón Cancelar
        val btnDelete = findViewById<AppCompatButton>(R.id.btnDelete)
        btnDelete?.setOnClickListener {
            finish()
        }
    }
}