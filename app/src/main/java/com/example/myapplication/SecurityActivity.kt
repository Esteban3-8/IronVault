package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

// ─────────────────────────────────────────────────────────────────
//  SecurityActivity
// ─────────────────────────────────────────────────────────────────
class SecurityActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("security_prefs", MODE_PRIVATE)
    }

    // ── Handler para el bloqueo automático ───────────────────────
    // Handler es una clase de Android que permite ejecutar código
    // después de un retraso (como un setTimeout en JavaScript).
    // Looper.getMainLooper() indica que el código se ejecutará en
    // el hilo principal (el único que puede tocar la interfaz).
    private val autoLockHandler = Handler(Looper.getMainLooper())

    // Runnable es el bloque de código que se ejecutará cuando
    // el temporizador llegue a 0. Lo guardamos en una variable
    // para poder cancelarlo con removeCallbacks() si el usuario
    // regresa a tiempo.
    private val autoLockRunnable = Runnable {
        // El tiempo se agotó → bloquear la bóveda automáticamente
        bloquearBoveda(motivo = "auto")
    }

    // Controla si el temporizador está activo para no iniciarlo dos veces
    private var autoLockScheduled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        configurarBotones()
        cargarValoresGuardados()
    }

    // ─────────────────────────────────────────────────────────────
    //  onPause() / onResume() — Ciclo de vida
    //
    //  CONCEPTO: Ciclo de vida de una Activity
    //  onPause()  → se llama cuando la Activity pierde el foco:
    //               el usuario cambia de app, apaga la pantalla, etc.
    //               Aquí iniciamos el temporizador de bloqueo.
    //
    //  onResume() → se llama cuando la Activity vuelve al frente.
    //               Si el temporizador no ha llegado a 0, lo cancelamos
    //               (el usuario regresó a tiempo).
    // ─────────────────────────────────────────────────────────────
    override fun onPause() {
        super.onPause()
        iniciarTemporizadorBloqueo()
    }

    override fun onResume() {
        super.onResume()
        // Cancelar el temporizador si el usuario regresó antes de que expirara
        if (autoLockScheduled) {
            autoLockHandler.removeCallbacks(autoLockRunnable)
            autoLockScheduled = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar el handler al destruir la Activity para evitar memory leaks
        autoLockHandler.removeCallbacks(autoLockRunnable)
    }

    // ─────────────────────────────────────────────────────────────
    //  iniciarTemporizadorBloqueo()
    //  Lee el tiempo configurado en SharedPreferences, lo convierte
    //  a milisegundos y programa el Runnable de bloqueo.
    //
    //  Handler.postDelayed(runnable, milisegundos) → ejecuta el
    //  runnable después del tiempo indicado en ms.
    // ─────────────────────────────────────────────────────────────
    private fun iniciarTemporizadorBloqueo() {
        val valorGuardado = prefs.getString("auto_lock", "5 minutos") ?: "5 minutos"

        // Convertimos el texto guardado a milisegundos
        val delayMs = when (valorGuardado) {
            "15 segundos" -> 15_000L
            "30 segundos" -> 30_000L
            "1 minuto"    -> 60_000L
            "3 minutos"   -> 3 * 60_000L
            "5 minutos"   -> 5 * 60_000L
            "10 minutos"  -> 10 * 60_000L
            else          -> 5 * 60_000L  // fallback: 5 minutos
        }

        // Programamos el Runnable para que se ejecute después de "delayMs"
        autoLockHandler.postDelayed(autoLockRunnable, delayMs)
        autoLockScheduled = true
    }

    // ─────────────────────────────────────────────────────────────
    //  bloquearBoveda()
    //  Navega a UnlockVaultActivity pasando el motivo del bloqueo.
    //
    //  CONCEPTO: Intent Extras
    //  Los extras son datos que puedes adjuntar a un Intent para
    //  pasarlos a la pantalla destino, como parámetros de función.
    //  intent.putExtra("clave", valor) → adjunta un dato
    //  En la pantalla destino: intent.getStringExtra("clave") → lo lee
    // ─────────────────────────────────────────────────────────────
    private fun bloquearBoveda(motivo: String) {
        val intent = Intent(this, UnlockVaultActivity::class.java)

        // Adjuntamos el motivo del bloqueo para que UnlockVaultActivity
        // muestre el mensaje adecuado al aparecer
        intent.putExtra("lock_reason", motivo)

        // FLAG_ACTIVITY_NEW_TASK + CLEAR_TASK: limpia toda la pila
        // de pantallas para que el usuario no pueda volver atrás
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun configurarBotones() {

        findViewById<android.widget.ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // ── Botón BLOQUEAR BÓVEDA AHORA ──────────────────────────
        // Pasamos motivo "manual" para que UnlockVaultActivity
        // muestre "La bóveda ha sido bloqueada"
        findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnLockNow).setOnClickListener {
            // Cancelamos el timer automático (ya no hace falta, vamos a bloquear ya)
            autoLockHandler.removeCallbacks(autoLockRunnable)
            bloquearBoveda(motivo = "manual")
        }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardChangeKey).setOnClickListener {
            startActivity(Intent(this, ChangeMasterKeyActivity::class.java))
        }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardAutoLock).setOnClickListener {
            mostrarDialogoAutoLock()
        }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardAttempts).setOnClickListener {
            mostrarDialogoIntentos()
        }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardHistory).setOnClickListener {
            startActivity(Intent(this, AccessHistoryActivity::class.java))
        }
    }

    private fun cargarValoresGuardados() {
        val tvAutoLock = findViewById<TextView>(R.id.tvAutoLockValue)
        val tvAttempts = findViewById<TextView>(R.id.tvAttemptsValue)
        tvAutoLock.text = prefs.getString("auto_lock", "5 minutos")
        tvAttempts.text = prefs.getString("attempts", "3 Intentos")
    }

    private fun mostrarDialogoAutoLock() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_auto_lock, null)
        dialog.setContentView(view)

        val opt15s      = view.findViewById<TextView>(R.id.opt15s)
        val opt30s      = view.findViewById<TextView>(R.id.opt30s)
        val opt1m       = view.findViewById<TextView>(R.id.opt1m)
        val opt3m       = view.findViewById<TextView>(R.id.opt3m)
        val opt5m       = view.findViewById<TextView>(R.id.opt5m)
        val opt10m      = view.findViewById<TextView>(R.id.opt10m)
        val btnCancelar = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnCancelSheet)

        fun seleccionar(valor: String) {
            prefs.edit().putString("auto_lock", valor).apply()
            findViewById<TextView>(R.id.tvAutoLockValue).text = valor
            Toast.makeText(this, "Bloqueo automático: $valor", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        opt15s.setOnClickListener  { seleccionar("15 segundos") }
        opt30s.setOnClickListener  { seleccionar("30 segundos") }
        opt1m.setOnClickListener   { seleccionar("1 minuto")    }
        opt3m.setOnClickListener   { seleccionar("3 minutos")   }
        opt5m.setOnClickListener   { seleccionar("5 minutos")   }
        opt10m.setOnClickListener  { seleccionar("10 minutos")  }
        btnCancelar.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun mostrarDialogoIntentos() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_attempts, null)
        dialog.setContentView(view)

        val opt3a       = view.findViewById<TextView>(R.id.opt3a)
        val opt5a       = view.findViewById<TextView>(R.id.opt5a)
        val opt7a       = view.findViewById<TextView>(R.id.opt7a)
        val optNever    = view.findViewById<TextView>(R.id.optNever)
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
