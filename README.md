# Vanta Unified AI Chat

A cross-platform AI-powered chat application built with Kotlin Multiplatform and Compose Multiplatform. Features intelligent virtual agent conversations using Claude/OpenAI with seamless handover to Amazon Connect for live agent support.

## Features

- **AI Virtual Agent** - Streaming conversational AI powered by Claude (primary) with OpenAI fallback
- **Human Agent Escalation** - Seamless handover to Amazon Connect with conversation context
- **Cross-Platform** - Single codebase for Android, iOS, Desktop, and Web
- **Real-time Communication** - WebSocket-based live chat with agents
- **Intelligent Escalation** - AI-detected triggers with user confirmation
- **Quick Replies** - AI-suggested response options for faster interactions

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Compose Multiplatform UI                          │
│    ┌──────────────┐    ┌──────────────┐    ┌──────────────────────────┐    │
│    │  ChatApp     │    │  Messages    │    │  SendMessage             │    │
│    │  (Scaffold)  │    │  (LazyList)  │    │  (TextField + Send btn)  │    │
│    └──────────────┘    └──────────────┘    └──────────────────────────┘    │
│    ┌──────────────────────────────────────────────────────────────────┐    │
│    │  AIComponents: StreamingBubble, QuickReplies, EscalationDialog   │    │
│    └──────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                             State Management                                 │
│    ┌──────────────┐    ┌──────────────┐    ┌──────────────────────────┐    │
│    │    Store     │ ◄─►│   Reducer    │    │     ChatState            │    │
│    │  (Actions)   │    │  (Pure fn)   │    │  (Messages, AI state,    │    │
│    └──────────────┘    └──────────────┘    │   Mode, Connection)      │    │
│                                            └──────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    ▼                               ▼
┌───────────────────────────────────┐ ┌───────────────────────────────────────┐
│       AI Agent Integration        │ │      Amazon Connect Integration       │
│  ┌─────────────────────────────┐  │ │  ┌─────────────────────────────────┐  │
│  │    AIAgentRepository        │  │ │  │    ConnectChatRepository        │  │
│  │  • processMessageStream()   │  │ │  │  • startHandover(context)       │  │
│  │  • generateSummary()        │  │ │  │  • sendMessage()                │  │
│  │  • analyzeSentiment()       │  │ │  │  • disconnect()                 │  │
│  └─────────────────────────────┘  │ │  └─────────────────────────────────┘  │
│               │                   │ │               │                       │
│               ▼                   │ │               ▼                       │
│  ┌─────────────────────────────┐  │ │  ┌─────────────────────────────────┐  │
│  │  AWS Lambda (SSE Streaming) │  │ │  │  AWS Connect Participant API    │  │
│  │  Claude / OpenAI APIs       │  │ │  │  WebSocket real-time events     │  │
│  └─────────────────────────────┘  │ │  └─────────────────────────────────┘  │
└───────────────────────────────────┘ └───────────────────────────────────────┘
```

## Supported Platforms

| Platform | Technology | HTTP Engine | Status |
|----------|-----------|------------|--------|
| **Android** | Jetpack Compose | OkHttp | Full featured |
| **iOS** | SwiftUI wrapper + Compose | Darwin | Full featured |
| **Desktop** | Compose Desktop (macOS/Windows/Linux) | OkHttp | Full featured |
| **Web** | Kotlin/JS + Compose for Web | Browser/Fetch | Full featured |

## Project Structure

```
vanta-unified-ai-chat/
├── shared/                          # Shared Kotlin Multiplatform code
│   └── src/
│       ├── commonMain/              # Common code for all platforms
│       │   └── kotlin/com/amazon/connect/chat/
│       │       ├── ai/              # AI Agent integration (NEW)
│       │       │   ├── AIAgentModels.kt
│       │       │   ├── AIAgentRepository.kt
│       │       │   └── AIAgentRepositoryImpl.kt
│       │       ├── connect/         # Amazon Connect integration
│       │       ├── data/            # Data models (Message, User, Colors)
│       │       ├── store/           # Redux-like state management
│       │       └── ui/              # Compose UI components
│       │           └── AIComponents.kt  # Streaming UI, quick replies
│       ├── androidMain/             # Android-specific (OkHttp engine)
│       ├── iosMain/                 # iOS-specific (Darwin engine)
│       ├── desktopMain/             # Desktop-specific (OkHttp engine)
│       └── jsMain/                  # Web/JS-specific (Browser engine)
├── androidApp/                      # Android application entry point
├── iosApp/                          # iOS application (Xcode project)
├── desktopApp/                      # Desktop application module
├── webApp/                          # Web application module (Kotlin/JS)
└── backend/                         # AWS Lambda backend for AI streaming
    ├── src/
    │   ├── stream.js                # Streaming AI responses (Claude/OpenAI)
    │   ├── health.js                # Provider health checks
    │   ├── summary.js               # Conversation summarization
    │   └── sentiment.js             # Sentiment analysis
    └── template.yaml                # AWS SAM infrastructure
