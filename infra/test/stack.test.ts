import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { describe, expect, it } from 'vitest';
import { resolveConfig } from '../lib/config.js';
import { ListenSpeakStack } from '../lib/listenspeak-stack.js';

function synth(context: Record<string, string> = {}) {
  const app = new App({ context: { project: 'listenspeak', env: 'dev', ...context } });
  const stack = new ListenSpeakStack(app, 'test-stack', {
    config: { ...resolveConfig(app), account: '111111111111', region: 'ca-central-1' },
  });
  return Template.fromStack(stack);
}

describe('ListenSpeakStack', () => {
  it('creates an on-demand DynamoDB table with TTL enabled', () => {
    synth().hasResourceProperties('AWS::DynamoDB::GlobalTable', {
      BillingMode: 'PAY_PER_REQUEST',
      TimeToLiveSpecification: { AttributeName: 'expiresAt', Enabled: true },
    });
  });

  it('blocks all public access on the audio bucket', () => {
    synth().hasResourceProperties('AWS::S3::Bucket', {
      PublicAccessBlockConfiguration: {
        BlockPublicAcls: true,
        BlockPublicPolicy: true,
        IgnorePublicAcls: true,
        RestrictPublicBuckets: true,
      },
    });
  });

  it('encrypts the audio bucket at rest', () => {
    synth().hasResourceProperties('AWS::S3::Bucket', {
      BucketEncryption: {
        ServerSideEncryptionConfiguration: [
          { ServerSideEncryptionByDefault: { SSEAlgorithm: 'AES256' } },
        ],
      },
    });
  });

  it('expires temporary speaking uploads after one day', () => {
    const rules = synth().findResources('AWS::S3::Bucket');
    const bucket = Object.values(rules)[0] as {
      Properties: { LifecycleConfiguration: { Rules: Array<{ Id: string; ExpirationInDays: number }> } };
    };
    const speaking = bucket.Properties.LifecycleConfiguration.Rules.find(
      (r) => r.Id === 'expire-temporary-speaking-uploads',
    );
    expect(speaking?.ExpirationInDays).toBe(1);
  });
});
