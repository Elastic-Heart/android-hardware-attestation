package com.martini.attestation.payment.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PaymentResponse(
    val status: String,
    val message: String
)
