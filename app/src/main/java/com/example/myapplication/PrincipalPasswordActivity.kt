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
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.Firebase

// ─────────────────────────────────────────────────────────────────
//  PrincipalPasswordActivity
//
//  Permite cambiar la contraseña principal de la bóveda.
//
//  Flujo:
//  1. El usuario escribe su contraseña actual.
//  2. Se reautentica contra Firebase Auth (misma contraseña que usa
//     para iniciar sesión en la app).
//  3. Si es correcta, se llama a updatePassword() con la nueva.
//  4. Firebase actualiza la contraseña en su base de datos.
//
//  "¿Olvidaste tu contraseña?" redirige al mismo flujo de recuperación
//  que existe en la pantalla de inicio de sesión (ForgotPasswordActivity).
//
//  NOTA SOBRE EXPORTACIÓN:
//  La contraseña que protege un archivo .vault es INDEPENDIENTE de la
//  contraseña principal. El usuario la elige en el momento de exportar.
//  Cambiar la contraseña principal aquí NO invalida los respaldos anteriores.
// ─────────────────────────────────────────────────────────────────
class PrincipalPasswordActivity : BaseActivity() {

    private var isCurrentVisible = false
    private var isNewVisible     = false
    private var isConfirmVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_principal_password)

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
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // ── Olvidé contraseña → mismo flujo del login ─────────────
        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        val etCurrent = findViewById<EditText>(R.id.etCurrentPassword)
        val etNew     = findViewById<EditText>(R.id.etNewPassword)
        val etConfirm = findViewById<EditText>(R.id.etConfirmPassword)
        val btnChange = findViewById<AppCompatButton>(R.id.btnChangePrincipal)

        btnChange.setOnClickListener {
            val current = etCurrent.text.toString().trim()
            val nueva   = etNew.text.toString().trim()
            val confirm = etConfirm.text.toString().trim()

            // ── Validaciones locales ──────────────────────────────
            if (current.isEmpty() || nueva.isEmpty() || confirm.isEmpty()) {
                mostrarBannerPersonalizado("Campos vacíos", "Por favor completa todos los campos", R.drawable.logo)
                return@setOnClickListener
            }
            if (nueva != confirm) {
                mostrarBannerPersonalizado("Error de coincidencia", "Las contraseñas nuevas no coinciden", R.drawable.logo)
                return@setOnClickListener
            }
            if (evaluarFortaleza(nueva) < 2) {
                mostrarBannerPersonalizado("Contraseña débil", "Usa al menos 8 caracteres con letras y números", R.drawable.logo)
                return@setOnClickListener
            }
            if (nueva == current) {
                mostrarBannerPersonalizado("Sin cambios", "La nueva contraseña debe ser diferente a la actual", R.drawable.logo)
                return@setOnClickListener
            }

            val user = Firebase.auth.currentUser
            if (user == null || user.email == null) {
                mostrarBannerPersonalizado("Error de sesión", "No se pudo verificar tu identidad. Inicia sesión nuevamente.", R.drawable.logo)
                return@setOnClickListener
            }

            // Deshabilitar botón mientras opera Firebase
            btnChange.isEnabled = false
            btnChange.text = "Verificando..."

            // ── Paso 1: reautenticar con la contraseña actual ─────
            val credential = EmailAuthProvider.getCredential(user.email!!, current)

            user.reauthenticate(credential)
                .addOnSuccessListener {
                    // ── Paso 2: actualizar contraseña en Firebase Auth ──
                    user.updatePassword(nueva)
                        .addOnSuccessListener {
                            btnChange.isEnabled = true
                            btnChange.text = "Cambiar contraseña principal"

                            mostrarBannerPersonalizado(
                                titulo = "¡Contraseña actualizada!",
                                mensaje = "Tu contraseña principal fue cambiada con éxito.",
                                iconoResId = R.drawable.logo
                            )

                            // Limpiar campos
                            etCurrent.text.clear()
                            etNew.text.clear()
                            etConfirm.text.clear()

                            finish()
                        }
                        .addOnFailureListener { e ->
                            btnChange.isEnabled = true
                            btnChange.text = "Cambiar contraseña principal"
                            mostrarBannerPersonalizado(
                                titulo = "Error al actualizar",
                                mensaje = "No se pudo cambiar la contraseña. Intenta de nuevo.",
                                iconoResId = R.drawable.logo
                            )
                        }
                }
                .addOnFailureListener {
                    btnChange.isEnabled = true
                    btnChange.text = "Cambiar contraseña principal"
                    mostrarBannerPersonalizado(
                        titulo = "Contraseña incorrecta",
                        mensaje = "La contraseña actual no es válida.",
                        iconoResId = R.drawable.logo
                    )
                }
        }
    }

    private fun configurarOjos() {
        val etCurrent = findViewById<EditText>(R.id.etCurrentPassword)
        val etNew     = findViewById<EditText>(R.id.etNewPassword)
        val etConfirm = findViewById<EditText>(R.id.etConfirmPassword)

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

    private fun configurarBarraFortaleza() {
        val etNew     = findViewById<EditText>(R.id.etNewPassword)
        val barraFill = findViewById<View>(R.id.viewStrengthFill)
        val tvLabel   = findViewById<TextView>(R.id.tvStrengthLabel)

        etNew.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                actualizarBarra(barraFill, tvLabel, evaluarFortaleza(s.toString()))
            }
        })
        actualizarBarra(barraFill, tvLabel, 0)
    }

    private fun actualizarBarra(barra: View, label: TextView, nivel: Int) {
        val params = barra.layoutParams as android.widget.LinearLayout.LayoutParams
        when (nivel) {
            0 -> { params.weight = 0f;  label.text = "" }
            1 -> { params.weight = 3f;  label.text = "Fortaleza Baja";  label.setTextColor(ContextCompat.getColor(this, R.color.history_red));      barra.setBackgroundColor(ContextCompat.getColor(this, R.color.history_red)) }
            2 -> { params.weight = 6f;  label.text = "Fortaleza Media"; label.setTextColor(0xFFFFC107.toInt());                                      barra.setBackgroundColor(0xFFFFC107.toInt()) }
            3 -> { params.weight = 10f; label.text = "Fortaleza Alta";  label.setTextColor(ContextCompat.getColor(this, R.color.green_accent));      barra.setBackgroundColor(ContextCompat.getColor(this, R.color.green_accent)) }
        }
        barra.layoutParams = params
        barra.requestLayout()
    }

    private fun evaluarFortaleza(password: String): Int {
        if (password.isEmpty()) return 0
        val letras   = Regex("[a-zA-Z]").containsMatchIn(password)
        val numeros  = Regex("[0-9]").containsMatchIn(password)
        val simbolos = Regex("[^a-zA-Z0-9]").containsMatchIn(password)
        return when {
            password.length >= 8 && letras && numeros && simbolos -> 3
            password.length >= 6 && (letras || numeros)           -> 2
            else                                                  -> 1
        }
    }
}
