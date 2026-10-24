package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class CredentialDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credential_detail)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack?.setOnClickListener {
            finish()
        }

        val btnEdit = findViewById<AppCompatButton>(R.id.btnEdit)
        btnEdit?.setOnClickListener {
            val intent = Intent(this, EditCredentialActivity::class.java)
            startActivity(intent)
        }
    }
}