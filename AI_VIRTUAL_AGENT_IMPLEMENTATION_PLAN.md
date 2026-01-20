# AI Virtual Agent Implementation Plan

## Amazon Connect Chat KMP - Real AI Integration

**Document Version:** 2.0
**Date:** January 19, 2026
**Status:** READY FOR FINAL APPROVAL

---

## Decisions Confirmed

| Decision | Choice |
|----------|--------|
| **AI Providers** | Claude (primary) + OpenAI (fallback) |
| **Response Mode** | Streaming responses (priority) |
| **Backend Infrastructure** | New serverless setup (future-integration ready) |
| **Escalation Behavior** | Always confirm with user before escalating |

---

## Executive Summary

This plan replaces the mock Virtual Agent with a real AI-powered conversational agent using Claude as the primary provider and OpenAI GPT-4 as fallback. The solution prioritizes streaming responses for a natural chat experience and uses a serverless AWS backend designed for future integration with existing infrastructure.

---

## Current State Analysis

### What Exists Today

The app currently has a **placeholder Virtual Agent** that:
- Displays a static welcome message: "Hello! I'm your virtual assistant. How can I help you today?"
- Has no NLU, intent detection, or response generation capabilities
- Cannot understand or respond to customer messages
- Requires manual handover via the "Talk to Agent" button

### Key Files Involved

| File | Purpose |
|------|---------|
| `shared/src/commonMain/.../data/Models.kt` | `virtualAgentUser` definition, `ParticipantRole` enum |
| `shared/src/commonMain/.../store/Reducer.kt` | State management, `ChatMode.VIRTUAL_AGENT` |
| `shared/src/commonMain/.../ui/ChatApp.kt` | Message handling and UI logic |
| `androidApp/.../MainActivity.kt` | Welcome message initialization, handover logic |
| `desktopApp/.../Main.kt` | Desktop entry point |
| `iosApp/.../Main.ios.kt` | iOS entry point |
| `webApp/.../Main.kt` | Web entry point |

---

## Architecture Overview

### High-Level Design

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           KMP SHARED MODULE                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────┐    ┌─────────────────┐    ┌─────────────────────────────┐   │
│  │  UI Layer  │───▶│  Store/Reducer  │───▶│    AI Agent Module (NEW)    │   │
│  │ (Compose)  │    │  (Redux-style)  │    │                             │   │
│  └────────────┘    └─────────────────┘    │  ┌───────────────────────┐  │   │
│                                           │  │ AIAgentRepository     │  │   │
│                                           │  │ (Interface)           │  │   │
│                                           │  └───────────┬───────────┘  │   │
│                                           │              │              │   │
│                                           │  ┌───────────▼───────────┐  │   │
│                                           │  │ StreamingAIClient     │  │   │
│                                           │  │ (Ktor SSE)            │  │   │
│                                           │  └───────────────────────┘  │   │
│                                           └─────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                            │
                                            │ HTTPS + SSE
                                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     AWS SERVERLESS BACKEND (NEW)                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────┐    ┌──────────────────┐    ┌────────────────────┐    │
│  │   API Gateway    │───▶│  Lambda Function │───▶│  Secrets Manager   │    │
│  │  (HTTP + Stream) │    │  (Node.js/Python)│    │  (API Keys)        │    │
│  └──────────────────┘    └────────┬─────────┘    └────────────────────┘    │
│                                   │                                         │
│                    ┌──────────────┼──────────────┐                         │
│                    ▼              ▼              ▼                         │
│            ┌────────────┐  ┌────────────┐  ┌────────────┐                  │
│            │  Claude    │  │  OpenAI    │  │ CloudWatch │                  │
│            │  API       │  │  API       │  │ (Logging)  │                  │
│            │ (Primary)  │  │ (Fallback) │  │            │                  │
│            └────────────┘  └────────────┘  └────────────┘                  │
│                                                                              │
│  Future Integration Points:                                                  │
│  ├── VPC Peering for existing services                                      │
│  ├── Shared authentication (Cognito/IAM)                                    │
│  └── Event bridge for analytics pipeline                                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Streaming Message Flow

```
User sends message
       │
       ▼
┌──────────────────────┐
│ Store.dispatch       │
│ (SendMessage)        │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐     ┌──────────────────────┐
│ ChatMode ==          │ YES │ dispatch             │
│ VIRTUAL_AGENT?       │────▶│ (AIProcessingStarted)│
└──────────────────────┘     └──────────┬───────────┘
                                        │
                                        ▼
                             ┌──────────────────────┐
                             │ AIAgentRepository    │
                             │ .processMessageStream│
                             └──────────┬───────────┘
                                        │
                    ┌───────────────────┼───────────────────┐
                    │                   │                   │
                    ▼                   ▼                   ▼
            ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
            │ SSE Chunk 1  │    │ SSE Chunk 2  │    │ SSE Final    │
            │ "I can "     │    │ "help you "  │    │ [DONE]       │
            └──────┬───────┘    └──────┬───────┘    └──────┬───────┘
                   │                   │                   │
                   ▼                   ▼                   ▼
            ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
            │ dispatch     │    │ dispatch     │    │ dispatch     │
            │ (AIStream    │    │ (AIStream    │    │ (AIProcessing│
            │  Chunk)      │    │  Chunk)      │    │  Complete)   │
            └──────────────┘    └──────────────┘    └──────┬───────┘
                                                          │
                                        ┌─────────────────┴─────────────────┐
                                        │                                   │
                                        ▼                                   ▼
                              ┌──────────────────┐              ┌──────────────────┐
                              │ shouldEscalate   │              │ Display final    │
                              │ = true?          │              │ response +       │
                              └────────┬─────────┘              │ quick replies    │
                                       │ YES                    └──────────────────┘
                                       ▼
                              ┌──────────────────┐
                              │ Show Escalation  │
                              │ Confirmation     │
                              │ Dialog           │
                              └────────┬─────────┘
                                       │
                        ┌──────────────┴──────────────┐
                        ▼                             ▼
               ┌──────────────┐              ┌──────────────┐
               │ User confirms│              │ User declines│
               │ → Handover   │              │ → Continue   │
               │   to Connect │              │   with AI    │
               └──────────────┘              └──────────────┘
```

---

## Implementation Phases

