package com.example.myapplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class EditCredentialActivity : BaseActivity() {

    private lateinit var etUser: EditText
    private lateinit var etPassword: EditText
    private lateinit var etUrl: EditText
    private lateinit var etNotes: EditText
    private lateinit var btnTogglePassword: ImageView

    private lateinit var tvStatusText: TextView
    private lateinit var vStatusDot: View
    private lateinit var layoutStatusBadge: LinearLayout
    private lateinit var tvStrengthLabel: TextView
    private lateinit var bar1: View
    private lateinit var bar2: View
    private lateinit var bar3: View

    private var isPasswordVisible = false
    private var ultimaPassword = ""

    // Datos originales: sirven para encontrar el documento en Firebase
    private var originalService = ""
    private var originalUser = ""
    private var originalPassword = ""

    // Fortaleza que se guardará: "Débil", "Media - Añade un símbolo" o "Fortaleza Alta"
    private var estadoParaGuardar = "Fortaleza Alta"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_credential)

        // 1. Referencias
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val tvInitial = findViewById<TextView>(R.id.tvInitial)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tvSubtitleCategory = findViewById<TextView>(R.id.tvSubtitleCategory)
        etUser = findViewById(R.id.etUser)
        etPassword = findViewById(R.id.etPassword)
        etUrl = findViewById(R.id.etUrl)
        etNotes = findViewById(R.id.etNotes)
        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        val btnCopyUser = findViewById<ImageView>(R.id.btnCopyUser)
        val btnCopyPassword = findViewById<ImageView>(R.id.btnCopyPassword)

        tvStatusText = findViewById(R.id.tvStatusText)
        vStatusDot = findViewById(R.id.vStatusDot)
        layoutStatusBadge = findViewById(R.id.layoutStatusBadge)
        tvStrengthLabel = findViewById(R.id.tvStrengthLabel)
        bar1 = findViewById(R.id.bar1)
        bar2 = findViewById(R.id.bar2)
        bar3 = findViewById(R.id.bar3)

        val layoutCategoryBox = findViewById<LinearLayout>(R.id.layoutCategoryBox)
        val ivCategoryIcon = findViewById<ImageView>(R.id.ivCategoryIcon)
        val tvCategoryName = findViewById<TextView>(R.id.tvCategoryName)

        val btnSave = findViewById<AppCompatButton>(R.id.btnSave)
        val btnCancel = findViewById<AppCompatButton>(R.id.btnDelete)

        // 2. Datos que llegan desde el detalle
        val title = intent.getStringExtra("serviceName") ?: ""
        val username = intent.getStringExtra("userOrEmail") ?: ""
        val password = intent.getStringExtra("password") ?: ""
        val url = intent.getStringExtra("url") ?: ""
        val notes = intent.getStringExtra("notes") ?: ""
        val rawCategory = intent.getStringExtra("category") ?: ""
        val updatedAt = intent.getLongExtra("updatedAt", 0L)
        val category = if (rawCategory.isNotBlank()) rawCategory else "Sin categoría"

        originalService = title
        originalUser = username
        originalPassword = password

        // 3. Pintar la información
        tvInitial.text = title.firstOrNull()?.uppercase() ?: "?"
        tvTitle.text = title
        findViewById<TextView>(R.id.tvUpdatedAt).text = TimeUtils.tiempoRelativo(updatedAt)
        tvSubtitleCategory.text = category
        etUser.setText(username)
        etPassword.setText(password)
        ultimaPassword = password
        etUrl.setText(url)
        etNotes.setText(notes)

        // 4. Categoría: texto, icono y color
        tvCategoryName.text = category
        ivCategoryIcon.setImageResource(iconoParaCategoria(category))
        val fondoCategoria = colorFondoParaCategoria(category)
        val fondoActual = layoutCategoryBox.background?.mutate()
        if (fondoActual is GradientDrawable) {
            fondoActual.setColor(fondoCategoria)
        } else {
            fondoActual?.setTint(fondoCategoria)
        }
        tvCategoryName.setTextColor(Color.WHITE)

        // 5. Contraseña oculta por defecto + fortaleza inicial
        etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        etPassword.setSelection(etPassword.text.length)
        btnTogglePassword.setImageResource(R.drawable.ojo_cerrado)
        actualizarFortaleza(password)

        // 6. Recalcular la fortaleza solo cuando el texto realmente cambie
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val actual = s.toString()
                if (actual == ultimaPassword) return
                ultimaPassword = actual
                actualizarFortaleza(actual)
            }
        })

        // 7. Ojo para mostrar/ocultar contraseña
        btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            val cursor = etPassword.selectionStart
            if (isPasswordVisible) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ojo)
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ojo_cerrado)
            }
            etPassword.setSelection(if (cursor >= 0) cursor else etPassword.text.length)
        }

        // 8. Copiar
        btnCopyUser.setOnClickListener {
            copiar("Usuario copiado", etUser.text.toString(), "Usuario copiado al portapapeles")
        }
        btnCopyPassword.setOnClickListener {
            copiar("Contraseña copiada", etPassword.text.toString(), "Contraseña copiada al portapapeles")
        }

        // 9. Navegación
        btnBack.setOnClickListener { finish() }
        btnCancel.setOnClickListener { finish() }
        btnSave.setOnClickListener { guardarCambios(category) }
    }

    private fun guardarCambios(category: String) {
        val nuevoUsuario = etUser.text.toString().trim().ifEmpty { "Sin usuario" }
        val nuevaPass = etPassword.text.toString().trim()
        val nuevaUrl = etUrl.text.toString().trim()
        val nuevasNotas = etNotes.text.toString().trim()
        val ahora = System.currentTimeMillis()

        if (nuevaPass.isEmpty()) {
            mostrarBannerPersonalizado(
                titulo = "Campo requerido",
                mensaje = "La contraseña no puede estar vacía",
                iconoResId = R.drawable.logo
            )
            return
        }

        // Actualizar la copia local que usa la lista
        val indice = VaultRepository.credentialList.indexOfFirst {
            it.serviceName == originalService &&
                    it.userOrEmail == originalUser &&
                    it.password == originalPassword
        }
        if (indice >= 0) {
            val anterior = VaultRepository.credentialList[indice]
            VaultRepository.credentialList[indice] = anterior.copy(
                userOrEmail = nuevoUsuario,
                password = nuevaPass,
                strengthStatus = estadoParaGuardar,
                url = nuevaUrl,
                notes = nuevasNotas,
                updatedAt = ahora
            )
        }

        val cambios = hashMapOf<String, Any>(
            "userOrEmail" to nuevoUsuario,
            "password" to nuevaPass,
            "strengthStatus" to estadoParaGuardar,
            "url" to nuevaUrl,
            "notes" to nuevasNotas,
            "updatedAt" to ahora
        )

        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            // Invitado: se guarda cifrado en el dispositivo
            VaultRepository.persistirSiInvitado(this)
            mostrarBannerPersonalizado(
                titulo = "Cambios guardados",
                mensaje = "Actualizada localmente",
                iconoResId = R.drawable.logo
            )
            volverALista()
            return
        }

        // Buscar el documento original en Firebase y actualizarlo
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("credentials")
            .whereEqualTo("serviceName", originalService)
            .whereEqualTo("userOrEmail", originalUser)
            .whereEqualTo("password", originalPassword)
            .limit(1)
            .get()
            .addOnSuccessListener { resultado ->
                val documento = resultado.documents.firstOrNull()
                if (documento == null) {
                    mostrarBannerPersonalizado(
                        titulo = "No se encontró",
                        mensaje = "No se encontró la credencial en la nube",
                        iconoResId = R.drawable.logo
                    )
                    return@addOnSuccessListener
                }
                documento.reference.update(cambios)
                    .addOnSuccessListener {
                        mostrarBannerPersonalizado(
                            titulo = "¡Cambios guardados!",
                            mensaje = "Credencial actualizada con éxito",
                            iconoResId = R.drawable.logo
                        )
                        volverALista()
                    }
                    .addOnFailureListener {
                        mostrarBannerPersonalizado(
                            titulo = "Error de sincronización",
                            mensaje = "No se pudieron guardar los cambios",
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

    // Vuelve a la lista (cierra también el detalle, que tendría datos viejos)
    private fun volverALista() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun copiar(etiqueta: String, texto: String, mensaje: String) {
        if (texto.isEmpty()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(etiqueta, texto))
        mostrarBannerPersonalizado(
            titulo = "Portapapeles",
            mensaje = mensaje,
            iconoResId = R.drawable.logo
        )
    }

    // Mismos iconos que usa el selector de categorías
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

    // Mismos colores de fondo que usa el selector de categorías
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

    // Calcula y pinta la fortaleza (mismas reglas que Nueva credencial)
    private fun actualizarFortaleza(pass: String) {
        val nivel: Int
        val colorHex: String
        val fondoHex: String
        val nombreBadge: String
        val etiqueta: String

        when {
            pass.length < 6 -> {
                nivel = 1
                colorHex = "#E26B6B"
                fondoHex = "#3D1A1A"
                nombreBadge = "Débil"
                etiqueta = "Débil"
                estadoParaGuardar = "Débil"
            }
            !pass.any { !it.isLetterOrDigit() } -> {
                nivel = 2
                colorHex = "#E5C158"
                fondoHex = "#3B3014"
                nombreBadge = "Media"
                etiqueta = "Media - Añade un símbolo"
                estadoParaGuardar = "Media - Añade un símbolo"
            }
            else -> {
                nivel = 3
                colorHex = "#84CC16"
                fondoHex = "#1A2E05"
                nombreBadge = "Segura"
                etiqueta = "Fortaleza alta"
                estadoParaGuardar = "Fortaleza Alta"
            }
        }

        val color = Color.parseColor(colorHex)
        val inactivo = Color.parseColor("#2A2A2A")

        tvStatusText.text = nombreBadge
        tvStatusText.setTextColor(color)
        vStatusDot.background?.mutate()?.setTint(color)
        layoutStatusBadge.background?.mutate()?.setTint(Color.parseColor(fondoHex))

        bar1.setBackgroundColor(if (nivel >= 1) color else inactivo)
        bar2.setBackgroundColor(if (nivel >= 2) color else inactivo)
        bar3.setBackgroundColor(if (nivel >= 3) color else inactivo)

        tvStrengthLabel.text = etiqueta
        tvStrengthLabel.setTextColor(color)
    }
}