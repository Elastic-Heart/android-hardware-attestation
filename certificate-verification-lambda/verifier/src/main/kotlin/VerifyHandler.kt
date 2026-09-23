package org.example

import com.amazonaws.services.lambda.runtime.Context
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

class VerifyHandler : RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private val gson = Gson()
    private val dynamodb = DynamoDbClient.create()
    private val nonceTableName = System.getenv("NONCE_TABLE_NAME") ?: ""
    private val deviceKeysTableName = System.getenv("DEVICE_KEYS_TABLE_NAME") ?: ""

    override fun handleRequest(
        input: APIGatewayProxyRequestEvent?,
        context: Context?
    ): APIGatewayProxyResponseEvent? {
        return try {
            val request = gson.fromJson(input!!.body, VerifyRequest::class.java)

            if (consumeNonce(request.nonce).not()) {
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

            certs.zipWithNext { child, parent->
                child.verify(parent.publicKey)
            }

            verifyGoogleRootTrust(certs)

            val attData = KeyAttestationParser.parse(certs[0])

            if (attData.challenge != request.nonce) {
                return buildRequestFailedResponse()
            }

            if (attData.securityLevel !in 1..2) {
                return buildRequestFailedResponse()
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

            buildResponse(
                statusCode = 200,
                body = mapOf(
                    "securityLevel" to verificationLevel,
                    "publicKeyBase64" to publicKeyBase64
                )
            )
        } catch (_: Exception) {
            buildRequestFailedResponse()
        }
    }

    private fun buildRequestFailedResponse(): APIGatewayProxyResponseEvent {
        return buildResponse(400, mapOf("error" to "Verification failed"))
    }

    private fun consumeNonce(nonce: String) : Boolean {
        return try {
            dynamodb.deleteItem(
                DeleteItemRequest.builder()
                    .tableName(nonceTableName)
                    .key(mapOf("nonce" to AttributeValue.builder().s(nonce).build()))
                    .conditionExpression("attribute_exists(nonce)")
                    .build()
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun verifyGoogleRootTrust(certs: List<X509Certificate>) {
        val topCert = certs.last()

        val isSignedByGoogle = GoogleRootCAs.TRUSTED_ROOTS.any { googleRoot ->
            try {
                topCert.verify(googleRoot.publicKey)
                true
            } catch (_: Exception) {
                false
            }
        }

        if (!isSignedByGoogle) throw SecurityException("Google root certificate is not signed in")
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