package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.Firebase

class UnlockVaultActivity : BaseActivity() {

    private var isPasswordVisible = true
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Invitado: sin clave maestra, va directo al Home
        if (GuestUtils.esInvitado(this)) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        setContentView(R.layout.activity_unlock_vault)

        // ── Motivo del bloqueo ────────────────────────────────────
        val lockReason = intent.getStringExtra("lock_reason")
        val tvSubtitle = findViewById<TextView>(R.id.tvSubtitle)

        when (lockReason) {
            "manual" -> {
                tvSubtitle.text = "La bóveda ha sido bloqueada.\nIngresa tu clave maestra para continuar."
                mostrarBannerPersonalizado(
                    titulo = "Bóveda bloqueada",
                    mensaje = "Acceso bloqueado manualmente",
                    iconoResId = R.drawable.logo
                )
            }
            "auto" -> {
                tvSubtitle.text = "La bóveda se bloqueó automáticamente por inactividad.\nIngresa tu clave maestra para continuar."
                mostrarBannerPersonalizado(
                    titulo = "Bóveda bloqueada",
                    mensaje = "Bloqueada por inactividad",
                    iconoResId = R.drawable.logo
                )
            }
            else -> {
                tvSubtitle.text = "Ya iniciaste sesión. Ahora ingresa la llave que protege tu bóveda."
            }
        }

        // ── Referencias de UI ─────────────────────────────────────
        val btnBack                = findViewById<ImageView>(R.id.btnBack)
        val etMasterPassword       = findViewById<EditText>(R.id.etMasterPassword)
        val ivToggleMasterPassword = findViewById<ImageView>(R.id.ivToggleMasterPassword)
        val btnUnlockVault         = findViewById<MaterialCardView>(R.id.btnUnlockVault)
        val layoutLockout          = findViewById<LinearLayout>(R.id.layoutLockout)
        val tvCountdown            = findViewById<TextView>(R.id.tvCountdown)

        // ── Estado inicial: contraseña visible ────────────────────
        etMasterPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
        ivToggleMasterPassword.setImageResource(R.drawable.ojo)
        etMasterPassword.setSelection(etMasterPassword.text.length)

        // ── Comprobar si hay bloqueo activo al entrar ─────────────
        val restanteAlEntrar = SessionManager.tiempoBloqueoRestanteMs(this)
        if (restanteAlEntrar > 0L) {
            activarModoBloqueo(layoutLockout, tvCountdown, etMasterPassword, btnUnlockVault, restanteAlEntrar)
        }

        // ── Botón atrás ───────────────────────────────────────────
        // Cuando viene de bloqueo no hay pila a la que volver dentro de la app,
        // así que ocultamos la flecha. El gesto/botón de sistema cierra la app.
        if (lockReason == "manual" || lockReason == "auto") {
            btnBack.visibility = View.GONE
        } else {
            btnBack.setOnClickListener { finish() }
        }

        // ── Toggle visibilidad contraseña ─────────────────────────
        ivToggleMasterPassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            val selection = etMasterPassword.selectionStart
            if (isPasswordVisible) {
                etMasterPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivToggleMasterPassword.setImageResource(R.drawable.ojo)
            } else {
                etMasterPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivToggleMasterPassword.setImageResource(R.drawable.ojo_cerrado)
            }
            etMasterPassword.setSelection(selection)
        }

        // ── Desbloquear bóveda ────────────────────────────────────
        btnUnlockVault.setOnClickListener {

            // Bloqueo activo: ignorar toque
            if (SessionManager.tiempoBloqueoRestanteMs(this) > 0L) return@setOnClickListener

            val password = etMasterPassword.text.toString().trim()

            if (password.isEmpty()) {
                mostrarBannerPersonalizado(
                    titulo = "Campo requerido",
                    mensaje = "Por favor ingresa tu contraseña maestra",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }

            val user = Firebase.auth.currentUser
            if (user == null || user.email == null) {
                mostrarBannerPersonalizado(
                    titulo = "Error de sesión",
                    mensaje = "Por favor inicia sesión nuevamente.",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }

            val credential = EmailAuthProvider.getCredential(user.email!!, password)

            user.reauthenticate(credential)
                .addOnSuccessListener {
                    // Éxito: resetear intentos y abrir Home
                    SessionManager.resetearIntentos(this)
                    SessionManager.limpiarSesion(this)

                    mostrarBannerPersonalizado(
                        titulo = "¡Bóveda desbloqueada!",
                        mensaje = "Acceso concedido con éxito",
                        iconoResId = R.drawable.logo
                    )

                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
                .addOnFailureListener {
                    // Registrar intento fallido
                    val bloqueoMs = SessionManager.registrarIntentoFallido(this)

                    if (bloqueoMs > 0L) {
                        // Se activó un nuevo período de bloqueo
                        activarModoBloqueo(layoutLockout, tvCountdown, etMasterPassword, btnUnlockVault, bloqueoMs)
                        mostrarBannerPersonalizado(
                            titulo = "Demasiados intentos",
                            mensaje = "Bóveda bloqueada por ${formatearTiempo(bloqueoMs)}",
                            iconoResId = R.drawable.logo
                        )
                    } else {
                        mostrarBannerPersonalizado(
                            titulo = "Error de acceso",
                            mensaje = "Contraseña maestra incorrecta",
                            iconoResId = R.drawable.logo
                        )
                    }
                }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Muestra el panel de bloqueo y lanza la cuenta regresiva.
     * Deshabilita el campo y el botón mientras dura el bloqueo.
     */
    private fun activarModoBloqueo(
        layoutLockout: LinearLayout,
        tvCountdown: TextView,
        etPassword: EditText,
        btnUnlock: MaterialCardView,
        duracionMs: Long
    ) {
        layoutLockout.visibility = View.VISIBLE
        etPassword.isEnabled = false
        btnUnlock.alpha = 0.4f

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(duracionMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                tvCountdown.text = formatearTiempo(millisUntilFinished)
            }

            override fun onFinish() {
                // Bloqueo expiró: restaurar UI
                layoutLockout.visibility = View.GONE
                etPassword.isEnabled = true
                btnUnlock.alpha = 1f
                tvCountdown.text = "00:00"
            }
        }.start()
    }

    /** Formatea ms como MM:SS o HH:MM:SS si supera una hora. */
    private fun formatearTiempo(ms: Long): String {
        val totalSeg = ms / 1000
        val horas    = totalSeg / 3600
        val minutos  = (totalSeg % 3600) / 60
        val segundos = totalSeg % 60
        return if (horas > 0) {
            "%02d:%02d:%02d".format(horas, minutos, segundos)
        } else {
            "%02d:%02d".format(minutos, segundos)
        }
    }

    override fun onBackPressed() {
        if (intent.getStringExtra("lock_reason") == "manual" ||
            intent.getStringExtra("lock_reason") == "auto") {
            // Cierra limpiamente toda la tarea; el usuario puede volver a abrir la app
            finishAffinity()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}