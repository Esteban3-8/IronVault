package com.example.myapplication

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// ─────────────────────────────────────────────────────────────────
//  PrincipalPasswordActivity
//  Permite cambiar la contraseña principal de la bóveda.
//  Similar a ChangeMasterKeyActivity pero con una barra de fortaleza
//  diferente: un solo View que crece/decrece con layout_weight.
// ─────────────────────────────────────────────────────────────────
class PrincipalPasswordActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("security_prefs", MODE_PRIVATE)
    }

    private var isCurrentVisible = false
    private var isNewVisible     = false
    private var isConfirmVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_principal_password)

        // paddingStart/End="24dp" en el XML
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

        // ── Atrás ─────────────────────────────────────────────────
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // ── Cambiar contraseña principal ──────────────────────────
        val etCurrent = findViewById<EditText>(R.id.etCurrentPassword)
        val etNew     = findViewById<EditText>(R.id.etNewPassword)
        val etConfirm = findViewById<EditText>(R.id.etConfirmPassword)

        findViewById<AppCompatButton>(R.id.btnChangePrincipal).setOnClickListener {
            val current = etCurrent.text.toString().trim()
            val nueva   = etNew.text.toString().trim()
            val confirm = etConfirm.text.toString().trim()

            if (current.isEmpty() || nueva.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val claveGuardada = prefs.getString("master_key", "") ?: ""
            if (claveGuardada.isNotEmpty() && current != claveGuardada) {
                Toast.makeText(this, "La contraseña actual es incorrecta", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (nueva != confirm) {
                Toast.makeText(this, "Las contraseñas nuevas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (nueva.length < 8) {
                Toast.makeText(this, "La clave debe tener al menos 8 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().putString("master_key", nueva).apply()
            Toast.makeText(this, "¡Contraseña principal actualizada!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun configurarOjos() {
        val etCurrent  = findViewById<EditText>(R.id.etCurrentPassword)
        val etNew      = findViewById<EditText>(R.id.etNewPassword)
        val etConfirm  = findViewById<EditText>(R.id.etConfirmPassword)

        findViewById<ImageView>(R.id.ivToggleCurrent).setOnClickListener {
            isCurrentVisible = !isCurrentVisible
            toggleVisibility(etCurrent, isCurrentVisible)
        }
        findViewById<ImageView>(R.id.ivToggleNew).setOnClickListener {
            isNewVisible = !isNewVisible
            toggleVisibility(etNew, isNewVisible)
        }
        findViewById<ImageView>(R.id.ivToggleConfirm).setOnClickListener {
            isConfirmVisible = !isConfirmVisible
            toggleVisibility(etConfirm, isConfirmVisible)
        }
    }

    private fun toggleVisibility(campo: EditText, mostrar: Boolean) {
        campo.transformationMethod = if (mostrar)
            HideReturnsTransformationMethod.getInstance()
        else
            PasswordTransformationMethod.getInstance()
        campo.setSelection(campo.text.length)
    }

    // ─────────────────────────────────────────────────────────────
    //  configurarBarraFortaleza()
    //  Esta pantalla tiene una barra diferente a ChangeMasterKeyActivity.
    //  En lugar de 3 Views separadas, tiene UNA sola View ("viewStrengthFill")
    //  dentro de un LinearLayout con weightSum="10".
    //
    //  CONCEPTO: layout_weight dinámico
    //  El "peso" (weight) de un View determina cuánto espacio ocupa
    //  dentro de su contenedor. Con weightSum="10":
    //    · weight=3 → ocupa 30% del ancho (débil)
    //    · weight=6 → ocupa 60% del ancho (medio)
    //    · weight=10 → ocupa 100% del ancho (fuerte)
    //
    //  Para cambiar el weight por código necesitamos acceder a los
    //  LayoutParams del View y modificar el campo "weight".
    //  LayoutParams son los parámetros de posición/tamaño de una vista
    //  dentro de su contenedor padre.
    // ─────────────────────────────────────────────────────────────
    private fun configurarBarraFortaleza() {
        val etNew        = findViewById<EditText>(R.id.etNewPassword)
        val barraFill    = findViewById<View>(R.id.viewStrengthFill)
        val tvLabel      = findViewById<TextView>(R.id.tvStrengthLabel)

        etNew.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val nivel = evaluarFortaleza(s.toString())
                actualizarBarra(barraFill, tvLabel, nivel)
            }
        })

        // Inicializar la barra con el texto actual (si ya hay algo escrito)
        actualizarBarra(barraFill, tvLabel, evaluarFortaleza(etNew.text.toString()))
    }

    // ─────────────────────────────────────────────────────────────
    //  actualizarBarra()
    //  Cambia el weight del View de relleno y actualiza color + texto.
    //
    //  LinearLayout.LayoutParams hereda de ViewGroup.LayoutParams y
    //  agrega el campo "weight". Casteamos con "as" para acceder a él.
    //  Después de cambiar los LayoutParams hay que llamar
    //  requestLayout() para que Android redibuje la vista.
    // ─────────────────────────────────────────────────────────────
    private fun actualizarBarra(barra: View, label: TextView, nivel: Int) {
        // Obtenemos los LayoutParams actuales y los convertimos al tipo correcto
        val params = barra.layoutParams as android.widget.LinearLayout.LayoutParams

        when (nivel) {
            0 -> {
                params.weight = 0f
                barra.setBackgroundColor(ContextCompat.getColor(this, R.color.border_option_card))
                label.text = ""
                label.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            }
            1 -> {
                params.weight = 3f   // 30% → débil
                barra.setBackgroundColor(ContextCompat.getColor(this, R.color.history_red))
                label.text = "Fortaleza Baja"
                label.setTextColor(ContextCompat.getColor(this, R.color.history_red))
            }
            2 -> {
                params.weight = 6f   // 60% → media
                barra.setBackgroundColor(0xFFFFC107.toInt())
                label.text = "Fortaleza Media"
                label.setTextColor(0xFFFFC107.toInt())
            }
            3 -> {
                params.weight = 10f  // 100% → fuerte
                barra.setBackgroundColor(ContextCompat.getColor(this, R.color.green_accent))
                label.text = "Fortaleza Alta"
                label.setTextColor(ContextCompat.getColor(this, R.color.green_accent))
            }
        }

        // Aplicamos los nuevos LayoutParams y pedimos redibujar
        barra.layoutParams = params
        barra.requestLayout()
    }

    private fun evaluarFortaleza(password: String): Int {
        if (password.isEmpty()) return 0
        val tieneLetras   = Regex("[a-zA-Z]").containsMatchIn(password)
        val tieneNumeros  = Regex("[0-9]").containsMatchIn(password)
        val tieneSimbolos = Regex("[^a-zA-Z0-9]").containsMatchIn(password)
        return when {
            password.length >= 8 && tieneLetras && tieneNumeros && tieneSimbolos -> 3
            password.length >= 6 && (tieneLetras || tieneNumeros)                -> 2
            else                                                                  -> 1
        }
    }
}
