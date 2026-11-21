# Specification Mode Blueprint (Phase 2)

## 1. Requirements (GIVEN/WHEN/THEN)

### Payment Flow
- **GIVEN** an Android device that has successfully completed hardware attestation and has its public key registered in DynamoDB.
- **WHEN** the user inputs a `recipient` and `amount` and initiates a payment.
- **THEN** the Android app signs a payload containing the payment details using the attested private key.
- **AND** the app sends the payment request along with the signature to the Payment API.
- **THEN** the Payment API retrieves the public key for the device from DynamoDB.
- **AND** the Payment API verifies the signature using the retrieved public key.
- **THEN** the Payment API returns HTTP 200 (Success) if the signature is valid, or HTTP 400 (Rejected) if invalid.

## 2. Interface & Contract Changes

### Android App Contracts
**Payment Request Payload (JSON over HTTP)**
```json
{
  "userId": "String",
  "deviceId": "String",
  "recipient": "String",
  "amount": "Double",
  "signatureBase64": "String"
}
```

**Signed Data Payload (Raw Bytes for signing)**
The data to be signed by the private key will be a structured string, encoded in UTF-8:
`"${recipient}:${amount}"` 
*(Example: `"john.doe@example.com:150.00"`)*

### Payment API Contracts
**Endpoint:** `POST /payment`

**Request:** `PaymentRequest` (matches Android payload)

**Response (Success):** HTTP 200
```json
{
  "status": "success",
  "message": "Payment processed"
}
```

**Response (Failure):** HTTP 400
```json
{
  "status": "error",
  "message": "Signature verification failed"
}
```

### Database Schema (DynamoDB)
No changes required. `device-keys-table` already stores:
- `deviceId` (Partition Key)
- `userId`
- `publicKey` (Base64)
- `secLevel`

## 3. Edge Cases & Error Handling

- **Missing Key in DB:** If the Payment API queries DynamoDB for `deviceId` and finds no record, return HTTP 400 ("Device not registered/attested").
- **Malformed Signature:** If `signatureBase64` cannot be decoded or verified, return HTTP 400.
- **KeyManager Exception (Android):** If signing fails on the Android side (e.g., key was invalidated by OS policy), surface a localized error on the UI.
- **Network Failure (Android):** Standard timeout/no-connection handling, prompting the user to retry.

## 4. Itemized Task Checklist

- [x] **Task 1.1:** Update `HardwareKeyManager` in Android App to implement a `signPayload(data: ByteArray): ByteArray` method.
- [x] **Task 1.2:** Create `PaymentScreen` and `PaymentViewModel` in the Android App (inputs, state holding).
- [x] **Task 1.3:** Scaffold `payment-api-lambda` backend service (Go or Kotlin).
- [x] **Task 2.1:** Implement navigation in `MainActivity` to transition from `SetupScreen` (on success) to `PaymentScreen`.
- [x] **Task 2.2:** Create Payment API network models and repository logic in the Android App to POST to the new backend.
- [x] **Task 3.1:** Implement DynamoDB public key lookup in `payment-api-lambda`.
- [x] **Task 3.2:** Implement ECDSA/RSA signature verification logic in `payment-api-lambda`.
- [x] **Task 4.1:** Wire up AWS API Gateway proxy handlers for `payment-api-lambda` and test end-to-end integration.

## 5. Concurrency

```json
{
  "1": {
    "tasks": [1.1, 1.2, 1.3],
    "dependsOn": []
  },
  "2": {
    "tasks": [2.1, 2.2],
    "dependsOn": [1.1, 1.2]
  },
  "3": {
    "tasks": [3.1, 3.2],
    "dependsOn": [1.3]
  },
  "4": {
    "tasks": [4.1],
    "dependsOn": [2.2, 3.1, 3.2]
  }
}
```
