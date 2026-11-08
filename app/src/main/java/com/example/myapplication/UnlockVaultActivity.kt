package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class UnlockVaultActivity : BaseActivity() {

    // INICIA EN TRUE PARA QUE LA CONTRASEÑA SEA VISIBLE POR DEFECTO
    private var isPasswordVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Invitado: no tiene clave maestra, vuelve directo a su bóveda
        if (GuestUtils.esInvitado(this)) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        setContentView(R.layout.activity_unlock_vault)

        // ── Leer el motivo de bloqueo ─────────────────────────────
        val lockReason = intent.getStringExtra("lock_reason")

        val tvSubtitle = findViewById<TextView>(R.id.tvSubtitle)

        when (lockReason) {
            "manual" -> {
                tvSubtitle.text = "La bóveda ha sido bloqueada.\nIngresa tu clave maestra para continuar."
                mostrarBannerPersonalizado(
                    titulo = "Bóveda bloqueada",
                    mensaje = "Acceso bloqueado manualmente",
                    iconoResId = R.drawable.logo
                )
            }
            "auto" -> {
                tvSubtitle.text = "La bóveda se bloqueó automáticamente por inactividad.\nIngresa tu clave maestra para continuar."
                mostrarBannerPersonalizado(
                    titulo = "Bóveda bloqueada",
                    mensaje = "Bloqueada por inactividad",
                    iconoResId = R.drawable.logo
                )
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

        // Desbloquear bóveda
        btnUnlockVault.setOnClickListener {
            val password = etMasterPassword.text.toString().trim()

            if (password.isEmpty()) {
                mostrarBannerPersonalizado(
                    titulo = "Campo requerido",
                    mensaje = "Por favor ingresa tu contraseña maestra",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }

            val user = Firebase.auth.currentUser
            if (user == null || user.email == null) {
                mostrarBannerPersonalizado(
                    titulo = "Error de sesión",
                    mensaje = "Por favor inicia sesión nuevamente.",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }

            // Reautenticación segura con Firebase Auth utilizando el correo actual y la contraseña ingresada
            val credential = EmailAuthProvider.getCredential(user.email!!, password)

            user.reauthenticate(credential)
                .addOnSuccessListener {
                    mostrarBannerPersonalizado(
                        titulo = "¡Bóveda desbloqueada!",
                        mensaje = "Acceso concedido con éxito",
                        iconoResId = R.drawable.logo
                    )

                    // CLAVE: Limpiamos el tiempo de inactividad aquí para romper el bucle
                    // y permitir que el usuario navegue de forma normal en el Home.
                    SessionManager.limpiarSesion(this)

                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener {
                    mostrarBannerPersonalizado(
                        titulo = "Error de acceso",
                        mensaje = "Contraseña maestra incorrecta",
                        iconoResId = R.drawable.logo
                    )
                }
        }
    }
}