### Phase 1: Core AI Agent Infrastructure (4-5 days)

**Objective:** Create the AI agent interface with streaming support and dual-provider architecture.

#### 1.1 AI Agent Data Models

**New file:** `shared/src/commonMain/kotlin/com/amazon/connect/chat/ai/AIAgentModels.kt`

```kotlin
package com.amazon.connect.chat.ai

import kotlinx.serialization.Serializable

/**
 * AI providers supported by the system
 */
enum class AIProvider {
    CLAUDE,   // Primary provider
    OPENAI    // Fallback provider
}

/**
 * Configuration for AI agent behavior
 */
@Serializable
data class AIAgentConfig(
    val primaryProvider: AIProvider = AIProvider.CLAUDE,
    val fallbackProvider: AIProvider = AIProvider.OPENAI,
    val proxyBaseUrl: String,
    val streamingEnabled: Boolean = true,
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val systemPrompt: String,
    val escalationConfig: EscalationConfig = EscalationConfig()
)

/**
 * Escalation behavior configuration
 */
@Serializable
data class EscalationConfig(
    val confirmWithUser: Boolean = true,  // Always true per requirements
    val keywords: List<String> = listOf(
        "human", "agent", "representative", "manager",
        "supervisor", "real person", "speak to someone"
    ),
    val confidenceThreshold: Float = 0.6f,
    val maxRetryAttempts: Int = 3
)

/**
 * Streaming chunk from AI response
 */
@Serializable
data class AIStreamChunk(
    val delta: String? = null,           // Text chunk
    val done: Boolean = false,           // Stream complete flag
    val shouldEscalate: Boolean = false, // Escalation recommendation
    val escalationReason: String? = null,
    val suggestedReplies: List<String>? = null,
    val error: String? = null,
    val provider: AIProvider? = null     // Which provider generated this
)

/**
 * Complete AI response (assembled from stream or non-streaming)
 */
@Serializable
data class AIAgentResponse(
    val responseText: String,
    val shouldEscalate: Boolean,
    val escalationReason: String?,
    val suggestedReplies: List<String>,
    val confidence: Float,
    val provider: AIProvider,
    val usage: TokenUsage? = null
)

@Serializable
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/**
 * Conversation context sent to AI
 */
@Serializable
data class ConversationContext(
    val messages: List<ConversationMessage>,
    val sessionId: String,
    val customerId: String? = null,
    val customerName: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class ConversationMessage(
    val role: String,  // "user", "assistant", "system"
    val content: String,
    val timestamp: Long
)

/**
 * Escalation request to be shown to user
 */
data class EscalationRequest(
    val reason: String,
    val aiSummary: String,
    val detectedIntent: String?,
    val sentiment: String  // "positive", "neutral", "negative", "frustrated"
)
```

#### 1.2 AI Agent Repository Interface

**New file:** `shared/src/commonMain/kotlin/com/amazon/connect/chat/ai/AIAgentRepository.kt`

```kotlin
package com.amazon.connect.chat.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for AI agent operations.
 * Supports streaming responses and automatic provider fallback.
 */
interface AIAgentRepository {

    /**
     * Current provider being used (may change on fallback)
     */
    val currentProvider: StateFlow<AIProvider>

    /**
     * Connection/health status
     */
    val isAvailable: StateFlow<Boolean>

    /**
     * Process a user message with streaming response.
     * This is the PRIMARY method - streaming is prioritized.
     *
     * @param userMessage The user's input text
     * @param context Full conversation context
     * @return Flow of streaming chunks, ending with done=true
     */
    fun processMessageStream(
        userMessage: String,
        context: ConversationContext
    ): Flow<AIStreamChunk>

    /**
     * Process a user message without streaming (fallback).
     * Used when streaming fails or is disabled.
     */
    suspend fun processMessage(
        userMessage: String,
        context: ConversationContext
    ): AIAgentResponse

    /**
     * Check if AI service is available.
     * Attempts primary provider first, then fallback.
     */
    suspend fun healthCheck(): HealthCheckResult

    /**
     * Generate a conversation summary for handover context.
     */
    suspend fun generateSummary(
        context: ConversationContext
    ): String

    /**
     * Detect customer sentiment from conversation.
     */
    suspend fun analyzeSentiment(
        context: ConversationContext
    ): SentimentResult
}

data class HealthCheckResult(
    val primaryAvailable: Boolean,
    val fallbackAvailable: Boolean,
    val activeProvider: AIProvider
)

data class SentimentResult(
    val sentiment: String,  // positive, neutral, negative, frustrated
    val confidence: Float,
    val indicators: List<String>
)
```

#### 1.3 Streaming AI Client Implementation

**New file:** `shared/src/commonMain/kotlin/com/amazon/connect/chat/ai/AIAgentRepositoryImpl.kt`

