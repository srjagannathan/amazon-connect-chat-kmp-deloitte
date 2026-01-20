#!/bin/bash
set -e

# AI Virtual Agent Backend Deployment Script
# Usage: ./deploy.sh [environment] [region]

ENVIRONMENT=${1:-dev}
REGION=${2:-us-east-1}
STACK_NAME="ai-virtual-agent-${ENVIRONMENT}"

echo "========================================"
echo "AI Virtual Agent Backend Deployment"
echo "========================================"
echo "Environment: ${ENVIRONMENT}"
echo "Region: ${REGION}"
echo "Stack: ${STACK_NAME}"
echo "========================================"

# Check for AWS CLI
if ! command -v aws &> /dev/null; then
    echo "ERROR: AWS CLI is not installed"
    exit 1
fi

# Check for SAM CLI
if ! command -v sam &> /dev/null; then
    echo "ERROR: AWS SAM CLI is not installed"
    echo "Install with: pip install aws-sam-cli"
    exit 1
fi

# Install dependencies
echo ""
echo "Installing dependencies..."
cd "$(dirname "$0")"
npm install --production

# Build
echo ""
echo "Building SAM application..."
sam build

# Deploy
echo ""
echo "Deploying to AWS..."
sam deploy \
    --stack-name ${STACK_NAME} \
    --region ${REGION} \
    --parameter-overrides Environment=${ENVIRONMENT} \
    --capabilities CAPABILITY_IAM \
    --resolve-s3 \
    --no-confirm-changeset \
    --no-fail-on-empty-changeset

# Get outputs
echo ""
echo "========================================"
echo "Deployment Complete!"
echo "========================================"

aws cloudformation describe-stacks \
    --stack-name ${STACK_NAME} \
    --region ${REGION} \
    --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' \
    --output table

# Reminder to set API keys
SECRETS_ARN=$(aws cloudformation describe-stacks \
    --stack-name ${STACK_NAME} \
    --region ${REGION} \
    --query "Stacks[0].Outputs[?OutputKey=='SecretsArn'].OutputValue" \
    --output text)

echo ""
echo "========================================"
echo "IMPORTANT: Configure API Keys"
echo "========================================"
echo ""
echo "Run this command to set your API keys:"
echo ""
echo "aws secretsmanager put-secret-value \\"
echo "    --secret-id ${ENVIRONMENT}/ai-virtual-agent/api-keys \\"
echo "    --region ${REGION} \\"
echo "    --secret-string '{\"ANTHROPIC_API_KEY\":\"your-claude-key\",\"OPENAI_API_KEY\":\"your-openai-key\"}'"
echo ""
echo "========================================"
