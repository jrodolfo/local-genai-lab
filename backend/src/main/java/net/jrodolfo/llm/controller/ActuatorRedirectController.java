package net.jrodolfo.llm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller that handles redirection for the Spring Boot Actuator endpoint.
 */
@Controller
public class ActuatorRedirectController {

    /**
     * Redirects requests from /actuator to the actuator health endpoint.
     *
     * @return a redirect string to the health endpoint.
     */
    @GetMapping("/actuator")
    public String redirectToHealth() {
        return "redirect:/actuator/health";
    }
}
