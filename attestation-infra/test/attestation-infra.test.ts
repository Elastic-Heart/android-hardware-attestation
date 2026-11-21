import * as cdk from 'aws-cdk-lib';
import { Match, Template } from 'aws-cdk-lib/assertions';
import * as AttestationInfra from '../lib/attestation-infra-stack';

test('VerifyHandler has expected environment variables for Application ID validation', () => {
    const app = new cdk.App();
    const stack = new AttestationInfra.AttestationInfraStack(app, 'TestStack');
    const template = Template.fromStack(stack);

    template.hasResourceProperties('AWS::Lambda::Function', {
        Handler: 'org.example.VerifyHandler',
        Environment: {
            Variables: Match.objectLike({
                EXPECTED_PACKAGE_NAME: 'com.martini.attestation',
                EXPECTED_SIGNATURE_DIGEST: '3B:A6:65:75:04:29:36:2C:4C:FC:D6:50:5E:FD:BE:0F:BE:1A:F8:6D:58:3F:2D:D5:52:62:24:C2:AD:DD:D7:73',
            })
        }
    });
});
