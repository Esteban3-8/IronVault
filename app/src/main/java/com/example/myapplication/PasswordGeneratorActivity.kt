package com.example.myapplication

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.math.ln
import kotlin.math.roundToInt

class PasswordGeneratorActivity : BaseActivity() {

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
    private val random = SecureRandom()
    private var restaurandoAjustes = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return
        setContentView(R.layout.activity_password_generator)

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
                val currentLength = progress + MIN_LENGTH

                // Actualiza el número de la derecha inmediatamente
                tvLengthValue.text = currentLength.toString()

                // Actualiza la contraseña al vuelo mientras deslizas
                if (fromUser && !restaurandoAjustes) {
                    generarYMostrar(currentLength)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Al mover un switch se genera una contraseña nueva con los ajustes actuales
        val alCambiarSwitch = { button: android.widget.CompoundButton, checked: Boolean ->
            if (!restaurandoAjustes) {
                if (!checked && obtenerGrupos().isEmpty()) {
                    button.isChecked = true
                    Toast.makeText(this, R.string.generator_keep_one, Toast.LENGTH_SHORT).show()
                } else {
                    generarYMostrar(longitudActual())
                }
            }
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
            val clip = ClipData.newPlainText("Contraseña IronVault", tvPassword.text.toString()).apply {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
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

        if (callingActivity != null) {
            findViewById<View>(R.id.bottomNavContainer).visibility = View.GONE
        } else {
            bindBottomNavigation(NavTab.GENERATOR)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        restaurandoAjustes = true
        super.onRestoreInstanceState(savedInstanceState)
        if (::seekBarLength.isInitialized) {
            if (obtenerGrupos().isEmpty()) switchLowercase.isChecked = true
            tvLengthValue.text = longitudActual().toString()
            generarYMostrar(longitudActual())
        }
        restaurandoAjustes = false
    }

    private fun longitudActual(): Int = seekBarLength.progress + MIN_LENGTH

    private fun obtenerGrupos(): List<String> = buildList {
        if (switchUppercase.isChecked) add("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        if (switchLowercase.isChecked) add("abcdefghijklmnopqrstuvwxyz")
        if (switchNumbers.isChecked) add("0123456789")
        if (switchSymbols.isChecked) add("!@#$%^&*()_+-=[]{}|;:,.<>?")
    }

    private fun generarYMostrar(longitud: Int) {
        val grupos = obtenerGrupos()
        val pool = grupos.joinToString("")
        var password: String
        // Rechazar las muestras incompletas conserva la misma probabilidad para cada contraseña válida.
        do {
            password = buildString(longitud) {
                repeat(longitud) { append(pool[random.nextInt(pool.length)]) }
            }
        } while (grupos.any { grupo -> password.none { it in grupo } })
        tvPassword.text = password
        actualizarFortaleza(bitsDeEntropia(longitud, grupos))
    }

    private fun bitsDeEntropia(longitud: Int, grupos: List<String>): Int {
        // Inclusión-exclusión sobre los conjuntos de caracteres omitidos.
        var cantidad = BigInteger.ZERO
        for (omitidos in 0 until (1 shl grupos.size)) {
            val disponibles = grupos.indices.sumOf { index ->
                if (omitidos and (1 shl index) == 0) grupos[index].length else 0
            }
            val combinaciones = BigInteger.valueOf(disponibles.toLong()).pow(longitud)
            cantidad = if (Integer.bitCount(omitidos) % 2 == 0) cantidad + combinaciones
            else cantidad - combinaciones
        }
        return (ln(cantidad.toDouble()) / ln(2.0)).roundToInt()
    }

    private fun actualizarFortaleza(bits: Int) {
        val nombre: String
        val color: Int
        val barras: Int

        when {
            bits < 45 -> { nombre = "Débil"; color = colorDebil; barras = 1 }
            bits < 75 -> { nombre = "Media"; color = colorMedia; barras = 2 }
            bits < 100 -> { nombre = "Alta"; color = colorAlta; barras = 3 }
            else -> { nombre = "Excelente"; color = colorAlta; barras = 3 }
        }

        tvStrengthLabel.text = "Fortaleza: $nombre ($bits bits de entropía)"
        tvStrengthLabel.setTextColor(color)

        val todas = listOf(viewBar1, viewBar2, viewBar3)
        for ((i, barra) in todas.withIndex()) {
            val encendida = i < barras
            barra.backgroundTintList = ColorStateList.valueOf(if (encendida) color else colorBarraApagada)
        }
    }

    private companion object {
        const val MIN_LENGTH = 4
    }
}
