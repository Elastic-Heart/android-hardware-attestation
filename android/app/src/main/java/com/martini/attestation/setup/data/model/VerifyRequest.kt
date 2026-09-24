package com.martini.attestation.setup.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifyRequest(
    @SerialName("userId") val userId: String,
    @SerialName("deviceId") val deviceId: String,
    @SerialName("nonce") val nonce: String,
    @SerialName("certificateChain") val certificateChain: List<String>
)
