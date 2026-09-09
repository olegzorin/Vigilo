# AWS setup

This directory contains the AWS resources and IAM policies required by Vigilo Framework lambda Lambda functions.

## Resources created

`setup.sh` creates the following resources in `us-east-1`:

- SQS queue `dev-botlab-lambda-results`, retained for four days
- SQS dead-letter queue `dev-botlab-lambda-results-dlq`, retained for 14 days
- SQS redrive policy that moves a result to the dead-letter queue after 10 receives
- CloudWatch alarms for visible and aging dead-letter messages
- SNS topic `dev-botlab-lambda-build-errors`
- SNS topic `dev-botlab-lambda-runtime-errors`
- IAM managed policy `dev-botlab-execution`
- IAM role `dev-botlab-execution-role`

The execution policy allows Lambda functions to write CloudWatch logs, send results to the SQS
queue, publish to the two SNS topics, and manage the network interfaces required for VPC access.

## Prerequisites

- AWS CLI configured with credentials for the target account
- Permission to call `sqs:CreateQueue`, `sqs:GetQueueAttributes`, `sqs:SetQueueAttributes`,
  `cloudwatch:PutMetricAlarm`, `sns:CreateTopic`, `iam:CreateRole`, `iam:CreatePolicy`, and
  `iam:AttachRolePolicy`
- A POSIX-compatible shell and `sed`

Verify the active AWS identity before provisioning:

```sh
aws sts get-caller-identity
```

To use a named AWS CLI profile, set it for both verification and setup:

```sh
AWS_PROFILE=dev aws sts get-caller-identity
AWS_PROFILE=dev sh setup.sh
```

## Run the setup

Run the script from this directory because it loads the policy files using relative paths:

```sh
cd config/aws
sh setup.sh
```

The script obtains the account ID from the active AWS credentials. It replaces the
`__AWS_ACCOUNT_ID__` placeholders in `access-policy.json` in memory before creating the managed
policy; the template file is not modified.

The SQS attributes and CloudWatch alarms are reconciled when this script runs. The worker also
reconciles the result queue and dead-letter queue before registering its result listener. IAM role
and managed-policy creation is intended for initial provisioning: rerunning the complete script
after those resources exist causes the corresponding IAM create operations to fail.

## Redrive dead-lettered results

Investigate and fix the processing failure before moving messages back to the result queue. Then
start an SQS message-move task:

```sh
RESULT_DLQ_ARN=$(aws sqs get-queue-attributes \
    --queue-url "$(aws sqs get-queue-url --queue-name dev-botlab-lambda-results-dlq --query QueueUrl --output text)" \
    --attribute-names QueueArn \
    --query Attributes.QueueArn \
    --output text)

RESULT_QUEUE_ARN=$(aws sqs get-queue-attributes \
    --queue-url "$(aws sqs get-queue-url --queue-name dev-botlab-lambda-results --query QueueUrl --output text)" \
    --attribute-names QueueArn \
    --query Attributes.QueueArn \
    --output text)

aws sqs start-message-move-task \
    --source-arn "$RESULT_DLQ_ARN" \
    --destination-arn "$RESULT_QUEUE_ARN"
```

The alarms are created without notification actions. Attach the environment's operational SNS
topic or incident integration to both alarms during deployment.

## Worker queue properties

The worker uses these defaults when reconciling the queues at startup:

- `vf.aws.sqs.lambdaResults.retention=4d`
- `vf.aws.sqs.lambdaResults.deadLetterRetention=14d`
- `vf.aws.sqs.lambdaResults.maxReceiveCount=10`
- `vf.sqs.visibilityTimeoutSeconds=60` (shared with the SQS consumer)

Duration properties require an explicit unit such as `s`, `m`, `h`, or `d`. The setup script
uses the same default values; worker-side overrides take effect the next time the worker starts.

## Files

- `setup.sh` provisions the resources and attaches the execution policy to the role.
- `access-policy.json` is the account-independent permissions-policy template.
- `assume-role-policy.json` is the Lambda trust policy for the execution role.
