package net.jrodolfo.llm.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for SpringDoc OpenAPI and Swagger UI.
 * Defines the API documentation details and groups.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Creates a {@link GroupedOpenApi} for the application endpoints.
     *
     * @return the grouped OpenAPI configuration
     */
    @Bean
    public GroupedOpenApi appApi() {
        return GroupedOpenApi.builder()
                .group("app")
                .pathsToMatch("/**")
                .pathsToExclude("/actuator/**")
                .build();
    }

    /**
     * Configures the {@link OpenAPI} metadata for the Local GenAI Lab API.
     *
     * @return the OpenAPI configuration
     */
    @Bean
    public OpenAPI localGenAiLabOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Local GenAI Lab API")
                        .description("Spring Boot API for chat, sessions, local artifacts, and optional MCP-backed AWS tooling.")
                        .version("v1")
                        .contact(new Contact()
                                .name("Rod Oliveira")
                                .url("https://jrodolfo.net")
                                .email("jrodolfo@gmail.com"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
