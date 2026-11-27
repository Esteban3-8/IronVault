package com.example.myapplication

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
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException
import com.google.firebase.auth.EmailAuthProvider

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
    private var verifiedToken: String? = null
    private var creating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return
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
        btnCreateAccount.isEnabled = false
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
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().isNotEmpty()) tvFullNameError.visibility = View.GONE
            }
        })

        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().isNotEmpty()) {
                    tvEmailError.visibility = View.GONE
                }
                // Si el usuario cambia el correo, se reinicia el estado de verificación
                if (isEmailVerified) {
                    isEmailVerified = false
                    verifiedToken = null
                    btnCreateAccount.isEnabled = false
                    layoutEmailVerified.visibility = View.GONE
                    btnVerify.isEnabled = true
                    btnVerify.text = "Verificar"
                }
            }
        })

        etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().isNotEmpty()) tvConfirmPasswordError.visibility = View.GONE
            }
        })

        btnVerify.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (email.isEmpty()) {
                tvEmailError.text = "Ingresa un correo electrónico"
                tvEmailError.setTextColor(Color.parseColor("#EF4444"))
                tvEmailError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            if (!validarFormatoCorreo(email)) {
                return@setOnClickListener
            }

            if (password.length < 6) {
                tvStrengthHint.text = "La contraseña debe tener al menos 6 caracteres"
                tvStrengthHint.setTextColor(Color.parseColor("#EF4444"))
                return@setOnClickListener
            }

            if (etFullName.text.toString().isBlank() || password != etConfirmPassword.text.toString() || !cbTerms.isChecked) {
                mostrarBannerPersonalizado("Completa tus datos", "Ingresa tu nombre, confirma la contraseña y acepta los términos antes de verificar.", R.drawable.logo)
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

        btnCreateAccount.setOnClickListener {
            val nombre = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val confirmPass = etConfirmPassword.text.toString()

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
            } else if (!validarFormatoCorreo(email)) {
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

            if (!isEmailVerified || verifiedToken == null) {
                mostrarBannerPersonalizado("Verifica tu correo", "Introduce el código recibido antes de crear tu cuenta.", R.drawable.logo)
                hayError = true
            }
            if (hayError || creating) return@setOnClickListener

            crearCuentaDefinitivaEnFirebase(nombre, email, password)
        }
    }

    private fun validarFormatoCorreo(email: String): Boolean {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tvEmailError.text = "Ingresa un correo electrónico válido"
            tvEmailError.setTextColor(Color.parseColor("#EF4444"))
            tvEmailError.visibility = View.VISIBLE
            return false
        }

        val dominio = email.substringAfter("@").lowercase()
        val erroresDominio = mapOf(
            "gamil.com" to "gmail.com",
            "gmial.com" to "gmail.com",
            "gmail.co" to "gmail.com",
            "hotmal.com" to "hotmail.com",
            "outlok.com" to "outlook.com"
        )

        val sugerenciaDominio = erroresDominio[dominio]
        if (sugerenciaDominio != null) {
            val sugerencia = "${email.substringBefore("@")}@$sugerenciaDominio"
            tvEmailError.text = "¿Quisiste decir $sugerencia?"
            tvEmailError.setTextColor(Color.parseColor("#F59E0B"))
            tvEmailError.visibility = View.VISIBLE
            return false
        }

        return true
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

    internal fun emailVerified(email: String, token: String) {
        if (!etEmail.text.toString().trim().equals(email, ignoreCase = true)) return
        verifiedToken = token
        isEmailVerified = true
        layoutEmailVerified.visibility = View.VISIBLE
        btnVerify.text = "Verificado"
        btnVerify.isEnabled = false
        btnCreateAccount.isEnabled = true
        mostrarBannerPersonalizado("Verificado", "Correo verificado. Ya puedes crear tu cuenta.", R.drawable.logo)
    }

    private fun enviarYGenerarOtpTemporal(email: String) {
        lifecycleScope.launch {
            try {
                val token = EmailVerificationApi.request(email)
                if (etEmail.text.toString().trim() != email) return@launch
                VerifyBottomSheet().apply {
                    arguments = Bundle().apply { putString("arg_email", email) }
                    challengeToken = token
                }.show(supportFragmentManager, "VerifyBottomSheet")
            } catch (e: CancellationException) { throw e }
            catch (e: EmailVerificationException) {
                val title = if (e.code == "EMAIL_ALREADY_REGISTERED") "Correo ya registrado"
                    else if (e.code == "SERVICE_NOT_CONFIGURED") "Servicio no configurado" else "No se envió el código"
                if (e.code == "EMAIL_ALREADY_REGISTERED") {
                    tvEmailError.text = e.userMessage
                    tvEmailError.setTextColor(Color.parseColor("#EF4444"))
                    tvEmailError.visibility = View.VISIBLE
                }
                mostrarBannerPersonalizado(title, e.userMessage, R.drawable.logo)
            }
            catch (_: Exception) {
                mostrarBannerPersonalizado("No se pudo conectar", "No se pudo contactar con el servicio de verificación. Comprueba tu conexión e inténtalo nuevamente.", R.drawable.logo)
            } finally { btnVerify.isEnabled = !isEmailVerified }
        }
    }

    private fun crearCuentaDefinitivaEnFirebase(fullName: String, email: String, pass: String) {
        val token = verifiedToken ?: return
        if (creating) return
        creating = true
        val controls = listOf(etFullName, etEmail, etPassword, etConfirmPassword, cbTerms, btnCreateAccount, btnVerify)
        controls.forEach { it.isEnabled = false }
        lifecycleScope.launch {
            try {
                val auth = Firebase.auth
                val existing = auth.currentUser
                val user = if (existing != null && existing.email.equals(email, ignoreCase = true)) {
                    // Retry interrupted registration without creating a duplicate account.
                    existing.reauthenticate(EmailAuthProvider.getCredential(email, pass)).await()
                    existing
                } else auth.createUserWithEmailAndPassword(email, pass).await().user ?: error("Registro incompleto")
                EmailVerificationApi.complete(email, token)
                val profile = Firebase.firestore.collection("users").document(user.uid)
                val old = profile.get(com.google.firebase.firestore.Source.SERVER).await()
                if (!old.exists()) profile.set(mapOf("email" to email, "fullName" to fullName,
                    "completedRegistration" to true, "verified" to false)).await()
                // Firebase Auth emailVerified is authoritative, not the editable profile flag.
                verifiedToken = null
                etPassword.text.clear(); etConfirmPassword.text.clear()
                GuestUtils.marcarInvitado(this@RegisterActivity, false)
                startActivity(Intent(this@RegisterActivity, MasterPasswordActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                verifiedToken = null; isEmailVerified = false
                layoutEmailVerified.visibility = View.GONE
                btnVerify.text = "Verificar"
                mostrarBannerPersonalizado("Registro pendiente", "No se pudo completar. Comprueba la conexión y verifica el correo nuevamente. Si la cuenta ya existía, inicia sesión.", R.drawable.logo)
            } finally {
                creating = false
                controls.forEach { it.isEnabled = true }
                btnCreateAccount.isEnabled = isEmailVerified
                btnVerify.isEnabled = !isEmailVerified
            }
        }
    }
}
