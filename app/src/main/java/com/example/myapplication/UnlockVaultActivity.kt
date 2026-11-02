package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class UnlockVaultActivity : AppCompatActivity() {

    // INICIA EN TRUE PARA QUE LA CONTRASEÑA SEA VISIBLE POR DEFECTO
    private var isPasswordVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unlock_vault)

        // ── Leer el motivo de bloqueo ─────────────────────────────
        val lockReason = intent.getStringExtra("lock_reason")

        val tvSubtitle = findViewById<TextView>(R.id.tvSubtitle)

        when (lockReason) {
            "manual" -> {
                tvSubtitle.text = "La bóveda ha sido bloqueada.\nIngresa tu clave maestra para continuar."
                Toast.makeText(this, "🔒 Bóveda bloqueada", Toast.LENGTH_SHORT).show()
            }
            "auto" -> {
                tvSubtitle.text = "La bóveda se bloqueó automáticamente por inactividad.\nIngresa tu clave maestra para continuar."
                Toast.makeText(this, "🔒 Bóveda bloqueada por inactividad", Toast.LENGTH_SHORT).show()
            }
            else -> {
                tvSubtitle.text = "Ya iniciaste sesión. Ahora ingresa la llave que protege tu bóveda."
            }
        }

        // Referencias de UI
        val btnBack              = findViewById<ImageView>(R.id.btnBack)
        val etMasterPassword     = findViewById<EditText>(R.id.etMasterPassword)
        val ivToggleMasterPassword = findViewById<ImageView>(R.id.ivToggleMasterPassword)
        val btnUnlockVault       = findViewById<MaterialCardView>(R.id.btnUnlockVault)

        // =================================================================
        // CONFIGURACIÓN INICIAL: CONTRASEÑA VISIBLE Y OJO ABIERTO POR DEFECTO
        // =================================================================
        etMasterPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
        ivToggleMasterPassword.setImageResource(R.drawable.ojo)
        etMasterPassword.setSelection(etMasterPassword.text.length)
        // =================================================================

        // Botón atrás
        btnBack.setOnClickListener { finish() }

        // Mostrar / ocultar contraseña y cambiar icono del ojo
        ivToggleMasterPassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            val selection = etMasterPassword.selectionStart
            if (isPasswordVisible) {
                etMasterPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivToggleMasterPassword.setImageResource(R.drawable.ojo)
            } else {
                etMasterPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivToggleMasterPassword.setImageResource(R.drawable.ojo_cerrado)
            }
            etMasterPassword.setSelection(selection)
        }

        // Desbloquear bóveda con validación real en Firebase Auth
        btnUnlockVault.setOnClickListener {
            val password = etMasterPassword.text.toString().trim()

            if (password.isEmpty()) {
                Toast.makeText(this, "Por favor ingresa tu contraseña maestra", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = Firebase.auth.currentUser
            if (user == null || user.email == null) {
                Toast.makeText(this, "Error de sesión. Por favor inicia sesión nuevamente.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Reautenticación segura con Firebase Auth utilizando el correo actual y la contraseña ingresada
            val credential = EmailAuthProvider.getCredential(user.email!!, password)

            user.reauthenticate(credential)
                .addOnSuccessListener {
                    Toast.makeText(this, "¡Bóveda desbloqueada con éxito!", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener {
                    // Si la contraseña maestra es incorrecta, se bloquea el acceso
                    Toast.makeText(this, "Contraseña maestra incorrecta", Toast.LENGTH_LONG).show()
                }
        }
    }
}