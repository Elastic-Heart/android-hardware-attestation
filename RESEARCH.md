# Research Mode Findings (Phase 1)

## Current State Analysis

### 1. `certificate-verification-lambda`
- **File**: `certificate-verification-lambda/verifier/src/main/kotlin/org/example/VerifyHandler.kt`
- **File**: `certificate-verification-lambda/verifier/src/main/kotlin/org/example/KeyAttestationParser.kt`
- **Status**: Complete. The Lambda correctly extracts the ASN.1 Key Attestation sequence. It parses the Application ID (`KM_TAG_ATTESTATION_APPLICATION_ID` / 709). It performs exact checks against `expectedPackageName` and matching expected SHA256 digests (`EXPECTED_SIGNATURE_DIGEST`). No further code changes are strictly needed here.

### 2. Android App
- **Location**: `android/app/src/main/java/com/martini/attestation/`
- **Status**: Partially complete. Contains the setup UI (`SetupScreen`), `HardwareKeyManager` to generate the keypair with attestation, and repository logic to interact with the challenge/verification APIs. 
- **Missing**: 
  - Payment UI (`PaymentScreen`).
  - Navigation between `SetupScreen` (after successful setup) and `PaymentScreen`.
  - Cryptographic signing logic for the payment payload.
  - Network layer logic to communicate with the proposed Fake Payment API.

### 3. Fake Payment API (Backend)
- **Location**: Needs to be created (e.g., `payment-api-lambda`).
- **Status**: Not started.

---

## Proposed Touchpoints

### Android App Touchpoints
1. **`android/app/src/main/java/com/martini/attestation/payment/ui/PaymentScreen.kt`** (New)
   - Inputs for `recipient` (String) and `amount` (String/Double).
   - Button to trigger payment request.
2. **`android/app/src/main/java/com/martini/attestation/payment/ui/PaymentViewModel.kt`** (New)
   - State management and signing orchestration.
3. **`android/app/src/main/java/com/martini/attestation/crypto/HardwareKeyManager.kt`** (Existing)
   - Needs a `signPayload(data: ByteArray): ByteArray` method to utilize the attested `PrivateKey`.
4. **`android/app/src/main/java/com/martini/attestation/MainActivity.kt`** (Existing)
   - Add navigation (e.g., Compose Navigation or simple state switching) to swap from `SetupScreen` to `PaymentScreen`.

### Payment API Touchpoints
1. **`payment-api-lambda/`** (New directory)
   - **Data Flow**: Receives `userId`, `deviceId`, `recipient`, `amount`, and `signature`.
   - **Verification Flow**: 
     - Queries DynamoDB (`DEVICE_KEYS_TABLE_NAME`) for the registered `publicKey`.
     - Validates the signature of the payload (`recipient` + `amount`) using the retrieved public key.
     - Returns 200 on success, 400 on rejection.
