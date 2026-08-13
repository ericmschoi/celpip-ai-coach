import { Duration, RemovalPolicy, Stack } from 'aws-cdk-lib';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as s3 from 'aws-cdk-lib/aws-s3';
import { Construct } from 'constructs';

export interface DataProps {
  readonly namePrefix: string;
  /** `true` for dev so `cdk destroy` actually cleans up. */
  readonly destroyOnRemove: boolean;
  /** Days to keep generated listening audio before lifecycle expiry. */
  readonly audioRetentionDays: number;
}

/**
 * Persistent state for the app: one DynamoDB table (single-table design) and
 * one private S3 bucket for generated listening audio plus temporary speaking
 * uploads.
 *
 * Key design (see docs/architecture.md):
 *   PK = USER#{cognitoSub}
 *   SK = EXERCISE#{exerciseId}
 *      | LISTENING_ATTEMPT#{createdAt}#{attemptId}
 *      | SPEAKING_EVALUATION#{createdAt}#{evaluationId}
 *      | USAGE#{yyyy-mm-dd}
 */
export class Data extends Construct {
  readonly table: dynamodb.TableV2;
  readonly audioBucket: s3.Bucket;

  constructor(scope: Construct, id: string, props: DataProps) {
    super(scope, id);

    const removalPolicy = props.destroyOnRemove ? RemovalPolicy.DESTROY : RemovalPolicy.RETAIN;

    this.table = new dynamodb.TableV2(this, 'Table', {
      tableName: `${props.namePrefix}-app`,
      partitionKey: { name: 'pk', type: dynamodb.AttributeType.STRING },
      sortKey: { name: 'sk', type: dynamodb.AttributeType.STRING },
      // On-demand: a personal app has bursty, near-zero baseline traffic.
      billing: dynamodb.Billing.onDemand(),
      // AWS-owned key: encrypted at rest with no monthly KMS key charge.
      encryption: dynamodb.TableEncryptionV2.dynamoOwnedKey(),
      timeToLiveAttribute: 'expiresAt',
      pointInTimeRecoverySpecification: {
        pointInTimeRecoveryEnabled: !props.destroyOnRemove,
      },
      removalPolicy,
    });

    this.audioBucket = new s3.Bucket(this, 'AudioBucket', {
      // S3 bucket names are globally unique; the account id keeps it collision-free.
      bucketName: `${props.namePrefix}-audio-${Stack.of(this).account}`,
      encryption: s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      enforceSSL: true,
      versioned: false,
      removalPolicy,
      autoDeleteObjects: props.destroyOnRemove,
      lifecycleRules: [
        {
          id: 'expire-generated-listening-audio',
          prefix: 'listening/',
          expiration: Duration.days(props.audioRetentionDays),
          abortIncompleteMultipartUploadAfter: Duration.days(1),
        },
        {
          id: 'expire-temporary-speaking-uploads',
          prefix: 'speaking/',
          expiration: Duration.days(1),
          abortIncompleteMultipartUploadAfter: Duration.days(1),
        },
      ],
    });
  }
}
