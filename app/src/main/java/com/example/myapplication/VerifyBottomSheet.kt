package com.example.myapplication

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VerifyBottomSheet : BottomSheetDialogFragment() {
    // Process death requires a new challenge; never persist its capability.
    internal var challengeToken: String? = null
    private var busy = false
    private var retryAt = 0L
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.dialog_verify, container, false)

    override fun onStart() {
        super.onStart()
        dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        val email = arguments?.getString("arg_email") ?: return dismiss()
        if (challengeToken == null) { dismiss(); return }
        val subtitle = view.findViewById<TextView>(R.id.tvSubTitle)
        val timer = view.findViewById<TextView>(R.id.tvTimer)
        val fields = listOf(R.id.etDigit1, R.id.etDigit2, R.id.etDigit3, R.id.etDigit4, R.id.etDigit5, R.id.etDigit6)
            .map { view.findViewById<EditText>(it).apply { isSaveEnabled = false } }
        subtitle.text = "Introduce el código enviado a $email. Caduca en 10 minutos."
        retryAt = SystemClock.elapsedRealtime() + 60000
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                val seconds = ((retryAt - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999) / 1000
                timer.text = if (seconds > 0) "Reenviar código en ${seconds}s" else "Reenviar código"
                timer.isEnabled = !busy && seconds == 0L
                delay(1000)
            }
        }
        timer.setOnClickListener {
            if (busy || SystemClock.elapsedRealtime() < retryAt) return@setOnClickListener
            busy = true; timer.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    challengeToken = EmailVerificationApi.request(email)
                    fields.forEach { it.text.clear() }
                    fields.first().requestFocus()
                    subtitle.text = "Nuevo código enviado a $email. Usa el último correo recibido."
                } catch (e: CancellationException) { throw e }
                catch (e: EmailVerificationException) { subtitle.text = e.userMessage }
                catch (_: Exception) { subtitle.text = "No se pudo reenviar. Revisa la conexión o inténtalo más tarde." }
                finally { retryAt = SystemClock.elapsedRealtime() + 60000; busy = false }
            }
        }
        fields.forEachIndexed { index, field ->
            field.doAfterTextChanged {
                if (busy) return@doAfterTextChanged
                if (field.text.length == 1 && index < fields.lastIndex) fields[index + 1].requestFocus()
                val code = fields.joinToString("") { it.text.toString() }
                if (code.length != 6) return@doAfterTextChanged
                val token = challengeToken ?: return@doAfterTextChanged
                busy = true; fields.forEach { it.isEnabled = false }; timer.isEnabled = false
                subtitle.text = "Verificando código…"
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        EmailVerificationApi.verify(email, token, code)
                        (activity as? RegisterActivity)?.emailVerified(email, token)
                        dismiss()
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        subtitle.text = "No se pudo verificar. Revisa el código y la conexión. Si caducó o agotaste los intentos, solicita uno nuevo."
                        fields.forEach { it.text.clear() }; fields.first().requestFocus()
                    } finally { busy = false; fields.forEach { it.isEnabled = true } }
                }
            }
        }
        fields.first().requestFocus()
    }
    override fun onDestroyView() { challengeToken = null; super.onDestroyView() }
}
