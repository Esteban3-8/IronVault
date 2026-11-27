package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return
        setContentView(R.layout.activity_main)

        configurarAvatarInicial()

        containerCredentials = findViewById(R.id.containerCredentials)
        progressBarLoading = findViewById(R.id.progressBarLoading)
        etSearch = findViewById(R.id.etSearch)

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
            mostrarCredenciales(VaultRepository.credentialList.filter {
                it.strengthStatus.contains("Segura", true) || it.strengthStatus.contains("Alta", true)
            })
        }

        btnDebiles?.setOnClickListener {
            mostrarCredenciales(VaultRepository.credentialList.filter {
                it.strengthStatus.contains("Débil", true)
            })
        }

        btnMedias?.setOnClickListener {
            mostrarCredenciales(VaultRepository.credentialList.filter {
                it.strengthStatus.contains("Media", true)
            })
        }

        btnRepetidas?.setOnClickListener {
            mostrarCredenciales(VaultRepository.credentialList.filter {
                it.strengthStatus.contains("Repetida", true)
            })
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

        bindBottomNavigation(NavTab.HOME)
    }

    override fun onResume() {
        super.onResume()
        if (!ensureVaultAccess()) return

        // Actualizar avatar al volver por si el nombre cambió en Firestore
        configurarAvatarInicial()

        // ── Invitado: siempre se carga desde el dispositivo (no hay Firebase) ──
        if (GuestUtils.esInvitado(this)) {
            VaultRepository.cargarDeInvitado(this)
        }

        calcularYMostrarEstadisticas(VaultRepository.credentialList)
        filtrarCredenciales(etSearch.text.toString())
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
        val credenciales = VaultRepository.credentialList
        val resultados = if (query.isEmpty()) credenciales else credenciales.filter {
            it.serviceName.contains(query, true) || it.userOrEmail.contains(query, true)
        }
        mostrarCredenciales(resultados)
    }

    private fun mostrarCredenciales(credenciales: List<Credential>) {
        if (!::containerCredentials.isInitialized) return
        containerCredentials.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (cred in credenciales) {
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