```kotlin
package com.amazon.connect.chat.ai

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

/**
 * Implementation of AIAgentRepository with streaming support
 * and automatic Claude → OpenAI fallback.
 */
class AIAgentRepositoryImpl(
    private val config: AIAgentConfig,
    private val httpClient: HttpClient = createDefaultClient()
) : AIAgentRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _currentProvider = MutableStateFlow(config.primaryProvider)
    override val currentProvider: StateFlow<AIProvider> = _currentProvider.asStateFlow()

    private val _isAvailable = MutableStateFlow(true)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    /**
     * Stream AI response with automatic fallback
     */
    override fun processMessageStream(
        userMessage: String,
        context: ConversationContext
    ): Flow<AIStreamChunk> = flow {
        try {
            // Try primary provider (Claude)
            emitAll(streamFromProvider(config.primaryProvider, userMessage, context))
        } catch (e: Exception) {
            // Emit error notification
            emit(AIStreamChunk(
                error = "Primary provider failed, switching to fallback..."
            ))

            // Try fallback provider (OpenAI)
            _currentProvider.value = config.fallbackProvider
            try {
                emitAll(streamFromProvider(config.fallbackProvider, userMessage, context))
            } catch (fallbackError: Exception) {
                _isAvailable.value = false
                emit(AIStreamChunk(
                    error = "AI service unavailable. Please try again later.",
                    done = true
                ))
            }
        }
    }

    private fun streamFromProvider(
        provider: AIProvider,
        userMessage: String,
        context: ConversationContext
    ): Flow<AIStreamChunk> = flow {
        val requestBody = buildRequestBody(provider, userMessage, context)

        httpClient.preparePost("${config.proxyBaseUrl}/api/v1/chat/stream") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
            header("X-Provider", provider.name.lowercase())
        }.execute { response ->
            val channel = response.bodyAsChannel()
            val buffer = StringBuilder()

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break

                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        // Parse final metadata from buffer if present
                        emit(AIStreamChunk(done = true, provider = provider))
                        break
                    }

                    try {
                        val chunk = json.decodeFromString<AIStreamChunk>(data)
                        emit(chunk.copy(provider = provider))
                    } catch (e: Exception) {
                        // Treat as raw text chunk
                        emit(AIStreamChunk(delta = data, provider = provider))
                    }
                }
            }
        }
    }

    override suspend fun processMessage(
        userMessage: String,
        context: ConversationContext
    ): AIAgentResponse {
        // Collect stream into complete response
        val buffer = StringBuilder()
        var finalChunk: AIStreamChunk? = null

        processMessageStream(userMessage, context).collect { chunk ->
            chunk.delta?.let { buffer.append(it) }
            if (chunk.done) finalChunk = chunk
        }

        return AIAgentResponse(
            responseText = buffer.toString(),
            shouldEscalate = finalChunk?.shouldEscalate ?: false,
            escalationReason = finalChunk?.escalationReason,
            suggestedReplies = finalChunk?.suggestedReplies ?: emptyList(),
            confidence = 0.85f, // Default confidence
            provider = finalChunk?.provider ?: config.primaryProvider
        )
    }

    override suspend fun healthCheck(): HealthCheckResult {
        val primaryOk = checkProvider(config.primaryProvider)
        val fallbackOk = checkProvider(config.fallbackProvider)

        val active = when {
            primaryOk -> config.primaryProvider
            fallbackOk -> config.fallbackProvider
            else -> config.primaryProvider
        }

        _isAvailable.value = primaryOk || fallbackOk
        _currentProvider.value = active

        return HealthCheckResult(primaryOk, fallbackOk, active)
    }

    override suspend fun generateSummary(context: ConversationContext): String {
        // Use non-streaming call for summary generation
        val response = httpClient.post("${config.proxyBaseUrl}/api/v1/summarize") {
            contentType(ContentType.Application.Json)
            setBody(mapOf(
                "messages" to context.messages,
                "provider" to _currentProvider.value.name.lowercase()
            ))
        }
        return response.bodyAsText()
    }

    override suspend fun analyzeSentiment(context: ConversationContext): SentimentResult {
        val response = httpClient.post("${config.proxyBaseUrl}/api/v1/sentiment") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("messages" to context.messages))
        }
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun checkProvider(provider: AIProvider): Boolean {
        return try {
            val response = httpClient.get("${config.proxyBaseUrl}/api/v1/health") {
                header("X-Provider", provider.name.lowercase())
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    private fun buildRequestBody(
        provider: AIProvider,
        userMessage: String,
        context: ConversationContext
    ): Map<String, Any> = mapOf(
        "provider" to provider.name.lowercase(),
        "messages" to buildMessageList(context, userMessage),
        "stream" to config.streamingEnabled,
        "max_tokens" to config.maxTokens,
        "temperature" to config.temperature,
        "system_prompt" to config.systemPrompt,
        "session_id" to context.sessionId
    )

    private fun buildMessageList(
        context: ConversationContext,
        newMessage: String
    ): List<Map<String, String>> {
        val messages = context.messages.map { msg ->
            mapOf("role" to msg.role, "content" to msg.content)
        }.toMutableList()

        messages.add(mapOf("role" to "user", "content" to newMessage))
        return messages
    }

    companion object {
        fun createDefaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(SSE)
        }
    }
}
```

---

### Phase 2: Serverless Backend Infrastructure (3-4 days)

**Objective:** Deploy a new serverless backend optimized for streaming, with clean integration points for future infrastructure.

#### 2.1 Architecture Decisions

| Requirement | Solution | Rationale |
|-------------|----------|-----------|
| Streaming support | Lambda Function URL + Response Streaming | Native SSE support, no API Gateway timeout limits |
| API key security | AWS Secrets Manager | Automatic rotation, IAM-based access |
| Provider abstraction | Multi-endpoint Lambda | Single deployment, provider routing |
| Future integration | VPC-ready, EventBridge hooks | Easy to connect to existing services |
| Monitoring | CloudWatch + X-Ray | Full observability from day 1 |

#### 2.2 Infrastructure as Code (AWS SAM)

**New file:** `backend/template.yaml`

```yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31
Description: AI Virtual Agent Backend - Streaming Proxy

Parameters:
  Environment:
    Type: String
    Default: dev
    AllowedValues: [dev, staging, prod]

  # Future integration parameters
  ExistingVpcId:
    Type: String
    Default: ""
    Description: Optional VPC ID for future integration

  ExistingSubnetIds:
    Type: CommaDelimitedList
    Default: ""
    Description: Optional subnet IDs for VPC integration

Globals:
  Function:
    Timeout: 120
    Runtime: nodejs20.x
    MemorySize: 512
    Environment:
      Variables:
        ENVIRONMENT: !Ref Environment
        SECRETS_ARN: !Ref AIProviderSecrets

Resources:
  # ============================================
  # SECRETS MANAGEMENT
  # ============================================
  AIProviderSecrets:
    Type: AWS::SecretsManager::Secret
    Properties:
      Name: !Sub "${Environment}/ai-virtual-agent/api-keys"
      Description: API keys for AI providers
      SecretString: |
        {
          "ANTHROPIC_API_KEY": "placeholder-replace-after-deploy",
          "OPENAI_API_KEY": "placeholder-replace-after-deploy"
        }

  # ============================================
  # LAMBDA FUNCTIONS
  # ============================================

  # Main streaming chat function
  AIStreamingFunction:
    Type: AWS::Serverless::Function
    Properties:
      FunctionName: !Sub "${Environment}-ai-agent-stream"
      CodeUri: src/
      Handler: stream.handler
      Description: Streaming AI chat with Claude/OpenAI fallback
      FunctionUrlConfig:
        AuthType: NONE  # Add auth in production
        InvokeMode: RESPONSE_STREAM
        Cors:
          AllowOrigins:
            - "*"
          AllowMethods:
            - POST
            - OPTIONS
          AllowHeaders:
            - Content-Type
            - X-Provider
      Policies:
        - Version: '2012-10-17'
          Statement:
            - Effect: Allow
              Action:
                - secretsmanager:GetSecretValue
              Resource: !Ref AIProviderSecrets
      # Future VPC integration (commented out for initial deployment)
      # VpcConfig:
      #   SubnetIds: !Ref ExistingSubnetIds
      #   SecurityGroupIds:
      #     - !Ref LambdaSecurityGroup

  # Health check function
  HealthCheckFunction:
    Type: AWS::Serverless::Function
    Properties:
      FunctionName: !Sub "${Environment}-ai-agent-health"
      CodeUri: src/
      Handler: health.handler
      Description: Health check for AI providers
      FunctionUrlConfig:
        AuthType: NONE
        Cors:
          AllowOrigins: ["*"]
          AllowMethods: [GET]
      Policies:
        - Version: '2012-10-17'
          Statement:
            - Effect: Allow
              Action: secretsmanager:GetSecretValue
              Resource: !Ref AIProviderSecrets

  # Summary generation function
  SummaryFunction:
    Type: AWS::Serverless::Function
    Properties:
      FunctionName: !Sub "${Environment}-ai-agent-summary"
      CodeUri: src/
      Handler: summary.handler
      Description: Generate conversation summaries for handover
      FunctionUrlConfig:
        AuthType: NONE
        Cors:
          AllowOrigins: ["*"]
          AllowMethods: [POST]
      Policies:
        - Version: '2012-10-17'
          Statement:
            - Effect: Allow
              Action: secretsmanager:GetSecretValue
              Resource: !Ref AIProviderSecrets

  # ============================================
  # MONITORING & LOGGING
  # ============================================

  AIAgentLogGroup:
    Type: AWS::Logs::LogGroup
    Properties:
      LogGroupName: !Sub "/aws/lambda/${Environment}-ai-agent"
      RetentionInDays: 30

  # ============================================
  # FUTURE INTEGRATION RESOURCES (INACTIVE)
  # ============================================

  # EventBridge for analytics pipeline integration
  AIAgentEventBus:
    Type: AWS::Events::EventBus
    Properties:
      Name: !Sub "${Environment}-ai-agent-events"

  # Rule to capture conversation events (for future analytics)
  ConversationEventRule:
    Type: AWS::Events::Rule
    Properties:
      EventBusName: !Ref AIAgentEventBus
      EventPattern:
        source:
          - ai-virtual-agent
        detail-type:
          - ConversationCompleted
          - EscalationTriggered
      State: DISABLED  # Enable when analytics pipeline is ready
      Targets: []

Outputs:
  StreamingEndpoint:
    Description: Streaming chat endpoint URL
    Value: !GetAtt AIStreamingFunctionUrl.FunctionUrl
    Export:
      Name: !Sub "${Environment}-ai-stream-url"

  HealthEndpoint:
    Description: Health check endpoint URL
    Value: !GetAtt HealthCheckFunctionUrl.FunctionUrl

  SummaryEndpoint:
    Description: Summary generation endpoint URL
    Value: !GetAtt SummaryFunctionUrl.FunctionUrl

  EventBusArn:
    Description: EventBridge bus for future analytics integration
    Value: !GetAtt AIAgentEventBus.Arn
    Export:
      Name: !Sub "${Environment}-ai-agent-eventbus"

  SecretsArn:
    Description: Secrets Manager ARN for API keys
    Value: !Ref AIProviderSecrets
```

#### 2.3 Streaming Lambda Handler

**New file:** `backend/src/stream.js`

