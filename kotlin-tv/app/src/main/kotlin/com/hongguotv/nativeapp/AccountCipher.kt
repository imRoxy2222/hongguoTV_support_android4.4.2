// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/** The stored format is IV + authenticated ciphertext; legacy Android 5 used a 16-byte IV. */
internal object AccountCipher {
    /**
     * javax.crypto.spec.GCMParameterSpec only exists from API 21. KitKat uses the
     * BouncyCastle provider, which accepts a plain IvParameterSpec for AES/GCM, while
     * the JVM provider used by unit tests requires GCMParameterSpec. Probe for the class
     * instead of reading Build.VERSION.SDK_INT: the class is genuinely absent on an API 19
     * device and present on the test JVM, which is exactly the distinction that matters.
     * The reflective lookup also keeps the API 21+ type out of this object constant pool,
     * so Dalvik never resolves a missing class while verifying AccountCipher.
     */
    private val gcmSpecClass: Class<*>? = try {
        Class.forName("javax.crypto.spec.GCMParameterSpec")
    } catch (error: ClassNotFoundException) {
        null
    }

    private fun gcmCipher(): Cipher =
        if (gcmSpecClass != null) Cipher.getInstance("AES/GCM/NoPadding")
        else Cipher.getInstance("AES/GCM/NoPadding", "BC")

    private fun gcmSpec(iv: ByteArray): AlgorithmParameterSpec =
        gcmSpecClass
            ?.getConstructor(java.lang.Integer.TYPE, ByteArray::class.java)
            ?.newInstance(128, iv) as? AlgorithmParameterSpec
            ?: IvParameterSpec(iv)

    fun encrypt(plain: ByteArray, key: SecretKey, legacyKey: Boolean): ByteArray {
        val cipher = gcmCipher()
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
            val cipher = gcmCipher()
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
