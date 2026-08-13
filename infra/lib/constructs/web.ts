import { existsSync } from 'node:fs';
import { CfnOutput, Duration, RemovalPolicy } from 'aws-cdk-lib';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import type * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as s3deploy from 'aws-cdk-lib/aws-s3-deployment';
import { Construct } from 'constructs';

export interface WebProps {
  readonly namePrefix: string;
  readonly destroyOnRemove: boolean;
  readonly loadBalancer: elbv2.ApplicationLoadBalancer;
  /** Path to the built frontend bundle; skipped when it has not been built. */
  readonly bundlePath: string;
}

/**
 * The static site on CloudFront, plus the API behind the same distribution.
 *
 * <p>Routing `/api/*` through CloudFront to the load balancer solves three
 * problems at once: the browser always talks HTTPS even though the ALB has no
 * certificate, the app is same-origin so there is no CORS preflight at all, and
 * the bundle needs no absolute API URL baked into it.
 */
export class Web extends Construct {
  readonly bucket: s3.Bucket;
  readonly distribution: cloudfront.Distribution;

  constructor(scope: Construct, id: string, props: WebProps) {
    super(scope, id);

    this.bucket = new s3.Bucket(this, 'SiteBucket', {
      encryption: s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      enforceSSL: true,
      removalPolicy: props.destroyOnRemove ? RemovalPolicy.DESTROY : RemovalPolicy.RETAIN,
      autoDeleteObjects: props.destroyOnRemove,
    });

    const apiOrigin = new origins.LoadBalancerV2Origin(props.loadBalancer, {
      // The ALB has no certificate; CloudFront terminates TLS for the browser.
      protocolPolicy: cloudfront.OriginProtocolPolicy.HTTP_ONLY,
      readTimeout: Duration.seconds(60),
      keepaliveTimeout: Duration.seconds(60),
    });

    const apiBehavior: cloudfront.BehaviorOptions = {
      origin: apiOrigin,
      viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
      allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
      // Nothing from the API is cacheable: it is all per-user and often a POST.
      cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
      // Forwards Authorization and every header the API needs, without caching.
      originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER,
      compress: true,
    };

    this.distribution = new cloudfront.Distribution(this, 'Distribution', {
      comment: `${props.namePrefix} ListenSpeak AI Coach`,
      defaultRootObject: 'index.html',
      // Origin Access Control: the bucket stays private and only CloudFront reads it.
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(this.bucket),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
        compress: true,
      },
      additionalBehaviors: {
        '/api/*': apiBehavior,
        '/actuator/health': apiBehavior,
        '/v3/api-docs*': apiBehavior,
      },
      errorResponses: [
        // Client-side routing: unknown paths are routes, not missing files.
        {
          httpStatus: 403,
          responseHttpStatus: 200,
          responsePagePath: '/index.html',
          ttl: Duration.minutes(5),
        },
        {
          httpStatus: 404,
          responseHttpStatus: 200,
          responsePagePath: '/index.html',
          ttl: Duration.minutes(5),
        },
      ],
      priceClass: cloudfront.PriceClass.PRICE_CLASS_100,
      httpVersion: cloudfront.HttpVersion.HTTP2_AND_3,
      // minimumProtocolVersion only applies with a custom certificate; the
      // default CloudFront certificate fixes its own policy.
      enableLogging: false,
    });

    if (existsSync(props.bundlePath)) {
      new s3deploy.BucketDeployment(this, 'Bundle', {
        sources: [s3deploy.Source.asset(props.bundlePath)],
        destinationBucket: this.bucket,
        prune: true,
        memoryLimit: 256,
        // Deliberately not wired to the distribution. Doing so makes CDK grant
        // the deployment lambda cloudfront:CreateInvalidation on "*", which is
        // the only unjustifiable wildcard this stack would contain. The deploy
        // workflow invalidates /index.html with the AWS CLI instead, scoped to
        // this distribution. See docs/aws-runbook.md.
      });
    } else {
      // Synth still succeeds so `cdk synth` works in a fresh clone; the deploy
      // workflow always builds the bundle first.
      // eslint-disable-next-line no-console
      console.warn(
        `[web] ${props.bundlePath} does not exist; the site bucket will deploy empty. ` +
          'Run `npm run build` in frontend/ first.',
      );
    }

    new CfnOutput(this, 'SiteUrl', {
      value: `https://${this.distribution.distributionDomainName}`,
      description: 'The application URL',
    });
    new CfnOutput(this, 'SiteBucketName', { value: this.bucket.bucketName });
    new CfnOutput(this, 'DistributionId', {
      value: this.distribution.distributionId,
    });
  }
}
