package com.example.myapplication

import android.content.Context

object VaultRepository {
    // Lista vacía para que no muestre datos de prueba al arrancar la aplicación
    val credentialList = mutableListOf<Credential>()

    // Función para añadir una nueva credencial y colocarla al principio de la lista
    fun addCredential(credential: Credential) {
        credentialList.add(0, credential)
    }

    /** Si es invitado, guarda la lista cifrada en el dispositivo. Si no, no hace nada. */
    fun persistirSiInvitado(context: Context) {
        if (GuestUtils.esInvitado(context)) {
            GuestStorage.guardar(context, credentialList)
        }
    }

    /** Carga en memoria las credenciales guardadas del invitado. */
    fun cargarDeInvitado(context: Context) {
        credentialList.clear()
        credentialList.addAll(GuestStorage.cargar(context))
    }
}