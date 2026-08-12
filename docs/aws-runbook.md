# AWS runbook

Everything you need to deploy, operate, and tear down the `dev` environment.

> Nothing in this repository creates a billable AWS resource until you run `cdk deploy` yourself.

## What gets created

| Resource                    | Notes                                                        |
| --------------------------- | ------------------------------------------------------------ |
| VPC, 2 AZs, public subnets  | **No NAT Gateway** — see ADR in `docs/architecture.md`        |
| Application Load Balancer   | HTTP:80, reachable only as a CloudFront origin in practice    |
| ECS Fargate cluster + service | 1 task, 0.5 vCPU / 1 GB, image built from `backend/`        |
| ECR repository (CDK assets) | Holds the backend image                                       |
| DynamoDB table              | On-demand, TTL on `expiresAt`                                 |
| S3 bucket (audio)           | Private, encrypted, lifecycle expiry                          |
| S3 bucket (site) + CloudFront | Private bucket, Origin Access Control, HTTPS                |
| Cognito user pool + client  | Self sign-up disabled, hosted UI domain                       |
| Secrets Manager secret      | Empty until you put the OpenAI key in it                      |
| CloudWatch log group        | 7-day retention in dev                                        |
| AWS Budget                  | Only when `-c budgetEmail=` is supplied                       |

## Recurring cost categories

Rough shape for a personal `dev` environment in `ca-central-1`, single user:

| Category                        | Driver                                        |
| ------------------------------- | --------------------------------------------- |
| ECS Fargate task hours          | The dominant cost — one task running 24/7      |
| Application Load Balancer hours | Fixed hourly charge plus LCU                   |
| CloudFront + S3                 | Requests and a few GB of transfer              |
| DynamoDB on-demand              | Negligible at this volume                      |
| CloudWatch Logs ingestion       | Small, capped by 7-day retention               |
| Secrets Manager                 | Per secret per month                           |
| ECR storage                     | One image, a few hundred MB                    |
| **OpenAI usage**                | Billed by OpenAI, not AWS. Capped by the app's daily limits |

To cut the largest line item when you are not practising, scale the service to zero:

```bash
aws ecs update-service --cluster listenspeak-dev-cluster --service listenspeak-dev-api --desired-count 0
```

and back to one when you want to practise again.

## Prerequisites

```bash
brew install awscli
aws configure          # or configure an SSO profile
aws sts get-caller-identity
```

Bootstrap the account/region once (creates the CDK asset bucket and roles):

```bash
cd infra
npx cdk bootstrap aws://<account-id>/ca-central-1
```

## Deploy

Always build the frontend first — the CDK stack uploads whatever is in
`frontend/dist`.

```bash
# 1. Build the bundle that the deployed site will serve
cd frontend
VITE_AUTH_MODE=COGNITO npm run build

# 2. Review, then deploy
cd ../infra
npx cdk diff  -c env=dev -c budgetEmail=you@example.com
npx cdk deploy -c env=dev -c budgetEmail=you@example.com
```

The first deploy takes 10–15 minutes, mostly CloudFront.

**Chicken-and-egg on the first deploy.** The bundle needs `VITE_COGNITO_DOMAIN`
and `VITE_COGNITO_CLIENT_ID`, which do not exist until the stack does. So:
deploy once, read the outputs, rebuild the bundle with them, and deploy again.

```bash
aws cloudformation describe-stacks --stack-name listenspeak-dev \
  --query 'Stacks[0].Outputs' --output table

cd ../frontend
VITE_AUTH_MODE=COGNITO \
VITE_COGNITO_DOMAIN=<CognitoDomain output> \
VITE_COGNITO_CLIENT_ID=<UserPoolClientId output> \
VITE_COGNITO_REDIRECT_URI=<SiteUrl output>/auth/callback \
npm run build

cd ../infra && npx cdk deploy -c env=dev
```

Then invalidate the cached entry point:

