package com.example.myapplication

import android.app.Application

class IronVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VaultService.initialize(this)
        AccountAccessHistory.initialize(this)
        registerActivityLifecycleCallbacks(VaultLifecycle(this))
    }
}
