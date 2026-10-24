package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
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
        // El layout tiene paddingStart/End="20dp". Aplicamos el mismo
        // patrón: convertir dp a px para no perder el padding del XML.
        val dp20 = (20 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(dp20, systemBars.top, dp20, systemBars.bottom)
            insets
        }

        configurarBotones()
        configurarSwitches()
        cargarValoresGuardados()
    }

    // ─────────────────────────────────────────────────────────────
    //  configurarBotones()
    // ─────────────────────────────────────────────────────────────
    private fun configurarBotones() {

        // ── Atrás ─────────────────────────────────────────────────
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // ── Editar perfil ─────────────────────────────────────────
        // La pantalla de edición de perfil no existe aún.
        // Un Toast es la forma más sencilla de dar feedback al usuario
        // cuando una función todavía no está implementada.
        findViewById<AppCompatButton>(R.id.btnEditProfile).setOnClickListener {
            Toast.makeText(this, "Próximamente: editar perfil", Toast.LENGTH_SHORT).show()
        }

        // ── Seguridad y bóveda ────────────────────────────────────
        // Navega a VaultSecurityActivity: contraseña principal,
        // biometría, exportar y borrado de datos.
        findViewById<MaterialCardView>(R.id.cardSecurity).setOnClickListener {
            startActivity(Intent(this, VaultSecurityActivity::class.java))
        }

        // ── Categorías ────────────────────────────────────────────
        // Navega a AdministrarCategoriasActivity (pantalla de Juan David).
        findViewById<MaterialCardView>(R.id.cardCategories).setOnClickListener {
            startActivity(Intent(this, AdministrarCategoriasActivity::class.java))
        }

        // ── Idioma ────────────────────────────────────────────────
        // Abre el diálogo de selección de idioma.
        findViewById<MaterialCardView>(R.id.cardLanguage).setOnClickListener {
            mostrarDialogoIdioma()
        }

        // ── Cerrar sesión ─────────────────────────────────────────
        // Acción destructiva → siempre pedimos confirmación.
        // Al confirmar, vamos a WelcomeActivity y limpiamos todo el
        // historial de pantallas con FLAG_ACTIVITY_NEW_TASK y
        // FLAG_ACTIVITY_CLEAR_TASK para que el usuario no pueda
        // volver atrás con el botón de retroceso.
        findViewById<MaterialCardView>(R.id.btnLogout).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Estás seguro de que quieres cerrar sesión?")
                .setPositiveButton("Cerrar sesión") { _, _ ->
                    val intent = Intent(this, WelcomeActivity::class.java)

                    // FLAGS de Intent:
                    // NEW_TASK      → inicia una nueva tarea (pila de pantallas)
                    // CLEAR_TASK    → borra todas las pantallas anteriores
                    // Combinados: el usuario llega a WelcomeActivity sin poder volver atrás
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  configurarSwitches()
    //  Los Switch tienen un listener especial: setOnCheckedChangeListener
    //  que se activa cada vez que el usuario los activa o desactiva.
    //
    //  CONCEPTO: setOnCheckedChangeListener
    //  Recibe dos parámetros en la lambda:
    //    · buttonView → referencia al Switch (raramente necesario)
    //    · isChecked  → Boolean: true = activado, false = desactivado
    // ─────────────────────────────────────────────────────────────
    private fun configurarSwitches() {

        // ── Switch TEMA OSCURO ────────────────────────────────────
        // La app siempre es oscura por diseño, pero guardamos la
        // preferencia de todas formas para cuando se implemente.
        findViewById<SwitchCompat>(R.id.switchTheme).setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_theme", isChecked).apply()
            val mensaje = if (isChecked) "Tema oscuro activado" else "Tema oscuro desactivado"
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        }

        // ── Switch NOTIFICACIONES ─────────────────────────────────
        findViewById<SwitchCompat>(R.id.switchNotifications).setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
            val mensaje = if (isChecked) "Notificaciones activadas" else "Notificaciones desactivadas"
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  cargarValoresGuardados()
    //  Al abrir la pantalla, restauramos los valores guardados
    //  para que los switches y el chip de idioma reflejen la
    //  configuración actual del usuario.
    //
    //  getBoolean("clave", valorPorDefecto) → lee un Boolean guardado.
    // ─────────────────────────────────────────────────────────────
    private fun cargarValoresGuardados() {
        // Switches: restaurar estado guardado
        // El tema oscuro viene activado por defecto (true)
        // Las notificaciones vienen desactivadas por defecto (false)
        findViewById<SwitchCompat>(R.id.switchTheme).isChecked =
            prefs.getBoolean("dark_theme", true)

        findViewById<SwitchCompat>(R.id.switchNotifications).isChecked =
            prefs.getBoolean("notifications", false)

        // Chip de idioma: mostrar el idioma guardado
        val idiomaGuardado = prefs.getString("language", "Español") ?: "Español"
        findViewById<TextView>(R.id.tvLanguageValue).text = idiomaGuardado
    }

    // ─────────────────────────────────────────────────────────────
    //  mostrarDialogoIdioma()
    //  Abre el dialog_language como BottomSheetDialog.
    //
    //  CONCEPTO: RadioGroup
    //  Un RadioGroup agrupa RadioButtons de forma que solo uno puede
    //  estar seleccionado al mismo tiempo.
    //  · checkedRadioButtonId → ID del RadioButton actualmente marcado
    //  · check(id) → marca un RadioButton por su ID
    //  · setOnCheckedChangeListener → se activa al cambiar la selección
    //    Parámetros: group (el RadioGroup) y checkedId (ID del seleccionado)
    // ─────────────────────────────────────────────────────────────
    private fun mostrarDialogoIdioma() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_language, null)
        dialog.setContentView(view)

        val radioGroup = view.findViewById<RadioGroup>(R.id.rgLanguage)
        val btnConfirmar = view.findViewById<AppCompatButton>(R.id.btnLanguageConfirm)

        // Restaurar la selección guardada: buscamos el RadioButton cuyo
        // texto coincida con el idioma guardado y lo marcamos.
        val idiomaGuardado = prefs.getString("language", "Español") ?: "Español"
        val idSeleccionado = when (idiomaGuardado) {
            "English"   -> R.id.rbEnglish
            "Português" -> R.id.rbPortuguese
            "Français"  -> R.id.rbFrench
            "Deutsch"   -> R.id.rbGerman
            "Italiano"  -> R.id.rbItalian
            else        -> R.id.rbSpanish  // "Español" por defecto
        }
        radioGroup.check(idSeleccionado)

        // Al pulsar "Seleccionar": leemos qué RadioButton está marcado,
        // guardamos el idioma y actualizamos el chip en la pantalla.
        btnConfirmar.setOnClickListener {
            // checkedRadioButtonId devuelve el ID del botón seleccionado
            val idiomaElegido = when (radioGroup.checkedRadioButtonId) {
                R.id.rbEnglish    -> "English"
                R.id.rbPortuguese -> "Português"
                R.id.rbFrench     -> "Français"
                R.id.rbGerman     -> "Deutsch"
                R.id.rbItalian    -> "Italiano"
                else              -> "Español"
            }

            // Guardar en SharedPreferences
            prefs.edit().putString("language", idiomaElegido).apply()

            // Actualizar el chip naranja en la pantalla principal
            findViewById<TextView>(R.id.tvLanguageValue).text = idiomaElegido

            Toast.makeText(this, "Idioma: $idiomaElegido", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
}
