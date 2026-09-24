package com.martini.attestation.setup.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChallengeResponse(
    @SerialName("nonce") val nonce: String
)
