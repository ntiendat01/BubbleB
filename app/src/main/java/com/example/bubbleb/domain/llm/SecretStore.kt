package com.example.bubbleb.domain.llm

sealed interface SecretRead {
    class Available(val value: String) : SecretRead { override fun toString() = "SecretRead.Available(<redacted>)" }
    data object Missing : SecretRead
    data object Invalidated : SecretRead
    data object Unavailable : SecretRead
}

/** Implementations run cryptography and file I/O off main; only ciphertext may reach disk. */
interface SecretStore {
    suspend fun read(binding: String): SecretRead
    suspend fun write(binding: String, value: String)
    suspend fun clear()
}
