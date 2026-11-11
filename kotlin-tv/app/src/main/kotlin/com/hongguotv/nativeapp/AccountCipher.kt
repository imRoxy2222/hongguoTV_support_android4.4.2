// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.os.Build
import androidx.annotation.RequiresApi
import java.security.GeneralSecurityException
import java.security.spec.AlgorithmParameterSpec
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec

/** The stored format is IV + authenticated ciphertext; legacy Android 5 used a 16-byte IV. */
internal object AccountCipher {
    /**
     * GCMParameterSpec was added in API 21. On KitKat the BouncyCastle provider accepts a
     * plain IvParameterSpec for AES/GCM, so keep the API 21+ type inside a lazily loaded
     * holder: Dalvik must never resolve that class while verifying AccountCipher itself.
     * GCM authenticated encryption is retained on every version.
     */
    private fun gcmSpec(iv: ByteArray): AlgorithmParameterSpec =
        if (Build.VERSION.SDK_INT >= 21) GcmSpecApi21.create(iv) else IvParameterSpec(iv)

    @RequiresApi(21)
    private object GcmSpecApi21 {
        fun create(iv: ByteArray): AlgorithmParameterSpec = GCMParameterSpec(128, iv)
    }
    fun encrypt(plain: ByteArray, key: SecretKey, legacyKey: Boolean): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (legacyKey) {
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec(iv))
        } else {
            // Android Keystore generates its own IV; caller-supplied IVs are prohibited.
            cipher.init(Cipher.ENCRYPT_MODE, key)
        }
        check(cipher.iv.size == 12) { "账号加密格式不兼容" }
        return cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(bytes: ByteArray, key: SecretKey, legacyKey: Boolean): ByteArray {
        require(bytes.size >= 28) { "登录态数据不完整" }
        fun open(ivSize: Int): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec(bytes.copyOfRange(0, ivSize)))
            return cipher.doFinal(bytes.copyOfRange(ivSize, bytes.size))
        }
        return try { open(12) } catch (error: GeneralSecurityException) {
            if (!legacyKey || bytes.size < 32) throw error
            // Authentication still has to succeed; never accept unauthenticated plaintext.
            open(16)
        }
    }
}
