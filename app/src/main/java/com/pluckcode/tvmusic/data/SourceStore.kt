package com.pluckcode.tvmusic.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists sources (including passwords and private keys) in app-private prefs,
 * encrypted with an AES key that never leaves the Android Keystore.
 */
object SourceStore {
    private const val PREFS = "sources"
    private const val KEY = "sources_v1"
    private lateinit var prefs: SharedPreferences
    private val _sources = MutableStateFlow<List<Source>>(emptyList())
    val sources: StateFlow<List<Source>> = _sources

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _sources.value = load()
    }

    fun get(id: String): Source? =
        if (id == Source.LIBRARY.id) Source.LIBRARY else _sources.value.find { it.id == id }

    @Synchronized
    fun upsert(source: Source) {
        val list = _sources.value.toMutableList()
        val i = list.indexOfFirst { it.id == source.id }
        if (i >= 0) list[i] = source else list += source
        _sources.value = list
        save(list)
    }

    @Synchronized
    fun remove(id: String) {
        val list = _sources.value.filterNot { it.id == id }
        _sources.value = list
        save(list)
    }

    private fun load(): List<Source> {
        val blob = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(Vault.decrypt(blob))
            (0 until arr.length()).map { Source.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e("SourceStore", "Could not read saved sources", e)
            emptyList()
        }
    }

    private fun save(list: List<Source>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY, Vault.encrypt(arr.toString())).apply()
    }
}

private object Vault {
    private const val ALIAS = "tvmusic_sources"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray())
        return b64(c.iv) + ":" + b64(ct)
    }

    fun decrypt(blob: String): String {
        val (iv, ct) = blob.split(":", limit = 2)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, unb64(iv)))
        return String(c.doFinal(unb64(ct)))
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
