package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class UnlockVaultActivity : AppCompatActivity() {

    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unlock_vault)

        // Referencias de UI actualizadas a tus IDs y tipos del XML
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val etMasterPassword = findViewById<EditText>(R.id.etMasterPassword)
        val ivToggleMasterPassword = findViewById<ImageView>(R.id.ivToggleMasterPassword)
        val btnUnlockVault = findViewById<MaterialCardView>(R.id.btnUnlockVault)

        // 1. Botón para regresar
        btnBack.setOnClickListener {
            finish()
        }

        // 2. Mostrar u Ocultar la contraseña maestra con el icono del ojo
        ivToggleMasterPassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            val selection = etMasterPassword.selectionStart
            if (isPasswordVisible) {
                etMasterPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
            } else {
                etMasterPassword.transformationMethod = PasswordTransformationMethod.getInstance()
            }
            etMasterPassword.setSelection(selection)
        }

        // 3. Botón para desbloquear / iniciar la bóveda
        btnUnlockVault.setOnClickListener {
            val password = etMasterPassword.text.toString().trim()

            if (password.isEmpty()) {
                Toast.makeText(this, "Por favor ingresa tu contraseña maestra", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // TODO: Aquí validas la contraseña con tu base de datos local (Room / SQLite) o SharedPreferences
            Toast.makeText(this, "¡Bóveda desbloqueada con éxito!", Toast.LENGTH_SHORT).show()

            // Ejemplo para pasar a tu pantalla principal (Dashboard / Home):
            // val intent = Intent(this, MainActivity::class.java)
            // startActivity(intent)
            // finish()
        }
    }
}