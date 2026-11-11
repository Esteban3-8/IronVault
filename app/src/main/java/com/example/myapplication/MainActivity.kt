package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
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
                it.strengthStatus.contains("Segura", true) || it.strengthStatus.contains("Alta", true)
            }
            mostrarCredencialesPaginadas()
        }

        btnDebiles?.setOnClickListener {
            paginaActual = 0
            listaActualMostrada = VaultRepository.credentialList.filter {
                it.strengthStatus.contains("Débil", true)
            }
            mostrarCredencialesPaginadas()
        }

        btnMedias?.setOnClickListener {
            paginaActual = 0
            listaActualMostrada = VaultRepository.credentialList.filter {
                it.strengthStatus.contains("Media", true)
            }
            mostrarCredencialesPaginadas()
        }

        btnRepetidas?.setOnClickListener {
            paginaActual = 0
            listaActualMostrada = VaultRepository.credentialList.filter {
                it.strengthStatus.contains("Repetida", true)
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

        // Invitado: el círculo abre el cuadro de cuenta unificado.
        // Con cuenta: abre la pantalla de perfil.
        val cardProfile = findViewById<MaterialCardView>(R.id.cardProfile)
        cardProfile?.setOnClickListener {
            if (GuestUtils.esInvitado(this)) {
                GuestUtils.mostrarDialogoCuenta(this)
            } else {
                startActivity(Intent(this, ProfileActivity::class.java))
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

        // Actualizar avatar al volver por si el nombre cambió en Firestore
        configurarAvatarInicial()

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
        val seguras = lista.count { it.strengthStatus.contains("Segura", true) || it.strengthStatus.contains("Alta", true) }
        val debiles = lista.count { it.strengthStatus.contains("Débil", true) }
        val medias = lista.count { it.strengthStatus.contains("Media", true) }
        val repetidas = lista.count { it.strengthStatus.contains("Repetida", true) }

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
                it.serviceName.contains(query, true) || it.userOrEmail.contains(query, true)
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
                    cred.strengthStatus.contains("Repetida", true) -> {
                        tvStatus.setTextColor(Color.parseColor("#9B9B9B"))
                        vDot?.setCardBackgroundColor(Color.parseColor("#9B9B9B"))
                    }
                    cred.strengthStatus.contains("Segura", true) || cred.strengthStatus.contains("Alta", true) -> {
                        tvStatus.setTextColor(Color.parseColor("#71C77D"))
                        vDot?.setCardBackgroundColor(Color.parseColor("#71C77D"))
                    }
                    cred.strengthStatus.contains("Débil", true) -> {
                        tvStatus.setTextColor(Color.parseColor("#E26B6B"))
                        vDot?.setCardBackgroundColor(Color.parseColor("#E26B6B"))
                    }
                    cred.strengthStatus.contains("Media", true) -> {
                        tvStatus.setTextColor(Color.parseColor("#E5C158"))
                        vDot?.setCardBackgroundColor(Color.parseColor("#E5C158"))
                    }
                    else -> {
                        tvStatus.setTextColor(Color.parseColor("#9B9B9B"))
                        vDot?.setCardBackgroundColor(Color.parseColor("#9B9B9B"))
                    }
                }

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

        val totalPaginas = if (listaActualMostrada.isNotEmpty()) (listaActualMostrada.size - 1) / elementosPorPagina + 1 else 1
        tvPaginaInfo?.text = "Pág ${paginaActual + 1}/$totalPaginas"

        tvAnterior?.alpha = if (paginaActual > 0) 1.0f else 0.3f
        tvSiguiente?.alpha = if (paginaActual < totalPaginas - 1) 1.0f else 0.3f
    }

    private fun configurarAvatarInicial() {
        val currentUser = Firebase.auth.currentUser
        val tvUserInitial = findViewById<TextView>(R.id.tvUserInitial)

        if (GuestUtils.esInvitado(this)) {
            tvUserInitial?.text = "I"
            return
        }

        if (currentUser != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.uid)
                .get()
                .addOnSuccessListener { doc ->
                    val fullName = doc.getString("fullName") ?: ""
                    val email = currentUser.email ?: ""

                    // Prioriza el nombre de Firestore para extraer la inicial ("F")
                    val base = if (fullName.isNotBlank()) fullName else email
                    if (base.isNotEmpty() && tvUserInitial != null) {
                        tvUserInitial.text = base.substring(0, 1).uppercase()
                    }
                }
        }
    }
}