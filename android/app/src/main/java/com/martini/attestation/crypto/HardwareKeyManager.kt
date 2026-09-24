package com.martini.attestation.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPairGenerator
import java.security.KeyStore

class HardwareKeyManager {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "hardware_attestation_key"
    }

    suspend fun generateAttestedKeyPair(nonce: String) : List<String> = withContext(Dispatchers.IO) {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }

        val specBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(nonce.toByteArray(Charsets.UTF_8))

        generateKeyWithFallback(specBuilder)

        val chain = keyStore.getCertificateChain(KEY_ALIAS)
            ?: throw IllegalStateException("Certificate chain missing for alias: $KEY_ALIAS")

        chain.map { cert ->
            Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
        }
    }

    private fun generateKeyWithFallback(specBuilder: KeyGenParameterSpec.Builder) {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            KEYSTORE_PROVIDER
        )

        try {
            // Attempt StrongBox hardware security module first
            specBuilder.setIsStrongBoxBacked(true)
            keyPairGenerator.initialize(specBuilder.build())
            keyPairGenerator.generateKeyPair()
            return
        } catch (_: Exception) {
            // Device lacks StrongBox support; fallback to standard TEE
            specBuilder.setIsStrongBoxBacked(false)
        }

        keyPairGenerator.initialize(specBuilder.build())
        keyPairGenerator.generateKeyPair()
    }
}