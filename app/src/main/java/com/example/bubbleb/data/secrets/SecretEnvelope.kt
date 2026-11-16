package com.example.bubbleb.data.secrets

import java.nio.ByteBuffer

data class EncryptedSecret(val iv: ByteArray, val ciphertext: ByteArray)

object SecretEnvelope {
    private const val VERSION = 1
    private const val IV_BYTES = 12
    const val MAX_BYTES = 8_192

    fun encode(secret: EncryptedSecret): ByteArray {
        require(secret.iv.size == IV_BYTES && secret.ciphertext.size in 16..(MAX_BYTES - 20))
        return ByteBuffer.allocate(8 + IV_BYTES + secret.ciphertext.size)
            .putInt(VERSION).putInt(secret.ciphertext.size).put(secret.iv).put(secret.ciphertext).array()
    }

    fun decode(bytes: ByteArray): EncryptedSecret {
        require(bytes.size in 36..MAX_BYTES)
        val buffer = ByteBuffer.wrap(bytes)
        require(buffer.int == VERSION)
        val length = buffer.int
        require(length >= 16 && length == bytes.size - 20)
        return EncryptedSecret(ByteArray(IV_BYTES).also(buffer::get), ByteArray(length).also(buffer::get))
    }
}

interface SecretCipher {
    fun encrypt(plaintext: ByteArray, binding: ByteArray): EncryptedSecret
    fun decrypt(secret: EncryptedSecret, binding: ByteArray): ByteArray
    fun reset()
}

class InvalidSecretKey : java.security.GeneralSecurityException()
