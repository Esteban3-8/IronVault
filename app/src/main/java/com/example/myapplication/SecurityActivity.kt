package com.example.myapplication

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar

class SecurityActivity : BaseActivity() {

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

        // Invitado: no tiene clave maestra, así que se ocultan las opciones que dependen de ella
        if (GuestUtils.esInvitado(this)) {
            configurarModoInvitado()
        }
    }

    /**
     * Modo invitado: sin clave maestra no hay nada que bloquear, cambiar ni intentar.
     * Se ocultan esas opciones y el botón principal invita a crear una cuenta.
     */
    private fun configurarModoInvitado() {
        listOf(
            R.id.cardChangeKey,
            R.id.cardAutoLock,
            R.id.cardAttempts,
            R.id.cardHistory
        ).forEach { id ->
            findViewById<View>(id)?.visibility = View.GONE
        }

        val btnLock = findViewById<AppCompatButton>(R.id.btnLockNow)
        btnLock.text = "Crear cuenta para proteger tu bóveda"
        btnLock.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun bloquearBoveda(motivo: String) {
        val intent = Intent(this, UnlockVaultActivity::class.java)
        intent.putExtra("lock_reason", motivo)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun configurarBotones() {
        findViewById<ImageView?>(R.id.btnBack)?.setOnClickListener {
            finish()
        }

        findViewById<AppCompatButton>(R.id.btnLockNow).setOnClickListener {
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

        // ── Barra de navegación inferior ──
        findViewById<LinearLayout>(R.id.btnNavHome).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.btnNavKey).setOnClickListener {
            val intent = Intent(this, PasswordGeneratorActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.btnNavSettings).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
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

        val opt15s     = view.findViewById<TextView>(R.id.opt15s)
        val opt30s     = view.findViewById<TextView>(R.id.opt30s)
        val opt1m      = view.findViewById<TextView>(R.id.opt1m)
        val opt3m      = view.findViewById<TextView>(R.id.opt3m)
        val opt5m      = view.findViewById<TextView>(R.id.opt5m)
        val opt10m     = view.findViewById<TextView>(R.id.opt10m)
        val optNever   = view.findViewById<TextView>(R.id.optNever)
        val btnCancelar = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnCancelSheet)

        fun seleccionar(valor: String) {
            prefs.edit().putString("auto_lock", valor).apply()
            findViewById<TextView>(R.id.tvAutoLockValue).text = valor

            mostrarBannerPersonalizado(
                titulo = "¡Cambios Guardados!",
                mensaje = "Bloqueo automático: $valor",
                iconoResId = R.drawable.logo
            )

            dialog.dismiss()
        }

        opt15s.setOnClickListener   { seleccionar("15 segundos") }
        opt30s.setOnClickListener   { seleccionar("30 segundos") }
        opt1m.setOnClickListener    { seleccionar("1 minuto")    }
        opt3m.setOnClickListener    { seleccionar("3 minutos")   }
        opt5m.setOnClickListener    { seleccionar("5 minutos")   }
        opt10m.setOnClickListener   { seleccionar("10 minutos")  }
        optNever.setOnClickListener { seleccionar("Desactivar") }
        btnCancelar.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun mostrarDialogoIntentos() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_attempts, null)
        dialog.setContentView(view)

        val opt3a      = view.findViewById<TextView>(R.id.opt3a)
        val opt5a      = view.findViewById<TextView>(R.id.opt5a)
        val opt7a      = view.findViewById<TextView>(R.id.opt7a)
        val optNever   = view.findViewById<TextView>(R.id.optNever)
        val btnCancelar = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnCancelSheet)

        fun seleccionar(valor: String) {
            prefs.edit().putString("attempts", valor).apply()
            findViewById<TextView>(R.id.tvAttemptsValue).text = valor

            mostrarBannerPersonalizado(
                titulo = "¡Cambios Guardados!",
                mensaje = "Intentos de acceso: $valor",
                iconoResId = R.drawable.logo
            )

            dialog.dismiss()
        }

        opt3a.setOnClickListener    { seleccionar("3 Intentos") }
        opt5a.setOnClickListener    { seleccionar("5 Intentos") }
        opt7a.setOnClickListener    { seleccionar("7 Intentos") }
        optNever.setOnClickListener { seleccionar("Nunca")      }
        btnCancelar.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun mostrarBannerPersonalizado(titulo: String, mensaje: String, iconoResId: Int) {
        val rootView = findViewById<View>(R.id.main) ?: window.decorView.rootView

        val snackbar = Snackbar.make(rootView, "", Snackbar.LENGTH_LONG)
        val customView = LayoutInflater.from(this).inflate(R.layout.layout_custom_banner, null)

        customView.findViewById<TextView>(R.id.bannerTitle).text = titulo
        customView.findViewById<TextView>(R.id.bannerMessage).text = mensaje
        customView.findViewById<ImageView>(R.id.bannerIcon).setImageResource(iconoResId)

        // Animación fluida de la línea naranja de izquierda a derecha
        val orangeLine = customView.findViewById<View>(R.id.bannerLine)
        orangeLine.post {
            orangeLine.pivotX = 0f
            val animator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 2750L
                addUpdateListener { animation ->
                    val scale = animation.animatedValue as Float
                    orangeLine.scaleX = scale
                }
            }
            animator.start()
        }

        // Corrección de tipo con ViewGroup para evitar errores de compilación
        val snackbarLayout = snackbar.view as ViewGroup
        snackbarLayout.setPadding(0, 0, 0, 0)
        snackbarLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        val params = snackbarLayout.layoutParams as FrameLayout.LayoutParams
        params.gravity = Gravity.TOP
        params.topMargin = 120
        snackbarLayout.layoutParams = params

        snackbarLayout.addView(customView, 0)
        snackbar.show()
    }
}