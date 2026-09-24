package com.martini.attestation.setup.data.services

import com.martini.attestation.setup.data.model.ChallengeRequest
import com.martini.attestation.setup.data.model.ChallengeResponse
import com.martini.attestation.setup.data.model.ErrorResponse
import com.martini.attestation.setup.data.model.VerifyRequest
import com.martini.attestation.setup.data.model.VerifyResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class AttestationApiService(
    private val client: HttpClient
) {
    suspend fun getChallenge(request: ChallengeRequest): ChallengeResponse {
        val response = client.post("attestation/challenge") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val error = runCatching { response.body<ErrorResponse>() }.getOrNull()
            throw IllegalStateException(error?.error ?: "Failed to fetch challenge: ${response.status}")
        }
    }

    suspend fun verify(request: VerifyRequest): VerifyResponse {
        val response = client.post("verify") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val error = runCatching { response.body<ErrorResponse>() }.getOrNull()
            throw IllegalStateException(error?.error ?: "Verification failed: ${response.status}")
        }
    }
}