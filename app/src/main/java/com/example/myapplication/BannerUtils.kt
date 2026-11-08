package com.example.myapplication

import android.animation.ValueAnimator
import android.app.Activity
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

// Función de extensión para cualquier Activity de la app
fun Activity.mostrarBannerPersonalizado(titulo: String, mensaje: String, iconoResId: Int = R.drawable.logo) {
    val rootView = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return

    // ── EVITAR ACUMULACIÓN: Si ya hay un banner visible, lo removemos al instante ──
    val bannerAnterior = rootView.findViewWithTag<View>("BANNER_FLOTANTE_TAG")
    if (bannerAnterior != null) {
        rootView.removeView(bannerAnterior)
    }

    // Inflamos la vista personalizada del banner
    val customView = LayoutInflater.from(this).inflate(R.layout.layout_custom_banner, rootView, false)
    customView.tag = "BANNER_FLOTANTE_TAG" // Le asignamos una etiqueta única

    customView.findViewById<TextView>(R.id.bannerTitle).text = titulo
    customView.findViewById<TextView>(R.id.bannerMessage).text = mensaje
    customView.findViewById<ImageView>(R.id.bannerIcon).setImageResource(iconoResId)

    // Animación fluida de la línea naranja de izquierda a derecha
    val orangeLine = customView.findViewById<View>(R.id.bannerLine)
    orangeLine.post {
        orangeLine.pivotX = 0f
        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 2750L
            addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                orangeLine.scaleX = scale
            }
        }
        animator.start()
    }

    // Configurar los parámetros para que flote fijo en la parte superior
    val params = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = Gravity.TOP
        topMargin = 120 // Espacio para evitar la barra de estado / notch
        marginStart = 32
        marginEnd = 32
    }

    customView.layoutParams = params
    customView.elevation = 20f // Sombreado elevado para que flote por encima de todo
    customView.alpha = 0f
    customView.translationY = -50f

    // Añadimos la vista directamente al contenedor raíz
    rootView.addView(customView)

    // Animación de entrada suave desde arriba
    customView.animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(300)
        .start()

    var isClosing = false

    // Función interna para remover el banner de forma limpia con animación de salida
    fun cerrarBanner() {
        if (isClosing) return
        isClosing = true
        if (customView.parent != null) {
            customView.animate()
                .alpha(0f)
                .translationY(-50f)
                .setDuration(300)
                .withEndAction {
                    if (customView.parent != null) {
                        rootView.removeView(customView)
                    }
                }
                .start()
        }
    }

    // Configurar el botón "X" para cerrar el banner manualmente al instante
    val btnClose = customView.findViewById<ImageView>(R.id.btnCloseBanner)
    btnClose?.setOnClickListener {
        cerrarBanner()
    }

    // Ocultar y remover el banner automáticamente después de 3 segundos
    customView.postDelayed({
        cerrarBanner()
    }, 3000L)
}