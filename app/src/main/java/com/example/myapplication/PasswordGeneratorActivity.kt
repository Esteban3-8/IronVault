package com.example.myapplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import java.security.SecureRandom
import kotlin.math.ln
import kotlin.math.roundToInt

class PasswordGeneratorActivity : BaseActivity() { // 👈 Hereda de BaseActivity

    private lateinit var tvPassword: TextView
    private lateinit var tvStrengthLabel: TextView
    private lateinit var viewBar1: android.view.View
    private lateinit var viewBar2: android.view.View
    private lateinit var viewBar3: android.view.View
    private lateinit var seekBarLength: SeekBar
    private lateinit var tvLengthValue: TextView
    private lateinit var switchUppercase: SwitchCompat
    private lateinit var switchLowercase: SwitchCompat
    private lateinit var switchNumbers: SwitchCompat
    private lateinit var switchSymbols: SwitchCompat

    private val colorDebil = Color.parseColor("#EF4444")
    private val colorMedia = Color.parseColor("#F59E0B")
    private val colorAlta = Color.parseColor("#8FC954")
    private val colorBarraApagada = Color.parseColor("#2C303B")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_generator)

        // 1. Botón para regresar con la flecha (si aplica)
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack?.setOnClickListener {
            finish()
        }

        // 2. Referencias de los componentes visuales
        tvPassword = findViewById(R.id.tvGeneratedPassword)
        tvStrengthLabel = findViewById(R.id.tvStrengthLabel)
        viewBar1 = findViewById(R.id.viewBar1)
        viewBar2 = findViewById(R.id.viewBar2)
        viewBar3 = findViewById(R.id.viewBar3)
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
                    generarYMostrar(currentLength)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Al mover un switch se genera una contraseña nueva con los ajustes actuales
        val alCambiarSwitch = { _: android.widget.CompoundButton, _: Boolean ->
            generarYMostrar(longitudActual())
        }
        switchUppercase.setOnCheckedChangeListener(alCambiarSwitch)
        switchLowercase.setOnCheckedChangeListener(alCambiarSwitch)
        switchNumbers.setOnCheckedChangeListener(alCambiarSwitch)
        switchSymbols.setOnCheckedChangeListener(alCambiarSwitch)

        // Generar contraseña inicial al abrir la pantalla
        val initialLength = longitudActual()
        tvLengthValue.text = initialLength.toString()
        generarYMostrar(initialLength)

        // 3. Botón "Generar de nuevo"
        btnGenerate?.setOnClickListener {
            generarYMostrar(longitudActual())
            mostrarBannerPersonalizado(
                titulo = "Generada",
                mensaje = "¡Nueva contraseña generada!",
                iconoResId = R.drawable.logo
            )
        }

        // 4. Botón "Copiar al Portapapeles"
        btnCopy?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Contraseña IronVault", tvPassword.text.toString())
            clipboard.setPrimaryClip(clip)
            mostrarBannerPersonalizado(
                titulo = "Portapapeles",
                mensaje = "¡Contraseña copiada con éxito!",
                iconoResId = R.drawable.logo
            )
        }

        // 5. Botón naranja "Continuar con esta contraseña"
        btnContinue?.setOnClickListener {
            val passwordGenerada = tvPassword.text.toString()
            if (passwordGenerada.isNotEmpty()) {
                if (callingActivity != null) {
                    // Lo abrió "Nueva contraseña": le devolvemos la contraseña
                    val resultIntent = Intent().apply {
                        putExtra("EXTRA_GENERATED_PASSWORD", passwordGenerada)
                    }
                    setResult(RESULT_OK, resultIntent)
                } else {
                    // Lo abrió el menú: abrimos "Nueva contraseña" con la contraseña ya escrita
                    startActivity(Intent(this, NuevaCredencialActivity::class.java).apply {
                        putExtra("EXTRA_GENERATED_PASSWORD", passwordGenerada)
                    })
                }
                finish()
            } else {
                mostrarBannerPersonalizado(
                    titulo = "Atención",
                    mensaje = "Genera una contraseña primero",
                    iconoResId = R.drawable.logo
                )
            }
        }

        // ── BARRA DE NAVEGACIÓN INFERIOR (Menú Píldora) ──────────

        // 1. Ir a Inicio (MainActivity)
        findViewById<LinearLayout>(R.id.btnNavHome)?.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // 2. Ir a Seguridad (SecurityActivity)
        findViewById<LinearLayout>(R.id.btnNavLock)?.setOnClickListener {
            val intent = Intent(this, SecurityActivity::class.java)
            startActivity(intent)
            finish()
        }

        // 3. Botón Llave (Ya estás aquí)

        // 4. Ir a Ajustes (SettingsActivity)
        findViewById<LinearLayout>(R.id.btnNavSettings)?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun longitudActual(): Int =
        if (seekBarLength.progress < 4) 4 else seekBarLength.progress

    /** Caracteres disponibles según los switches activos. */
    private fun obtenerCaracteres(): String {
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
        return charPool
    }

    /** Genera una contraseña nueva, la muestra y actualiza la fortaleza. */
    private fun generarYMostrar(longitud: Int) {
        val pool = obtenerCaracteres()
        tvPassword.text = crearContrasenaAleatoria(longitud, pool)
        actualizarFortaleza(longitud, pool.length)
    }

    // Función para crear la contraseña con los caracteres disponibles
    private fun crearContrasenaAleatoria(longitud: Int, charPool: String): String {
        val random = SecureRandom()
        val resultado = StringBuilder(longitud)

        for (i in 0 until longitud) {
            val indice = random.nextInt(charPool.length)
            resultado.append(charPool[indice])
        }

        return resultado.toString()
    }

    /**
     * Fortaleza real: bits de entropía = longitud * log2(caracteres posibles).
     * Débil < 45, Media < 75, Alta < 100, Excelente >= 100.
     */
    private fun actualizarFortaleza(longitud: Int, tamanoPool: Int) {
        val bits = (longitud * (ln(tamanoPool.toDouble()) / ln(2.0))).roundToInt()

        val nombre: String
        val color: Int
        val barras: Int

        when {
            bits < 45 -> { nombre = "Débil"; color = colorDebil; barras = 1 }
            bits < 75 -> { nombre = "Media"; color = colorMedia; barras = 2 }
            bits < 100 -> { nombre = "Alta"; color = colorAlta; barras = 3 }
            else -> { nombre = "Excelente"; color = colorAlta; barras = 3 }
        }

        tvPassword.setTextColor(color)
        tvStrengthLabel.text = "Fortaleza: $nombre ($bits bits de entropía)"
        tvStrengthLabel.setTextColor(color)

        val todas = listOf(viewBar1, viewBar2, viewBar3)
        for ((i, barra) in todas.withIndex()) {
            val encendida = i < barras
            barra.backgroundTintList = ColorStateList.valueOf(if (encendida) color else colorBarraApagada)
        }
    }
}