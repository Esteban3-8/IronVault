package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import android.view.View

class AdministrarCategoriasActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_administrar_categorias)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<ImageView>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, NuevaCategoriaActivity::class.java))
        }

        findViewById<View>(R.id.rowMail).setOnClickListener {
            startActivity(Intent(this, EditarCategoriaActivity::class.java))
        }
    }
}