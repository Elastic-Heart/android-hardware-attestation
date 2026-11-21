import * as path from 'path';
import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as apigw from 'aws-cdk-lib/aws-apigatewayv2';
import * as integrations from 'aws-cdk-lib/aws-apigatewayv2-integrations';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import { GoFunction } from '@aws-cdk/aws-lambda-go-alpha';
import { Construct } from 'constructs';

interface PaymentServiceProps {
    api: apigw.HttpApi;
    deviceKeysTable: dynamodb.ITable;
}

export class PaymentService extends Construct {
    constructor(scope: Construct, id: string, props: PaymentServiceProps) {
        super(scope, id);

        const paymentLambda = new GoFunction(this, 'PaymentHandler', {
            entry: path.join(__dirname, '../../../payment-api-lambda'),
            architecture: lambda.Architecture.ARM_64,
            timeout: cdk.Duration.seconds(5),
            environment: {
                DEVICE_KEYS_TABLE_NAME: props.deviceKeysTable.tableName
            },
        });

        props.deviceKeysTable.grantReadData(paymentLambda);

        props.api.addRoutes({
            path: '/payment',
            methods: [apigw.HttpMethod.POST],
            integration: new integrations.HttpLambdaIntegration('PaymentIntegration', paymentLambda)
        })
    }
}
