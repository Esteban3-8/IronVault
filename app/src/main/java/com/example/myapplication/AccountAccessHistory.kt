package com.example.myapplication

import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Best-effort client-reported device history. Firebase rules validate account and auth_time.
 * Device labels are not attested; this is not an exhaustive server-side security audit.
 * No passwords, email addresses, tokens, IP addresses or location are stored here.
 */
internal object AccountAccessHistory {
    private lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var recorded: String? = null
    private var initialized = false
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private fun collection(uid: String) = db.collection("users").document(uid).collection("accessEvents")

    fun initialize(app: Context) {
        if (initialized) return
        initialized = true
        context = app.applicationContext
        auth.addAuthStateListener { firebase ->
            if (firebase.currentUser == null) recorded = null
            else scope.launch {
                try { recordCurrent() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Retried on next app start or history refresh; never block login. */ }
            }
        }
    }

    fun deviceId(uid: String): String {
        val prefs = context.getSharedPreferences("access_history_installation", Context.MODE_PRIVATE)
        val installation = prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString("id", it).commit())
        }
        return AccountAccessEvent.deviceId(uid, installation)
    }

    suspend fun recordCurrent() = mutex.withLock {
        withTimeout(15000) {
            val user = auth.currentUser ?: return@withTimeout
            val uid = user.uid
            val token = user.getIdToken(false).await()
            check(auth.currentUser?.uid == uid)
            val device = deviceId(uid)
            val id = AccountAccessEvent.documentId(device, token.authTimestamp)
            if (recorded == "$uid:$id") return@withTimeout
            val ref = collection(uid).document(id)
            val payload = mapOf(
                "version" to 1L, "deviceId" to device,
                "deviceName" to AccountAccessEvent.label("${Build.MANUFACTURER} ${Build.MODEL}", 80),
                "androidVersion" to AccountAccessEvent.label(Build.VERSION.RELEASE, 32),
                "authTime" to token.authTimestamp, "recordedAt" to FieldValue.serverTimestamp())
            db.runTransaction { transaction ->
                val exists = transaction.get(ref).exists()
                check(auth.currentUser?.uid == uid)
                if (!exists) transaction.set(ref, payload)
                null
            }.await()
            if (auth.currentUser?.uid == uid) recorded = "$uid:$id"
        }
    }

    data class Page(val events: List<AccountAccessEvent>, val cursor: DocumentSnapshot?, val hasMore: Boolean)
    suspend fun load(uid: String, cursor: DocumentSnapshot?): Page = withTimeout(15000) {
        check(VaultService.unlocked && auth.currentUser?.uid == uid)
        var query = collection(uid).orderBy("authTime", Query.Direction.DESCENDING).limit(30)
        if (cursor != null) query = query.startAfter(cursor)
        val snapshot = query.get(Source.SERVER).await()
        check(VaultService.unlocked && auth.currentUser?.uid == uid)
        val events = snapshot.documents.map { document ->
            AccountAccessEvent.parse(document.data ?: error("Registro no válido")).also {
                check(document.id == AccountAccessEvent.documentId(it.deviceId, it.authTime))
            }
        }
        Page(events, snapshot.documents.lastOrNull(), snapshot.size() == 30)
    }
}
