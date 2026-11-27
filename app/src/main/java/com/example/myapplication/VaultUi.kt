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
    val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_master_key, null)
    val input = dialogView.findViewById<EditText>(R.id.etMasterKeyInput)
    val toggleBtn = dialogView.findViewById<android.widget.ImageView>(R.id.ivToggleMasterKey)
    val errorText = dialogView.findViewById<android.widget.TextView>(R.id.tvMasterKeyError)
    val btnConfirm = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnConfirmMasterKey)
    val btnCancel = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnCancelMasterKey)

    var passwordVisible = false
    toggleBtn.setOnClickListener {
        passwordVisible = !passwordVisible
        input.inputType = if (passwordVisible)
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        else
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.setSelection(input.text.length)
        toggleBtn.setImageResource(if (passwordVisible) R.drawable.ojo else R.drawable.ojo_cerrado)
    }

    val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
        .setView(dialogView).create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

    btnCancel.setOnClickListener { dialog.dismiss() }

    btnConfirm.setOnClickListener {
        val chars = input.text.toString().toCharArray()
        input.text.clear()
        errorText.visibility = android.view.View.GONE
        btnConfirm.isEnabled = false
        vaultJob {
            try {
                VaultService.authorize(chars, action)
                dialog.dismiss(); done()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorText.visibility = android.view.View.VISIBLE
                btnConfirm.isEnabled = true
            } finally { chars.fill('\u0000') }
        }
    }
    dialog.setOnDismissListener { input.text.clear() }
    dialog.show()
}
