# Frontend Visual Review Checklist

Use this checklist after meaningful Agent or RAG UI changes. Automated tests can
catch regressions in behavior, but they do not replace a short browser review.

## Setup

1. Start the app:

   ```bash
   ./restart.sh
   ```

2. Open the frontend in a clean browser tab:

   ```text
   http://localhost:5173
   ```

3. Check both top-level workspaces:
   - `RAG`
   - `Agent`

## Areas To Inspect

- Top navigation: `RAG` and `Agent` tabs are visible, readable, and do not move unexpectedly.
- Page header: title, description, `show technical details`, and `Show Sessions` or `Hide Sessions` controls align cleanly.
- Session sidebar: title is `Sessions`, buttons stay inside the sidebar, session cards do not overflow, and mobile wrapping is usable.
- Status strip: status text and action buttons stay inside the card with no large empty columns.
- Input/query form: provider/model/retrieval controls are labeled, prompt/question fields are clear, and action buttons wrap safely.
- Empty states: first-run messages explain what to do next without looking like disabled controls.
- Answer/message cards: generated content is readable, cited sources or tool provenance stay attached to the relevant answer, and technical details remain compact.
- Artifact/tool panels: idle artifact text is useful, artifact actions are visible after tool results, and close buttons only appear when there is something to close.
- Responsive layout: narrow browser widths do not push buttons outside cards or sidebars.

## Manual Smoke Prompts

Agent:

```text
Hello, summarize what this app does.
```

```text
Give me a report from AWS S3 for the last month.
```

RAG:

```text
How are sessions persisted?
```

```text
What should I check when vector RAG is not working?
```

## Commit Rule

Before committing UI alignment work:

1. Run the relevant automated frontend tests.
2. Review both pages with this checklist.
3. Fix visible overflow, spacing, or confusing empty-state issues before making broader UI changes.