```javascript
const {
  SecretsManagerClient,
  GetSecretValueCommand
} = require("@aws-sdk/client-secrets-manager");
const Anthropic = require("@anthropic-ai/sdk");
const OpenAI = require("openai");

const secretsClient = new SecretsManagerClient({});
let cachedSecrets = null;

/**
 * Main streaming handler using Lambda Response Streaming
 */
exports.handler = awslambda.streamifyResponse(
  async (event, responseStream, context) => {
    const metadata = {
      statusCode: 200,
      headers: {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache",
        "Connection": "keep-alive",
        "Access-Control-Allow-Origin": "*"
      }
    };

    responseStream = awslambda.HttpResponseStream.from(responseStream, metadata);

    try {
      const body = JSON.parse(event.body);
      const {
        messages,
        provider = "claude",
        system_prompt,
        max_tokens = 1024,
        temperature = 0.7,
        session_id
      } = body;

      const secrets = await getSecrets();

      // Try primary provider, fall back if needed
      try {
        if (provider === "claude") {
          await streamClaude(responseStream, messages, system_prompt, max_tokens, temperature, secrets);
        } else {
          await streamOpenAI(responseStream, messages, system_prompt, max_tokens, temperature, secrets);
        }
      } catch (primaryError) {
        console.error(`Primary provider (${provider}) failed:`, primaryError);

        // Send fallback notification
        responseStream.write(`data: ${JSON.stringify({
          error: "Switching to fallback provider..."
        })}\n\n`);

        // Try fallback
        const fallback = provider === "claude" ? "openai" : "claude";
        if (fallback === "claude") {
          await streamClaude(responseStream, messages, system_prompt, max_tokens, temperature, secrets);
        } else {
          await streamOpenAI(responseStream, messages, system_prompt, max_tokens, temperature, secrets);
        }
      }

    } catch (error) {
      console.error("Stream error:", error);
      responseStream.write(`data: ${JSON.stringify({
        error: error.message,
        done: true
      })}\n\n`);
    } finally {
      responseStream.write("data: [DONE]\n\n");
      responseStream.end();
    }
  }
);

/**
 * Stream from Claude API
 */
async function streamClaude(responseStream, messages, systemPrompt, maxTokens, temperature, secrets) {
  const anthropic = new Anthropic({
    apiKey: secrets.ANTHROPIC_API_KEY
  });

  const response = await anthropic.messages.stream({
    model: "claude-sonnet-4-20250514",
    max_tokens: maxTokens,
    temperature: temperature,
    system: buildSystemPrompt(systemPrompt),
    messages: formatMessagesForClaude(messages)
  });

  let fullResponse = "";
  let shouldEscalate = false;
  let escalationReason = null;

  for await (const event of response) {
    if (event.type === "content_block_delta" && event.delta.type === "text_delta") {
      const text = event.delta.text;
      fullResponse += text;

      // Check for escalation markers in response
      if (text.includes("[ESCALATE:") || fullResponse.includes("[ESCALATE:")) {
        shouldEscalate = true;
        const match = fullResponse.match(/\[ESCALATE:\s*([^\]]+)\]/);
        if (match) escalationReason = match[1];
      }

      responseStream.write(`data: ${JSON.stringify({
        delta: text,
        provider: "claude"
      })}\n\n`);
    }
  }

  // Send final metadata
  const suggestedReplies = extractSuggestedReplies(fullResponse);
  responseStream.write(`data: ${JSON.stringify({
    done: true,
    shouldEscalate,
    escalationReason,
    suggestedReplies,
    provider: "claude"
  })}\n\n`);
}

/**
 * Stream from OpenAI API
 */
async function streamOpenAI(responseStream, messages, systemPrompt, maxTokens, temperature, secrets) {
  const openai = new OpenAI({
    apiKey: secrets.OPENAI_API_KEY
  });

  const response = await openai.chat.completions.create({
    model: "gpt-4-turbo-preview",
    max_tokens: maxTokens,
    temperature: temperature,
    stream: true,
    messages: [
      { role: "system", content: buildSystemPrompt(systemPrompt) },
      ...formatMessagesForOpenAI(messages)
    ]
  });

  let fullResponse = "";
  let shouldEscalate = false;
  let escalationReason = null;

  for await (const chunk of response) {
    const text = chunk.choices[0]?.delta?.content || "";
    if (text) {
      fullResponse += text;

      if (text.includes("[ESCALATE:") || fullResponse.includes("[ESCALATE:")) {
        shouldEscalate = true;
        const match = fullResponse.match(/\[ESCALATE:\s*([^\]]+)\]/);
        if (match) escalationReason = match[1];
      }

      responseStream.write(`data: ${JSON.stringify({
        delta: text,
        provider: "openai"
      })}\n\n`);
    }
  }

  const suggestedReplies = extractSuggestedReplies(fullResponse);
  responseStream.write(`data: ${JSON.stringify({
    done: true,
    shouldEscalate,
    escalationReason,
    suggestedReplies,
    provider: "openai"
  })}\n\n`);
}

/**
 * Build system prompt with escalation instructions
 */
function buildSystemPrompt(customPrompt) {
  const escalationInstructions = `

ESCALATION PROTOCOL:
When you determine that the user should speak with a human agent, include exactly this marker in your response:
[ESCALATE: brief reason]

Recommend escalation when:
- User explicitly asks for a human ("I want to talk to a person", "get me an agent")
- Issue requires account-level access you don't have
- User expresses significant frustration (but first acknowledge their feelings)
- Complex billing disputes or refunds
- Legal or compliance questions
- You cannot confidently help after 2-3 attempts

Always be helpful and empathetic. If recommending escalation, explain why a human would be better suited to help.

SUGGESTED REPLIES:
At the end of your response, you may suggest 2-3 quick reply options for the user by including:
[QUICK_REPLIES: option1 | option2 | option3]
`;

  return (customPrompt || "You are a helpful customer service assistant.") + escalationInstructions;
}

function formatMessagesForClaude(messages) {
  return messages.map(m => ({
    role: m.role === "assistant" ? "assistant" : "user",
    content: m.content
  }));
}

function formatMessagesForOpenAI(messages) {
  return messages.map(m => ({
    role: m.role,
    content: m.content
  }));
}

function extractSuggestedReplies(response) {
  const match = response.match(/\[QUICK_REPLIES:\s*([^\]]+)\]/);
  if (match) {
    return match[1].split("|").map(s => s.trim()).filter(s => s.length > 0);
  }
  return [];
}

async function getSecrets() {
  if (cachedSecrets) return cachedSecrets;

  const command = new GetSecretValueCommand({
    SecretId: process.env.SECRETS_ARN
  });

  const response = await secretsClient.send(command);
  cachedSecrets = JSON.parse(response.SecretString);
  return cachedSecrets;
}
```

#### 2.4 Deployment Script

**New file:** `backend/deploy.sh`

```bash
#!/bin/bash
set -e

ENVIRONMENT=${1:-dev}
REGION=${2:-us-east-1}
STACK_NAME="ai-virtual-agent-${ENVIRONMENT}"

echo "Deploying AI Virtual Agent Backend to ${ENVIRONMENT}..."

# Build
sam build

# Deploy
sam deploy \
  --stack-name ${STACK_NAME} \
  --region ${REGION} \
  --parameter-overrides Environment=${ENVIRONMENT} \
  --capabilities CAPABILITY_IAM \
  --resolve-s3 \
  --no-confirm-changeset

# Get outputs
echo ""
echo "=== Deployment Complete ==="
aws cloudformation describe-stacks \
  --stack-name ${STACK_NAME} \
  --query 'Stacks[0].Outputs' \
  --output table

echo ""
echo "IMPORTANT: Update API keys in Secrets Manager:"
echo "aws secretsmanager put-secret-value --secret-id ${ENVIRONMENT}/ai-virtual-agent/api-keys --secret-string '{\"ANTHROPIC_API_KEY\":\"your-key\",\"OPENAI_API_KEY\":\"your-key\"}'"
```

---

### Phase 3: State Management Integration (2-3 days)

**Objective:** Integrate streaming AI responses into the Redux-style store.

#### 3.1 Enhanced Actions

**Modify:** `shared/src/commonMain/kotlin/com/amazon/connect/chat/store/Reducer.kt`

