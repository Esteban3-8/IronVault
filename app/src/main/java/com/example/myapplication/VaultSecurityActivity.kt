package com.example.myapplication
import android.content.Intent
import android.os.Bundle
class VaultSecurityActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return; startActivity(Intent(this, SettingsActivity::class.java)); finish() }
}
