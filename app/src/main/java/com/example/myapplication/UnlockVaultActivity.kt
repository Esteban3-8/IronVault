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

class UnlockVaultActivity : AppCompatActivity() {

    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unlock_vault)

        // ── Leer el motivo de bloqueo ─────────────────────────────
        // CONCEPTO: Intent Extras
        // La pantalla anterior nos pasó un dato con putExtra("lock_reason", motivo).
        // Lo leemos aquí con getStringExtra("lock_reason").
        // Si no viene ningún extra (acceso normal), devuelve null.
        val lockReason = intent.getStringExtra("lock_reason")

        // Según el motivo, mostramos un mensaje distinto en el subtítulo
        // y un Toast al abrir para notificar al usuario qué pasó.
        val tvSubtitle = findViewById<TextView>(R.id.tvSubtitle)

        when (lockReason) {
            "manual" -> {
                // El usuario tocó "Bloquear bóveda ahora"
                tvSubtitle.text = "La bóveda ha sido bloqueada.\nIngresa tu clave maestra para continuar."
                Toast.makeText(this, "🔒 Bóveda bloqueada", Toast.LENGTH_SHORT).show()
            }
            "auto" -> {
                // El tiempo de inactividad se agotó
                tvSubtitle.text = "La bóveda se bloqueó automáticamente por inactividad.\nIngresa tu clave maestra para continuar."
                Toast.makeText(this, "🔒 Bóveda bloqueada por inactividad", Toast.LENGTH_SHORT).show()
            }
            else -> {
                // Acceso normal sin bloqueo previo
                tvSubtitle.text = "Ya iniciaste sesión. Ahora ingresa la llave que protege tu bóveda."
            }
        }

        // Referencias de UI
        val btnBack                  = findViewById<ImageView>(R.id.btnBack)
        val etMasterPassword         = findViewById<EditText>(R.id.etMasterPassword)
        val ivToggleMasterPassword = findViewById<ImageView>(R.id.ivToggleMasterPassword)
        val btnUnlockVault           = findViewById<MaterialCardView>(R.id.btnUnlockVault)

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

        // Desbloquear bóveda
        btnUnlockVault.setOnClickListener {
            val password = etMasterPassword.text.toString().trim()

            if (password.isEmpty()) {
                Toast.makeText(this, "Por favor ingresa tu contraseña maestra", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "¡Bóveda desbloqueada con éxito!", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}