package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Botón flecha atrás para volver (cierra esta pantalla y regresa a la bienvenida)
        val btnBack: ImageView? = findViewById(R.id.btnBack)
        btnBack?.setOnClickListener {
            finish()
        }

        // Botón para mostrar el BottomSheet de verificación
        val btnVerify = findViewById<AppCompatButton>(R.id.btnVerify)
        btnVerify.setOnClickListener {
            val bottomSheet = VerifyBottomSheet()
            bottomSheet.show(supportFragmentManager, "VerifyBottomSheet")
        }

        // Botón naranja "Crear cuenta"
        val btnCreateAccount = findViewById<AppCompatButton>(R.id.btnCreateAccount)
        btnCreateAccount.setOnClickListener {
            // Abre la pantalla de la contraseña maestra
            val intent = Intent(this, MasterPasswordActivity::class.java)
            startActivity(intent)
            // IMPORTANTE: Ya NO ponemos finish() aquí para que esta pantalla
            // quede guardada atrás y la flecha pueda regresar aquí.
        }
    }
}