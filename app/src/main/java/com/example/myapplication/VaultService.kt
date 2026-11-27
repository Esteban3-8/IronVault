package com.example.myapplication

import android.content.Context
import android.os.SystemClock
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** All account-vault plaintext crosses this boundary. Never use Firebase credentials as key material. */
object VaultService {
    lateinit var context: Context
        private set
    private var owner: String? = null
    private var dek: ByteArray? = null
    private var envelope: VaultEnvelope? = null
    private var generation = 0L
    private var permit: Pair<String, Long>? = null
    private var ownerPermit: ByteArray? = null
    private val writes = Mutex()
    val uid: String get() = FirebaseAuth.getInstance().currentUser?.uid ?: error("Inicia sesión nuevamente")
    val unlocked: Boolean get() = owner != null && owner == FirebaseAuth.getInstance().currentUser?.uid && dek != null
    internal val binding: String get() {
        val e = envelope ?: error("Bóveda bloqueada")
        return "$uid:${e.id}:${e.keyRevision}"
    }
    fun initialize(app: Context) {
        context = app.applicationContext
        FirebaseAuth.getInstance().addAuthStateListener { if (owner != it.currentUser?.uid) lock() }
    }
    @Synchronized fun lock(navigate: Boolean = true) {
        generation++
        ownerPermit?.fill(0); ownerPermit = null
        dek?.fill(0); dek = null; owner = null; envelope = null; permit = null
        VaultRepository.credentialList.clear()
        NuevaCredencialActivity.categoryCounts.clear()
        NuevaCredencialActivity.categoryLastUsedTime.clear()
        if (::context.isInitialized) VaultLifecycle.clearSensitiveScreens(navigate)
    }
    fun logout() {
        FirebaseAuth.getInstance().currentUser?.uid?.let { VaultBiometrics.revoke(context, it) }
        lock(false)
        FirebaseAuth.getInstance().signOut()
    }
    fun requireUnlocked() { check(unlocked) { "Desbloquea la bóveda" } }
    suspend fun checkBackupPassword(password: String) {
        requireUnlocked()
        val e = envelope ?: error("Bóveda bloqueada")
        val user = uid
        val chars = password.toCharArray()
        try {
            val same = withContext(Dispatchers.Default) {
                try { e.unwrap(user, chars).fill(0); true }
                catch (_: javax.crypto.AEADBadTagException) { false }
            }
            require(!same) { "La contraseña del respaldo debe ser diferente de la maestra" }
        } finally { chars.fill('\u0000') }
    }
    private fun reference(user: String) = FirebaseFirestore.getInstance().collection("users").document(user)
        .collection("vault").document("active")

