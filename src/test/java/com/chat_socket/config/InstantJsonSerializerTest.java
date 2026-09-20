package com.chat_socket.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

class InstantJsonSerializerTest {
    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new SimpleModule().addSerializer(Instant.class, new InstantJsonSerializer()))
            .build();

    @Test
    void writesUtcWithSixFractionDigits() {
        assertThat(mapper.writeValueAsString(Instant.parse("2026-01-01T12:00:00Z")))
                .isEqualTo("\"2026-01-01T12:00:00.000000Z\"");
        assertThat(mapper.writeValueAsString(Instant.parse("2026-01-01T12:00:00.5Z")))
                .isEqualTo("\"2026-01-01T12:00:00.500000Z\"");
        assertThat(mapper.writeValueAsString(Instant.parse("2026-01-01T12:00:00.123456789Z")))
                .isEqualTo("\"2026-01-01T12:00:00.123456Z\"");
    }

    @Test
    void nullStaysNull() {
        record Holder(Instant at) {}
        assertThat(mapper.writeValueAsString(new Holder(null))).isEqualTo("{\"at\":null}");
    }
}
