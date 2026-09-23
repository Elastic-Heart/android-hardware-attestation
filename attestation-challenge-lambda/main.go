package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"os"
	"strconv"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

type ChallengeRequest struct {
	UserID string `json:"userId"`
}

type ChallengeResponse struct {
	Nonce string `json:"nonce"`
}

var dbClient *dynamodb.Client
var tableName string

func init() {

	ctx := context.Background()

	tableName = os.Getenv("NONCE_TABLE_NAME")
	cfg, err := config.LoadDefaultConfig(ctx)

	if err != nil {
		panic("unable to load SDK config: " + err.Error())
	}

	dbClient = dynamodb.NewFromConfig(cfg)
}

func generateNonce(length int) (string, error) {
	bytes := make([]byte, length)

	if _, err := rand.Read(bytes); err != nil {
		return "", err
	}

	return hex.EncodeToString(bytes), nil
}

func handler(ctx context.Context, request events.APIGatewayProxyRequest) (events.APIGatewayProxyResponse, error) {
	var body ChallengeRequest

	if err := json.Unmarshal([]byte(request.Body), &body); err != nil || body.UserID == "" {
		return events.APIGatewayProxyResponse{
			StatusCode: 400,
			Body:       `{"error": "Invalid userId"}`,
		}, nil
	}

	nonce, err := generateNonce(32)

	if err != nil {
		return events.APIGatewayProxyResponse{
			StatusCode: 500,
			Body:       `{"error": "Failed to generate nonce"}`,
		}, nil
	}

	ttl := time.Now().Add(5 * time.Minute).Unix()

	_, err = dbClient.PutItem(ctx, &dynamodb.PutItemInput{
		TableName: aws.String(tableName),
		Item: map[string]types.AttributeValue{
			"nonce":     &types.AttributeValueMemberS{Value: nonce},
			"userId":    &types.AttributeValueMemberS{Value: body.UserID},
			"expiresAt": &types.AttributeValueMemberN{Value: strconv.FormatInt(ttl, 10)},
		},
	})

	if err != nil {
		return events.APIGatewayProxyResponse{
			StatusCode: 500,
			Body:       `{"error": "Failed to save nonce to DynamoDB"}`,
		}, nil
	}

	respBody, _ := json.Marshal(ChallengeResponse{Nonce: nonce})

	return events.APIGatewayProxyResponse{
		StatusCode: 200,
		Headers:    map[string]string{"Content-Type": "application/json"},
		Body:       string(respBody),
	}, nil
}

func main() {
	lambda.Start(handler)
}
