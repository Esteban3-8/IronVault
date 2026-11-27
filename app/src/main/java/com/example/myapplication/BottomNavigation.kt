package com.example.myapplication

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.core.view.children

internal enum class NavTab(val button: Int, val icon: Int, val label: String, val screen: Class<out Activity>) {
    HOME(R.id.btnNavHome, R.id.ivNavHome, "Inicio", MainActivity::class.java),
    SECURITY(R.id.btnNavLock, R.id.ivNavLock, "Seguridad", SecurityActivity::class.java),
    GENERATOR(R.id.btnNavKey, R.id.ivNavKey, "Generador de contraseñas", PasswordGeneratorActivity::class.java),
    SETTINGS(R.id.btnNavSettings, R.id.ivNavSettings, "Ajustes", SettingsActivity::class.java)
}

internal fun Activity.bindBottomNavigation(current: NavTab) {
    animateTabContent(intent)
    for (tab in NavTab.entries) {
        val button = findViewById<View>(tab.button)
        button.contentDescription = tab.label
        button.isSelected = tab == current
        if (button.isSelected) {
            button.setBackgroundResource(R.drawable.bg_nav_pill_active)
            findViewById<ImageView>(tab.icon).setColorFilter(getColor(R.color.orange_primary))
            continue
        }
        button.setOnClickListener {
            val destination = Intent(this, tab.screen)
            if (current == NavTab.SECURITY || current == NavTab.SETTINGS || tab == NavTab.HOME) {
                destination.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            destination.putExtra(TAB_FORWARD, tab.ordinal > current.ordinal)
            val transition = ActivityOptions.makeCustomAnimation(this, 0, 0)
            startActivity(destination, transition.toBundle())
            if (current == NavTab.GENERATOR || current == NavTab.SETTINGS) finish()
        }
    }
}

private const val TAB_FORWARD = "com.example.myapplication.TAB_FORWARD"

internal fun Activity.animateTabContent(source: Intent) {
    if (!source.hasExtra(TAB_FORWARD)) return
    val forward = source.getBooleanExtra(TAB_FORWARD, true)
    source.removeExtra(TAB_FORWARD)
    val bar = findViewById<View>(R.id.bottomNavContainer) ?: return
    val screen = bar.parent as ViewGroup
    val animation = if (forward) R.anim.nav_from_right else R.anim.nav_from_left
    for (child in screen.children) {
        if (child !== bar && child.visibility == View.VISIBLE) {
            child.startAnimation(AnimationUtils.loadAnimation(this, animation))
        }
    }
}
