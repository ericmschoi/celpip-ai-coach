import { CfnOutput, Stack, type StackProps } from 'aws-cdk-lib';
import * as logs from 'aws-cdk-lib/aws-logs';
import { Construct } from 'constructs';
import { type DeploymentConfig, prefix } from './config.js';
import { Auth } from './constructs/auth.js';
import { CostAlarm } from './constructs/budget.js';
import { Data } from './constructs/data.js';
import { Network } from './constructs/network.js';
import { ApiService } from './constructs/service.js';
import { Web } from './constructs/web.js';

export interface ListenSpeakStackProps extends StackProps {
  readonly config: DeploymentConfig;
}

/**
 * Single stack for the whole personal app. Splitting into multiple stacks would
 * buy nothing here and would add cross-stack export churn on every redeploy;
 * the pieces are separated as constructs instead.
 *
 * <p>Construction order matters and is not arbitrary: the load balancer must
 * exist before CloudFront can use it as an origin, CloudFront must exist before
 * Cognito can whitelist its callback URL, and the service needs the Cognito ids
 * for its environment. Network → Data → Web → Auth → ApiService is the order
 * that has no cycle.
 */
export class ListenSpeakStack extends Stack {
  readonly data: Data;

  constructor(scope: Construct, id: string, props: ListenSpeakStackProps) {
    super(scope, id, props);

    const { config } = props;
    const namePrefix = prefix(config);
    const isDev = config.envName === 'dev';

    const network = new Network(this, 'Network', { namePrefix });

    this.data = new Data(this, 'Data', {
      namePrefix,
      destroyOnRemove: isDev,
      audioRetentionDays: isDev ? 7 : 30,
    });

    const web = new Web(this, 'Web', {
      namePrefix,
      destroyOnRemove: isDev,
      loadBalancer: network.loadBalancer,
      bundlePath: '../frontend/dist',
    });

    const siteUrl = `https://${web.distribution.distributionDomainName}`;

    const auth = new Auth(this, 'Auth', {
      namePrefix,
      destroyOnRemove: isDev,
      // Localhost stays allowed in dev so the hosted UI can be used while
      // developing against the deployed pool. Production allows only the site.
      callbackUrls: isDev
        ? [`${siteUrl}/auth/callback`, 'http://localhost:5173/auth/callback']
        : [`${siteUrl}/auth/callback`],
      logoutUrls: isDev ? [siteUrl, 'http://localhost:5173'] : [siteUrl],
    });

    new ApiService(this, 'Api', {
      namePrefix,
      envName: config.envName,
      destroyOnRemove: isDev,
      network,
      auth,
      table: this.data.table,
      audioBucket: this.data.audioBucket,
      // Short retention keeps CloudWatch ingestion off the monthly bill.
      logRetentionDays: isDev ? logs.RetentionDays.ONE_WEEK : logs.RetentionDays.ONE_MONTH,
      listeningPerDay: 20,
      speakingPerDay: 30,
    });

    new CostAlarm(this, 'Budget', {
      namePrefix,
      monthlyBudgetUsd: config.monthlyBudgetUsd,
      notifyEmail: config.budgetEmail,
    });

    new CfnOutput(this, 'TableName', {
      value: this.data.table.tableName,
      description: 'DynamoDB single table',
    });
    new CfnOutput(this, 'AudioBucketName', {
      value: this.data.audioBucket.bucketName,
      description: 'Private S3 bucket for generated audio and speaking uploads',
    });
    new CfnOutput(this, 'Region', { value: this.region });
  }
}
