import { App } from 'aws-cdk-lib';
import { Match, Template } from 'aws-cdk-lib/assertions';
import { beforeAll, describe, expect, it } from 'vitest';
import { resolveConfig } from '../lib/config.js';
import { ListenSpeakStack } from '../lib/listenspeak-stack.js';

function synth(context: Record<string, string> = {}) {
  const app = new App({
    context: { project: 'listenspeak', env: 'dev', ...context },
  });
  const stack = new ListenSpeakStack(app, 'test-stack', {
    config: resolveConfig(app),
  });
  return Template.fromStack(stack);
}

describe('ListenSpeakStack', () => {
  let template: Template;

  beforeAll(() => {
    template = synth();
  });

  // --- data --------------------------------------------------------------

  it('creates an on-demand DynamoDB table with TTL enabled', () => {
    template.hasResourceProperties('AWS::DynamoDB::GlobalTable', {
      BillingMode: 'PAY_PER_REQUEST',
      TimeToLiveSpecification: { AttributeName: 'expiresAt', Enabled: true },
    });
  });

  it('blocks all public access on every bucket', () => {
    const buckets = template.findResources('AWS::S3::Bucket');
    expect(Object.keys(buckets).length).toBeGreaterThanOrEqual(2);

    for (const bucket of Object.values(buckets)) {
      expect((bucket as { Properties: Record<string, unknown> }).Properties).toHaveProperty(
        'PublicAccessBlockConfiguration',
        {
          BlockPublicAcls: true,
          BlockPublicPolicy: true,
          IgnorePublicAcls: true,
          RestrictPublicBuckets: true,
        },
      );
    }
  });

  it('encrypts every bucket at rest', () => {
    for (const bucket of Object.values(template.findResources('AWS::S3::Bucket'))) {
      const properties = (bucket as { Properties: Record<string, unknown> }).Properties;
      expect(properties.BucketEncryption).toBeDefined();
    }
  });

  it('expires temporary speaking uploads after one day', () => {
    const buckets = template.findResources('AWS::S3::Bucket', {
      Properties: { LifecycleConfiguration: Match.anyValue() },
    });
    const rules = Object.values(buckets).flatMap(
      (bucket) =>
        (
          bucket as {
            Properties: {
              LifecycleConfiguration: {
                Rules: Array<{ Id: string; ExpirationInDays: number }>;
              };
            };
          }
        ).Properties.LifecycleConfiguration.Rules,
    );

    expect(
      rules.find((rule) => rule.Id === 'expire-temporary-speaking-uploads')?.ExpirationInDays,
    ).toBe(1);
  });

  // --- network -----------------------------------------------------------

  it('creates no NAT gateway, because it would dominate the monthly bill', () => {
    template.resourceCountIs('AWS::EC2::NatGateway', 0);
  });

  it('lets only the load balancer reach the container', () => {
    template.hasResourceProperties('AWS::EC2::SecurityGroupIngress', {
      FromPort: 8080,
      ToPort: 8080,
      SourceSecurityGroupId: Match.anyValue(),
    });
  });

  it('does not expose the container port to the internet', () => {
    const ingress = template.findResources('AWS::EC2::SecurityGroupIngress');
    const openToWorld = Object.values(ingress).filter(
      (rule) => (rule as { Properties: { CidrIp?: string } }).Properties.CidrIp === '0.0.0.0/0',
    );

    // Only the load balancer's own port 80 may be open to the world.
    for (const rule of openToWorld) {
      expect((rule as { Properties: { FromPort: number } }).Properties.FromPort).toBe(80);
    }
  });

  // --- auth --------------------------------------------------------------

  it('disables Cognito self sign-up, which is what keeps this app private', () => {
    template.hasResourceProperties('AWS::Cognito::UserPool', {
      AdminCreateUserConfig: { AllowAdminCreateUserOnly: true },
    });
  });

  it('requires a long password', () => {
    template.hasResourceProperties('AWS::Cognito::UserPool', {
      Policies: {
        PasswordPolicy: Match.objectLike({
          MinimumLength: 12,
          RequireNumbers: true,
          RequireSymbols: true,
          RequireUppercase: true,
        }),
      },
    });
  });

  it('uses the authorization code flow only, with no client secret', () => {
    template.hasResourceProperties('AWS::Cognito::UserPoolClient', {
      AllowedOAuthFlows: ['code'],
      GenerateSecret: false,
    });
  });

  // --- service -----------------------------------------------------------

  it('runs exactly one small Fargate task', () => {
    template.hasResourceProperties('AWS::ECS::Service', { DesiredCount: 1 });
    template.hasResourceProperties('AWS::ECS::TaskDefinition', {
      Cpu: '512',
      Memory: '1024',
    });
  });

  it('injects the OpenAI key from Secrets Manager, never as plain text', () => {
    const taskDefinitions = template.findResources('AWS::ECS::TaskDefinition');
    const container = Object.values(taskDefinitions)[0] as {
      Properties: {
        ContainerDefinitions: Array<{
          Secrets?: Array<{ Name: string }>;
          Environment?: Array<{ Name: string; Value: string }>;
        }>;
      };
    };
    const definition = container.Properties.ContainerDefinitions[0]!;

    expect(definition.Secrets?.map((secret) => secret.Name)).toContain('OPENAI_API_KEY');
    expect(definition.Environment?.map((entry) => entry.Name)).not.toContain('OPENAI_API_KEY');
  });

  it('tells the container to use live content, Cognito auth, and AWS storage', () => {
    const taskDefinitions = template.findResources('AWS::ECS::TaskDefinition');
    const environment = (
      Object.values(taskDefinitions)[0] as {
        Properties: {
          ContainerDefinitions: Array<{
            Environment: Array<{ Name: string; Value: string }>;
          }>;
        };
      }
    ).Properties.ContainerDefinitions[0]!.Environment;

    const byName = Object.fromEntries(environment.map((entry) => [entry.Name, entry.Value]));
    expect(byName.APP_CONTENT_MODE).toBe('LIVE');
    expect(byName.APP_AUTH_MODE).toBe('COGNITO');
    expect(byName.APP_STORAGE_MODE).toBe('AWS');
  });

  /**
   * The only permitted wildcard resource in the whole stack.
   * `ecr:GetAuthorizationToken` does not support resource-level permissions -
   * it is an account-scoped token request - so AWS requires "*". Every other
   * grant names its table, bucket, secret, or distribution. Justified in
   * docs/security.md.
   */
  const ALLOWED_WILDCARD_ACTIONS = ['ecr:GetAuthorizationToken'];

  it('grants no wildcard resource except the one AWS requires', () => {
    const offenders: string[] = [];

    for (const [name, policy] of Object.entries(template.findResources('AWS::IAM::Policy'))) {
      const statements = (
        policy as {
          Properties: {
            PolicyDocument: {
              Statement: Array<{ Resource?: unknown; Action?: unknown }>;
            };
          };
        }
      ).Properties.PolicyDocument.Statement;

      for (const statement of statements) {
        if (statement.Resource !== '*') continue;

        const actions = [statement.Action].flat().filter(Boolean) as string[];
        const unjustified = actions.filter((action) => !ALLOWED_WILDCARD_ACTIONS.includes(action));
        if (unjustified.length > 0) {
          offenders.push(`${name}: ${unjustified.join(', ')}`);
        }
      }
    }

    expect(offenders).toEqual([]);
  });

  it('grants no wildcard action anywhere', () => {
    for (const policy of Object.values(template.findResources('AWS::IAM::Policy'))) {
      const statements = (
        policy as {
          Properties: {
            PolicyDocument: { Statement: Array<{ Action?: unknown }> };
          };
        }
      ).Properties.PolicyDocument.Statement;

      for (const statement of statements) {
        expect([statement.Action].flat()).not.toContain('*');
      }
    }
  });

  it('keeps log retention short so CloudWatch ingestion stays cheap', () => {
    template.hasResourceProperties('AWS::Logs::LogGroup', {
      RetentionInDays: 7,
    });
  });

  it('health-checks the actuator endpoint', () => {
    template.hasResourceProperties('AWS::ElasticLoadBalancingV2::TargetGroup', {
      HealthCheckPath: '/actuator/health',
    });
  });

  // --- web ---------------------------------------------------------------

  it('serves the API through CloudFront so the browser is always on HTTPS', () => {
    template.hasResourceProperties('AWS::CloudFront::Distribution', {
      DistributionConfig: Match.objectLike({
        CacheBehaviors: Match.arrayWith([
          Match.objectLike({
            PathPattern: '/api/*',
            ViewerProtocolPolicy: 'redirect-to-https',
          }),
        ]),
      }),
    });
  });

  it('does not cache API responses', () => {
    const distributions = template.findResources('AWS::CloudFront::Distribution');
    const behaviours = (
      Object.values(distributions)[0] as {
        Properties: {
          DistributionConfig: {
            CacheBehaviors: Array<{
              PathPattern: string;
              CachePolicyId: string;
            }>;
          };
        };
      }
    ).Properties.DistributionConfig.CacheBehaviors;

    const api = behaviours.find((behaviour) => behaviour.PathPattern === '/api/*');
    // The AWS managed CachingDisabled policy.
    expect(api?.CachePolicyId).toBe('4135ea2d-6df8-44a3-9df3-4b5a84be39ad');
  });

  it('reaches the site bucket through Origin Access Control, not a public policy', () => {
    template.resourceCountIs('AWS::CloudFront::OriginAccessControl', 1);
  });

  it('rewrites unknown paths to index.html for client-side routing', () => {
    template.hasResourceProperties('AWS::CloudFront::Distribution', {
      DistributionConfig: Match.objectLike({
        CustomErrorResponses: Match.arrayWith([
          Match.objectLike({
            ErrorCode: 404,
            ResponseCode: 200,
            ResponsePagePath: '/index.html',
          }),
        ]),
      }),
    });
  });

  // --- budget ------------------------------------------------------------

  it('creates no budget without an email to send it to', () => {
    template.resourceCountIs('AWS::Budgets::Budget', 0);
  });

  it('creates a forecast and an actual alert when an email is configured', () => {
    const withEmail = synth({
      budgetEmail: 'someone@example.com',
      monthlyBudgetUsd: '30',
    });

    withEmail.hasResourceProperties('AWS::Budgets::Budget', {
      Budget: Match.objectLike({
        BudgetType: 'COST',
        TimeUnit: 'MONTHLY',
        BudgetLimit: { Amount: 30, Unit: 'USD' },
      }),
      NotificationsWithSubscribers: Match.arrayWith([
        Match.objectLike({
          Notification: Match.objectLike({ NotificationType: 'FORECASTED' }),
        }),
        Match.objectLike({
          Notification: Match.objectLike({ NotificationType: 'ACTUAL' }),
        }),
      ]),
    });
  });

  // --- tagging -----------------------------------------------------------

  it('is destroyable in dev so cleanup actually works', () => {
    const buckets = template.findResources('AWS::S3::Bucket');
    for (const bucket of Object.values(buckets)) {
      expect((bucket as { DeletionPolicy: string }).DeletionPolicy).toBe('Delete');
    }
  });
});
