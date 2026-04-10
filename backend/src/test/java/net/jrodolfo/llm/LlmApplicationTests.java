package net.jrodolfo.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmApplicationTests {

    @Test
    void applicationClassNameIsStable() {
        assertEquals("LlmApplication", LlmApplication.class.getSimpleName());
    }
}
