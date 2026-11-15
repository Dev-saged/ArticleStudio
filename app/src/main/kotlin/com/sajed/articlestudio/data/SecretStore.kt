package com.sajed.articlestudio.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** مفاتيح المزودين مشفّرة بمفتاح AES-GCM داخل Android Keystore؛ لا تُسجَّل ولا تُصدَّر نصاً. */
class SecretException(message: String) : Exception(message)

@Singleton
open class SecretStore @Inject constructor(@ApplicationContext c: Context) {
    private val sp = c.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build(),
        )
        return gen.generateKey()
    }

    open fun put(name: String, value: String) {
        if (value.isEmpty()) { sp.edit().remove(name).apply(); return }
        val enc = try {
            val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
            cipher.iv + cipher.doFinal(value.toByteArray())
        } catch (e: Exception) { throw SecretException("تعذّر حفظ المفتاح بأمان على هذا الجهاز (Keystore)") }
        sp.edit().putString(name, Base64.encodeToString(enc, Base64.NO_WRAP)).apply()
    }

    open fun get(name: String): String {
        val raw = sp.getString(name, null) ?: return ""
        return runCatching {
            val b = Base64.decode(raw, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, IV_LEN))
            String(cipher.doFinal(b, IV_LEN, b.size - IV_LEN))
        }.getOrDefault("")
    }

    open fun has(name: String) = sp.contains(name)

    private companion object {
        const val ALIAS = "studio_secrets"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
