package com.example.myapplication

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import com.google.android.material.bottomsheet.BottomSheetDialog

internal fun AppCompatActivity.showCategoryIconPicker(
    category: VaultCategory,
    onApply: (VaultCategory) -> Unit
) {
    val dialog = BottomSheetDialog(this)
    val view = layoutInflater.inflate(R.layout.dialog_icon_category, null)
    dialog.setContentView(view)

    // Hacer transparente el fondo nativo del BottomSheet para respetar el diseño curvo
    (view.parent as View).setBackgroundColor(Color.TRANSPARENT)

    var selection = category
    val preview = view.findViewById<ImageView>(R.id.ivPreview)
    selection.showIcon(preview)
    val iconGrid = view.findViewById<ViewGroup>(R.id.gridIcons)
    for (option in iconGrid.children) {
        option.setOnClickListener {
            selection = selection.copy(icon = option.tag as String)
            selection.showIcon(preview)
        }
    }
    val colorGrid = view.findViewById<ViewGroup>(R.id.gridColors)
    for (option in colorGrid.children) {
        option.setOnClickListener {
            selection = selection.copy(color = option.backgroundTintList?.defaultColor)
            selection.showIcon(preview)
        }
    }
    view.findViewById<View>(R.id.btnApply).setOnClickListener {
        onApply(selection)
        dialog.dismiss()
    }

    val colors = view.findViewById<View>(R.id.containerColors)
    val icons = view.findViewById<View>(R.id.containerIcons)
    val tvColor = view.findViewById<TextView>(R.id.tvTabColor)
    val tvIcon = view.findViewById<TextView>(R.id.tvTabIcon)
    val lineColor = view.findViewById<View>(R.id.lineColor)
    val lineIcon = view.findViewById<View>(R.id.lineIcon)

    // Función para alternar entre pestañas de Color e Icono
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

        lineColor.requestLayout()
        lineIcon.requestLayout()
    }

    view.findViewById<View>(R.id.tabColor).setOnClickListener { mostrarColor(true) }
    view.findViewById<View>(R.id.tabIcon).setOnClickListener { mostrarColor(false) }

    mostrarColor(false)
    dialog.show()
}
