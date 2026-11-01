package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import kotlin.random.Random

class VerifyCodeActivity : AppCompatActivity() {

    private lateinit var digitFields: Array<EditText>
    private var userEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_code)

        // Recibimos el correo de la pantalla anterior
        userEmail = intent.getStringExtra("USER_EMAIL") ?: "correo@example.com"

        // Inicializamos las 6 casillas del código OTP
        digitFields = arrayOf(
            findViewById(R.id.etDigit1),
            findViewById(R.id.etDigit2),
            findViewById(R.id.etDigit3),
            findViewById(R.id.etDigit4),
            findViewById(R.id.etDigit5),
            findViewById(R.id.etDigit6)
        )

        setupOtpListeners()
        startResendTimer()

        // Botón de verificación
        findViewById<View>(R.id.btnVerifyCode).setOnClickListener {
            val code = digitFields.joinToString("") { it.text.toString() }
            if (code.length == digitFields.size) {
                Toast.makeText(this, "¡Código verificado con éxito!", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, ResetPasswordActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Por favor ingresa todos los dígitos completos", Toast.LENGTH_SHORT).show()
            }
        }

        // Botón de reenvío de código
        findViewById<TextView>(R.id.tvResendCode).setOnClickListener {
            val nuevoCodigo = String.format("%06d", Random.nextInt(1000000))
            enviarCorreoVerificacion(userEmail, nuevoCodigo)

            Toast.makeText(this, "Nuevo código reenviado con éxito", Toast.LENGTH_SHORT).show()
            startResendTimer()
        }

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun setupOtpListeners() {
        for (i in digitFields.indices) {
            val currentField = digitFields[i]

            // Cambio de borde dinámico según el foco
            currentField.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    currentField.setBackgroundResource(R.drawable.bg_security_badge_active)
                } else {
                    currentField.setBackgroundResource(R.drawable.bg_security_badge)
                }
            }

            // 1. Salto automático al siguiente campo al escribir un dígito
            currentField.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val text = s.toString()
                    if (text.isNotEmpty() && i < digitFields.size - 1) {
                        digitFields[i + 1].requestFocus()
                        digitFields[i + 1].setSelection(digitFields[i + 1].text.length)
                    }
                }
            })

            // 2. Regresar al campo anterior y limpiar al presionar Backspace en un campo vacío
            currentField.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    if (currentField.text.isEmpty() && i > 0) {
                        digitFields[i - 1].requestFocus()
                        digitFields[i - 1].setText("")
                        digitFields[i - 1].setSelection(digitFields[i - 1].text.length)
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }
    }

    private fun startResendTimer() {
        object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val timeFormatted = String.format("Reenviar en 0:%02d", seconds)
                val tvResend = findViewById<TextView>(R.id.tvResendCode)
                tvResend.text = timeFormatted
                tvResend.isEnabled = false
                tvResend.alpha = 0.5f
            }

            override fun onFinish() {
                val tvResend = findViewById<TextView>(R.id.tvResendCode)
                tvResend.text = "¿No recibiste el código? Reenviar"
                tvResend.isEnabled = true
                tvResend.alpha = 1.0f
            }
        }.start()
    }

    private fun enviarCorreoVerificacion(destinatarioEmail: String, codigoOtp: String) {
        // NUEVA URL pública y correcta del proyecto IronVaultRecuperacion
        val webAppUrl = "https://script.google.com/macros/s/AKfycbxC_bCo3hVevwd2GkWH0kz_7OCbKYlHhnR049EDMTk4oOr4Z7hERLTbWksRHryT_iG4JQ/exec"

        val client = OkHttpClient()

        val urlBuilder = webAppUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("email", destinatarioEmail)
            ?.addQueryParameter("code", codigoOtp)
            ?.addQueryParameter("token", "ironvault_secreto_2026")
            ?.build()

        val urlFinal = urlBuilder?.toString() ?: return

        val request = Request.Builder()
            .url(urlFinal)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                Log.e("IronVaultEmail", "Error de conexión: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                if (response.isSuccessful) {
                    Log.i("IronVaultEmail", "Éxito al reenviar: $responseData")
                } else {
                    Log.e("IronVaultEmail", "Error del servidor al reenviar: $responseData")
                }
            }
        })
    }
}