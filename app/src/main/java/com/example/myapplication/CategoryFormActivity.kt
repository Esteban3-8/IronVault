package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

abstract class CategoryFormActivity(private val layout: Int) : BaseActivity() {
    private lateinit var category: VaultCategory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout)

        val store = CategoryStore(this)
        val existingKey = intent.getStringExtra(CategoryStore.EXTRA_CATEGORY)
        val existing = store.all().find { it.key == existingKey }
        if (layout == R.layout.activity_editar_categoria && existing == null) {
            finish()
            return
        }

        category = existing ?: VaultCategory("", "", CategoryGroup.PERSONAL, "ic_pick_lock")
        savedInstanceState?.let { state ->
            category = category.copy(
                group = CategoryGroup.valueOf(state.getString("group") ?: category.group.name),
                icon = state.getString("icon") ?: category.icon,
                color = if (state.containsKey("color")) state.getInt("color") else null
            )
        }

        val nameInput = findViewById<EditText>(R.id.etCategoryName)
        val groupLabel = findViewById<TextView>(R.id.tvGroup)
        val preview = findViewById<ImageView>(R.id.ivCategoryPreview)
        nameInput.setText(category.name)
        groupLabel.text = category.group.label
        category.showIcon(preview)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnIcon).setOnClickListener {
            showCategoryIconPicker(category) {
                category = it
                category.showIcon(preview)
            }
        }

        val dropdown = findViewById<View>(R.id.groupDropdown)
        val arrow = findViewById<ImageView>(R.id.ivGroupArrow)
        fun showGroups(expanded: Boolean) {
            dropdown.visibility = if (expanded) View.VISIBLE else View.GONE
            arrow.setImageResource(if (expanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down)
        }

        findViewById<View>(R.id.groupHeader).setOnClickListener {
            showGroups(dropdown.visibility != View.VISIBLE)
        }
        val groupOptions = mapOf(
            R.id.optPersonales to CategoryGroup.PERSONAL,
            R.id.optFinanzas to CategoryGroup.FINANCE,
            R.id.optTrabajo to CategoryGroup.WORK,
            R.id.optFrecuentes to CategoryGroup.FREQUENT
        )
        for ((id, group) in groupOptions) {
            findViewById<View>(id).setOnClickListener {
                category = category.copy(group = group)
                groupLabel.text = group.label
                showGroups(false)
            }
        }

        findViewById<View>(R.id.btnSave).setOnClickListener {
            val name = nameInput.text.toString().trim()
            when {
                name.isEmpty() -> nameInput.error = "Ingresa un nombre"
                store.all().any {
                    it.key != existingKey && (it.name.equals(name, true) || it.key.equals(name, true))
                } -> nameInput.error = "Ya existe una categoría con ese nombre"
                else -> {
                    category = category.copy(key = existingKey ?: name, name = name)
                    store.save(category)
                    setResult(RESULT_OK, Intent().putExtra(CategoryStore.EXTRA_CATEGORY, category.key))
                    finish()
                }
            }
        }

        findViewById<View>(R.id.btnDelete)?.apply {
            if (existingKey == CategoryStore.FALLBACK) visibility = View.GONE
            setOnClickListener {
                val content = layoutInflater.inflate(R.layout.dialog_eliminar_categoria, null)
                val dialog = AlertDialog.Builder(this@CategoryFormActivity).setView(content).create()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                content.findViewById<View>(R.id.btnCancelDelete).setOnClickListener { dialog.dismiss() }
                content.findViewById<View>(R.id.btnConfirmDelete).setOnClickListener {
                    store.delete(category.key)
                    dialog.dismiss()
                    finish()
                }
                dialog.show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::category.isInitialized) {
            outState.putString("group", category.group.name)
            outState.putString("icon", category.icon)
            category.color?.let { outState.putInt("color", it) }
        }
    }
}
