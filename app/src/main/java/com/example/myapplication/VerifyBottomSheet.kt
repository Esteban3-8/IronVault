package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class VerifyBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Conectamos tu diseño XML del Bottom Sheet
        return inflater.inflate(R.layout.dialog_verify, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Referencias a los 6 EditText del diseño dialog_verify.xml
        val editTexts = arrayOf(
            view.findViewById<EditText>(R.id.etDigit1),
            view.findViewById<EditText>(R.id.etDigit2),
            view.findViewById<EditText>(R.id.etDigit3),
            view.findViewById<EditText>(R.id.etDigit4),
            view.findViewById<EditText>(R.id.etDigit5),
            view.findViewById<EditText>(R.id.etDigit6)
        )

        for (i in editTexts.indices) {
            // 1. Cambiar el borde naranja dinámicamente según el foco
            editTexts[i].setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    editTexts[i].setBackgroundResource(R.drawable.bg_security_badge_active)
                } else {
                    editTexts[i].setBackgroundResource(R.drawable.bg_security_badge)
                }
            }

            // 2. Avanzar automáticamente al escribir un número
            editTexts[i].addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: android.text.Editable?) {
                    if (!s.isNullOrEmpty() && i < editTexts.size - 1) {
                        editTexts[i + 1].requestFocus()
                        editTexts[i + 1].setSelection(editTexts[i + 1].text.length)
                    }
                }
            })

            // 3. Retroceder al presionar borrar (Backspace)
            editTexts[i].setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL && event.action == android.view.KeyEvent.ACTION_DOWN) {
                    if (editTexts[i].text.isEmpty() && i > 0) {
                        editTexts[i - 1].requestFocus()
                        editTexts[i - 1].setText("")
                        editTexts[i - 1].setSelection(editTexts[i - 1].text.length)
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }


        // 4. Detectar cuando se llena el último campo con un pequeño retraso
        editTexts.last().addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                if (!s.isNullOrEmpty()) {
                    val codigoCompleto = editTexts.joinToString("") { it.text.toString() }
                    if (codigoCompleto.length == 6) {
                        // Esperamos 400 milisegundos antes de cerrar para que se alcance a ver el número
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({

                            // ¡AQUÍ ENVIAMOS LA SEÑAL A LA REGISTER ACTIVITY!
                            parentFragmentManager.setFragmentResult("verifyKey", Bundle().apply {
                                putBoolean("emailVerified", true)
                            })

                            dismiss()
                        }, 400)
                    }
                }
            }
        })
    }
}