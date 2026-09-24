package com.martini.attestation.setup.data.datasources

import com.martini.attestation.setup.data.model.ChallengeRequest
import com.martini.attestation.setup.data.model.VerifyRequest
import com.martini.attestation.setup.data.model.VerifyResponse
import com.martini.attestation.setup.data.services.AttestationApiService

class AttestationRemoteDataSource(
    private val apiService: AttestationApiService
) {
    suspend fun fetchChallengeNonce(userId: String): Result<String> = runCatching {
        val request = ChallengeRequest(userId = userId)
        apiService.getChallenge(request).nonce
    }

    suspend fun submitAttestation(
        userId: String,
        deviceId: String,
        nonce: String,
        certificateChainPemOrBase64: List<String>
    ): Result<VerifyResponse> = runCatching {
        val request = VerifyRequest(
            userId = userId,
            deviceId = deviceId,
            nonce = nonce,
            certificateChain = certificateChainPemOrBase64
        )
        apiService.verify(request)
    }
}