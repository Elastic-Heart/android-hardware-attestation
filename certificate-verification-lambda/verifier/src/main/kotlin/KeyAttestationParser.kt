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

    //schema can be found here
    //https://source.android.com/docs/security/features/keystore/attestation
//    SecurityLevel ::= ENUMERATED {
//        Software                     (0),
//        TrustedEnvironment           (1),
//        StrongBox                    (2),
//    }
    data class AttestationData(
        val attestationVersion: Int,
        val securityLevel: Int, // 1 = TEE, 2 = StrongBox
        val challenge: String // nonce
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

//        KeyDescription ::= SEQUENCE {
//            attestationVersion           INTEGER, # Value 500
//            attestationSecurityLevel     SecurityLevel,
//            keyMintVersion               INTEGER, # Value 500
//            keyMintSecurityLevel         SecurityLevel,
//            attestationChallenge         OCTET_STRING,
//            uniqueId                     OCTET_STRING,
//            softwareEnforced             AuthorizationList,
//            hardwareEnforced             AuthorizationList,
//        }
        val version = (seq.getObjectAt(0) as ASN1Integer).value.toInt()
        val securityLevel = (seq.getObjectAt(1) as ASN1Enumerated).value.toInt()
        val challengeBytes = (seq.getObjectAt(4) as ASN1OctetString).octets

        return AttestationData(
            attestationVersion = version,
            securityLevel = securityLevel,
            challenge = String(challengeBytes, Charsets.UTF_8)
        )
    }
}