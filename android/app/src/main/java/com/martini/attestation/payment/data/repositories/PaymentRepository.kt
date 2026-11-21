package com.martini.attestation.payment.data.repositories

import com.martini.attestation.payment.data.model.PaymentRequest
import com.martini.attestation.payment.data.model.PaymentResponse
import com.martini.attestation.payment.data.services.PaymentApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PaymentRepository(
    private val paymentApiService: PaymentApiService
) {
    suspend fun processPayment(request: PaymentRequest): Result<PaymentResponse> = withContext(Dispatchers.IO) {
        try {
            val response = paymentApiService.processPayment(request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
