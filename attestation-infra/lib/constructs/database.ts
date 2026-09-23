import { Construct } from "constructs";
import * as cdk from 'aws-cdk-lib';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';

export class AttestationDatabase extends Construct {
    public readonly nonceTable: dynamodb.ITable;
    public readonly deviceKeysTable: dynamodb.ITable;

    constructor(scope: Construct, id: string) {
        super(scope, id);

        this.nonceTable = new dynamodb.Table(this, 'AttestationNonces', {
            partitionKey: { name: 'nonce', type: dynamodb.AttributeType.STRING },
            timeToLiveAttribute: 'expiresAt',
            removalPolicy: cdk.RemovalPolicy.DESTROY,
            billingMode: dynamodb.BillingMode.PAY_PER_REQUEST
        });

        this.deviceKeysTable = new dynamodb.Table(this, 'DeviceKeys', {
            partitionKey: { name: 'userId', type: dynamodb.AttributeType.STRING },
            sortKey: { name: 'deviceId', type: dynamodb.AttributeType.STRING },
            removalPolicy: cdk.RemovalPolicy.DESTROY,
            billingMode: dynamodb.BillingMode.PAY_PER_REQUEST
        });
    }
}