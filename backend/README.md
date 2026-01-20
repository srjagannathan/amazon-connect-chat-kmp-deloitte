# AI Virtual Agent Backend

Serverless backend for the AI Virtual Agent feature in Amazon Connect Chat KMP.

## Overview

This backend provides:
- **Streaming AI responses** using Lambda Response Streaming
- **Claude (primary) + OpenAI (fallback)** dual-provider architecture
- **Conversation summarization** for agent handover
- **Sentiment analysis** for enhanced agent context

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  KMP Mobile App │────▶│  Lambda Functions │────▶│  Claude/OpenAI  │
│  (All Platforms)│     │  (Streaming SSE)  │     │  APIs           │
└─────────────────┘     └──────────────────┘     └─────────────────┘
                               │
                               ▼
                        ┌──────────────────┐
                        │ Secrets Manager  │
                        │ (API Keys)       │
                        └──────────────────┘
```

## Prerequisites

- AWS CLI configured with appropriate credentials
- AWS SAM CLI installed (`pip install aws-sam-cli`)
- Node.js 20.x
- Claude API key from Anthropic
- OpenAI API key

## Deployment

### 1. Deploy the Stack

```bash
# Development environment
./deploy.sh dev us-east-1

# Staging environment
./deploy.sh staging us-east-1

# Production environment
./deploy.sh prod us-east-1
```

### 2. Configure API Keys

After deployment, update the secrets with your API keys:

```bash
aws secretsmanager put-secret-value \
    --secret-id dev/ai-virtual-agent/api-keys \
    --region us-east-1 \
    --secret-string '{"ANTHROPIC_API_KEY":"sk-ant-...","OPENAI_API_KEY":"sk-..."}'
```

### 3. Get Endpoint URLs

The deployment outputs will show your endpoint URLs:

```
StreamingEndpoint: https://xxx.lambda-url.us-east-1.on.aws/
HealthEndpoint: https://xxx.lambda-url.us-east-1.on.aws/
SummaryEndpoint: https://xxx.lambda-url.us-east-1.on.aws/
SentimentEndpoint: https://xxx.lambda-url.us-east-1.on.aws/
```

### 4. Update Mobile App Configuration

Update the `AI_PROXY_URL` in your platform entry points:

- **Android**: `MainActivity.kt`
- **iOS**: `Main.ios.kt`
- **Desktop**: `Main.kt`
- **Web**: `Main.kt`

## API Endpoints

### POST /api/v1/chat/stream

Stream AI responses using Server-Sent Events (SSE).

**Request:**
```json
{
  "messages": [
    {"role": "user", "content": "Hello, I need help with my order"},
    {"role": "assistant", "content": "I'd be happy to help..."}
  ],
  "provider": "claude",
  "systemPrompt": "You are a helpful assistant...",
  "maxTokens": 1024,
  "temperature": 0.7,
  "sessionId": "session-123"
}
```

**Response (SSE):**
```
data: {"delta": "I can ", "provider": "claude"}
data: {"delta": "help you ", "provider": "claude"}
data: {"delta": "with that!", "provider": "claude"}
data: {"done": true, "shouldEscalate": false, "suggestedReplies": ["Track order", "Return item"], "provider": "claude"}
data: [DONE]
```

### GET /api/v1/health

Check AI provider availability.

**Response:**
```json
{
  "timestamp": "2026-01-19T12:00:00Z",
  "providers": {
    "claude": {"healthy": true, "latencyMs": 234, "model": "claude-sonnet-4-20250514"},
    "openai": {"healthy": true, "latencyMs": 456, "model": "gpt-4-turbo-preview"}
  }
}
```

### POST /api/v1/summarize

Generate conversation summary for agent handover.

**Request:**
```json
{
  "messages": [...],
  "provider": "claude"
}
```

**Response:** Plain text summary

### POST /api/v1/sentiment

Analyze customer sentiment.

**Request:**
```json
{
  "messages": [...]
}
```

**Response:**
```json
{
  "sentiment": "neutral",
  "confidence": 0.75,
  "indicators": ["polite language", "clear questions"]
}
```

## Escalation Protocol

The AI is instructed to include escalation markers in responses when appropriate:

```
[ESCALATE: Customer requesting refund over policy limit]
```

The backend parses these markers and sets `shouldEscalate: true` in the final SSE chunk.

**Escalation triggers:**
- Explicit request for human agent
- Complex billing/refund issues
- Legal or compliance questions
- Repeated failed resolution attempts
- Frustrated customer sentiment

## Quick Replies

The AI can suggest quick reply options:

```
[QUICK_REPLIES: Track my order | Return an item | Talk to agent]
```

These are parsed and returned in the final SSE chunk as `suggestedReplies`.

## Monitoring

### CloudWatch Dashboard

A dashboard is automatically created at: `{env}-ai-virtual-agent`

### Alarms

- High error rate alarm triggers when >10 errors in 5 minutes

### Logs

Logs are stored in: `/aws/lambda/{env}-ai-agent`

## Future Integration

The infrastructure includes disabled resources for future integration:

- **EventBridge**: Event bus for analytics pipeline
- **VPC Config**: Commented out VPC configuration for peering

To enable VPC integration, uncomment the `VpcConfig` section in `template.yaml`.

## Cost Optimization

- Lambda functions use ARM64 (Graviton2) for cost efficiency
- Secrets are cached to reduce Secrets Manager calls
- Response streaming reduces perceived latency

## Security

- API keys stored in AWS Secrets Manager
- Lambda functions use least-privilege IAM roles
- CORS configured for mobile app origins
- No sensitive data logged

## Troubleshooting

### "Primary provider failed"

1. Check API key is valid in Secrets Manager
2. Verify Claude API quota/limits
3. Check CloudWatch logs for detailed error

### Slow responses

1. Check CloudWatch metrics for latency
2. Verify Lambda memory allocation (currently 1024MB)
3. Consider enabling provisioned concurrency

### CORS errors

Update `AllowOrigins` in `template.yaml` to include your domains.
