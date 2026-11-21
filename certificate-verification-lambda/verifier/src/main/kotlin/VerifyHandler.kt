package org.example

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.google.gson.Gson
import org.example.model.VerifyRequest
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import java.util.HexFormat

class VerifyHandler @JvmOverloads constructor(
    private val dynamodb: DynamoDbClient = DynamoDbClient.create(),
    private val nonceTableName: String = System.getenv("NONCE_TABLE_NAME") ?: "",
    private val deviceKeysTableName: String = System.getenv("DEVICE_KEYS_TABLE_NAME") ?: "",
    private val expectedPackageName: String = System.getenv("EXPECTED_PACKAGE_NAME") ?: "com.martini.attestation",
    private val expectedSignatureDigestEnv: String = System.getenv("EXPECTED_SIGNATURE_DIGEST") ?: "",
    private val trustedRoots: List<X509Certificate> = GoogleRootCAs.TRUSTED_ROOTS
) : RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private val gson = Gson()

    override fun handleRequest(
        input: APIGatewayProxyRequestEvent?,
        context: Context?
    ): APIGatewayProxyResponseEvent? {
        val logger = context?.logger

        return try {
            logger?.log("Received verification request. Body: ${input?.body}")
            val request = gson.fromJson(input!!.body, VerifyRequest::class.java)
            logger?.log("Parsed request for userId: ${request.userId}, deviceId: ${request.deviceId}")

            if (consumeNonce(request.nonce, logger).not()) {
                logger?.log("Nonce validation failed for nonce: ${request.nonce}")
                return buildResponse(
                    statusCode = 400,
                    body = mapOf("error" to "Invalid or expired nonce"),
                )
            }

            val certFactory = CertificateFactory.getInstance("X.509")

            val certs = request.certificateChain.map { base64Cert ->
                val decoded = Base64.getDecoder().decode(base64Cert)
                certFactory.generateCertificate(decoded.inputStream()) as X509Certificate
            }
            logger?.log("Successfully parsed ${certs.size} certificates in the chain.")

            certs.zipWithNext { child, parent->
                child.verify(parent.publicKey)
            }

            logger?.log("Certificate chain signatures verified successfully.")

            verifyGoogleRootTrust(certs)

            val attData = KeyAttestationParser.parse(certs[0])

            if (attData.challenge != request.nonce) {
                logger?.log("Nonce mismatch: expected ${request.nonce}, got ${attData.challenge}")
                return buildRequestFailedResponse(
                    message = "Invalid nonce for ${request.nonce}",
                )
            }

            val appId = attData.applicationId
            if (appId == null) {
                logger?.log("Missing attestation application ID in certificate")
                return buildRequestFailedResponse("Missing attestation application ID in certificate")
            }

            if (appId.packageInfos.none { it.packageName == expectedPackageName }) {
                logger?.log("Application ID package mismatch: expected $expectedPackageName, got ${appId.packageInfos.map { it.packageName }}")
                return buildRequestFailedResponse("Application ID package mismatch")
            }

            if (expectedSignatureDigestEnv.isBlank()) {
                logger?.log("EXPECTED_SIGNATURE_DIGEST is not configured")
                return buildRequestFailedResponse("Missing or empty expected signature digest configuration")
            }

            val expectedDigests = parseExpectedDigests(expectedSignatureDigestEnv)
            if (expectedDigests.isEmpty()) {
                logger?.log("No valid expected signature digests parsed")
                return buildRequestFailedResponse("Missing or empty expected signature digest configuration")
            }

            val hasMatchingDigest = appId.signatureDigests.any { actualDigest ->
                expectedDigests.any { expectedDigest -> actualDigest.contentEquals(expectedDigest) }
            }

            if (!hasMatchingDigest) {
                logger?.log("Signature digest mismatch")
                return buildRequestFailedResponse("Application ID signature digest mismatch")
            }

            if (attData.securityLevel !in 1..2) {
                logger?.log("Unsupported security level: ${attData.securityLevel}")
                return buildRequestFailedResponse(
                    message = "Invalid security level, should be either TEE or STRONGBOX"
                )
            }

            val verificationLevel = when (attData.securityLevel) {
                1 -> "TEE"
                else -> "STRONGBOX"
            }

            val publicKeyBase64 = Base64.getEncoder().encodeToString(certs[0].publicKey.encoded)

            savePublicKey(
                userId = request.userId,
                deviceId = request.deviceId,
                publicKey = publicKeyBase64,
                secLevel = verificationLevel
            )

            logger?.log("Successfully saved public key for user: ${request.userId}")

            buildResponse(
                statusCode = 200,
                body = mapOf(
                    "securityLevel" to verificationLevel,
                    "publicKeyBase64" to publicKeyBase64
                )
            )
        } catch (e: Exception) {
            logger?.log("ERROR in VerifyHandler: ${e.message}\n${e.stackTraceToString()}")
            buildRequestFailedResponse(message = "Bad request")
        }
    }

    private fun buildRequestFailedResponse(message: String): APIGatewayProxyResponseEvent {
        return buildResponse(400, mapOf("error" to message))
    }

    private fun consumeNonce(nonce: String, logger: LambdaLogger?) : Boolean {
        return try {
            dynamodb.deleteItem(
                DeleteItemRequest.builder()
                    .tableName(nonceTableName)
                    .key(mapOf("nonce" to AttributeValue.builder().s(nonce).build()))
                    .conditionExpression("attribute_exists(nonce)")
                    .build()
            )
            true
        } catch (e: Exception) {
            logger?.log("Failed to consume nonce '$nonce': ${e.message}")
            false
        }
    }

    private fun verifyGoogleRootTrust(certs: List<X509Certificate>) {
        val topCert = certs.last()

        val isSignedByGoogle = trustedRoots.any { googleRoot ->
            try {
                topCert.verify(googleRoot.publicKey)
                true
            } catch (_: Exception) {
                false
            }
        }

        if (!isSignedByGoogle) throw SecurityException("Google root certificate is not signed in")
    }

    private fun parseExpectedDigests(raw: String): List<ByteArray> {
        val hexFormat = HexFormat.of()
        return raw.split(",")
            .map { it.trim().replace(":", "").replace(" ", "").lowercase() }
            .filter { it.isNotEmpty() }
            .mapNotNull { hex ->
                try {
                    hexFormat.parseHex(hex)
                } catch (_: Exception) {
                    null
                }
            }
    }

    private fun savePublicKey(userId: String, deviceId: String, publicKey: String, secLevel: String) {
        dynamodb.putItem(
            PutItemRequest.builder()
                .tableName(deviceKeysTableName)
                .item(mapOf(
                    "userId" to AttributeValue.builder().s(userId).build(),
                    "deviceId" to AttributeValue.builder().s(deviceId).build(),
                    "publicKey" to AttributeValue.builder().s(publicKey).build(),
                    "secLevel" to AttributeValue.builder().s(secLevel).build()
                ))
                .build()
        )
    }

    private fun buildResponse(statusCode: Int, body: Any) : APIGatewayProxyResponseEvent {
        return APIGatewayProxyResponseEvent()
            .withStatusCode(statusCode)
            .withHeaders(mapOf("Content-Type" to "application/json"))
            .withBody(gson.toJson(body))
    }
}