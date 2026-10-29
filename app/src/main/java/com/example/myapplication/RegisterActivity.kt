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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class RegisterActivity : AppCompatActivity() {

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

    // Vistas para los mensajes de error
    private lateinit var tvFullNameError: TextView
    private lateinit var tvEmailError: TextView
    private lateinit var tvConfirmPasswordError: TextView

    // Vistas para las barras de seguridad y el texto de ayuda
    private lateinit var bar1: View
    private lateinit var bar2: View
    private lateinit var bar3: View
    private lateinit var tvStrengthHint: TextView

    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false
    private var isEmailVerified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Inicializar vistas con los IDs del XML
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

        // TextViews de error
        tvFullNameError = findViewById(R.id.tvFullNameError)
        tvEmailError = findViewById(R.id.tvEmailError)
        tvConfirmPasswordError = findViewById(R.id.tvConfirmPasswordError)

        tvFullNameError.visibility = View.GONE
        tvEmailError.visibility = View.GONE
        tvConfirmPasswordError.visibility = View.GONE
        layoutEmailVerified.visibility = View.GONE

        // Referencias de las barras de seguridad
        val layoutPasswordStrength = findViewById<LinearLayout>(R.id.layoutPasswordStrength)
        bar1 = layoutPasswordStrength.getChildAt(0)
        bar2 = layoutPasswordStrength.getChildAt(1)
        bar3 = layoutPasswordStrength.getChildAt(2)
        tvStrengthHint = findViewById(R.id.tvStrengthHint)

        // Botón de retroceso
        btnBack.setOnClickListener {
            finish()
        }

        // Estado inicial
        resetearBarrasSeguridad()

        // Icono de ojo para la contraseña principal
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

        // Icono de ojo para confirmar contraseña
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

        // Control dinámico LETRA POR LETRA con onTextChanged
        etPassword.addTextChangedListener(object : TextWatcher {
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

        // Limpiar errores al escribir
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
                if (s.toString().isNotEmpty()) tvEmailError.visibility = View.GONE
            }
        })

        etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().isNotEmpty()) tvConfirmPasswordError.visibility = View.GONE
            }
        })

        // Botón Verificar Correo
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

            registrarUsuarioProvisional(email, password)
        }

        // Resultado del BottomSheet de verificación
        supportFragmentManager.setFragmentResultListener("verifyKey", this) { _, bundle ->
            isEmailVerified = bundle.getBoolean("emailVerified", false)
            if (isEmailVerified) {
                Toast.makeText(this, "¡Correo verificado con éxito!", Toast.LENGTH_SHORT).show()
                layoutEmailVerified.visibility = View.VISIBLE
                btnVerify.isEnabled = false
                btnVerify.text = "Verificado"
            }
        }

        // Botón Crear Cuenta final
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
                Toast.makeText(this, "Debes verificar tu correo antes de continuar", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Debes aceptar los Términos y la Política de Privacidad", Toast.LENGTH_SHORT).show()
                hayError = true
            }

            if (hayError) return@setOnClickListener

            val sharedPreferences = getSharedPreferences("IronVaultPrefs", Context.MODE_PRIVATE)
            sharedPreferences.edit().putString("user_email", email).apply()

            completarRegistroYContinuar(nombre)
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
        val rojoDebil = Color.parseColor("#EF4444")     // Rojo para contraseñas cortas o débiles
        val amarilloMedio = Color.parseColor("#F59E0B") // Amarillo para nivel medio
        val verdeFuerte = Color.parseColor("#8FC954")   // Verde original de IronVault

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

    private fun registrarUsuarioProvisional(email: String, pass: String) {
        val auth = Firebase.auth
        val db = Firebase.firestore
        val otpCode = (100000..999999).random().toString()
        val createdAt = System.currentTimeMillis()

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val userId = result.user?.uid ?: return@addOnSuccessListener
                val userData = hashMapOf(
                    "email" to email,
                    "otpCode" to otpCode,
                    "otpCreatedAt" to createdAt,
                    "verified" to false
                )

                db.collection("users").document(userId).set(userData)
                    .addOnSuccessListener {
                        enviarCorreoHttp(email, otpCode) { enviado ->
                            if (enviado) {
                                Toast.makeText(this, "Código enviado a tu correo", Toast.LENGTH_SHORT).show()
                                val bottomSheet = VerifyBottomSheet()
                                bottomSheet.show(supportFragmentManager, "VerifyBottomSheet")
                            } else {
                                Toast.makeText(this, "Error al enviar el correo HTTP", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun enviarCorreoHttp(email: String, code: String, onResult: (Boolean) -> Unit) {
        Thread {
            try {
                val baseUrl = "https://script.google.com/macros/s/AKfycbzlTcAipY61Fsg5duZgOpL1ofPg98WmMTJEeDJ1WTKXuyAeh3CRXhPCFQd6VDGUJlq0/exec"
                val token = "ironvault_secreto_2026"
                val urlStr = baseUrl + "?email=" + URLEncoder.encode(email, "UTF-8") +
                        "&code=" + URLEncoder.encode(code, "UTF-8") +
                        "&token=" + URLEncoder.encode(token, "UTF-8")

                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                val respuesta = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                runOnUiThread {
                    onResult(respuesta.trim() == "ok")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    onResult(false)
                }
            }
        }.start()
    }

    private fun completarRegistroYContinuar(fullName: String) {
        val user = Firebase.auth.currentUser
        if (user != null) {
            val db = Firebase.firestore
            val updates = hashMapOf<String, Any>(
                "fullName" to fullName,
                "completedRegistration" to true
            )

            db.collection("users").document(user.uid)
                .update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "¡Cuenta creada correctamente!", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this, MasterPasswordActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al guardar perfil: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}