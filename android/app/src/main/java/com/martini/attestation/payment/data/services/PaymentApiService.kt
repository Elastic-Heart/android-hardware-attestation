package com.martini.attestation.payment.data.services

import com.martini.attestation.payment.data.model.PaymentRequest
import com.martini.attestation.payment.data.model.PaymentResponse
import com.martini.attestation.setup.data.model.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class PaymentApiService(
    private val client: HttpClient
) {
    suspend fun processPayment(request: PaymentRequest): PaymentResponse {
        val response = client.post("payment") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val error = runCatching { response.body<ErrorResponse>() }.getOrNull()
            throw IllegalStateException(error?.error ?: "Payment failed: ${response.status}")
        }
    }
}
