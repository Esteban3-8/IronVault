package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

// ─────────────────────────────────────────────────────────────────
//  SecurityActivity
//  Pantalla de seguridad: muestra opciones de bloqueo, intentos,
//  historial y cambio de clave maestra.
// ─────────────────────────────────────────────────────────────────
class SecurityActivity : AppCompatActivity() {

    // ── SharedPreferences ────────────────────────────────────────
    // SharedPreferences es como un "bloc de notas" interno de la app.
    // Guarda datos simples (texto, números, booleanos) que persisten
    // aunque el usuario cierre la app. Se accede por nombre de archivo
    // ("security_prefs") y un modo de acceso (MODE_PRIVATE = solo esta app).
    private val prefs by lazy {
        getSharedPreferences("security_prefs", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // enableEdgeToEdge() hace que el contenido se dibuje bajo
        // la barra de estado y la barra de navegación del sistema.
        // Lo quitamos aquí porque usamos WindowInsets manualmente abajo.
        setContentView(R.layout.activity_security)

        // WindowInsets: ajusta el padding de la vista raíz para que
        // el contenido no quede tapado por la barra de estado (arriba)
        // o la barra de navegación (abajo) del sistema.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Llamamos a las funciones que configuran cada parte de la pantalla.
        // Separar en funciones hace el código más ordenado y fácil de leer.
        configurarBotones()
        cargarValoresGuardados()
    }

    // ─────────────────────────────────────────────────────────────
    //  configurarBotones()
    //  Aquí conectamos cada vista del XML con su acción.
    //  "findViewById" busca una vista por su ID (el que pusiste en
    //  android:id="@+id/...") y nos devuelve el objeto para manipularlo.
    // ─────────────────────────────────────────────────────────────
    private fun configurarBotones() {

        // ── Botón ATRÁS ──────────────────────────────────────────
        // finish() cierra la Activity actual y regresa a la anterior
        // en la pila de navegación (back stack).
        findViewById<android.widget.ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // ── Botón BLOQUEAR BÓVEDA AHORA ─────────────────────────
        // Por ahora redirige a la pantalla de desbloqueo (UnlockVaultActivity).
        // Intent es el "mensaje" que Android usa para navegar entre pantallas.
        // startActivity() lanza la nueva pantalla.
        // finishAffinity() cierra esta Activity Y todas las anteriores,
        // así el usuario no puede volver con el botón atrás.
        findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnLockNow).setOnClickListener {
            val intent = Intent(this, UnlockVaultActivity::class.java)
            startActivity(intent)
            finishAffinity() // Bloquea la app completamente: limpia el historial de pantallas
        }

        // ── Card CAMBIAR CLAVE MAESTRA ───────────────────────────
        // Al tocar la card, abrimos ChangeMasterKeyActivity.
        // No usamos finishAffinity() aquí porque queremos que el usuario
        // pueda volver con el botón atrás.
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardChangeKey).setOnClickListener {
            startActivity(Intent(this, ChangeMasterKeyActivity::class.java))
        }

