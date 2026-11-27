package com.example.myapplication

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Screen-off always locks; background timeout does not reset merely because a prompt is displayed. */
internal class VaultLifecycle(private val app: Application) : Application.ActivityLifecycleCallbacks {
    companion object {
        private val activities = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Activity, Boolean>())
        private var resumed = java.lang.ref.WeakReference<Activity>(null)
        fun clearSensitiveScreens(navigate: Boolean = true) {
            fun clear(view: View) {
                if (view is TextView) view.text = ""
                if (view is ViewGroup) for (i in 0 until view.childCount) clear(view.getChildAt(i))
            }
            activities.toList().forEach { activity ->
                if (activity is BaseActivity && activity.requiresVault()) {
                    activity.findViewById<View>(android.R.id.content)?.let(::clear)
                    if (navigate && resumed.get() === activity && !activity.isFinishing) {
                        activity.startActivity(Intent(activity, UnlockVaultActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        activity.finish()
                    }
                }
            }
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private var started = 0
    private var deadline = 0L
    private val lock = Runnable { VaultService.lock() }
    init {
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { VaultService.lock(false) }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onActivityStarted(activity: Activity) {
        if (started == 0) {
            if (VaultTimeoutPolicy.expired(deadline, SystemClock.elapsedRealtime())) VaultService.lock()
            if (VaultService.unlocked) com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                ?.getIdToken(true)?.addOnFailureListener { VaultService.lock() }
        }
        started++; deadline = 0; handler.removeCallbacks(lock)
    }
    override fun onActivityStopped(activity: Activity) {
        started = maxOf(0, started - 1)
        if (started == 0 && VaultService.unlocked) {
            val value = app.getSharedPreferences("security_prefs", Context.MODE_PRIVATE).getString("auto_lock", "5 minutos")
            val delay = VaultTimeoutPolicy.delay(value)
            if (delay < 0) return
            deadline = SystemClock.elapsedRealtime() + delay
            handler.postDelayed(lock, delay)
        }
    }
    override fun onActivityCreated(a: Activity, b: Bundle?) { activities.add(a) }
    override fun onActivityResumed(a: Activity) { resumed = java.lang.ref.WeakReference(a) }
    override fun onActivityPaused(a: Activity) { if (resumed.get() === a) resumed.clear() }
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
    override fun onActivityDestroyed(a: Activity) { activities.remove(a) }
}
