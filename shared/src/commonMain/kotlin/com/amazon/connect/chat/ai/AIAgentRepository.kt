package com.amazon.connect.chat.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for AI agent operations.
 * Supports streaming responses and automatic provider fallback.
 *
 * Primary provider: Claude
 * Fallback provider: OpenAI
 */
interface AIAgentRepository {

    /**
     * Current provider being used (may change on fallback).
     */
    val currentProvider: StateFlow<AIProvider>

    /**
     * Connection/health status of AI service.
     */
    val isAvailable: StateFlow<Boolean>

    /**
     * Process a user message with streaming response.
     * This is the PRIMARY method - streaming is prioritized for better UX.
     *
     * The flow emits [AIStreamChunk] objects:
     * - delta: Text chunk to append to response
     * - done: True when stream is complete
     * - shouldEscalate: True if AI recommends human agent
     * - escalationReason: Why escalation is recommended
     * - suggestedReplies: Quick reply options for user
     * - error: Error message if something went wrong
     *
     * @param userMessage The user's input text
     * @param context Full conversation context including history
     * @return Flow of streaming chunks, ending with done=true
     */
    fun processMessageStream(
        userMessage: String,
        context: ConversationContext
    ): Flow<AIStreamChunk>

    /**
     * Process a user message without streaming (fallback).
     * Used when streaming fails or is disabled.
     *
     * @param userMessage The user's input text
     * @param context Full conversation context
     * @return Complete AI response
     */
    suspend fun processMessage(
        userMessage: String,
        context: ConversationContext
    ): AIAgentResponse

    /**
     * Check if AI service is available.
     * Attempts primary provider first, then fallback.
     *
     * @return Health check result with provider availability
     */
    suspend fun healthCheck(): HealthCheckResult

    /**
     * Generate a conversation summary for handover context.
     * Used when escalating to human agent to provide context.
     *
     * @param context Conversation context to summarize
     * @return Summary string for human agent
     */
    suspend fun generateSummary(
        context: ConversationContext
    ): String

    /**
     * Detect customer sentiment from conversation.
     * Used to inform escalation decisions and agent context.
     *
     * @param context Conversation context to analyze
     * @return Sentiment result (positive/neutral/negative/frustrated)
     */
    suspend fun analyzeSentiment(
        context: ConversationContext
    ): SentimentResult
}
