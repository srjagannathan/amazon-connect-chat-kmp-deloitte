/**
 * Sentiment Analysis Handler
 *
 * Analyzes customer sentiment from conversation history.
 */

const {
  SecretsManagerClient,
  GetSecretValueCommand
} = require("@aws-sdk/client-secrets-manager");
const Anthropic = require("@anthropic-ai/sdk");

const secretsClient = new SecretsManagerClient({});
let cachedSecrets = null;

const SENTIMENT_PROMPT = `Analyze the customer's sentiment in this conversation. Respond with ONLY a JSON object in this exact format:
{
  "sentiment": "positive" | "neutral" | "negative" | "frustrated",
  "confidence": 0.0-1.0,
  "indicators": ["indicator1", "indicator2"]
}

Sentiment definitions:
- positive: Customer is happy, satisfied, or grateful
- neutral: Customer is calm, factual, no strong emotion
- negative: Customer is unhappy but composed
- frustrated: Customer is very upset, using caps, repeated complaints, or explicit frustration

Indicators are specific words or phrases that influenced your assessment.`;

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
    const { messages = [] } = body;

    if (messages.length === 0) {
      return {
        statusCode: 200,
        headers,
        body: JSON.stringify({
          sentiment: "neutral",
          confidence: 0.5,
          indicators: []
        })
      };
    }

    // Extract only customer messages for sentiment analysis
    const customerMessages = messages
      .filter(m => m.role === "user")
      .map(m => m.content)
      .join("\n\n");

    if (!customerMessages) {
      return {
        statusCode: 200,
        headers,
        body: JSON.stringify({
          sentiment: "neutral",
          confidence: 0.5,
          indicators: []
        })
      };
    }

    const secrets = await getSecrets();
    const result = await analyzeSentiment(customerMessages, secrets);

    return {
      statusCode: 200,
      headers,
      body: JSON.stringify(result)
    };

  } catch (error) {
    console.error("Sentiment analysis error:", error);

    // Return neutral on error rather than failing
    return {
      statusCode: 200,
      headers,
      body: JSON.stringify({
        sentiment: "neutral",
        confidence: 0.5,
        indicators: [],
        error: "Analysis failed, defaulting to neutral"
      })
    };
  }
};

/**
 * Analyze sentiment using Claude
 */
async function analyzeSentiment(customerText, secrets) {
  try {
    const anthropic = new Anthropic({
      apiKey: secrets.ANTHROPIC_API_KEY
    });

    const response = await anthropic.messages.create({
      model: "claude-sonnet-4-20250514",
      max_tokens: 200,
      messages: [{
        role: "user",
        content: `${SENTIMENT_PROMPT}\n\nCustomer messages:\n${customerText}`
      }]
    });

    const text = response.content[0].text;

    // Extract JSON from response with safer parsing
    try {
      // Try to find the last complete JSON object in the response
      const jsonMatch = text.match(/\{[^{}]*\}/g);
      if (jsonMatch && jsonMatch.length > 0) {
        // Use the last match (more likely to be the actual response object)
        return JSON.parse(jsonMatch[jsonMatch.length - 1]);
      }
    } catch (jsonError) {
      console.error("JSON extraction failed:", jsonError.message);
    }

    // Fallback parsing
    return parseBasicSentiment(text);

  } catch (error) {
    console.error("Claude sentiment analysis failed:", error.message);
    // Fall back to basic keyword analysis
    return basicSentimentAnalysis(customerText);
  }
}

/**
 * Parse sentiment from non-JSON response
 */
function parseBasicSentiment(text) {
  const lower = text.toLowerCase();

  if (lower.includes("frustrated")) {
    return { sentiment: "frustrated", confidence: 0.7, indicators: ["detected in response"] };
  }
  if (lower.includes("negative")) {
    return { sentiment: "negative", confidence: 0.7, indicators: ["detected in response"] };
  }
  if (lower.includes("positive")) {
    return { sentiment: "positive", confidence: 0.7, indicators: ["detected in response"] };
  }

  return { sentiment: "neutral", confidence: 0.5, indicators: [] };
}

/**
 * Basic keyword-based sentiment analysis (fallback)
 */
function basicSentimentAnalysis(text) {
  const lower = text.toLowerCase();

  const frustratedIndicators = [
    "frustrated", "ridiculous", "unacceptable", "worst", "terrible",
    "hate", "angry", "furious", "outraged"
  ];
  const negativeIndicators = [
    "unhappy", "disappointed", "upset", "annoyed", "problem",
    "issue", "wrong", "bad", "poor"
  ];
  const positiveIndicators = [
    "thank", "great", "awesome", "perfect", "excellent",
    "wonderful", "appreciate", "helpful", "amazing"
  ];

  const foundFrustrated = frustratedIndicators.filter(i => lower.includes(i));
  const foundNegative = negativeIndicators.filter(i => lower.includes(i));
  const foundPositive = positiveIndicators.filter(i => lower.includes(i));

  // Check for caps lock (frustration indicator)
  const capsRatio = (text.match(/[A-Z]/g) || []).length / text.length;
  const hasCapsLock = capsRatio > 0.5 && text.length > 20;

  if (foundFrustrated.length > 0 || hasCapsLock) {
    return {
      sentiment: "frustrated",
      confidence: 0.7,
      indicators: [...foundFrustrated, ...(hasCapsLock ? ["excessive caps"] : [])]
    };
  }

  if (foundNegative.length > foundPositive.length) {
    return {
      sentiment: "negative",
      confidence: 0.6,
      indicators: foundNegative
    };
  }

  if (foundPositive.length > 0) {
    return {
      sentiment: "positive",
      confidence: 0.6,
      indicators: foundPositive
    };
  }

  return {
    sentiment: "neutral",
    confidence: 0.5,
    indicators: []
  };
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
