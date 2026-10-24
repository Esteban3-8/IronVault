package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Clic en la credencial para ir al Detalle
        val ivPieza = findViewById<ImageView>(R.id.ivPieza)
        ivPieza?.setOnClickListener {
            val intent = Intent(this, CredentialDetailActivity::class.java)
            startActivity(intent)
        }

        // 2. Clic en el botón flotante (+) para agregar nueva credencial
        val fabAdd = findViewById<MaterialCardView>(R.id.fabAdd)
        fabAdd?.setOnClickListener {
            val intent = Intent(this, NuevaCredencialActivity::class.java)
            startActivity(intent)
        }

        // 3. Clic en el perfil (Círculo L) -> Lleva a Detalle o Perfil
        val cardProfile = findViewById<MaterialCardView>(R.id.cardProfile)
        cardProfile?.setOnClickListener {
            val intent = Intent(this, CredentialDetailActivity::class.java)
            startActivity(intent)
        }

        // ─────────────────────────────────────────────────────────────
        // 4. BARRA DE NAVEGACIÓN INFERIOR
        // ─────────────────────────────────────────────────────────────

        // Clic en el CANDADO -> Abre la pantalla de Seguridad (SecurityActivity)
        val btnNavLock = findViewById<LinearLayout>(R.id.btnNavLock)
        btnNavLock?.setOnClickListener {
            val intent = Intent(this, SecurityActivity::class.java)
            startActivity(intent)
        }

        // Clic en la LLAVE -> Abre el Generador de contraseñas (PasswordGeneratorActivity)
        val btnNavKey = findViewById<LinearLayout>(R.id.btnNavKey)
        btnNavKey?.setOnClickListener {
            val intent = Intent(this, PasswordGeneratorActivity::class.java)
            startActivity(intent)
        }

        // Clic en el ENGRANAJE/CONFIGURACIÓN -> Abre la pantalla de Ajustes (SettingsActivity)
        val btnNavSettings = findViewById<LinearLayout>(R.id.btnNavSettings)
        btnNavSettings?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

    }
}