package com.example.myapplication

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Servicio de respaldo: serialización, exportación e importación de bóvedas.
 *
 * Responsabilidades:
 *  - Serializar y deserializar la lista de credenciales a/desde JSON.
 *  - Construir y parsear el sobre del archivo .vault.
 *  - Delegar el cifrado/descifrado a VaultCryptoService.
 *  - Detectar duplicados según tres criterios.
 *  - Aplicar las tres modalidades de importación.
 *
 * Esta clase no tiene dependencias de Android (Context, Activity) para facilitar
 * su prueba unitaria independiente.
 */
object VaultBackupService {

    // ─────────────────────────────────────────────────────────────
    //  Modalidades de importación
    // ─────────────────────────────────────────────────────────────
    enum class ImportMode {
        /** Combina listas; los registros del archivo que ya existen se omiten. */
        MERGE,

        /** Solo importa los que no estén en la bóveda actual (alias de MERGE sin conflictos). */
        SKIP_DUPLICATES,

        /** Reemplaza toda la bóveda con el contenido del respaldo. */
        REPLACE
    }

    // ─────────────────────────────────────────────────────────────
    //  Resultado de la importación
    // ─────────────────────────────────────────────────────────────
    data class ImportResult(
        val found: Int,
        val imported: Int,
        val skipped: Int,
        val duplicates: Int,
        val conflicts: Int,
        val replaced: Int,
        val errors: Int
    )

    // ─────────────────────────────────────────────────────────────
    //  exportToBytes()
    //
    //  Serializa las credenciales, construye el sobre JSON,
    //  cifra con VaultCryptoService y devuelve los bytes del archivo.
    //  No escribe nada en disco: el llamador decide dónde guardar.
    // ─────────────────────────────────────────────────────────────
    fun exportToBytes(
        credentials: List<Credential>,
        backupPassword: String
    ): ByteArray {
        // 1. Serializar credenciales — sin strengthStatus que es un campo calculado
        val array = JSONArray()
        for (c in credentials) {
            array.put(
                JSONObject()
                    .put("id",          c.id)
                    .put("serviceName", c.serviceName)
                    .put("userOrEmail", c.userOrEmail)
                    .put("password",    c.password)
                    .put("url",         c.url)
                    .put("notes",       c.notes)
                    .put("category",    c.category)
                    .put("updatedAt",   c.updatedAt)
                // No se exporta: strengthStatus (campo calculado), ni ninguna clave interna
            )
        }
        val plaintext = array.toString()

        // 2. Metadatos del encabezado (ninguno es sensible)
        val createdAt = isoNow()

        // 3. Construir AAD antes de cifrar (encabezado que se autenticará)
        val aad = VaultCryptoService.buildAad(VaultCryptoService.FORMAT, VaultCryptoService.VERSION, createdAt)

        // 4. Cifrar
        val enc = VaultCryptoService.encrypt(plaintext, backupPassword, aad)

        // 5. Construir el sobre JSON del archivo
        val envelope = JSONObject()
            .put("format",    VaultCryptoService.FORMAT)
            .put("version",   VaultCryptoService.VERSION)
            .put("createdAt", createdAt)
            .put("encryption", JSONObject()
                .put("algorithm",  VaultCryptoService.ALGORITHM)
                .put("kdf",        VaultCryptoService.KDF)
                .put("iterations", VaultCryptoService.KDF_ITERS)
                .put("salt",       enc.salt)
                .put("nonce",      enc.nonce)
            )
            .put("ciphertext", enc.ciphertext)
            .put("authTag",    enc.authTag)

        return envelope.toString(2).toByteArray(Charsets.UTF_8)
    }

