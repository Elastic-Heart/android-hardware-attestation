package com.martini.attestation.setup.data.repositories

import com.martini.attestation.setup.data.datasources.AttestationRemoteDataSource
import com.martini.attestation.setup.data.model.VerifyResponse

interface AttestationRepository {
    suspend fun getNonce(userId: String): Result<String>
    suspend fun verifyDevice(
        userId: String,
        deviceId: String,
        nonce: String,
        certificates: List<String>
    ): Result<VerifyResponse>
}

class AttestationRepositoryImpl(
    private val remoteDataSource: AttestationRemoteDataSource
) : AttestationRepository {

    override suspend fun getNonce(userId: String): Result<String> {
        return remoteDataSource.fetchChallengeNonce(userId)
    }

    override suspend fun verifyDevice(
        userId: String,
        deviceId: String,
        nonce: String,
        certificates: List<String>
    ): Result<VerifyResponse> {
        return remoteDataSource.submitAttestation(
            userId = userId,
            deviceId = deviceId,
            nonce = nonce,
            certificateChainPemOrBase64 = certificates
        )
    }
}