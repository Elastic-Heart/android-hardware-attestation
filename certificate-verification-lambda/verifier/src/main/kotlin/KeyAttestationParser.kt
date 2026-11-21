package org.example

import org.bouncycastle.asn1.ASN1Enumerated
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import java.security.cert.X509Certificate

object KeyAttestationParser {

    // Android Key Attestation Extension OID
    //This is essentially a location coordinate for the
    //AttestationData below, which can be found inside the
    //extensions block of the X.509
    //https://source.android.com/docs/security/features/keystore/attestation
    private const val KEY_ATTESTATION_OID = "1.3.6.1.4.1.11129.2.1.17"
    private const val KM_TAG_ATTESTATION_APPLICATION_ID = 709

    data class AttestationPackageInfo(
        val packageName: String,
        val version: Long
    )

    data class AttestationApplicationId(
        val packageInfos: List<AttestationPackageInfo>,
        val signatureDigests: List<ByteArray>
    )

    data class AttestationData(
        val attestationVersion: Int,
        val securityLevel: Int, // 1 = TEE, 2 = StrongBox
        val challenge: String, // nonce
        val applicationId: AttestationApplicationId? = null
    )

    fun parse(cert: X509Certificate): AttestationData {
        val extensionValue = cert.getExtensionValue(KEY_ATTESTATION_OID)
            ?: throw IllegalArgumentException("Missing Android Key Attestation extension in certificate")

        // Read ASN.1 Octet String wrapper
        val octetStream = ASN1InputStream(extensionValue)
        val octets = (octetStream.readObject() as ASN1OctetString).octets

        // Parse Root ASN.1 Sequence
        val seqStream = ASN1InputStream(octets)
        val seq = seqStream.readObject() as ASN1Sequence

        val version = (seq.getObjectAt(0) as ASN1Integer).value.toInt()
        val securityLevel = (seq.getObjectAt(1) as ASN1Enumerated).value.toInt()
        val challengeBytes = (seq.getObjectAt(4) as ASN1OctetString).octets

        val softwareEnforced = if (seq.size() > 6) seq.getObjectAt(6) as? ASN1Sequence else null
        val hardwareEnforced = if (seq.size() > 7) seq.getObjectAt(7) as? ASN1Sequence else null

        val applicationId = extractApplicationId(softwareEnforced)
            ?: extractApplicationId(hardwareEnforced)

        return AttestationData(
            attestationVersion = version,
            securityLevel = securityLevel,
            challenge = String(challengeBytes, Charsets.UTF_8),
            applicationId = applicationId
        )
    }

    private fun extractApplicationId(authList: ASN1Sequence?): AttestationApplicationId? {
        if (authList == null) return null

        for (i in 0 until authList.size()) {
            val encodable = authList.getObjectAt(i)
            if (encodable is org.bouncycastle.asn1.ASN1TaggedObject && encodable.tagNo == KM_TAG_ATTESTATION_APPLICATION_ID) {
                val octetString = org.bouncycastle.asn1.ASN1OctetString.getInstance(encodable, true)
                return parseAttestationApplicationId(octetString.octets)
            }
        }
        return null
    }

    private fun parseAttestationApplicationId(octets: ByteArray): AttestationApplicationId {
        try {
            val stream = ASN1InputStream(octets)
            val root = stream.readObject()
            if (root !is ASN1Sequence || root.size() < 2) {
                throw IllegalArgumentException("Malformed AttestationApplicationId sequence")
            }

            val packageInfosSeq = root.getObjectAt(0)
            val signatureDigestsSeq = root.getObjectAt(1)

            val packageInfos = parsePackageInfos(packageInfosSeq)
            val signatureDigests = parseSignatureDigests(signatureDigestsSeq)

            return AttestationApplicationId(packageInfos, signatureDigests)
        } catch (e: Exception) {
            if (e is IllegalArgumentException) throw e
            throw IllegalArgumentException("Failed to parse AttestationApplicationId: ${e.message}", e)
        }
    }

    private fun parsePackageInfos(encodable: org.bouncycastle.asn1.ASN1Encodable): List<AttestationPackageInfo> {
        val items = when (encodable) {
            is org.bouncycastle.asn1.ASN1Set -> (0 until encodable.size()).map { encodable.getObjectAt(it) }
            is ASN1Sequence -> (0 until encodable.size()).map { encodable.getObjectAt(it) }
            else -> throw IllegalArgumentException("Expected ASN1Set or ASN1Sequence for package_infos")
        }

        return items.map { item ->
            if (item !is ASN1Sequence || item.size() < 2) {
                throw IllegalArgumentException("Expected ASN1Sequence for AttestationPackageInfo")
            }
            val packageNameBytes = (item.getObjectAt(0) as? ASN1OctetString)?.octets
                ?: throw IllegalArgumentException("Expected OCTET_STRING for package_name")
            val version = (item.getObjectAt(1) as? ASN1Integer)?.value?.toLong()
                ?: throw IllegalArgumentException("Expected INTEGER for version")

            AttestationPackageInfo(
                packageName = String(packageNameBytes, Charsets.UTF_8),
                version = version
            )
        }
    }

    private fun parseSignatureDigests(encodable: org.bouncycastle.asn1.ASN1Encodable): List<ByteArray> {
        val items = when (encodable) {
            is org.bouncycastle.asn1.ASN1Set -> (0 until encodable.size()).map { encodable.getObjectAt(it) }
            is ASN1Sequence -> (0 until encodable.size()).map { encodable.getObjectAt(it) }
            else -> throw IllegalArgumentException("Expected ASN1Set or ASN1Sequence for signature_digests")
        }

        return items.map { item ->
            (item as? ASN1OctetString)?.octets
                ?: throw IllegalArgumentException("Expected OCTET_STRING for signature_digest")
        }
    }
}