package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class SplashActivity : BaseActivity() { // 👈 Hereda de BaseActivity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // ── Leer el motivo de bloqueo si viene desde MainActivity por inactividad ──
        val lockReason = intent.getStringExtra("lock_reason")

        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = Firebase.auth.currentUser

            if (currentUser != null) {
                val userId = currentUser.uid

                // Verificamos si el documento del usuario realmente existe en Firestore
                Firebase.firestore.collection("users").document(userId).get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            // ¡El usuario existe! Mandamos a UnlockVaultActivity pasando el motivo de bloqueo
                            val intent = Intent(this, UnlockVaultActivity::class.java).apply {
                                putExtra("lock_reason", lockReason) // Mantiene "auto" si vino por inactividad
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            // Firebase Auth dice que hay sesión, pero NO hay datos en Firestore
                            Firebase.auth.signOut()
                            irABienvenida()
                        }
                    }
                    .addOnFailureListener {
                        // Si falla la red o hay error, por seguridad limpiamos y mandamos a bienvenida
                        Firebase.auth.signOut()
                        irABienvenida()
                    }
            } else {
                // Si NO hay sesión activa en Auth, va a la pantalla de bienvenida normal
                irABienvenida()
            }

        }, 3000) // 3 segundos de Splash con la animación de carga
    }

    private fun irABienvenida() {
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}