import { CfnOutput, Stack, type StackProps } from 'aws-cdk-lib';
import { Construct } from 'constructs';
import { type DeploymentConfig, prefix } from './config.js';
import { Data } from './constructs/data.js';

export interface ListenSpeakStackProps extends StackProps {
  readonly config: DeploymentConfig;
}

/**
 * Single stack for the whole personal app. Splitting into multiple stacks
 * would buy nothing here and would add cross-stack export churn on every
 * redeploy; the pieces are separated as constructs instead.
 */
export class ListenSpeakStack extends Stack {
  readonly data: Data;

  constructor(scope: Construct, id: string, props: ListenSpeakStackProps) {
    super(scope, id, props);

    const { config } = props;
    const namePrefix = prefix(config);
    const isDev = config.envName === 'dev';

    this.data = new Data(this, 'Data', {
      namePrefix,
      destroyOnRemove: isDev,
      audioRetentionDays: isDev ? 7 : 30,
    });

    new CfnOutput(this, 'TableName', {
      value: this.data.table.tableName,
      description: 'DynamoDB single table',
    });
    new CfnOutput(this, 'AudioBucketName', {
      value: this.data.audioBucket.bucketName,
      description: 'Private S3 bucket for generated audio and speaking uploads',
    });
  }
}
