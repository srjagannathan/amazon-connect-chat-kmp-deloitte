/**
 * Summary Generation Handler
 *
 * Generates conversation summaries for agent handover context.
 */

const {
  SecretsManagerClient,
  GetSecretValueCommand
} = require("@aws-sdk/client-secrets-manager");
const Anthropic = require("@anthropic-ai/sdk");
const OpenAI = require("openai");

const secretsClient = new SecretsManagerClient({});
let cachedSecrets = null;

const SUMMARY_PROMPT = `Analyze this customer service conversation and provide a brief summary for the human agent who will take over.

Include:
1. Main issue or request (1 sentence)
2. Key details mentioned (bullet points)
3. Current status/what's been attempted
4. Customer sentiment (positive/neutral/negative/frustrated)

Keep the summary under 150 words. Be factual and helpful for the incoming agent.`;

exports.handler = async (event) => {
  const headers = {
    "Content-Type": "application/json",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type"
  };

  // Handle OPTIONS preflight
  if (event.requestContext?.http?.method === "OPTIONS") {
    return { statusCode: 200, headers, body: "" };
  }

  try {
    const body = JSON.parse(event.body || "{}");
    const { messages = [], provider = "claude" } = body;

    if (messages.length === 0) {
      return {
        statusCode: 400,
        headers,
        body: JSON.stringify({ error: "No messages provided" })
      };
    }

    const secrets = await getSecrets();

    // Format conversation for summary
    const conversation = messages
      .map(m => `${m.role.toUpperCase()}: ${m.content}`)
      .join("\n\n");

    let summary;
    try {
      if (provider === "claude") {
        summary = await summarizeWithClaude(conversation, secrets);
      } else {
        summary = await summarizeWithOpenAI(conversation, secrets);
      }
    } catch (primaryError) {
      console.error(`Primary provider (${provider}) failed:`, primaryError.message);
      // Try fallback
      const fallback = provider === "claude" ? "openai" : "claude";
      if (fallback === "claude") {
        summary = await summarizeWithClaude(conversation, secrets);
      } else {
        summary = await summarizeWithOpenAI(conversation, secrets);
      }
    }

    return {
      statusCode: 200,
      headers,
      body: JSON.stringify({ summary })
    };

  } catch (error) {
    console.error("Summary generation error:", error);
    return {
      statusCode: 500,
      headers,
      body: JSON.stringify({
        error: "Failed to generate summary",
        message: error.message
      })
    };
  }
};

/**
 * Generate summary using Claude
 */
async function summarizeWithClaude(conversation, secrets) {
  const anthropic = new Anthropic({
    apiKey: secrets.ANTHROPIC_API_KEY
  });

  const response = await anthropic.messages.create({
    model: "claude-sonnet-4-20250514",
    max_tokens: 300,
    messages: [{
      role: "user",
      content: `${SUMMARY_PROMPT}\n\nConversation:\n${conversation}`
    }]
  });

  return response.content[0].text;
}

/**
 * Generate summary using OpenAI
 */
async function summarizeWithOpenAI(conversation, secrets) {
  const openai = new OpenAI({
    apiKey: secrets.OPENAI_API_KEY
  });

  const response = await openai.chat.completions.create({
    model: "gpt-4-turbo-preview",
    max_tokens: 300,
    messages: [
      { role: "system", content: SUMMARY_PROMPT },
      { role: "user", content: `Conversation:\n${conversation}` }
    ]
  });

  return response.choices[0].message.content;
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
