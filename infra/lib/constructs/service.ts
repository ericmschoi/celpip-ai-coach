import { CfnOutput, Duration, RemovalPolicy, Stack } from 'aws-cdk-lib';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';
import type { Auth } from './auth.js';
import type { Network } from './network.js';

export interface ApiServiceProps {
  readonly namePrefix: string;
  readonly envName: string;
  readonly destroyOnRemove: boolean;
  readonly network: Network;
  readonly auth: Auth;
  readonly table: dynamodb.TableV2;
  readonly audioBucket: s3.Bucket;
  readonly logRetentionDays: logs.RetentionDays;
  /** Daily caps, passed through to the application. */
  readonly listeningPerDay: number;
  readonly speakingPerDay: number;
}

/**
 * The Spring Boot API on ECS Fargate behind an Application Load Balancer.
 *
 * <p>One task, the smallest size that comfortably runs a JVM plus FFmpeg. The
 * task role is scoped to exactly one table, one bucket, and one secret.
 */
export class ApiService extends Construct {
  readonly openAiSecret: secretsmanager.Secret;
  readonly service: ecs.FargateService;

  constructor(scope: Construct, id: string, props: ApiServiceProps) {
    super(scope, id);

    // Created empty. The value is placed by the operator with the AWS CLI, so
    // the key never appears in a template, a repository, or a CI log.
    this.openAiSecret = new secretsmanager.Secret(this, 'OpenAiApiKey', {
      secretName: `${props.namePrefix}/openai-api-key`,
      description: 'OpenAI API key. Set with: aws secretsmanager put-secret-value',
      removalPolicy: props.destroyOnRemove ? RemovalPolicy.DESTROY : RemovalPolicy.RETAIN,
    });

    const logGroup = new logs.LogGroup(this, 'Logs', {
      logGroupName: `/listenspeak/${props.envName}/api`,
      retention: props.logRetentionDays,
      removalPolicy: RemovalPolicy.DESTROY,
    });

    const cluster = new ecs.Cluster(this, 'Cluster', {
      clusterName: `${props.namePrefix}-cluster`,
      vpc: props.network.vpc,
      // Container Insights bills per metric; this app has one task.
      containerInsightsV2: ecs.ContainerInsights.DISABLED,
    });

    const taskDefinition = new ecs.FargateTaskDefinition(this, 'TaskDefinition', {
      cpu: 512,
      // 1 GB: the JVM plus an FFmpeg process assembling a few minutes of audio.
      memoryLimitMiB: 1024,
      runtimePlatform: {
        cpuArchitecture: ecs.CpuArchitecture.X86_64,
        operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
      },
    });

    taskDefinition.addContainer('api', {
      // Built and pushed to ECR at deploy time; the image includes FFmpeg.
      image: ecs.ContainerImage.fromAsset('../backend', {
        platform: undefined,
      }),
      containerName: 'api',
      logging: ecs.LogDrivers.awsLogs({ streamPrefix: 'api', logGroup }),
      portMappings: [{ containerPort: 8080, protocol: ecs.Protocol.TCP }],
      environment: {
        SPRING_PROFILES_ACTIVE: 'aws',
        APP_CONTENT_MODE: 'LIVE',
        APP_AUTH_MODE: 'COGNITO',
        APP_STORAGE_MODE: 'AWS',
        APP_COGNITO_ISSUER_URI: `https://cognito-idp.${Stack.of(this).region}.amazonaws.com/${props.auth.userPool.userPoolId}`,
        APP_COGNITO_USER_POOL_ID: props.auth.userPool.userPoolId,
        APP_COGNITO_CLIENT_ID: props.auth.client.userPoolClientId,
        APP_DYNAMODB_TABLE: props.table.tableName,
        APP_AUDIO_BUCKET: props.audioBucket.bucketName,
        APP_LIMITS_LISTENING_PER_DAY: String(props.listeningPerDay),
        APP_LIMITS_SPEAKING_PER_DAY: String(props.speakingPerDay),
        AWS_REGION: Stack.of(this).region,
      },
      secrets: {
        // Injected by ECS at start-up; never written to a file or a log.
        OPENAI_API_KEY: ecs.Secret.fromSecretsManager(this.openAiSecret),
      },
      healthCheck: {
        command: ['CMD-SHELL', 'curl -fsS http://localhost:8080/actuator/health || exit 1'],
        interval: Duration.seconds(30),
        timeout: Duration.seconds(5),
        retries: 3,
        startPeriod: Duration.seconds(90),
      },
    });

    // Least privilege: one table, one bucket, one secret, enumerated actions.
    props.table.grantReadWriteData(taskDefinition.taskRole);
    props.audioBucket.grantReadWrite(taskDefinition.taskRole);
    this.openAiSecret.grantRead(taskDefinition.taskRole);

    taskDefinition.taskRole.addToPrincipalPolicy(
      new iam.PolicyStatement({
        sid: 'PresignAudioObjects',
        actions: ['s3:GetObject'],
        resources: [props.audioBucket.arnForObjects('*')],
      }),
    );

    this.service = new ecs.FargateService(this, 'Service', {
      serviceName: `${props.namePrefix}-api`,
      cluster,
      taskDefinition,
      desiredCount: 1,
      // Public subnet with a public IP is what replaces a NAT Gateway; the
      // security group is what keeps the task unreachable from outside.
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      assignPublicIp: true,
      securityGroups: [props.network.serviceSecurityGroup],
      minHealthyPercent: 0,
      maxHealthyPercent: 200,
      circuitBreaker: { rollback: true },
      enableExecuteCommand: false,
    });

    // The load balancer itself lives in Network so CloudFront and Cognito can
    // be created before the service; this only attaches the target group.
    props.network.listener.addTargets('Api', {
      priority: 1,
      conditions: [elbv2.ListenerCondition.pathPatterns(['/*'])],
      port: 8080,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targets: [this.service],
      deregistrationDelay: Duration.seconds(15),
      healthCheck: {
        path: '/actuator/health',
        interval: Duration.seconds(30),
        timeout: Duration.seconds(5),
        healthyThresholdCount: 2,
        unhealthyThresholdCount: 3,
      },
    });

    new CfnOutput(this, 'ApiUrl', {
      value: `http://${props.network.loadBalancer.loadBalancerDnsName}`,
      description: 'Load balancer origin. Browsers reach it through CloudFront over HTTPS.',
    });
    new CfnOutput(this, 'OpenAiSecretName', {
      value: this.openAiSecret.secretName,
      description: 'Put the OpenAI key here; see docs/aws-runbook.md',
    });
  }
}
