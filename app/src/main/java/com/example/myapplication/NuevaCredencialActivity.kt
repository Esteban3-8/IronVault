package com.example.myapplication

import android.content.Intent
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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class NuevaCredencialActivity : AppCompatActivity() {

    private lateinit var etPassword: EditText
    private lateinit var tvCategory: TextView
    private lateinit var ivCatIcon: ImageView
    private lateinit var ivTopCategoryIcon: ImageView // Icono del cuadro grande superior
    private lateinit var etService: EditText
    private lateinit var etUser: EditText
    private lateinit var etUrl: EditText
    private lateinit var etNotes: EditText
    private lateinit var btnTogglePassword: ImageView
    private lateinit var viewStrengthBar: View
    private lateinit var tvStrengthHint: TextView

    // Variable para controlar si la contraseña se muestra u oculta (Inicia en TRUE por defecto)
    private var isPasswordVisible = true

    // Sin categoría seleccionada al inicio (null). Al guardar se usa "Otras Claves" si sigue en null.
    private var selectedCategory: String? = null

    private val colorSeleccionado = Color.parseColor("#F97316") // Naranja del tema
    private val colorNormal = Color.parseColor("#FFFFFF")

    // Launcher para recibir de vuelta la contraseña generada
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nueva_credencial)

        // Vincular componentes de la interfaz
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

        // CONFIGURACIÓN INICIAL: CONTRASEÑA VISIBLE Y OJO ABIERTO POR DEFECTO
        etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
        btnTogglePassword.setImageResource(R.drawable.ojo)
        etPassword.setSelection(etPassword.text.length)
        evaluarSeguridadContrasena(etPassword.text.toString())

        // 1. Flecha de retroceso
        btnBack?.setOnClickListener {
            finish()
        }

        // 2. Mostrar / ocultar contraseña
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

        // 3. Seguridad de la contraseña letra por letra
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                evaluarSeguridadContrasena(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 4. Generador de contraseñas
        btnGeneratePassword?.setOnClickListener {
            val intent = Intent(this, PasswordGeneratorActivity::class.java)
            passwordGeneratorLauncher.launch(intent)
        }

        // 5. Categoría -> BottomSheet
        btnSelectCategory?.setOnClickListener {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_category, null)
            val dialog = BottomSheetDialog(this)
            dialog.setContentView(view)

            configurarOpcionCategoria(dialog, view, R.id.rowOther, "Otras Claves", R.drawable.ic_cat_lupa)
            configurarOpcionCategoria(dialog, view, R.id.rowSocial, "Redes Sociales", R.drawable.ic_cat_social)
            configurarOpcionCategoria(dialog, view, R.id.rowMail, "Correos", R.drawable.ic_cat_mail)
            configurarOpcionCategoria(dialog, view, R.id.rowGames, "Videojuegos", R.drawable.ic_cat_games)
            configurarOpcionCategoria(dialog, view, R.id.rowBank, "Bancos", R.drawable.ic_cat_bank)
            configurarOpcionCategoria(dialog, view, R.id.rowShop, "Compras", R.drawable.ic_cat_shop)
            configurarOpcionCategoria(dialog, view, R.id.rowEdu, "Educación", R.drawable.ic_cat_edu)

            dialog.show()
        }

        // 6. Guardar credencial
        btnSaveCredential?.setOnClickListener {
            val servicio = etService.text.toString().trim()
            val usuario = etUser.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            val url = etUrl.text.toString().trim()
            val notas = etNotes.text.toString().trim()

            if (servicio.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "El servicio y la contraseña son obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fortaleza = tvStrengthHint.text.toString()
            val usuarioFinal = if (usuario.isEmpty()) "Sin usuario" else usuario
            val categoriaFinal = selectedCategory ?: "Otras Claves" // Si no eligió ninguna

            val nuevaCredencial = Credential(
                serviceName = servicio,
                userOrEmail = usuarioFinal,
                password = pass,
                strengthStatus = fortaleza,
                url = url,
                notes = notas
            )

            val userId = Firebase.auth.currentUser?.uid

            if (userId != null) {
                val credMap = hashMapOf(
                    "serviceName" to servicio,
                    "userOrEmail" to usuarioFinal,
                    "password" to pass,
                    "strengthStatus" to fortaleza,
                    "url" to url,
                    "notes" to notas,
                    "category" to categoriaFinal
                )

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .collection("credentials")
                    .add(credMap)
                    .addOnSuccessListener { documentReference ->
                        Log.e("IRONVAULT_DEBUG", "¡Guardado exitoso en: ${documentReference.path}")
                        VaultRepository.addCredential(nuevaCredencial)
                        Toast.makeText(this, "¡Credencial guardada con éxito!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Log.e("IRONVAULT_DEBUG", "Error al guardar: ${e.message}")
                        Toast.makeText(this, "Error al guardar en la nube: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                VaultRepository.addCredential(nuevaCredencial)
                Toast.makeText(this, "¡Credencial guardada localmente!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun configurarOpcionCategoria(dialog: BottomSheetDialog, view: View, idLayout: Int, nombre: String, iconoRes: Int) {
        val rowView = view.findViewById<View>(idLayout) ?: return

        resaltarFila(rowView, nombre == selectedCategory, idLayout == R.id.rowOther)

        rowView.setOnClickListener {
            selectedCategory = nombre
            tvCategory.text = nombre
            tvCategory.setTextColor(colorNormal)
            tvCategory.setTypeface(null, Typeface.BOLD)

            ivCatIcon.setImageResource(iconoRes)
            ivTopCategoryIcon.setImageResource(iconoRes)

            dialog.dismiss()
        }
    }

    // Frecuentes: rectángulo pequeño (tamaño del contenido). Gris normal, naranja si está
    //             seleccionada. El texto siempre queda blanco.
    // Las demás:  sin fondo; si están seleccionadas, fondo naranja a todo el ancho y texto naranja.
    private fun resaltarFila(row: View, activa: Boolean, esFrecuente: Boolean) {
        val density = resources.displayMetrics.density
        val padH = (12 * density).toInt()
        val padV = (8 * density).toInt()

        val params = row.layoutParams
        params.width = if (esFrecuente) ViewGroup.LayoutParams.WRAP_CONTENT
        else ViewGroup.LayoutParams.MATCH_PARENT
        row.layoutParams = params
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

    // Busca el primer TextView dentro de la fila (el nombre de la categoría)
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

    private fun evaluarSeguridadContrasena(password: String) {
        val rojoDebil = Color.parseColor("#EF4444")
        val amarilloMedio = Color.parseColor("#F59E0B")
        val verdeFuerte = Color.parseColor("#8FC954")

        val params = viewStrengthBar.layoutParams as ViewGroup.LayoutParams

        when {
            password.isEmpty() -> {
                params.width = 0
                viewStrengthBar.layoutParams = params
                tvStrengthHint.text = "Mínimo 6 caracteres"
                tvStrengthHint.setTextColor(Color.parseColor("#9CA3AF"))
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