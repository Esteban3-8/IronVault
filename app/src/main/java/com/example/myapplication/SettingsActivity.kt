package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView

// ─────────────────────────────────────────────────────────────────
//  SettingsActivity
//  Pantalla de configuración general: perfil, seguridad,
//  idioma, tema, notificaciones y cerrar sesión.
// ─────────────────────────────────────────────────────────────────
class SettingsActivity : AppCompatActivity() {

    // SharedPreferences para guardar las preferencias del usuario
    private val prefs by lazy {
        getSharedPreferences("settings_prefs", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        // ── WindowInsets ──────────────────────────────────────────
        // Ajustamos los insets para mantener la compatibilidad con gestos EdgeToEdge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        configurarBotones()
        configurarSwitches()
        configurarNavegacionInferior()
        cargarValoresGuardados()
    }

    // ─────────────────────────────────────────────────────────────
    //  configurarBotones()
    // ─────────────────────────────────────────────────────────────
    private fun configurarBotones() {

        // ── Editar perfil ─────────────────────────────────────────
        findViewById<AppCompatButton>(R.id.btnEditProfile).setOnClickListener {
            Toast.makeText(this, "Próximamente: editar perfil", Toast.LENGTH_SHORT).show()
        }

        // ── Seguridad y bóveda ────────────────────────────────────
        findViewById<MaterialCardView>(R.id.cardSecurity).setOnClickListener {
            startActivity(Intent(this, VaultSecurityActivity::class.java))
        }

        // ── Categorías ────────────────────────────────────────────
        findViewById<MaterialCardView>(R.id.cardCategories).setOnClickListener {
            startActivity(Intent(this, AdministrarCategoriasActivity::class.java))
        }

        // ── Idioma ────────────────────────────────────────────────
        findViewById<MaterialCardView>(R.id.cardLanguage).setOnClickListener {
            mostrarDialogoIdioma()
        }

        // ── Cerrar sesión ─────────────────────────────────────────
        findViewById<MaterialCardView>(R.id.btnLogout).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Estás seguro de que quieres cerrar sesión?")
                .setPositiveButton("Cerrar sesión") { _, _ ->
                    val intent = Intent(this, WelcomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  configurarNavegacionInferior()
    //  Controla los clics en la barra flotante inferior para cambiar de pantalla
    // ─────────────────────────────────────────────────────────────
    private fun configurarNavegacionInferior() {
        // Ir a Inicio (MainActivity)
        findViewById<LinearLayout>(R.id.btnNavHome).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // Ir a Seguridad (VaultSecurityActivity)
        findViewById<LinearLayout>(R.id.btnNavLock).setOnClickListener {
            val intent = Intent(this, VaultSecurityActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // Ir a Generador de Contraseñas (PasswordGeneratorActivity)
        findViewById<LinearLayout>(R.id.btnNavKey).setOnClickListener {
            val intent = Intent(this, PasswordGeneratorActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // (Opcional por si se pulsa estando en ajustes)
        findViewById<LinearLayout>(R.id.btnNavSettings)?.setOnClickListener {
            // Ya estás en configuración
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  configurarSwitches()
    // ─────────────────────────────────────────────────────────────
    private fun configurarSwitches() {
        findViewById<SwitchCompat>(R.id.switchTheme).setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_theme", isChecked).apply()
            val mensaje = if (isChecked) "Tema oscuro activado" else "Tema oscuro desactivado"
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        }

        findViewById<SwitchCompat>(R.id.switchNotifications).setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
            val mensaje = if (isChecked) "Notificaciones activadas" else "Notificaciones desactivadas"
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  cargarValoresGuardados()
    // ─────────────────────────────────────────────────────────────
    private fun cargarValoresGuardados() {
        findViewById<SwitchCompat>(R.id.switchTheme).isChecked =
            prefs.getBoolean("dark_theme", true)

        findViewById<SwitchCompat>(R.id.switchNotifications).isChecked =
            prefs.getBoolean("notifications", false)

        val idiomaGuardado = prefs.getString("language", "Español") ?: "Español"
        findViewById<TextView>(R.id.tvLanguageValue).text = idiomaGuardado
    }

    // ─────────────────────────────────────────────────────────────
    //  mostrarDialogoIdioma()
    // ─────────────────────────────────────────────────────────────
    private fun mostrarDialogoIdioma() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_language, null)
        dialog.setContentView(view)

        val radioGroup = view.findViewById<RadioGroup>(R.id.rgLanguage)
        val btnConfirmar = view.findViewById<AppCompatButton>(R.id.btnLanguageConfirm)

        val idiomaGuardado = prefs.getString("language", "Español") ?: "Español"
        val idSeleccionado = when (idiomaGuardado) {
            "English"   -> R.id.rbEnglish
            "Português" -> R.id.rbPortuguese
            "Français"  -> R.id.rbFrench
            "Deutsch"   -> R.id.rbGerman
            "Italiano"  -> R.id.rbItalian
            else        -> R.id.rbSpanish
        }
        radioGroup.check(idSeleccionado)

        btnConfirmar.setOnClickListener {
            val idiomaElegido = when (radioGroup.checkedRadioButtonId) {
                R.id.rbEnglish    -> "English"
                R.id.rbPortuguese -> "Português"
                R.id.rbFrench     -> "Français"
                R.id.rbGerman     -> "Deutsch"
                R.id.rbItalian    -> "Italiano"
                else              -> "Español"
            }

            prefs.edit().putString("language", idiomaElegido).apply()
            findViewById<TextView>(R.id.tvLanguageValue).text = idiomaElegido

            Toast.makeText(this, "Idioma: $idiomaElegido", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
}