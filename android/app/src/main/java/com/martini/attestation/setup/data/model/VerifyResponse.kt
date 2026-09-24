package com.martini.attestation.setup.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifyResponse(
    @SerialName("securityLevel") val securityLevel: String,
    @SerialName("publicKeyBase64") val publicKeyBase64: String
)