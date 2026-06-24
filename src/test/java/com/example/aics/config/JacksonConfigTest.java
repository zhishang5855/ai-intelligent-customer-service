package com.example.aics.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonConfigTest {

    @Test
    void serializesLongIdsAsStringsForBrowserSafety() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().longToStringSerializer().customize(builder);
        ObjectMapper objectMapper = builder.build();

        String json = objectMapper.writeValueAsString(new IdPayload(2069761208094896000L));

        assertTrue(json.contains("\"id\":\"2069761208094896000\""));
    }

    @Test
    void deserializesStringIdsBackToLongs() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        IdPayload payload = objectMapper.readValue("{\"id\":\"2069761208094896000\"}", IdPayload.class);

        assertEquals(2069761208094896000L, payload.id());
    }

    private record IdPayload(Long id) {
    }
}
