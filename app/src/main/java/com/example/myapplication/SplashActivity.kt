package com.example.myapplication
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class SplashActivity : BaseActivity() {
    private companion object {
        const val STARTUP_DISPLAY_MS = 650L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L
    private val showWelcome = Runnable {
        if (!isFinishing && !isDestroyed) {
            startActivity(Intent(this, WelcomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return
        VaultService.lock(false)
        setContentView(R.layout.activity_splash)
        startedAt = savedInstanceState?.getLong("splash_started_at") ?: SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val remaining = STARTUP_DISPLAY_MS - (SystemClock.elapsedRealtime() - startedAt)
        handler.postDelayed(showWelcome, remaining.coerceAtLeast(0))
    }

    override fun onStop() {
        handler.removeCallbacks(showWelcome)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("splash_started_at", startedAt)
        super.onSaveInstanceState(outState)
    }
}
