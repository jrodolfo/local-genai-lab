package net.jrodolfo.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.model.ChatSessionMessage;
import net.jrodolfo.llm.provider.ProviderPrompt;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPromptBuilderTest {

    private final ChatPromptBuilder promptBuilder = new ChatPromptBuilder(new ObjectMapper());

    @Test
    void buildsToolAssistedPromptWithHistoryAndToolContext() {
        String prompt = promptBuilder.buildToolAssistedPrompt(
                "what happened in the audit?",
                List.of(
                        new ChatSessionMessage("user", "run aws audit", null, null, null, Instant.parse("2026-04-10T10:00:00Z")),
                        new ChatSessionMessage(
                                "assistant",
                                "Audit complete.",
                                new ChatToolMetadata(true, "aws_region_audit", "success", "done"),
                                null,
                                null,
                                Instant.parse("2026-04-10T10:01:00Z")
                        )
                ),
                new ChatPromptBuilder.ToolContext(
                        "aws_region_audit",
                        "aws audit request",
                        "AWS audit completed with success_count=3, failure_count=0, skipped_count=1.",
                        Map.of("ok", true, "summary", Map.of("success_count", 3))
                )
        );

        assertTrue(prompt.contains("<assistant_instructions>"));
        assertTrue(prompt.contains("<conversation_history>"));
        assertTrue(prompt.contains("user: run aws audit"));
        assertTrue(prompt.contains("<current_user_message>"));
        assertTrue(prompt.contains("what happened in the audit?"));
        assertTrue(prompt.contains("<tool_context>"));
        assertTrue(prompt.contains("tool_name: aws_region_audit"));
        assertTrue(prompt.contains("tool_result_json:"));
        assertTrue(prompt.contains("\"success_count\" : 3"));
        assertTrue(prompt.contains("<response_rules>"));
        assertTrue(prompt.contains("summarize what the tool already completed instead of refusing or redirecting"));
        assertTrue(prompt.contains("Do not claim inability or lack of access when tool output is already available in the prompt."));
    }

    @Test
    void buildsPlainPromptWithoutToolWrappers() {
        String prompt = promptBuilder.buildPlainChatPrompt(
                "explain recursion",
                List.of()
        );

        assertTrue(prompt.contains("You are a concise, factual assistant."));
        assertTrue(prompt.contains("User: explain recursion"));
        assertTrue(prompt.contains("Assistant:"));
        assertFalse(prompt.contains("<assistant_instructions>"));
        assertFalse(prompt.contains("<conversation_history>"));
        assertFalse(prompt.contains("<current_user_message>"));
        assertFalse(prompt.contains("<tool_context>"));
        assertFalse(prompt.contains("<response_rules>"));
    }

    @Test
    void formatsEmptyToolResultSafely() {
        String prompt = promptBuilder.buildToolAssistedPrompt(
                "summarize the report",
                List.of(),
                new ChatPromptBuilder.ToolContext(
                        "read_report_summary",
                        "latest report lookup",
                        "Read audit with success_count=unknown and failure_count=unknown.",
                        Map.of()
                ));

        assertTrue(prompt.contains("tool_summary: Read audit with success_count=unknown and failure_count=unknown."));
        assertTrue(prompt.contains("tool_result_json:\n{}"));
    }

    @Test
    void buildDispatchesToPlainPromptWhenToolContextIsMissing() {
        String prompt = promptBuilder.build(new ChatPromptBuilder.PromptContext(
                "now explain it using fibonacci and java",
                List.of(
                        new ChatSessionMessage("user", "explain recursion", null, null, null, Instant.parse("2026-04-10T10:00:00Z")),
                        new ChatSessionMessage("assistant", "Recursion is when a function calls itself.", null, null, null, Instant.parse("2026-04-10T10:00:05Z"))
                ),
                null
        ));

        assertTrue(prompt.contains("Conversation so far:"));
        assertTrue(prompt.contains("User: explain recursion"));
        assertTrue(prompt.contains("Assistant: Recursion is when a function calls itself."));
        assertTrue(prompt.contains("User: now explain it using fibonacci and java"));
        assertFalse(prompt.contains("<assistant_instructions>"));
    }

    @Test
    void buildsPlainProviderPromptWithRoleBasedMessages() {
        ProviderPrompt prompt = promptBuilder.buildPlainChatProviderPrompt(
                "now explain it using fibonacci and java",
                List.of(
                        new ChatSessionMessage("user", "explain recursion", null, null, null, Instant.parse("2026-04-10T10:00:00Z")),
                        new ChatSessionMessage("assistant", "Recursion is when a function calls itself.", null, null, null, Instant.parse("2026-04-10T10:00:05Z"))
                )
        );

        assertTrue(prompt.hasMessages());
        assertEquals("system", prompt.messages().get(0).role());
        assertEquals("user", prompt.messages().get(1).role());
        assertEquals("assistant", prompt.messages().get(2).role());
        assertEquals("user", prompt.messages().get(3).role());
        assertTrue(prompt.prompt().contains("Conversation so far:"));
    }
}
