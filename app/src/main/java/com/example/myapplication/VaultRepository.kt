package com.example.myapplication

object VaultRepository {
    // Lista inicial con las credenciales de prueba
    val credentialList = mutableListOf(
        Credential("Gmail", "laura.gomez@gmail.com", "P@ssw0rd123!", "Segura"),
        Credential("Instagram", "lau.gomez", "InstaSecure99!", "Segura"),
        Credential("Banco Nacional", "lgomez92", "123456", "Débil"),
        Credential("Netflix", "laura.gomez@gmail.com", "Netlfix2026", "Repetida")
    )

    // Función para añadir una nueva credencial y colocarla al principio de la lista
    fun addCredential(credential: Credential) {
        credentialList.add(0, credential)
    }
}