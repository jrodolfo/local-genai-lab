# ADR 0005: Ground Tool Results Through The Selected Model

- Status: Accepted

## Context

When a tool executes successfully, the application can either:

1. return raw tool output directly to the user
2. bypass the selected LLM and synthesize a backend-owned final answer
3. enrich the prompt with the tool result and let the selected model answer

This is a core product behavior decision because it defines what “tool-assisted chat” means in the project.

## Decision

Ground successful tool results through the selected model.

The backend enriches the prompt with structured tool output and still asks the selected provider to produce the final answer.

## Rationale

- the project is explicitly about studying model behavior under grounded prompts
- comparing providers is more meaningful when they reason over the same tool result
- bypassing the model would remove an important part of the learning goal

## Consequences

Positive:

- tool-assisted answers remain comparable across providers
- the application preserves a consistent “chat with a model” experience
- tool output becomes grounding context rather than a separate output mode

Negative:

- final wording can still vary by provider and model
- a successful tool run does not guarantee identical answers
- prompt construction becomes more important because the tool result must be represented well

## Revisit Triggers

Reevaluate this decision if:

- a future feature requires deterministic raw tool output as the primary product behavior
- the project shifts away from model comparison and toward pure task execution
