# Amazon Connect Chat - Kotlin Multiplatform

[![CI](https://github.com/sjesinc/amazon-connect-chat-kmp/actions/workflows/ci.yml/badge.svg)](https://github.com/sjesinc/amazon-connect-chat-kmp/actions/workflows/ci.yml)

A cross-platform chat application built with Kotlin Multiplatform and Compose Multiplatform that integrates with Amazon Connect for live agent handover capabilities.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Compose Multiplatform UI                          │
│    ┌──────────────┐    ┌──────────────┐    ┌──────────────────────────┐    │
│    │  ChatApp     │    │  Messages    │    │  SendMessage             │    │
│    │  (Scaffold)  │    │  (LazyList)  │    │  (TextField + Send btn)  │    │
│    └──────────────┘    └──────────────┘    └──────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                             State Management                                 │
│    ┌──────────────┐    ┌──────────────┐    ┌──────────────────────────┐    │
│    │    Store     │ ◄─►│   Reducer    │    │     ChatState            │    │
│    │  (Actions)   │    │  (Pure fn)   │    │  (Messages, Mode, etc.)  │    │
│    └──────────────┘    └──────────────┘    └──────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      Amazon Connect Integration                              │
│    ┌────────────────────────────────────────────────────────────────────┐   │
│    │              ConnectChatRepository (Interface)                      │   │
│    │  • startHandover(context)  • sendMessage()  • disconnect()         │   │
│    └────────────────────────────────────────────────────────────────────┘   │
│                                    │                                         │
│    ┌────────────────────────────────────────────────────────────────────┐   │
│    │            ConnectChatRepositoryImpl (Ktor + WebSocket)             │   │
│    │  • HTTP: CreateParticipantConnection, SendMessage, GetTranscript   │   │
│    │  • WebSocket: Real-time events (messages, typing, join/leave)      │   │
│    └────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Supported Platforms

- **Android** - Native Android app with Jetpack Compose
- **iOS** - Native iOS app with SwiftUI wrapper around Compose
- **Desktop** - JVM-based desktop app (macOS, Windows, Linux)
- **Web** - Browser-based app with Kotlin/JS and Compose for Web

## Project Structure

```
amazon-connect-chat-kmp/
├── shared/                          # Shared Kotlin Multiplatform code
│   └── src/
│       ├── commonMain/              # Common code for all platforms
│       │   └── kotlin/com/amazon/connect/chat/
│       │       ├── data/            # Data models (Message, User, Colors)
│       │       ├── store/           # Redux-like state management
│       │       ├── connect/         # Amazon Connect integration
│       │       └── ui/              # Compose UI components
│       ├── androidMain/             # Android-specific implementations
│       ├── iosMain/                 # iOS-specific implementations
│       ├── desktopMain/             # Desktop-specific implementations
│       └── jsMain/                  # Web/JS-specific implementations
├── androidApp/                      # Android application module
├── iosApp/                          # iOS application (Xcode project)
├── desktopApp/                      # Desktop application module
└── webApp/                          # Web application module (Kotlin/JS)
```

## Key Components

### Data Layer (`shared/src/commonMain/kotlin/.../data/`)

- **Models.kt** - `Message`, `User`, `ParticipantRole`, `ChatMode`
- **Colors.kt** - `ChatColors` with Amazon-inspired theme

### State Management (`shared/src/commonMain/kotlin/.../store/`)

- **Store.kt** - Redux-like store with action dispatch
- **Reducer.kt** - Pure reducer function, `ChatState`, `Action` sealed class

### Amazon Connect Integration (`shared/src/commonMain/kotlin/.../connect/`)

- **ConnectModels.kt** - DTOs for Connect APIs (`ChatSession`, `HandoverContext`, etc.)
- **ConnectChatRepository.kt** - Interface for Connect operations
- **ConnectChatRepositoryImpl.kt** - Ktor-based implementation

### UI Components (`shared/src/commonMain/kotlin/.../ui/`)

- **ChatApp.kt** - Main chat application with scaffold and event handling
- **Messages.kt** - Scrollable message list
- **ChatMessage.kt** - Individual message bubble component
- **SendMessage.kt** - Text input with send button

## Handover Flow

```
1. VIRTUAL AGENT PHASE
   • Customer chats with VA in the app
   • Conversation stored in ChatState.messages
   • VA detects need for human escalation

2. HANDOVER TRIGGER
   • User taps "Talk to Agent" button
   • App builds HandoverContext (transcript, intent, summary)
   • ConnectChatRepository.startHandover() called

3. AUTH API CALL
   • POST to your backend auth API
   • Your API validates auth + calls Connect StartChatContact
   • Returns participantToken

4. CONNECT DIRECT CONNECTION
   • CreateParticipantConnection with participantToken
   • Returns connectionToken + websocketUrl
   • App connects to WebSocket

5. TRANSCRIPT INJECTION
   • Prior VA conversation sent as first message
   • Agent sees full context in their chat panel

6. LIVE AGENT CONVERSATION
   • Agent joins (ParticipantJoined event)
   • Real-time bidirectional messaging
```

## Getting Started

### Prerequisites

- JDK 17+
- Android Studio Hedgehog (2023.1.1) or newer
- Xcode 15+ (for iOS)
- Kotlin 2.0.21+

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

## Configuration

Update the auth API URL in each platform's entry point:

```kotlin
repository.startHandover(
    authApiUrl = "https://your-api.example.com/chat/start",  // Your auth API
    context = context
)
```

### Required Auth API

Your backend needs a `/chat/start` endpoint that:

1. Validates user authentication (Cognito, etc.)
2. Calls Amazon Connect `StartChatContact` API
3. Returns `{ contactId, participantToken }`

Example Lambda:

```javascript
export async function handler(event) {
    const { customerId, customerName, attributes } = JSON.parse(event.body);

    // Validate auth token
    await validateToken(event.headers.Authorization);

    // Call Connect
    const response = await connect.startChatContact({
        InstanceId: CONNECT_INSTANCE_ID,
        ContactFlowId: CONTACT_FLOW_ID,
        Attributes: attributes,
        ParticipantDetails: { DisplayName: customerName }
    }).promise();

    return {
        statusCode: 200,
        body: JSON.stringify({
            contactId: response.ContactId,
            participantToken: response.ParticipantToken
        })
    };
}
```

## Dependencies

- **Compose Multiplatform** 1.7.1 - Cross-platform UI
- **Ktor** 2.3.12 - HTTP client & WebSocket
- **Kotlinx Serialization** 1.7.3 - JSON parsing
- **Kotlinx Coroutines** 1.8.1 - Async operations
- **Kotlinx DateTime** 0.6.1 - Date/time handling

## License

Apache 2.0
