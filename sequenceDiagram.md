# Sequence Diagrams

This document contains sequence diagrams for the key flows in the Vanta Unified AI Chat application.

## AI Virtual Agent Flow

```mermaid
sequenceDiagram
    participant User
    participant App as KMP Chat App
    participant Store as Redux Store
    participant AIRepo as AIAgentRepository
    participant Lambda as AWS Lambda<br/>(Streaming)
    participant AI as Claude / OpenAI

    %% User sends message
    User->>App: Types and sends message
    App->>Store: dispatch(Action.SendMessage)
    Store->>Store: Add message to state

    %% AI Processing starts
    rect rgb(230, 245, 255)
    Note over App,AI: AI Streaming Response
    App->>Store: dispatch(Action.AIProcessingStarted)
    Store->>Store: isAIProcessing = true
    App->>AIRepo: processMessageStream(message, context)
    AIRepo->>Lambda: POST /api/v1/chat/stream<br/>(SSE Connection)
    Lambda->>AI: Create streaming request

    loop SSE Chunks
        AI-->>Lambda: Text delta
        Lambda-->>AIRepo: data: {"delta": "...", "provider": "claude"}
        AIRepo-->>App: emit AIStreamChunk
        App->>Store: dispatch(Action.AIStreamChunk)
        Store->>Store: aiStreamBuffer += chunk
        Store-->>App: UI updates with streaming text
    end

    AI-->>Lambda: Stream complete
    Lambda-->>AIRepo: data: {"done": true, "shouldEscalate": false, "suggestedReplies": [...]}
    AIRepo-->>App: emit final chunk
    App->>Store: dispatch(Action.AIResponseComplete)
    Store->>Store: Finalize message, set suggestedReplies
    end

    %% Quick replies shown
    App->>User: Display response + quick reply chips

    %% Optional: User taps quick reply
    User->>App: Taps quick reply
    App->>Store: dispatch(Action.SendMessage)
    Note over App,AI: Cycle repeats
```

## AI Escalation Flow

```mermaid
sequenceDiagram
    participant User
    participant App as KMP Chat App
    participant Store as Redux Store
    participant AIRepo as AIAgentRepository
    participant Lambda as AWS Lambda

    %% AI detects escalation need
    rect rgb(255, 245, 230)
    Note over Lambda,AIRepo: AI Response includes [ESCALATE: reason]
    Lambda-->>AIRepo: data: {"done": true, "shouldEscalate": true, "escalationReason": "..."}
    AIRepo-->>App: emit final chunk with escalation
    App->>Store: dispatch(Action.AIResponseComplete)<br/>shouldEscalate = true
    Store->>Store: showEscalationDialog = true
    end

    %% User confirms escalation
    App->>User: Show EscalationConfirmationDialog
    alt User confirms
        User->>App: Clicks "Yes, connect me"
        App->>Store: dispatch(Action.EscalationResponse(true))
        Store->>Store: chatMode = CONNECTING_TO_AGENT

        %% Generate handover context
        App->>AIRepo: generateSummary(context)
        AIRepo->>Lambda: POST /api/v1/summarize
        Lambda-->>AIRepo: Conversation summary
        App->>AIRepo: analyzeSentiment(context)
        AIRepo->>Lambda: POST /api/v1/sentiment
        Lambda-->>AIRepo: {"sentiment": "neutral", "confidence": 0.8}

        Note over App: Proceed to Handover Flow
    else User declines
        User->>App: Clicks "No, continue"
        App->>Store: dispatch(Action.EscalationResponse(false))
        Store->>Store: showEscalationDialog = false
        Note over App,User: Continue with AI conversation
    end
```

## Handover to Human Agent Flow

