package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.TextView

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
            val colors = view.findViewById<View>(R.id.containerColors)
            val icons = view.findViewById<View>(R.id.containerIcons)
            val tvColor = view.findViewById<TextView>(R.id.tvTabColor)
            val tvIcon = view.findViewById<TextView>(R.id.tvTabIcon)
            val lineColor = view.findViewById<View>(R.id.lineColor)
            val lineIcon = view.findViewById<View>(R.id.lineIcon)

            fun mostrarColor(esColor: Boolean) {
                colors.visibility = if (esColor) View.VISIBLE else View.GONE
                icons.visibility = if (esColor) View.GONE else View.VISIBLE
                val on = getColor(R.color.orange_primary)
                val off = getColor(R.color.text_muted)
                val lineOff = getColor(R.color.border_color)
                tvColor.setTextColor(if (esColor) on else off)
                tvIcon.setTextColor(if (esColor) off else on)
                lineColor.setBackgroundColor(if (esColor) on else lineOff)
                lineIcon.setBackgroundColor(if (esColor) lineOff else on)
                lineColor.layoutParams.height = if (esColor) 6 else 3
                lineIcon.layoutParams.height = if (esColor) 3 else 6
                lineColor.requestLayout(); lineIcon.requestLayout()
            }

            view.findViewById<View>(R.id.tabColor).setOnClickListener { mostrarColor(true) }
            view.findViewById<View>(R.id.tabIcon).setOnClickListener { mostrarColor(false) }
            dialog.show()
        }
    }
}