# Architecture Overview

This Mermaid version replaces the generated SVG because it is easier to maintain in Git, review in diffs, and update when the architecture changes.

The diagram keeps the same architectural meaning as the previous SVG:

- `React UI` covers chat, sessions, provider/model selection, exports, and artifact access.
- `Chat Flow` covers routing, prompt construction, streaming, and persistence.
- `Provider Hub` covers runtime provider selection, configured-provider filtering, and provider status/troubleshooting.
- `Reports` covers generated summaries, report files, stderr files, and backend-driven previews.

```mermaid
flowchart LR
  subgraph UI[Frontend]
    ReactUI[React UI]
  end

  subgraph API[Spring Boot Backend]
    ChatFlow[Chat Flow]
    ProviderHub[Provider Hub]
    Sessions[Sessions]
    Reports[Reports]
  end

  subgraph Providers[LLM Providers]
    Ollama[Ollama]
    Bedrock[Bedrock]
    HF[Hugging Face]
  end

  subgraph Tools[Tooling]
    MCP[MCP Server]
    Scripts[Shell Scripts]
    AWS[AWS CLI]
  end

  ReactUI -->|HTTP + SSE| ChatFlow
  ChatFlow --> ProviderHub

  ChatFlow -->|plain chat| Ollama
  ProviderHub -->|configured only| Bedrock
  ProviderHub -->|hosted check| HF
  ChatFlow -->|tool chat| MCP

  MCP --> Scripts
  Scripts --> AWS
  Scripts -. reports .-> Reports

  ChatFlow -->|persist turns| Sessions
  ChatFlow -->|list / preview| Reports
  Reports -. backend APIs .-> ReactUI
```