```kotlin
// Add these new actions to the sealed interface
sealed interface Action {
    // ... existing actions ...

    // === AI AGENT ACTIONS ===

    /** Start AI processing (shows typing indicator) */
    object AIProcessingStarted : Action

    /** Streaming chunk received */
    data class AIStreamChunk(val chunk: String) : Action

    /** AI response complete */
    data class AIResponseComplete(
        val suggestedReplies: List<String>,
        val shouldEscalate: Boolean,
        val escalationReason: String?
    ) : Action

    /** AI processing error */
    data class AIError(val error: String) : Action

    /** User requested escalation confirmation */
    data class ShowEscalationDialog(val reason: String) : Action

    /** User responded to escalation prompt */
    data class EscalationResponse(val confirmed: Boolean) : Action

    /** Clear AI stream buffer (after message is finalized) */
    object ClearAIBuffer : Action

    /** Update suggested quick replies */
    data class SetQuickReplies(val replies: List<String>) : Action

    /** AI provider changed (e.g., fallback triggered) */
    data class AIProviderChanged(val provider: String) : Action
}
```

#### 3.2 Enhanced State

```kotlin
data class ChatState(
    // Existing fields
    val messages: List<Message> = emptyList(),
    val chatMode: ChatMode = ChatMode.VIRTUAL_AGENT,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val currentUser: User? = null,
    val agentUser: User? = null,
    val virtualAgentUser: User = virtualAgentUser,
    val isAgentTyping: Boolean = false,
    val error: String? = null,
    val chatSession: ChatSession? = null,

    // === NEW AI FIELDS ===

    /** AI is currently processing/streaming */
    val isAIProcessing: Boolean = false,

    /** Buffer for streaming AI response */
    val aiStreamBuffer: String = "",

    /** Suggested quick replies from AI */
    val suggestedReplies: List<String> = emptyList(),

    /** Show escalation confirmation dialog */
    val showEscalationDialog: Boolean = false,

    /** Reason for escalation (shown in dialog) */
    val escalationReason: String? = null,

    /** Current AI provider being used */
    val currentAIProvider: String = "claude"
)
```

#### 3.3 Reducer Updates

```kotlin
fun reduce(state: ChatState, action: Action): ChatState = when (action) {
    // ... existing cases ...

    is Action.AIProcessingStarted -> state.copy(
        isAIProcessing = true,
        aiStreamBuffer = "",
        suggestedReplies = emptyList(),
        error = null
    )

    is Action.AIStreamChunk -> state.copy(
        aiStreamBuffer = state.aiStreamBuffer + action.chunk
    )

    is Action.AIResponseComplete -> {
        // Finalize the AI message
        val aiMessage = Message(
            user = state.virtualAgentUser,
            text = cleanAIResponse(state.aiStreamBuffer)
        )

        state.copy(
            messages = state.messages + aiMessage,
            isAIProcessing = false,
            aiStreamBuffer = "",
            suggestedReplies = action.suggestedReplies,
            showEscalationDialog = action.shouldEscalate,
            escalationReason = action.escalationReason
        )
    }

    is Action.AIError -> state.copy(
        isAIProcessing = false,
        error = action.error
    )

    is Action.ShowEscalationDialog -> state.copy(
        showEscalationDialog = true,
        escalationReason = action.reason
    )

    is Action.EscalationResponse -> {
        if (action.confirmed) {
            state.copy(
                showEscalationDialog = false,
                chatMode = ChatMode.CONNECTING_TO_AGENT
            )
        } else {
            state.copy(
                showEscalationDialog = false,
                escalationReason = null
            )
        }
    }

    is Action.ClearAIBuffer -> state.copy(
        aiStreamBuffer = ""
    )

    is Action.SetQuickReplies -> state.copy(
        suggestedReplies = action.replies
    )

    is Action.AIProviderChanged -> state.copy(
        currentAIProvider = action.provider
    )

    // ... existing cases ...
}

/** Remove escalation markers and quick reply markers from display text */
private fun cleanAIResponse(text: String): String {
    return text
        .replace(Regex("\\[ESCALATE:[^\\]]*\\]"), "")
        .replace(Regex("\\[QUICK_REPLIES:[^\\]]*\\]"), "")
        .trim()
}
```

---

### Phase 4: UI Enhancements (3-4 days)

**Objective:** Build streaming UI components and escalation dialog.

#### 4.1 Streaming Message Display

**Modify:** `shared/src/commonMain/kotlin/com/amazon/connect/chat/ui/ChatApp.kt`

Add streaming message bubble and quick replies:

