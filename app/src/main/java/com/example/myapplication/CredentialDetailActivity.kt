package com.example.myapplication

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class CredentialDetailActivity : BaseActivity() {

    private lateinit var etPassword: EditText
    private lateinit var btnTogglePassword: ImageView
    private var isPasswordVisible = false
    private var ultimaPassword = ""

    // Vistas de fortaleza para controlarlas en tiempo real
    private lateinit var tvStatusText: TextView
    private lateinit var vStatusDot: View
    private lateinit var layoutStatusBadge: LinearLayout
    private lateinit var tvStrengthLabel: TextView
    private lateinit var bar1: View
    private lateinit var bar2: View
    private lateinit var bar3: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credential_detail)

        // 1. Referencias de las vistas
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val tvInitial = findViewById<TextView>(R.id.tvInitial)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tvSubtitleCategory = findViewById<TextView>(R.id.tvSubtitleCategory)
        val etUser = findViewById<EditText>(R.id.etUser)
        etPassword = findViewById(R.id.etPassword)
        val etUrl = findViewById<EditText>(R.id.etUrl)
        val etNotes = findViewById<EditText>(R.id.etNotes)
        btnTogglePassword = findViewById(R.id.btnTogglePassword)

        tvStatusText = findViewById(R.id.tvStatusText)
        vStatusDot = findViewById(R.id.vStatusDot)
        layoutStatusBadge = findViewById(R.id.layoutStatusBadge)
        val tvCategoryName = findViewById<TextView>(R.id.tvCategoryName)
        val ivCategoryIcon = findViewById<ImageView>(R.id.ivCategoryIcon)

        tvStrengthLabel = findViewById(R.id.tvStrengthLabel)
        bar1 = findViewById(R.id.bar1)
        bar2 = findViewById(R.id.bar2)
        bar3 = findViewById(R.id.bar3)

        // 2. Recibir los datos reales
        val title = intent.getStringExtra("serviceName") ?: ""
        val username = intent.getStringExtra("userOrEmail") ?: ""
        val password = intent.getStringExtra("password") ?: ""
        val url = intent.getStringExtra("url") ?: ""
        val notes = intent.getStringExtra("notes") ?: ""
        val initialStatus = intent.getStringExtra("strengthStatus") ?: "Segura"
        val updatedAt = intent.getLongExtra("updatedAt", 0L)

        // La categoría puede llegar con distintas claves; se usa la primera que no esté vacía
        val rawCategory = intent.getStringExtra("category")
            ?: intent.getStringExtra("categoria")
            ?: intent.getStringExtra("categoryName")
            ?: ""

        android.util.Log.d("CAT_DEBUG", "Detalle recibe category = '$rawCategory'")

        // Si no llega, se muestra "Sin categoría"
        val category = if (rawCategory.isNotBlank()) rawCategory else "Sin categoría"

        // 3. Botón de retroceso
        btnBack?.setOnClickListener { finish() }

        // 4. Botones inferiores (Editar y Eliminar)
        val btnEdit = findViewById<AppCompatButton>(R.id.btnEdit)
        btnEdit?.setOnClickListener {
            val editIntent = Intent(this, EditCredentialActivity::class.java).apply {
                putExtra("serviceName", title)
                putExtra("userOrEmail", username)
                putExtra("password", etPassword.text.toString())
                putExtra("url", url)
                putExtra("notes", notes)
                putExtra("category", category)
                putExtra("updatedAt", updatedAt)
            }
            startActivity(editIntent)
        }

        val btnDelete = findViewById<AppCompatButton>(R.id.btnDelete)
        btnDelete?.setOnClickListener {
            mostrarDialogoEliminar(title) {
                eliminarCredencial(title, username, password)
            }
        }

        // 5. Botones de copia al portapapeles
        val btnCopyUser = findViewById<ImageView>(R.id.btnCopyUser)
        btnCopyUser?.setOnClickListener {
            val userText = etUser.text.toString()
            if (userText.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Usuario copiado", userText))
                mostrarBannerPersonalizado(
                    titulo = "Portapapeles",
                    mensaje = "Usuario copiado al portapapeles",
                    iconoResId = R.drawable.logo
                )
            }
        }

        val btnCopyPassword = findViewById<ImageView>(R.id.btnCopyPassword)
        btnCopyPassword?.setOnClickListener {
            val passwordText = etPassword.text.toString()
            if (passwordText.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Contraseña copiada", passwordText))
                mostrarBannerPersonalizado(
                    titulo = "Portapapeles",
                    mensaje = "Contraseña copiada al portapapeles",
                    iconoResId = R.drawable.logo
                )
            }
        }

        // 6. Pintar la información inicial en las vistas
        tvInitial?.text = title.firstOrNull()?.uppercase() ?: "?"
        tvTitle?.text = title
        findViewById<TextView>(R.id.tvUpdatedAt)?.text = TimeUtils.tiempoRelativo(updatedAt)
        tvSubtitleCategory?.text = category
        etUser.setText(username)
        etPassword.setText(password)
        ultimaPassword = password
        etUrl.setText(url)
        etNotes.setText(notes)

        // 7. Categoría seleccionada: texto + icono
        tvCategoryName?.text = category
        ivCategoryIcon?.setImageResource(iconoParaCategoria(category))

        // Fondo y color de texto según la categoría (igual que en el selector de categorías)
        val layoutCategoryBox = findViewById<LinearLayout>(R.id.layoutCategoryBox)
        val fondoCategoria = colorFondoParaCategoria(category)
        val fondoActual = layoutCategoryBox?.background?.mutate()
        if (fondoActual is GradientDrawable) {
            fondoActual.setColor(fondoCategoria)
        } else {
            fondoActual?.setTint(fondoCategoria)
        }
        tvCategoryName?.setTextColor(Color.WHITE)

        // Evaluar la fortaleza inicial según lo que traiga
        actualizarFortalezaUI(password, initialStatus)

        // 8. Detectar cambios en tiempo real en la contraseña
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val actual = s.toString()
                // Mostrar/ocultar la contraseña (el ojo) vuelve a disparar este listener
                // sin que el texto cambie: en ese caso no se recalcula nada.
                if (actual == ultimaPassword) return
                ultimaPassword = actual
                calcularYActualizarSeguridad(actual)
            }
        })

        // 9. Contraseña oculta por defecto
        etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        etPassword.setSelection(etPassword.text.length)
        btnTogglePassword.setImageResource(R.drawable.ojo_cerrado)

        // 10. Botón del ojo para mostrar/ocultar contraseña
        btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            val cursorPosition = etPassword.selectionStart

            if (isPasswordVisible) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ojo)
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ojo_cerrado)
            }

            etPassword.setSelection(cursorPosition)
        }
    }

    /**
     * Devuelve el icono según la categoría.
     */
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

    /**
     * Color de fondo del recuadro de categoría (mismos colores que usa el selector de categorías).
     */
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

    /**
     * Modal de confirmación con el diseño oscuro de la app
     * (mismo estilo que "Eliminar categoría").
     */
    private fun mostrarDialogoEliminar(nombre: String, alConfirmar: () -> Unit) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_confirm_delete)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<TextView>(R.id.tvDialogMessage).text =
            "¿Seguro que quieres eliminar \"$nombre\"? Esta acción no se puede deshacer."

        dialog.findViewById<AppCompatButton>(R.id.btnDialogConfirm).setOnClickListener {
            dialog.dismiss()
            alConfirmar()
        }
        dialog.findViewById<AppCompatButton>(R.id.btnDialogCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Elimina la credencial de Firebase y de la lista local.
     * Se identifica por servicio + usuario + contraseña originales.
     */
    private fun eliminarCredencial(servicio: String, usuario: String, contrasena: String) {
        val userId = Firebase.auth.currentUser?.uid

        // Sin sesión (invitado): solo se elimina de la lista local y se guarda en el dispositivo
        if (userId == null) {
            quitarDeListaLocal(servicio, usuario, contrasena)
            mostrarBannerPersonalizado(
                titulo = "Credencial eliminada",
                mensaje = "Eliminada localmente",
                iconoResId = R.drawable.logo
            )
            volverALista()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("credentials")
            .whereEqualTo("serviceName", servicio)
            .whereEqualTo("userOrEmail", usuario)
            .whereEqualTo("password", contrasena)
            .limit(1)
            .get()
            .addOnSuccessListener { resultado ->
                val documento = resultado.documents.firstOrNull()
                if (documento == null) {
                    // No estaba en la nube: se limpia solo la copia local
                    quitarDeListaLocal(servicio, usuario, contrasena)
                    volverALista()
                    return@addOnSuccessListener
                }
                documento.reference.delete()
                    .addOnSuccessListener {
                        quitarDeListaLocal(servicio, usuario, contrasena)
                        mostrarBannerPersonalizado(
                            titulo = "Credencial eliminada",
                            mensaje = "Se eliminó \"$servicio\" con éxito",
                            iconoResId = R.drawable.logo
                        )
                        volverALista()
                    }
                    .addOnFailureListener {
                        mostrarBannerPersonalizado(
                            titulo = "Error de sincronización",
                            mensaje = "No se pudo eliminar de la nube",
                            iconoResId = R.drawable.logo
                        )
                    }
            }
            .addOnFailureListener {
                mostrarBannerPersonalizado(
                    titulo = "Error de sincronización",
                    mensaje = "No se pudo conectar con la nube",
                    iconoResId = R.drawable.logo
                )
            }
    }

    private fun quitarDeListaLocal(servicio: String, usuario: String, contrasena: String) {
        val indice = VaultRepository.credentialList.indexOfFirst {
            it.serviceName == servicio && it.userOrEmail == usuario && it.password == contrasena
        }
        if (indice >= 0) {
            VaultRepository.credentialList.removeAt(indice)
            // Invitado: guarda la lista actualizada cifrada en el dispositivo
            VaultRepository.persistirSiInvitado(this)
        }
    }

    // Vuelve a la lista y cierra esta pantalla
    private fun volverALista() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    // Calcula dinámicamente la seguridad según el texto actual
    private fun calcularYActualizarSeguridad(pass: String) {
        val tieneLetras = pass.any { it.isLetter() }
        val tieneNumeros = pass.any { it.isDigit() }
        val tieneSimbolos = pass.any { !it.isLetterOrDigit() }
        val esLarga = pass.length >= 8

        val statusCalculado = when {
            pass.length < 6 -> "Débil"
            esLarga && tieneLetras && tieneNumeros && tieneSimbolos -> "Segura"
            esLarga && (tieneLetras || tieneNumeros) -> "Media - Añade un símbolo"
            else -> "Débil"
        }

        actualizarFortalezaUI(pass, statusCalculado)
    }

    // Pinta la UI de fortaleza de forma sincronizada
    private fun actualizarFortalezaUI(pass: String, status: String) {
        // Nombre unificado: "Fortaleza Alta" y "Segura" se muestran siempre como "Segura"
        val esSegura = status.contains("Segura", ignoreCase = true) || status.contains("Alta", ignoreCase = true)
        tvStatusText.text = if (esSegura) "Segura" else status

        val colorHex: String
        val fondoHex: String

        when {
            status.contains("Repetida", ignoreCase = true) -> {
                colorHex = "#9B9B9B"
                fondoHex = "#2A2A2A"
                pintarBarritas("#9B9B9B", 1)
            }
            status.contains("Segura", ignoreCase = true) || status.contains("Alta", ignoreCase = true) -> {
                colorHex = "#84CC16"
                fondoHex = "#1A2E05"
                pintarBarritas("#84CC16", 3)
            }
            status.contains("Débil", ignoreCase = true) -> {
                colorHex = "#E26B6B"
                fondoHex = "#3D1A1A"
                pintarBarritas("#E26B6B", 1)
            }
            else -> {
                colorHex = "#E5C158"
                fondoHex = "#3B3014"
                pintarBarritas("#E5C158", 2)
            }
        }

        tvStatusText.setTextColor(Color.parseColor(colorHex))
        vStatusDot.background?.mutate()?.setTint(Color.parseColor(colorHex))
        layoutStatusBadge.background?.mutate()?.setTint(Color.parseColor(fondoHex))
        tvStrengthLabel.text = if (esSegura) "Fortaleza alta" else status
        tvStrengthLabel.setTextColor(Color.parseColor(colorHex))
    }

    private fun pintarBarritas(colorHex: String, nivel: Int) {
        val color = Color.parseColor(colorHex)
        val colorInactivo = Color.parseColor("#2A2A2A")

        bar1.background?.mutate()?.setTint(if (nivel >= 1) color else colorInactivo)
        bar2.background?.mutate()?.setTint(if (nivel >= 2) color else colorInactivo)
        bar3.background?.mutate()?.setTint(if (nivel >= 3) color else colorInactivo)
    }
}