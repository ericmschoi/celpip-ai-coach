import type { App } from 'aws-cdk-lib';

/**
 * Deployment configuration resolved from CDK context (`-c key=value`) with
 * environment-variable fallbacks. Keeping this in one place means the stacks
 * never read `process.env` directly.
 */
export interface DeploymentConfig {
  /** Short project slug used in resource names and tags. */
  readonly project: string;
  /** Environment name: `dev` or `prod`. Only `dev` is deployed initially. */
  readonly envName: 'dev' | 'prod';
  readonly region: string;
  /** AWS account id; undefined means "env-agnostic synth". */
  readonly account?: string;
  /** Optional custom domain. Generated AWS URLs are used when absent. */
  readonly domainName?: string;
  readonly hostedZoneId?: string;
  /** Monthly USD threshold for the AWS Budgets alarm. */
  readonly monthlyBudgetUsd: number;
  /** Email for budget notifications; the budget is skipped when absent. */
  readonly budgetEmail?: string;
}

function contextOrEnv(app: App, key: string, envKey: string): string | undefined {
  const fromContext = app.node.tryGetContext(key) as string | undefined;
  return fromContext ?? process.env[envKey];
}

export function resolveConfig(app: App): DeploymentConfig {
  const envName = (contextOrEnv(app, 'env', 'LISTENSPEAK_ENV') ?? 'dev') as 'dev' | 'prod';
  if (envName !== 'dev' && envName !== 'prod') {
    throw new Error(`Unsupported env "${envName}". Use "dev" or "prod".`);
  }

  const budget = contextOrEnv(app, 'monthlyBudgetUsd', 'LISTENSPEAK_BUDGET_USD');

  return {
    project: contextOrEnv(app, 'project', 'LISTENSPEAK_PROJECT') ?? 'listenspeak',
    envName,
    region:
      contextOrEnv(app, 'region', 'LISTENSPEAK_REGION') ??
      process.env.CDK_DEFAULT_REGION ??
      'ca-central-1',
    account: contextOrEnv(app, 'account', 'CDK_DEFAULT_ACCOUNT'),
    domainName: contextOrEnv(app, 'domainName', 'LISTENSPEAK_DOMAIN'),
    hostedZoneId: contextOrEnv(app, 'hostedZoneId', 'LISTENSPEAK_HOSTED_ZONE_ID'),
    monthlyBudgetUsd: budget ? Number(budget) : 25,
    budgetEmail: contextOrEnv(app, 'budgetEmail', 'LISTENSPEAK_BUDGET_EMAIL'),
  };
}

/** Deterministic, human-readable resource name prefix. */
export function prefix(config: DeploymentConfig): string {
  return `${config.project}-${config.envName}`;
}
