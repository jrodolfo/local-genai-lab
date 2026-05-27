package net.jrodolfo.llm.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contributes custom information to the application's actuator /info endpoint.
 * Provides details about application naming, runtime configuration, storage paths,
 * and the status of various LLM providers and MCP tooling.
 */
@Component
public class ActuatorInfoContributor implements InfoContributor {

    private final Environment environment;
    private final AppModelProperties appModelProperties;
    private final AppStorageProperties appStorageProperties;
    private final McpProperties mcpProperties;
    private final OllamaProperties ollamaProperties;
    private final BedrockProperties bedrockProperties;
    private final HuggingFaceProperties huggingFaceProperties;

    public ActuatorInfoContributor(Environment environment,
                                   AppModelProperties appModelProperties,
                                   AppStorageProperties appStorageProperties,
                                   McpProperties mcpProperties,
                                   OllamaProperties ollamaProperties,
                                   BedrockProperties bedrockProperties,
                                   HuggingFaceProperties huggingFaceProperties) {
        this.environment = environment;
        this.appModelProperties = appModelProperties;
        this.appStorageProperties = appStorageProperties;
        this.mcpProperties = mcpProperties;
        this.ollamaProperties = ollamaProperties;
        this.bedrockProperties = bedrockProperties;
        this.huggingFaceProperties = huggingFaceProperties;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("app", appDetails());
        builder.withDetail("runtime", runtimeDetails());
        builder.withDetail("docs", Map.of(
                "health", "/actuator/health",
                "info", "/actuator/info",
                "openApi", "/v3/api-docs",
                "swaggerUi", "/swagger-ui/index.html"
        ));
    }

    private Map<String, Object> appDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", environment.getProperty("spring.application.name", "local-genai-lab-backend"));
        details.put("description", "Spring Boot backend for chat, sessions, artifacts, and MCP-backed tooling.");
        return details;
    }

    private Map<String, Object> runtimeDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("provider", appModelProperties.provider());
        details.put("sessionsDirectory", appStorageProperties.resolvedSessionsDirectory().toString());
        details.put("reportsDirectory", appStorageProperties.resolvedReportsDirectory().toString());
        details.put("mcp", mcpDetails());

        if ("bedrock".equalsIgnoreCase(appModelProperties.provider())) {
            details.put("modelId", bedrockProperties.modelId());
            details.put("region", bedrockProperties.region());
        } else if ("huggingface".equalsIgnoreCase(appModelProperties.provider())) {
            details.put("modelId", huggingFaceProperties.defaultModel());
            details.put("baseUrl", huggingFaceProperties.baseUrl());
        } else {
            details.put("modelId", ollamaProperties.defaultModel());
            details.put("baseUrl", ollamaProperties.baseUrl());
        }
        return details;
    }

    private Map<String, Object> mcpDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("enabled", mcpProperties.enabled());
        details.put("command", mcpProperties.command());
        details.put("workingDirectory", mcpProperties.resolvedWorkingDirectory().toString());
        details.put("workingDirectoryExists", mcpProperties.resolvedWorkingDirectory().toFile().isDirectory());
        details.put("startupTimeoutSeconds", mcpProperties.startupTimeoutSeconds());
        details.put("toolTimeoutSeconds", mcpProperties.toolTimeoutSeconds());
        details.put("configured",
                mcpProperties.command() != null
                        && !mcpProperties.command().isBlank()
                        && mcpProperties.resolvedWorkingDirectory().toFile().isDirectory());
        details.put("status", mcpProperties.enabled() ? "enabled" : "disabled");
        return details;
    }
}
