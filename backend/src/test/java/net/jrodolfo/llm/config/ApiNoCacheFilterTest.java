package net.jrodolfo.llm.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiNoCacheFilterTest {

    @Test
    void addsNoStoreHeadersForApiRoutes() throws Exception {
        ApiNoCacheFilter filter = new ApiNoCacheFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sessions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, passthroughChain());

        assertEquals("no-store, no-cache, must-revalidate, max-age=0", response.getHeader("Cache-Control"));
        assertEquals("no-cache", response.getHeader("Pragma"));
        assertEquals(0L, response.getDateHeader("Expires"));
    }

    @Test
    void leavesNonApiRoutesUntouched() throws Exception {
        ApiNoCacheFilter filter = new ApiNoCacheFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, passthroughChain());

        assertNull(response.getHeader("Cache-Control"));
        assertNull(response.getHeader("Pragma"));
    }

    private FilterChain passthroughChain() {
        return (request, response) -> {
        };
    }
}
