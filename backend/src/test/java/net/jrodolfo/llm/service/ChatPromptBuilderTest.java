package net.jrodolfo.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.model.ChatSessionMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPromptBuilderTest {

    private final ChatPromptBuilder promptBuilder = new ChatPromptBuilder(new ObjectMapper());

    @Test
    void buildsPromptWithHistoryAndToolContext() {
        String prompt = promptBuilder.build(new ChatPromptBuilder.PromptContext(
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
        ));

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
    void omitsOptionalSectionsWhenHistoryAndToolContextAreMissing() {
        String prompt = promptBuilder.build(new ChatPromptBuilder.PromptContext(
                "explain recursion",
                List.of(),
                null
        ));

        assertTrue(prompt.contains("<assistant_instructions>"));
        assertTrue(prompt.contains("<current_user_message>"));
        assertFalse(prompt.contains("<conversation_history>"));
        assertFalse(prompt.contains("<tool_context>"));
        assertTrue(prompt.contains("<response_rules>"));
    }

    @Test
    void formatsEmptyToolResultSafely() {
        String prompt = promptBuilder.build(new ChatPromptBuilder.PromptContext(
                "summarize the report",
                List.of(),
                new ChatPromptBuilder.ToolContext(
                        "read_report_summary",
                        "latest report lookup",
                        "Read audit with success_count=unknown and failure_count=unknown.",
                        Map.of()
                )
        ));

        assertTrue(prompt.contains("tool_summary: Read audit with success_count=unknown and failure_count=unknown."));
        assertTrue(prompt.contains("tool_result_json:\n{}"));
    }
}
