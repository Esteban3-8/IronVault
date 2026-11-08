package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import kotlin.random.Random

class ForgotPasswordActivity : BaseActivity() { // 👈 Hereda de BaseActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val btnContinue = findViewById<AppCompatButton>(R.id.btnContinue)
        val tvLoginRedirect = findViewById<TextView>(R.id.tvLoginRedirect)

        btnBack?.setOnClickListener { finish() }
        tvLoginRedirect?.setOnClickListener { finish() }

        btnContinue?.setOnClickListener {
            val email = etEmail?.text.toString().trim()

            if (email.isEmpty()) {
                mostrarBannerPersonalizado(
                    titulo = "Campo requerido",
                    mensaje = "Por favor ingresa tu correo electrónico",
                    iconoResId = R.drawable.logo
                )
                etEmail?.requestFocus()
                return@setOnClickListener
            }

            btnContinue.isEnabled = false

            // 1. Generamos el código aleatorio de 6 dígitos para la verificación OTP
            val codigoOtp = String.format("%06d", Random.nextInt(1000000))

            mostrarBannerPersonalizado(
                titulo = "Enviando...",
                mensaje = "Enviando código de verificación...",
                iconoResId = R.drawable.logo
            )

            // 2. Ejecutamos la petición asegurando la transición limpia
            enviarCorreoAppsScript(email, codigoOtp) { exito ->
                runOnUiThread {
                    btnContinue.isEnabled = true

                    val intent = Intent(this, VerifyCodeActivity::class.java).apply {
                        putExtra("USER_EMAIL", email)
                        putExtra("OTP_CODE", codigoOtp)
                    }
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    private fun enviarCorreoAppsScript(destinatarioEmail: String, codigoOtp: String, callback: (Boolean) -> Unit) {
        // URL específica del proyecto de recuperación de contraseña en Google Apps Script
        val webAppUrl = "https://script.google.com/macros/s/AKfycbxC_bCo3hVevwd2GkWH0kz_7OCbKYlHhnR049EDMTk4oOr4Z7hERLTbWksRHryT_iG4JQ/exec"

        val client = OkHttpClient()

        val urlBuilder = webAppUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("email", destinatarioEmail)
            ?.addQueryParameter("code", codigoOtp)
            ?.addQueryParameter("token", "ironvault_secreto_2026")
            ?.build()

        val urlFinal = urlBuilder?.toString() ?: run {
            callback(false)
            return
        }

        val request = Request.Builder()
            .url(urlFinal)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                Log.e("IronVaultEmail", "Error al enviar correo: ${e.message}")
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                if (response.isSuccessful) {
                    Log.i("IronVaultEmail", "Correo enviado con éxito: $responseData")
                    callback(true)
                } else {
                    Log.e("IronVaultEmail", "Error del servidor Apps Script: $responseData")
                    callback(false)
                }
            }
        })
    }
}