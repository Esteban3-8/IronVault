package com.example.myapplication

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// ─────────────────────────────────────────────────────────────────
//  AccessHistoryActivity
//  Muestra el historial de intentos de desbloqueo de la bóveda.
//  Por ahora los registros son estáticos (definidos en el XML).
//  En el futuro se pueden cargar dinámicamente desde una base de datos.
// ─────────────────────────────────────────────────────────────────
class AccessHistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_access_history)

        // ── WindowInsets ──────────────────────────────────────────
        // Este layout tiene paddingStart/End="20dp" y paddingTop="24dp"
        // en el XML. Usamos el mismo patrón que aprendimos: convertir
        // los dp a px y pasarlos en setPadding para no perder el padding
        // horizontal del XML al aplicar los insets del sistema.
        val dpStart = (20 * resources.displayMetrics.density).toInt()
        val dpEnd   = (20 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(dpStart, systemBars.top, dpEnd, systemBars.bottom)
            insets
        }

        configurarBotones()
    }

    // ─────────────────────────────────────────────────────────────
    //  configurarBotones()
    // ─────────────────────────────────────────────────────────────
    private fun configurarBotones() {

        // ── Botón ATRÁS ───────────────────────────────────────────
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // ── Botón BORRAR HISTORIAL ────────────────────────────────
        // Antes de borrar algo importante, siempre es buena práctica
        // pedir confirmación al usuario con un AlertDialog.
        //
        // CONCEPTO: AlertDialog
        // Es el diálogo nativo de Android (el cuadro que aparece en
        // el centro de la pantalla). Se construye con un Builder:
        //   · setTitle()    → título del diálogo
        //   · setMessage()  → texto explicativo
        //   · setPositiveButton() → botón de acción principal (Confirmar)
        //   · setNegativeButton() → botón de cancelar
        //   · show()        → lo muestra en pantalla
        //
        // Los botones reciben una lambda con dos parámetros:
        //   dialog → referencia al diálogo (para cerrarlo si hace falta)
        //   which  → qué botón se presionó (raramente necesario)
        // Si no los necesitas, puedes usar "_" para ignorarlos.
        findViewById<AppCompatButton>(R.id.btnClearHistory).setOnClickListener {
            mostrarConfirmacionBorrar()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  mostrarConfirmacionBorrar()
    //  Construye y muestra el AlertDialog de confirmación.
    //  Separamos esto en su propia función para mantener
    //  configurarBotones() limpio y fácil de leer.
    // ─────────────────────────────────────────────────────────────
    private fun mostrarConfirmacionBorrar() {

        // AlertDialog.Builder recibe el contexto (this = Activity actual)
        AlertDialog.Builder(this)
            .setTitle("Borrar historial")
            .setMessage("¿Estás seguro de que quieres borrar todo el historial de accesos? Esta acción no se puede deshacer.")

            // Botón positivo: acción destructiva (borrar)
            // "_, _" → ignoramos los parámetros dialog y which
            .setPositiveButton("Borrar") { _, _ ->
                // Aquí iría la lógica real de borrado (base de datos, etc.)
                // Por ahora el historial es estático en el XML, así que
                // solo mostramos un mensaje de confirmación.
                Toast.makeText(this, "Historial borrado", Toast.LENGTH_SHORT).show()
            }

            // Botón negativo: cancela sin hacer nada
            // El diálogo se cierra automáticamente al tocar cualquier botón
            .setNegativeButton("Cancelar", null)

            // show() construye el diálogo y lo muestra.
            // Sin esta línea el diálogo no aparece.
            .show()
    }
}
