package com.chat_socket.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.exception.BadRequestException;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PaginationUtilsTest {

    @Test
    void resolveCursorPage_nullRequest_usesDefaultLimitAndFetchesOneExtra() {
        PaginationUtils.CursorPage page = PaginationUtils.resolveCursorPage(null);

        assertThat(page.limit()).isEqualTo(50);
        assertThat(page.cursor()).isNull();
        assertThat(page.pageRequest().getPageSize()).isEqualTo(51);
    }

    @Test
    void resolveCursorPage_limitAboveMax_isCappedAt100() {
        PaginationUtils.CursorPage page = PaginationUtils.resolveCursorPage(new PaginationRequest(500, null, null));

        assertThat(page.limit()).isEqualTo(100);
    }

    @Test
    void resolveCursorPage_limitZero_throwsBadRequest() {
        assertThatThrownBy(() -> PaginationUtils.resolveCursorPage(new PaginationRequest(0, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Limit must be greater than 0.");
    }

    @Test
    void resolveCursorPage_parsesUtcAndOffsetCursor() {
        assertThat(PaginationUtils.resolveCursorPage(new PaginationRequest(10, "2026-01-01T12:00:00Z", null))
                        .cursor())
                .isEqualTo(Instant.parse("2026-01-01T12:00:00Z"));
        assertThat(PaginationUtils.resolveCursorPage(new PaginationRequest(10, "2026-01-01T19:00:00+07:00", null))
                        .cursor())
                .isEqualTo(Instant.parse("2026-01-01T12:00:00Z"));
        assertThat(PaginationUtils.resolveCursorPage(new PaginationRequest(10, "2026-01-01T12:00:00.123456Z", null))
                        .cursor())
                .isEqualTo(Instant.parse("2026-01-01T12:00:00.123456Z"));
    }

    @Test
    void resolveCursorPage_localCursorWithoutZone_throwsBadRequest() {
        assertThatThrownBy(
                        () -> PaginationUtils.resolveCursorPage(new PaginationRequest(10, "2026-01-01T12:00:00", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cursor is invalid.");
    }

    @Test
    void resolveCursorPage_invalidCursor_throwsBadRequest() {
        assertThatThrownBy(() -> PaginationUtils.resolveCursorPage(new PaginationRequest(10, "yesterday", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cursor is invalid.");
    }

    @Test
    void toCursorResponse_moreThanLimit_trimsAndSetsNextCursorFromLastKeptItem() {
        PaginationUtils.CursorPage page = PaginationUtils.resolveCursorPage(new PaginationRequest(2, null, null));
        List<Instant> fetched = List.of(
                Instant.parse("2026-01-03T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));

        PaginationResponse<String> response =
                PaginationUtils.toCursorResponse(fetched, page, Instant::toString, Function.identity(), false);

        assertThat(response.items()).containsExactly("2026-01-03T00:00:00Z", "2026-01-02T00:00:00Z");
        assertThat(response.nextCursor()).isEqualTo("2026-01-02T00:00:00.000000Z");
        assertThat(response.nextOffset()).isNull();
    }

    @Test
    void toCursorResponse_reverseItems_reversesOrderAndKeepsCursorFromNewestFetchOrder() {
        PaginationUtils.CursorPage page = PaginationUtils.resolveCursorPage(new PaginationRequest(5, null, null));
        List<Instant> fetched = List.of(Instant.parse("2026-01-02T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));

        PaginationResponse<String> response =
                PaginationUtils.toCursorResponse(fetched, page, Instant::toString, Function.identity(), true);

        assertThat(response.items()).containsExactly("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z");
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void resolveOffsetPage_negativeOffset_throwsBadRequest() {
        assertThatThrownBy(() -> PaginationUtils.resolveOffsetPage(new PaginationRequest(10, null, -1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Offset must be greater than or equal to 0.");
    }

    @Test
    void resolveOffsetPage_buildsPageableWithOffsetAndLimitPlusOne() {
        PaginationUtils.OffsetPage page = PaginationUtils.resolveOffsetPage(new PaginationRequest(10, null, 20));

        assertThat(page.offset()).isEqualTo(20);
        assertThat(page.limit()).isEqualTo(10);
        assertThat(page.pageRequest().getOffset()).isEqualTo(20);
        assertThat(page.pageRequest().getPageSize()).isEqualTo(11);
    }

    @Test
    void toOffsetResponse_moreThanLimit_setsNextOffset() {
        PaginationUtils.OffsetPage page = PaginationUtils.resolveOffsetPage(new PaginationRequest(2, null, 4));

        PaginationResponse<Integer> response = PaginationUtils.toOffsetResponse(List.of(1, 2, 3), page, i -> i * 10);

        assertThat(response.items()).containsExactly(10, 20);
        assertThat(response.nextOffset()).isEqualTo(6);
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void toOffsetResponse_exactlyLimit_noNextOffset() {
        PaginationUtils.OffsetPage page = PaginationUtils.resolveOffsetPage(new PaginationRequest(2, null, 0));

        PaginationResponse<Integer> response = PaginationUtils.toOffsetResponse(List.of(1, 2), page, i -> i);

        assertThat(response.nextOffset()).isNull();
    }
}
