/**
 * Health Check Handler
 *
 * Verifies connectivity to Claude and OpenAI APIs.
 */

const {
  SecretsManagerClient,
  GetSecretValueCommand
} = require("@aws-sdk/client-secrets-manager");
const Anthropic = require("@anthropic-ai/sdk");
const OpenAI = require("openai");

const secretsClient = new SecretsManagerClient({});
let cachedSecrets = null;

exports.handler = async (event) => {
  const headers = {
    "Content-Type": "application/json",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "X-Provider"
  };

  // Handle OPTIONS preflight
  if (event.requestContext?.http?.method === "OPTIONS") {
    return { statusCode: 200, headers, body: "" };
  }

  const requestedProvider = event.headers?.["x-provider"] || "all";

  try {
    const secrets = await getSecrets();

    const results = {
      timestamp: new Date().toISOString(),
      providers: {}
    };

    // Check Claude
    if (requestedProvider === "all" || requestedProvider === "claude") {
      results.providers.claude = await checkClaude(secrets);
    }

    // Check OpenAI
    if (requestedProvider === "all" || requestedProvider === "openai") {
      results.providers.openai = await checkOpenAI(secrets);
    }

    // Determine overall health
    const anyHealthy = Object.values(results.providers).some(p => p.healthy);

    return {
      statusCode: anyHealthy ? 200 : 503,
      headers,
      body: JSON.stringify(results)
    };

  } catch (error) {
    console.error("Health check error:", error);
    return {
      statusCode: 500,
      headers,
      body: JSON.stringify({
        error: "Health check failed",
        message: error.message
      })
    };
  }
};

/**
 * Check Claude API health
 */
async function checkClaude(secrets) {
  const start = Date.now();
  try {
    const anthropic = new Anthropic({
      apiKey: secrets.ANTHROPIC_API_KEY
    });

    // Simple test call
    await anthropic.messages.create({
      model: "claude-sonnet-4-20250514",
      max_tokens: 10,
      messages: [{ role: "user", content: "Hi" }]
    });

    return {
      healthy: true,
      latencyMs: Date.now() - start,
      model: "claude-sonnet-4-20250514"
    };
  } catch (error) {
    return {
      healthy: false,
      latencyMs: Date.now() - start,
      error: error.message
    };
  }
}

/**
 * Check OpenAI API health
 */
async function checkOpenAI(secrets) {
  const start = Date.now();
  try {
    const openai = new OpenAI({
      apiKey: secrets.OPENAI_API_KEY
    });

    // Simple test call
    await openai.chat.completions.create({
      model: "gpt-4-turbo-preview",
      max_tokens: 10,
      messages: [{ role: "user", content: "Hi" }]
    });

    return {
      healthy: true,
      latencyMs: Date.now() - start,
      model: "gpt-4-turbo-preview"
    };
  } catch (error) {
    return {
      healthy: false,
      latencyMs: Date.now() - start,
      error: error.message
    };
  }
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
