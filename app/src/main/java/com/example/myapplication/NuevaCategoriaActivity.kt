package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class NuevaCategoriaActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nueva_categoria)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val dropdown = findViewById<LinearLayout>(R.id.groupDropdown)
        val arrow = findViewById<ImageView>(R.id.ivGroupArrow)

        findViewById<LinearLayout>(R.id.groupHeader).setOnClickListener {
            val abierto = dropdown.visibility == View.VISIBLE
            dropdown.visibility = if (abierto) View.GONE else View.VISIBLE
            arrow.setImageResource(
                if (abierto) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up
            )
        }
    }
}