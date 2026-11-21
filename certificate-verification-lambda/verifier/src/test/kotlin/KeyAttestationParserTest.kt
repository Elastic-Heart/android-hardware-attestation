package org.example

import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyAttestationParserTest {

    private fun createCertificate(
        attestationVersion: Int = 3,
        securityLevel: Int = 1,
        challenge: String = "test-challenge-nonce",
        softwareEnforced: ASN1Sequence = DERSequence(),
        hardwareEnforced: ASN1Sequence = DERSequence(),
        rawExtension: ASN1Encodable? = null
    ): X509Certificate {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val builder = JcaX509v3CertificateBuilder(
            X500Name("CN=Test Issuer"),
            BigInteger.valueOf(System.currentTimeMillis()),
            Date(System.currentTimeMillis() - 60000),
            Date(System.currentTimeMillis() + 3600000),
            X500Name("CN=Test Subject"),
            keyPair.public
        )

        val keyDescription = rawExtension ?: DERSequence(
            arrayOf(
                ASN1Integer(attestationVersion.toLong()),
                ASN1Enumerated(securityLevel),
                ASN1Integer(attestationVersion.toLong()),
                ASN1Enumerated(securityLevel),
                DEROctetString(challenge.toByteArray(Charsets.UTF_8)),
                DEROctetString(ByteArray(0)),
                softwareEnforced,
                hardwareEnforced
            )
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
        val packageInfosSet = DERSet(packageInfosVector)

        val digestsVector = ASN1EncodableVector()
        signatureDigests.forEach { digest ->
            digestsVector.add(DEROctetString(digest))
        }
        val signatureDigestsSet = DERSet(digestsVector)

        val seq = DERSequence(arrayOf(packageInfosSet, signatureDigestsSet))
        return seq.encoded
    }

    private fun buildAuthList(appIdBytes: ByteArray?): ASN1Sequence {
        val vector = ASN1EncodableVector()
        if (appIdBytes != null) {
            vector.add(DERTaggedObject(true, 709, DEROctetString(appIdBytes)))
        }
        return DERSequence(vector)
    }

    @Test
    fun `parses application ID in softwareEnforced list`() {
        val expectedDigest = ByteArray(32) { 0x3B.toByte() }
        val appIdBytes = buildAttestationApplicationIdBytes(
            packages = listOf("com.martini.attestation" to 1L),
            signatureDigests = listOf(expectedDigest)
        )
        val cert = createCertificate(
            softwareEnforced = buildAuthList(appIdBytes),
            hardwareEnforced = buildAuthList(null)
        )

        val data = KeyAttestationParser.parse(cert)

        val appId = data.applicationId
        assertNotNull(appId)
        assertEquals(1, appId.packageInfos.size)
        assertEquals("com.martini.attestation", appId.packageInfos[0].packageName)
        assertEquals(1L, appId.packageInfos[0].version)
        assertEquals(1, appId.signatureDigests.size)
        assertTrue(appId.signatureDigests[0].contentEquals(expectedDigest))
    }

    @Test
    fun `parses application ID in hardwareEnforced fallback`() {
        val expectedDigest = ByteArray(32) { 0x4C.toByte() }
        val appIdBytes = buildAttestationApplicationIdBytes(
            packages = listOf("com.martini.attestation" to 42L),
            signatureDigests = listOf(expectedDigest)
        )
        val cert = createCertificate(
            softwareEnforced = buildAuthList(null),
            hardwareEnforced = buildAuthList(appIdBytes)
        )

        val data = KeyAttestationParser.parse(cert)

        val appId = data.applicationId
        assertNotNull(appId)
        assertEquals("com.martini.attestation", appId.packageInfos[0].packageName)
        assertEquals(42L, appId.packageInfos[0].version)
        assertTrue(appId.signatureDigests[0].contentEquals(expectedDigest))
    }

    @Test
    fun `returns null applicationId when tag 709 is omitted`() {
        val cert = createCertificate(
            softwareEnforced = buildAuthList(null),
            hardwareEnforced = buildAuthList(null)
        )

        val data = KeyAttestationParser.parse(cert)

        assertNull(data.applicationId)
    }

    @Test
    fun `parses multiple packages and signature digests`() {
        val digest1 = ByteArray(32) { 1 }
        val digest2 = ByteArray(32) { 2 }
        val appIdBytes = buildAttestationApplicationIdBytes(
            packages = listOf("pkg.one" to 10L, "pkg.two" to 20L),
            signatureDigests = listOf(digest1, digest2)
        )
        val cert = createCertificate(softwareEnforced = buildAuthList(appIdBytes))

        val data = KeyAttestationParser.parse(cert)

        val appId = data.applicationId
        assertNotNull(appId)
        assertEquals(2, appId.packageInfos.size)
        assertEquals(setOf("pkg.one", "pkg.two"), appId.packageInfos.map { it.packageName }.toSet())
        assertEquals(2, appId.signatureDigests.size)
    }

    @Test
    fun `throws IllegalArgumentException on malformed tag 709 octets`() {
        val vector = ASN1EncodableVector()
        vector.add(DERTaggedObject(true, 709, DEROctetString(byteArrayOf(0x01, 0x02, 0x03)))) // invalid ASN.1
        val cert = createCertificate(softwareEnforced = DERSequence(vector))

        assertThrows<IllegalArgumentException> {
            KeyAttestationParser.parse(cert)
        }
    }
}
