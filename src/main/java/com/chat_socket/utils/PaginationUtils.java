package com.chat_socket.utils;

import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.exception.BadRequestException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;

public final class PaginationUtils {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private PaginationUtils() {}

    public record CursorPage(int limit, LocalDateTime cursor, PageRequest pageRequest) {
        public <T> List<T> items(List<T> fetchedItems) {
            if (fetchedItems.size() <= limit) return fetchedItems;
            return fetchedItems.subList(0, limit);
        }
    }

    public static CursorPage resolveCursorPage(PaginationRequest request) {
        CursorPage page;

        try {
            int limit = request == null || request.limit() == null ? DEFAULT_LIMIT : request.limit();
            if (limit < 1) throw new IllegalArgumentException("Limit must be greater than 0.");

            limit = Math.min(limit, MAX_LIMIT);
            LocalDateTime cursor = parseDateTimeCursor(request == null ? null : request.cursor());
            page = new CursorPage(limit, cursor, PageRequest.of(0, limit + 1));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Cursor is invalid.");
        }

        return page;
    }

    private static LocalDateTime parseDateTimeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;

        String normalizedCursor = cursor.trim();
        try {
            return LocalDateTime.parse(normalizedCursor, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return OffsetDateTime.parse(normalizedCursor, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toLocalDateTime();
        }
    }

    private static String formatDateTimeCursor(LocalDateTime cursor) {
        return cursor.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public static <T, R> PaginationResponse<R> toCursorResponse(
            List<T> fetchedItems,
            CursorPage page,
            Function<T, R> mapper,
            Function<T, LocalDateTime> cursorExtractor,
            boolean reverseItems) {
        boolean hasNextPage = fetchedItems.size() > page.limit();
        List<T> pageItems = page.items(fetchedItems);
        String nextCursor = hasNextPage ? formatDateTimeCursor(cursorExtractor.apply(pageItems.getLast())) : null;

        List<R> items = new ArrayList<>(pageItems.stream().map(mapper).toList());
        if (reverseItems) Collections.reverse(items);

        return new PaginationResponse<>(items, nextCursor);
    }
}
