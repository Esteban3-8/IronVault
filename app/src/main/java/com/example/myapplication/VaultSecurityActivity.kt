package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView

// ─────────────────────────────────────────────────────────────────
//  VaultSecurityActivity
// ─────────────────────────────────────────────────────────────────
class VaultSecurityActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("security_prefs", MODE_PRIVATE)
    }

    // Guardamos referencia al CountDownTimer para poder cancelarlo
    // si el usuario toca "Usar contraseña maestra" antes de que termine.
    // "var" porque puede cambiar (lo asignamos al crear el timer).
    // "?" significa que puede ser null (al inicio no hay timer todavía).
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_vault_security)

        val dp20 = (20 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(dp20, systemBars.top, dp20, systemBars.bottom)
            insets
        }

        configurarBotones()
        cargarEstadoBiometrico()
    }

    private fun configurarBotones() {

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // ── Contraseña principal ──────────────────────────────────
        findViewById<MaterialCardView>(R.id.cardMainPassword).setOnClickListener {
            startActivity(Intent(this, PrincipalPasswordActivity::class.java))
        }

        // ── Switch Biométrico ─────────────────────────────────────
        // CONCEPTO: setOnCheckedChangeListener tiene un parámetro
        // importante: "isChecked" nos dice el NUEVO estado del switch.
        // true = acaba de activarse / false = acaba de desactivarse.
        //
        // Problema clásico: cuando cargamos el estado guardado con
        // "switch.isChecked = true", el listener se dispara aunque
        // el usuario no haya tocado nada. Para evitarlo, solo abrimos
        // el diálogo si el switch cambió por un toque del usuario,
        // no por código. Lo controlamos con la variable "isUserInteraction".
        val switchBiometric = findViewById<SwitchCompat>(R.id.switchBiometric)

        switchBiometric.setOnCheckedChangeListener { buttonView, isChecked ->
            // buttonView.isPressed es true solo cuando el usuario toca el switch
            // físicamente. Si el cambio viene del código, isPressed es false.
            if (!buttonView.isPressed) return@setOnCheckedChangeListener

            if (isChecked) {
                // Usuario ACTIVÓ el switch → mostrar diálogo biométrico
                mostrarDialogoBiometrico(switchBiometric)
            } else {
                // Usuario DESACTIVÓ el switch → solo guardar y notificar
                prefs.edit().putBoolean("biometric_enabled", false).apply()
                Toast.makeText(this, "Autenticación biométrica desactivada", Toast.LENGTH_SHORT).show()
            }
        }

        // ── Exportar datos ────────────────────────────────────────
        findViewById<MaterialCardView>(R.id.cardExport).setOnClickListener {
            mostrarDialogoExportar()
        }

        // ── Borrado de datos ──────────────────────────────────────
        findViewById<MaterialCardView>(R.id.cardDeleteData).setOnClickListener {
            mostrarDialogoBorrar()
        }
    }

    private fun cargarEstadoBiometrico() {
        val biometricActivado = prefs.getBoolean("biometric_enabled", false)
        // isChecked = valor sin disparar el diálogo (isPressed será false)
        findViewById<SwitchCompat>(R.id.switchBiometric).isChecked = biometricActivado
    }

    // ─────────────────────────────────────────────────────────────
    //  mostrarDialogoBiometrico()
    //  Abre el diálogo con cuenta regresiva de 5 segundos.
    //
    //  CONCEPTO: CountDownTimer
    //  Es una clase de Android para hacer cuentas regresivas.
    //  Constructor: CountDownTimer(totalMilisegundos, intervaloMilisegundos)
    //    · onTick()   → se llama cada "intervalo" ms. Aquí actualizamos el texto.
    //    · onFinish() → se llama cuando el tiempo llega a 0.
    //  .start() → inicia el temporizador.
    //  .cancel() → lo detiene (importante para no seguir corriendo en memoria).
    // ─────────────────────────────────────────────────────────────
    private fun mostrarDialogoBiometrico(switchBiometric: SwitchCompat) {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_biometric, null)
        dialog.setContentView(view)

        // Referencia al botón "Usar contraseña maestra"
        val btnUseMasterPassword = view.findViewById<MaterialCardView>(R.id.btnUseMasterPassword)

        // Buscamos el TextView del subtítulo para mostrar la cuenta regresiva.
        // En dialog_biometric.xml el subtítulo es el 3er TextView (índice 2
        // dentro del LinearLayout). Lo identificamos con su texto actual.
        // Nota: si en el futuro le agregas un android:id, úsalo directamente.
        val tvSubtitle = view.findViewById<TextView>(R.id.tvBiometricCountdown)

        // ── Iniciar cuenta regresiva de 5 segundos ────────────────
        // 5000 ms total, tick cada 1000 ms (1 segundo)
        countDownTimer = object : CountDownTimer(5000, 1000) {

            // Se llama cada segundo. "millisUntilFinished" = ms restantes.
            override fun onTick(millisUntilFinished: Long) {
                val segundos = (millisUntilFinished / 1000).toInt()
                tvSubtitle.text = "Este diálogo se cerrará en $segundos segundo${if (segundos != 1) "s" else ""}"
            }

            // Se llama cuando llegan a 0 — el usuario no tocó nada
            override fun onFinish() {
                tvSubtitle.text = "Configurando..."
                dialog.dismiss()
                // Activar biometría por defecto (sin elegir clave maestra)
                activarBiometria("Autenticación biométrica activada")
            }
        }.start()

        // ── Botón "Usar contraseña maestra" ───────────────────────
        // Si el usuario lo toca antes de que acabe el timer:
        //   1. Cancelamos el timer (para que no siga corriendo)
        //   2. Cerramos el diálogo
        //   3. Activamos con mensaje diferente
        btnUseMasterPassword.setOnClickListener {
            countDownTimer?.cancel()  // "?" = solo cancela si no es null
            dialog.dismiss()
            activarBiometria("Configurado con contraseña maestra")
        }

        // Si el usuario cierra el diálogo arrastrando hacia abajo,
        // cancelamos el timer y desactivamos el switch (no confirmó)
        dialog.setOnDismissListener {
            // Solo cancelamos si el timer sigue activo
            // (si ya terminó naturalmente, countDownTimer?.cancel() no hace daño)
            countDownTimer?.cancel()
        }

        dialog.show()
    }

    // ─────────────────────────────────────────────────────────────
    //  activarBiometria()
    //  Guarda la preferencia y muestra el Toast de confirmación.
    //  Separado en función para no repetir el código en los
    //  dos caminos (timeout y botón "contraseña maestra").
    // ─────────────────────────────────────────────────────────────
    private fun activarBiometria(mensaje: String) {
        prefs.edit().putBoolean("biometric_enabled", true).apply()
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
    }

    // ─────────────────────────────────────────────────────────────
    //  onDestroy()
    //  Se llama cuando la Activity se destruye (el usuario la cierra
    //  o Android la elimina de memoria). Es importante cancelar el
    //  CountDownTimer aquí para evitar memory leaks — si no lo
    //  cancelamos, el timer seguiría en memoria tratando de actualizar
    //  vistas que ya no existen.
    // ─────────────────────────────────────────────────────────────
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    private fun mostrarDialogoExportar() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_export_data, null)
        dialog.setContentView(view)

        view.findViewById<AppCompatButton>(R.id.btnExportConfirm).setOnClickListener {
            Toast.makeText(this, "Exportando datos... (próximamente)", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        view.findViewById<MaterialCardView>(R.id.btnExportCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun mostrarDialogoBorrar() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_delete_data, null)
        dialog.setContentView(view)

        view.findViewById<AppCompatButton>(R.id.btnDeleteConfirm).setOnClickListener {
            getSharedPreferences("security_prefs", MODE_PRIVATE).edit().clear().apply()
            getSharedPreferences("settings_prefs", MODE_PRIVATE).edit().clear().apply()
            Toast.makeText(this, "Datos eliminados", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        view.findViewById<MaterialCardView>(R.id.btnDeleteCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }
}
