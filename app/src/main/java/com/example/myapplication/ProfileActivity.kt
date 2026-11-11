package com.example.myapplication

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Perfil completo para cuentas con correo.
 * El invitado no usa esta pantalla: tiene su propio cuadro "Modo invitado".
 */
class ProfileActivity : BaseActivity() {

    private lateinit var tvInitial: TextView
    private lateinit var tvName: TextView
    private var nombreActual = ""
    private var emailActual = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = Firebase.auth.currentUser
        if (user == null) {
            finish()
            return
        }

        setContentView(R.layout.activity_profile)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        tvInitial = findViewById(R.id.tvProfileInitial)
        tvName = findViewById(R.id.tvProfileName)

        emailActual = user.email ?: ""
        findViewById<TextView>(R.id.tvProfileEmail).text = emailActual

        nombreActual = user.displayName ?: ""
        pintarNombre()

        // ── Tu cuenta ──
        val tvVerified = findViewById<TextView>(R.id.tvAccountVerified)
        findViewById<TextView>(R.id.tvAccountType).text = tipoDeCuenta(user)
        findViewById<TextView>(R.id.tvLastLogin).text =
            formatearFecha(user.metadata?.lastSignInTimestamp, conHora = true)
        findViewById<TextView>(R.id.tvProfileSince).text =
            formatearFecha(user.metadata?.creationTimestamp, conHora = false)

