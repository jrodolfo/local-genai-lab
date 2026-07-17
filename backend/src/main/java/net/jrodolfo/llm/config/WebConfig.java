package net.jrodolfo.llm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for web-related settings, including CORS mappings.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    /**
     * Constructs a {@code WebConfig} with the specified CORS properties.
     *
     * @param corsProperties the CORS configuration properties
     */
    public WebConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * Constructs a {@code WebConfig} with default CORS origins.
     */
    public WebConfig() {
        this(new CorsProperties("http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000"));
    }

    /**
     * Configures Cross-Origin Resource Sharing (CORS) for the application's API endpoints.
     *
     * @param registry the CORS registry to configure
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.originsArray())
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
