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
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Firebase

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
        if (!ensureVaultAccess()) return
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
        if (!ensureVaultAccess()) return

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

    // El caché guarda 9 campos (id al inicio + los 8 campos originales)
    private fun guardarCredencialesEnCache(lista: List<Credential>) {
        // No plaintext cache.
    }

    // Retrocompatible: si el caché tiene 8 campos (sin id) genera uno nuevo automáticamente
    private fun cargarCredencialesDeCache(): List<Credential> {
        return emptyList()
    }

    private fun cargarListaCredenciales() {
        if (!GuestUtils.esInvitado(this) && !VaultService.unlocked) return
        listaActualMostrada = VaultRepository.credentialList
        calcularYMostrarEstadisticas(VaultRepository.credentialList)
        mostrarCredencialesPaginadas()
        progressBarLoading.visibility = View.GONE
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
                        putExtra("credential_id", cred.id)
                        putExtra("strengthStatus", cred.strengthStatus)
                        putExtra("url", cred.url)
                        putExtra("notes", cred.notes)
                        putExtra("category", cred.category)
                        putExtra("updatedAt", cred.updatedAt)
                    }
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