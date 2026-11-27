package com.example.myapplication

import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

internal fun FragmentActivity.vaultJob(work: suspend () -> Unit) = lifecycleScope.launch {
    try { work() }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { Toast.makeText(this@vaultJob, "No se pudo confirmar la operación. Comprueba tu contraseña, sesión y conexión. Vuelve a cargar la bóveda antes de reintentar.", Toast.LENGTH_LONG).show() }
}

internal fun FragmentActivity.confirmMaster(action: String, done: () -> Unit) {
    val input = EditText(this).apply {
        hint = "Contraseña maestra actual"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        isSaveEnabled = false
    }
    val dialog = AlertDialog.Builder(this).setTitle("Confirmar contraseña maestra")
        .setMessage("Esta contraseña protege tu bóveda. No es la contraseña de tu cuenta.")
        .setView(input).setNegativeButton("Cancelar", null).setPositiveButton("Confirmar", null).create()
    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val chars = input.text.toString().toCharArray(); input.text.clear()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        vaultJob {
            try {
                VaultService.authorize(chars, action)
                dialog.dismiss(); done()
            } finally { chars.fill('\u0000'); dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true }
        }
    } }
    dialog.setOnDismissListener { input.text.clear() }
    dialog.show()
}