```kotlin
@Composable
fun ChatApp(
    store: Store,
    connectRepository: ConnectChatRepository,
    aiRepository: AIAgentRepository,  // NEW parameter
    modifier: Modifier = Modifier,
    showSendMessage: Boolean = true
) {
    val state by store.stateFlow.collectAsState()
    val scope = rememberCoroutineScope()

    // ... existing LaunchedEffects ...

    Surface(
        modifier = modifier.fillMaxSize(),
        color = ChatColors.SURFACE
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Messages area
            Messages(
                messages = state.messages,
                currentUser = state.currentUser,
                modifier = Modifier.weight(1f)
            )

            // === NEW: Streaming AI response ===
            if (state.isAIProcessing && state.aiStreamBuffer.isNotEmpty()) {
                StreamingMessageBubble(
                    text = state.aiStreamBuffer,
                    isComplete = false
                )
            }

            // Typing indicator (AI or agent)
            if (state.isAIProcessing && state.aiStreamBuffer.isEmpty()) {
                TypingIndicator(userName = "Virtual Assistant")
            } else if (state.isAgentTyping) {
                TypingIndicator(userName = state.agentUser?.name ?: "Agent")
            }

            // === NEW: Quick reply chips ===
            if (state.suggestedReplies.isNotEmpty() && !state.isAIProcessing) {
                QuickReplies(
                    replies = state.suggestedReplies,
                    onReplyClick = { reply ->
                        val user = state.currentUser ?: return@QuickReplies
                        val message = Message(user, reply)
                        store.send(Action.SendMessage(message))
                        store.send(Action.SetQuickReplies(emptyList()))

                        // Process with AI
                        if (state.chatMode == ChatMode.VIRTUAL_AGENT) {
                            processWithAI(store, aiRepository, reply, state, scope)
                        }
                    }
                )
            }

            // Connection status banner
            ConnectionBanner(state)

            // Send message input
            if (showSendMessage && state.chatMode != ChatMode.ENDED) {
                SendMessage(
                    onSendMessage = { text ->
                        val user = state.currentUser ?: return@SendMessage
                        val message = Message(user, text)
                        store.send(Action.SendMessage(message))

                        // Process with AI if in virtual agent mode
                        if (state.chatMode == ChatMode.VIRTUAL_AGENT) {
                            processWithAI(store, aiRepository, text, state, scope)
                        } else if (state.chatMode == ChatMode.HUMAN_AGENT) {
                            // Send to Connect
                            scope.launch {
                                try {
                                    connectRepository.sendMessage(text)
                                } catch (e: Exception) {
                                    store.send(Action.SetError("Failed to send: ${e.message}"))
                                }
                            }
                        }
                    },
                    onTyping = { /* ... existing code ... */ },
                    enabled = state.chatMode != ChatMode.CONNECTING_TO_AGENT && !state.isAIProcessing
                )
            }
        }
    }

    // === NEW: Escalation confirmation dialog ===
    if (state.showEscalationDialog) {
        EscalationConfirmationDialog(
            reason = state.escalationReason ?: "I think a human agent could help you better.",
            onConfirm = {
                store.send(Action.EscalationResponse(confirmed = true))
                // Trigger handover
                scope.launch {
                    initiateHandover(store, connectRepository, aiRepository, state)
                }
            },
            onDismiss = {
                store.send(Action.EscalationResponse(confirmed = false))
            }
        )
    }
}

/**
 * Process user message with AI (streaming)
 */
private fun processWithAI(
    store: Store,
    aiRepository: AIAgentRepository,
    userMessage: String,
    state: ChatState,
    scope: CoroutineScope
) {
    scope.launch {
        store.send(Action.AIProcessingStarted)

        val context = ConversationContext(
            messages = state.messages.map { msg ->
                ConversationMessage(
                    role = if (msg.user.role == ParticipantRole.CUSTOMER) "user" else "assistant",
                    content = msg.text,
                    timestamp = msg.timeMs
                )
            },
            sessionId = state.chatSession?.contactId ?: "local-${System.currentTimeMillis()}"
        )

        try {
            aiRepository.processMessageStream(userMessage, context)
                .collect { chunk ->
                    chunk.delta?.let {
                        store.send(Action.AIStreamChunk(it))
                    }

                    chunk.error?.let {
                        store.send(Action.AIError(it))
                    }

                    if (chunk.done) {
                        store.send(Action.AIResponseComplete(
                            suggestedReplies = chunk.suggestedReplies ?: emptyList(),
                            shouldEscalate = chunk.shouldEscalate,
                            escalationReason = chunk.escalationReason
                        ))
                    }

                    chunk.provider?.let {
                        store.send(Action.AIProviderChanged(it.name))
                    }
                }
        } catch (e: Exception) {
            store.send(Action.AIError("Failed to process message: ${e.message}"))
        }
    }
}
```

#### 4.2 New UI Components

**New file:** `shared/src/commonMain/kotlin/com/amazon/connect/chat/ui/AIComponents.kt`

```kotlin
package com.amazon.connect.chat.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amazon.connect.chat.data.ChatColors
import com.amazon.connect.chat.data.Message
import com.amazon.connect.chat.data.virtualAgentUser

/**
 * Streaming message bubble with cursor animation
 */
@Composable
fun StreamingMessageBubble(
    text: String,
    isComplete: Boolean,
    modifier: Modifier = Modifier
) {
    // Blinking cursor animation
    val infiniteTransition = rememberInfiniteTransition()
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        )
    )

    val displayText = if (isComplete) text else "$text▋"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = ChatColors.VA_BUBBLE,
            elevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.body1,
                    color = ChatColors.TEXT_PRIMARY
                )
                if (!isComplete) {
                    Text(
                        text = "▋",
                        modifier = Modifier.alpha(cursorAlpha),
                        style = MaterialTheme.typography.body1,
                        color = ChatColors.PRIMARY
                    )
                }
            }
        }
    }
}

/**
 * Quick reply chips
 */
@Composable
fun QuickReplies(
    replies: List<String>,
    onReplyClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(replies) { reply ->
            QuickReplyChip(
                text = reply,
                onClick = { onReplyClick(reply) }
            )
        }
    }
}

@Composable
private fun QuickReplyChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = ChatColors.PRIMARY.copy(alpha = 0.1f),
        border = ButtonDefaults.outlinedBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(ChatColors.PRIMARY)
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.body2,
            color = ChatColors.PRIMARY
        )
    }
}

/**
 * Escalation confirmation dialog
 */
@Composable
fun EscalationConfirmationDialog(
    reason: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Connect to Live Agent?",
                style = MaterialTheme.typography.h6
            )
        },
        text = {
            Column {
                Text(
                    text = reason,
                    style = MaterialTheme.typography.body1
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Would you like to speak with a live agent?",
                    style = MaterialTheme.typography.body2,
                    color = ChatColors.TEXT_SECONDARY
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ChatColors.PRIMARY
                )
            ) {
                Text("Yes, connect me", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("No, continue with assistant")
            }
        },
        backgroundColor = ChatColors.SURFACE,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * AI provider indicator (shown when fallback is used)
 */
@Composable
fun AIProviderBadge(
    provider: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = ChatColors.TEXT_SECONDARY.copy(alpha = 0.1f)
    ) {
        Text(
            text = "Powered by $provider",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.caption,
            color = ChatColors.TEXT_SECONDARY
        )
    }
}
```

---

### Phase 5: Escalation with User Confirmation (2-3 days)

**Objective:** Implement user-confirmed escalation flow.

#### 5.1 Escalation Flow (User Always Confirms)

```
AI detects escalation trigger
           │
           ▼
┌──────────────────────────────┐
│ AI includes [ESCALATE: ...]  │
│ marker in response           │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│ Backend extracts marker      │
│ Sets shouldEscalate = true   │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│ App shows confirmation       │
│ dialog to user               │
└──────────────┬───────────────┘
               │
       ┌───────┴───────┐
       │               │
       ▼               ▼
┌─────────────┐  ┌─────────────┐
│ User clicks │  │ User clicks │
│ "Yes"       │  │ "No"        │
└──────┬──────┘  └──────┬──────┘
       │                │
       ▼                ▼
┌─────────────┐  ┌─────────────┐
│ Handover    │  │ Continue    │
│ to Connect  │  │ with AI     │
│ initiated   │  │             │
└─────────────┘  └─────────────┘
```

#### 5.2 Enhanced Handover Context

