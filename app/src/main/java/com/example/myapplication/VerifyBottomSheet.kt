package com.example.myapplication

import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class VerifyBottomSheet : BottomSheetDialogFragment() {

    private var countDownTimer: CountDownTimer? = null
    private lateinit var tvTimer: TextView
    private var isResendEnabled = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_verify, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvSubTitle = view.findViewById<TextView>(R.id.tvSubTitle)
        tvTimer = view.findViewById<TextView>(R.id.tvTimer)

        // 1. Mostrar el correo real del usuario enmascarado arriba
        mostrarCorreoEnmascarado(tvSubTitle)

        // 2. Iniciar la cuenta regresiva en 1 minuto y 30 segundos (90,000 ms)
        iniciarCronometro(90000)

        // 3. Hacer clic en el texto del temporizador para reenviar cuando termine
        tvTimer.setOnClickListener {
            if (isResendEnabled) {
                reenviarNuevoCodigo()
            }
        }

        // Referencias a los 6 EditText del diseño XML
        val editTexts = arrayOf(
            view.findViewById<EditText>(R.id.etDigit1),
            view.findViewById<EditText>(R.id.etDigit2),
            view.findViewById<EditText>(R.id.etDigit3),
            view.findViewById<EditText>(R.id.etDigit4),
            view.findViewById<EditText>(R.id.etDigit5),
            view.findViewById<EditText>(R.id.etDigit6)
        )

        for (i in editTexts.indices) {
            editTexts[i].setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    editTexts[i].setBackgroundResource(R.drawable.bg_security_badge_active)
                } else {
                    editTexts[i].setBackgroundResource(R.drawable.bg_security_badge)
                }
            }

            editTexts[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (!s.isNullOrEmpty() && i < editTexts.size - 1) {
                        editTexts[i + 1].requestFocus()
                        editTexts[i + 1].setSelection(editTexts[i + 1].text.length)
                    }
                }
            })

            editTexts[i].setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    if (editTexts[i].text.isEmpty() && i > 0) {
                        editTexts[i - 1].requestFocus()
                        editTexts[i - 1].setText("")
                        editTexts[i - 1].setSelection(editTexts[i - 1].text.length)
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }

        editTexts.last().addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) {
                    val codigoIngresado = editTexts.joinToString("") { it.text.toString() }
                    if (codigoIngresado.length == 6) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            validarCodigoOTPConFirestore(codigoIngresado)
                        }, 400)
                    }
                }
            }
        })
    }

    private fun mostrarCorreoEnmascarado(tvSubTitle: TextView?) {
        val user = Firebase.auth.currentUser
        val email = user?.email ?: ""

        if (email.contains("@")) {
            val partes = email.split("@")
            val usuario = partes[0]
            val dominio = partes[1]

            val enmascarado = if (usuario.length > 2) {
                usuario.take(2) + "***"
            } else {
                "***"
            }
            tvSubTitle?.text = "Código enviado a $enmascarado@$dominio"
        } else {
            tvSubTitle?.text = "Código enviado a tu correo registrado"
        }
    }

    private fun iniciarCronometro(tiempoMillis: Long) {
        isResendEnabled = false
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(tiempoMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val segundosTotales = millisUntilFinished / 1000
                val minutos = segundosTotales / 60
                val segundos = segundosTotales % 60
                tvTimer.text = String.format("¿No recibiste el código? Reenviar en %02d:%02d", minutos, segundos)
            }

            override fun onFinish() {
                isResendEnabled = true
                tvTimer.text = "¿No recibiste el código? Reenviar ahora"
            }
        }.start()
    }

    private fun reenviarNuevoCodigo() {
        val userId = Firebase.auth.currentUser?.uid ?: return
        val email = Firebase.auth.currentUser?.email ?: return

        val nuevoOtp = (100000..999999).random().toString()
        val nuevoTiempo = System.currentTimeMillis()

        val updates = hashMapOf<String, Any>(
            "otpCode" to nuevoOtp,
            "otpCreatedAt" to nuevoTiempo
        )

        Firebase.firestore.collection("users").document(userId)
            .update(updates)
            .addOnSuccessListener {
                enviarCorreoHttp(email, nuevoOtp) { enviado ->
                    if (enviado) {
                        Toast.makeText(context, "Nuevo código enviado a tu correo", Toast.LENGTH_SHORT).show()
                        iniciarCronometro(180000)
                    } else {
                        Toast.makeText(context, "Error al enviar el correo", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al actualizar código", Toast.LENGTH_SHORT).show()
            }
    }

    private fun enviarCorreoHttp(email: String, code: String, onResult: (Boolean) -> Unit) {
        Thread {
            try {
                val baseUrl = "https://script.google.com/macros/s/AKfycbzlTcAipY61Fsg5duZgOpL1ofPg98WmMTJEeDJ1WTKXuyAeh3CRXhPCFQd6VDGUJlq0/exec"
                val token = "ironvault_secreto_2026"

                val urlStr = baseUrl +
                        "?email=" + URLEncoder.encode(email, "UTF-8") +
                        "&code=" + URLEncoder.encode(code, "UTF-8") +
                        "&token=" + URLEncoder.encode(token, "UTF-8")

                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                val respuesta = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                activity?.runOnUiThread {
                    onResult(respuesta.trim() == "ok")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                activity?.runOnUiThread {
                    onResult(false)
                }
            }
        }.start()
    }

    private fun validarCodigoOTPConFirestore(codigoIngresado: String) {
        val userId = Firebase.auth.currentUser?.uid ?: return
        val db = Firebase.firestore

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val otpGuardado = document.getString("otpCode")
                    val otpCreatedAt = document.getLong("otpCreatedAt") ?: 0L
                    val tiempoActual = System.currentTimeMillis()
                    val limiteExpiracion = 5 * 60 * 1000 // 5 minutos

                    if (tiempoActual - otpCreatedAt > limiteExpiracion) {
                        Toast.makeText(context, "El código ha expirado. Solicita uno nuevo.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    if (codigoIngresado == otpGuardado) {
                        Toast.makeText(context, "¡Cuenta verificada exitosamente!", Toast.LENGTH_SHORT).show()

                        parentFragmentManager.setFragmentResult("verifyKey", Bundle().apply {
                            putBoolean("emailVerified", true)
                        })

                        dismiss()
                    } else {
                        Toast.makeText(context, "El código ingresado es incorrecto", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error al verificar código: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
    }
}