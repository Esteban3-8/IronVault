package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

class EditarCategoriaActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_categoria)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // Desplegable de grupo
        val dropdown = findViewById<LinearLayout>(R.id.groupDropdown)
        val arrow = findViewById<ImageView>(R.id.ivGroupArrow)
        findViewById<LinearLayout>(R.id.groupHeader).setOnClickListener {
            val abierto = dropdown.visibility == View.VISIBLE
            dropdown.visibility = if (abierto) View.GONE else View.VISIBLE
            arrow.setImageResource(
                if (abierto) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up
            )
        }

        // Panel de ícono / color (el mismo de Nueva Categoría)
        findViewById<View>(R.id.btnIcon).setOnClickListener {
            val sheet = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.dialog_icon_category, null)
            sheet.setContentView(view)
            (view.parent as View).setBackgroundColor(Color.TRANSPARENT)
            view.findViewById<View>(R.id.btnApply).setOnClickListener { sheet.dismiss() }
            sheet.show()
        }

        // Diálogo de eliminar
        findViewById<ImageView>(R.id.btnDelete).setOnClickListener {
            val view = layoutInflater.inflate(R.layout.dialog_eliminar_categoria, null)
            val dialog = AlertDialog.Builder(this).setView(view).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            view.findViewById<View>(R.id.btnCancelDelete).setOnClickListener { dialog.dismiss() }
            // La eliminación real va en la fase de lógica
            view.findViewById<View>(R.id.btnConfirmDelete).setOnClickListener { dialog.dismiss() }
            dialog.show()

            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
    }
}