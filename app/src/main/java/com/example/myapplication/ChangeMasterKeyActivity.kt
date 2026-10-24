package com.example.myapplication

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// ─────────────────────────────────────────────────────────────────
//  ChangeMasterKeyActivity
//  Permite al usuario cambiar su clave maestra. Requiere confirmar
//  la clave actual antes de aceptar la nueva.
// ─────────────────────────────────────────────────────────────────
class ChangeMasterKeyActivity : AppCompatActivity() {

    // ── SharedPreferences ────────────────────────────────────────
    // El mismo "bloc de notas" que usamos en SecurityActivity.
    // Usamos el mismo nombre "security_prefs" para acceder a los
    // mismos datos (la clave maestra guardada ahí).
    private val prefs by lazy {
        getSharedPreferences("security_prefs", MODE_PRIVATE)
    }

    // ── Variables de estado para los ojos ────────────────────────
    // Guardamos si cada campo está visible o no.
    // "var" = variable que puede cambiar (a diferencia de "val" que es constante).
    private var isCurrentVisible = false
    private var isNewVisible     = false
    private var isConfirmVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_change_master_key)

        // ── WindowInsets (arreglo de bordes) ─────────────────────
        // El layout tiene android:padding="24dp" en el XML.
        // El problema: setPadding() REEMPLAZA todo el padding, borrando
        // los 24dp laterales del XML. La solución es SUMAR el padding
        // del sistema al padding base que ya tiene el layout (24dp).
        //
        // TypedValue.applyDimension convierte dp → px porque setPadding
        // trabaja en píxeles. La densidad de la pantalla (displayMetrics)
        // varía según el dispositivo, por eso no podemos hardcodear píxeles.
        val dp24 = (24 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Lados: 24dp fijos del XML. Arriba/abajo: barra de estado + navegación
            v.setPadding(dp24, systemBars.top, dp24, systemBars.bottom)
            insets
        }

        configurarBotones()
        configurarOjos()
        configurarBarraFortaleza()
    }

    // ─────────────────────────────────────────────────────────────
    //  configurarBotones()
    //  Conecta btnBack y btnSaveNewKey con sus acciones.
    // ─────────────────────────────────────────────────────────────
    private fun configurarBotones() {

        // ── Botón ATRÁS ──────────────────────────────────────────
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // ── Botón GUARDAR NUEVA CLAVE ────────────────────────────
        val etCurrent = findViewById<EditText>(R.id.etCurrentPassword)
        val etNew     = findViewById<EditText>(R.id.etNewPassword)
        val etConfirm = findViewById<EditText>(R.id.etConfirmPassword)

        findViewById<AppCompatButton>(R.id.btnSaveNewKey).setOnClickListener {

            // .text.toString().trim() → convierte el contenido del EditText
            // a String y elimina espacios al inicio y al final.
            val current = etCurrent.text.toString().trim()
            val nueva   = etNew.text.toString().trim()
            val confirm = etConfirm.text.toString().trim()

            // ── Validación 1: campos vacíos ──────────────────────
            // Si cualquier campo está vacío, mostramos un Toast y
            // salimos de la función con "return@setOnClickListener".
            // "return@" en una lambda indica a cuál bloque salir.
            if (current.isEmpty() || nueva.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ── Validación 2: contraseña actual correcta ─────────
            // Leemos la clave guardada en SharedPreferences.
            // Si el usuario nunca la guardó (primera vez), será "".
            val claveGuardada = prefs.getString("master_key", "") ?: ""

            if (claveGuardada.isNotEmpty() && current != claveGuardada) {
                Toast.makeText(this, "La contraseña actual es incorrecta", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ── Validación 3: nueva y confirmación coinciden ──────
            if (nueva != confirm) {
                Toast.makeText(this, "Las contraseñas nuevas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ── Validación 4: mínimo de seguridad ─────────────────
            if (nueva.length < 8) {
                Toast.makeText(this, "La clave debe tener al menos 8 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ── Todo OK: guardar la nueva clave ───────────────────
            // Escribimos la nueva clave en SharedPreferences y cerramos.
            prefs.edit().putString("master_key", nueva).apply()
            Toast.makeText(this, "¡Clave actualizada correctamente!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  configurarOjos()
    //  Cada ícono de ojo alterna el campo entre "texto visible"
    //  y "texto oculto con puntos".
    //
    //  CONCEPTO: TransformationMethod
    //  Los EditText de tipo password ocultan el texto usando
    //  PasswordTransformationMethod (muestra ••••).
    //  Para mostrarlo usamos HideReturnsTransformationMethod.
    //  Después de cambiar el método, movemos el cursor al final
    //  con setSelection() para que no salte al inicio.
    // ─────────────────────────────────────────────────────────────
    private fun configurarOjos() {

        val etCurrent  = findViewById<EditText>(R.id.etCurrentPassword)
        val eyeCurrent = findViewById<ImageView>(R.id.ivToggleCurrentPassword)

        val etNew  = findViewById<EditText>(R.id.etNewPassword)
        val eyeNew = findViewById<ImageView>(R.id.ivToggleNewPassword)

        val etConfirm  = findViewById<EditText>(R.id.etConfirmPassword)
        val eyeConfirm = findViewById<ImageView>(R.id.ivToggleConfirmPassword)

        // Usamos una función de extensión local para no repetir el mismo
        // código tres veces. Recibe el EditText y la variable de estado.
        // Como "isVisible" es un Boolean primitivo (no una referencia),
        // la manejamos directamente con un lambda que devuelve el nuevo estado.
        eyeCurrent.setOnClickListener {
            isCurrentVisible = !isCurrentVisible        // invertimos el estado
            toggleVisibility(etCurrent, isCurrentVisible)
        }

        eyeNew.setOnClickListener {
            isNewVisible = !isNewVisible
            toggleVisibility(etNew, isNewVisible)
        }

        eyeConfirm.setOnClickListener {
            isConfirmVisible = !isConfirmVisible
            toggleVisibility(etConfirm, isConfirmVisible)
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  toggleVisibility()
    //  Función privada reutilizable que muestra u oculta el texto
    //  de un EditText según el parámetro "mostrar".
    //
    //  "private fun" = función solo usable dentro de esta clase.
    //  Parámetros: el campo (EditText) y si debe verse (Boolean).
    // ─────────────────────────────────────────────────────────────
    private fun toggleVisibility(campo: EditText, mostrar: Boolean) {
        if (mostrar) {
            // Muestra el texto en claro
            campo.transformationMethod = HideReturnsTransformationMethod.getInstance()
        } else {
            // Oculta el texto con ••••
            campo.transformationMethod = PasswordTransformationMethod.getInstance()
        }
        // Mueve el cursor al final para que no salte al principio
        campo.setSelection(campo.text.length)
    }

    // ─────────────────────────────────────────────────────────────
    //  configurarBarraFortaleza()
    //  Escucha en tiempo real lo que el usuario escribe en "Nueva
    //  clave maestra" y pinta las 3 barras según la fortaleza.
    //
    //  CONCEPTO: TextWatcher
    //  Es una interfaz que "observa" los cambios en un EditText.
    //  Tiene 3 métodos obligatorios:
    //    · beforeTextChanged → antes de que cambie (raramente se usa)
    //    · onTextChanged     → mientras está cambiando
    //    · afterTextChanged  → después de cambiar (el más útil)
    //  Usamos addTextChangedListener para adjuntarlo al EditText.
    // ─────────────────────────────────────────────────────────────
    private fun configurarBarraFortaleza() {

        val etNew = findViewById<EditText>(R.id.etNewPassword)

        // Obtenemos las 3 barras del LinearLayout por su posición.
        // layoutStrengthBars es un LinearLayout con 3 hijos (View).
        // getChildAt(índice) devuelve el hijo en esa posición (0, 1, 2).
        val layout = findViewById<android.widget.LinearLayout>(R.id.layoutStrengthBars)
        val barra1 = layout.getChildAt(0)   // primera barra (izquierda)
        val barra2 = layout.getChildAt(1)   // segunda barra (centro)
        val barra3 = layout.getChildAt(2)   // tercera barra (derecha)

        etNew.addTextChangedListener(object : TextWatcher {

            // No necesitamos estos dos métodos pero son obligatorios
            // en la interfaz TextWatcher, así que los dejamos vacíos.
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            // Este se llama cada vez que el texto termina de cambiar.
            // "s: Editable?" contiene el texto actual del campo.
            override fun afterTextChanged(s: Editable?) {
                val texto = s.toString()
                val nivel = evaluarFortaleza(texto)  // 0=vacío, 1=débil, 2=media, 3=fuerte
                pintarBarras(barra1, barra2, barra3, nivel)
            }
        })
    }

    // ─────────────────────────────────────────────────────────────
    //  evaluarFortaleza()
    //  Recibe la contraseña y devuelve un número del 0 al 3:
    //    0 = vacía
    //    1 = débil  (menos de 6 caracteres)
    //    2 = media  (6+ chars, solo letras o solo números)
    //    3 = fuerte (8+ chars con letras Y números Y símbolo)
    //
    //  Usamos Regex (expresiones regulares) para detectar si
    //  contiene letras, números o símbolos.
    //  Regex("patron").containsMatchIn(texto) → true/false
    // ─────────────────────────────────────────────────────────────
    private fun evaluarFortaleza(password: String): Int {
        if (password.isEmpty()) return 0

        val tieneLetras   = Regex("[a-zA-Z]").containsMatchIn(password)
        val tieneNumeros  = Regex("[0-9]").containsMatchIn(password)
        val tieneSimbolos = Regex("[^a-zA-Z0-9]").containsMatchIn(password)

        return when {
            password.length >= 8 && tieneLetras && tieneNumeros && tieneSimbolos -> 3 // fuerte
            password.length >= 6 && (tieneLetras || tieneNumeros)                -> 2 // media
            else                                                                  -> 1 // débil
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  pintarBarras()
    //  Cambia el color de fondo de cada barra según el nivel.
    //
    //  ContextCompat.getColor(context, R.color.nombre) → convierte
    //  un recurso de color (@color/...) en un Int que Android entiende.
    //  setBackgroundColor(colorInt) → aplica ese color a la Vista.
    // ─────────────────────────────────────────────────────────────
    private fun pintarBarras(b1: View, b2: View, b3: View, nivel: Int) {
        val gris  = ContextCompat.getColor(this, R.color.border_option_card)
        val rojo  = ContextCompat.getColor(this, R.color.history_red)
        val amarillo = 0xFFFFC107.toInt()   // naranja/amarillo estándar de Material
        val verde = ContextCompat.getColor(this, R.color.green_accent)

        when (nivel) {
            0 -> { b1.setBackgroundColor(gris);   b2.setBackgroundColor(gris);     b3.setBackgroundColor(gris)  }
            1 -> { b1.setBackgroundColor(rojo);   b2.setBackgroundColor(gris);     b3.setBackgroundColor(gris)  }
            2 -> { b1.setBackgroundColor(amarillo); b2.setBackgroundColor(amarillo); b3.setBackgroundColor(gris) }
            3 -> { b1.setBackgroundColor(verde);  b2.setBackgroundColor(verde);    b3.setBackgroundColor(verde) }
        }
    }
}
