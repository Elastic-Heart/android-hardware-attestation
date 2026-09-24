package com.martini.attestation.setup.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ChallengeRequest(
    val userId: String
)