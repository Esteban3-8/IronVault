package com.example.myapplication

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

internal data class VaultCategory(
    val key: String,
    val name: String,
    val group: CategoryGroup,
    val icon: String,
    val color: Int? = null
) {
    fun showIcon(view: ImageView) {
        val resource = view.resources.getIdentifier(icon, "drawable", view.context.packageName)
        view.setImageResource(if (resource == 0) R.drawable.ic_pick_lock else resource)
        view.imageTintList = null
        if (color == null) view.clearColorFilter() else view.setColorFilter(color)
    }
}

internal fun VaultCategory.showPreview(view: ImageView) {
    showIcon(view)
    val container = view.parent as View
    container.backgroundTintList = color?.let { ColorStateList.valueOf(it) }
    color?.let { background ->
        val darkContrast = ColorUtils.calculateContrast(Color.BLACK, background)
        val lightContrast = ColorUtils.calculateContrast(Color.WHITE, background)
        view.setColorFilter(if (darkContrast >= lightContrast) Color.BLACK else Color.WHITE)
    }
}

internal enum class CategoryGroup(val label: String) {
    PERSONAL("Cuentas personales"),
    FINANCE("Finanzas y servicios"),
    WORK("Trabajo y educación"),
    FREQUENT("Frecuentes"),
    ENTERTAINMENT("Entretenimiento"),
    HEALTH("Salud y bienestar"),
    TRAVEL("Viajes"),
    HOME("Hogar")
}

internal class CategoryStore(context: Context) {
    private val account = FirebaseAuth.getInstance().currentUser?.uid
    private val legacy = context.getSharedPreferences("vault_categories_${account ?: "guest"}", Context.MODE_PRIVATE)
    private val preferences = context.getSharedPreferences(
        "vault_categories_v3_${account ?: "guest"}",
        Context.MODE_PRIVATE
    )

    private fun read(): JSONObject {
        if (account != null) VaultService.requireUnlocked()
        val stored = preferences.getString("categories", null)
        if (stored != null) return JSONObject(if (account != null) VaultService.localOpen(stored, "categories") else stored)
        // Preserve existing local custom categories. Originals remain for supervised cleanup.
        val data = JSONObject()
        for ((key, value) in legacy.all) if (value is String) data.put(key, JSONObject(value))
        write(data)
        return data
    }

    private fun write(data: JSONObject) {
        val encoded = if (account != null) VaultService.localSeal(data.toString(), "categories") else data.toString()
        check(preferences.edit().putString("categories", encoded).commit()) { "No se pudieron guardar las categorías" }
    }

    fun all(): List<VaultCategory> {
        val categories = defaults.associateByTo(linkedMapOf()) { it.key }
        val entries = read()
        for (key in entries.keys()) {
            val data = entries.getJSONObject(key)
            if (data.optBoolean("deleted")) {
                categories.remove(key)
                continue
            }
            val group = CategoryGroup.entries.find { it.name == data.optString("group") }
                ?: continue
            categories[key] = VaultCategory(
                key, data.getString("name"), group, data.getString("icon"),
                if (data.has("color")) data.getInt("color") else null
            )
        }
        return categories.values.toList()
    }

    fun save(category: VaultCategory) {
        val data = JSONObject()
            .put("name", category.name)
            .put("group", category.group.name)
            .put("icon", category.icon)
        category.color?.let { data.put("color", it) }
        write(read().put(category.key, data))
    }

    fun delete(key: String) {
        write(read().put(key, JSONObject().put("deleted", true)))
    }

    companion object {
        const val EXTRA_CATEGORY = "category_key"
        const val FALLBACK = "Otras Claves"

        private val defaults = listOf(
            VaultCategory("Redes Sociales", "Redes Sociales", CategoryGroup.PERSONAL, "ic_cat_social"),
            VaultCategory("Correos", "Correos", CategoryGroup.PERSONAL, "ic_cat_mail"),
            VaultCategory("Videojuegos", "Videojuegos", CategoryGroup.PERSONAL, "ic_cat_games"),
            VaultCategory("Bancos", "Bancos", CategoryGroup.FINANCE, "ic_cat_bank"),
            VaultCategory("Compras", "Compras", CategoryGroup.FINANCE, "ic_cat_shop"),
            VaultCategory("Educación", "Educación", CategoryGroup.WORK, "ic_cat_edu"),
            VaultCategory(FALLBACK, FALLBACK, CategoryGroup.WORK, "ic_cat_lupa")
        )
    }
}
