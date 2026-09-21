package com.chat_socket.constant;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public interface TimeFormat {
    /** Every timestamp the API emits: UTC, 6 fraction digits (Postgres timestamptz precision), fixed width. */
    DateTimeFormatter UTC_MICROS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);
}
