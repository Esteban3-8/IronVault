package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

internal fun LayoutInflater.categoryRow(parent: ViewGroup, category: VaultCategory): View {
    val row = inflate(R.layout.item_vault_category, parent, false)
    row.findViewById<TextView>(R.id.categoryRowName).text = category.name
    category.showPreview(row.findViewById<ImageView>(R.id.categoryRowIcon))
    return row
}
