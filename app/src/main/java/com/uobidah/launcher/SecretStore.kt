package com.uobidah.launcher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * تخزين الأسرار (مفاتيح API) مشفّرة بـ AES-256-GCM بمفتاح داخل Android Keystore.
 * لا يوجد أي تخزين نصي صريح: عند فشل Keystore على جهاز ما تُرجع put() القيمة false
 * وتخبر الواجهة المستخدم، ولا يوجد تراجع صامت إلى نص مكشوف.
 */
object SecretStore {
    private const val ALIAS = "uobidah_secret_key_v1"
    private const val FILE = "uobidah_secrets"
    private const val PREFIX = "secret_"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    @Synchronized fun put(ctx: Context, name: String, value: String): Boolean = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val blob = c.iv + c.doFinal(value.toByteArray(Charsets.UTF_8))
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(PREFIX + name, Base64.encodeToString(blob, Base64.NO_WRAP)).commit()
    } catch (_: Exception) { false }

    @Synchronized fun get(ctx: Context, name: String): String? = try {
        val raw = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(PREFIX + name, null)
        if (raw == null) null else {
            val blob = Base64.decode(raw, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
            String(c.doFinal(blob, 12, blob.size - 12), Charsets.UTF_8)
        }
    } catch (_: Exception) { null }

    @Synchronized fun remove(ctx: Context, name: String) {
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(PREFIX + name).apply()
    }

    fun has(ctx: Context, name: String): Boolean =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).contains(PREFIX + name)

    /** عرض مقنّع للمفتاح في الواجهة، مثل ab••••yz. */
    fun mask(v: String): String = if (v.length <= 6) "••••" else v.take(2) + "••••" + v.takeLast(2)
}
