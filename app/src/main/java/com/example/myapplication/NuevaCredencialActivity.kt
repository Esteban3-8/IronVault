package com.example.myapplication

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class NuevaCredencialActivity : BaseActivity() {

    private lateinit var etPassword: EditText
    private lateinit var tvCategory: TextView
    private lateinit var ivCatIcon: ImageView
    private lateinit var ivTopCategoryIcon: ImageView
    private lateinit var etService: EditText
    private lateinit var etUser: EditText
    private lateinit var etUrl: EditText
    private lateinit var etNotes: EditText
    private lateinit var btnTogglePassword: ImageView
    private lateinit var viewStrengthBar: View
    private lateinit var tvStrengthHint: TextView

    private var isPasswordVisible = true
    private var selectedCategory: String? = null

    private val colorSeleccionado = Color.parseColor("#F97316")
    private val colorNormal = Color.parseColor("#FFFFFF")

    companion object {
        val categoryCounts = mutableMapOf<String, Int>()
        val categoryLastUsedTime = mutableMapOf<String, Long>()
    }

    private val passwordGeneratorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val generatedPassword = result.data?.getStringExtra("EXTRA_GENERATED_PASSWORD")
            if (!generatedPassword.isNullOrEmpty()) {
                etPassword.setText(generatedPassword)
                etPassword.setSelection(etPassword.text.length)
            }
        }
    }

    /** Un archivo de frecuencias por usuario: cada cuenta tiene el suyo y el invitado otro. */
    private fun prefsFrecuencias(): SharedPreferences {
        val uid = Firebase.auth.currentUser?.uid ?: "guest"
        return getSharedPreferences("IronVaultPrefs_$uid", MODE_PRIVATE)
    }

    /** Documento en la nube con los contadores. Solo existe para cuentas con correo. */
    private fun docFrecuencias(): DocumentReference? {
        val uid = Firebase.auth.currentUser?.uid ?: return null
        return FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("settings")
            .document("categoryStats")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nueva_credencial)

        cargarFrecuenciasDeCache()
        cargarFrecuenciasDeNube()

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnGeneratePassword = findViewById<ImageView>(R.id.btnGeneratePassword)
        val btnSelectCategory = findViewById<RelativeLayout>(R.id.btnSelectCategory)
        val btnSaveCredential = findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnSaveCredential)

        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        etPassword = findViewById(R.id.etPassword)
        tvCategory = findViewById(R.id.tvCategory)
        ivCatIcon = findViewById(R.id.ivCatIcon)
        ivTopCategoryIcon = findViewById(R.id.ivTopCategoryIcon)
        etService = findViewById(R.id.etService)
        etUser = findViewById(R.id.etUser)
        etUrl = findViewById(R.id.etUrl)
        etNotes = findViewById(R.id.etNotes)
        viewStrengthBar = findViewById(R.id.viewStrengthBar)
        tvStrengthHint = findViewById(R.id.tvStrengthHint)

        etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
        btnTogglePassword.setImageResource(R.drawable.ojo)
        etPassword.setSelection(etPassword.text.length)
        evaluarSeguridadContrasena(etPassword.text.toString())

        btnBack?.setOnClickListener {
            finish()
        }

        btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            val selection = etPassword.selectionStart
            if (isPasswordVisible) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ojo)
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ojo_cerrado)
            }
            etPassword.setSelection(selection)
        }

        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                evaluarSeguridadContrasena(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnGeneratePassword?.setOnClickListener {
            val intent = Intent(this, PasswordGeneratorActivity::class.java)
            passwordGeneratorLauncher.launch(intent)
        }

        btnSelectCategory?.setOnClickListener {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_category, null)
            val dialog = BottomSheetDialog(this)
            dialog.setContentView(view)

            val rowOther = view.findViewById<View>(R.id.rowOther)
            val rowFrequentExtra1 = view.findViewById<View>(R.id.rowFrequentExtra1)
            val rowFrequentExtra2 = view.findViewById<View>(R.id.rowFrequentExtra2)
            val rowFrequentExtra3 = view.findViewById<View>(R.id.rowFrequentExtra3)
            val layoutRowFrequent1 = view.findViewById<View>(R.id.layoutRowFrequent1)
            val layoutRowFrequent2 = view.findViewById<View>(R.id.layoutRowFrequent2)

            val tvHeaderFrecuentes = view.findViewById<TextView>(R.id.tvHeaderFrecuentes)
            val tvHeaderPersonales = view.findViewById<TextView>(R.id.tvHeaderPersonales)
            val tvHeaderFinanzas = view.findViewById<TextView>(R.id.tvHeaderFinanzas)
            val tvHeaderTrabajo = view.findViewById<TextView>(R.id.tvHeaderTrabajo)
            val btnNewCategory = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnNewCategory)

            btnNewCategory?.setOnClickListener {
                dialog.dismiss()
                startActivity(Intent(this, NuevaCategoriaActivity::class.java))
            }

            val etSearchCategory = view.findViewById<EditText>(R.id.etSearch)
            val cardSearchCategory = view.findViewById<MaterialCardView>(R.id.cardSearch)

            etSearchCategory?.setOnFocusChangeListener { _, hasFocus ->
                cardSearchCategory?.strokeColor = if (hasFocus) Color.parseColor("#E8873E") else Color.parseColor("#2C303B")
            }

            val tiempoExpiracionMs = 7L * 24L * 60L * 60L * 1000L
            val ahora = System.currentTimeMillis()

            val iterator = categoryLastUsedTime.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val categoria = entry.key
                val ultimoUso = entry.value

                if (categoria != "Otras Claves" && (ahora - ultimoUso) > tiempoExpiracionMs) {
                    val countActual = categoryCounts[categoria] ?: 0
                    if (countActual > 0) {
                        categoryCounts[categoria] = (countActual - 1).coerceAtLeast(0)
                    }
                }
            }

            rowOther?.visibility = View.VISIBLE
            val iconoOtras = obtenerIconoParaCategoria("Otras Claves")
            configurarOpcionCategoria(dialog, view, R.id.rowOther, "Otras Claves", iconoOtras, esFrecuenteSlot = true)

            val umbralMinimo = 3
            val topCategoriasFrecuentes = categoryCounts.entries
                .filter { it.key != "Otras Claves" && it.value >= umbralMinimo }
                .sortedByDescending { it.value }
                .map { it.key }
                .take(3)

            val slotsExtra = listOf(rowFrequentExtra1, rowFrequentExtra2, rowFrequentExtra3)
            val idsSlotsExtra = listOf(R.id.rowFrequentExtra1, R.id.rowFrequentExtra2, R.id.rowFrequentExtra3)

            var mostrarFila2 = false

            for (i in slotsExtra.indices) {
                val slotView = slotsExtra[i]
                if (slotView != null) {
                    if (i < topCategoriasFrecuentes.size) {
                        val catNombre = topCategoriasFrecuentes[i]
                        slotView.visibility = View.VISIBLE
                        val iconoCat = obtenerIconoParaCategoria(catNombre)
                        configurarOpcionCategoria(dialog, view, idsSlotsExtra[i], catNombre, iconoCat, esFrecuenteSlot = true)

                        if (i >= 1) {
                            mostrarFila2 = true
                        }
                    } else {
                        slotView.visibility = View.GONE
                    }
                }
            }

            layoutRowFrequent2?.visibility = if (mostrarFila2) View.VISIBLE else View.GONE

            // Configurar todas las categorías de las secciones inferiores para que respondan al clic
            val idsCategoriasCompletas = mapOf(
                "Redes Sociales" to R.id.rowSocial,
                "Correos" to R.id.rowMail,
                "Videojuegos" to R.id.rowGames,
                "Bancos" to R.id.rowBank,
                "Compras" to R.id.rowShop,
                "Educación" to R.id.rowEdu,
                "Otras Claves" to R.id.rowOtherGroup
            )

            for ((nombreCat, idLayout) in idsCategoriasCompletas) {
                val iconoCat = obtenerIconoParaCategoria(nombreCat)
                configurarOpcionCategoria(dialog, view, idLayout, nombreCat, iconoCat, esFrecuenteSlot = false)
            }

            val categoriaPersonales = listOf("Redes Sociales", "Correos", "Videojuegos")
            val categoriaFinanzas = listOf("Bancos", "Compras")
            val categoriaTrabajo = listOf("Educación", "Otras Claves Grupo")

            val tvNoEncontrado = TextView(view.context).apply {
                text = "No se encontraron categorías"
                setTextColor(Color.parseColor("#8E94A0"))
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 40, 0, 40)
                visibility = View.GONE
            }

            val containerCategoryList = view.findViewById<ViewGroup>(R.id.containerCategoryList)
            containerCategoryList?.addView(tvNoEncontrado)

            // Lógica de filtrado inteligente manteniendo tamaño y controlando títulos por sección
            etSearchCategory?.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s.toString().lowercase().trim()
                    var visiblesCount = 0

                    var hayPersonales = false
                    var hayFinanzas = false
                    var hayTrabajo = false
                    var hayFrecuentes = false

                    val coincideOther = "otras claves".contains(query) || query.isEmpty()
                    rowOther?.visibility = if (coincideOther) View.VISIBLE else View.GONE
                    if (coincideOther) {
                        visiblesCount++
                        hayFrecuentes = true
                    }

                    val idsCategorias = mapOf(
                        "Redes Sociales" to R.id.rowSocial,
                        "Correos" to R.id.rowMail,
                        "Videojuegos" to R.id.rowGames,
                        "Bancos" to R.id.rowBank,
                        "Compras" to R.id.rowShop,
                        "Educación" to R.id.rowEdu,
                        "Otras Claves" to R.id.rowOtherGroup
                    )

                    for ((nombreCat, idLayout) in idsCategorias) {
                        val rowView = view.findViewById<View>(idLayout)
                        if (rowView != null) {
                            val coincide = nombreCat.lowercase().contains(query) || query.isEmpty()
                            if (coincide) {
                                rowView.visibility = View.VISIBLE
                                visiblesCount++

                                if (categoriaPersonales.contains(nombreCat)) hayPersonales = true
                                if (categoriaFinanzas.contains(nombreCat)) hayFinanzas = true
                                if (categoriaTrabajo.contains(nombreCat) || nombreCat == "Otras Claves") hayTrabajo = true
                            } else {
                                rowView.visibility = View.GONE
                            }
                        }
                    }

                    if (query.isNotEmpty()) {
                        rowFrequentExtra1?.visibility = View.GONE
                        rowFrequentExtra2?.visibility = View.GONE
                        rowFrequentExtra3?.visibility = View.GONE
                        layoutRowFrequent2?.visibility = View.GONE
                    } else {
                        rowFrequentExtra1?.visibility = if (topCategoriasFrecuentes.size >= 1) View.VISIBLE else View.GONE
                        rowFrequentExtra2?.visibility = if (topCategoriasFrecuentes.size >= 2) View.VISIBLE else View.GONE
                        rowFrequentExtra3?.visibility = if (topCategoriasFrecuentes.size >= 3) View.VISIBLE else View.GONE
                        layoutRowFrequent2?.visibility = if (mostrarFila2) View.VISIBLE else View.GONE
                        hayFrecuentes = true
                    }

                    // Control independiente de títulos de sección para evitar títulos flotantes
                    tvHeaderFrecuentes?.visibility = if (hayFrecuentes) View.VISIBLE else View.GONE
                    tvHeaderPersonales?.visibility = if (hayPersonales) View.VISIBLE else View.GONE
                    tvHeaderFinanzas?.visibility = if (hayFinanzas) View.VISIBLE else View.GONE
                    tvHeaderTrabajo?.visibility = if (hayTrabajo) View.VISIBLE else View.GONE

                    if (visiblesCount == 0) {
                        tvNoEncontrado.visibility = View.VISIBLE
                    } else {
                        tvNoEncontrado.visibility = View.GONE
                    }
                }
            })

            dialog.show()
        }

        btnSaveCredential?.setOnClickListener {
            val servicio = etService.text.toString().trim()
            val usuario = etUser.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            val url = etUrl.text.toString().trim()
            val notas = etNotes.text.toString().trim()

            if (servicio.isEmpty() || pass.isEmpty()) {
                mostrarBannerPersonalizado(
                    titulo = "Campos requeridos",
                    mensaje = "El servicio y la contraseña son obligatorios",
                    iconoResId = R.drawable.logo
                )
                return@setOnClickListener
            }

            val usuarioFinal = if (usuario.isEmpty()) "Sin usuario" else usuario
            val categoriaFinal = selectedCategory ?: "Otras Claves"
            val fortalezaFinal = tvStrengthHint.text.toString()
            val momentoGuardado = System.currentTimeMillis()

            // La credencial lleva la categoría elegida
            val nuevaCredencial = Credential(
                serviceName = servicio,
                userOrEmail = usuarioFinal,
                password = pass,
                strengthStatus = fortalezaFinal,
                url = url,
                notes = notas,
                category = categoriaFinal,
                updatedAt = momentoGuardado
            )

            val userId = Firebase.auth.currentUser?.uid

            if (userId != null) {
                val credMap = hashMapOf(
                    "serviceName" to servicio,
                    "userOrEmail" to usuarioFinal,
                    "password" to pass,
                    "strengthStatus" to fortalezaFinal,
                    "url" to url,
                    "notes" to notas,
                    "category" to categoriaFinal,
                    "updatedAt" to momentoGuardado
                )

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .collection("credentials")
                    .add(credMap)
                    .addOnSuccessListener { documentReference ->
                        Log.e("IRONVAULT_DEBUG", "¡Guardado exitoso en: ${documentReference.path}")
                        VaultRepository.addCredential(nuevaCredencial)

                        mostrarBannerPersonalizado(
                            titulo = "¡Credencial guardada!",
                            mensaje = "Registrada en la nube con éxito",
                            iconoResId = R.drawable.logo
                        )

                        finish()
                    }
                    .addOnFailureListener { e ->
                        Log.e("IRONVAULT_DEBUG", "Error al guardar: ${e.message}")
                        mostrarBannerPersonalizado(
                            titulo = "Error de sincronización",
                            mensaje = "No se pudo guardar en la nube",
                            iconoResId = R.drawable.logo
                        )
                    }
            } else {
                // Invitado: se guarda cifrada en el dispositivo
                VaultRepository.addCredential(nuevaCredencial)
                VaultRepository.persistirSiInvitado(this)

                mostrarBannerPersonalizado(
                    titulo = "¡Credencial guardada!",
                    mensaje = "Registrada localmente con éxito",
                    iconoResId = R.drawable.logo
                )

                finish()
            }
        }
    }

    /** Guarda los contadores en el dispositivo y, si hay cuenta, también en la nube. */
    private fun guardarFrecuenciasEnCache() {
        guardarFrecuenciasLocal()

        // Cuenta con correo: copia en la nube para que sobreviva a reinstalar la app
        docFrecuencias()?.set(
            hashMapOf(
                "counts" to categoryCounts.toMap(),
                "times" to categoryLastUsedTime.toMap()
            )
        )?.addOnFailureListener { e ->
            Log.e("FRECUENCIAS", "No se pudo guardar en la nube: ${e.message}")
        }
    }

    private fun guardarFrecuenciasLocal() {
        val editor = prefsFrecuencias().edit()

        val countsString = categoryCounts.entries.joinToString(";") { "${it.key}:${it.value}" }
        editor.putString("category_counts", countsString)

        val timesString = categoryLastUsedTime.entries.joinToString(";") { "${it.key}:${it.value}" }
        editor.putString("category_times", timesString)

        editor.apply()
    }

    private fun cargarFrecuenciasDeCache() {
        val prefs = prefsFrecuencias()

        // Siempre limpiamos: así no se arrastran los contadores de otra sesión
        categoryCounts.clear()
        categoryLastUsedTime.clear()

        val countsString = prefs.getString("category_counts", "")
        if (!countsString.isNullOrEmpty()) {
            countsString.split(";").forEach { item ->
                val partes = item.split(":")
                if (partes.size == 2) {
                    val cat = partes[0]
                    val count = partes[1].toIntOrNull() ?: 0
                    categoryCounts[cat] = count
                }
            }
        }

        val timesString = prefs.getString("category_times", "")
        if (!timesString.isNullOrEmpty()) {
            timesString.split(";").forEach { item ->
                val partes = item.split(":")
                if (partes.size == 2) {
                    val cat = partes[0]
                    val time = partes[1].toLongOrNull() ?: 0L
                    categoryLastUsedTime[cat] = time
                }
            }
        }
    }

    /** Cuenta con correo: trae los contadores de la nube (así vuelven tras reinstalar). */
    private fun cargarFrecuenciasDeNube() {
        val doc = docFrecuencias() ?: return

        doc.get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) return@addOnSuccessListener

                val counts = snap.get("counts") as? Map<*, *>
                val times = snap.get("times") as? Map<*, *>

                if (counts != null) {
                    categoryCounts.clear()
                    for ((k, v) in counts) {
                        val cat = k as? String ?: continue
                        categoryCounts[cat] = (v as? Number)?.toInt() ?: 0
                    }
                }
                if (times != null) {
                    categoryLastUsedTime.clear()
                    for ((k, v) in times) {
                        val cat = k as? String ?: continue
                        categoryLastUsedTime[cat] = (v as? Number)?.toLong() ?: 0L
                    }
                }

                // Deja también copia local, para cuando no haya internet
                guardarFrecuenciasLocal()
            }
            .addOnFailureListener { e ->
                Log.e("FRECUENCIAS", "No se pudo leer de la nube: ${e.message}")
            }
    }

    private fun configurarOpcionCategoria(dialog: BottomSheetDialog, view: View, idLayout: Int, nombre: String, iconoRes: Int, esFrecuenteSlot: Boolean) {
        val rowView = view.findViewById<View>(idLayout) ?: return

        rowView.visibility = View.VISIBLE
        val tvNombre = encontrarTextView(rowView)
        tvNombre?.text = nombre

        val ivIcono = encontrarImageView(rowView)
        ivIcono?.setImageResource(iconoRes)

        val cardView = encontrarMaterialCardView(rowView)
        cardView?.setCardBackgroundColor(obtenerColorBgParaCategoria(nombre))

        resaltarFila(rowView, nombre == selectedCategory, esFrecuenteSlot)

        rowView.setOnClickListener {
            selectedCategory = nombre
            tvCategory.text = nombre
            tvCategory.setTextColor(colorNormal)
            tvCategory.setTypeface(null, Typeface.BOLD)

            ivCatIcon.setImageResource(iconoRes)
            ivTopCategoryIcon.setImageResource(iconoRes)

            categoryCounts[nombre] = (categoryCounts[nombre] ?: 0) + 1
            categoryLastUsedTime[nombre] = System.currentTimeMillis()

            guardarFrecuenciasEnCache()

            dialog.dismiss()
        }
    }

    private fun obtenerIconoParaCategoria(nombre: String): Int {
        return when (nombre) {
            "Redes Sociales" -> R.drawable.ic_cat_social
            "Correos" -> R.drawable.ic_cat_mail
            "Videojuegos" -> R.drawable.ic_cat_games
            "Bancos" -> R.drawable.ic_cat_bank
            "Compras" -> R.drawable.ic_cat_shop
            "Educación" -> R.drawable.ic_cat_edu
            else -> R.drawable.ic_cat_lupa
        }
    }

    private fun obtenerColorBgParaCategoria(nombre: String): Int {
        return when (nombre) {
            "Redes Sociales" -> getColor(R.color.cat_social_bg)
            "Correos" -> getColor(R.color.cat_mail_bg)
            "Videojuegos" -> getColor(R.color.cat_games_bg)
            "Bancos" -> getColor(R.color.cat_bank_bg)
            "Compras" -> getColor(R.color.cat_shop_bg)
            "Educación" -> getColor(R.color.cat_edu_bg)
            else -> getColor(R.color.cat_other_bg)
        }
    }

    private fun resaltarFila(row: View, activa: Boolean, esFrecuente: Boolean) {
        val density = resources.displayMetrics.density
        val padH = (12 * density).toInt()
        val padV = (8 * density).toInt()

        row.setPadding(padH, padV, padH, padV)

        if (esFrecuente) {
            row.setBackgroundResource(
                if (activa) R.drawable.bg_selected_category else R.drawable.bg_frecuente
            )
            encontrarTextView(row)?.setTextColor(colorNormal)
        } else {
            if (activa) {
                row.setBackgroundResource(R.drawable.bg_selected_category)
            } else {
                row.setBackgroundColor(Color.TRANSPARENT)
            }
            encontrarTextView(row)?.setTextColor(if (activa) colorSeleccionado else colorNormal)
        }
    }

    private fun encontrarTextView(view: View): TextView? {
        if (view is TextView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val encontrado = encontrarTextView(view.getChildAt(i))
                if (encontrado != null) return encontrado
            }
        }
        return null
    }

    private fun encontrarImageView(view: View): ImageView? {
        if (view is ImageView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val encontrado = encontrarImageView(view.getChildAt(i))
                if (encontrado != null) return encontrado
            }
        }
        return null
    }

    private fun encontrarMaterialCardView(view: View): com.google.android.material.card.MaterialCardView? {
        if (view is com.google.android.material.card.MaterialCardView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val encontrado = encontrarMaterialCardView(view.getChildAt(i))
                if (encontrado != null) return encontrado
            }
        }
        return null
    }

    private fun evaluarSeguridadContrasena(password: String) {
        val rojoDebil = Color.parseColor("#EF4444")
        val amarilloMedio = Color.parseColor("#F59E0B")
        val verdeFuerte = Color.parseColor("#8FC954")
        val grisRepetida = Color.parseColor("#9B9B9B")

        val params = viewStrengthBar.layoutParams as ViewGroup.LayoutParams
        val esRepetida = password.isNotEmpty() && VaultRepository.credentialList.any { it.password == password }

        when {
            password.isEmpty() -> {
                params.width = 0
                viewStrengthBar.layoutParams = params
                tvStrengthHint.text = "Mínimo 6 caracteres"
                tvStrengthHint.setTextColor(Color.parseColor("#9CA3AF"))
            }
            esRepetida -> {
                params.width = (resources.displayMetrics.widthPixels * 0.50).toInt()
                viewStrengthBar.layoutParams = params
                viewStrengthBar.setBackgroundColor(grisRepetida)
                tvStrengthHint.text = "Repetida"
                tvStrengthHint.setTextColor(grisRepetida)
            }
            password.length < 6 -> {
                params.width = (resources.displayMetrics.widthPixels * 0.33).toInt()
                viewStrengthBar.layoutParams = params
                viewStrengthBar.setBackgroundColor(rojoDebil)
                tvStrengthHint.text = "Débil"
                tvStrengthHint.setTextColor(rojoDebil)
            }
            password.length >= 6 && !password.any { !it.isLetterOrDigit() } -> {
                params.width = (resources.displayMetrics.widthPixels * 0.66).toInt()
                viewStrengthBar.layoutParams = params
                viewStrengthBar.setBackgroundColor(amarilloMedio)
                tvStrengthHint.text = "Media - Añade un símbolo"
                tvStrengthHint.setTextColor(amarilloMedio)
            }
            else -> {
                params.width = resources.displayMetrics.widthPixels
                viewStrengthBar.layoutParams = params
                viewStrengthBar.setBackgroundColor(verdeFuerte)
                tvStrengthHint.text = "Fortaleza Alta"
                tvStrengthHint.setTextColor(verdeFuerte)
            }
        }
    }
}