```bash
aws cloudfront create-invalidation \
  --distribution-id <DistributionId output> --paths '/index.html'
```

The stack deliberately does not grant the deployment lambda CloudFront
invalidation rights, because CDK would grant it on `*`. Invalidating here keeps
that permission out of the account entirely.

## Configure the OpenAI key

The secret is created empty. Put the value in without it ever touching a shell
history file, a repository, or a CI log:

```bash
read -rs OPENAI_KEY                      # types nothing to the screen
aws secretsmanager put-secret-value \
  --secret-id listenspeak-dev/openai-api-key \
  --secret-string "$OPENAI_KEY"
unset OPENAI_KEY

# Restart the task so it picks the value up
aws ecs update-service --cluster listenspeak-dev-cluster \
  --service listenspeak-dev-api --force-new-deployment
```

## Create the private user

Self sign-up is disabled, so the account is created by you. Let Cognito generate
and email a temporary password rather than choosing one on the command line:

```bash
aws cognito-idp admin-create-user \
  --user-pool-id <UserPoolId output> \
  --username you@example.com \
  --user-attributes Name=email,Value=you@example.com Name=email_verified,Value=true \
  --desired-delivery-mediums EMAIL
```

Sign in at the site URL; the hosted UI will ask you to set a permanent password.

## Smoke test

```bash
./scripts/smoke-test.sh dev
```

It checks that the backend health endpoint answers, the site loads, and an
unauthenticated API call is rejected with `401`.

Then, by hand: sign in, generate one Listening exercise, and confirm the audio
plays and the transcript appears only after submitting.

## View logs

```bash
aws logs tail /listenspeak/dev/api --follow
```

Useful filters:

```bash
aws logs tail /listenspeak/dev/api --filter-pattern 'ERROR'
aws logs tail /listenspeak/dev/api --filter-pattern 'usage'   # OpenAI token usage
```

Logs contain request ids, latency, model names, and token counts. They never
contain recordings, transcripts, or the API key.

## Rotate the OpenAI key

1. Create a new key in the OpenAI dashboard.
2. `aws secretsmanager put-secret-value --secret-id listenspeak-dev/openai-api-key --secret-string "$NEW_KEY"`
3. `aws ecs update-service --cluster listenspeak-dev-cluster --service listenspeak-dev-api --force-new-deployment`
4. Revoke the old key in the OpenAI dashboard.

Rotate at the first hint of exposure. Rotation is cheap; investigation is not.

## Budget alerts

Pass `-c budgetEmail=you@example.com` at deploy time and confirm the SNS
subscription email. Two alerts are created: forecast above 80% of the monthly
limit, and actual above 100%. Set the limit with
`-c monthlyBudgetUsd=25`.

## GitHub Actions deployment

The workflow uses OIDC, so no long-lived AWS keys exist in GitHub. It is **not
active** until you configure:

1. An IAM OIDC identity provider for `token.actions.githubusercontent.com`.
2. A role trusting `repo:ericmschoi/celpip-ai-coach:*` with permission to deploy CDK.
3. Repository variables: `AWS_DEPLOY_ROLE_ARN`, `VITE_API_BASE_URL`,
   `VITE_COGNITO_DOMAIN`, `VITE_COGNITO_CLIENT_ID`, `VITE_COGNITO_REDIRECT_URI`.
4. A protected GitHub environment named `prod` with a required reviewer.

## Destroy everything

```bash
cd infra
npx cdk destroy -c env=dev
```

In `dev` every resource has a `DESTROY` removal policy and both buckets
auto-delete their objects, so this really does leave nothing behind. Afterwards,
check for leftovers that CDK does not own:

```bash
aws ecr describe-repositories                     # CDK asset repository
aws logs describe-log-groups --log-group-name-prefix /listenspeak
aws budgets describe-budgets --account-id <account-id>
```

Also delete the OpenAI key in the OpenAI dashboard — destroying the AWS secret
does not revoke it.
