package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class SplashActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val lockReason = intent.getStringExtra("lock_reason")

        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = Firebase.auth.currentUser

            if (currentUser != null) {
                val userId = currentUser.uid

                Firebase.firestore.collection("users").document(userId).get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            irADesbloquear(lockReason)
                        } else {
                            Firebase.auth.signOut()
                            irABienvenida()
                        }
                    }
                    .addOnFailureListener {
                        Firebase.auth.signOut()
                        irABienvenida()
                    }
            } else if (GuestUtils.esInvitado(this) && GuestUtils.tieneClave(this)) {
                // Invitado que ya creó su clave maestra: directo a ingresarla
                irADesbloquear(lockReason)
            } else {
                irABienvenida()
            }
        }, 3000)
    }

    private fun irADesbloquear(lockReason: String?) {
        val intent = Intent(this, UnlockVaultActivity::class.java).apply {
            putExtra("lock_reason", lockReason)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun irABienvenida() {
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}