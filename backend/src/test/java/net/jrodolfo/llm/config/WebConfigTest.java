package net.jrodolfo.llm.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebConfigTest {

    @Test
    void allowsDeleteRequestsForFrontendOrigins() throws Exception {
        WebConfig webConfig = new WebConfig();
        CorsRegistry registry = new CorsRegistry();

        webConfig.addCorsMappings(registry);

        Field registrationsField = CorsRegistry.class.getDeclaredField("registrations");
        registrationsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var registrations = (java.util.List<CorsRegistration>) registrationsField.get(registry);

        Field configField = CorsRegistration.class.getDeclaredField("config");
        configField.setAccessible(true);
        var config = (org.springframework.web.cors.CorsConfiguration) configField.get(registrations.getFirst());

        assertTrue(config.getAllowedMethods() != null && config.getAllowedMethods().contains("DELETE"));
    }
}
