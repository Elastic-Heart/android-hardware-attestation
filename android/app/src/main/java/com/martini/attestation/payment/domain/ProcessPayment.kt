package com.martini.attestation.payment.domain

import android.util.Base64
import com.martini.attestation.crypto.HardwareKeyManager
import com.martini.attestation.payment.data.model.PaymentRequest
import com.martini.attestation.payment.data.repositories.PaymentRepository

class ProcessPayment(
    private val paymentRepository: PaymentRepository,
    private val hardwareKeyManager: HardwareKeyManager
) {
    suspend operator fun invoke(userId: String, deviceId: String, recipient: String, amount: Double): Result<Unit> {
        return try {
            val payload = "$recipient:$amount".toByteArray(Charsets.UTF_8)
            val signature = hardwareKeyManager.signPayload(payload)
            
            val request = PaymentRequest(
                userId = userId,
                deviceId = deviceId,
                recipient = recipient,
                amount = amount,
                signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP)
            )
            
            val response = paymentRepository.processPayment(request)
            if (response.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(response.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
