package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileActionsTest {
    @Test fun accountActionsAreAvailableInSettingsInsteadOfProfile() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_MyApplication)
            val inflater = LayoutInflater.from(context)
            val profile = inflater.inflate(R.layout.activity_profile, null)
            val settings = inflater.inflate(R.layout.activity_settings, null)
            val changePassword = inflater.inflate(R.layout.activity_change_account_password, null)
            val logoutConfirmation = inflater.inflate(R.layout.dialog_logout, null)

            assertNull(profile.findViewById<View>(R.id.btnChangePassword))
            assertNull(profile.findViewById<View>(R.id.btnLogout))
            assertNotNull(profile.findViewById<View>(R.id.tvEditName))
            assertNotNull(settings.findViewById<View>(R.id.cardMainPassword))
            assertNotNull(settings.findViewById<View>(R.id.btnLogout))
            assertNotNull(changePassword.findViewById<View>(R.id.btnChangePassword))
            assertNotNull(logoutConfirmation.findViewById<View>(R.id.btnLogoutConfirm))
        }
    }
}
