package com.example.myapplication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    override fun onStop() {
        super.onStop()
        // Registra el momento en que cualquier pantalla pasa a segundo plano
        SessionManager.registrarSalida(this)
    }

    override fun onStart() {
        super.onStart()
        // Verifica si el tiempo de inactividad expiró al volver a abrir la app desde cualquier pantalla
        if (SessionManager.haExpiradoInactividad(this)) {
            SessionManager.limpiarSesion(this)

            // Redirige primero a tu pantalla de carga (SplashActivity)
            val intent = Intent(this, SplashActivity::class.java).apply {
                putExtra("lock_reason", "auto")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }
}