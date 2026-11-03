package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

// ─────────────────────────────────────────────────────────────────
//  SecurityActivity
// ─────────────────────────────────────────────────────────────────
class SecurityActivity : BaseActivity() { // 👈 Hereda de BaseActivity

    private val prefs by lazy {
        getSharedPreferences("security_prefs", MODE_PRIVATE)
    }

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

    private fun bloquearBoveda(motivo: String) {
        val intent = Intent(this, UnlockVaultActivity::class.java)
        intent.putExtra("lock_reason", motivo)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun configurarBotones() {

        // Botón de retroceso (si existiera en el layout)
        findViewById<android.widget.ImageView?>(R.id.btnBack)?.setOnClickListener {
            finish()
        }

        // ── Botón BLOQUEAR BÓVEDA AHORA ──────────────────────────
        findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnLockNow).setOnClickListener {
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

        // ── BARRA DE NAVEGACIÓN INFERIOR (Menú Píldora) ──────────

        // 1. Ir a Inicio (MainActivity)
        findViewById<LinearLayout>(R.id.btnNavHome).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // 2. Ir al Generador de Contraseñas / Llaves
        findViewById<LinearLayout>(R.id.btnNavKey).setOnClickListener {
            val intent = Intent(this, PasswordGeneratorActivity::class.java)
            startActivity(intent)
            finish()
        }

        // 3. Ir a Ajustes
        findViewById<LinearLayout>(R.id.btnNavSettings).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            finish()
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