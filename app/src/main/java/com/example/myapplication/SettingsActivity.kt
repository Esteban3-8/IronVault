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
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

// ─────────────────────────────────────────────────────────────────
//  SettingsActivity
//  Pantalla de configuración general: perfil, seguridad,
//  idioma, tema, notificaciones y cerrar sesión.
// ─────────────────────────────────────────────────────────────────
class SettingsActivity : BaseActivity() { // 👈 Hereda de BaseActivity

    // Bandera para evitar que los switches disparen eventos al cargar valores guardados
    private var isInitialized = false

    // SharedPreferences para guardar las preferencias del usuario
    private val prefs by lazy {
        getSharedPreferences("settings_prefs", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        // ── WindowInsets ──────────────────────────────────────────
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        configurarBotones()
        cargarValoresGuardados() // Cargamos los valores PRIMERO antes de activar los listeners
        configurarSwitches()
        configurarNavegacionInferior()

        isInitialized = true // Marcamos que la actividad ya cargó oficialmente
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

        // ── Cerrar sesión con diseño oscuro personalizado ─────────
        findViewById<MaterialCardView>(R.id.btnLogout).setOnClickListener {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logout, null)

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .create()

            // Removemos el fondo blanco por defecto del sistema
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            // Botón Cancelar
            dialogView.findViewById<AppCompatButton>(R.id.btnCancel).setOnClickListener {
                dialog.dismiss()
            }

            // Botón Confirmar Cierre de Sesión
            dialogView.findViewById<AppCompatButton>(R.id.btnLogoutConfirm).setOnClickListener {
                // 1. Desactivamos la bandera local de sesión
                prefs.edit().putBoolean("is_logged_in", false).apply()

                // 2. ¡Cerramos sesión oficialmente en Firebase Auth para limpiar el token!
                Firebase.auth.signOut()

                // 3. Redirigimos a WelcomeActivity limpiando todo el historial de pantallas
                val intent = Intent(this, WelcomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }

            dialog.show()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  configurarNavegacionInferior()
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

        // Botón de Ajustes en la barra inferior (Ya estás aquí)
        findViewById<LinearLayout>(R.id.btnNavSettings)?.setOnClickListener {
            // Sin acciones
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  configurarSwitches()
    // ─────────────────────────────────────────────────────────────
    private fun configurarSwitches() {
        findViewById<SwitchCompat>(R.id.switchTheme).setOnCheckedChangeListener { _, isChecked ->
            if (!isInitialized) return@setOnCheckedChangeListener // Ignora el cambio al iniciar la app

            prefs.edit().putBoolean("dark_theme", isChecked).apply()
            val mensaje = if (isChecked) "Tema oscuro activado" else "Tema oscuro desactivado"
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        }

        findViewById<SwitchCompat>(R.id.switchNotifications).setOnCheckedChangeListener { _, isChecked ->
            if (!isInitialized) return@setOnCheckedChangeListener // Ignora el cambio al iniciar la app

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