        // ── Card BLOQUEO AUTOMÁTICO ──────────────────────────────
        // Muestra un BottomSheet (panel deslizante desde abajo)
        // con las opciones de tiempo.
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardAutoLock).setOnClickListener {
            mostrarDialogoAutoLock()
        }

        // ── Card INTENTOS DE ACCESO ──────────────────────────────
        // Muestra un BottomSheet con las opciones de intentos permitidos.
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardAttempts).setOnClickListener {
            mostrarDialogoIntentos()
        }

        // ── Card HISTORIAL DE ACCESOS ────────────────────────────
        // Navega a la pantalla del historial.
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardHistory).setOnClickListener {
            startActivity(Intent(this, AccessHistoryActivity::class.java))
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  cargarValoresGuardados()
    //  Al abrir la pantalla, leemos los valores que el usuario eligió
    //  anteriormente (guardados en SharedPreferences) y los mostramos
    //  en los chips de color naranja.
    //  getString("clave", "valor_por_defecto") → lee un String guardado.
    //  Si no existe todavía, devuelve el valor por defecto.
    // ─────────────────────────────────────────────────────────────
    private fun cargarValoresGuardados() {
        val tvAutoLock = findViewById<TextView>(R.id.tvAutoLockValue)
        val tvAttempts = findViewById<TextView>(R.id.tvAttemptsValue)

        // Leemos el valor guardado. Si es la primera vez, usamos el
        // valor por defecto que viene del layout ("5 minutos" / "3 Intentos").
        tvAutoLock.text = prefs.getString("auto_lock", "5 minutos")
        tvAttempts.text = prefs.getString("attempts", "3 Intentos")
    }

    // ─────────────────────────────────────────────────────────────
    //  mostrarDialogoAutoLock()
    //  Crea y muestra un BottomSheetDialog con el layout dialog_auto_lock.
    //
    //  BottomSheetDialog: diálogo que aparece deslizándose desde abajo.
    //  LayoutInflater: "infla" (convierte) un archivo XML en objetos View
    //  que podemos manipular en código.
    // ─────────────────────────────────────────────────────────────
    private fun mostrarDialogoAutoLock() {
        // Creamos el diálogo
        val dialog = BottomSheetDialog(this)

        // Inflamos el layout del diálogo: convertimos el XML en una Vista
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_auto_lock, null)

        // Le decimos al diálogo qué vista mostrar
        dialog.setContentView(view)

        // Obtenemos las opciones del diálogo por su ID
        val opt15s   = view.findViewById<TextView>(R.id.opt15s)
        val opt30s   = view.findViewById<TextView>(R.id.opt30s)
        val opt1m    = view.findViewById<TextView>(R.id.opt1m)
        val opt3m    = view.findViewById<TextView>(R.id.opt3m)
        val opt5m    = view.findViewById<TextView>(R.id.opt5m)
        val opt10m   = view.findViewById<TextView>(R.id.opt10m)
        val btnCancelar = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnCancelSheet)

        // Función local para manejar la selección.
        // Al tocar una opción: guardamos en SharedPreferences, actualizamos
        // el chip de la pantalla principal y cerramos el diálogo.
        fun seleccionar(valor: String) {
            // prefs.edit() abre el "bloc de notas" para escritura
            // putString("clave", valor) escribe el valor
            // apply() guarda los cambios de forma asíncrona (en segundo plano)
            prefs.edit().putString("auto_lock", valor).apply()

            // Actualizamos el TextView del chip en la pantalla principal
            findViewById<TextView>(R.id.tvAutoLockValue).text = valor

            // Cerramos el BottomSheet
            dialog.dismiss()
        }

        // Asignamos la función seleccionar a cada opción
        opt15s.setOnClickListener  { seleccionar("15 segundos") }
        opt30s.setOnClickListener  { seleccionar("30 segundos") }
        opt1m.setOnClickListener   { seleccionar("1 minuto")    }
        opt3m.setOnClickListener   { seleccionar("3 minutos")   }
        opt5m.setOnClickListener   { seleccionar("5 minutos")   }
        opt10m.setOnClickListener  { seleccionar("10 minutos")  }
        btnCancelar.setOnClickListener { dialog.dismiss() }

        // Mostramos el diálogo
        dialog.show()
    }

    // ─────────────────────────────────────────────────────────────
    //  mostrarDialogoIntentos()
    //  Igual que el anterior pero para los intentos de acceso.
    //  Nota: el botón Cancelar en este diálogo es un MaterialCardView,
    //  no un AppCompatButton, por eso usamos MaterialCardView abajo.
    // ─────────────────────────────────────────────────────────────
    private fun mostrarDialogoIntentos() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_attempts, null)
        dialog.setContentView(view)

        val opt3a      = view.findViewById<TextView>(R.id.opt3a)
        val opt5a      = view.findViewById<TextView>(R.id.opt5a)
        val opt7a      = view.findViewById<TextView>(R.id.opt7a)
        val optNever   = view.findViewById<TextView>(R.id.optNever)
        // En dialog_attempts el "Cancelar" es un MaterialCardView
        val btnCancelar = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnCancelSheet)

        fun seleccionar(valor: String) {
            prefs.edit().putString("attempts", valor).apply()
            findViewById<TextView>(R.id.tvAttemptsValue).text = valor
            dialog.dismiss()
        }

        opt3a.setOnClickListener    { seleccionar("3 Intentos") }
        opt5a.setOnClickListener    { seleccionar("5 Intentos") }
        opt7a.setOnClickListener    { seleccionar("7 Intentos") }
        optNever.setOnClickListener { seleccionar("Nunca")      }
        btnCancelar.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
