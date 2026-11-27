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
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Firebase

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

    private val categoryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val key = result.data?.getStringExtra(CategoryStore.EXTRA_CATEGORY)
            CategoryStore(this).all().find { it.key == key }?.let(::selectCategory)
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
        if (!ensureVaultAccess()) return
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
        val restoredCategory = savedInstanceState?.getString(CategoryStore.EXTRA_CATEGORY)
        if (restoredCategory != null) {
            CategoryStore(this).all().find { it.key == restoredCategory }?.let(::showCategory)
        }
        etService = findViewById(R.id.etService)
        etUser = findViewById(R.id.etUser)
        etUrl = findViewById(R.id.etUrl)
        etNotes = findViewById(R.id.etNotes)
        viewStrengthBar = findViewById(R.id.viewStrengthBar)
        tvStrengthHint = findViewById(R.id.tvStrengthHint)

        etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
        btnTogglePassword.setImageResource(R.drawable.ojo)
        // El campo arranca vacío (se ignora el texto de ejemplo del diseño).
        // Solo se llena si llega una contraseña desde el generador del menú.
        etPassword.setText(intent.getStringExtra("EXTRA_GENERATED_PASSWORD") ?: "")
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
            val layoutRowFrequent2 = view.findViewById<View>(R.id.layoutRowFrequent2)

            val tvHeaderFrecuentes = view.findViewById<TextView>(R.id.tvHeaderFrecuentes)
            val tvHeaderPersonales = view.findViewById<TextView>(R.id.tvHeaderPersonales)
            val tvHeaderFinanzas = view.findViewById<TextView>(R.id.tvHeaderFinanzas)
            val tvHeaderTrabajo = view.findViewById<TextView>(R.id.tvHeaderTrabajo)
            val btnNewCategory = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnNewCategory)

            btnNewCategory?.setOnClickListener {
                dialog.dismiss()
                categoryLauncher.launch(Intent(this, NuevaCategoriaActivity::class.java))
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

            val categories = CategoryStore(this).all()
            val byKey = categories.associateBy { it.key }
            val fallback = byKey.getValue(CategoryStore.FALLBACK)
            bindCategoryOption(dialog, rowOther, fallback, frequent = false)

            val frequent = categoryCounts.entries
                .filter { it.key != fallback.key && it.value >= 3 }
                .sortedByDescending { it.value }
                .mapNotNull { byKey[it.key] }
                .take(3)
            val slots = listOf(rowFrequentExtra1, rowFrequentExtra2, rowFrequentExtra3)
            for ((index, slot) in slots.withIndex()) {
                val category = frequent.getOrNull(index)
                slot.visibility = if (category == null) View.GONE else View.VISIBLE
                category?.let { bindCategoryOption(dialog, slot, it, frequent = true) }
            }

            val containerCategoryList = view.findViewById<ViewGroup>(R.id.containerCategoryList)
            val groupHeaders = mutableMapOf(
                CategoryGroup.PERSONAL to tvHeaderPersonales,
                CategoryGroup.FINANCE to tvHeaderFinanzas,
                CategoryGroup.WORK to tvHeaderTrabajo
            )
            for (group in CategoryGroup.entries) {
                if (group == CategoryGroup.FREQUENT || group in groupHeaders) continue
                val header = TextView(view.context).apply {
                    text = group.label
                    setTextColor(tvHeaderTrabajo.currentTextColor)
                    typeface = tvHeaderTrabajo.typeface
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, tvHeaderTrabajo.textSize)
                }
                containerCategoryList.addView(header, tvHeaderTrabajo.layoutParams)
                groupHeaders[group] = header
            }
            val rows = mutableListOf<Pair<VaultCategory, View>>()
            for ((group, header) in groupHeaders) {
                var index = containerCategoryList.indexOfChild(header) + 1
                for (category in categories.filter { it.group == group }) {
                    val row = layoutInflater.categoryRow(containerCategoryList, category)
                    bindCategoryOption(dialog, row, category, frequent = false)
                    containerCategoryList.addView(row, index++)
                    rows.add(category to row)
                }
            }
            var frequentIndex = containerCategoryList.indexOfChild(layoutRowFrequent2) + 1
            for (category in categories.filter { it.group == CategoryGroup.FREQUENT && it.key != fallback.key }) {
                val row = layoutInflater.categoryRow(containerCategoryList, category)
                bindCategoryOption(dialog, row, category, frequent = false)
                containerCategoryList.addView(row, frequentIndex++)
                rows.add(category to row)
            }

            val tvNoEncontrado = TextView(view.context).apply {
                text = "No se encontraron categorías"
                setTextColor(Color.parseColor("#8E94A0"))
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 40, 0, 40)
                visibility = View.GONE
            }

            containerCategoryList.addView(tvNoEncontrado)

            fun filterCategories(query: String) {
                val showFallback = fallback.name.contains(query, ignoreCase = true)
                rowOther.visibility = if (showFallback) View.VISIBLE else View.GONE
                slots.forEachIndexed { index, slot ->
                    slot.visibility = if (query.isEmpty() && index < frequent.size) View.VISIBLE else View.GONE
                }
                layoutRowFrequent2.visibility = if (query.isEmpty() && frequent.size >= 2) View.VISIBLE else View.GONE
                val visibleGroups = mutableSetOf<CategoryGroup>()
                for ((category, row) in rows) {
                    val visible = category.name.contains(query, ignoreCase = true)
                    row.visibility = if (visible) View.VISIBLE else View.GONE
                    if (visible) visibleGroups.add(category.group)
                }
                for ((group, header) in groupHeaders) {
                    header.visibility = if (group in visibleGroups) View.VISIBLE else View.GONE
                }
                tvHeaderFrecuentes.visibility = if (showFallback || CategoryGroup.FREQUENT in visibleGroups) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                tvNoEncontrado.visibility = if (showFallback || visibleGroups.isNotEmpty()) View.GONE else View.VISIBLE
            }
            etSearchCategory.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { filterCategories(s.toString().trim()) }
            })
            filterCategories("")

            dialog.show()
        }

        btnSaveCredential?.setOnClickListener {
            val servicio = etService.text.toString().trim()
            val usuario = etUser.text.toString().trim()
            val pass = etPassword.text.toString()
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
            val categoriaFinal = selectedCategory ?: CategoryStore.FALLBACK
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
                vaultJob {
                    VaultService.save(nuevaCredencial)
                    mostrarBannerPersonalizado("Credencial guardada", "Guardada con cifrado de extremo a extremo", R.drawable.logo)
                    finish()
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
    }

    private fun guardarFrecuenciasLocal() {
        // Derived from encrypted credentials.
    }

    private fun cargarFrecuenciasDeCache() {
        categoryCounts.clear(); categoryLastUsedTime.clear()
        VaultRepository.credentialList.forEach {
            categoryCounts[it.category] = (categoryCounts[it.category] ?: 0) + 1
            categoryLastUsedTime[it.category] = maxOf(categoryLastUsedTime[it.category] ?: 0L, it.updatedAt)
        }
    }

    /** Cuenta con correo: trae los contadores de la nube (así vuelven tras reinstalar). */
    private fun cargarFrecuenciasDeNube() {
        // Statistics are computed locally from decrypted records.
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(CategoryStore.EXTRA_CATEGORY, selectedCategory)
    }

    private fun bindCategoryOption(dialog: BottomSheetDialog, row: View, category: VaultCategory, frequent: Boolean) {
        if (row.findViewById<View>(R.id.categoryRowIcon) == null) {
            encontrarTextView(row)?.text = category.name
            encontrarImageView(row)?.let(category::showIcon)
            encontrarMaterialCardView(row)?.setCardBackgroundColor(obtenerColorBgParaCategoria(category.key))
        }
        resaltarFila(row, category.key == selectedCategory, frequent)
        row.setOnClickListener {
            selectCategory(category)
            dialog.dismiss()
        }
    }

    private fun showCategory(category: VaultCategory) {
        selectedCategory = category.key
        tvCategory.text = category.name
        tvCategory.setTextColor(colorNormal)
        tvCategory.setTypeface(null, Typeface.BOLD)
        category.showIcon(ivCatIcon)
        category.showIcon(ivTopCategoryIcon)
    }

    private fun selectCategory(category: VaultCategory) {
        showCategory(category)
        categoryCounts[category.key] = (categoryCounts[category.key] ?: 0) + 1
        categoryLastUsedTime[category.key] = System.currentTimeMillis()
        guardarFrecuenciasEnCache()
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
