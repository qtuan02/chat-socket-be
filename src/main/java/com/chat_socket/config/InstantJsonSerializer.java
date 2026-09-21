package com.chat_socket.config;

import com.chat_socket.constant.TimeFormat;
import java.time.Instant;
import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** Registered globally by Spring Boot; the STOMP broker converter shares the same JsonMapper. */
@JacksonComponent
public class InstantJsonSerializer extends ValueSerializer<Instant> {
    @Override
    public void serialize(Instant value, JsonGenerator generator, SerializationContext context) {
        generator.writeString(TimeFormat.UTC_MICROS.format(value));
    }
}
