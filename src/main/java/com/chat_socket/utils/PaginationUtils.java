package com.chat_socket.utils;

import com.chat_socket.dto.PaginationResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public final class PaginationUtils {
    private PaginationUtils() {}

    public static int resolveLimit(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null) return defaultLimit;
        if (limit < 1) return limit;
        return Math.min(limit, maxLimit);
    }

    public static int fetchLimit(int limit) {
        validateLimit(limit);
        return limit + 1;
    }

    public static LocalDateTime parseDateTimeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;

        String normalizedCursor = cursor.trim();
        try {
            return LocalDateTime.parse(normalizedCursor, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return OffsetDateTime.parse(normalizedCursor, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toLocalDateTime();
        }
    }

    public static String formatDateTimeCursor(LocalDateTime cursor) {
        return cursor.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public static <T> boolean hasNextPage(List<T> fetchedItems, int limit) {
        validateLimit(limit);
        return fetchedItems.size() > limit;
    }

    public static <T> List<T> pageItems(List<T> fetchedItems, int limit) {
        validateLimit(limit);
        if (!hasNextPage(fetchedItems, limit)) return fetchedItems;
        return fetchedItems.subList(0, limit);
    }

    public static <T, R> PaginationResponse<R> toCursorResponse(
            List<T> fetchedItems,
            int limit,
            Function<T, R> mapper,
            Function<T, LocalDateTime> cursorExtractor,
            boolean reverseItems) {
        validateLimit(limit);
        boolean hasNextPage = hasNextPage(fetchedItems, limit);
        List<T> pageItems = pageItems(fetchedItems, limit);
        String nextCursor = hasNextPage ? formatDateTimeCursor(cursorExtractor.apply(pageItems.getLast())) : null;

        List<R> items = new ArrayList<>(pageItems.stream().map(mapper).toList());
        if (reverseItems) Collections.reverse(items);

        return new PaginationResponse<>(items, nextCursor);
    }

    private static void validateLimit(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0.");
    }
}
