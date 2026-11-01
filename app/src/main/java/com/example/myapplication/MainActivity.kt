package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var containerCredentials: LinearLayout
    private lateinit var progressBarLoading: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        configurarAvatarInicial()

        // Vincular vistas de la interfaz
        containerCredentials = findViewById(R.id.containerCredentials)
        progressBarLoading = findViewById(R.id.progressBarLoading)

        cargarListaCredenciales()

        // 1. Botón flotante (+) para ir a NuevaCredencialActivity
        val fabAdd = findViewById<MaterialCardView>(R.id.fabAdd)
        fabAdd?.setOnClickListener {
            val intent = Intent(this, NuevaCredencialActivity::class.java)
            startActivity(intent)
        }

        // 2. Perfil superior derecho
        val cardProfile = findViewById<MaterialCardView>(R.id.cardProfile)
        cardProfile?.setOnClickListener {
            val intent = Intent(this, CredentialDetailActivity::class.java)
            startActivity(intent)
        }

        // 3. Barra de Navegación Inferior
        val btnNavLock = findViewById<LinearLayout>(R.id.btnNavLock)
        btnNavLock?.setOnClickListener {
            startActivity(Intent(this, SecurityActivity::class.java))
        }

        val btnNavKey = findViewById<LinearLayout>(R.id.btnNavKey)
        btnNavKey?.setOnClickListener {
            startActivity(Intent(this, PasswordGeneratorActivity::class.java))
        }

        val btnNavSettings = findViewById<LinearLayout>(R.id.btnNavSettings)
        btnNavSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Recarga automáticamente la lista desde la subcolección de Firestore al volver al Home
        cargarListaCredenciales()
    }

    private fun cargarListaCredenciales() {
        if (!::containerCredentials.isInitialized || !::progressBarLoading.isInitialized) return

        val userId = Firebase.auth.currentUser?.uid ?: return
        val inflater = LayoutInflater.from(this)

        // 1. Mostrar el indicador de carga y limpiar vistas anteriores
        progressBarLoading.visibility = View.VISIBLE
        containerCredentials.removeAllViews()

        // Consulta en la subcolección correcta: users/{userId}/credentials
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("credentials")
            .get()
            .addOnSuccessListener { documents ->
                // 2. Ocultar la carga al recibir los datos exitosamente
                progressBarLoading.visibility = View.GONE
                VaultRepository.credentialList.clear()

                for (document in documents) {
                    val servicio = document.getString("serviceName") ?: ""
                    val usuario = document.getString("userOrEmail") ?: ""
                    val pass = document.getString("password") ?: ""
                    val status = document.getString("strengthStatus") ?: "Segura"

                    val cred = Credential(
                        serviceName = servicio,
                        userOrEmail = usuario,
                        password = pass,
                        strengthStatus = status
                    )
                    VaultRepository.credentialList.add(cred)
                }

                // Dibuja una tarjeta por cada credencial almacenada
                for (cred in VaultRepository.credentialList) {
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

                    when (cred.strengthStatus) {
                        "Segura" -> {
                            tvStatus.setTextColor(Color.parseColor("#71C77D"))
                            vDot?.setCardBackgroundColor(Color.parseColor("#71C77D"))
                        }
                        "Débil" -> {
                            tvStatus.setTextColor(Color.parseColor("#E26B6B"))
                            vDot?.setCardBackgroundColor(Color.parseColor("#E26B6B"))
                        }
                        else -> {
                            tvStatus.setTextColor(Color.parseColor("#9B9B9B"))
                            vDot?.setCardBackgroundColor(Color.parseColor("#9B9B9B"))
                        }
                    }

                    itemView.setOnClickListener {
                        startActivity(Intent(this, CredentialDetailActivity::class.java))
                    }

                    containerCredentials.addView(itemView)
                }
            }
            .addOnFailureListener { e ->
                // 3. Si ocurre un error, ocultar la carga y cargar respaldo local si existe
                progressBarLoading.visibility = View.GONE
                Log.e("IRONVAULT_DEBUG", "Error al cargar desde la nube: ${e.message}")
                containerCredentials.removeAllViews()

                for (cred in VaultRepository.credentialList) {
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

                    when (cred.strengthStatus) {
                        "Segura" -> {
                            tvStatus.setTextColor(Color.parseColor("#71C77D"))
                            vDot?.setCardBackgroundColor(Color.parseColor("#71C77D"))
                        }
                        "Débil" -> {
                            tvStatus.setTextColor(Color.parseColor("#E26B6B"))
                            vDot?.setCardBackgroundColor(Color.parseColor("#E26B6B"))
                        }
                        else -> {
                            tvStatus.setTextColor(Color.parseColor("#9B9B9B"))
                            vDot?.setCardBackgroundColor(Color.parseColor("#9B9B9B"))
                        }
                    }
                    containerCredentials.addView(itemView)
                }
            }
    }

    private fun configurarAvatarInicial() {
        val currentUser = Firebase.auth.currentUser
        val tvUserInitial = findViewById<TextView>(R.id.tvUserInitial)
        if (currentUser != null) {
            val email = currentUser.email ?: ""
            if (email.isNotEmpty() && tvUserInitial != null) {
                tvUserInitial.text = email.substring(0, 1).uppercase()
            }
        }
    }
}