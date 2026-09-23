import * as cdk from 'aws-cdk-lib';
import * as apigw from 'aws-cdk-lib/aws-apigatewayv2';
import { Construct } from 'constructs';
import { AttestationDatabase } from './constructs/database';
import { ChallengeService } from './constructs/challenge-service';
import { VerificationService } from './constructs/verification-service';

export class AttestationInfraStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const httpApi = new apigw.HttpApi(this, 'AttestationApi', {
      apiName: 'HardwareAttestationAPi'
    })

    const db = new AttestationDatabase(this, 'Database');

    new ChallengeService(this, 'ChallengeEndpoint', {
      api: httpApi,
      nonceTable: db.nonceTable
    });

    new VerificationService(this, 'VerificationEndpoint', {
      api: httpApi,
      nonceTable: db.nonceTable,
      deviceKeysTable: db.deviceKeysTable,
    });

    new cdk.CfnOutput(this, 'ApiUrl', {
      value: httpApi.url!,
      description: 'HTTP API Gateway base endpoint URL'
    });
  }
}
