package com.example.myapplication
import android.content.Intent
import android.os.Bundle
/** Account password changes live in ProfileActivity; master changes use ChangeMasterKeyActivity. */
class PrincipalPasswordActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return
        startActivity(Intent(this, ProfileActivity::class.java)); finish()
    }
}