```

## Key Components

### AI Agent Module (`shared/src/commonMain/kotlin/.../ai/`)

- **AIAgentModels.kt** - `AIStreamChunk`, `AIAgentConfig`, `ConversationContext`, `SentimentResult`
- **AIAgentRepository.kt** - Interface for AI operations with streaming support
- **AIAgentRepositoryImpl.kt** - Ktor-based implementation with Claude/OpenAI fallback

### Data Layer (`shared/src/commonMain/kotlin/.../data/`)

- **Models.kt** - `Message`, `User`, `ParticipantRole`, `ChatMode`
- **Colors.kt** - `ChatColors` with Amazon-inspired theme

### State Management (`shared/src/commonMain/kotlin/.../store/`)

- **Store.kt** - Redux-like store with action dispatch
- **Reducer.kt** - Pure reducer function, `ChatState` (with AI streaming state), `Action` sealed class

### Amazon Connect Integration (`shared/src/commonMain/kotlin/.../connect/`)

- **ConnectModels.kt** - DTOs for Connect APIs (`ChatSession`, `HandoverContext`, etc.)
- **ConnectChatRepository.kt** - Interface for Connect operations
- **ConnectChatRepositoryImpl.kt** - Ktor-based implementation with WebSocket

### UI Components (`shared/src/commonMain/kotlin/.../ui/`)

- **ChatApp.kt** - Main chat application with AI processing and escalation
- **AIComponents.kt** - `StreamingMessageBubble`, `QuickReplies`, `EscalationConfirmationDialog`
- **Messages.kt** - Scrollable message list
- **ChatMessage.kt** - Individual message bubble component
- **SendMessage.kt** - Text input with send button

## Conversation Flow

### AI Virtual Agent Phase
```
User sends message
       │
       ▼
┌──────────────────────┐
│ Store dispatches     │
│ SendMessage action   │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐     ┌──────────────────────┐
│ ChatMode ==          │ YES │ AIAgentRepository    │
│ VIRTUAL_AGENT?       │────▶│ .processMessageStream│
└──────────────────────┘     └──────────┬───────────┘
                                        │
                        ┌───────────────┼───────────────┐
                        ▼               ▼               ▼
                 ┌────────────┐  ┌────────────┐  ┌────────────┐
                 │ SSE Chunk  │  │ SSE Chunk  │  │ Final      │
                 │ (streaming)│  │ (streaming)│  │ [DONE]     │
                 └────────────┘  └────────────┘  └─────┬──────┘
                                                       │
                                        ┌──────────────┴──────────────┐
                                        ▼                             ▼
                               ┌──────────────────┐       ┌──────────────────┐
                               │ shouldEscalate?  │       │ Display response │
                               │ Show dialog      │       │ + quick replies  │
                               └──────────────────┘       └──────────────────┘
```

### Handover to Human Agent
```
1. ESCALATION TRIGGERED
   • AI detects need for human escalation (includes [ESCALATE: reason] marker)
   • User confirms via escalation dialog
   • AI generates conversation summary + sentiment analysis

