package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────────
//  ImportVaultActivity
//
//  Flujo:
//  1. El usuario ya se autenticó en SettingsActivity.
//  2. Selecciona un archivo .vault vía el selector nativo del SO.
//  3. Se valida el formato y se muestran los metadatos del archivo.
//  4. El usuario introduce la contraseña del respaldo.
//  5. Se elige la modalidad (Combinar / Omitir / Reemplazar).
//  6. Se descifra, se aplica la modalidad y se persisten los datos.
//  7. Se muestra el resumen del resultado.
// ─────────────────────────────────────────────────────────────────
class ImportVaultActivity : BaseActivity() {

    private var parsedEnvelope: JSONObject? = null
    private var previewCount: Int           = 0
    private var importPasswordVisible       = false

    // Límite de tamaño al leer el archivo en memoria
    private val MAX_READ_BYTES = VaultCryptoService.MAX_FILE_BYTES

    // ── Selector de archivo del sistema operativo ─────────────────
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> procesarArchivoSeleccionado(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
        setContentView(R.layout.activity_import_vault)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, sys.top, 0, 0)
            insets
        }

        configurarUI()
    }

    private fun configurarUI() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // ── Selector de archivo ───────────────────────────────────
        findViewById<AppCompatButton>(R.id.btnSelectFile).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                // Algunos gestores de archivos filtran por MIME; como .vault es custom
                // usamos */* y validamos el contenido nosotros mismos
            }
            filePickerLauncher.launch(intent)
        }

        // ── Toggle visibilidad contraseña de importación ──────────
        val etPwd   = findViewById<EditText>(R.id.etImportPassword)
        val ivToggle = findViewById<ImageView>(R.id.ivToggleImportPwd)

        ivToggle.setOnClickListener {
            importPasswordVisible = !importPasswordVisible
            val pos = etPwd.selectionStart
            etPwd.transformationMethod = if (importPasswordVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            ivToggle.setImageResource(if (importPasswordVisible) R.drawable.ojo else R.drawable.ojo_cerrado)
            etPwd.setSelection(pos)
        }

        // ── Selección de modalidad: radio buttons exclusivos ──────
        val rbMerge   = findViewById<RadioButton>(R.id.rbMerge)
        val rbSkip    = findViewById<RadioButton>(R.id.rbSkip)
        val rbReplace = findViewById<RadioButton>(R.id.rbReplace)

        fun selectMode(selected: RadioButton) {
            rbMerge.isChecked   = (selected === rbMerge)
            rbSkip.isChecked    = (selected === rbSkip)
            rbReplace.isChecked = (selected === rbReplace)
            actualizarBordesModos(selected)
        }

        for (card in listOf(
            R.id.cardModeMerge   to rbMerge,
            R.id.cardModeSkip    to rbSkip,
            R.id.cardModeReplace to rbReplace
        )) {
            val (cardId, rb) = card
            rb.setOnClickListener { selectMode(rb) }
            findViewById<MaterialCardView>(cardId).setOnClickListener { selectMode(rb) }
        }

        // ── Botón importar ────────────────────────────────────────
        findViewById<AppCompatButton>(R.id.btnImport).setOnClickListener {
            ejecutarImportacion()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  procesarArchivoSeleccionado()
    // ─────────────────────────────────────────────────────────────
    private fun procesarArchivoSeleccionado(uri: Uri) {
        parsedEnvelope = null
        previewCount   = 0

        val tvFileName   = findViewById<TextView>(R.id.tvFileName)
        val tvFileStatus = findViewById<TextView>(R.id.tvFileStatus)
        val cardSummary  = findViewById<MaterialCardView>(R.id.cardFileSummary)
        val tvSummary    = findViewById<TextView>(R.id.tvSummaryRecords)
        val cardPassword = findViewById<MaterialCardView>(R.id.cardPasswordInput)
        val cardMode     = findViewById<MaterialCardView>(R.id.cardImportMode)
        val btnImport    = findViewById<AppCompatButton>(R.id.btnImport)

        // Nombre del archivo
        val name = obtenerNombreArchivo(uri)
        tvFileName.text = name
        tvFileName.setTextColor(getColor(R.color.text_primary))

        try {
            val bytes = leerUri(uri)

            // validateAndParse lanza VaultFormatException con mensaje claro si hay problema
            val envelope = VaultBackupService.validateAndParse(bytes)
            parsedEnvelope = envelope

            // Metadatos del encabezado (ninguno es sensible)
            val createdAt = envelope.optString("createdAt", "desconocida")
            val version   = envelope.optInt("version", -1)

            tvFileStatus.text = "✓ Archivo válido"
            tvFileStatus.setTextColor(getColor(R.color.green_accent))
            tvFileStatus.visibility = View.VISIBLE

            tvSummary.text = "Versión: $version\nFecha de exportación: $createdAt\n" +
                    "Algoritmo: ${envelope.getJSONObject("encryption").optString("algorithm")}"
            cardSummary.visibility  = View.VISIBLE
            cardPassword.visibility = View.VISIBLE
            cardMode.visibility     = View.VISIBLE
            btnImport.visibility    = View.VISIBLE

        } catch (e: VaultFormatException) {
            tvFileStatus.text = "✗ ${e.message}"
            tvFileStatus.setTextColor(0xFFEF4444.toInt())
            tvFileStatus.visibility = View.VISIBLE
            cardSummary.visibility  = View.GONE
            cardPassword.visibility = View.GONE
            cardMode.visibility     = View.GONE
            btnImport.visibility    = View.GONE
        } catch (e: Exception) {
            tvFileStatus.text = "✗ No se pudo leer el archivo."
            tvFileStatus.setTextColor(0xFFEF4444.toInt())
            tvFileStatus.visibility = View.VISIBLE
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  ejecutarImportacion()
    // ─────────────────────────────────────────────────────────────
    private fun ejecutarImportacion() {
        val envelope = parsedEnvelope ?: return
        val password = findViewById<EditText>(R.id.etImportPassword).text.toString()
        val tvError  = findViewById<TextView>(R.id.tvImportPasswordError)

        if (password.isEmpty()) {
            tvError.text = "Ingresa la contraseña del respaldo."
            tvError.visibility = View.VISIBLE
            return
        }
        tvError.visibility = View.GONE

        val mode = when {
            findViewById<RadioButton>(R.id.rbReplace).isChecked -> VaultBackupService.ImportMode.REPLACE
            findViewById<RadioButton>(R.id.rbSkip).isChecked    -> VaultBackupService.ImportMode.SKIP_DUPLICATES
            else                                                 -> VaultBackupService.ImportMode.MERGE
        }

        // Reemplazar requiere confirmación adicional
        if (mode == VaultBackupService.ImportMode.REPLACE) {
            confirmarReemplazo { realizarImportacion(envelope, password, mode) }
            return
        }

        realizarImportacion(envelope, password, mode)
    }

    private fun realizarImportacion(
        envelope: JSONObject,
        password: String,
        mode: VaultBackupService.ImportMode
    ) {
        val progress = findViewById<ProgressBar>(R.id.progressImport)
        val btn = findViewById<AppCompatButton>(R.id.btnImport)
        fun start() {
            btn.isEnabled = false; progress.visibility = View.VISIBLE
            vaultJob {
                try {
                    val incoming = kotlinx.coroutines.withContext(Dispatchers.Default) { VaultBackupService.decrypt(envelope, password) }
                    val (result, summary) = VaultBackupService.applyImport(VaultRepository.credentialList.toList(), incoming, mode)
                    if (GuestUtils.esInvitado(this@ImportVaultActivity)) {
                        GuestStorage.guardar(this@ImportVaultActivity, result)
                        VaultRepository.credentialList.clear(); VaultRepository.credentialList.addAll(result)
                    } else VaultService.replace(result, "import")
                    findViewById<EditText>(R.id.etImportPassword).text.clear()
                    mostrarResultado(summary)
                } finally { btn.isEnabled = true; progress.visibility = View.GONE }
            }
        }
        if (GuestUtils.esInvitado(this)) start() else confirmMaster("import") { start() }
    }

    // ─────────────────────────────────────────────────────────────
    //  sincronizarFirestore()
    //  Reescribe la colección del usuario con la lista resultante.
    //  Las contraseñas se almacenan tal como llegaron (ya cifradas
    //  con EncryptionHelper en origen); si son de otro dispositivo
    //  del mismo usuario, seguirán siendo válidas porque la clave
    //  proviene de Firebase Auth (no de un secreto local).
    // ─────────────────────────────────────────────────────────────
    private fun sincronizarFirestore(lista: List<Credential>) {
        error("Usar VaultService.replace con autorización maestra")
    }

    // ─────────────────────────────────────────────────────────────
    //  mostrarResultado()
    // ─────────────────────────────────────────────────────────────
    private fun mostrarResultado(r: VaultBackupService.ImportResult) {
        val sb = StringBuilder()
        sb.appendLine("Registros encontrados: ${r.found}")
        sb.appendLine("Importados:            ${r.imported}")
        sb.appendLine("Omitidos:              ${r.skipped}")
        if (r.duplicates > 0)   sb.appendLine("Duplicados exactos:    ${r.duplicates}")
        if (r.conflicts > 0)    sb.appendLine("Conflictos resueltos:  ${r.conflicts}")
        if (r.replaced > 0)     sb.appendLine("Registros reemplazados:${r.replaced}")
        if (r.errors > 0)       sb.appendLine("Errores:               ${r.errors}")

        val cardResult = findViewById<MaterialCardView>(R.id.cardResult)
        val tvResult   = findViewById<TextView>(R.id.tvResult)
        tvResult.text  = sb.toString().trimEnd()
        cardResult.visibility = View.VISIBLE

        mostrarBannerPersonalizado(
            titulo = "Importación completada",
            mensaje = "${r.imported} credencial${if (r.imported != 1) "es" else ""} importada${if (r.imported != 1) "s" else ""}.",
            iconoResId = R.drawable.logo
        )
    }

    // ─────────────────────────────────────────────────────────────
    //  confirmarReemplazo()
    // ─────────────────────────────────────────────────────────────
    private fun confirmarReemplazo(onConfirmed: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_action, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvActionTitle)?.text = "¿Reemplazar bóveda?"
        dialogView.findViewById<TextView>(R.id.tvActionMessage)?.text =
            "Esta acción eliminará todas las credenciales actuales y las reemplazará con las del respaldo. No podrás deshacer este cambio."

        dialogView.findViewById<AppCompatButton>(R.id.btnActionConfirm)?.apply {
            text = "Sí, reemplazar"
            setOnClickListener {
                dialog.dismiss()
                onConfirmed()
            }
        }
        dialogView.findViewById<TextView>(R.id.btnActionCancel)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────
    private fun leerUri(uri: Uri): ByteArray {
        val stream = contentResolver.openInputStream(uri)
            ?: throw VaultFormatException("No se pudo abrir el archivo.")
        return stream.use { input ->
            val bytes = input.readBytes()
            if (bytes.size > MAX_READ_BYTES)
                throw VaultFormatException("El archivo supera el tamaño máximo permitido (10 MB).")
            bytes
        }
    }

    private fun obtenerNombreArchivo(uri: Uri): String {
        var name = "archivo.vault"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx) ?: name
            }
        }
        return name
    }

    private fun actualizarBordesModos(selected: RadioButton) {
        val modos = mapOf(
            R.id.cardModeMerge   to R.id.rbMerge,
            R.id.cardModeSkip    to R.id.rbSkip,
            R.id.cardModeReplace to R.id.rbReplace
        )
        modos.forEach { (cardId, rbId) ->
            val rb   = findViewById<RadioButton>(rbId)
            val card = findViewById<MaterialCardView>(cardId)
            val isSelected = (rb === selected)
            card.strokeColor = if (isSelected)
                getColor(R.color.orange_primary)
            else
                getColor(R.color.border_option_card)
        }
    }
}
