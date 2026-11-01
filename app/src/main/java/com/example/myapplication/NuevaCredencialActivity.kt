package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class NuevaCredencialActivity : AppCompatActivity() {

    private lateinit var etPassword: EditText
    private lateinit var tvCategory: TextView
    private lateinit var etService: EditText
    private lateinit var etUser: EditText
    private lateinit var etUrl: EditText
    private lateinit var etNotes: EditText

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
        etService = findViewById(R.id.etService)
        etUser = findViewById(R.id.etUser)
        etUrl = findViewById(R.id.etUrl)
        etNotes = findViewById(R.id.etNotes)

        // 1. Clic en la flecha de retroceso -> Cierra la pantalla
        btnBack?.setOnClickListener {
            finish()
        }

        // 2. Clic en el icono de la llave -> Abre el generador de contraseñas
        btnGeneratePassword?.setOnClickListener {
            val intent = Intent(this, PasswordGeneratorActivity::class.java)
            passwordGeneratorLauncher.launch(intent)
        }

        // 3. Clic en la categoría -> Despliega el BottomSheet
        btnSelectCategory?.setOnClickListener {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_category, null)
            val dialog = BottomSheetDialog(this)
            dialog.setContentView(view)
            dialog.show()
        }

        // 4. Clic en el botón "Guardar credencial" -> Captura todos los campos y los guarda en Firestore
        btnSaveCredential?.setOnClickListener {
            val servicio = etService.text.toString().trim()
            val usuario = etUser.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            val url = etUrl.text.toString().trim()
            val notas = etNotes.text.toString().trim()

            if (servicio.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "El servicio y la contraseña son obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Calculamos la fortaleza
            val fortaleza = if (pass.length > 8 && pass.contains(Regex("[0-9]"))) "Segura" else "Débil"
            val usuarioFinal = if (usuario.isEmpty()) "Sin usuario" else usuario

            // Creamos la credencial con la información completa de la pantalla
            val nuevaCredencial = Credential(
                serviceName = servicio,
                userOrEmail = usuarioFinal,
                password = pass,
                strengthStatus = fortaleza,
                url = url,
                notes = notas
            )

            // Guardar permanentemente en Firebase Firestore bajo el usuario actual
            val currentUser = Firebase.auth.currentUser
            val userId = currentUser?.uid

            if (userId != null) {
                val credMap = hashMapOf(
                    "serviceName" to servicio,
                    "userOrEmail" to usuarioFinal,
                    "password" to pass,
                    "strengthStatus" to fortaleza,
                    "url" to url,
                    "notes" to notas
                )

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .collection("credentials")
                    .add(credMap)
                    .addOnSuccessListener { documentReference ->
                        Log.e("IRONVAULT_DEBUG", "¡Guardado exitoso en: ${documentReference.path}")
                        VaultRepository.addCredential(nuevaCredencial)
                        Toast.makeText(this, "¡Credencial guardada con éxito!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Log.e("IRONVAULT_DEBUG", "Error al guardar: ${e.message}")
                        Toast.makeText(this, "Error al guardar en la nube: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                VaultRepository.addCredential(nuevaCredencial)
                Toast.makeText(this, "¡Credencial guardada localmente!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}