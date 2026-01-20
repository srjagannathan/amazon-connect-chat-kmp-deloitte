/**
 * Streaming AI Chat Handler
 *
 * Provides streaming responses using Claude (primary) and OpenAI (fallback).
 * Uses Lambda Response Streaming for real-time SSE delivery.
 *
 * NOTE: The `awslambda` global is provided by the Lambda runtime when using
 * RESPONSE_STREAM invoke mode. It is NOT imported - it's a global provided
 * by AWS Lambda Web Adapter for response streaming functions.
 */

const {
  SecretsManagerClient,
  GetSecretValueCommand
} = require("@aws-sdk/client-secrets-manager");
const Anthropic = require("@anthropic-ai/sdk");
const OpenAI = require("openai");

const secretsClient = new SecretsManagerClient({});
let cachedSecrets = null;

// Global awslambda is provided by Lambda runtime for RESPONSE_STREAM functions
// This check ensures graceful handling in non-streaming test environments
const streamifyResponse = typeof awslambda !== 'undefined'
  ? awslambda.streamifyResponse
  : (handler) => handler;
const HttpResponseStream = typeof awslambda !== 'undefined'
  ? awslambda.HttpResponseStream
  : { from: (stream, meta) => stream };

// System prompt with escalation and quick reply instructions
const ESCALATION_INSTRUCTIONS = `

ESCALATION PROTOCOL:
When you determine that the user should speak with a human agent, include exactly this marker in your response:
[ESCALATE: brief reason for escalation]

Recommend escalation when:
- User explicitly asks for a human ("I want to talk to a person", "get me an agent", "live agent")
- Issue requires account-level access or sensitive operations you cannot perform
- User expresses significant frustration after multiple attempts
- Complex billing disputes, refunds, or account changes
- Legal, medical, or compliance questions
- You cannot confidently help after 2-3 attempts on the same issue

Always be helpful and empathetic. If recommending escalation, explain why a human would be better suited to help.

SUGGESTED REPLIES:
At the end of your response, suggest 2-3 quick reply options for the user by including:
[QUICK_REPLIES: option1 | option2 | option3]

Keep quick replies short (2-5 words each) and relevant to the conversation.
`;

/**
 * Main streaming handler using Lambda Response Streaming
 */
exports.handler = streamifyResponse(
  async (event, responseStream, context) => {
    const metadata = {
      statusCode: 200,
      headers: {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache",
        "Connection": "keep-alive",
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "Content-Type, X-Provider, X-Session-Id"
      }
    };

    // Handle OPTIONS preflight
    if (event.requestContext?.http?.method === "OPTIONS") {
      responseStream = HttpResponseStream.from(responseStream, {
        statusCode: 200,
        headers: metadata.headers
      });
      responseStream.end();
      return;
    }

    responseStream = HttpResponseStream.from(responseStream, metadata);

    try {
      const body = JSON.parse(event.body || "{}");
      const {
        messages = [],
        provider = "claude",
        systemPrompt = "You are a helpful customer service assistant.",
        maxTokens = 1024,
        temperature = 0.7,
        sessionId = "unknown"
      } = body;

      console.log(`[${sessionId}] Starting stream with provider: ${provider}`);

      const secrets = await getSecrets();

      // Try primary provider, fall back if needed
      try {
        if (provider === "claude") {
          await streamClaude(responseStream, messages, systemPrompt, maxTokens, temperature, secrets, sessionId);
        } else {
          await streamOpenAI(responseStream, messages, systemPrompt, maxTokens, temperature, secrets, sessionId);
        }
      } catch (primaryError) {
        console.error(`[${sessionId}] Primary provider (${provider}) failed:`, primaryError.message);

        // Send fallback notification
        writeSSE(responseStream, {
          error: "Primary provider unavailable, switching to fallback...",
          provider: provider
        });

        // Try fallback
        const fallbackProvider = provider === "claude" ? "openai" : "claude";
        console.log(`[${sessionId}] Attempting fallback to ${fallbackProvider}`);

        try {
          if (fallbackProvider === "claude") {
            await streamClaude(responseStream, messages, systemPrompt, maxTokens, temperature, secrets, sessionId);
          } else {
            await streamOpenAI(responseStream, messages, systemPrompt, maxTokens, temperature, secrets, sessionId);
          }
        } catch (fallbackError) {
          console.error(`[${sessionId}] Fallback provider failed:`, fallbackError.message);
          writeSSE(responseStream, {
            error: "AI service unavailable. Please try again later.",
            done: true
          });
        }
      }

    } catch (error) {
      console.error("Stream error:", error);
      writeSSE(responseStream, {
        error: error.message || "An unexpected error occurred",
        done: true
      });
    } finally {
      responseStream.write("data: [DONE]\n\n");
      responseStream.end();
    }
  }
);

