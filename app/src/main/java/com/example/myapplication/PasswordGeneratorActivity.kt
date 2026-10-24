package com.example.myapplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import java.security.SecureRandom

class PasswordGeneratorActivity : AppCompatActivity() {

    private lateinit var tvPassword: TextView
    private lateinit var seekBarLength: SeekBar
    private lateinit var tvLengthValue: TextView
    private lateinit var switchUppercase: SwitchCompat
    private lateinit var switchLowercase: SwitchCompat
    private lateinit var switchNumbers: SwitchCompat
    private lateinit var switchSymbols: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_generator)

        // 1. Botón para regresar con la flecha
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack?.setOnClickListener {
            finish()
        }

        // 2. Referencias de los componentes visuales
        tvPassword = findViewById(R.id.tvGeneratedPassword)
        seekBarLength = findViewById(R.id.seekBarLength)
        tvLengthValue = findViewById(R.id.tvLengthValue)

        switchUppercase = findViewById(R.id.switchUppercase)
        switchLowercase = findViewById(R.id.switchLowercase)
        switchNumbers = findViewById(R.id.switchNumbers)
        switchSymbols = findViewById(R.id.switchSymbols)

        val btnCopy = findViewById<AppCompatButton>(R.id.btnCopy)
        val btnGenerate = findViewById<AppCompatButton>(R.id.btnGenerateNew)
        val btnContinue = findViewById<AppCompatButton>(R.id.btnAcceptPassword)

        // Control del SeekBar (Longitud) en tiempo real
        seekBarLength.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Mínimo 4 caracteres para evitar errores
                val currentLength = if (progress < 4) 4 else progress

                // Actualiza el número de la derecha inmediatamente
                tvLengthValue.text = currentLength.toString()

                // Actualiza la contraseña al vuelo mientras deslizas
                if (fromUser) {
                    tvPassword.text = crearContrasenaAleatoria(currentLength)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Generar contraseña inicial al abrir la pantalla
        val initialLength = if (seekBarLength.progress < 4) 4 else seekBarLength.progress
        tvLengthValue.text = initialLength.toString()
        tvPassword.text = crearContrasenaAleatoria(initialLength)

        // 3. Botón "Generar de nuevo"
        btnGenerate?.setOnClickListener {
            val currentLength = if (seekBarLength.progress < 4) 4 else seekBarLength.progress
            tvPassword.text = crearContrasenaAleatoria(currentLength)
            Toast.makeText(this, "¡Nueva contraseña generada!", Toast.LENGTH_SHORT).show()
        }

        // 4. Botón "Copiar al Portapapeles"
        btnCopy?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Contraseña IronVault", tvPassword.text.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "¡Contraseña copiada!", Toast.LENGTH_SHORT).show()
        }

        // 5. Botón naranja "Continuar con esta contraseña"
        btnContinue?.setOnClickListener {
            val passwordGenerada = tvPassword.text.toString()
            if (passwordGenerada.isNotEmpty()) {
                val resultIntent = Intent().apply {
                    putExtra("EXTRA_GENERATED_PASSWORD", passwordGenerada)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "Genera una contraseña primero", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Función para crear la contraseña combinando la barra y los switches activos
    private fun crearContrasenaAleatoria(longitud: Int): String {
        val upperChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowerChars = "abcdefghijklmnopqrstuvwxyz"
        val numberChars = "0123456789"
        val symbolChars = "!@#$%^&*()_+-=[]{}|;:,.<>?"

        var charPool = ""
        if (switchUppercase.isChecked) charPool += upperChars
        if (switchLowercase.isChecked) charPool += lowerChars
        if (switchNumbers.isChecked) charPool += numberChars
        if (switchSymbols.isChecked) charPool += symbolChars

        // Si el usuario desactiva todos los switches, usar minúsculas por defecto
        if (charPool.isEmpty()) {
            charPool = lowerChars
        }

        val random = SecureRandom()
        val resultado = StringBuilder(longitud)

        for (i in 0 until longitud) {
            val indice = random.nextInt(charPool.length)
            resultado.append(charPool[indice])
        }

        return resultado.toString()
    }
}