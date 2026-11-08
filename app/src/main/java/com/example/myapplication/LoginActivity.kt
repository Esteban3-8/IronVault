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
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class LoginActivity : BaseActivity() {

    private var isPasswordVisible = true // Inicia visible por defecto

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 0. Verificación automática si ya hay una sesión activa en Firebase
        if (Firebase.auth.currentUser != null) {
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
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

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

            Log.d("LOGIN_DEBUG", "Intentando iniciar sesión con el correo: $email")

            // ¿Venía del modo invitado? Se revisa ANTES de iniciar sesión
            val eraInvitado = GuestUtils.esInvitado(this)

            // Autenticación segura con Firebase Auth
            Firebase.auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    // El resto de la app lee el correo de aquí
                    sharedPreferences.edit().putString("user_email", email).apply()

                    // ── GUARDAR SESIÓN PERSISTENTE PARA WELCOMEACTIVITY ──
                    val settingsPrefs = getSharedPreferences("settings_prefs", MODE_PRIVATE)
                    settingsPrefs.edit().putBoolean("is_logged_in", true).apply()
                    // ----------------------------------------------------

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
        val locales = GuestStorage.cargar(this)

        // Nada que subir: solo limpiamos la marca de invitado
        if (locales.isEmpty()) {
            GuestUtils.marcarInvitado(this, false)
            mostrarBannerPersonalizado(
                titulo = "¡Bienvenido!",
                mensaje = "¡Inicio de sesión exitoso!",
                iconoResId = R.drawable.logo
            )
            alTerminar()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val coleccion = db.collection("users").document(uid).collection("credentials")
        val lote = db.batch()

        for (c in locales) {
            lote.set(
                coleccion.document(),
                hashMapOf(
                    "serviceName" to c.serviceName,
                    "userOrEmail" to c.userOrEmail,
                    "password" to c.password,
                    "strengthStatus" to c.strengthStatus,
                    "url" to c.url,
                    "notes" to c.notes,
                    "category" to c.category,
                    "updatedAt" to c.updatedAt
                )
            )
        }

        lote.commit()
            .addOnSuccessListener {
                // Subidas con éxito: ahora sí se borra la copia local del invitado
                GuestStorage.guardar(this, emptyList())
                GuestUtils.marcarInvitado(this, false)
                VaultRepository.credentialList.clear()

                mostrarBannerPersonalizado(
                    titulo = "Credenciales migradas",
                    mensaje = "Las del modo invitado ya están en tu cuenta",
                    iconoResId = R.drawable.logo
                )
                alTerminar()
            }
            .addOnFailureListener { e ->
                Log.e("LOGIN_DEBUG", "No se pudo subir lo del invitado: ${e.message}")
                // No se borra nada local
                GuestUtils.marcarInvitado(this, false)
                mostrarBannerPersonalizado(
                    titulo = "Sesión iniciada",
                    mensaje = "No se pudieron subir las del invitado; siguen en este dispositivo",
                    iconoResId = R.drawable.logo
                )
                alTerminar()
            }
    }

    private fun irAlDesbloqueo() {
        val intent = Intent(this, UnlockVaultActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}