    // ─────────────────────────────────────────────────────────────
    //  generateFileName()
    // ─────────────────────────────────────────────────────────────
    fun generateFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "vault_backup_$date.vault"
    }

    // ─────────────────────────────────────────────────────────────
    //  validateAndParse()
    //
    //  Valida el formato y los parámetros criptográficos del archivo.
    //  Devuelve el JSONObject del sobre si es válido.
    //  Lanza VaultFormatException con un mensaje claro en caso contrario.
    // ─────────────────────────────────────────────────────────────
    fun validateAndParse(bytes: ByteArray): JSONObject {
        if (bytes.isEmpty())
            throw VaultFormatException("El archivo está vacío.")

        if (bytes.size > VaultCryptoService.MAX_FILE_BYTES)
            throw VaultFormatException("El archivo supera el tamaño máximo permitido (10 MB).")

        val json = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw VaultFormatException("El archivo no tiene un formato válido.")
        }

        if (json.optString("format") != VaultCryptoService.FORMAT)
            throw VaultFormatException("El archivo no es un respaldo de IronVault.")

        val version = json.optInt("version", -1)
        if (version != VaultCryptoService.VERSION)
            throw VaultFormatException("Versión de respaldo incompatible ($version). Solo se admite la versión ${VaultCryptoService.VERSION}.")

        if (!json.has("encryption") || !json.has("ciphertext") || !json.has("authTag"))
            throw VaultFormatException("El archivo está incompleto o fue manipulado.")

        // Validar parámetros criptográficos (previene ataques de agotamiento de recursos)
        VaultCryptoService.validarParametros(json.getJSONObject("encryption"))

        return json
    }

    // ─────────────────────────────────────────────────────────────
    //  decrypt()
    //
    //  Descifra el sobre ya validado con la contraseña del respaldo.
    //  Devuelve la lista de credenciales del respaldo.
    // ─────────────────────────────────────────────────────────────
    fun decrypt(envelope: JSONObject, backupPassword: String): List<Credential> {
        val enc       = envelope.getJSONObject("encryption")
        val createdAt = envelope.getString("createdAt")

        val aad = VaultCryptoService.buildAad(
            envelope.getString("format"),
            envelope.getInt("version"),
            createdAt
        )

        val plaintext = VaultCryptoService.decrypt(
            ciphertextB64 = envelope.getString("ciphertext"),
            authTagB64    = envelope.getString("authTag"),
            saltB64       = enc.getString("salt"),
            nonceB64      = enc.getString("nonce"),
            password      = backupPassword,
            aad           = aad
        )

        return parseCredentials(plaintext)
    }

    // ─────────────────────────────────────────────────────────────
    //  applyImport()
    //
    //  Aplica la modalidad de importación elegida y devuelve
    //  la lista resultante + el resumen de la operación.
    // ─────────────────────────────────────────────────────────────
    fun applyImport(
        current: List<Credential>,
        incoming: List<Credential>,
        mode: ImportMode
    ): Pair<List<Credential>, ImportResult> {

        if (incoming.size > VaultCryptoService.MAX_CREDENTIALS)
            throw VaultFormatException("El respaldo contiene demasiados registros (${incoming.size}).")

        return when (mode) {
            ImportMode.REPLACE -> {
                val result = ImportResult(
                    found      = incoming.size,
                    imported   = incoming.size,
                    skipped    = 0,
                    duplicates = 0,
                    conflicts  = 0,
                    replaced   = current.size,
                    errors     = 0
                )
                Pair(incoming.toList(), result)
            }

            ImportMode.MERGE, ImportMode.SKIP_DUPLICATES -> {
                val existingIds  = current.map { it.id }.toSet()
                val existingKeys = current.map { contentKey(it) }.toSet()

                var imported   = 0
                var skipped    = 0
                var duplicates = 0
                var conflicts  = 0

                val merged = current.toMutableList()

                for (inc in incoming) {
                    when {
                        // Mismo id → duplicado exacto, omitir
                        inc.id in existingIds -> {
                            duplicates++
                            skipped++
                        }
                        // Misma combinación (servicio + usuario) → conflicto
                        contentKey(inc) in existingKeys -> {
                            conflicts++
                            if (mode == ImportMode.MERGE) {
                                // En MERGE, el registro del archivo tiene prioridad si es más nuevo
                                val existing = merged.indexOfFirst { contentKey(it) == contentKey(inc) }
                                if (existing >= 0 && inc.updatedAt > merged[existing].updatedAt) {
                                    merged[existing] = inc
                                    imported++
                                } else {
                                    skipped++
                                }
                            } else {
                                skipped++
                            }
                        }
                        // Registro nuevo — importar
                        else -> {
                            merged.add(inc)
                            imported++
                        }
                    }
                }

                val result = ImportResult(
                    found      = incoming.size,
                    imported   = imported,
                    skipped    = skipped,
                    duplicates = duplicates,
                    conflicts  = conflicts,
                    replaced   = 0,
                    errors     = 0
                )
                Pair(merged, result)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers privados
    // ─────────────────────────────────────────────────────────────

    /**
     * Clave de contenido para detectar conflictos entre registros con distinto id
     * pero mismo servicio + usuario. No se usa el título solo porque dos servicios
     * diferentes pueden tener el mismo nombre.
     */
    private fun contentKey(c: Credential): String =
        "${c.serviceName.lowercase().trim()}|${c.userOrEmail.lowercase().trim()}"

    private fun parseCredentials(json: String): List<Credential> {
        val array = try {
            JSONArray(json)
        } catch (e: Exception) {
            throw VaultFormatException("Los datos del respaldo están corruptos.")
        }

        val list = mutableListOf<Credential>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                Credential(
                    id          = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    serviceName = sanitize(o.optString("serviceName"), 200),
                    userOrEmail = sanitize(o.optString("userOrEmail"), 200),
                    password    = sanitize(o.optString("password"),    1000),
                    url         = sanitize(o.optString("url"),         500),
                    notes       = sanitize(o.optString("notes"),       2000),
                    category    = sanitize(o.optString("category"),    100),
                    updatedAt   = o.optLong("updatedAt", 0L),
                    // strengthStatus se recalculará en la bóveda; no se importa
                    strengthStatus = "Segura"
                )
            )
        }
        return list
    }

    /** Trunca el campo y elimina caracteres de control para prevenir inyecciones. */
    private fun sanitize(value: String, maxLen: Int): String =
        value.replace(Regex("[\\u0000-\\u001F\\u007F]"), "").take(maxLen)

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(Date())
}

/** Excepción para errores de formato o validación del archivo de respaldo. */
class VaultFormatException(message: String) : Exception(message)
