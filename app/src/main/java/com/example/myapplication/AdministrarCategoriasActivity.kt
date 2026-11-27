package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged

class AdministrarCategoriasActivity : BaseActivity() {
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private var categories = emptyList<VaultCategory>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return
        setContentView(R.layout.activity_administrar_categorias)
        list = findViewById(R.id.manageCategoryList)
        search = findViewById(R.id.etSearch)
        search.doAfterTextChanged { renderCategories() }
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, NuevaCategoriaActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::list.isInitialized || !ensureVaultAccess()) return
        categories = CategoryStore(this).all()
        renderCategories()
    }

    private fun renderCategories() {
        list.removeAllViews()
        val query = search.text.toString().trim()
        for (group in CategoryGroup.entries) {
            val matches = categories.filter { it.group == group && it.name.contains(query, true) }
            if (matches.isEmpty()) continue
            list.addView((layoutInflater.inflate(R.layout.item_manage_category_header, list, false) as TextView).apply {
                text = group.label
            })
            for (category in matches) {
                list.addView(layoutInflater.categoryRow(list, category).apply {
                    setBackgroundResource(R.drawable.bg_card_row)
                    findViewById<View>(R.id.categoryRowChevron).visibility = View.VISIBLE
                    setOnClickListener {
                        startActivity(
                            Intent(this@AdministrarCategoriasActivity, EditarCategoriaActivity::class.java)
                                .putExtra(CategoryStore.EXTRA_CATEGORY, category.key)
                        )
                    }
                })
            }
        }
        if (list.childCount == 0) {
            list.addView(TextView(this).apply {
                text = if (query.isEmpty()) "Todavía no hay categorías" else "No se encontraron categorías"
                gravity = android.view.Gravity.CENTER
                setTextColor(getColor(R.color.text_soft))
            })
        }
    }
}