        // El nombre y la verificación del correo se guardaron en Firestore al registrarse
        Firebase.firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val fullName = doc.getString("fullName") ?: ""
                if (nombreActual.isBlank() && fullName.isNotBlank()) {
                    nombreActual = fullName
                    pintarNombre()
                }
                val verificado = user.isEmailVerified || doc.getBoolean("verified") == true
                pintarVerificado(tvVerified, verificado)
            }
            .addOnFailureListener {
                pintarVerificado(tvVerified, user.isEmailVerified)
            }

        // ── Acciones ──
        findViewById<TextView>(R.id.tvEditName).setOnClickListener { mostrarDialogoNombre(user) }

        findViewById<AppCompatButton>(R.id.btnChangePassword).setOnClickListener {
            mostrarConfirmacion(
                titulo = "Cambiar contraseña",
                mensaje = "Te enviaremos un correo a $emailActual con un enlace para crear una contraseña nueva.",
                textoConfirmar = "Enviar correo"
            ) { enviarCorreoCambioPassword() }
        }

        findViewById<AppCompatButton>(R.id.btnLogout).setOnClickListener {
            mostrarConfirmacion(
                titulo = "Cerrar sesión",
                mensaje = "Se cerrará tu sesión y se quitarán las contraseñas guardadas en este dispositivo. Seguirán en tu cuenta.",
                textoConfirmar = "Cerrar sesión"
            ) { cerrarSesion() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Firebase.auth.currentUser != null) pintarResumen()
    }

    // ───────────────────────── Cabecera ─────────────────────────

    private fun pintarNombre() {
        // Inicial: del nombre si existe, si no del correo
        val base = if (nombreActual.isNotBlank()) nombreActual else emailActual
        tvInitial.text = if (base.isNotEmpty()) base.substring(0, 1).uppercase() else "?"
        tvName.text = if (nombreActual.isNotBlank()) nombreActual else "Sin nombre"
    }

    private fun pintarVerificado(tv: TextView, verificado: Boolean) {
        tv.text = if (verificado) "Sí" else "No"
        tv.setTextColor(Color.parseColor(if (verificado) "#71C77D" else "#E26B6B"))
    }

    private fun tipoDeCuenta(user: FirebaseUser): String {
        val proveedor = user.providerData.map { it.providerId }
        return when {
            proveedor.contains("password") -> "Correo y contraseña"
            proveedor.contains("google.com") -> "Google"
            else -> "Cuenta IronVault"
        }
    }

    private fun formatearFecha(millis: Long?, conHora: Boolean): String {
        if (millis == null || millis <= 0L) return "—"
        val patron = if (conHora) "d MMM yyyy, HH:mm" else "d 'de' MMMM 'de' yyyy"
        return SimpleDateFormat(patron, Locale("es")).format(Date(millis))
    }

    // ───────────────────────── Resumen de la bóveda ─────────────────────────

    private fun pintarResumen() {
        val lista = VaultRepository.credentialList
        val total = lista.size

        val seguras = lista.count {
            it.strengthStatus.contains("Segura", ignoreCase = true) ||
                    it.strengthStatus.contains("Alta", ignoreCase = true)
        }
        val debiles = lista.count { it.strengthStatus.contains("Débil", ignoreCase = true) }
        val medias = lista.count { it.strengthStatus.contains("Media", ignoreCase = true) }
        val repetidas = lista.count { it.strengthStatus.contains("Repetida", ignoreCase = true) }

        findViewById<TextView>(R.id.tvProfileTotal).text = total.toString()
        findViewById<TextView>(R.id.tvProfileSecure).text = seguras.toString()
        findViewById<TextView>(R.id.tvProfileWeak).text = debiles.toString()
        findViewById<TextView>(R.id.tvProfileMedium).text = medias.toString()
        findViewById<TextView>(R.id.tvProfileRepeated).text = repetidas.toString()

        pintarPuntaje(seguras, total)
        pintarAvisos(lista, repetidas)
        pintarCategorias(lista)
    }

    private fun pintarPuntaje(seguras: Int, total: Int) {
        val tvPercent = findViewById<TextView>(R.id.tvScorePercent)
        val tvDetail = findViewById<TextView>(R.id.tvScoreDetail)
        val bar = findViewById<ProgressBar>(R.id.progressScore)

        if (total == 0) {
            tvPercent.text = "— %"
            tvPercent.setTextColor(Color.parseColor("#9A9A9A"))
            tvDetail.text = "Sin contraseñas aún"
            bar.progress = 0
            return
        }

        val porcentaje = seguras * 100 / total
        // Verde desde 70 %, rojo por debajo de 40 %, amarillo en medio
        val color = Color.parseColor(
            when {
                porcentaje >= 70 -> "#71C77D"
                porcentaje < 40 -> "#E26B6B"
                else -> "#E5C158"
            }
        )

        tvPercent.text = "$porcentaje %"
        tvPercent.setTextColor(color)
        tvDetail.text = "$seguras de $total contraseñas seguras"
        bar.progress = porcentaje
        bar.progressTintList = ColorStateList.valueOf(color)
    }

    private fun pintarAvisos(lista: List<Credential>, repetidas: Int) {
        val verde = Color.parseColor("#71C77D")
        val ambar = Color.parseColor("#E5C158")

        // Repetidas
        val tvRepetidas = findViewById<TextView>(R.id.tvAlertRepeated)
        if (repetidas > 0) {
            tvRepetidas.text = if (repetidas == 1) {
                "Tienes 1 contraseña repetida, cámbiala."
            } else {
                "Tienes $repetidas contraseñas repetidas, cámbialas."
            }
            tvRepetidas.setTextColor(ambar)
        } else {
            tvRepetidas.text = "No tienes contraseñas repetidas."
            tvRepetidas.setTextColor(verde)
        }

        // Viejas (más de 90 días). Solo cuentan las que tienen fecha.
        val limite = 90L * 24L * 60L * 60L * 1000L
        val ahora = System.currentTimeMillis()
        val conFecha = lista.filter { it.updatedAt > 0L }
        val viejas = conFecha.count { ahora - it.updatedAt > limite }
        val sinFecha = lista.size - conFecha.size

        val tvViejas = findViewById<TextView>(R.id.tvAlertOld)
        var textoViejas = if (viejas > 0) {
            if (viejas == 1) "1 contraseña sin cambiar hace más de 90 días."
            else "$viejas contraseñas sin cambiar hace más de 90 días."
        } else {
            "Ninguna contraseña tiene más de 90 días."
        }
        if (sinFecha > 0) {
            textoViejas += " ($sinFecha sin fecha de registro: edítalas una vez para medirlas.)"
        }
        tvViejas.text = textoViejas
        tvViejas.setTextColor(if (viejas > 0) ambar else verde)

        // Última modificación
        val tvUltima = findViewById<TextView>(R.id.tvAlertLast)
        val ultima = lista.maxOfOrNull { it.updatedAt } ?: 0L
        if (ultima > 0L) {
            tvUltima.text = "Última contraseña modificada " +
                    TimeUtils.tiempoRelativo(ultima).removePrefix("Actualizada ") + "."
        } else {
            tvUltima.text = "Aún no hay fecha de modificación registrada."
        }
        tvUltima.setTextColor(Color.parseColor("#9A9A9A"))
    }

    private fun pintarCategorias(lista: List<Credential>) {
        val contenedor = findViewById<LinearLayout>(R.id.containerCategories)
        contenedor.removeAllViews()

        if (lista.isEmpty()) {
            contenedor.addView(TextView(this).apply {
                text = "Aún no tienes contraseñas guardadas."
                setTextColor(Color.parseColor("#9A9A9A"))
                textSize = 13f
                setPadding(0, dp(8), 0, dp(4))
            })
            return
        }

        val porCategoria = lista
            .groupingBy { if (it.category.isBlank()) "Sin categoría" else it.category }
            .eachCount()
            .entries
            .sortedByDescending { it.value }

        for ((nombre, cantidad) in porCategoria) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }

            // Icono con el color de la categoría
            val cajaIcono = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(colorFondoParaCategoria(nombre))
                }
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            }
            cajaIcono.addView(ImageView(this).apply {
                setImageResource(iconoParaCategoria(nombre))
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            })

            val tvNombre = TextView(this).apply {
                text = nombre
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                }
            }

            val tvCantidad = TextView(this).apply {
                text = cantidad.toString()
                setTextColor(Color.parseColor("#E8873E"))
                textSize = 14f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }

            fila.addView(cajaIcono)
            fila.addView(tvNombre)
            fila.addView(tvCantidad)
            contenedor.addView(fila)
        }
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

    // Mismos iconos y colores que el selector de categorías
    private fun iconoParaCategoria(category: String): Int {
        return when {
            category.contains("Correo", ignoreCase = true) -> R.drawable.ic_cat_mail
            category.contains("Social", ignoreCase = true) ||
                    category.contains("Redes", ignoreCase = true) -> R.drawable.ic_cat_social
            category.contains("Videojuego", ignoreCase = true) -> R.drawable.ic_cat_games
            category.contains("Banc", ignoreCase = true) -> R.drawable.ic_cat_bank
            category.contains("Compra", ignoreCase = true) -> R.drawable.ic_cat_shop
            category.contains("Educaci", ignoreCase = true) -> R.drawable.ic_cat_edu
            else -> R.drawable.ic_cat_lupa
        }
    }

    private fun colorFondoParaCategoria(category: String): Int {
        return when {
            category.contains("Correo", ignoreCase = true) -> getColor(R.color.cat_mail_bg)
            category.contains("Social", ignoreCase = true) ||
                    category.contains("Redes", ignoreCase = true) -> getColor(R.color.cat_social_bg)
            category.contains("Videojuego", ignoreCase = true) -> getColor(R.color.cat_games_bg)
            category.contains("Banc", ignoreCase = true) -> getColor(R.color.cat_bank_bg)
            category.contains("Compra", ignoreCase = true) -> getColor(R.color.cat_shop_bg)
            category.contains("Educaci", ignoreCase = true) -> getColor(R.color.cat_edu_bg)
            else -> getColor(R.color.cat_other_bg)
        }
    }

    // ───────────────────────── Modales ─────────────────────────

    private fun crearDialogo(layout: Int): Dialog {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(layout)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return dialog
    }

    private fun mostrarConfirmacion(
        titulo: String,
        mensaje: String,
        textoConfirmar: String,
        alConfirmar: () -> Unit
    ) {
        val dialog = crearDialogo(R.layout.dialog_confirm_action)
        dialog.findViewById<TextView>(R.id.tvActionTitle).text = titulo
        dialog.findViewById<TextView>(R.id.tvActionMessage).text = mensaje
        dialog.findViewById<AppCompatButton>(R.id.btnActionConfirm).apply {
            text = textoConfirmar
            setOnClickListener {
                dialog.dismiss()
                alConfirmar()
            }
        }
        dialog.findViewById<TextView>(R.id.btnActionCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun mostrarDialogoNombre(user: FirebaseUser) {
        val dialog = crearDialogo(R.layout.dialog_edit_name)
        val etNombre = dialog.findViewById<EditText>(R.id.etDialogName)
        etNombre.setText(nombreActual)
        etNombre.setSelection(etNombre.text.length)

        dialog.findViewById<AppCompatButton>(R.id.btnNameSave).setOnClickListener {
            val nuevo = etNombre.text.toString().trim()
            if (nuevo.isEmpty()) {
                mostrarBannerPersonalizado(
                    titulo = "Campo requerido",
                    mensaje = "Escribe tu nombre",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }
            dialog.dismiss()
            guardarNombre(user, nuevo)
        }
        dialog.findViewById<TextView>(R.id.btnNameCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ───────────────────────── Acciones ─────────────────────────

    private fun guardarNombre(user: FirebaseUser, nuevo: String) {
        val cambio = userProfileChangeRequest { displayName = nuevo }

        user.updateProfile(cambio)
            .addOnSuccessListener {
                Firebase.firestore.collection("users").document(user.uid)
                    .set(hashMapOf("fullName" to nuevo), SetOptions.merge())
                    .addOnSuccessListener {
                        nombreActual = nuevo
                        pintarNombre()
                        mostrarBannerPersonalizado(
                            titulo = "Nombre actualizado",
                            mensaje = "Tu nombre se guardó con éxito",
                            iconoResId = R.drawable.logo
                        )
                    }
                    .addOnFailureListener {
                        mostrarBannerPersonalizado(
                            titulo = "Error de sincronización",
                            mensaje = "No se pudo guardar el nombre en la nube",
                            iconoResId = R.drawable.logo
                        )
                    }
            }
            .addOnFailureListener {
                mostrarBannerPersonalizado(
                    titulo = "Error",
                    mensaje = "No se pudo actualizar el nombre",
                    iconoResId = R.drawable.logo
                )
            }
    }

    private fun enviarCorreoCambioPassword() {
        if (emailActual.isEmpty()) return
        Firebase.auth.sendPasswordResetEmail(emailActual)
            .addOnSuccessListener {
                mostrarBannerPersonalizado(
                    titulo = "Correo enviado",
                    mensaje = "Revisa $emailActual para cambiar tu contraseña",
                    iconoResId = R.drawable.logo
                )
            }
            .addOnFailureListener {
                mostrarBannerPersonalizado(
                    titulo = "Error",
                    mensaje = "No se pudo enviar el correo. Revisa tu conexión.",
                    iconoResId = R.drawable.logo
                )
            }
    }

    private fun cerrarSesion() {
        Firebase.auth.signOut()

        // La siguiente persona no debe ver estas contraseñas
        VaultRepository.credentialList.clear()
        getSharedPreferences("IronVaultPermanentCache", MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences("settings_prefs", MODE_PRIVATE)
            .edit().putBoolean("is_logged_in", false).apply()

        // No tocamos GuestStorage: si en este dispositivo hubo un invitado, sus datos siguen ahí
        GuestUtils.marcarInvitado(this, false)
        SessionManager.limpiarSesion(this)

        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}