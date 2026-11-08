package com.example.myapplication

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// ─────────────────────────────────────────────────────────────────
//  ChangeMasterKeyActivity
//  Permite al usuario cambiar su clave maestra. Requiere confirmar
//  la clave actual antes de aceptar la nueva.
// ─────────────────────────────────────────────────────────────────
class ChangeMasterKeyActivity : BaseActivity() { // 👈 Hereda de BaseActivity para el control de inactividad

    // ── SharedPreferences ────────────────────────────────────────
    private val prefs by lazy {
        getSharedPreferences("security_prefs", MODE_PRIVATE)
    }

    // ── Variables de estado para los ojos ────────────────────────
    private var isCurrentVisible = false
    private var isNewVisible     = false
    private var isConfirmVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_change_master_key)

        val dp24 = (24 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(dp24, systemBars.top, dp24, systemBars.bottom)
            insets
        }

        configurarBotones()
        configurarOjos()
        configurarBarraFortaleza()
    }

    private fun configurarBotones() {

        // ── Botón ATRÁS ──────────────────────────────────────────
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // ── Botón GUARDAR NUEVA CLAVE ────────────────────────────
        val etCurrent = findViewById<EditText>(R.id.etCurrentPassword)
        val etNew     = findViewById<EditText>(R.id.etNewPassword)
        val etConfirm = findViewById<EditText>(R.id.etConfirmPassword)

        findViewById<AppCompatButton>(R.id.btnSaveNewKey).setOnClickListener {

            val current = etCurrent.text.toString().trim()
            val nueva   = etNew.text.toString().trim()
            val confirm = etConfirm.text.toString().trim()

            // ── Validación 1: campos vacíos ──────────────────────
            if (current.isEmpty() || nueva.isEmpty() || confirm.isEmpty()) {
                mostrarBannerPersonalizado(
                    titulo = "Campos requeridos",
                    mensaje = "Por favor completa todos los campos",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }

            // ── Validación 2: contraseña actual correcta ─────────
            val claveGuardada = prefs.getString("master_key", "") ?: ""

            if (claveGuardada.isNotEmpty() && current != claveGuardada) {
                mostrarBannerPersonalizado(
                    titulo = "Contraseña incorrecta",
                    mensaje = "La contraseña actual no es válida",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }

            // ── Validación 3: nueva y confirmación coinciden ──────
            if (nueva != confirm) {
                mostrarBannerPersonalizado(
                    titulo = "Error de coincidencia",
                    mensaje = "Las contraseñas nuevas no coinciden",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }

            // ── Validación 4: mínimo de seguridad ─────────────────
            if (nueva.length < 8) {
                mostrarBannerPersonalizado(
                    titulo = "Contraseña débil",
                    mensaje = "La clave debe tener al menos 8 caracteres",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }

            // ── Todo OK: guardar la nueva clave ───────────────────
            prefs.edit().putString("master_key", nueva).apply()

            mostrarBannerPersonalizado(
                titulo = "¡Clave actualizada!",
                mensaje = "Clave actualizada correctamente",
                iconoResId = R.drawable.logo
            )

            finish()
        }
    }

    private fun configurarOjos() {

        val etCurrent  = findViewById<EditText>(R.id.etCurrentPassword)
        val eyeCurrent = findViewById<ImageView>(R.id.ivToggleCurrentPassword)

        val etNew  = findViewById<EditText>(R.id.etNewPassword)
        val eyeNew = findViewById<ImageView>(R.id.ivToggleNewPassword)

        val etConfirm  = findViewById<EditText>(R.id.etConfirmPassword)
        val eyeConfirm = findViewById<ImageView>(R.id.ivToggleConfirmPassword)

        eyeCurrent.setOnClickListener {
            isCurrentVisible = !isCurrentVisible
            toggleVisibility(etCurrent, isCurrentVisible)
        }

        eyeNew.setOnClickListener {
            isNewVisible = !isNewVisible
            toggleVisibility(etNew, isNewVisible)
        }

        eyeConfirm.setOnClickListener {
            isConfirmVisible = !isConfirmVisible
            toggleVisibility(etConfirm, isConfirmVisible)
        }
    }

    private fun toggleVisibility(campo: EditText, mostrar: Boolean) {
        if (mostrar) {
            campo.transformationMethod = HideReturnsTransformationMethod.getInstance()
        } else {
            campo.transformationMethod = PasswordTransformationMethod.getInstance()
        }
        campo.setSelection(campo.text.length)
    }

    private fun configurarBarraFortaleza() {

        val etNew = findViewById<EditText>(R.id.etNewPassword)

        val layout = findViewById<android.widget.LinearLayout>(R.id.layoutStrengthBars)
        val barra1 = layout.getChildAt(0)
        val barra2 = layout.getChildAt(1)
        val barra3 = layout.getChildAt(2)

        etNew.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val texto = s.toString()
                val nivel = evaluarFortaleza(texto)
                pintarBarras(barra1, barra2, barra3, nivel)
            }
        })
    }

    private fun evaluarFortaleza(password: String): Int {
        if (password.isEmpty()) return 0

        val tieneLetras   = Regex("[a-zA-Z]").containsMatchIn(password)
        val tieneNumeros  = Regex("[0-9]").containsMatchIn(password)
        val tieneSimbolos = Regex("[^a-zA-Z0-9]").containsMatchIn(password)

        return when {
            password.length >= 8 && tieneLetras && tieneNumeros && tieneSimbolos -> 3
            password.length >= 6 && (tieneLetras || tieneNumeros)                -> 2
            else                                                                 -> 1
        }
    }

    private fun pintarBarras(b1: View, b2: View, b3: View, nivel: Int) {
        val gris     = ContextCompat.getColor(this, R.color.border_option_card)
        val rojo     = ContextCompat.getColor(this, R.color.history_red)
        val amarillo = 0xFFFFC107.toInt()
        val verde    = ContextCompat.getColor(this, R.color.green_accent)

        when (nivel) {
            0 -> { b1.setBackgroundColor(gris);     b2.setBackgroundColor(gris);     b3.setBackgroundColor(gris)  }
            1 -> { b1.setBackgroundColor(rojo);     b2.setBackgroundColor(gris);     b3.setBackgroundColor(gris)  }
            2 -> { b1.setBackgroundColor(amarillo); b2.setBackgroundColor(amarillo); b3.setBackgroundColor(gris) }
            3 -> { b1.setBackgroundColor(verde);    b2.setBackgroundColor(verde);    b3.setBackgroundColor(verde) }
        }
    }
}