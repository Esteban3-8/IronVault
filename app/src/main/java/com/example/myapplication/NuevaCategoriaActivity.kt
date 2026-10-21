package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color
import com.google.android.material.bottomsheet.BottomSheetDialog


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

        findViewById<View>(R.id.btnIcon).setOnClickListener {
            val dialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.dialog_icon_category, null)
            dialog.setContentView(view)
            (view.parent as View).setBackgroundColor(Color.TRANSPARENT)
            view.findViewById<View>(R.id.btnApply).setOnClickListener { dialog.dismiss() }
            dialog.show()
        }
    }
}