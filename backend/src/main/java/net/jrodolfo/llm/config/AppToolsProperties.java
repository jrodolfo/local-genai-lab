package net.jrodolfo.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for application tools and routing.
 *
 * @param routingMode the mode used for tool routing (e.g., "manual" or "agentic")
 * @param logPlanner  whether to log planner details during execution
 */
@ConfigurationProperties(prefix = "app.tools")
public record AppToolsProperties(
        String routingMode,
        boolean logPlanner
) {
}
