package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

open class BaseActivity : AppCompatActivity() {
    private var redirectingToUnlock = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun setContentView(layoutResID: Int) {
        if (NavTab.entries.any { it.screen == javaClass }) enableEdgeToEdge()
        super.setContentView(layoutResID)
        fun preventSavedSecrets(view: android.view.View) {
            if (view is android.widget.TextView) view.isSaveEnabled = false
            if (view is ViewGroup) for (i in 0 until view.childCount) preventSavedSecrets(view.getChildAt(i))
        }
        findViewById<android.view.View>(android.R.id.content)?.let(::preventSavedSecrets)
        aplicarInsetsAdaptables()
    }

    private fun aplicarInsetsAdaptables() {
        val contentFrame = findViewById<ViewGroup>(android.R.id.content)
        val rootView = contentFrame?.getChildAt(0) ?: return

        val extraTop = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            12f,
            resources.displayMetrics
        ).toInt()

        val initialTop = rootView.paddingTop
        val initialBottom = rootView.paddingBottom
        val initialLeft = rootView.paddingLeft
        val initialRight = rootView.paddingRight

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(systemBars.bottom, imeInsets.bottom)

            view.setPadding(
                initialLeft + systemBars.left,
                initialTop + systemBars.top + extraTop,
                initialRight + systemBars.right,
                initialBottom + bottomInset
            )
            insets
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        animateTabContent(intent)
    }

    override fun onStart() {
        super.onStart()
        ensureVaultAccess()
    }

    internal fun requiresVault() = !isPublicScreen() && !GuestUtils.esInvitado(this)
    protected fun ensureVaultAccess(): Boolean {
        if (!requiresVault() || VaultService.unlocked) return true
        if (redirectingToUnlock) return false
        redirectingToUnlock = true
        startActivity(Intent(this, UnlockVaultActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
        return false
    }
    override fun onResume() {
        super.onResume()
        ensureVaultAccess()
    }
    private fun isPublicScreen() = this is VaultAccessActivity || this is SplashActivity ||
        this is WelcomeActivity || this is LoginActivity || this is RegisterActivity ||
        this is ForgotPasswordActivity || this is ResetPasswordActivity || this is VerifyCodeActivity ||
        this is GuestModeActivity || this is BusinessRulesActivity

}
