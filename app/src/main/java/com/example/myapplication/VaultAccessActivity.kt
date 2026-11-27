package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

open class VaultAccessActivity : BaseActivity() {
    private var create = false
    private var migration = false
    private val changing get() = this is ChangeMasterKeyActivity
    private var busy = false
    private var ready = false
    private lateinit var message: TextView
    private val countdownHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val countdown = object : Runnable {
        override fun run() {
            updateAccessControls()
            countdownHandler.postDelayed(this, 1000)
        }
    }
    override fun onStart() {
        super.onStart()
        countdownHandler.post(countdown)
    }
    override fun onStop() {
        countdownHandler.removeCallbacks(countdown)
        super.onStop()
    }
    private fun updateAccessControls() {
        val remaining = if (!create && !changing) SessionManager.tiempoBloqueoRestanteMs(this) else 0L
        findViewById<TextView>(R.id.accessLockout).apply {
            visibility = if (remaining > 0) View.VISIBLE else View.GONE
            text = if (remaining > 0) "Demasiados intentos incorrectos. Podrás volver a intentar en ${(remaining + 999) / 1000} segundos." else ""
        }
        listOf(R.id.accessSubmit, R.id.accessBiometric, R.id.accessFingerprint).forEach {
            findViewById<View>(it).isEnabled = !busy && ready && remaining == 0L
        }
    }
    private fun field(id: Int) = findViewById<EditText>(id)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault_access)
        findViewById<ImageButton>(R.id.accessBack).apply {
            if (changing) {
                visibility = View.VISIBLE
                setOnClickListener { onBackPressedDispatcher.onBackPressed() }
            }
        }
        message = findViewById(R.id.accessMessage)
        findViewById<Button>(R.id.accessSignOut).setOnClickListener {
            VaultService.logout(); startActivity(Intent(this, WelcomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish()
        }
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java)); finish(); return
        }
        findViewById<Button>(R.id.accessSubmit).setOnClickListener { submit() }
        findViewById<Button>(R.id.accessBiometric).setOnClickListener { biometric() }
        findViewById<ImageButton>(R.id.accessFingerprint).setOnClickListener { biometric() }

        val form = findViewById<View>(R.id.accessForm)
        val loading = findViewById<View>(R.id.accessLoading)
        if (changing) {
            message.setText(R.string.vault_loading)
        } else {
            loading.findViewById<TextView>(R.id.loadingStatus).setText(R.string.vault_loading)
            form.visibility = View.GONE
            loading.visibility = View.VISIBLE
        }

        vaultJob {
            setBusy(true)
            try {
                create = VaultService.load() == null
                if (create && VaultService.isLegacy()) {
                    message.text = "Esta cuenta usa la arquitectura anterior. Sus datos permanecen intactos. La migración requiere un procedimiento supervisado para evitar que alguien que controle la cuenta se apropie de la bóveda. No se creará otra bóveda encima."
                    migration = VaultMigration.approved(VaultService.uid)
                    if (!migration) {
                        configure(); findViewById<Button>(R.id.accessSubmit).visibility = View.GONE
                        message.text = "Cuenta heredada: migración pendiente de autorización independiente. No se ha modificado ningún dato. Recuperar la cuenta no demuestra propiedad de la bóveda anterior.\n\nCódigo público de este dispositivo para el procedimiento de autorización:\n${VaultMigration.publicKey(VaultService.uid)}"
                        message.setTextIsSelectable(true)
                        return@vaultJob
                    }
                }
                configure()
                ready = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message.text = "No se pudo comprobar la bóveda. Revisa tu conexión y vuelve a abrir esta pantalla. No se ha creado ni modificado ninguna bóveda."
            } finally {
                loading.visibility = View.GONE
                form.visibility = View.VISIBLE
                setBusy(false)
            }
        }
    }
    private fun configure() {
        val setup = create || changing
        listOf(R.id.accessNewPassword, R.id.accessNewPasswordLabel, R.id.accessConfirm, R.id.accessConfirmLabel, R.id.accessAcknowledgement)
            .forEach { findViewById<View>(it).visibility = if (setup) View.VISIBLE else View.GONE }
        field(R.id.accessPassword).visibility = if (create) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.accessPasswordLabel).apply {
            visibility = if (create) View.GONE else View.VISIBLE
            text = if (changing) "Contraseña maestra actual" else "Usar contraseña maestra"
        }
        val passwordProvider = FirebaseAuth.getInstance().currentUser!!.providerData.any { it.providerId == "password" }
        val accountPasswordVisibility = if (setup && passwordProvider) View.VISIBLE else View.GONE
        field(R.id.accessAccountPassword).visibility = accountPasswordVisibility
        findViewById<View>(R.id.accessAccountPasswordLabel).visibility = accountPasswordVisibility
        findViewById<TextView>(R.id.accessTitle).text = when { changing -> "Cambiar contraseña maestra"; create -> "Crear tu bóveda"; else -> "Desbloquear bóveda" }
        findViewById<Button>(R.id.accessSubmit).text = when { changing -> "Cambiar contraseña maestra"; create -> "Crear bóveda cifrada"; else -> "Desbloquear bóveda" }
        if (setup) {
            message.setText(R.string.vault_master_password_requirements)
            if (passwordProvider) {
                message.append("\n\n")
                message.append(getString(R.string.vault_master_account_password_note))
            }
        }
        if (migration) message.text = "Migración autorizada: establece una contraseña maestra NUEVA e independiente. Se comprobarán todos los registros antes de activar el cifrado. Los originales se conservarán para una retirada supervisada. Esta migración no revierte exposiciones anteriores."
        val bio = !setup && VaultBiometrics.enabled(this, VaultService.uid)
        listOf(R.id.accessBiometric, R.id.accessFingerprint).forEach { findViewById<View>(it).visibility = if (bio) View.VISIBLE else View.GONE }
    }
    private fun setBusy(value: Boolean) {
        busy = value
        findViewById<View>(R.id.accessFingerprint).animate().alpha(if (value) 0.45f else 1f).setDuration(200).start()
        findViewById<View>(R.id.accessProgress).visibility = if (value) View.VISIBLE else View.GONE
        updateAccessControls()
    }
    private fun submit() {
        if (busy || !ready) return
        if (!create && !changing && SessionManager.tiempoBloqueoRestanteMs(this) > 0) {
            updateAccessControls(); return
        }
        val current = field(R.id.accessPassword).text.toString().toCharArray()
        val next = field(R.id.accessNewPassword).text.toString().toCharArray()
        val confirmation = field(R.id.accessConfirm).text.toString().toCharArray()
        val account = field(R.id.accessAccountPassword).text.toString().toCharArray()
        listOf(R.id.accessPassword, R.id.accessNewPassword, R.id.accessConfirm, R.id.accessAccountPassword).forEach { field(it).text.clear() }
        setBusy(true)
        vaultJob {
            try {
                if (create || changing) {
                    require(next.size in 8..4096 && next.contentEquals(confirmation))
                    require(findViewById<CheckBox>(R.id.accessAcknowledgement).isChecked)
                    val user = FirebaseAuth.getInstance().currentUser ?: error("Inicia sesión")
                    if (user.providerData.any { it.providerId == "password" }) {
                        require(!next.contentEquals(account)) { "Las contraseñas deben ser distintas" }
                        user.reauthenticate(EmailAuthProvider.getCredential(user.email!!, String(account))).await()
                    }
                    if (changing) VaultService.changePassword(current, next) else VaultService.create(next, migration)
                } else {
                    check(SessionManager.tiempoBloqueoRestanteMs(this@VaultAccessActivity) == 0L)
                    VaultService.unlock(current)
                }
                SessionManager.resetearIntentos(this@VaultAccessActivity)
                SessionManager.marcarBovedaDesbloqueada(this@VaultAccessActivity)
                message.text = "Bóveda desbloqueada"
                startActivity(Intent(this@VaultAccessActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                message.text = "No se pudo completar. Revisa las contraseñas y la conexión. En configuración, confirma la advertencia y usa al menos 8 caracteres. Si hay demasiados intentos, espera antes de reintentar."
            } finally {
                current.fill('\u0000'); next.fill('\u0000'); confirmation.fill('\u0000'); account.fill('\u0000'); setBusy(false)
            }
        }
    }
    private fun biometric() {
        if (busy || !ready) return
        if (SessionManager.tiempoBloqueoRestanteMs(this) > 0) { updateAccessControls(); return }
        setBusy(true); message.text = "Esperando autenticación del sistema…"
        vaultJob {
            try {
                val user = VaultService.uid; val epoch = VaultService.epoch()
                val e = VaultService.load() ?: error("Bóveda no disponible")
                VaultBiometrics.unlock(this@VaultAccessActivity, e, { key ->
                    // This callback consumes the recovered key before the biometric helper wipes it.
                    VaultService.acceptBiometric(e, key, user, epoch)
                    message.text = "Bóveda desbloqueada"
                    SessionManager.marcarBovedaDesbloqueada(this@VaultAccessActivity)
                    startActivity(Intent(this@VaultAccessActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish()
                }, { message.text = it; setBusy(false) })
            } catch (_: Exception) { message.text = "No se pudo cargar la bóveda. Comprueba tu sesión y conexión."; setBusy(false) }
        }
    }
}
