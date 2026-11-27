package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChangeAccountPasswordActivity : BaseActivity() {

    private lateinit var etCurrent: EditText
    private lateinit var etNew: EditText
    private lateinit var etConfirm: EditText
    private lateinit var tvError: TextView
    private lateinit var btnChange: AppCompatButton
    private lateinit var tvForgot: TextView

    private var currentVisible = false
    private var newVisible = false
    private var confirmVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return
        setContentView(R.layout.activity_change_account_password)

        etCurrent = findViewById(R.id.etCurrentPassword)
        etNew = findViewById(R.id.etNewPassword)
        etConfirm = findViewById(R.id.etConfirmPassword)
        tvError = findViewById(R.id.tvError)
        btnChange = findViewById(R.id.btnChangePassword)
        tvForgot = findViewById(R.id.tvForgotPassword)

        findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnBack).setOnClickListener { finish() }

        // Toggles de visibilidad
        findViewById<ImageView>(R.id.ivToggleCurrent).setOnClickListener {
            currentVisible = !currentVisible
            etCurrent.transformationMethod = if (currentVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            etCurrent.setSelection(etCurrent.text.length)
            (it as ImageView).setImageResource(if (currentVisible) R.drawable.ojo else R.drawable.ojo_cerrado)
        }
        findViewById<ImageView>(R.id.ivToggleNew).setOnClickListener {
            newVisible = !newVisible
            etNew.transformationMethod = if (newVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            etNew.setSelection(etNew.text.length)
            (it as ImageView).setImageResource(if (newVisible) R.drawable.ojo else R.drawable.ojo_cerrado)
        }
        findViewById<ImageView>(R.id.ivToggleConfirm).setOnClickListener {
            confirmVisible = !confirmVisible
            etConfirm.transformationMethod = if (confirmVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            etConfirm.setSelection(etConfirm.text.length)
            (it as ImageView).setImageResource(if (confirmVisible) R.drawable.ojo else R.drawable.ojo_cerrado)
        }

        btnChange.setOnClickListener { cambiarContrasena() }

        // Enlace de recuperación: envía email de restablecimiento y vuelve atrás
        tvForgot.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    private fun cambiarContrasena() {
        tvError.visibility = View.GONE

        val current = etCurrent.text.toString()
        val nuevo = etNew.text.toString()
        val confirmar = etConfirm.text.toString()

        if (current.isBlank()) {
            mostrarError("Ingresa tu contraseña actual.")
            return
        }
        if (nuevo.length < 6) {
            mostrarError("La nueva contraseña debe tener al menos 6 caracteres.")
            return
        }
        if (nuevo != confirmar) {
            mostrarError("Las contraseñas nuevas no coinciden.")
            return
        }
        if (nuevo == current) {
            mostrarError("La nueva contraseña debe ser diferente a la actual.")
            return
        }

        btnChange.isEnabled = false
        val controls = listOf(etCurrent, etNew, etConfirm, btnChange)
        controls.forEach { it.isEnabled = false }

        vaultJob {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                    ?: error("Sesión no disponible. Vuelve a iniciar sesión.")
                val email = user.email
                    ?: error("No se pudo obtener el correo de la cuenta.")

                // Reautenticar con la contraseña actual
                user.reauthenticate(EmailAuthProvider.getCredential(email, current)).await()

                // Actualizar contraseña en Firebase Auth
                user.updatePassword(nuevo).await()

                etCurrent.text.clear()
                etNew.text.clear()
                etConfirm.text.clear()

                mostrarBannerPersonalizado(
                    "Contraseña actualizada",
                    "Tu contraseña de cuenta fue cambiada correctamente.",
                    R.drawable.logo
                )
                finish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val mensaje = when {
                    e.message?.contains("INVALID_LOGIN_CREDENTIALS") == true ||
                    e.message?.contains("wrong-password") == true ||
                    e.message?.contains("credential") == true ->
                        "Contraseña actual incorrecta."
                    e.message?.contains("network") == true ||
                    e.message?.contains("NETWORK") == true ->
                        "Sin conexión. Verifica tu internet e inténtalo de nuevo."
                    else -> "No se pudo cambiar la contraseña. Inténtalo de nuevo."
                }
                mostrarError(mensaje)
            } finally {
                controls.forEach { it.isEnabled = true }
            }
        }
    }

    private fun mostrarError(mensaje: String) {
        tvError.text = mensaje
        tvError.visibility = View.VISIBLE
    }
}
