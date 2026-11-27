package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.util.Patterns
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Firebase

class LoginActivity : BaseActivity() {
    private var signingIn = false

    private var isPasswordVisible = true // Inicia visible por defecto

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return

        // 0. Verificación automática si ya hay una sesión activa en Firebase
        if (Firebase.auth.currentUser != null) {
            GuestUtils.marcarInvitado(this, false)
            val intent = Intent(this, UnlockVaultActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        // Referencias de UI
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val ivTogglePassword = findViewById<ImageView>(R.id.ivTogglePassword)
        val cbRemember = findViewById<CheckBox>(R.id.cbRemember)
        val btnContinue = findViewById<MaterialCardView>(R.id.btnContinue)
        val btnForgotPassword = findViewById<TextView>(R.id.btnForgotPassword)
        val btnGoToRegister = findViewById<TextView>(R.id.btnGoToRegister)

        val sharedPreferences = getSharedPreferences("IronVaultPrefs", Context.MODE_PRIVATE)

        // "Recordarme": solo rellena el correo (la contraseña siempre se escribe)
        if (sharedPreferences.getBoolean("remember_user", false)) {
            val recordado = sharedPreferences.getString("last_email", null)
            if (!recordado.isNullOrEmpty()) {
                etEmail.setText(recordado)
                cbRemember.isChecked = true
            }
        }

        // Sincronizar estado inicial: Contraseña visible y ojo abierto
        isPasswordVisible = true
        etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
        ivTogglePassword.setImageResource(R.drawable.ojo)

        // 1. Botón para regresar
        btnBack.setOnClickListener {
            finish()
        }

        // 2. Mostrar u Ocultar la contraseña con el icono del ojo
        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            val selection = etPassword.selectionStart

            if (isPasswordVisible) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivTogglePassword.setImageResource(R.drawable.ojo)
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivTogglePassword.setImageResource(R.drawable.ojo_cerrado)
            }
            etPassword.setSelection(selection.coerceAtMost(etPassword.text.length))
        }

        // 3. Botón "Continuar" con autenticación de Firebase y validaciones por banner superior
        btnContinue.setOnClickListener {
            if (signingIn) return@setOnClickListener
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            // Validación del correo
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                mostrarBannerPersonalizado(
                    titulo = "Correo inválido",
                    mensaje = "Ingresa el correo de tu cuenta",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }

            // Validación si el campo está vacío
            if (password.isEmpty()) {
                mostrarBannerPersonalizado(
                    titulo = "Atención",
                    mensaje = "Por favor ingresa tu contraseña",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }

            // Validación si la contraseña es muy corta
            if (password.length < 6) {
                mostrarBannerPersonalizado(
                    titulo = "Contraseña corta",
                    mensaje = "Debe tener al menos 6 caracteres",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }

            // Guardar o limpiar la preferencia de la casilla "Recordarme"
            val editor = sharedPreferences.edit()
            if (cbRemember.isChecked) {
                editor.putBoolean("remember_user", true)
                editor.putString("last_email", email)
            } else {
                editor.putBoolean("remember_user", false)
                editor.remove("last_email")
            }
            editor.apply()



            // ¿Venía del modo invitado? Se revisa ANTES de iniciar sesión
            val eraInvitado = GuestUtils.esInvitado(this)

            // Autenticación segura con Firebase Auth
            signingIn = true
            btnContinue.isEnabled = false
            Firebase.auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    signingIn = false
                    if (isFinishing || isDestroyed) return@addOnSuccessListener
                    etPassword.text.clear()
                    // El resto de la app lee el correo de aquí
                    sharedPreferences.edit().putString("user_email", email).apply()

                    // Firebase conserva su propia sesión; ninguna preferencia desbloquea la bóveda.

                    val uid = result.user?.uid

                    if (eraInvitado && uid != null) {
                        subirCredencialesDeInvitado(uid) { irAlDesbloqueo() }
                    } else {
                        mostrarBannerPersonalizado(
                            titulo = "¡Bienvenido!",
                            mensaje = "¡Inicio de sesión exitoso!",
                            iconoResId = R.drawable.logo
                        )
                        irAlDesbloqueo()
                    }
                }
                .addOnFailureListener {
                    signingIn = false
                    if (isFinishing || isDestroyed) return@addOnFailureListener
                    btnContinue.isEnabled = true
                    // Banner superior de error
                    mostrarBannerPersonalizado(
                        titulo = "Error de acceso",
                        mensaje = "Correo o contraseña incorrectos",
                        iconoResId = R.drawable.logo
                    )
                }
        }

        // 4. Recuperación de contraseña
        btnForgotPassword.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }

        // 5. Ir al registro
        btnGoToRegister?.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Sube a la cuenta las credenciales que el invitado tenía en el dispositivo.
     * Solo borra la copia local si la subida salió bien.
     */
    private fun subirCredencialesDeInvitado(uid: String, alTerminar: () -> Unit) {
        GuestUtils.marcarInvitado(this, false)
        mostrarBannerPersonalizado("Datos locales conservados", "Exporta la bóveda de invitado e impórtala después de desbloquear la cuenta.", R.drawable.logo)
        alTerminar()
    }

    private fun irAlDesbloqueo() {
        GuestUtils.marcarInvitado(this, false)
        val intent = Intent(this, UnlockVaultActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
