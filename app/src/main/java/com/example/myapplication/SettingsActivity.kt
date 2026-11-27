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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
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
        if (!ensureVaultAccess()) return
        setContentView(R.layout.activity_settings)

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
                startActivity(Intent(this, ChangeMasterKeyActivity::class.java))
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
                btn.isPressed = false
                if (isChecked) mostrarDialogoBiometrico(sw)
                else {
                    sw.isChecked = true
                    confirmMaster("biometric-disable") {
                        VaultService.consume("biometric-disable")
                        VaultBiometrics.revoke(this, VaultService.uid); sw.isChecked = false
                        mostrarBannerPersonalizado("Biometría desactivada", "Acceso biométrico eliminado de este dispositivo", R.drawable.logo)
                    }

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
                VaultService.logout()
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
        val action = when { subtitulo.contains("exportar") -> "export"; subtitulo.contains("importar") -> "import"; else -> "delete" }
        if (GuestUtils.esInvitado(this)) {
            if (!GuestUtils.tieneClave(this)) { mostrarAdvertenciaInvitado(advertenciaInvitado, onConfirmado); return }
            val input = EditText(this).apply { inputType = 129; isSaveEnabled = false }
            AlertDialog.Builder(this).setTitle("Clave local").setView(input).setNegativeButton("Cancelar", null)
                .setPositiveButton("Confirmar") { _, _ ->
                    val password = input.text.toString(); input.text.clear()
                    if (GuestUtils.verificarClave(this, password)) onConfirmado()
                }.show()
            return
        }
        confirmMaster(action, onConfirmado)
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
            Firebase.auth.currentUser?.uid?.let { VaultBiometrics.enabled(this, it) } ?: false
    }

    private fun mostrarDialogoBiometrico(sw: SwitchCompat) {
        sw.isChecked = false
        val input = EditText(this).apply { inputType = 129; isSaveEnabled = false }
        AlertDialog.Builder(this).setTitle("Activar biometría")
            .setMessage("Confirma la contraseña maestra. El sistema pedirá autenticación y una comprobación final.")
            .setView(input).setNegativeButton("Cancelar", null).setPositiveButton("Continuar") { _, _ ->
                val chars = input.text.toString().toCharArray(); input.text.clear()
                vaultJob {
                    try {
                        val key = VaultService.verify(chars)
                        try { VaultBiometrics.enroll(this@SettingsActivity, key,
                            { sw.isChecked = true; mostrarBannerPersonalizado("Biometría activada", "Configuración verificada", R.drawable.logo) },
                            { sw.isChecked = false; mostrarBannerPersonalizado("Biometría", it, R.drawable.logo) }) }
                        finally { key.fill(0) }
                    } finally { chars.fill('\u0000') }
                }
            }.show()
    }

    private fun activarBiometria(msg: String) {
        // State is derived from the verified device envelope.
    }

    // ─────────────────────────────────────────────────────────────
    //  Eliminar datos
    // ─────────────────────────────────────────────────────────────
    private fun mostrarDialogoBorrar() {
        AlertDialog.Builder(this).setTitle("Eliminar todas las credenciales")
            .setMessage("Esta operación es irreversible. La contraseña maestra no se modifica.")
            .setNegativeButton("Cancelar", null).setPositiveButton("Eliminar") { _, _ ->
                if (GuestUtils.esInvitado(this)) {
                    VaultRepository.credentialList.clear(); GuestStorage.guardar(this, emptyList())
                } else vaultJob {
                    VaultService.replace(emptyList(), "delete")
                    mostrarBannerPersonalizado("Datos eliminados", "La bóveda cifrada está vacía", R.drawable.logo)
                }
            }.show()
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
        bindBottomNavigation(NavTab.SETTINGS)
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
