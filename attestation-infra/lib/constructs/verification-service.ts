import * as path from 'path';
import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as apigw from 'aws-cdk-lib/aws-apigatewayv2';
import * as integrations from 'aws-cdk-lib/aws-apigatewayv2-integrations';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import { Construct } from 'constructs';

interface VerificationServiceProps {
    api: apigw.HttpApi;
    deviceKeysTable: dynamodb.ITable;
    nonceTable: dynamodb.ITable;
}

export class VerificationService extends Construct {

    public readonly verifyFunction: lambda.Function;

    constructor(scope: Construct, id: string, props: VerificationServiceProps) {
        super(scope, id);

        this.verifyFunction = new lambda.Function(this, 'VerifyHandler', {
            runtime: lambda.Runtime.JAVA_21,
            handler: 'com.example.VerifyHandler',
            code: lambda.Code.fromAsset(
                path.join(__dirname, '../../../certificate-verification-lambda/verifier/build/libs/verifier-1.0-SNAPSHOT.jar')
            ),
            memorySize: 1024,
            timeout: cdk.Duration.seconds(10),
            environment: {
                DEVICE_KEYS_TABLE_NAME: props.deviceKeysTable.tableName,
                NONCE_TABLE_NAME: props.nonceTable.tableName,
            },
        });

        props.deviceKeysTable.grantReadWriteData(this.verifyFunction);
        props.nonceTable.grantReadWriteData(this.verifyFunction);

        const integration = new integrations.HttpLambdaIntegration(
            'VerifyIntegration',
            this.verifyFunction,
        );

        props.api.addRoutes({
            path: '/verify',
            methods: [apigw.HttpMethod.POST],
            integration: integration,
        });
    }
}