    internal suspend fun load(): VaultEnvelope? {
        val user = uid
        FirebaseAuth.getInstance().currentUser!!.getIdToken(true).await()
        val document = reference(user).get(Source.SERVER).await()
        check(uid == user)
        if (!document.exists()) return null
        val encoded = document.getString("envelope") ?: error("Configuración de bóveda dañada")
        require(encoded.length <= 950000)
        return VaultEnvelope(JSONObject(encoded)).also { it.validate() }
    }
    suspend fun isLegacy(): Boolean {
        val user = uid
        val profile = FirebaseFirestore.getInstance().collection("users").document(user).get(Source.SERVER).await()
        check(uid == user)
        // The server also checks orphaned legacy records before accepting creation.
        return profile.getBoolean("hasMasterPassword") == true
    }
    suspend fun create(password: CharArray, migrate: Boolean = false) = writes.withLock {
        require(password.size in 8..4096)
        val user = uid; val epoch = generation
        check(load() == null) { "Ya existe una bóveda" }
        check(migrate || !isLegacy()) { "Esta cuenta requiere una migración supervisada; no se sobrescribirá" }
        val records = if (migrate) VaultMigration.read(user) else emptyList()
        val key = VaultCipher.randomBytes(32)
        try {
            val e = VaultEnvelope(JSONObject().put("version", 2).put("id", UUID.randomUUID().toString())
                .put("revision", 1).put("dataRevision", 1).put("keyRevision", 1))
            withContext(Dispatchers.Default) {
                e.wrap(user, password, key)
                VaultSigning.initialize(e, user, key, password)
                val plain = CredentialCodec.encode(records)
                try {
                    e.value.put("payload", VaultCipher.seal(key, plain, e.aad(user, "records")).json())
                    val roundTrip = VaultCipher.open(key, e.value.getJSONObject("payload").box(), e.aad(user, "records"))
                    try { check(CredentialCodec.decode(roundTrip) == records) } finally { roundTrip.fill(0) }
                } finally { plain.fill(0) }
            }
            check(epoch == generation && uid == user)
            VaultSigning.commit(user, 0, if (migrate) "migrate" else "create", e, null)
            check(epoch == generation && uid == user)
            accept(user, e, key, records)
            VaultBiometrics.revoke(context, user)
        } finally { key.fill(0) }
    }
    suspend fun unlock(password: CharArray) {
        SessionManager.requireAccessAllowed(context)
        val user = uid; val epoch = generation
        val e = load() ?: error("Configura tu bóveda")
        val key = try { withContext(Dispatchers.Default) { e.unwrap(user, password) } }
        catch (e: javax.crypto.AEADBadTagException) {
            if (uid == user && epoch == generation) SessionManager.registrarIntentoFallido(context)
            throw e
        }
        try { unlockKey(user, epoch, e, key) } finally { key.fill(0) }
    }
    internal fun acceptBiometric(e: VaultEnvelope, key: ByteArray, user: String, epoch: Long) {
        SessionManager.requireAccessAllowed(context)
        unlockKey(user, epoch, e, key)
    }
    internal fun epoch() = generation
    private fun unlockKey(user: String, epoch: Long, e: VaultEnvelope, key: ByteArray) {
        val plain = VaultCipher.open(key, e.value.getJSONObject("payload").box(), e.aad(user, "records"))
        try {
            val list = CredentialCodec.decode(plain)
            check(uid == user && generation == epoch) { "Sesión modificada" }
            SessionManager.requireAccessAllowed(context)
            accept(user, e, key, list)
            SessionManager.resetearIntentos(context)
        } finally { plain.fill(0) }
    }
    private fun accept(user: String, e: VaultEnvelope, key: ByteArray, list: List<Credential>) {
        dek?.fill(0); dek = key.copyOf(); envelope = e; owner = user
        VaultRepository.credentialList.clear(); VaultRepository.credentialList.addAll(list)
    }
    internal suspend fun verify(password: CharArray): ByteArray {
        requireUnlocked()
        val user = uid; val epoch = generation
        val current = envelope ?: error("Bóveda bloqueada")
        val latest = load() ?: error("Bóveda no disponible")
        check(latest.id == current.id && latest.keyRevision == current.keyRevision) { "La configuración cambió. Bloquea y vuelve a entrar" }
        val key = withContext(Dispatchers.Default) { latest.unwrap(user, password) }
        if (epoch != generation || !unlocked || !MessageDigest.isEqual(key, dek)) {
            key.fill(0); error("Sesión modificada")
        }
        return key
    }
    suspend fun authorize(password: CharArray, action: String) {
        verify(password).fill(0)
        val e = envelope ?: error("Bóveda bloqueada")
        val user = uid; val epoch = generation
        val signingKey = withContext(Dispatchers.Default) { VaultSigning.owner(e, user, password) }
        if (epoch != generation || !unlocked || uid != user) { signingKey.fill(0); error("Sesión modificada") }
        ownerPermit?.fill(0); ownerPermit = signingKey
        permit = action to SystemClock.elapsedRealtime()
    }
    fun consume(action: String) {
        requireUnlocked()
        val p = permit; permit = null
        if (p?.first != action || SystemClock.elapsedRealtime() - p.second >= 120000) {
            ownerPermit?.fill(0); ownerPermit = null
            error("Confirma nuevamente tu contraseña maestra")
        }
        if (action != "import" && action != "delete") { ownerPermit?.fill(0); ownerPermit = null }
    }
    suspend fun replace(list: List<Credential>, action: String? = null) = writes.withLock {
        replaceLocked(list, action)
    }
    private suspend fun replaceLocked(list: List<Credential>, action: String? = null) {
        if (action != null) consume(action)
        requireUnlocked()
        val user = uid; val epoch = generation
        val old = envelope ?: error("Bóveda bloqueada")
        val key = dek!!.copyOf()
        try {
            val next = VaultEnvelope(JSONObject(old.value.toString()).put("revision", old.revision + 1).put("dataRevision", old.value.getLong("dataRevision") + 1))
            val plain = CredentialCodec.encode(list)
            try {
                next.value.put("payload", VaultCipher.seal(key, plain, next.aad(user, "records")).json())
            } finally { plain.fill(0) }
            val signer = if (action != null) ownerPermit?.copyOf() ?: error("Confirma la maestra")
                else VaultSigning.writer(old, user, key)
            try { commit(user, epoch, old, next, action ?: "write", signer) }
            finally { signer.fill(0); if (action != null) { ownerPermit?.fill(0); ownerPermit = null } }
            accept(user, next, key, list)
        } finally { key.fill(0) }
    }
    suspend fun save(credential: Credential) = writes.withLock {
        requireUnlocked()
        replaceLocked(VaultRepository.credentialList.filterNot { it.id == credential.id } + credential)
    }
    suspend fun delete(id: String) = writes.withLock {
        requireUnlocked(); replaceLocked(VaultRepository.credentialList.filterNot { it.id == id })
    }
    suspend fun changePassword(current: CharArray, nextPassword: CharArray) = writes.withLock {
        require(nextPassword.size in 8..4096)
        val key = verify(current)
        try {
            val user = uid; val epoch = generation; val old = envelope!!
            val next = VaultEnvelope(JSONObject(old.value.toString()).put("revision", old.revision + 1)
                .put("keyRevision", old.keyRevision + 1))
            val signer = withContext(Dispatchers.Default) { VaultSigning.owner(old, user, current) }
            try {
                withContext(Dispatchers.Default) {
                    next.wrap(user, nextPassword, key)
                    VaultSigning.wrapOwner(next, user, nextPassword, signer)
                }
                commit(user, epoch, old, next, "rekey", signer)
            } finally { signer.fill(0) }
            envelope = next; permit = null; ownerPermit?.fill(0); ownerPermit = null
            VaultBiometrics.revoke(context, user)
        } finally { key.fill(0) }
    }
    private suspend fun commit(user: String, epoch: Long, old: VaultEnvelope, next: VaultEnvelope, operation: String, signer: ByteArray) {
        check(uid == user && generation == epoch && unlocked)
        VaultSigning.commit(user, old.revision, operation, next, signer)
        check(uid == user && generation == epoch && unlocked)
    }
    internal fun localSeal(plain: String, purpose: String): String {
        requireUnlocked()
        return VaultCipher.seal(dek!!, plain.toByteArray(), "$uid:${envelope!!.id}:local:$purpose".toByteArray()).json().toString()
    }
    internal fun localOpen(encoded: String, purpose: String): String {
        requireUnlocked()
        val bytes = VaultCipher.open(dek!!, JSONObject(encoded).box(), "$uid:${envelope!!.id}:local:$purpose".toByteArray())
        return try { String(bytes, Charsets.UTF_8) } finally { bytes.fill(0) }
    }
}