When escalating, include AI-generated context:

```kotlin
private suspend fun initiateHandover(
    store: Store,
    connectRepository: ConnectChatRepository,
    aiRepository: AIAgentRepository,
    state: ChatState
) {
    val context = buildConversationContext(state)

    // Generate AI summary for agent
    val summary = try {
        aiRepository.generateSummary(context)
    } catch (e: Exception) {
        "Unable to generate summary"
    }

    // Analyze sentiment
    val sentiment = try {
        aiRepository.analyzeSentiment(context)
    } catch (e: Exception) {
        SentimentResult("neutral", 0.5f, emptyList())
    }

    val handoverContext = HandoverContext(
        customerId = state.currentUser?.name ?: "Unknown",
        customerName = state.currentUser?.name ?: "Customer",
        intent = state.escalationReason ?: "General inquiry",
        summary = summary,
        transcript = state.messages.map { msg ->
            TranscriptEntry(
                participantRole = msg.user.role.name,
                content = msg.text,
                timestamp = msg.timeMs
            )
        },
        metadata = mapOf(
            "aiProvider" to state.currentAIProvider,
            "escalationReason" to (state.escalationReason ?: "User requested"),
            "customerSentiment" to sentiment.sentiment,
            "sentimentConfidence" to sentiment.confidence.toString()
        )
    )

    try {
        connectRepository.startHandover(
            authApiUrl = AUTH_API_URL,
            context = handoverContext
        )
    } catch (e: Exception) {
        store.send(Action.SetError("Failed to connect to agent: ${e.message}"))
        store.send(Action.EscalationResponse(confirmed = false))
    }
}
```

---

### Phase 6: Configuration & Platform Integration (2 days)

**Objective:** Make AI agent configurable and integrate across all platforms.

#### 6.1 Configuration File

**New file:** `shared/src/commonMain/resources/ai_agent_config.json`

```json
{
  "providers": {
    "primary": "claude",
    "fallback": "openai",
    "models": {
      "claude": "claude-sonnet-4-20250514",
      "openai": "gpt-4-turbo-preview"
    }
  },
  "streaming": {
    "enabled": true,
    "chunkBufferMs": 50
  },
  "escalation": {
    "confirmWithUser": true,
    "keywords": [
      "human", "agent", "representative", "manager",
      "supervisor", "real person", "speak to someone",
      "talk to a person"
    ],
    "confidenceThreshold": 0.6,
    "sentimentThreshold": -0.5
  },
  "quickReplies": {
    "enabled": true,
    "maxCount": 3
  },
  "systemPrompt": "You are a helpful customer service assistant for our company. Be friendly, concise, and helpful. If you cannot help with something, politely offer to connect the customer with a human agent."
}
```

#### 6.2 Platform Entry Point Updates

Each platform needs to initialize the AI repository:

**Android - MainActivity.kt:**
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val connectRepository = ConnectChatRepositoryImpl()

        // NEW: Initialize AI repository
        val aiRepository = AIAgentRepositoryImpl(
            config = AIAgentConfig(
                proxyBaseUrl = BuildConfig.AI_PROXY_URL,
                systemPrompt = loadSystemPrompt()
            )
        )

        val store = createStore()

        // Add welcome message
        store.send(Action.ReceiveMessage(Message(
            user = virtualAgentUser,
            text = "Hello! I'm your virtual assistant. How can I help you today?"
        )))

        setContent {
            ChatAppWithScaffold(
                store = store,
                connectRepository = connectRepository,
                aiRepository = aiRepository,  // NEW parameter
                onRequestHandover = { /* ... */ }
            )
        }
    }
}
```

Similar changes for Desktop, iOS, and Web entry points.

---

## File Changes Summary

### New Files to Create

| File | Description |
|------|-------------|
| `shared/.../ai/AIAgentModels.kt` | Data models for AI responses, config, context |
| `shared/.../ai/AIAgentRepository.kt` | Repository interface with streaming support |
| `shared/.../ai/AIAgentRepositoryImpl.kt` | Implementation with Claude/OpenAI fallback |
| `shared/.../ui/AIComponents.kt` | Streaming bubble, quick replies, escalation dialog |
| `shared/.../resources/ai_agent_config.json` | Default configuration |
| `backend/template.yaml` | SAM template for serverless infrastructure |
| `backend/src/stream.js` | Lambda streaming handler |
| `backend/src/health.js` | Health check handler |
| `backend/src/summary.js` | Summary generation handler |
| `backend/deploy.sh` | Deployment script |
| `backend/package.json` | Node.js dependencies |

### Files to Modify

| File | Changes |
|------|---------|
| `store/Reducer.kt` | Add AI actions and state fields |
| `ui/ChatApp.kt` | Add streaming UI, AI processing, escalation dialog |
| `ui/SendMessage.kt` | Integrate quick reply interaction |
| `MainActivity.kt` | Initialize AI repository, wire up |
| `Main.kt` (Desktop) | Same initialization |
| `Main.ios.kt` | Same initialization |
| `Main.kt` (Web) | Same initialization |
| `build.gradle.kts` | Add Ktor SSE dependency if needed |

---

## Estimated Timeline (Revised)

| Phase | Duration | Deliverables |
|-------|----------|--------------|
| Phase 1: Core AI Infrastructure | 4-5 days | AI models, streaming repository, dual-provider support |
| Phase 2: Serverless Backend | 3-4 days | Lambda streaming, API Gateway, secrets management |
| Phase 3: State Management | 2-3 days | Redux actions, streaming state |
| Phase 4: UI Enhancements | 3-4 days | Streaming bubble, quick replies, animations |
| Phase 5: User-Confirmed Escalation | 2-3 days | Confirmation dialog, enhanced handover context |
| Phase 6: Configuration | 2 days | Config file, platform integration |
| Testing & Refinement | 3-4 days | Unit, integration, E2E tests |

**Total Estimated Time: 19-25 days**

---

## Next Steps (Upon Approval)

1. **Set up backend infrastructure** - Deploy SAM template, configure API keys
2. **Begin Phase 1** - Implement AI agent models and streaming client
3. **Create test scenarios** - Define conversation flows for testing
4. **Customize system prompt** - Tailor AI personality for your use case

---

**This plan is ready for your final approval to begin implementation.**
