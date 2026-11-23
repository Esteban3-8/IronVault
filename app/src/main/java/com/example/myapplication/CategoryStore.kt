package com.example.myapplication

import android.content.Context
import android.widget.ImageView
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

internal enum class CategoryGroup(val label: String) {
    PERSONAL("Cuentas personales"),
    FINANCE("Finanzas y servicios"),
    WORK("Trabajo y educación"),
    FREQUENT("Frecuentes")
}

internal class CategoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "vault_categories_${FirebaseAuth.getInstance().currentUser?.uid ?: "guest"}",
        Context.MODE_PRIVATE
    )

    fun all(): List<VaultCategory> {
        val categories = defaults.associateByTo(linkedMapOf()) { it.key }
        for ((key, value) in preferences.all) {
            val data = (value as? String)?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: continue
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
        preferences.edit().putString(category.key, data.toString()).apply()
    }

    fun delete(key: String) {
        preferences.edit().putString(key, JSONObject().put("deleted", true).toString()).apply()
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
