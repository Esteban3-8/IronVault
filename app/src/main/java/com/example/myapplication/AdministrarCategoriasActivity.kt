package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.view.View

class AdministrarCategoriasActivity : BaseActivity() { // 👈 Hereda de BaseActivity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_administrar_categorias)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<ImageView>(R.id.btnAdd).setOnClickListener {
            val intent = Intent(this, NuevaCategoriaActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.rowMail).setOnClickListener {
            val intent = Intent(this, EditarCategoriaActivity::class.java)
            startActivity(intent)
        }
    }
}