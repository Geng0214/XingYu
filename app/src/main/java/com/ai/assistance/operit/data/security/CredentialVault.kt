package com.ai.assistance.operit.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.ai.assistance.operit.data.model.ChatFailure
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface Credentials { fun read(ref: String, endpoint: String): String }

class CredentialVault(context: Context) : Credentials {
    private val directory = File(context.noBackupFilesDir, "vault").apply { mkdirs() }
    private val alias = "companion.master.v1"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized private fun master(): SecretKey {
        if (keyStore.containsAlias(alias)) return keyStore.getKey(alias, null) as SecretKey
        // Losing the wrapping key is an error, never a reason to silently reset the database.
        check(directory.listFiles()!!.isEmpty()) { "Missing wrapping key" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    private fun file(ref: String): File {
        require(ref == "database" || ref == "privacy-lock" || Regex("[a-f0-9-]{36}").matches(ref))
        return File(directory, "$ref.enc")
    }
    private fun seal(ref: String, bytes: ByteArray, binding: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, master())
        cipher.updateAAD(binding.toByteArray(Charsets.UTF_8))
        val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(bytes)
        val target = AtomicFile(file(ref))
        val stream = target.startWrite()
        try { stream.write(encrypted); target.finishWrite(stream) }
        catch (e: Exception) { target.failWrite(stream); throw e }
    }
    private fun unseal(ref: String, binding: String): ByteArray {
        val encoded = AtomicFile(file(ref)).readFully()
        check(encoded.size >= 29 && encoded[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, master(), GCMParameterSpec(128, encoded.copyOfRange(1,13)))
        cipher.updateAAD(binding.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(encoded.copyOfRange(13, encoded.size))
    }
    @Synchronized fun databaseKey(databaseExists: Boolean): ByteArray {
        if (file("database").exists() || File(file("database").path + ".bak").exists()) return unseal("database", "database:v1")
        check(!databaseExists) { "Database key is missing" }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        seal("database", key, "database:v1")
        return key
    }
    @Synchronized fun save(key: String, endpoint: String): String {
        require(key.isNotBlank() && key.length <= 8192 && key.none { it.isISOControl() })
        val ref = UUID.randomUUID().toString()
        val bytes = key.toByteArray(Charsets.UTF_8)
        try { seal(ref, bytes, "credential:v1:$endpoint") } finally { bytes.fill(0) }
        return ref
    }
    @Synchronized fun privacyLockEnabled():Boolean {
        val target=file("privacy-lock")
        // Absence is the first enrollment of pre-lock installs, not a damaged-policy recovery path.
        if(!target.exists()&&!File(target.path+".bak").exists()) {setPrivacyLock(false);return false}
        val bytes=unseal("privacy-lock","privacy-lock:v1")
        try {check(bytes.size==1&&bytes[0] in byteArrayOf(0,1));return bytes[0]==1.toByte()}
        finally {bytes.fill(0)}
    }
    @Synchronized fun setPrivacyLock(enabled:Boolean)=seal("privacy-lock",byteArrayOf(if(enabled)1 else 0),"privacy-lock:v1")
    @Synchronized fun removeCredential(ref:String) {
        require(Regex("[a-f0-9-]{36}").matches(ref))
        AtomicFile(file(ref)).delete()
    }
    @Synchronized fun pruneCredentials(retainedRefs:Set<String>) {
        val refPattern=Regex("[a-f0-9-]{36}")
        require(retainedRefs.all(refPattern::matches))
        val storedRefs=directory.listFiles()!!.asSequence().filter {it.isFile}.mapNotNull {candidate->
            val ref=when {
                candidate.name.endsWith(".enc.bak")->candidate.name.removeSuffix(".enc.bak")
                candidate.name.endsWith(".enc.new")->candidate.name.removeSuffix(".enc.new")
                candidate.name.endsWith(".enc")->candidate.name.removeSuffix(".enc")
                else->null
            }
            ref?.takeIf(refPattern::matches)
        }.toSet()
        (storedRefs-retainedRefs).forEach(::removeCredential)
    }
    @Synchronized override fun read(ref: String, endpoint: String): String = try {
        val bytes = unseal(ref, "credential:v1:$endpoint")
        try { bytes.toString(Charsets.UTF_8) } finally { bytes.fill(0) }
    } catch (_: Exception) { throw ChatFailure("CREDENTIAL") }
}
