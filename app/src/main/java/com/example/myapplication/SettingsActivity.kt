package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore

// ─────────────────────────────────────────────────────────────────
//  SettingsActivity
//
//  Secciones:
//   • Perfil
//   • Seguridad   : Contraseña principal · Biométrico · Categorías
//   • Gestión de datos : Exportar · Importar
//   • Zona de peligro  : Eliminar datos · Cerrar sesión
// ─────────────────────────────────────────────────────────────────
class SettingsActivity : BaseActivity() {

    private val prefs by lazy { getSharedPreferences("settings_prefs", MODE_PRIVATE) }
    private val securityPrefs by lazy { getSharedPreferences("security_prefs", MODE_PRIVATE) }

    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, sys.top, 0, 0)
            insets
        }

        configurarBotones()
        cargarEstadoBiometrico()
        configurarNavegacionInferior()
        cargarDatosPerfil()

        if (GuestUtils.esInvitado(this)) aplicarModoInvitado()
    }

    // ─────────────────────────────────────────────────────────────
    //  Modo invitado
    // ─────────────────────────────────────────────────────────────
    private fun aplicarModoInvitado() {
        // Sin cuenta: contraseña principal y biométrico no aplican
        listOf(R.id.cardMainPassword, R.id.cardBiometric).forEach { id ->
            val card = findViewById<MaterialCardView>(id) ?: return@forEach
            card.alpha = 0.4f
            card.setOnClickListener { GuestUtils.mostrarDialogoCuenta(this) }
        }
        findViewById<SwitchCompat>(R.id.switchBiometric)?.apply {
            isEnabled = false
            isChecked = false
        }
        // Exportar e importar: sí aplican para invitado (datos locales).
        // solicitarClaveMaestra() manejará la variante sin clave.
    }

    // ─────────────────────────────────────────────────────────────
    //  configurarBotones()
    // ─────────────────────────────────────────────────────────────
    private fun configurarBotones() {
        val esInvitado = GuestUtils.esInvitado(this)

        // ── Perfil ────────────────────────────────────────────────
        findViewById<AppCompatButton>(R.id.btnEditProfile).setOnClickListener {
            if (esInvitado) GuestUtils.mostrarDialogoCuenta(this)
            else startActivity(Intent(this, ProfileActivity::class.java))
        }

        // ── Contraseña principal ──────────────────────────────────
        if (!esInvitado) {
            findViewById<MaterialCardView>(R.id.cardMainPassword).setOnClickListener {
                startActivity(Intent(this, PrincipalPasswordActivity::class.java))
            }
        }

        // ── Categorías ────────────────────────────────────────────
        findViewById<MaterialCardView>(R.id.cardCategories).setOnClickListener {
            startActivity(Intent(this, AdministrarCategoriasActivity::class.java))
        }

        // ── Biométrico ────────────────────────────────────────────
        if (!esInvitado) {
            val sw = findViewById<SwitchCompat>(R.id.switchBiometric)
            sw.setOnCheckedChangeListener { btn, isChecked ->
                if (!btn.isPressed) return@setOnCheckedChangeListener
                if (isChecked) mostrarDialogoBiometrico(sw)
                else {
                    securityPrefs.edit().putBoolean("biometric_enabled", false).apply()
                    mostrarBannerPersonalizado("Biometría desactivada", "Autenticación biométrica desactivada", R.drawable.logo)
                }
            }
        }

        // ── Exportar bóveda ───────────────────────────────────────
        // Requiere verificar clave maestra; luego navega a ExportVaultActivity.
        findViewById<MaterialCardView>(R.id.cardExport).setOnClickListener {
            solicitarClaveMaestra(
                subtitulo = "Para exportar debes confirmar\ntu contraseña maestra.",
                advertenciaInvitado = "Vas a exportar los datos guardados en este\ndispositivo. Estás en modo invitado.",
                onConfirmado = {
                    startActivity(Intent(this, ExportVaultActivity::class.java))
                }
            )
        }

        // ── Importar bóveda ───────────────────────────────────────
        // También requiere verificar clave maestra antes de continuar.
        findViewById<MaterialCardView>(R.id.cardImport).setOnClickListener {
            solicitarClaveMaestra(
                subtitulo = "Para importar debes confirmar\ntu contraseña maestra.",
                advertenciaInvitado = "Vas a importar datos en modo invitado.\nTus datos actuales pueden verse afectados.",
                onConfirmado = {
                    startActivity(Intent(this, ImportVaultActivity::class.java))
                }
            )
        }

        // ── Eliminar todos los datos ──────────────────────────────
        findViewById<MaterialCardView>(R.id.cardDeleteData).setOnClickListener {
            solicitarClaveMaestra(
                subtitulo = "Esta acción es irreversible. Confirma tu\ncontraseña maestra para continuar.",
                advertenciaInvitado = "Estás en modo invitado. Esta acción borrará\npermanentemente todos los datos del dispositivo.",
                onConfirmado = { mostrarDialogoBorrar() }
            )
        }

        // ── Cerrar sesión / Crear cuenta ──────────────────────────
        val btnLogout = findViewById<MaterialCardView>(R.id.btnLogout)
        if (esInvitado) encontrarTextView(btnLogout)?.text = getString(R.string.settings_guest_btn)

        btnLogout.setOnClickListener {
            if (esInvitado) { GuestUtils.mostrarDialogoCuenta(this); return@setOnClickListener }

            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logout, null)
            val dialog = AlertDialog.Builder(this).setView(dialogView).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialogView.findViewById<AppCompatButton>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
            dialogView.findViewById<AppCompatButton>(R.id.btnLogoutConfirm).setOnClickListener {
                prefs.edit().putBoolean("is_logged_in", false).apply()
                Firebase.auth.signOut()
                VaultRepository.credentialList.clear()
                getSharedPreferences("IronVaultPermanentCache", MODE_PRIVATE).edit().clear().apply()
                GuestUtils.marcarInvitado(this, false)
                SessionManager.limpiarSesion(this)
                startActivity(Intent(this, WelcomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            dialog.show()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  solicitarClaveMaestra()
    // ─────────────────────────────────────────────────────────────
    private fun solicitarClaveMaestra(
        subtitulo: String,
        advertenciaInvitado: String,
        onConfirmado: () -> Unit
    ) {
        val esInvitado = GuestUtils.esInvitado(this)

        if (esInvitado && !GuestUtils.tieneClave(this)) {
            mostrarAdvertenciaInvitado(advertenciaInvitado, onConfirmado)
            return
        }

        val dialog = BottomSheetDialog(this)
        val view   = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_master_key, null)
        dialog.setContentView(view)

        val etInput     = view.findViewById<EditText>(R.id.etMasterKeyInput)
        val ivToggle    = view.findViewById<ImageView>(R.id.ivToggleMasterKey)
        val tvError     = view.findViewById<TextView>(R.id.tvMasterKeyError)
        val tvSub       = view.findViewById<TextView>(R.id.tvConfirmSubtitle)
        val btnConfirm  = view.findViewById<AppCompatButton>(R.id.btnConfirmMasterKey)
        val btnCancel   = view.findViewById<MaterialCardView>(R.id.btnCancelMasterKey)

        tvSub.text = if (esInvitado) "Ingresa tu clave local para confirmar\nesta acción." else subtitulo

        var pwdVisible = false
        ivToggle.setOnClickListener {
            pwdVisible = !pwdVisible
            val pos = etInput.selectionStart
            etInput.transformationMethod = if (pwdVisible)
                HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
            ivToggle.setImageResource(if (pwdVisible) R.drawable.ojo else R.drawable.ojo_cerrado)
            etInput.setSelection(pos)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val clave = etInput.text.toString().trim()
            if (clave.isEmpty()) {
                tvError.text = "Ingresa tu contraseña maestra."
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            tvError.visibility = View.GONE

            if (esInvitado) {
                if (GuestUtils.verificarClave(this, clave)) { dialog.dismiss(); onConfirmado() }
                else { tvError.text = "Contraseña incorrecta."; tvError.visibility = View.VISIBLE }
                return@setOnClickListener
            }

            val user = Firebase.auth.currentUser
            if (user == null || user.email == null) {
                tvError.text = "Sesión expirada. Reinicia la aplicación."
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            btnConfirm.isEnabled = false
            btnConfirm.text = "Verificando..."

            user.reauthenticate(EmailAuthProvider.getCredential(user.email!!, clave))
                .addOnSuccessListener { dialog.dismiss(); onConfirmado() }
                .addOnFailureListener {
                    btnConfirm.isEnabled = true
                    btnConfirm.text = "Confirmar"
                    tvError.text = "Contraseña incorrecta."
                    tvError.visibility = View.VISIBLE
                }
        }
        dialog.show()
    }

    private fun mostrarAdvertenciaInvitado(mensaje: String, onConfirmado: () -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_action, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tvActionTitle)?.text  = "Modo invitado"
        dialogView.findViewById<TextView>(R.id.tvActionMessage)?.text = mensaje
        dialogView.findViewById<AppCompatButton>(R.id.btnActionConfirm)?.setOnClickListener { dialog.dismiss(); onConfirmado() }
        dialogView.findViewById<TextView>(R.id.btnActionCancel)?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ─────────────────────────────────────────────────────────────
    //  Biométrico
    // ─────────────────────────────────────────────────────────────
    private fun cargarEstadoBiometrico() {
        findViewById<SwitchCompat>(R.id.switchBiometric).isChecked =
            securityPrefs.getBoolean("biometric_enabled", false)
    }

    private fun mostrarDialogoBiometrico(sw: SwitchCompat) {
        val dialog = BottomSheetDialog(this)
        val view   = LayoutInflater.from(this).inflate(R.layout.dialog_biometric, null)
        dialog.setContentView(view)

        val tvSub = view.findViewById<TextView>(R.id.tvBiometricCountdown)
        val btnPwd = view.findViewById<MaterialCardView>(R.id.btnUseMasterPassword)

        countDownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(ms: Long) {
                val s = (ms / 1000).toInt()
                tvSub.text = "Este diálogo se cerrará en $s segundo${if (s != 1) "s" else ""}"
            }
            override fun onFinish() { tvSub.text = "Configurando..."; dialog.dismiss(); activarBiometria("Biometría activada") }
        }.start()

        btnPwd.setOnClickListener { countDownTimer?.cancel(); dialog.dismiss(); activarBiometria("Configurado con contraseña maestra") }
        dialog.setOnDismissListener { countDownTimer?.cancel() }
        dialog.show()
    }

    private fun activarBiometria(msg: String) {
        securityPrefs.edit().putBoolean("biometric_enabled", true).apply()
        mostrarBannerPersonalizado("Seguridad actualizada", msg, R.drawable.logo)
    }

    // ─────────────────────────────────────────────────────────────
    //  Eliminar datos
    // ─────────────────────────────────────────────────────────────
    private fun mostrarDialogoBorrar() {
        val dialog = BottomSheetDialog(this)
        val view   = LayoutInflater.from(this).inflate(R.layout.dialog_delete_data, null)
        dialog.setContentView(view)
        view.findViewById<AppCompatButton>(R.id.btnDeleteConfirm).setOnClickListener {
            securityPrefs.edit().clear().apply()
            prefs.edit().clear().apply()
            mostrarBannerPersonalizado("Datos eliminados", "Se ha borrado toda la información", R.drawable.logo)
            startActivity(Intent(this, WelcomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
        view.findViewById<MaterialCardView>(R.id.btnDeleteCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────
    private fun encontrarTextView(view: View): TextView? {
        if (view is TextView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            val f = encontrarTextView(view.getChildAt(i))
            if (f != null) return f
        }
        return null
    }

    private fun configurarNavegacionInferior() {
        val nav = listOf<Pair<Int, Class<*>>>(
            R.id.btnNavHome to MainActivity::class.java,
            R.id.btnNavLock to SecurityActivity::class.java,
            R.id.btnNavKey  to PasswordGeneratorActivity::class.java
        )
        nav.forEach { (id, cls) ->
            findViewById<LinearLayout>(id).setOnClickListener {
                startActivity(Intent(this, cls).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
                finish()
            }
        }
        findViewById<LinearLayout>(R.id.btnNavSettings)?.setOnClickListener { }
        findViewById<LinearLayout>(R.id.btnNavSettings)?.setBackgroundResource(R.drawable.bg_nav_pill_active)
        findViewById<ImageView>(R.id.ivNavSettings)?.setColorFilter(getColor(R.color.orange_primary))
    }

    private fun cargarDatosPerfil() {
        val tvNombre = findViewById<TextView>(R.id.tvUserName)
        val tvCorreo = findViewById<TextView>(R.id.tvUserEmail)

        if (GuestUtils.esInvitado(this)) {
            tvNombre.text = getString(R.string.settings_guest_name)
            tvCorreo.text = getString(R.string.settings_guest_email)
            return
        }

        val user = Firebase.auth.currentUser ?: return
        tvCorreo.text = user.email ?: ""
        FirebaseFirestore.getInstance().collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val n = doc.getString("fullName")?.trim()
                tvNombre.text = if (!n.isNullOrEmpty()) n else user.email ?: "Usuario"
            }
            .addOnFailureListener { tvNombre.text = user.email ?: "Usuario" }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
