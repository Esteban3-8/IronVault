package com.example.myapplication
import android.content.Intent
import android.os.Bundle
import com.google.firebase.auth.FirebaseAuth
class SplashActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return; VaultService.lock()
        val target = WelcomeActivity::class.java
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish()
    }
}
