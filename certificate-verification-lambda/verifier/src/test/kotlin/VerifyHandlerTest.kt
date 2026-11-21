package org.example

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.google.gson.Gson
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse
import java.lang.reflect.Proxy
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VerifyHandlerTest {

    private val gson = Gson()
    private val expectedDigestHex = "3B:A6:65:75:04:29:36:2C:4C:FC:D6:50:5E:FD:BE:0F:BE:1A:F8:6D:58:3F:2D:D5:52:62:24:C2:AD:DD:D7:73"
    private val expectedDigestBytes = HexFormat.of().parseHex(expectedDigestHex.replace(":", ""))

    private fun createFakeDynamoDbClient(): DynamoDbClient {
        return Proxy.newProxyInstance(
            DynamoDbClient::class.java.classLoader,
            arrayOf(DynamoDbClient::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "deleteItem" -> DeleteItemResponse.builder().build()
                "putItem" -> PutItemResponse.builder().build()
                "close" -> null
                else -> null
            }
        } as DynamoDbClient
    }

    private fun createRootCa(): Pair<KeyPair, X509Certificate> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val name = X500Name("CN=Test Root CA")
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(1),
            Date(System.currentTimeMillis() - 60000),
            Date(System.currentTimeMillis() + 3600000),
            name,
            keyPair.public
        )
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        return keyPair to cert
    }

    private fun createLeafCert(
        rootKeyPair: KeyPair,
        rootCert: X509Certificate,
        nonce: String,
        appIdBytes: ByteArray?
    ): X509Certificate {
        val leafKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(rootKeyPair.private)

        val authListVector = ASN1EncodableVector()
        if (appIdBytes != null) {
            authListVector.add(DERTaggedObject(true, 709, DEROctetString(appIdBytes)))
        }

        val keyDescription = DERSequence(
            arrayOf(
                ASN1Integer(3),
                ASN1Enumerated(1), // TEE
                ASN1Integer(3),
                ASN1Enumerated(1),
                DEROctetString(nonce.toByteArray(Charsets.UTF_8)),
                DEROctetString(ByteArray(0)),
                DERSequence(authListVector),
                DERSequence()
            )
        )

        val builder = JcaX509v3CertificateBuilder(
            X500Name.getInstance(rootCert.subjectX500Principal.encoded),
            BigInteger.valueOf(2),
            Date(System.currentTimeMillis() - 60000),
            Date(System.currentTimeMillis() + 3600000),
            X500Name("CN=Leaf Attestation Cert"),
            leafKeyPair.public
        )
        builder.addExtension(ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17"), false, keyDescription)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    private fun buildAttestationApplicationIdBytes(
        packages: List<Pair<String, Long>>,
        signatureDigests: List<ByteArray>
    ): ByteArray {
        val packageInfosVector = ASN1EncodableVector()
        packages.forEach { (pkgName, version) ->
            packageInfosVector.add(
                DERSequence(
                    arrayOf(
                        DEROctetString(pkgName.toByteArray(Charsets.UTF_8)),
                        ASN1Integer(version)
                    )
                )
            )
        }

        val digestsVector = ASN1EncodableVector()
        signatureDigests.forEach { digest ->
            digestsVector.add(DEROctetString(digest))
        }

        return DERSequence(arrayOf(DERSet(packageInfosVector), DERSet(digestsVector))).encoded
    }

    private fun createVerifyRequestEvent(
        leafCert: X509Certificate,
        rootCert: X509Certificate,
        nonce: String
    ): APIGatewayProxyRequestEvent {
        val bodyMap = mapOf(
            "userId" to "user-123",
            "deviceId" to "device-456",
            "nonce" to nonce,
            "certificateChain" to listOf(
                Base64.getEncoder().encodeToString(leafCert.encoded),
                Base64.getEncoder().encodeToString(rootCert.encoded)
            )
        )
        return APIGatewayProxyRequestEvent().withBody(gson.toJson(bodyMap))
    }

    @Test
    fun `returns 200 on valid attestation matching package and signature digest`() {
        val (rootKeyPair, rootCert) = createRootCa()
        val nonce = "valid-test-nonce"
        val appIdBytes = buildAttestationApplicationIdBytes(
            packages = listOf("com.martini.attestation" to 1L),
            signatureDigests = listOf(expectedDigestBytes)
        )
        val leafCert = createLeafCert(rootKeyPair, rootCert, nonce, appIdBytes)
        val event = createVerifyRequestEvent(leafCert, rootCert, nonce)

        val handler = VerifyHandler(
            dynamodb = createFakeDynamoDbClient(),
            expectedPackageName = "com.martini.attestation",
            expectedSignatureDigestEnv = expectedDigestHex,
            trustedRoots = listOf(rootCert)
        )

        val response = handler.handleRequest(event, null)

        assertEquals(200, response?.statusCode)
        assertTrue(response?.body?.contains("TEE") == true)
    }

    @Test
    fun `returns 400 when attestation application ID is missing`() {
        val (rootKeyPair, rootCert) = createRootCa()
        val nonce = "valid-test-nonce"
        val leafCert = createLeafCert(rootKeyPair, rootCert, nonce, appIdBytes = null)
        val event = createVerifyRequestEvent(leafCert, rootCert, nonce)

        val handler = VerifyHandler(
            dynamodb = createFakeDynamoDbClient(),
            expectedPackageName = "com.martini.attestation",
            expectedSignatureDigestEnv = expectedDigestHex,
            trustedRoots = listOf(rootCert)
        )

        val response = handler.handleRequest(event, null)

        assertEquals(400, response?.statusCode)
        assertTrue(response?.body?.contains("Missing attestation application ID in certificate") == true)
    }

    @Test
    fun `returns 400 on package name mismatch`() {
        val (rootKeyPair, rootCert) = createRootCa()
        val nonce = "valid-test-nonce"
        val appIdBytes = buildAttestationApplicationIdBytes(
            packages = listOf("com.other.unauthorized" to 1L),
            signatureDigests = listOf(expectedDigestBytes)
        )
        val leafCert = createLeafCert(rootKeyPair, rootCert, nonce, appIdBytes)
        val event = createVerifyRequestEvent(leafCert, rootCert, nonce)

        val handler = VerifyHandler(
            dynamodb = createFakeDynamoDbClient(),
            expectedPackageName = "com.martini.attestation",
            expectedSignatureDigestEnv = expectedDigestHex,
            trustedRoots = listOf(rootCert)
        )

        val response = handler.handleRequest(event, null)

        assertEquals(400, response?.statusCode)
        assertTrue(response?.body?.contains("Application ID package mismatch") == true)
    }

    @Test
    fun `returns 400 when EXPECTED_SIGNATURE_DIGEST is empty`() {
        val (rootKeyPair, rootCert) = createRootCa()
        val nonce = "valid-test-nonce"
        val appIdBytes = buildAttestationApplicationIdBytes(
            packages = listOf("com.martini.attestation" to 1L),
            signatureDigests = listOf(expectedDigestBytes)
        )
        val leafCert = createLeafCert(rootKeyPair, rootCert, nonce, appIdBytes)
        val event = createVerifyRequestEvent(leafCert, rootCert, nonce)

        val handler = VerifyHandler(
            dynamodb = createFakeDynamoDbClient(),
            expectedPackageName = "com.martini.attestation",
            expectedSignatureDigestEnv = "",
            trustedRoots = listOf(rootCert)
        )

        val response = handler.handleRequest(event, null)

        assertEquals(400, response?.statusCode)
        assertTrue(response?.body?.contains("Missing or empty expected signature digest configuration") == true)
    }

    @Test
    fun `returns 400 on signature digest mismatch`() {
        val (rootKeyPair, rootCert) = createRootCa()
        val nonce = "valid-test-nonce"
        val wrongDigest = ByteArray(32) { 0xAA.toByte() }
        val appIdBytes = buildAttestationApplicationIdBytes(
            packages = listOf("com.martini.attestation" to 1L),
            signatureDigests = listOf(wrongDigest)
        )
        val leafCert = createLeafCert(rootKeyPair, rootCert, nonce, appIdBytes)
        val event = createVerifyRequestEvent(leafCert, rootCert, nonce)

        val handler = VerifyHandler(
            dynamodb = createFakeDynamoDbClient(),
            expectedPackageName = "com.martini.attestation",
            expectedSignatureDigestEnv = expectedDigestHex,
            trustedRoots = listOf(rootCert)
        )

        val response = handler.handleRequest(event, null)

        assertEquals(400, response?.statusCode)
        assertTrue(response?.body?.contains("Application ID signature digest mismatch") == true)
    }

    @Test
    fun `returns 200 when matching one of multiple comma-separated expected digests`() {
        val (rootKeyPair, rootCert) = createRootCa()
        val nonce = "valid-test-nonce"
        val altDigestHex = "AA:BB:CC:DD:04:29:36:2C:4C:FC:D6:50:5E:FD:BE:0F:BE:1A:F8:6D:58:3F:2D:D5:52:62:24:C2:AD:DD:D7:73"
        val commaSeparatedDigests = "$altDigestHex, $expectedDigestHex"

        val appIdBytes = buildAttestationApplicationIdBytes(
            packages = listOf("com.martini.attestation" to 1L),
            signatureDigests = listOf(expectedDigestBytes)
        )
        val leafCert = createLeafCert(rootKeyPair, rootCert, nonce, appIdBytes)
        val event = createVerifyRequestEvent(leafCert, rootCert, nonce)

        val handler = VerifyHandler(
            dynamodb = createFakeDynamoDbClient(),
            expectedPackageName = "com.martini.attestation",
            expectedSignatureDigestEnv = commaSeparatedDigests,
            trustedRoots = listOf(rootCert)
        )

        val response = handler.handleRequest(event, null)

        assertEquals(200, response?.statusCode)
    }
}