/**
 * Stream from Claude API
 */
async function streamClaude(responseStream, messages, systemPrompt, maxTokens, temperature, secrets, sessionId) {
  const anthropic = new Anthropic({
    apiKey: secrets.ANTHROPIC_API_KEY
  });

  const fullSystemPrompt = systemPrompt + ESCALATION_INSTRUCTIONS;

  const stream = await anthropic.messages.stream({
    model: "claude-sonnet-4-20250514",
    max_tokens: maxTokens,
    temperature: temperature,
    system: fullSystemPrompt,
    messages: formatMessagesForClaude(messages)
  });

  let fullResponse = "";
  let shouldEscalate = false;
  let escalationReason = null;

  for await (const event of stream) {
    if (event.type === "content_block_delta" && event.delta?.type === "text_delta") {
      const text = event.delta.text;
      fullResponse += text;

      // Check for escalation markers
      const escalateMatch = fullResponse.match(/\[ESCALATE:\s*([^\]]+)\]/);
      if (escalateMatch) {
        shouldEscalate = true;
        escalationReason = escalateMatch[1].trim();
      }

      writeSSE(responseStream, {
        delta: text,
        provider: "claude"
      });
    }
  }

  // Send final metadata
  const suggestedReplies = extractSuggestedReplies(fullResponse);

  console.log(`[${sessionId}] Claude response complete. Escalate: ${shouldEscalate}, Replies: ${suggestedReplies.length}`);

  writeSSE(responseStream, {
    done: true,
    shouldEscalate,
    escalationReason: shouldEscalate ? escalationReason : null,
    suggestedReplies,
    provider: "claude"
  });
}

/**
 * Stream from OpenAI API
 */
async function streamOpenAI(responseStream, messages, systemPrompt, maxTokens, temperature, secrets, sessionId) {
  const openai = new OpenAI({
    apiKey: secrets.OPENAI_API_KEY
  });

  const fullSystemPrompt = systemPrompt + ESCALATION_INSTRUCTIONS;

  const stream = await openai.chat.completions.create({
    model: "gpt-4-turbo-preview",
    max_tokens: maxTokens,
    temperature: temperature,
    stream: true,
    messages: [
      { role: "system", content: fullSystemPrompt },
      ...formatMessagesForOpenAI(messages)
    ]
  });

  let fullResponse = "";
  let shouldEscalate = false;
  let escalationReason = null;

  for await (const chunk of stream) {
    const text = chunk.choices[0]?.delta?.content || "";
    if (text) {
      fullResponse += text;

      // Check for escalation markers
      const escalateMatch = fullResponse.match(/\[ESCALATE:\s*([^\]]+)\]/);
      if (escalateMatch) {
        shouldEscalate = true;
        escalationReason = escalateMatch[1].trim();
      }

      writeSSE(responseStream, {
        delta: text,
        provider: "openai"
      });
    }
  }

  // Send final metadata
  const suggestedReplies = extractSuggestedReplies(fullResponse);

  console.log(`[${sessionId}] OpenAI response complete. Escalate: ${shouldEscalate}, Replies: ${suggestedReplies.length}`);

  writeSSE(responseStream, {
    done: true,
    shouldEscalate,
    escalationReason: shouldEscalate ? escalationReason : null,
    suggestedReplies,
    provider: "openai"
  });
}

/**
 * Format messages for Claude API (user/assistant only)
 */
function formatMessagesForClaude(messages) {
  return messages
    .filter(m => m.role === "user" || m.role === "assistant")
    .map(m => ({
      role: m.role,
      content: m.content
    }));
}

/**
 * Format messages for OpenAI API
 */
function formatMessagesForOpenAI(messages) {
  return messages.map(m => ({
    role: m.role,
    content: m.content
  }));
}

/**
 * Extract suggested quick replies from response
 */
function extractSuggestedReplies(response) {
  const match = response.match(/\[QUICK_REPLIES:\s*([^\]]+)\]/);
  if (match) {
    return match[1]
      .split("|")
      .map(s => s.trim())
      .filter(s => s.length > 0 && s.length < 50);
  }
  return [];
}

/**
 * Write SSE formatted data
 */
function writeSSE(stream, data) {
  stream.write(`data: ${JSON.stringify(data)}\n\n`);
}

/**
 * Get secrets from AWS Secrets Manager (with caching)
 */
async function getSecrets() {
  if (cachedSecrets) return cachedSecrets;

  const command = new GetSecretValueCommand({
    SecretId: process.env.SECRETS_ARN
  });

  const response = await secretsClient.send(command);
  cachedSecrets = JSON.parse(response.SecretString);
  return cachedSecrets;
}
