package com.martini.attestation.payment.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PaymentRequest(
    val userId: String,
    val deviceId: String,
    val recipient: String,
    val amount: Double,
    val signatureBase64: String
)
