package org.example.model

data class VerifyRequest(
    val userId: String,
    val deviceId: String,
    val nonce: String,
    val certificateChain: List<String>
)
