import * as path from 'path';
import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as apigw from 'aws-cdk-lib/aws-apigatewayv2';
import * as integrations from 'aws-cdk-lib/aws-apigatewayv2-integrations';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import { GoFunction } from '@aws-cdk/aws-lambda-go-alpha';
import { Construct } from 'constructs';

interface ChallengeServiceProps {
    api: apigw.HttpApi;
    nonceTable: dynamodb.ITable;
}

export class ChallengeService extends Construct {
    constructor(scope: Construct, id: string, props: ChallengeServiceProps) {
        super(scope, id);

        const challengeLambda = new GoFunction(this, 'ChallengeHandler', {
            entry: path.join(__dirname, '../../../attestation-challenge-lambda'),
            architecture: lambda.Architecture.ARM_64,
            timeout: cdk.Duration.seconds(5),
            environment: {
                NONCE_TABLE_NAME: props.nonceTable.tableName
            },
        });

        props.nonceTable.grantWriteData(challengeLambda);

        props.api.addRoutes({
            path: '/attestation/challenge',
            methods: [apigw.HttpMethod.POST],
            integration: new integrations.HttpLambdaIntegration('ChallengeIntegration', challengeLambda)
        })
    }
}