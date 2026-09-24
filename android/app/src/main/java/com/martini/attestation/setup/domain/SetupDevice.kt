package com.martini.attestation.setup.domain

import com.martini.attestation.common.LoadState
import com.martini.attestation.crypto.HardwareKeyManager
import com.martini.attestation.setup.data.repositories.AttestationRepository
import java.util.UUID

class SetupDevice(
    private val repository: AttestationRepository,
    private val hardwareKeyManager: HardwareKeyManager
) {

    private val userId = UUID.randomUUID().toString()
    private val deviceId = UUID.randomUUID().toString()

    suspend operator fun invoke() : LoadState<Unit, Throwable> {
        val nonceResult = repository.getNonce(userId)

        if (nonceResult.isFailure) return LoadState.Failure(nonceResult.exceptionOrNull()!!)

        val certs = hardwareKeyManager.generateAttestedKeyPair(nonceResult.getOrThrow())

        val verificationResult = repository.verifyDevice(
            userId = userId,
            deviceId = deviceId,
            nonce = nonceResult.getOrThrow(),
            certificates = certs
        )

        if (verificationResult.isFailure) return LoadState.Failure(verificationResult.exceptionOrNull()!!)

        return LoadState.Success(Unit)
    }
}