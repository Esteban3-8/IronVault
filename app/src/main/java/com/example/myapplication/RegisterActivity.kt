package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class RegisterActivity : BaseActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var ivTogglePassword: ImageView
    private lateinit var ivToggleConfirmPassword: ImageView
    private lateinit var cbTerms: CheckBox
    private lateinit var btnVerify: AppCompatButton
    private lateinit var btnCreateAccount: AppCompatButton
    private lateinit var layoutEmailVerified: View

    private lateinit var tvFullNameError: TextView
    private lateinit var tvEmailError: TextView
    private lateinit var tvConfirmPasswordError: TextView

    private lateinit var tvTermsClick: TextView
    private lateinit var tvPrivacyClick: TextView

    private lateinit var bar1: View
    private lateinit var bar2: View
    private lateinit var bar3: View
    private lateinit var tvStrengthHint: TextView

    private var isPasswordVisible = true
    private var isConfirmPasswordVisible = true
    private var isEmailVerified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        btnBack = findViewById(R.id.btnBack)
        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        ivTogglePassword = findViewById(R.id.ivTogglePassword)
        ivToggleConfirmPassword = findViewById(R.id.ivToggleConfirmPassword)
        cbTerms = findViewById(R.id.cbTerms)
        btnVerify = findViewById(R.id.btnVerify)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        layoutEmailVerified = findViewById(R.id.layoutEmailVerified)

        tvFullNameError = findViewById(R.id.tvFullNameError)
        tvEmailError = findViewById(R.id.tvEmailError)
        tvConfirmPasswordError = findViewById(R.id.tvConfirmPasswordError)

        tvTermsClick = findViewById(R.id.tvTermsClick)
        tvPrivacyClick = findViewById(R.id.tvPrivacyClick)

        tvFullNameError.visibility = View.GONE
        tvEmailError.visibility = View.GONE
        tvConfirmPasswordError.visibility = View.GONE
        layoutEmailVerified.visibility = View.GONE

        val layoutPasswordStrength = findViewById<LinearLayout>(R.id.layoutPasswordStrength)
        bar1 = layoutPasswordStrength.getChildAt(0)
        bar2 = layoutPasswordStrength.getChildAt(1)
        bar3 = layoutPasswordStrength.getChildAt(2)
        tvStrengthHint = findViewById(R.id.tvStrengthHint)

        btnBack.setOnClickListener { finish() }

        resetearBarrasSeguridad()

        etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
        ivTogglePassword.setImageResource(R.drawable.ojo)
        etPassword.setSelection(etPassword.text.length)

        etConfirmPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
        ivToggleConfirmPassword.setImageResource(R.drawable.ojo)
        etConfirmPassword.setSelection(etConfirmPassword.text.length)

        tvTermsClick.setOnClickListener {
            mostrarDialogoLegal(
                "Términos y Condiciones",
                "Bienvenido a IronVault. Al utilizar nuestra aplicación de bóveda de credenciales, aceptas proteger tu contraseña maestra, usar una conexión segura y entender que los datos de acceso se almacenan cifrados para tu máxima seguridad."
            )
        }

        tvPrivacyClick.setOnClickListener {
            mostrarDialogoLegal(
                "Política de Privacidad",
                "En IronVault valoramos tu privacidad. Tus credenciales y datos sensibles se procesan con altos estándares de seguridad y autenticación mediante Firebase. No compartimos tu información personal con terceros."
            )
        }

        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivTogglePassword.setImageResource(R.drawable.ojo)
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivTogglePassword.setImageResource(R.drawable.ojo_cerrado)
            }
            etPassword.setSelection(etPassword.text.length)
        }

        ivToggleConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            if (isConfirmPasswordVisible) {
                etConfirmPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivToggleConfirmPassword.setImageResource(R.drawable.ojo)
            } else {
                etConfirmPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivToggleConfirmPassword.setImageResource(R.drawable.ojo_cerrado)
            }
            etConfirmPassword.setSelection(etConfirmPassword.text.length)
        }

        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val pass = s.toString()
                if (pass.isEmpty()) resetearBarrasSeguridad() else evaluarSeguridadContrasena(pass)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etFullName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { if (s.toString().isNotEmpty()) tvFullNameError.visibility = View.GONE }
        })

        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { if (s.toString().isNotEmpty()) tvEmailError.visibility = View.GONE }
        })

        etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { if (s.toString().isNotEmpty()) tvConfirmPasswordError.visibility = View.GONE }
        })

        btnVerify.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty()) {
                tvEmailError.text = "Ingresa un correo electrónico"
                tvEmailError.setTextColor(Color.parseColor("#EF4444"))
                tvEmailError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (password.length < 6) {
                tvStrengthHint.text = "La contraseña debe tener al menos 6 caracteres"
                tvStrengthHint.setTextColor(Color.parseColor("#EF4444"))
                return@setOnClickListener
            }

            btnVerify.isEnabled = false
            mostrarBannerPersonalizado(
                titulo = "Verificación",
                mensaje = "Enviando código de acceso...",
                iconoResId = R.drawable.logo
            )
            enviarYGenerarOtpTemporal(email)
        }

        supportFragmentManager.setFragmentResultListener("verifyKey", this) { _, bundle ->
            isEmailVerified = bundle.getBoolean("emailVerified", false)
            if (isEmailVerified) {
                mostrarBannerPersonalizado(
                    titulo = "Verificado",
                    mensaje = "¡Correo verificado con éxito!",
                    iconoResId = R.drawable.logo
                )
                layoutEmailVerified.visibility = View.VISIBLE
                btnVerify.isEnabled = false
                btnVerify.text = "Verificado"
            }
        }

        btnCreateAccount.setOnClickListener {
            val nombre = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPass = etConfirmPassword.text.toString().trim()

            var hayError = false

            if (nombre.isEmpty()) {
                tvFullNameError.text = "Ingresa tu nombre completo"
                tvFullNameError.setTextColor(Color.parseColor("#EF4444"))
                tvFullNameError.visibility = View.VISIBLE
                hayError = true
            }

            if (email.isEmpty()) {
                tvEmailError.text = "Ingresa un correo electrónico"
                tvEmailError.setTextColor(Color.parseColor("#EF4444"))
                tvEmailError.visibility = View.VISIBLE
                hayError = true
            }

            if (!isEmailVerified) {
                mostrarBannerPersonalizado(
                    titulo = "Atención",
                    mensaje = "Debes verificar tu correo antes de continuar",
                    iconoResId = R.drawable.logo
                )
                hayError = true
            }

            if (password.length < 6) {
                tvStrengthHint.text = "La contraseña debe tener al menos 6 caracteres"
                tvStrengthHint.setTextColor(Color.parseColor("#EF4444"))
                hayError = true
            }

            if (password != confirmPass) {
                tvConfirmPasswordError.text = "Las contraseñas no coinciden"
                tvConfirmPasswordError.setTextColor(Color.parseColor("#EF4444"))
                tvConfirmPasswordError.visibility = View.VISIBLE
                hayError = true
            }

            if (!cbTerms.isChecked) {
                mostrarBannerPersonalizado(
                    titulo = "Términos y condiciones",
                    mensaje = "Debes aceptar los Términos y la Política de Privacidad",
                    iconoResId = R.drawable.logo
                )
                hayError = true
            }

            if (hayError) return@setOnClickListener

            crearCuentaDefinitivaEnFirebase(nombre, email, password)
        }
    }

    private fun mostrarDialogoLegal(titulo: String, contenido: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_legal, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnClose = dialogView.findViewById<AppCompatButton>(R.id.btnCloseDialog)

        tvTitle.text = titulo
        tvMessage.text = contenido

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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

    private fun enviarYGenerarOtpTemporal(email: String) {
        val otpCode = (100000..999999).random().toString()

        val sharedPrefs = getSharedPreferences("IronVaultPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("temp_otp", otpCode)
            .putString("user_email", email)
            .apply()

        enviarCorreoHttp(email, otpCode) { enviado ->
            if (enviado) {
                mostrarBannerPersonalizado(
                    titulo = "Código enviado",
                    mensaje = "Revisa tu correo electrónico",
                    iconoResId = R.drawable.logo
                )

                val bottomSheet = VerifyBottomSheet().apply {
                    arguments = Bundle().apply {
                        putString("arg_email", email)
                    }
                }
                bottomSheet.show(supportFragmentManager, "VerifyBottomSheet")
                btnVerify.isEnabled = true
            } else {
                mostrarBannerPersonalizado(
                    titulo = "Error de envío",
                    mensaje = "No se pudo enviar el correo. Revisa tu conexión.",
                    iconoResId = R.drawable.logo
                )
                btnVerify.isEnabled = true
            }
        }
    }

    private fun enviarCorreoHttp(email: String, code: String, onResult: (Boolean) -> Unit) {
        Thread {
            try {
                val baseUrl = "https://script.google.com/macros/s/AKfycbyBVOes8JC04vuOc0FXUHmPQ_d9Po5TKSX9XNwISKlfj6LYJI8iw-I0SBXrxtPU-dzi/exec"
                val token = "ironvault_secreto_2026"
                val urlStr = baseUrl + "?email=" + URLEncoder.encode(email, "UTF-8") +
                        "&code=" + URLEncoder.encode(code, "UTF-8") +
                        "&token=" + URLEncoder.encode(token, "UTF-8")

                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 20000
                conn.readTimeout = 20000

                val responseCode = conn.responseCode
                conn.disconnect()

                val exito = responseCode == HttpURLConnection.HTTP_OK

                runOnUiThread {
                    onResult(exito)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    onResult(false)
                }
            }
        }.start()
    }

    private fun crearCuentaDefinitivaEnFirebase(fullName: String, email: String, pass: String) {
        val auth = Firebase.auth
        val db = Firebase.firestore

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val userId = result.user?.uid ?: return@addOnSuccessListener
                val userData = hashMapOf(
                    "email" to email,
                    "fullName" to fullName,
                    "completedRegistration" to true,
                    "verified" to true
                )

                db.collection("users").document(userId).set(userData)
                    .addOnSuccessListener {
                        mostrarBannerPersonalizado(
                            titulo = "¡Bienvenido!",
                            mensaje = "¡Cuenta creada correctamente!",
                            iconoResId = R.drawable.logo
                        )

                        // ── GUARDAR SESIÓN PERSISTENTE PARA WELCOMEACTIVITY ──
                        val settingsPrefs = getSharedPreferences("settings_prefs", MODE_PRIVATE)
                        settingsPrefs.edit().putBoolean("is_logged_in", true).apply()
                        // Ya no es invitado
                        GuestUtils.marcarInvitado(this, false)
                        // ----------------------------------------------------

                        val intent = Intent(this, MasterPasswordActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        mostrarBannerPersonalizado(
                            titulo = "Error de perfil",
                            mensaje = "No se pudo guardar: ${e.message}",
                            iconoResId = R.drawable.logo
                        )
                    }
            }
            .addOnFailureListener { e ->
                val exceptionMessage = e.message ?: ""

                val mensajePersonalizado = if (exceptionMessage.contains("already in use", ignoreCase = true)) {
                    "Este correo ya está registrado. Inicia sesión."
                } else {
                    "Error al crear cuenta: $exceptionMessage"
                }

                mostrarBannerPersonalizado(
                    titulo = "Error de registro",
                    mensaje = mensajePersonalizado,
                    iconoResId = R.drawable.logo
                )
            }
    }
}