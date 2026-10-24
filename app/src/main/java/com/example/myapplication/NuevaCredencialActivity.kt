package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

class NuevaCredencialActivity : AppCompatActivity() {

    private lateinit var etPassword: EditText
    private lateinit var tvCategory: TextView

    // Launcher para recibir de vuelta la contraseña generada
    private val passwordGeneratorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val generatedPassword = result.data?.getStringExtra("EXTRA_GENERATED_PASSWORD")
            if (!generatedPassword.isNullOrEmpty()) {
                etPassword.setText(generatedPassword)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nueva_credencial)

        // Vincular componentes de la interfaz
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnGeneratePassword = findViewById<ImageView>(R.id.btnGeneratePassword)
        val btnSelectCategory = findViewById<RelativeLayout>(R.id.btnSelectCategory)
        val btnSaveCredential = findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnSaveCredential)

        etPassword = findViewById(R.id.etPassword)
        tvCategory = findViewById(R.id.tvCategory)
        val etService = findViewById<EditText>(R.id.etService)
        val etUser = findViewById<EditText>(R.id.etUser)
        val etUrl = findViewById<EditText>(R.id.etUrl)
        val etNotes = findViewById<EditText>(R.id.etNotes)

        // 1. Clic en la flecha de retroceso -> Cierra la pantalla
        btnBack?.setOnClickListener {
            finish()
        }

        // 2. Clic en el icono de la llave -> Abre el generador de contraseñas
        btnGeneratePassword?.setOnClickListener {
            val intent = Intent(this, PasswordGeneratorActivity::class.java)
            passwordGeneratorLauncher.launch(intent)
        }

        // 3. Clic en la categoría -> Despliega tu BottomSheet desde abajo con el diseño 'dialog_category'
        btnSelectCategory?.setOnClickListener {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_category, null)
            val dialog = BottomSheetDialog(this)
            dialog.setContentView(view)
            dialog.show()
        }

        // 4. Clic en el botón "Guardar credencial" -> Valida y procesa
        btnSaveCredential?.setOnClickListener {
            val servicio = etService.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (servicio.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "El servicio y la contraseña son obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "Credencial guardada con éxito", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}