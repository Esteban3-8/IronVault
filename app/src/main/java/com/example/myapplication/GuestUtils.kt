package com.example.myapplication

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import android.util.Base64
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Datos del modo invitado, guardados solo en el dispositivo.
 */
object GuestUtils {
    private const val PREFS = "settings_prefs"
    private const val KEY_GUEST = "is_guest"
    private const val KEY_KEY_CREATED = "master_key_created"
    private const val KEY_HASH = "guest_master_hash"
    private const val KEY_SALT = "guest_master_salt"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Es invitado solo si tiene la marca Y no hay una cuenta de Firebase iniciada. */
    fun esInvitado(c: Context): Boolean =
        prefs(c).getBoolean(KEY_GUEST, false) && Firebase.auth.currentUser == null

    fun tieneClave(c: Context) = prefs(c).getBoolean(KEY_KEY_CREATED, false)

    fun marcarInvitado(c: Context, valor: Boolean) {
        prefs(c).edit().putBoolean(KEY_GUEST, valor).apply()
    }

    /** Guarda la clave maestra del invitado como hash con sal (nunca en texto plano). */
    fun guardarClave(c: Context, clave: String) {
        val sal = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(c).edit()
            .putString(KEY_SALT, Base64.encodeToString(sal, Base64.NO_WRAP))
            .putString(KEY_HASH, hash(clave, sal))
            .putBoolean(KEY_KEY_CREATED, true)
            .apply()
    }

    /** Cuadro para el invitado: crear una cuenta o iniciar sesión en una existente. */
    fun mostrarDialogoCuenta(activity: Activity) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_guest_account)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<AppCompatButton>(R.id.btnGuestCreate).setOnClickListener {
            dialog.dismiss()
            activity.startActivity(Intent(activity, RegisterActivity::class.java))
        }
        dialog.findViewById<AppCompatButton>(R.id.btnGuestLogin).setOnClickListener {
            dialog.dismiss()
            activity.startActivity(Intent(activity, LoginActivity::class.java))
        }
        dialog.findViewById<TextView>(R.id.tvGuestCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    /** Borra la clave maestra local del invitado (al pasar a una cuenta). */
    fun borrarClave(c: Context) {
        prefs(c).edit()
            .remove(KEY_HASH)
            .remove(KEY_SALT)
            .putBoolean(KEY_KEY_CREATED, false)
            .apply()
    }

    /** Comprueba la clave escrita al desbloquear. */
    fun verificarClave(c: Context, clave: String): Boolean {
        val salB64 = prefs(c).getString(KEY_SALT, null) ?: return false
        val guardado = prefs(c).getString(KEY_HASH, null) ?: return false
        val sal = Base64.decode(salB64, Base64.NO_WRAP)
        return hash(clave, sal) == guardado
    }

    private fun hash(clave: String, sal: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(sal)
        return Base64.encodeToString(md.digest(clave.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
}