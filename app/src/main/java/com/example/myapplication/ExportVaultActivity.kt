package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────────────────────────
//  ExportVaultActivity
//
//  Flujo:
//  1. El usuario ya se autenticó (contraseña maestra verificada en
//     SettingsActivity antes de lanzar esta Activity).
//  2. El usuario introduce una contraseña de respaldo independiente.
//  3. Se cifra en segundo plano con VaultCryptoService (PBKDF2 + AES-GCM).
//  4. El archivo .vault se escribe en el caché privado de la app.
//  5. Se comparte con el sistema operativo vía FileProvider.
//
//  FLAG_SECURE impide capturas de pantalla en esta pantalla sensible.
// ─────────────────────────────────────────────────────────────────
class ExportVaultActivity : BaseActivity() {

    private var backupVisible = false
    private var confirmVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return
        // Bloquear capturas de pantalla
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
        setContentView(R.layout.activity_export_vault)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, sys.top, 0, 0)
            insets
        }

        configurarUI()
    }

    private fun configurarUI() {
        val etPassword  = findViewById<EditText>(R.id.etBackupPassword)
        val etConfirm   = findViewById<EditText>(R.id.etBackupConfirm)
        val ivToggle    = findViewById<ImageView>(R.id.ivToggleBackup)
        val ivToggleC   = findViewById<ImageView>(R.id.ivToggleConfirm)
        val tvError     = findViewById<TextView>(R.id.tvPasswordError)
        val barraFill   = findViewById<View>(R.id.viewStrengthFill)
        val tvStrength  = findViewById<TextView>(R.id.tvStrengthLabel)
        val btnExport   = findViewById<AppCompatButton>(R.id.btnExport)
        val progress    = findViewById<ProgressBar>(R.id.progressExport)

        // ── Volver ────────────────────────────────────────────────
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // ── Toggles de visibilidad ────────────────────────────────
        ivToggle.setOnClickListener {
            backupVisible = !backupVisible
            val pos = etPassword.selectionStart
            etPassword.transformationMethod = if (backupVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            ivToggle.setImageResource(if (backupVisible) R.drawable.ojo else R.drawable.ojo_cerrado)
            etPassword.setSelection(pos)
        }

        ivToggleC.setOnClickListener {
            confirmVisible = !confirmVisible
            val pos = etConfirm.selectionStart
            etConfirm.transformationMethod = if (confirmVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            ivToggleC.setImageResource(if (confirmVisible) R.drawable.ojo else R.drawable.ojo_cerrado)
            etConfirm.setSelection(pos)
        }

        // ── Indicador de fortaleza ────────────────────────────────
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                actualizarBarra(barraFill, tvStrength, evaluarFortaleza(s.toString()))
                tvError.visibility = View.GONE
            }
        })

        etConfirm.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { tvError.visibility = View.GONE }
        })

        // ── Exportar ──────────────────────────────────────────────
        btnExport.setOnClickListener {
            val password = etPassword.text.toString()
            val confirm  = etConfirm.text.toString()

            // Validaciones
            if (password.isEmpty()) {
                mostrarBannerPersonalizado("Campo requerido", "Ingresa la contraseña del respaldo.", R.drawable.logo)
                return@setOnClickListener
            }
            if (password.length !in 15..4096) {
                mostrarBannerPersonalizado("Contraseña débil", "Usa una frase de al menos 15 caracteres.", R.drawable.logo)
                return@setOnClickListener
            }
            if (password != confirm) {
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            if (!GuestUtils.esInvitado(this)) {
                try { VaultService.consume("export") } catch (_: Exception) {
                    confirmMaster("export") { btnExport.performClick() }; return@setOnClickListener
                }
            }
            val epoch = VaultService.epoch()
            val credenciales = VaultRepository.credentialList.toList()

            // Deshabilitar UI durante el proceso
            btnExport.isEnabled = false
            btnExport.alpha = 0.5f
            progress.visibility = View.VISIBLE

            // Cifrado en IO thread (PBKDF2 es intensivo)
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    if (!GuestUtils.esInvitado(this@ExportVaultActivity)) VaultService.checkBackupPassword(password)
                    val bytes    = VaultBackupService.exportToBytes(credenciales, password)
                    val fileName = VaultBackupService.generateFileName()

                    if (!GuestUtils.esInvitado(this@ExportVaultActivity)) check(VaultService.unlocked && VaultService.epoch() == epoch)
                    // Escribir en caché privada (no requiere permisos de almacenamiento)
                    val cacheDir = File(cacheDir, "vault_exports").also { it.mkdirs() }
                    val file     = File(cacheDir, fileName)
                    file.writeBytes(bytes)

                    // Obtener URI mediante FileProvider
                    val uri = FileProvider.getUriForFile(
                        this@ExportVaultActivity,
                        "${packageName}.fileprovider",
                        file
                    )

                    withContext(Dispatchers.Main) {
                        progress.visibility = View.GONE
                        btnExport.isEnabled = true
                        btnExport.alpha = 1f

                        // Limpiar campos inmediatamente después de usar la contraseña
                        etPassword.text.clear()
                        etConfirm.text.clear()

                        if (!GuestUtils.esInvitado(this@ExportVaultActivity)) check(VaultService.unlocked && VaultService.epoch() == epoch)
                        // Compartir con el sistema operativo
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, fileName)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Guardar respaldo .vault"))

                        mostrarBannerPersonalizado(
                            titulo = "Exportación lista",
                            mensaje = "Archivo $fileName generado con éxito.",
                            iconoResId = R.drawable.logo
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progress.visibility = View.GONE
                        btnExport.isEnabled = true
                        btnExport.alpha = 1f

                        val msg = when (e) {
                            is VaultCryptoException -> "Error de cifrado. Intenta de nuevo."
                            else -> "No se pudo crear el respaldo."
                        }
                        mostrarBannerPersonalizado("Error", msg, R.drawable.logo)
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Fortaleza de contraseña
    // ─────────────────────────────────────────────────────────────
    private fun evaluarFortaleza(p: String): Int {
        if (p.isEmpty()) return 0
        val letras   = Regex("[a-zA-Z]").containsMatchIn(p)
        val numeros  = Regex("[0-9]").containsMatchIn(p)
        val simbolos = Regex("[^a-zA-Z0-9]").containsMatchIn(p)
        return when {
            p.length >= 8 && letras && numeros && simbolos -> 3
            p.length >= 6 && (letras || numeros)           -> 2
            else                                           -> 1
        }
    }

    private fun actualizarBarra(barra: View, label: TextView, nivel: Int) {
        val params = barra.layoutParams as android.widget.LinearLayout.LayoutParams
        when (nivel) {
            0 -> { params.weight = 0f;  label.text = ""; }
            1 -> { params.weight = 3f;  label.text = "Fortaleza Baja";  label.setTextColor(ContextCompat.getColor(this, R.color.history_red)) }
            2 -> { params.weight = 6f;  label.text = "Fortaleza Media"; label.setTextColor(0xFFFFC107.toInt()) }
            3 -> { params.weight = 10f; label.text = "Fortaleza Alta";  label.setTextColor(ContextCompat.getColor(this, R.color.green_accent)) }
        }
        barra.layoutParams = params
        barra.requestLayout()
    }
}
