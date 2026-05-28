package net.jrodolfo.llm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configuration for chat streaming execution.
 * Defines a cached thread pool executor for handling streaming responses from LLM providers.
 */
@Configuration
public class StreamingConfig {

    /**
     * Creates an {@link Executor} for handling streaming chat responses.
     * The executor uses a cached thread pool with daemon threads.
     *
     * @return the chat streaming executor
     */
    @Bean(name = "chatStreamingExecutor", destroyMethod = "shutdown")
    public Executor chatStreamingExecutor() {
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("chat-stream-" + thread.threadId());
            thread.setDaemon(true);
            return thread;
        });
    }
}
