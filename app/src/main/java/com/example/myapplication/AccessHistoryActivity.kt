package com.example.myapplication

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// ─────────────────────────────────────────────────────────────────
//  AccessHistoryActivity
//  Muestra el historial de intentos de desbloqueo de la bóveda.
//  Por ahora los registros son estáticos (definidos en el XML).
//  En el futuro se pueden cargar dinámicamente desde una base de datos.
// ─────────────────────────────────────────────────────────────────
class AccessHistoryActivity : BaseActivity() { // 👈 Hereda de BaseActivity para el control de inactividad

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_access_history)

        val dpStart = (20 * resources.displayMetrics.density).toInt()
        val dpEnd   = (20 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(dpStart, systemBars.top, dpEnd, systemBars.bottom)
            insets
        }

        configurarBotones()
    }

    private fun configurarBotones() {

        // ── Botón ATRÁS ───────────────────────────────────────────
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // ── Botón BORRAR HISTORIAL ────────────────────────────────
        findViewById<AppCompatButton>(R.id.btnClearHistory).setOnClickListener {
            mostrarConfirmacionBorrar()
        }
    }

    private fun mostrarConfirmacionBorrar() {

        AlertDialog.Builder(this)
            .setTitle("Borrar historial")
            .setMessage("¿Estás seguro de que quieres borrar todo el historial de accesos? Esta acción no se puede deshacer.")

            // Botón positivo: acción destructiva (borrar)
            .setPositiveButton("Borrar") { _, _ ->
                mostrarBannerPersonalizado(
                    titulo = "Historial borrado",
                    mensaje = "Se ha eliminado el registro de accesos",
                    iconoResId = R.drawable.logo
                )
            }

            // Botón negativo: cancela sin hacer nada
            .setNegativeButton("Cancelar", null)
            .show()
    }
}