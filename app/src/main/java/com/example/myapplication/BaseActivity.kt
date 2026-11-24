package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

open class BaseActivity : AppCompatActivity() {

    companion object {
        // Cuántas pantallas de la app están visibles ahora mismo.
        // Si llega a 0, la app pasó a segundo plano (o se cerró del todo).
        private var pantallasIniciadas = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        aplicarInsetsAdaptables()
    }

    private fun aplicarInsetsAdaptables() {
        // MainActivity ya ajusta su R.id.scrollContent, así que evitamos sumar doble margen ahí
        if (this is MainActivity) return

        val contentFrame = findViewById<ViewGroup>(android.R.id.content)
        val rootView = contentFrame?.getChildAt(0) ?: return

        val extraTop = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            12f,
            resources.displayMetrics
        ).toInt()

        val initialTop = rootView.paddingTop
        val initialBottom = rootView.paddingBottom
        val initialLeft = rootView.paddingLeft
        val initialRight = rootView.paddingRight

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(systemBars.bottom, imeInsets.bottom)

            view.setPadding(
                initialLeft + systemBars.left,
                initialTop + systemBars.top + extraTop,
                initialRight + systemBars.right,
                initialBottom + bottomInset
            )
            insets
        }
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