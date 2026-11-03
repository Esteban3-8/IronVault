package com.example.myapplication

import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton

class ResetPasswordActivity : BaseActivity() { // 👈 Hereda de BaseActivity

    private var isPasswordVisible1 = false
    private var isPasswordVisible2 = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val etNewPassword = findViewById<EditText>(R.id.etNewPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val ivTogglePassword1 = findViewById<ImageView>(R.id.ivTogglePassword1)
        val ivTogglePassword2 = findViewById<ImageView>(R.id.ivTogglePassword2)
        val btnSavePassword = findViewById<AppCompatButton>(R.id.btnSavePassword)

        // Botón para regresar
        btnBack?.setOnClickListener {
            finish()
        }

        // Control visual del ojo para Nueva Contraseña
        ivTogglePassword1.setOnClickListener {
            isPasswordVisible1 = !isPasswordVisible1
            val selection = etNewPassword.selectionStart
            if (isPasswordVisible1) {
                etNewPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivTogglePassword1.setImageResource(R.drawable.ojo_cerrado)
            } else {
                etNewPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivTogglePassword1.setImageResource(R.drawable.ojo)
            }
            etNewPassword.setSelection(selection)
        }

        // Control visual del ojo para Confirmar Contraseña
        ivTogglePassword2.setOnClickListener {
            isPasswordVisible2 = !isPasswordVisible2
            val selection = etConfirmPassword.selectionStart
            if (isPasswordVisible2) {
                etConfirmPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivTogglePassword2.setImageResource(R.drawable.ojo_cerrado)
            } else {
                etConfirmPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivTogglePassword2.setImageResource(R.drawable.ojo)
            }
            etConfirmPassword.setSelection(selection)
        }

        // Botón para validar y guardar la contraseña
        btnSavePassword?.setOnClickListener {
            val newPassword = etNewPassword?.text.toString().trim()
            val confirmPassword = etConfirmPassword?.text.toString().trim()

            if (newPassword.length < 6) {
                etNewPassword?.error = "La contraseña debe tener al menos 6 caracteres"
                etNewPassword?.requestFocus()
                return@setOnClickListener
            }

            if (newPassword != confirmPassword) {
                etConfirmPassword?.error = "Las contraseñas no coinciden"
                etConfirmPassword?.requestFocus()
                return@setOnClickListener
            }

            // Aquí puedes implementar tu lógica local o el flujo de guardado que necesites
            Toast.makeText(
                this,
                "¡Contraseña actualizada con éxito!",
                Toast.LENGTH_LONG
            ).show()

            finish()
        }
    }
}