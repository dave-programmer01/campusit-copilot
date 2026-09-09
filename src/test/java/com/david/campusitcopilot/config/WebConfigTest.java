package com.david.campusitcopilot.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebConfigTest {

    @Test
    void testCorsMappingsConfiguredCorrectly() throws Exception {
        String[] origins = new String[]{"https://campusit-copilot.vercel.app", "http://localhost:3000"};
        WebConfig webConfig = new WebConfig(origins);

        CorsRegistry registry = new CorsRegistry();
        webConfig.addCorsMappings(registry);

        Method getCorsConfigurationsMethod = CorsRegistry.class.getDeclaredMethod("getCorsConfigurations");
        getCorsConfigurationsMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, CorsConfiguration> configs = (Map<String, CorsConfiguration>) getCorsConfigurationsMethod.invoke(registry);

        assertTrue(configs.containsKey("/chat"));
        assertTrue(configs.containsKey("/chat/**"));
        assertTrue(configs.containsKey("/deflection"));
        assertTrue(configs.containsKey("/deflection/**"));
        assertFalse(configs.containsKey("/ingest"));
        assertFalse(configs.containsKey("/search"));

        CorsConfiguration chatConfig = configs.get("/chat");
        assertNotNull(chatConfig);
        assertEquals(List.of("https://campusit-copilot.vercel.app", "http://localhost:3000"), chatConfig.getAllowedOrigins());
        assertTrue(chatConfig.getAllowedMethods().containsAll(List.of("POST", "GET", "OPTIONS")));
        assertEquals(List.of("Content-Type"), chatConfig.getAllowedHeaders());
        assertFalse(chatConfig.getAllowedHeaders().contains("X-Internal-Api-Key"));

        CorsConfiguration deflectionConfig = configs.get("/deflection");
        assertNotNull(deflectionConfig);
        assertEquals(List.of("https://campusit-copilot.vercel.app", "http://localhost:3000"), deflectionConfig.getAllowedOrigins());
        assertTrue(deflectionConfig.getAllowedMethods().containsAll(List.of("POST", "GET", "OPTIONS")));
        assertEquals(List.of("Content-Type"), deflectionConfig.getAllowedHeaders());
        assertFalse(deflectionConfig.getAllowedHeaders().contains("X-Internal-Api-Key"));
    }

    @Test
    void testCustomOriginTrimmingAndFiltering() {
        WebConfig webConfig = new WebConfig(new String[]{"  https://my-custom-domain.com  ", "", "  "});
        assertArrayEquals(new String[]{"https://my-custom-domain.com"}, webConfig.getAllowedOrigins());
    }
}
