package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MasterPasswordActivity : AppCompatActivity() {

    // Variables para controlar el estado de visibilidad de las contraseñas
    private var isNewPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_master_password)

        // Referencias de UI
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val etNewPassword = findViewById<EditText>(R.id.etNewPassword)
        val ivToggleNewPassword = findViewById<ImageView>(R.id.ivToggleNewPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val ivToggleConfirmPassword = findViewById<ImageView>(R.id.ivToggleConfirmPassword)
        val btnCreateVault = findViewById<MaterialCardView>(R.id.btnCreateVault)

        // 1. Botón Volver / Atrás (Cierra esta pantalla y regresa a RegisterActivity)
        btnBack.setOnClickListener {
            finish()
        }

        // 2. Lógica para mostrar/ocultar "Nueva contraseña"
        ivToggleNewPassword.setOnClickListener {
            isNewPasswordVisible = !isNewPasswordVisible
            togglePasswordVisibility(etNewPassword, ivToggleNewPassword, isNewPasswordVisible)
        }

        // 3. Lógica para mostrar/ocultar "Confirma contraseña"
        ivToggleConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            togglePasswordVisibility(etConfirmPassword, ivToggleConfirmPassword, isConfirmPasswordVisible)
        }

        // 4. Validación de seguridad en tiempo real para las barras indicadoras
        etNewPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSecurityBars(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 5. Botón Crear Bóveda
        btnCreateVault.setOnClickListener {
            val password = etNewPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            if (password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Por favor completa ambos campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "La contraseña maestra debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "¡Bóveda creada con éxito!", Toast.LENGTH_SHORT).show()

            // NAVEGACIÓN A LA HOME (MainActivity)
            val intent = Intent(this, MainActivity::class.java)
            // Limpia la pila para que no pueda volver atrás con el botón físico de Android
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun togglePasswordVisibility(editText: EditText, imageView: ImageView, isVisible: Boolean) {
        val selection = editText.selectionStart
        if (isVisible) {
            editText.transformationMethod = HideReturnsTransformationMethod.getInstance()
        } else {
            editText.transformationMethod = PasswordTransformationMethod.getInstance()
        }
        editText.setSelection(selection)
    }

    private fun updateSecurityBars(password: String) {
        val container = findViewById<LinearLayout>(R.id.layoutStrengthBars)
        if (container == null) return

        val bar1 = container.getChildAt(0)
        val bar2 = container.getChildAt(1)
        val bar3 = container.getChildAt(2)

        val inactiveColor = android.graphics.Color.parseColor("#3A3F4B")
        val activeColor = android.graphics.Color.parseColor("#8FC954") // Verde claro

        val length = password.length
        val hasUpper = password.any { it.isUpperCase() }
        val hasNumber = password.any { it.isDigit() }

        when {
            length == 0 -> {
                bar1?.setBackgroundColor(inactiveColor)
                bar2?.setBackgroundColor(inactiveColor)
                bar3?.setBackgroundColor(inactiveColor)
            }
            length < 6 -> {
                bar1?.setBackgroundColor(activeColor)
                bar2?.setBackgroundColor(inactiveColor)
                bar3?.setBackgroundColor(inactiveColor)
            }
            length >= 6 && (!hasUpper || !hasNumber) -> {
                bar1?.setBackgroundColor(activeColor)
                bar2?.setBackgroundColor(activeColor)
                bar3?.setBackgroundColor(inactiveColor)
            }
            length >= 8 && hasUpper && hasNumber -> {
                bar1?.setBackgroundColor(activeColor)
                bar2?.setBackgroundColor(activeColor)
                bar3?.setBackgroundColor(activeColor)
            }
            else -> {
                bar1?.setBackgroundColor(activeColor)
                bar2?.setBackgroundColor(activeColor)
                bar3?.setBackgroundColor(inactiveColor)
            }
        }
    }
}