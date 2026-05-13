package com.chat_socket.utils;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.EnumSet;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;

public class UUIDv7Generator implements BeforeExecutionGenerator {
    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object id, EventType eventType) {
        return UuidCreator.getTimeOrderedEpoch();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }
}
