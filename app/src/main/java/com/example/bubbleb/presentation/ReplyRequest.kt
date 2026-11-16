package com.example.bubbleb.presentation

/** M3 integration contract. Created only on explicit Send after preview; this does not perform I/O. */
data class ReplyRequest(val id: Long, val instruction: String, val untrustedScreenText: String)
