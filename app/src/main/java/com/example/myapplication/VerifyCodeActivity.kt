package com.example.myapplication
import android.content.Intent
import android.os.Bundle
class VerifyCodeActivity : BaseActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return; startActivity(Intent(this, ForgotPasswordActivity::class.java)); finish() }
}
