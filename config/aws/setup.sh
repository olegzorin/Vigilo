# Suppress verbose output
export AWS_PAGER=""

AWS_ACCOUNT_ID=$(aws sts get-caller-identity \
    --query Account \
    --output text)

REGION=us-east-1
RESULT_QUEUE=dev-botlab-lambda-results
RESULT_DLQ=${RESULT_QUEUE}-dlq

RESULT_DLQ_URL=$(aws sqs get-queue-url \
    --queue-name "$RESULT_DLQ" \
    --region "$REGION" \
    --query QueueUrl \
    --output text 2>/dev/null || aws sqs create-queue \
        --queue-name "$RESULT_DLQ" \
        --region "$REGION" \
        --query QueueUrl \
        --output text)

aws sqs set-queue-attributes \
    --queue-url "$RESULT_DLQ_URL" \
    --attributes MessageRetentionPeriod=1209600,VisibilityTimeout=60 \
    --region "$REGION"

RESULT_DLQ_ARN=$(aws sqs get-queue-attributes \
    --queue-url "$RESULT_DLQ_URL" \
    --attribute-names QueueArn \
    --region "$REGION" \
    --query Attributes.QueueArn \
    --output text)

RESULT_QUEUE_URL=$(aws sqs get-queue-url \
    --queue-name "$RESULT_QUEUE" \
    --region "$REGION" \
    --query QueueUrl \
    --output text 2>/dev/null || aws sqs create-queue \
        --queue-name "$RESULT_QUEUE" \
        --region "$REGION" \
        --query QueueUrl \
        --output text)

RESULT_QUEUE_ATTRIBUTES=$(printf \
    '{"MessageRetentionPeriod":"345600","VisibilityTimeout":"60","RedrivePolicy":"{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"10\"}"}' \
    "$RESULT_DLQ_ARN")

aws sqs set-queue-attributes \
    --queue-url "$RESULT_QUEUE_URL" \
    --attributes "$RESULT_QUEUE_ATTRIBUTES" \
    --region "$REGION"

aws cloudwatch put-metric-alarm \
    --alarm-name "$RESULT_DLQ-messages-visible" \
    --namespace AWS/SQS \
    --metric-name ApproximateNumberOfMessagesVisible \
    --dimensions Name=QueueName,Value="$RESULT_DLQ" \
    --statistic Maximum \
    --period 60 \
    --evaluation-periods 1 \
    --threshold 1 \
    --comparison-operator GreaterThanOrEqualToThreshold \
    --treat-missing-data notBreaching \
    --region "$REGION"

aws cloudwatch put-metric-alarm \
    --alarm-name "$RESULT_DLQ-oldest-message" \
    --namespace AWS/SQS \
    --metric-name ApproximateAgeOfOldestMessage \
    --dimensions Name=QueueName,Value="$RESULT_DLQ" \
    --statistic Maximum \
    --period 60 \
    --evaluation-periods 1 \
    --threshold 300 \
    --comparison-operator GreaterThanOrEqualToThreshold \
    --treat-missing-data notBreaching \
    --region "$REGION"

aws sns create-topic \
    --name dev-botlab-lambda-build-errors \
    --region "$REGION"

aws sns create-topic \
    --name dev-botlab-lambda-runtime-errors \
    --region "$REGION"


# Execution role for lambdas
ROLE=dev-botlab-execution-role

aws iam create-role \
    --path /service-role/ \
    --role-name $ROLE \
    --assume-role-policy-document file://assume-role-policy.json

ACCESS_POLICY=$(sed \
    "s/__AWS_ACCOUNT_ID__/$AWS_ACCOUNT_ID/g" \
    access-policy.json)

POLICY_ARN=$(aws iam create-policy \
    --policy-name dev-botlab-execution \
    --policy-document "$ACCESS_POLICY" \
    --query Policy.Arn \
    --output text)

aws iam attach-role-policy \
    --role-name "$ROLE" \
    --policy-arn "$POLICY_ARN"
