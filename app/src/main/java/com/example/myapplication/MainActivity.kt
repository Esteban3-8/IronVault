package com.example.myapplication

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class MainActivity : BaseActivity() {

    private lateinit var containerCredentials: LinearLayout
    private lateinit var progressBarLoading: ProgressBar
    private lateinit var etSearch: EditText

    private var estaExpandido = true

    // Variables de Paginación (10 por página)
    private var paginaActual = 0
    private val elementosPorPagina = 10
    private var listaActualMostrada: List<Credential> = listOf()

    // Controles de paginación en la posición superior
    private var tvAnterior: TextView? = null
    private var tvPaginaInfo: TextView? = null
    private var tvSiguiente: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        configurarAvatarInicial()

        containerCredentials = findViewById(R.id.containerCredentials)
        progressBarLoading = findViewById(R.id.progressBarLoading)
        etSearch = findViewById(R.id.etSearch)

        // Vincular los controles de paginación
        tvAnterior = findViewById(R.id.tvAnterior)
        tvPaginaInfo = findViewById(R.id.tvPaginaInfo)
        tvSiguiente = findViewById(R.id.tvSiguiente)

        val headerEstadoGeneral = findViewById<LinearLayout>(R.id.headerEstadoGeneral)
        val layoutSubcontadores = findViewById<LinearLayout>(R.id.layoutSubcontadores)

        headerEstadoGeneral?.setOnClickListener {
            if (estaExpandido) {
                layoutSubcontadores?.visibility = View.GONE
                estaExpandido = false
            } else {
                layoutSubcontadores?.visibility = View.VISIBLE
                estaExpandido = true
            }
        }

        val btnSeguras = findViewById<LinearLayout>(R.id.btnSeguras)
        val btnDebiles = findViewById<LinearLayout>(R.id.btnDebiles)
        val btnMedias = findViewById<LinearLayout>(R.id.btnMedias)
        val btnRepetidas = findViewById<LinearLayout>(R.id.btnRepetidas)

        btnSeguras?.setOnClickListener {
            paginaActual = 0
            listaActualMostrada = VaultRepository.credentialList.filter {
                it.strengthStatus.contains("Segura", ignoreCase = true) || it.strengthStatus.contains("Alta", ignoreCase = true)
            }
            mostrarCredencialesPaginadas()
        }

        btnDebiles?.setOnClickListener {
            paginaActual = 0
            listaActualMostrada = VaultRepository.credentialList.filter {
                it.strengthStatus.contains("Débil", ignoreCase = true)
            }
            mostrarCredencialesPaginadas()
        }

        btnMedias?.setOnClickListener {
            paginaActual = 0
            listaActualMostrada = VaultRepository.credentialList.filter {
                it.strengthStatus.contains("Media", ignoreCase = true)
            }
            mostrarCredencialesPaginadas()
        }

        btnRepetidas?.setOnClickListener {
            paginaActual = 0
            listaActualMostrada = VaultRepository.credentialList.filter {
                it.strengthStatus.contains("Repetida", ignoreCase = true)
            }
            mostrarCredencialesPaginadas()
        }

        // Configurar acciones de los botones de página
        tvAnterior?.setOnClickListener {
            if (paginaActual > 0) {
                paginaActual--
                mostrarCredencialesPaginadas()
            }
        }

        tvSiguiente?.setOnClickListener {
            val maxPaginas = if (listaActualMostrada.isNotEmpty()) (listaActualMostrada.size - 1) / elementosPorPagina else 0
            if (paginaActual < maxPaginas) {
                paginaActual++
                mostrarCredencialesPaginadas()
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filtrarCredenciales(s.toString())
            }
        })

        val fabAdd = findViewById<MaterialCardView>(R.id.fabAdd)
        fabAdd?.setOnClickListener {
            startActivity(Intent(this, NuevaCredencialActivity::class.java))
        }

        // Invitado: el círculo abre el cuadro de cuenta. Con cuenta: igual que antes.
        val cardProfile = findViewById<MaterialCardView>(R.id.cardProfile)
        cardProfile?.setOnClickListener {
            if (GuestUtils.esInvitado(this)) {
                mostrarDialogoInvitado()
            } else {
                startActivity(Intent(this, CredentialDetailActivity::class.java))
            }
        }

        findViewById<LinearLayout>(R.id.btnNavLock)?.setOnClickListener {
            startActivity(Intent(this, SecurityActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnNavKey)?.setOnClickListener {
            startActivity(Intent(this, PasswordGeneratorActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnNavSettings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()

        // ── Invitado: siempre se carga desde el dispositivo (no hay Firebase) ──
        if (GuestUtils.esInvitado(this)) {
            VaultRepository.cargarDeInvitado(this)
            listaActualMostrada = VaultRepository.credentialList
            calcularYMostrarEstadisticas(VaultRepository.credentialList)
            paginaActual = 0
            mostrarCredencialesPaginadas()
            progressBarLoading.visibility = View.GONE
            return
        }

        if (VaultRepository.credentialList.isEmpty()) {
            val credsLocales = cargarCredencialesDeCache()
            if (credsLocales.isNotEmpty()) {
                VaultRepository.credentialList.clear()
                VaultRepository.credentialList.addAll(credsLocales)
                listaActualMostrada = VaultRepository.credentialList
                calcularYMostrarEstadisticas(VaultRepository.credentialList)
                paginaActual = 0
                mostrarCredencialesPaginadas()
                progressBarLoading.visibility = View.GONE
            }
        } else {
            listaActualMostrada = VaultRepository.credentialList
            calcularYMostrarEstadisticas(VaultRepository.credentialList)
            paginaActual = 0
            mostrarCredencialesPaginadas()
        }

        cargarListaCredenciales()
    }

    /** Cuadro del invitado: crear cuenta, iniciar sesión o seguir como invitado. */
    private fun mostrarDialogoInvitado() {
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog_card)
            setPadding(px(24), px(24), px(24), px(20))
        }

        val titulo = TextView(this).apply {
            text = "Modo invitado"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val mensaje = TextView(this).apply {
            text = "Tus datos solo están en este dispositivo. Con una cuenta se guardan en la nube."
            setTextColor(Color.parseColor("#8E94A0"))
            textSize = 14f
            setPadding(0, px(8), 0, px(8))
        }

        fun boton(texto: String, fondo: Int, color: Int, accion: () -> Unit): TextView {
            return TextView(this).apply {
                text = texto
                setTextColor(color)
                textSize = 15f
                gravity = Gravity.CENTER
                setBackgroundResource(fondo)
                setPadding(px(16), px(14), px(16), px(14))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = px(10) }
                setOnClickListener {
                    dialog.dismiss()
                    accion()
                }
            }
        }

        contenedor.addView(titulo)
        contenedor.addView(mensaje)
        contenedor.addView(
            boton("Crear una cuenta", R.drawable.bg_primary_button, Color.WHITE) {
                startActivity(Intent(this, RegisterActivity::class.java))
            }
        )
        contenedor.addView(
            boton("Ya tengo una cuenta", R.drawable.bg_secondary_button, Color.WHITE) {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        )
        contenedor.addView(
            boton("Seguir como invitado", R.drawable.bg_secondary_button, Color.parseColor("#8E94A0")) {}
        )

        dialog.setContentView(contenedor)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    // El caché guarda 8 campos (categoría y fecha de actualización al final)
    private fun guardarCredencialesEnCache(lista: List<Credential>) {
        val prefs = getSharedPreferences("IronVaultPermanentCache", MODE_PRIVATE)
        val editor = prefs.edit()
        val dataString = lista.joinToString("|||") {
            "${it.serviceName}###${it.userOrEmail}###${it.password}###${it.strengthStatus}###${it.url}###${it.notes}###${it.category}###${it.updatedAt}"
        }
        editor.putString("all_credentials_key", dataString)
        editor.apply()
    }

    // Lee la categoría si existe (el caché viejo de 6 campos sigue funcionando)
    private fun cargarCredencialesDeCache(): List<Credential> {
        val prefs = getSharedPreferences("IronVaultPermanentCache", MODE_PRIVATE)
        val dataString = prefs.getString("all_credentials_key", "") ?: ""
        val lista = mutableListOf<Credential>()
        if (dataString.isNotEmpty()) {
            val items = dataString.split("|||")
            for (item in items) {
                val partes = item.split("###")
                if (partes.size >= 6) {
                    lista.add(
                        Credential(
                            partes[0], partes[1], partes[2], partes[3], partes[4], partes[5],
                            partes.getOrElse(6) { "" },
                            partes.getOrElse(7) { "0" }.toLongOrNull() ?: 0L
                        )
                    )
                }
            }
        }
        return lista
    }

    private fun cargarListaCredenciales() {
        if (!::containerCredentials.isInitialized || !::progressBarLoading.isInitialized) return
        val userId = Firebase.auth.currentUser?.uid ?: return

        if (VaultRepository.credentialList.isEmpty()) {
            progressBarLoading.visibility = View.VISIBLE
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("credentials")
            .get()
            .addOnSuccessListener { documents ->
                progressBarLoading.visibility = View.GONE
                VaultRepository.credentialList.clear()

                val tempCloudList = mutableListOf<Credential>()
                for (document in documents) {
                    tempCloudList.add(
                        Credential(
                            serviceName = document.getString("serviceName") ?: "",
                            userOrEmail = document.getString("userOrEmail") ?: "",
                            password = document.getString("password") ?: "",
                            strengthStatus = document.getString("strengthStatus") ?: "Segura",
                            url = document.getString("url") ?: "",
                            notes = document.getString("notes") ?: "",
                            category = document.getString("category") ?: "",
                            updatedAt = document.getLong("updatedAt") ?: 0L
                        )
                    )
                }

                val passwordCounts = tempCloudList.groupingBy { it.password }.eachCount()
                for (cred in tempCloudList) {
                    val estadoFinal = if ((passwordCounts[cred.password] ?: 0) > 1) "Repetida" else cred.strengthStatus
                    VaultRepository.credentialList.add(cred.copy(strengthStatus = estadoFinal))
                }

                guardarCredencialesEnCache(VaultRepository.credentialList)

                listaActualMostrada = VaultRepository.credentialList
                calcularYMostrarEstadisticas(VaultRepository.credentialList)
                paginaActual = 0
                mostrarCredencialesPaginadas()
            }
            .addOnFailureListener {
                progressBarLoading.visibility = View.GONE
                listaActualMostrada = VaultRepository.credentialList
                calcularYMostrarEstadisticas(VaultRepository.credentialList)
                mostrarCredencialesPaginadas()
            }
    }

    private fun calcularYMostrarEstadisticas(lista: List<Credential>) {
        val seguras = lista.count { it.strengthStatus.contains("Segura", ignoreCase = true) || it.strengthStatus.contains("Alta", ignoreCase = true) }
        val debiles = lista.count { it.strengthStatus.contains("Débil", ignoreCase = true) }
        val medias = lista.count { it.strengthStatus.contains("Media", ignoreCase = true) }
        val repetidas = lista.count { it.strengthStatus.contains("Repetida", ignoreCase = true) }

        val total = seguras + debiles + medias + repetidas

        findViewById<TextView>(R.id.tvTotalCount)?.text = total.toString()
        findViewById<TextView>(R.id.tvSecureCount)?.text = seguras.toString()
        findViewById<TextView>(R.id.tvWeakCount)?.text = debiles.toString()
        findViewById<TextView>(R.id.tvMediumCount)?.text = medias.toString()
        findViewById<TextView>(R.id.tvRepeatedCount)?.text = repetidas.toString()
    }

    private fun filtrarCredenciales(query: String) {
        listaActualMostrada = if (query.isEmpty()) {
            VaultRepository.credentialList
        } else {
            VaultRepository.credentialList.filter {
                it.serviceName.contains(query, ignoreCase = true) || it.userOrEmail.contains(query, ignoreCase = true)
            }
        }
        paginaActual = 0
        mostrarCredencialesPaginadas()
    }

    private fun mostrarCredencialesPaginadas() {
        if (!::containerCredentials.isInitialized) return
        containerCredentials.removeAllViews()
        val inflater = LayoutInflater.from(this)

        val inicio = paginaActual * elementosPorPagina
        val fin = minOf(inicio + elementosPorPagina, listaActualMostrada.size)

        if (inicio < listaActualMostrada.size) {
            val subLista = listaActualMostrada.subList(inicio, fin)

            for (cred in subLista) {
                val itemView = inflater.inflate(R.layout.item_credential, containerCredentials, false)

                val tvInitial = itemView.findViewById<TextView>(R.id.tvServiceInitial)
                val tvName = itemView.findViewById<TextView>(R.id.tvServiceName)
                val tvUser = itemView.findViewById<TextView>(R.id.tvServiceUser)
                val tvStatus = itemView.findViewById<TextView>(R.id.tvStatusText)
                val vDot = itemView.findViewById<MaterialCardView>(R.id.vStatusDot)

                tvName.text = cred.serviceName
                tvUser.text = cred.userOrEmail
                tvInitial.text = if (cred.serviceName.isNotEmpty()) cred.serviceName.substring(0, 1).uppercase() else "C"
                tvStatus.text = cred.strengthStatus

                when {
                    cred.strengthStatus.contains("Repetida", ignoreCase = true) -> {
                        tvStatus.setTextColor(Color.parseColor("#9B9B9B"))
                        vDot?.setCardBackgroundColor(Color.parseColor("#9B9B9B"))
                    }
                    cred.strengthStatus.contains("Segura", ignoreCase = true) || cred.strengthStatus.contains("Alta", ignoreCase = true) -> {
                        tvStatus.setTextColor(Color.parseColor("#71C77D"))
                        vDot?.setCardBackgroundColor(Color.parseColor("#71C77D"))
                    }
                    cred.strengthStatus.contains("Débil", ignoreCase = true) -> {
                        tvStatus.setTextColor(Color.parseColor("#E26B6B"))
                        vDot?.setCardBackgroundColor(Color.parseColor("#E26B6B"))
                    }
                    cred.strengthStatus.contains("Media", ignoreCase = true) -> {
                        tvStatus.setTextColor(Color.parseColor("#E5C158"))
                        vDot?.setCardBackgroundColor(Color.parseColor("#E5C158"))
                    }
                    else -> {
                        tvStatus.setTextColor(Color.parseColor("#9B9B9B"))
                        vDot?.setCardBackgroundColor(Color.parseColor("#9B9B9B"))
                    }
                }

                // Se envían los datos reales de la credencial (incluida la categoría) al detalle
                itemView.setOnClickListener {
                    val intent = Intent(this, CredentialDetailActivity::class.java).apply {
                        putExtra("serviceName", cred.serviceName)
                        putExtra("userOrEmail", cred.userOrEmail)
                        putExtra("password", cred.password)
                        putExtra("strengthStatus", cred.strengthStatus)
                        putExtra("url", cred.url)
                        putExtra("notes", cred.notes)
                        putExtra("category", cred.category)
                        putExtra("updatedAt", cred.updatedAt)
                    }
                    Log.d("CAT_DEBUG", "Lista envía category = '${cred.category}'")
                    startActivity(intent)
                }

                containerCredentials.addView(itemView)
            }
        }

        // Actualizar el texto indicador de la página actual
        val totalPaginas = if (listaActualMostrada.isNotEmpty()) (listaActualMostrada.size - 1) / elementosPorPagina + 1 else 1
        tvPaginaInfo?.text = "Pág ${paginaActual + 1}/$totalPaginas"

        // Gestionar opacidad visual de los botones según el límite de páginas
        tvAnterior?.alpha = if (paginaActual > 0) 1.0f else 0.3f
        tvSiguiente?.alpha = if (paginaActual < totalPaginas - 1) 1.0f else 0.3f
    }

    private fun configurarAvatarInicial() {
        val currentUser = Firebase.auth.currentUser
        val tvUserInitial = findViewById<TextView>(R.id.tvUserInitial)

        // Invitado: el círculo muestra una "I" de Invitado
        if (GuestUtils.esInvitado(this)) {
            tvUserInitial?.text = "I"
            return
        }

        if (currentUser != null) {
            val email = currentUser.email ?: ""
            if (email.isNotEmpty() && tvUserInitial != null) {
                tvUserInitial.text = email.substring(0, 1).uppercase()
            }
        }
    }
}