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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar

class SecurityActivity : BaseActivity() {

    private val prefs by lazy {
        getSharedPreferences("security_prefs", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return
        setContentView(R.layout.activity_security)

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
        VaultService.lock(false)
        SessionManager.limpiarSesion(this)
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
            confirmMaster("security-settings") { VaultService.consume("security-settings"); mostrarDialogoAutoLock() }
        }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardAttempts).setOnClickListener {
            confirmMaster("security-settings") { VaultService.consume("security-settings"); mostrarDialogoIntentos() }
        }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardHistory).setOnClickListener {
            startActivity(Intent(this, AccessHistoryActivity::class.java))
        }

        // ── Barra de navegación inferior ──
        bindBottomNavigation(NavTab.SECURITY)
    }

    private fun cargarValoresGuardados() {
        val tvAutoLock = findViewById<TextView>(R.id.tvAutoLockValue)
        val tvAttempts = findViewById<TextView>(R.id.tvAttemptsValue)
        tvAutoLock.text = prefs.getString("auto_lock", "5 minutos")
        tvAttempts.text = prefs.getString("attempts", "3 Intentos")
    }

    /** Aplica el estilo de opción seleccionada a [selected] y lo quita del resto. */
    private fun resaltarOpcion(opciones: List<TextView>, selected: TextView) {
        opciones.forEach { tv ->
            if (tv === selected) {
                tv.setBackgroundResource(R.drawable.bg_option_selected)
                tv.setTextColor(getColor(R.color.orange_primary))
                tv.typeface = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.inter_bold)
            } else {
                tv.background = null
                tv.setTextColor(getColor(R.color.text_primary))
                tv.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
        }
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

        val opciones = listOf(opt15s, opt30s, opt1m, opt3m, opt5m, opt10m, optNever)
        val mapaOpciones = mapOf(
            "15 segundos" to opt15s,
            "30 segundos" to opt30s,
            "1 minuto"    to opt1m,
            "3 minutos"   to opt3m,
            "5 minutos"   to opt5m,
            "10 minutos"  to opt10m,
            "Desactivar"  to optNever
        )

        // Resaltar la opción actualmente guardada
        val valorGuardado = prefs.getString("auto_lock", "5 minutos") ?: "5 minutos"
        mapaOpciones[valorGuardado]?.let { resaltarOpcion(opciones, it) }

        fun seleccionar(valor: String) {
            mapaOpciones[valor]?.let { resaltarOpcion(opciones, it) }
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
        optNever.setOnClickListener { seleccionar("Desactivar")  }
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

        val opciones = listOf(opt3a, opt5a, opt7a, optNever)
        val mapaOpciones = mapOf(
            "3 Intentos" to opt3a,
            "5 Intentos" to opt5a,
            "7 Intentos" to opt7a,
            "Nunca"       to optNever
        )

        // Resaltar la opción actualmente guardada
        val valorGuardado = prefs.getString("attempts", "3 Intentos") ?: "3 Intentos"
        mapaOpciones[valorGuardado]?.let { resaltarOpcion(opciones, it) }

        fun seleccionar(valor: String) {
            mapaOpciones[valor]?.let { resaltarOpcion(opciones, it) }
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
