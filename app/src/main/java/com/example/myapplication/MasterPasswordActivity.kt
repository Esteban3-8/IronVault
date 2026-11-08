package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MasterPasswordActivity : BaseActivity() {

    // INICIAN EN TRUE PARA QUE LOS CAMPOS SE MUESTREN VISIBLES POR DEFECTO
    private var isNewPasswordVisible = true
    private var isConfirmPasswordVisible = true

    private lateinit var bar1: View
    private lateinit var bar2: View
    private lateinit var bar3: View
    private lateinit var tvStrengthHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_master_password)

        // Referencias de UI sincronizadas exactamente con los IDs del XML
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val etNewPassword = findViewById<EditText>(R.id.etNewPassword)
        val ivToggleNewPassword = findViewById<ImageView>(R.id.ivToggleNewPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val ivToggleConfirmPassword = findViewById<ImageView>(R.id.ivToggleConfirmPassword)
        val btnCreateVault = findViewById<MaterialCardView>(R.id.btnCreateVault)
        tvStrengthHint = findViewById(R.id.tvStrengthHint)

        // Referencias de las barras de seguridad del contenedor XML
        val layoutStrengthBars = findViewById<LinearLayout>(R.id.layoutStrengthBars)
        bar1 = layoutStrengthBars.getChildAt(0)
        bar2 = layoutStrengthBars.getChildAt(1)
        bar3 = layoutStrengthBars.getChildAt(2)

        // Estado inicial de las barras y textos
        resetearBarrasSeguridad()

        // =================================================================
        // CONFIGURACIÓN INICIAL: CONTRASEÑAS VISIBLES Y OJO ABIERTO POR DEFECTO
        // =================================================================
        etNewPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
        ivToggleNewPassword.setImageResource(R.drawable.ojo)
        etNewPassword.setSelection(etNewPassword.text.length)

        etConfirmPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
        ivToggleConfirmPassword.setImageResource(R.drawable.ojo)
        etConfirmPassword.setSelection(etConfirmPassword.text.length)
        // =================================================================

        // 1. Botón Volver
        btnBack.setOnClickListener {
            finish()
        }

        // 2. Mostrar/Ocultar contraseña nueva
        ivToggleNewPassword.setOnClickListener {
            isNewPasswordVisible = !isNewPasswordVisible
            val selection = etNewPassword.selectionStart
            if (isNewPasswordVisible) {
                etNewPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivToggleNewPassword.setImageResource(R.drawable.ojo)
            } else {
                etNewPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivToggleNewPassword.setImageResource(R.drawable.ojo_cerrado)
            }
            etNewPassword.setSelection(selection)
        }

        // 3. Mostrar/Ocultar confirmación
        ivToggleConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            val selection = etConfirmPassword.selectionStart
            if (isConfirmPasswordVisible) {
                etConfirmPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivToggleConfirmPassword.setImageResource(R.drawable.ojo)
            } else {
                etConfirmPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivToggleConfirmPassword.setImageResource(R.drawable.ojo_cerrado)
            }
            etConfirmPassword.setSelection(selection)
        }

        // 4. Control dinámico de seguridad letra por letra con onTextChanged
        etNewPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val pass = s.toString()
                if (pass.isEmpty()) {
                    resetearBarrasSeguridad()
                } else {
                    evaluarSeguridadContrasena(pass)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 5. Botón Crear Bóveda y guardado en Firestore
        btnCreateVault.setOnClickListener {
            val password = etNewPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (password.isEmpty() || confirmPassword.isEmpty()) {
                mostrarBannerPersonalizado(
                    titulo = "Campos requeridos",
                    mensaje = "Por favor completa ambos campos",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                mostrarBannerPersonalizado(
                    titulo = "Error de validación",
                    mensaje = "Las contraseñas no coinciden",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }

            if (password.length < 6) {
                mostrarBannerPersonalizado(
                    titulo = "Contraseña débil",
                    mensaje = "Debe tener al menos 6 caracteres",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }

            // ── Modo invitado: la clave se guarda solo en el dispositivo ──
            if (GuestUtils.esInvitado(this)) {
                GuestUtils.guardarClave(this, password)
                mostrarBannerPersonalizado(
                    titulo = "¡Bóveda creada!",
                    mensaje = "Guardada en este dispositivo",
                    iconoResId = R.drawable.logo
                )
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return@setOnClickListener
            }

            // Actualizar el estado de la bóveda en Firestore vinculado al usuario actual
            val user = Firebase.auth.currentUser
            if (user != null) {
                val db = Firebase.firestore
                val masterData = hashMapOf<String, Any>(
                    "hasMasterPassword" to true,
                    "masterPasswordSetupAt" to System.currentTimeMillis()
                )

                db.collection("users").document(user.uid)
                    .set(masterData, SetOptions.merge())
                    .addOnSuccessListener {
                        mostrarBannerPersonalizado(
                            titulo = "¡Bóveda creada!",
                            mensaje = "Configurada con éxito",
                            iconoResId = R.drawable.logo
                        )

                        // Pasar a la MainActivity limpiando la pila de actividades
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        mostrarBannerPersonalizado(
                            titulo = "Error de sincronización",
                            mensaje = "No se pudo guardar: ${e.message}",
                            iconoResId = R.drawable.logo
                        )
                    }
            } else {
                mostrarBannerPersonalizado(
                    titulo = "Error de autenticación",
                    mensaje = "Usuario no autenticado",
                    iconoResId = R.drawable.logo
                )
            }
        }
    }

    private fun resetearBarrasSeguridad() {
        val grisInactivo = Color.parseColor("#3A3F4B")
        bar1.setBackgroundColor(grisInactivo)
        bar2.setBackgroundColor(grisInactivo)
        bar3.setBackgroundColor(grisInactivo)
        tvStrengthHint.text = "Mínimo 6 caracteres"
        tvStrengthHint.setTextColor(Color.parseColor("#9CA3AF"))
    }

    private fun evaluarSeguridadContrasena(password: String) {
        val rojoDebil = Color.parseColor("#EF4444")
        val amarilloMedio = Color.parseColor("#F59E0B")
        val verdeFuerte = Color.parseColor("#8FC954")

        resetearBarrasSeguridad()

        when {
            password.length < 6 -> {
                bar1.setBackgroundColor(rojoDebil)
                tvStrengthHint.text = "Débil - Mínimo 6 caracteres"
                tvStrengthHint.setTextColor(rojoDebil)
            }
            password.length >= 6 && !password.any { !it.isLetterOrDigit() } -> {
                bar1.setBackgroundColor(amarilloMedio)
                bar2.setBackgroundColor(amarilloMedio)
                tvStrengthHint.text = "Media - Añade un símbolo"
                tvStrengthHint.setTextColor(amarilloMedio)
            }
            password.length >= 8 && password.any { !it.isLetterOrDigit() } -> {
                bar1.setBackgroundColor(verdeFuerte)
                bar2.setBackgroundColor(verdeFuerte)
                bar3.setBackgroundColor(verdeFuerte)
                tvStrengthHint.text = "Fuerte"
                tvStrengthHint.setTextColor(verdeFuerte)
            }
            else -> {
                bar1.setBackgroundColor(amarilloMedio)
                bar2.setBackgroundColor(amarilloMedio)
                tvStrengthHint.text = "Media"
                tvStrengthHint.setTextColor(amarilloMedio)
            }
        }
    }
}