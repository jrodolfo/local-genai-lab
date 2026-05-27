# Provider Evaluation Template

Use this template to compare Ollama, Bedrock, and Hugging Face with the same prompts and the same UI flow.

Why both objective and subjective latency notes:
- objective timing tells you what happened numerically
- perceived responsiveness tells you how the app felt while you were waiting

Example:
- two providers may both take 8 seconds total
- one may show the first token quickly and feel smooth
- the other may feel stalled and then finish all at once

## Metadata

- Date:
- Tester:
- Backend commit:
- Frontend commit:

## Prompt Set

1. Explain recursion with a simple Java example.
2. Summarize the difference between S3 and EBS in 5 bullets.
3. Please audit my AWS account.
4. Check S3 CloudWatch metrics for the last 7 days.

## Ollama

### Plain Chat
- Prompt:
- Result:
- Quality:
- Objective timing:
- Perceived responsiveness:
- Formatting:
- Notes:

### Streaming
- Prompt:
- Time to first token:
- Total time to complete:
- Stream smoothness:
- Completion behavior:
- Cancel behavior:
- Notes:

### Tool-Assisted
- Prompt:
- Tool selected:
- Tool phases clear:
- Provenance/result UI:
- Final answer quality:
- Objective timing:
- Perceived responsiveness:
- Notes:

### Clarification Flow
- Prompt:
- Clarification requested:
- Follow-up worked:
- Reopen/import checked:
- Notes:

### Failures
- Error seen:
- Message quality:
- Actionable:
- Notes:

### Scorecard
- Plain chat: good | acceptable | weak
- Streaming UX: good | acceptable | weak
- Tool use quality: good | acceptable | weak
- Clarification flow: good | acceptable | weak
- Failure clarity: good | acceptable | weak
- Overall reliability: good | acceptable | weak

## Bedrock

### Plain Chat
- Prompt:
- Result:
- Quality:
- Objective timing:
- Perceived responsiveness:
- Formatting:
- Notes:

### Streaming
- Prompt:
- Time to first token:
- Total time to complete:
- Stream smoothness:
- Completion behavior:
- Cancel behavior:
- Notes:

### Tool-Assisted
- Prompt:
- Tool selected:
- Tool phases clear:
- Provenance/result UI:
- Final answer quality:
- Objective timing:
- Perceived responsiveness:
- Notes:

### Clarification Flow
- Prompt:
- Clarification requested:
- Follow-up worked:
- Reopen/import checked:
- Notes:

### Failures
- Error seen:
- Message quality:
- Actionable:
- Notes:

### Scorecard
- Plain chat: good | acceptable | weak
- Streaming UX: good | acceptable | weak
- Tool use quality: good | acceptable | weak
- Clarification flow: good | acceptable | weak
- Failure clarity: good | acceptable | weak
- Overall reliability: good | acceptable | weak

## Hugging Face

### Plain Chat
- Prompt:
- Result:
- Quality:
- Objective timing:
- Perceived responsiveness:
- Formatting:
- Notes:

### Streaming
- Prompt:
- Time to first token:
- Total time to complete:
- Stream smoothness:
- Completion behavior:
- Cancel behavior:
- Notes:

### Tool-Assisted
- Prompt:
- Tool selected:
- Tool phases clear:
- Provenance/result UI:
- Final answer quality:
- Objective timing:
- Perceived responsiveness:
- Notes:

### Clarification Flow
- Prompt:
- Clarification requested:
- Follow-up worked:
- Reopen/import checked:
- Notes:

### Failures
- Error seen:
- Message quality:
- Actionable:
- Notes:

### Scorecard
- Plain chat: good | acceptable | weak
- Streaming UX: good | acceptable | weak
- Tool use quality: good | acceptable | weak
- Clarification flow: good | acceptable | weak
- Failure clarity: good | acceptable | weak
- Overall reliability: good | acceptable | weak

## Cross-Provider Findings

### Best At
- Best plain chat:
- Best streaming:
- Best tool-assisted answer quality:
- Best clarification flow:
- Best failure messaging:

### Weakest Areas
- Ollama:
- Bedrock:
- Hugging Face:

### Repeated Problems Worth Fixing
1.
2.
3.

### Next Recommended Engineering Task
- 
