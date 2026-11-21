package org.example

import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Enumerated
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1Set
import org.bouncycastle.asn1.ASN1TaggedObject
import java.security.cert.X509Certificate

object KeyAttestationParser {

    private const val KEY_ATTESTATION_OID = "1.3.6.1.4.1.11129.2.1.17"
    private const val KM_TAG_ATTESTATION_APPLICATION_ID = 709

    // Sequence indices based on Key Attestation Extension ASN.1 schema
    private const val INDEX_ATTESTATION_VERSION = 0
    private const val INDEX_ATTESTATION_SECURITY_LEVEL = 1
    private const val INDEX_KEYMASTER_VERSION = 2
    private const val INDEX_KEYMASTER_SECURITY_LEVEL = 3
    private const val INDEX_ATTESTATION_CHALLENGE = 4
    private const val INDEX_UNIQUE_ID = 5
    private const val INDEX_SOFTWARE_ENFORCED = 6
    private const val INDEX_HARDWARE_ENFORCED = 7

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
        val challenge: String,
        val applicationId: AttestationApplicationId? = null
    )

    fun parse(certificate: X509Certificate): AttestationData {
        val extensionValue = certificate.getExtensionValue(KEY_ATTESTATION_OID)
            ?: throw IllegalArgumentException("Missing Android Key Attestation extension in certificate")

        val rootSequence = parseRootSequence(extensionValue)

        val attestationVersion = getIntegerFromSequence(rootSequence, INDEX_ATTESTATION_VERSION, "attestation version")
        val securityLevel = getEnumeratedFromSequence(rootSequence, INDEX_ATTESTATION_SECURITY_LEVEL, "security level")
        val challengeBytes = getOctetStringFromSequence(rootSequence, INDEX_ATTESTATION_CHALLENGE, "attestation challenge")
        val challengeString = String(challengeBytes, Charsets.UTF_8)

        val softwareEnforcedList = getSequenceOrNull(rootSequence, INDEX_SOFTWARE_ENFORCED)
        val hardwareEnforcedList = getSequenceOrNull(rootSequence, INDEX_HARDWARE_ENFORCED)

        val applicationId = extractApplicationId(softwareEnforcedList)
            ?: extractApplicationId(hardwareEnforcedList)

        return AttestationData(
            attestationVersion = attestationVersion,
            securityLevel = securityLevel,
            challenge = challengeString,
            applicationId = applicationId
        )
    }

    private fun parseRootSequence(extensionValue: ByteArray): ASN1Sequence {
        val octetStream = ASN1InputStream(extensionValue)
        val extensionOctets = octetStream.readObject() as? ASN1OctetString
            ?: throw IllegalArgumentException("Expected ASN1OctetString for extension value")

        val sequenceStream = ASN1InputStream(extensionOctets.octets)
        val rootSequence = sequenceStream.readObject() as? ASN1Sequence
            ?: throw IllegalArgumentException("Expected ASN1Sequence for root attestation data")
            
        return rootSequence
    }

    private fun getIntegerFromSequence(sequence: ASN1Sequence, index: Int, fieldName: String): Int {
        if (sequence.size() <= index) throw IllegalArgumentException("Missing field: $fieldName")
        val asn1Integer = sequence.getObjectAt(index) as? ASN1Integer
            ?: throw IllegalArgumentException("Expected ASN1Integer for $fieldName")
        return asn1Integer.value.toInt()
    }

    private fun getEnumeratedFromSequence(sequence: ASN1Sequence, index: Int, fieldName: String): Int {
        if (sequence.size() <= index) throw IllegalArgumentException("Missing field: $fieldName")
        val asn1Enumerated = sequence.getObjectAt(index) as? ASN1Enumerated
            ?: throw IllegalArgumentException("Expected ASN1Enumerated for $fieldName")
        return asn1Enumerated.value.toInt()
    }

    private fun getOctetStringFromSequence(sequence: ASN1Sequence, index: Int, fieldName: String): ByteArray {
        if (sequence.size() <= index) throw IllegalArgumentException("Missing field: $fieldName")
        val asn1OctetString = sequence.getObjectAt(index) as? ASN1OctetString
            ?: throw IllegalArgumentException("Expected ASN1OctetString for $fieldName")
        return asn1OctetString.octets
    }

    private fun getSequenceOrNull(sequence: ASN1Sequence, index: Int): ASN1Sequence? {
        if (sequence.size() <= index) return null
        return sequence.getObjectAt(index) as? ASN1Sequence
    }

    private fun extractApplicationId(authorizationList: ASN1Sequence?): AttestationApplicationId? {
        if (authorizationList == null) return null

        for (i in 0 until authorizationList.size()) {
            val encodable = authorizationList.getObjectAt(i)
            if (encodable is ASN1TaggedObject && encodable.tagNo == KM_TAG_ATTESTATION_APPLICATION_ID) {
                val octetString = ASN1OctetString.getInstance(encodable, true)
                return parseAttestationApplicationId(octetString.octets)
            }
        }
        
        return null
    }

    private fun parseAttestationApplicationId(applicationIdOctets: ByteArray): AttestationApplicationId {
        try {
            val stream = ASN1InputStream(applicationIdOctets)
            val rootSequence = stream.readObject() as? ASN1Sequence
                ?: throw IllegalArgumentException("Expected ASN1Sequence for AttestationApplicationId")

            if (rootSequence.size() < 2) {
                throw IllegalArgumentException("Malformed AttestationApplicationId sequence, expected at least 2 elements")
            }

            val packageInfosEncodable = rootSequence.getObjectAt(0)
            val signatureDigestsEncodable = rootSequence.getObjectAt(1)

            val packageInfos = parsePackageInfos(packageInfosEncodable)
            val signatureDigests = parseSignatureDigests(signatureDigestsEncodable)

            return AttestationApplicationId(packageInfos, signatureDigests)
        } catch (e: Exception) {
            if (e is IllegalArgumentException) throw e
            throw IllegalArgumentException("Failed to parse AttestationApplicationId: ${e.message}", e)
        }
    }

    private fun parsePackageInfos(encodable: ASN1Encodable): List<AttestationPackageInfo> {
        val items = getItemsFromSetOrSequence(encodable, "package_infos")

        return items.map { item ->
            val sequence = item as? ASN1Sequence
                ?: throw IllegalArgumentException("Expected ASN1Sequence for AttestationPackageInfo")
                
            if (sequence.size() < 2) {
                throw IllegalArgumentException("AttestationPackageInfo sequence must have at least 2 elements")
            }

            val packageNameOctets = sequence.getObjectAt(0) as? ASN1OctetString
                ?: throw IllegalArgumentException("Expected ASN1OctetString for package_name")
                
            val versionInteger = sequence.getObjectAt(1) as? ASN1Integer
                ?: throw IllegalArgumentException("Expected ASN1Integer for version")

            AttestationPackageInfo(
                packageName = String(packageNameOctets.octets, Charsets.UTF_8),
                version = versionInteger.value.toLong()
            )
        }
    }

    private fun parseSignatureDigests(encodable: ASN1Encodable): List<ByteArray> {
        val items = getItemsFromSetOrSequence(encodable, "signature_digests")

        return items.map { item ->
            val octetString = item as? ASN1OctetString
                ?: throw IllegalArgumentException("Expected ASN1OctetString for signature_digest")
            octetString.octets
        }
    }

    private fun getItemsFromSetOrSequence(encodable: ASN1Encodable, fieldName: String): List<ASN1Encodable> {
        return when (encodable) {
            is ASN1Set -> (0 until encodable.size()).map { encodable.getObjectAt(it) }
            is ASN1Sequence -> (0 until encodable.size()).map { encodable.getObjectAt(it) }
            else -> throw IllegalArgumentException("Expected ASN1Set or ASN1Sequence for $fieldName")
        }
    }
}