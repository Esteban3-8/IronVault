package com.example.myapplication
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.FirebaseAuth
class ForgotPasswordActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return; setContentView(R.layout.activity_forgot_password)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvLoginRedirect).setOnClickListener { finish() }
        findViewById<AppCompatButton>(R.id.btnContinue).setOnClickListener {
            val email = findViewById<EditText>(R.id.etEmail).text.toString().trim()
            if (email.isBlank()) return@setOnClickListener
            FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener {
                mostrarBannerPersonalizado("Recuperación de cuenta", "Si la cuenta existe, recibirás un enlace. Esto no recupera ni cambia tu contraseña maestra.", R.drawable.logo)
            }
        }
    }
}
