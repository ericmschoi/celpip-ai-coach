import { Duration } from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';

export interface NetworkProps {
  readonly namePrefix: string;
}

/**
 * The network edge: VPC, security groups, and the load balancer.
 *
 * <p>The VPC is deliberately minimal — two availability zones, public subnets
 * only, and **no NAT Gateway**. A NAT Gateway would cost roughly as much per
 * month as everything else in this stack combined, purely to give one container
 * outbound access to the OpenAI API. Instead the Fargate task runs in a public
 * subnet with a public IP for egress, while its security group accepts inbound
 * traffic only from the load balancer. The container can reach out; nothing on
 * the internet can reach in.
 *
 * <p>If this ever needed fully private subnets for a compliance reason, the fix
 * is a NAT Gateway or VPC endpoints, and the cost changes accordingly.
 *
 * <p>The load balancer lives here rather than with the service because
 * CloudFront needs it as an origin, and Cognito needs the CloudFront domain for
 * its callback URLs. Creating it first is what breaks that cycle.
 */
export class Network extends Construct {
  readonly vpc: ec2.Vpc;
  readonly albSecurityGroup: ec2.SecurityGroup;
  readonly serviceSecurityGroup: ec2.SecurityGroup;
  readonly loadBalancer: elbv2.ApplicationLoadBalancer;
  readonly listener: elbv2.ApplicationListener;

  constructor(scope: Construct, id: string, props: NetworkProps) {
    super(scope, id);

    this.vpc = new ec2.Vpc(this, 'Vpc', {
      vpcName: `${props.namePrefix}-vpc`,
      // Two AZs is the minimum an ALB requires. Note that synthesising against
      // a pinned account looks up its availability zones, so a credential-free
      // `cdk synth` must be env-agnostic; CI does exactly that.
      maxAzs: 2,
      natGateways: 0,
      subnetConfiguration: [{ name: 'public', subnetType: ec2.SubnetType.PUBLIC, cidrMask: 24 }],
      restrictDefaultSecurityGroup: true,
    });

    this.albSecurityGroup = new ec2.SecurityGroup(this, 'AlbSecurityGroup', {
      vpc: this.vpc,
      description: 'Load balancer. Every route behind it requires a Cognito JWT.',
      allowAllOutbound: true,
    });
    this.albSecurityGroup.addIngressRule(
      ec2.Peer.anyIpv4(),
      ec2.Port.tcp(80),
      'CloudFront reaches the API origin here',
    );

    this.serviceSecurityGroup = new ec2.SecurityGroup(this, 'ServiceSecurityGroup', {
      vpc: this.vpc,
      description: 'Fargate task. Outbound to OpenAI and AWS; inbound only from the ALB.',
      allowAllOutbound: true,
    });
    this.serviceSecurityGroup.addIngressRule(
      this.albSecurityGroup,
      ec2.Port.tcp(8080),
      'Only the load balancer may reach the container',
    );

    this.loadBalancer = new elbv2.ApplicationLoadBalancer(this, 'LoadBalancer', {
      loadBalancerName: `${props.namePrefix}-alb`,
      vpc: this.vpc,
      internetFacing: true,
      securityGroup: this.albSecurityGroup,
      // Generation plus TTS plus assembly is the slowest request in the app.
      idleTimeout: Duration.seconds(180),
      dropInvalidHeaderFields: true,
    });

    this.listener = this.loadBalancer.addListener('Http', {
      port: 80,
      protocol: elbv2.ApplicationProtocol.HTTP,
      open: false,
      defaultAction: elbv2.ListenerAction.fixedResponse(503, {
        contentType: 'application/problem+json',
        messageBody: JSON.stringify({
          type: 'https://listenspeak.app/problems/provider-unavailable',
          title: 'Service starting',
          status: 503,
          code: 'PROVIDER_UNAVAILABLE',
          retryable: true,
        }),
      }),
    });
  }
}
