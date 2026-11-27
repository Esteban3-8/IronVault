package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

class AccessHistoryActivity : BaseActivity() {
    private var cursor: DocumentSnapshot? = null
    private var hasMore = false
    private var loading = false
    private var job: Job? = null
    private var generation = 0
    private lateinit var account: String
    private lateinit var rows: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureVaultAccess()) return
        setContentView(R.layout.activity_access_history)
        if (!VaultService.unlocked) { finish(); return }
        account = VaultService.uid
        rows = findViewById(R.id.historyRows)
        status = findViewById(R.id.historyStatus)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<AppCompatButton>(R.id.btnRefreshHistory).setOnClickListener { load(true) }
        findViewById<AppCompatButton>(R.id.btnMoreHistory).setOnClickListener { load(false) }
    }

    override fun onStart() {
        super.onStart()
        if (::rows.isInitialized && VaultService.unlocked) load(true)
    }

    override fun onStop() {
        generation++
        job?.cancel()
        loading = false
        hasMore = false
        if (::rows.isInitialized) rows.removeAllViews()
        cursor = null
        super.onStop()
    }

    private fun load(refresh: Boolean) {
        if (loading || !VaultService.unlocked || VaultService.uid != account) return
        loading = true
        val requestGeneration = ++generation
        if (refresh) { rows.removeAllViews(); cursor = null; hasMore = false }
        controls()
        status.text = "Cargando historial…"
        job = lifecycleScope.launch {
            try {
                var recorded = true
                if (refresh) try { AccountAccessHistory.recordCurrent() }
                    catch (e: TimeoutCancellationException) { recorded = false }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { recorded = false }
                val page = AccountAccessHistory.load(account, cursor)
                ensureActive()
                if (requestGeneration != generation || !VaultService.unlocked || VaultService.uid != account) return@launch
                page.events.forEach(::addRow)
                cursor = page.cursor
                hasMore = page.hasMore
                status.text = when {
                    !recorded -> "No se pudo registrar este acceso. Se muestra el historial disponible; pulsa Actualizar para reintentar."
                    rows.childCount == 0 -> "Todavía no hay accesos registrados."
                    else -> "${rows.childCount} accesos cargados · Hora local (${TimeZone.getDefault().id})."
                }
            } catch (_: TimeoutCancellationException) {
                status.text = "La consulta tardó demasiado. Comprueba tu conexión y pulsa Actualizar."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                status.text = if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                    "El historial todavía no está habilitado para esta cuenta o la sesión perdió autorización."
                else "No se pudo cargar el historial. Comprueba tu conexión y pulsa Actualizar."
            } finally {
                if (requestGeneration == generation) { loading = false; controls() }
            }
        }
    }

    private fun controls() {
        findViewById<View>(R.id.historyProgress).visibility = if (loading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnRefreshHistory).isEnabled = !loading
        findViewById<View>(R.id.btnMoreHistory).apply {
            visibility = if (hasMore) View.VISIBLE else View.GONE
            isEnabled = !loading
        }
    }

    private fun addRow(event: AccountAccessEvent) {
        val row = layoutInflater.inflate(R.layout.item_account_access, rows, false)
        row.findViewById<TextView>(R.id.historyDevice).text = event.deviceName
        val current = event.deviceId == AccountAccessHistory.deviceId(account)
        row.findViewById<TextView>(R.id.historyDeviceTag).apply {
            visibility = if (current) View.VISIBLE else View.GONE
            text = "Este dispositivo"
        }
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(event.authTime * 1000))
        row.findViewById<TextView>(R.id.historyDetails).text = "Android ${event.androidVersion}\nAutenticación de cuenta: $date"
        rows.addView(row)
    }
}
