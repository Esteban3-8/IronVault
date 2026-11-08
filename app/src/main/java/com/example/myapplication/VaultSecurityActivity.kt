package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView

// ─────────────────────────────────────────────────────────────────
//  VaultSecurityActivity
// ─────────────────────────────────────────────────────────────────
class VaultSecurityActivity : BaseActivity() { // 👈 Hereda de BaseActivity

    private val prefs by lazy {
        getSharedPreferences("security_prefs", MODE_PRIVATE)
    }

    // Guardamos referencia al CountDownTimer para poder cancelarlo
    // si el usuario toca "Usar contraseña maestra" antes de que termine.
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_vault_security)

        val dp20 = (20 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(dp20, systemBars.top, dp20, systemBars.bottom)
            insets
        }

        configurarBotones()
        cargarEstadoBiometrico()
    }

    private fun configurarBotones() {

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // ── Contraseña principal ──────────────────────────────────
        findViewById<MaterialCardView>(R.id.cardMainPassword).setOnClickListener {
            startActivity(Intent(this, PrincipalPasswordActivity::class.java))
        }

        // ── Switch Biométrico ─────────────────────────────────────
        val switchBiometric = findViewById<SwitchCompat>(R.id.switchBiometric)

        switchBiometric.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) return@setOnCheckedChangeListener

            if (isChecked) {
                // Usuario ACTIVÓ el switch → mostrar diálogo biométrico
                mostrarDialogoBiometrico(switchBiometric)
            } else {
                // Usuario DESACTIVÓ el switch → solo guardar y notificar con banner superior
                prefs.edit().putBoolean("biometric_enabled", false).apply()
                mostrarBannerPersonalizado(
                    titulo = "Biometría desactivada",
                    mensaje = "Autenticación biométrica desactivada",
                    iconoResId = R.drawable.logo
                )
            }
        }

        // ── Exportar datos ────────────────────────────────────────
        findViewById<MaterialCardView>(R.id.cardExport).setOnClickListener {
            mostrarDialogoExportar()
        }

        // ── Borrado de datos ──────────────────────────────────────
        findViewById<MaterialCardView>(R.id.cardDeleteData).setOnClickListener {
            mostrarDialogoBorrar()
        }
    }

    private fun cargarEstadoBiometrico() {
        val biometricActivado = prefs.getBoolean("biometric_enabled", false)
        findViewById<SwitchCompat>(R.id.switchBiometric).isChecked = biometricActivado
    }

    private fun mostrarDialogoBiometrico(switchBiometric: SwitchCompat) {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_biometric, null)
        dialog.setContentView(view)

        val btnUseMasterPassword = view.findViewById<MaterialCardView>(R.id.btnUseMasterPassword)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvBiometricCountdown)

        // ── Iniciar cuenta regresiva de 5 segundos ────────────────
        countDownTimer = object : CountDownTimer(5000, 1000) {

            override fun onTick(millisUntilFinished: Long) {
                val segundos = (millisUntilFinished / 1000).toInt()
                tvSubtitle.text = "Este diálogo se cerrará en $segundos segundo${if (segundos != 1) "s" else ""}"
            }

            override fun onFinish() {
                tvSubtitle.text = "Configurando..."
                dialog.dismiss()
                activarBiometria("Autenticación biométrica activada")
            }
        }.start()

        btnUseMasterPassword.setOnClickListener {
            countDownTimer?.cancel()
            dialog.dismiss()
            activarBiometria("Configurado con contraseña maestra")
        }

        dialog.setOnDismissListener {
            countDownTimer?.cancel()
        }

        dialog.show()
    }

    private fun activarBiometria(mensaje: String) {
        prefs.edit().putBoolean("biometric_enabled", true).apply()
        mostrarBannerPersonalizado(
            titulo = "Seguridad actualizada",
            mensaje = mensaje,
            iconoResId = R.drawable.logo
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    private fun mostrarDialogoExportar() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_export_data, null)
        dialog.setContentView(view)

        view.findViewById<AppCompatButton>(R.id.btnExportConfirm).setOnClickListener {
            mostrarBannerPersonalizado(
                titulo = "Exportación",
                mensaje = "Exportando datos... (próximamente)",
                iconoResId = R.drawable.logo
            )
            dialog.dismiss()
        }
        view.findViewById<MaterialCardView>(R.id.btnExportCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun mostrarDialogoBorrar() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_delete_data, null)
        dialog.setContentView(view)

        view.findViewById<AppCompatButton>(R.id.btnDeleteConfirm).setOnClickListener {
            getSharedPreferences("security_prefs", MODE_PRIVATE).edit().clear().apply()
            getSharedPreferences("settings_prefs", MODE_PRIVATE).edit().clear().apply()

            mostrarBannerPersonalizado(
                titulo = "Datos eliminados",
                mensaje = "Se ha borrado toda la información",
                iconoResId = R.drawable.logo
            )

            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        view.findViewById<MaterialCardView>(R.id.btnDeleteCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }
}