2. HANDOVER INITIATED
   • App builds HandoverContext (transcript, AI summary, sentiment)
   • ConnectChatRepository.startHandover() called
   • POST to backend auth API → Connect StartChatContact

3. WEBSOCKET CONNECTION
   • CreateParticipantConnection with participantToken
   • Returns connectionToken + websocketUrl
   • App connects to WebSocket, subscribes to aws/chat topic

4. LIVE AGENT CONVERSATION
   • Agent joins (ParticipantJoined event)
   • Agent sees AI summary + full transcript
   • Real-time bidirectional messaging via WebSocket
```

## Getting Started

### Prerequisites

- JDK 17+
- Android Studio Ladybug (2024.2.1) or newer
- Xcode 15+ (for iOS)
- Kotlin 2.0.21+
- AWS CLI + SAM CLI (for backend deployment)
- Node.js 20.x (for backend)

### Build & Run

```bash
# Build shared module
./gradlew :shared:build

# Run Android app
./gradlew :androidApp:installDebug

# Run Desktop app
./gradlew :desktopApp:run

# Build iOS framework
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
# Then open iosApp/iosApp.xcodeproj in Xcode

# Run Web app (opens at http://localhost:8080)
./gradlew :webApp:jsBrowserDevelopmentRun
```

### Backend Deployment

See [backend/README.md](backend/README.md) for detailed deployment instructions.

```bash
# Deploy to development environment
cd backend
./deploy.sh dev us-east-1

# Configure API keys in Secrets Manager
aws secretsmanager put-secret-value \
    --secret-id dev/ai-virtual-agent/api-keys \
    --secret-string '{"ANTHROPIC_API_KEY":"sk-ant-...","OPENAI_API_KEY":"sk-..."}'
```

## Configuration

### AI Agent Configuration

Update the AI proxy URL in each platform's entry point:

```kotlin
val aiRepository = AIAgentRepositoryImpl(
    config = AIAgentConfig(
        proxyBaseUrl = "https://your-lambda-url.on.aws",
        systemPrompt = "You are a helpful customer service assistant..."
    )
)
```

### Amazon Connect Auth API

Your backend needs a `/chat/start` endpoint that:

1. Validates user authentication (Cognito, etc.)
2. Calls Amazon Connect `StartChatContact` API
3. Returns `{ contactId, participantToken }`

```kotlin
repository.startHandover(
    authApiUrl = "https://your-api.example.com/chat/start",
    context = handoverContext  // Includes AI summary + sentiment
)
```

## Dependencies

### Frontend (Kotlin Multiplatform)

| Dependency | Version | Purpose |
|------------|---------|---------|
| **Compose Multiplatform** | 1.10.0 | Cross-platform UI framework |
| **Kotlin** | 2.0.21 | Programming language |
| **Ktor** | 2.3.12 | HTTP client & WebSocket |
| **Kotlinx Serialization** | 1.7.3 | JSON serialization |
| **Kotlinx Coroutines** | 1.8.1 | Async operations |
| **Kotlinx DateTime** | 0.6.1 | Date/time handling |

### Backend (AWS Lambda)

| Dependency | Purpose |
|------------|---------|
| **@anthropic-ai/sdk** | Claude API client |
| **openai** | OpenAI API client |
| **@aws-sdk/client-secrets-manager** | Secure API key storage |

## API Endpoints

The backend provides four Lambda Function URLs:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/chat/stream` | POST | Streaming AI responses (SSE) |
| `/api/v1/health` | GET | Provider health check |
| `/api/v1/summarize` | POST | Conversation summarization |
| `/api/v1/sentiment` | POST | Sentiment analysis |

See [backend/README.md](backend/README.md) for full API documentation.

## Related Documentation

- [Backend README](backend/README.md) - Deployment and API details
- [Sequence Diagrams](sequenceDiagram.md) - Visual flow diagrams
- [Implementation Plan](AI_VIRTUAL_AGENT_IMPLEMENTATION_PLAN.md) - Architecture details

## License

Apache 2.0
