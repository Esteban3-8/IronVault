package com.example.myapplication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    companion object {
        // Cuántas pantallas de la app están visibles ahora mismo.
        // Si llega a 0, la app pasó a segundo plano (o se cerró del todo).
        private var pantallasIniciadas = 0
    }

    override fun onStart() {
        super.onStart()

        // Si no había ninguna pantalla visible, la app viene de segundo plano.
        // Si ya había una, solo estamos navegando entre pantallas de la app.
        val veniaDeSegundoPlano = pantallasIniciadas == 0
        pantallasIniciadas++

        // Invitado: no tiene clave maestra, así que el bloqueo por inactividad no aplica
        if (GuestUtils.esInvitado(this)) {
            SessionManager.limpiarSesion(this)
            return
        }

        // El bloqueo por inactividad solo se revisa al VOLVER a la app desde segundo plano,
        // no cada vez que se cambia de pantalla dentro de la app.
        if (veniaDeSegundoPlano && SessionManager.haExpiradoInactividad(this)) {
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

    override fun onStop() {
        super.onStop()

        pantallasIniciadas--
        if (pantallasIniciadas <= 0) {
            pantallasIniciadas = 0
            // La app salió de verdad a segundo plano: aquí empieza a contar la inactividad
            SessionManager.registrarSalida(this)
        }
    }
}