package main

import (
	"context"
	"crypto/ecdsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
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

var dbClient *dynamodb.Client
var deviceKeysTableName string

func init() {
	ctx := context.Background()

	deviceKeysTableName = os.Getenv("DEVICE_KEYS_TABLE_NAME")
	if deviceKeysTableName == "" {
		deviceKeysTableName = "device-keys-table"
	}
	
	cfg, err := config.LoadDefaultConfig(ctx)
	if err != nil {
		panic("unable to load SDK config: " + err.Error())
	}

	dbClient = dynamodb.NewFromConfig(cfg)
}

func handler(ctx context.Context, request events.APIGatewayProxyRequest) (events.APIGatewayProxyResponse, error) {
	var body PaymentRequest

	if err := json.Unmarshal([]byte(request.Body), &body); err != nil {
		return errorResponse(400, "Invalid request body"), nil
	}

	out, err := dbClient.GetItem(ctx, &dynamodb.GetItemInput{
		TableName: &deviceKeysTableName,
		Key: map[string]types.AttributeValue{
			"deviceId": &types.AttributeValueMemberS{Value: body.DeviceID},
		},
	})
	if err != nil || out.Item == nil {
		return errorResponse(400, "Device not registered or attested"), nil
	}

	pubKeyBase64 := out.Item["publicKey"].(*types.AttributeValueMemberS).Value
	pubKeyBytes, err := base64.StdEncoding.DecodeString(pubKeyBase64)
	if err != nil {
		return errorResponse(400, "Failed to decode public key"), nil
	}

	pubKey, err := x509.ParsePKIXPublicKey(pubKeyBytes)
	if err != nil {
		return errorResponse(400, "Failed to parse public key"), nil
	}

	ecdsaPubKey, ok := pubKey.(*ecdsa.PublicKey)
	if !ok {
		return errorResponse(400, "Not an ECDSA public key"), nil
	}

	signatureBytes, err := base64.StdEncoding.DecodeString(body.SignatureBase64)
	if err != nil {
		return errorResponse(400, "Failed to decode signature"), nil
	}

	payload := []byte(fmt.Sprintf("%s:%f", body.Recipient, body.Amount))
	hash := sha256.Sum256(payload)

	valid := ecdsa.VerifyASN1(ecdsaPubKey, hash[:], signatureBytes)
	if !valid {
		return errorResponse(400, "Signature verification failed"), nil
	}

	return successResponse(200, "Payment processed"), nil
}

func errorResponse(statusCode int, message string) events.APIGatewayProxyResponse {
	resp := PaymentResponse{Status: "error", Message: message}
	respBody, _ := json.Marshal(resp)
	return events.APIGatewayProxyResponse{
		StatusCode: statusCode,
		Headers:    map[string]string{"Content-Type": "application/json"},
		Body:       string(respBody),
	}
}

func successResponse(statusCode int, message string) events.APIGatewayProxyResponse {
	resp := PaymentResponse{Status: "success", Message: message}
	respBody, _ := json.Marshal(resp)
	return events.APIGatewayProxyResponse{
		StatusCode: statusCode,
		Headers:    map[string]string{"Content-Type": "application/json"},
		Body:       string(respBody),
	}
}

func main() {
	lambda.Start(handler)
}
