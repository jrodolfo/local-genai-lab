# Architecture Overview

This Mermaid diagram is the maintained source of truth for the system architecture. Mermaid diagrams are easier to maintain in Git, review in diffs, and update when the architecture changes. Please update this document whenever architectural changes are introduced.

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

  ProviderHub -->|local| Ollama
  ProviderHub -->|configured| Bedrock
  ProviderHub -->|hosted| HF
  ChatFlow <-->|tool chat| MCP

  MCP <--> Scripts
  Scripts <--> AWS
  Scripts -. reports .-> Reports

  ChatFlow -->|persist turns| Sessions
  ChatFlow -->|list / preview| Reports
  Reports -. backend APIs .-> ReactUI
```
