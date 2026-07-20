package com.example.routeplanning.mvp.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class DatabaseKeyManager(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @SuppressLint("ApplySharedPref")
    fun getOrCreatePassphrase(): ByteArray {
        val encrypted = preferences.getString(ENCRYPTED_PASSPHRASE, null)
        val iv = preferences.getString(PASSPHRASE_IV, null)
        if (encrypted != null && iv != null) {
            return decrypt(
                ciphertext = Base64.decode(encrypted, Base64.NO_WRAP),
                iv = Base64.decode(iv, Base64.NO_WRAP)
            )
        }

        val passphrase = ByteArray(PASSPHRASE_SIZE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        }
        val ciphertext = cipher.doFinal(passphrase)
        // The wrapped key must be durable before the database can be created with it.
        preferences.edit()
            .putString(ENCRYPTED_PASSPHRASE, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(PASSPHRASE_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
        return passphrase
    }

    private fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "route_planning_database_wrapping_key"
        const val PREFERENCES_NAME = "route_planning_secure_key"
        const val ENCRYPTED_PASSPHRASE = "encrypted_passphrase"
        const val PASSPHRASE_IV = "passphrase_iv"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val PASSPHRASE_SIZE_BYTES = 32
    }
}
