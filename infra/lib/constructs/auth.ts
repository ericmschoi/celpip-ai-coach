import { CfnOutput, Duration, RemovalPolicy, Stack } from 'aws-cdk-lib';
import * as cognito from 'aws-cdk-lib/aws-cognito';
import { Construct } from 'constructs';

export interface AuthProps {
  readonly namePrefix: string;
  readonly destroyOnRemove: boolean;
  /** Where Cognito is allowed to redirect after sign-in. */
  readonly callbackUrls: string[];
  readonly logoutUrls: string[];
}

/**
 * Cognito user pool for a single private user.
 *
 * <p>Self sign-up is disabled, so the only way an account exists is the
 * operator creating it. That is what keeps this a personal app rather than an
 * open endpoint someone else can run a bill up on.
 */
export class Auth extends Construct {
  readonly userPool: cognito.UserPool;
  readonly client: cognito.UserPoolClient;
  readonly domain: cognito.UserPoolDomain;

  constructor(scope: Construct, id: string, props: AuthProps) {
    super(scope, id);

    this.userPool = new cognito.UserPool(this, 'UserPool', {
      userPoolName: `${props.namePrefix}-users`,
      // The whole point: nobody can register themselves.
      selfSignUpEnabled: false,
      signInAliases: { email: true },
      standardAttributes: { email: { required: true, mutable: false } },
      passwordPolicy: {
        minLength: 12,
        requireLowercase: true,
        requireUppercase: true,
        requireDigits: true,
        requireSymbols: true,
      },
      accountRecovery: cognito.AccountRecovery.EMAIL_ONLY,
      mfa: cognito.Mfa.OPTIONAL,
      mfaSecondFactor: { sms: false, otp: true },
      // Threat protection is a paid Cognito plan feature and needs a real user
      // base to be worth it. With self sign-up disabled and one account, the
      // password policy and MFA are the controls that matter.
      removalPolicy: props.destroyOnRemove ? RemovalPolicy.DESTROY : RemovalPolicy.RETAIN,
    });

    this.client = this.userPool.addClient('WebClient', {
      userPoolClientName: `${props.namePrefix}-web`,
      // Public client: no secret, because a browser cannot keep one.
      generateSecret: false,
      authFlows: { userSrp: true },
      oAuth: {
        // Authorization Code with PKCE, the current recommendation for SPAs.
        flows: { authorizationCodeGrant: true, implicitCodeGrant: false },
        scopes: [cognito.OAuthScope.OPENID, cognito.OAuthScope.EMAIL, cognito.OAuthScope.PROFILE],
        callbackUrls: props.callbackUrls,
        logoutUrls: props.logoutUrls,
      },
      preventUserExistenceErrors: true,
      accessTokenValidity: Duration.hours(1),
      idTokenValidity: Duration.hours(1),
      refreshTokenValidity: Duration.days(30),
      enableTokenRevocation: true,
    });

    this.domain = this.userPool.addDomain('HostedUiDomain', {
      cognitoDomain: {
        domainPrefix: `${props.namePrefix}-${Stack.of(this).account}`,
      },
    });

    new CfnOutput(this, 'UserPoolId', { value: this.userPool.userPoolId });
    new CfnOutput(this, 'UserPoolClientId', {
      value: this.client.userPoolClientId,
    });
    new CfnOutput(this, 'CognitoDomain', { value: this.domain.baseUrl() });
    new CfnOutput(this, 'IssuerUri', {
      value: `https://cognito-idp.${Stack.of(this).region}.amazonaws.com/${this.userPool.userPoolId}`,
      description: 'Value for APP_COGNITO_ISSUER_URI',
    });
  }
}
