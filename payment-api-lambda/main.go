package main

import (
	"context"
	"crypto/ecdsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log/slog"
	"os"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-lambda-go/lambdacontext"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

type PaymentRequest struct {
	UserID          string  `json:"userId"`
	DeviceID        string  `json:"deviceId"`
	Recipient       string  `json:"recipient"`
	Amount          float64 `json:"amount"`
	SignatureBase64 string  `json:"signatureBase64"`
}

type PaymentResponse struct {
	Status  string `json:"status"`
	Message string `json:"message"`
}

var (
	dbClient            *dynamodb.Client
	deviceKeysTableName string
	logger              *slog.Logger
)

func init() {
	logLevel := slog.LevelInfo
	if os.Getenv("LOG_LEVEL") == "DEBUG" {
		logLevel = slog.LevelDebug
	}

	logger = slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: logLevel,
	})).With("service", "payment-verifier")

	ctx := context.Background()

	deviceKeysTableName = os.Getenv("DEVICE_KEYS_TABLE_NAME")
	if deviceKeysTableName == "" {
		deviceKeysTableName = "device-keys-table"
		logger.Warn("DEVICE_KEYS_TABLE_NAME not set; using default", "table", deviceKeysTableName)
	}

	cfg, err := config.LoadDefaultConfig(ctx)
	if err != nil {
		logger.Error("unable to load AWS SDK config", "error", err)
		panic("unable to load SDK config: " + err.Error())
	}

	dbClient = dynamodb.NewFromConfig(cfg)
	logger.Info("initialized payment verifier", "table", deviceKeysTableName)
}

func requestLogger(ctx context.Context, req events.APIGatewayProxyRequest) *slog.Logger {
	reqID := req.RequestContext.RequestID
	if reqID == "" {
		if lc, ok := lambdacontext.FromContext(ctx); ok && lc.AwsRequestID != "" {
			reqID = lc.AwsRequestID
		}
	}
	if reqID != "" {
		return logger.With("request_id", reqID)
	}
	return logger
}

func handler(ctx context.Context, request events.APIGatewayProxyRequest) (events.APIGatewayProxyResponse, error) {
	log := requestLogger(ctx, request)

	var body PaymentRequest
	if err := json.Unmarshal([]byte(request.Body), &body); err != nil {
		log.Warn("failed to unmarshal request body", "error", err)
		return errorResponse(400, "Invalid request body"), nil
	}

	log = log.With(
		"user_id", body.UserID,
		"device_id", body.DeviceID,
		"recipient", body.Recipient,
		"amount", body.Amount,
	)
	log.Debug("processing payment request")

	if body.DeviceID == "" {
		log.Warn("missing deviceId in payment request")
		return errorResponse(400, "Device not registered or attested"), nil
	}

	out, err := dbClient.GetItem(ctx, &dynamodb.GetItemInput{
		TableName: &deviceKeysTableName,
		Key: map[string]types.AttributeValue{
			"userId":   &types.AttributeValueMemberS{Value: body.UserID},
			"deviceId": &types.AttributeValueMemberS{Value: body.DeviceID},
		},
	})
	if err != nil {
		log.Error("failed to fetch device key from DynamoDB", "error", err)
		return errorResponse(500, "Internal server error"), nil
	}
	if out.Item == nil {
		log.Warn("device not found in keys table")
		return errorResponse(400, "Device not registered or attested"), nil
	}

	pubKeyAttr, ok := out.Item["publicKey"].(*types.AttributeValueMemberS)
	if !ok || pubKeyAttr.Value == "" {
		log.Error("device record missing string publicKey attribute")
		return errorResponse(500, "Device key configuration invalid"), nil
	}

	pubKeyBytes, err := base64.StdEncoding.DecodeString(pubKeyAttr.Value)
	if err != nil {
		log.Error("failed to base64-decode stored public key", "error", err)
		return errorResponse(400, "Failed to decode public key"), nil
	}

	pubKey, err := x509.ParsePKIXPublicKey(pubKeyBytes)
	if err != nil {
		log.Error("failed to parse PKIX public key", "error", err)
		return errorResponse(400, "Failed to parse public key"), nil
	}

	ecdsaPubKey, ok := pubKey.(*ecdsa.PublicKey)
	if !ok {
		log.Warn("stored public key is not ECDSA", "key_type", fmt.Sprintf("%T", pubKey))
		return errorResponse(400, "Not an ECDSA public key"), nil
	}

	signatureBytes, err := base64.StdEncoding.DecodeString(body.SignatureBase64)
	if err != nil {
		log.Warn("failed to base64-decode signature", "error", err)
		return errorResponse(400, "Failed to decode signature"), nil
	}

	payload := []byte(fmt.Sprintf("%s:%f", body.Recipient, body.Amount))
	hash := sha256.Sum256(payload)

	if !ecdsa.VerifyASN1(ecdsaPubKey, hash[:], signatureBytes) {
		log.Warn("ECDSA signature verification failed")
		return errorResponse(400, "Signature verification failed"), nil
	}

	log.Info("payment signature verified and processed")
	return successResponse(200, "Payment processed"), nil
}

func errorResponse(statusCode int, message string) events.APIGatewayProxyResponse {
	resp := PaymentResponse{Status: "error", Message: message}
	respBody, err := json.Marshal(resp)
	if err != nil {
		logger.Error("failed to marshal error response", "error", err)
	}
	return events.APIGatewayProxyResponse{
		StatusCode: statusCode,
		Headers:    map[string]string{"Content-Type": "application/json"},
		Body:       string(respBody),
	}
}

func successResponse(statusCode int, message string) events.APIGatewayProxyResponse {
	resp := PaymentResponse{Status: "success", Message: message}
	respBody, err := json.Marshal(resp)
	if err != nil {
		logger.Error("failed to marshal success response", "error", err)
	}
	return events.APIGatewayProxyResponse{
		StatusCode: statusCode,
		Headers:    map[string]string{"Content-Type": "application/json"},
		Body:       string(respBody),
	}
}

func main() {
	lambda.Start(handler)
}