```mermaid
sequenceDiagram
participant User
participant App as KMP Chat App
participant Store as Redux Store
participant Repo as ConnectChatRepository
participant AuthAPI as Backend Auth API
participant Connect as AWS Connect<br/>StartChatContact
participant PService as Participant Service<br/>(HTTPS)
participant WS as Participant Service<br/>(WebSocket)
participant Agent

      %% Phase 1: User initiates handover from Virtual Agent                                                                                                                  
      User->>App: Clicks "Talk to Agent"                                                                                                                                      
      App->>Store: dispatch(Action.InitiateHandover)                                                                                                                          
      Store->>Store: chatMode = CONNECTING_TO_AGENT                                                                                                                           
                                                                                                                                                                              
      %% Phase 2: Start Chat Contact                                                                                                                                          
      rect rgb(230, 245, 255)                                                                                                                                                 
      Note over App,Connect: StartChatContact API Flow                                                                                                                        
      App->>Repo: startHandover(authApiUrl, HandoverContext)                                                                                                                  
      Repo->>AuthAPI: POST /startChat<br/>{ParticipantDetails, Attributes}                                                                                                    
      AuthAPI->>Connect: startChatContact()                                                                                                                                   
      Connect-->>AuthAPI: ContactId, ParticipantId, ParticipantToken                                                                                                          
      AuthAPI-->>Repo: StartChatResponse                                                                                                                                      
      end                                                                                                                                                                     
                                                                                                                                                                              
      %% Phase 3: Create Participant Connection                                                                                                                               
      rect rgb(255, 245, 230)                                                                                                                                                 
      Note over Repo,PService: CreateParticipantConnection Flow                                                                                                               
      Repo->>PService: POST /participant/connection<br/>X-Amz-Bearer: {ParticipantToken}<br/>{Type: ["WEBSOCKET", "CONNECTION_CREDENTIALS"]}                                  
      PService-->>Repo: ConnectionToken, WebSocket URL, Expiry                                                                                                                
      Repo->>Repo: Store ChatSession                                                                                                                                          
      Repo-->>App: emit ConnectEvent.Connected                                                                                                                                
      end                                                                                                                                                                     
                                                                                                                                                                              
      %% Phase 4: WebSocket Connection                                                                                                                                        
      rect rgb(245, 255, 230)                                                                                                                                                 
      Note over Repo,WS: WebSocket Connection Flow                                                                                                                            
      Repo->>WS: Connect to wss://participant.connect.{region}...                                                                                                             
      WS-->>Repo: WebSocket Connected                                                                                                                                         
      Repo->>WS: {"topic": "aws/subscribe",<br/>"content": {"topics": ["aws/chat"]}}                                                                                          
      WS-->>Repo: Subscription Confirmed                                                                                                                                      
      Repo->>Repo: Start Heartbeat (every 30s)                                                                                                                                
      Repo->>Repo: connectionState = WAITING_FOR_AGENT                                                                                                                        
      end                                                                                                                                                                     
                                                                                                                                                                              
      %% Phase 5: Inject Transcript                                                                                                                                           
      rect rgb(255, 230, 245)                                                                                                                                                 
      Note over Repo,Agent: Transcript Injection                                                                                                                              
      Repo->>PService: POST /participant/message<br/>X-Amz-Bearer: {ConnectionToken}<br/>{Content: "--- Prior conversation ---<br/>[CUSTOMER]: ...<br/>[VIRTUAL AGENT]: ..."} 
      PService-->>Repo: MessageId, AbsoluteTime                                                                                                                               
      end                                                                                                                                                                     
                                                                                                                                                                              
      %% Phase 6: Agent Joins                                                                                                                                                 
      Agent->>WS: Agent accepts chat                                                                                                                                          
      WS->>Repo: {"topic": "aws/chat",<br/>"content": {Type: "EVENT",<br/>ContentType: "participant.joined",<br/>ParticipantRole: "AGENT"}}                                   
      Repo-->>App: emit ConnectEvent.ParticipantJoined                                                                                                                        
      App->>Store: dispatch(Action.ParticipantJoined)                                                                                                                         
      Store->>Store: chatMode = HUMAN_AGENT<br/>connectionState = AGENT_CONNECTED                                                                                             
      App->>User: "Agent joined the chat"                                                                                                                                     
                                                                                                                                                                              
      %% Phase 7: Live Chat                                                                                                                                                   
      rect rgb(230, 255, 245)                                                                                                                                                 
      Note over User,Agent: Bidirectional Messaging                                                                                                                           
                                                                                                                                                                              
      User->>App: Types message                                                                                                                                               
      App->>Repo: sendMessage(content)                                                                                                                                        
      Repo->>PService: POST /participant/message<br/>X-Amz-Bearer: {ConnectionToken}                                                                                          
      PService-->>Repo: MessageId                                                                                                                                             
      PService->>WS: Route to Agent                                                                                                                                           
      WS->>Agent: Message delivered                                                                                                                                           
                                                                                                                                                                              
      Agent->>WS: Agent sends reply                                                                                                                                           
      WS->>Repo: {"topic": "aws/chat",<br/>"content": {Type: "MESSAGE",<br/>Content: "...",<br/>ParticipantRole: "AGENT"}}                                                    
      Repo-->>App: emit ConnectEvent.MessageReceived
      App->>Store: dispatch(Action.ReceiveMessage)
      App->>User: Display agent message
      end
```

## Provider Fallback Flow

```mermaid
sequenceDiagram
    participant App as KMP Chat App
    participant AIRepo as AIAgentRepository
    participant Lambda as AWS Lambda
    participant Claude as Claude API
    participant OpenAI as OpenAI API

    App->>AIRepo: processMessageStream()
    AIRepo->>Lambda: POST /api/v1/chat/stream<br/>provider=claude
    Lambda->>Claude: Create streaming request

    alt Claude succeeds
        Claude-->>Lambda: Stream response
        Lambda-->>AIRepo: SSE chunks with provider: "claude"
    else Claude fails
        Claude--xLambda: Error/Timeout
        Lambda-->>AIRepo: data: {"error": "Primary provider unavailable..."}
        Lambda->>OpenAI: Fallback to OpenAI
        OpenAI-->>Lambda: Stream response
        Lambda-->>AIRepo: SSE chunks with provider: "openai"
    end

    AIRepo-->>App: Complete response with provider info
```