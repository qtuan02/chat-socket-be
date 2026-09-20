package com.chat_socket.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.chat_socket.dto.UserPair;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NormalizeTest {

    @Test
    void normalizeUserPair_ordersBySmallerUuidStringFirst() {
        UUID small = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID big = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertThat(Normalize.normalizeUserPair(big, small)).isEqualTo(new UserPair(small, big));
        assertThat(Normalize.normalizeUserPair(small, big)).isEqualTo(new UserPair(small, big));
    }

    @Test
    void normalizeSearchText_stripsAccentsSpacesAndLowercases() {
        assertThat(Normalize.normalizeSearchText("  Nguyễn Văn Đức ")).isEqualTo("nguyenvanduc");
        assertThat(Normalize.normalizeSearchText("Đình")).isEqualTo("dinh");
    }

    @Test
    void normalizeSearchText_nullOrBlank_returnsEmpty() {
        assertThat(Normalize.normalizeSearchText(null)).isEmpty();
        assertThat(Normalize.normalizeSearchText("   ")).isEmpty();
    }

    @Test
    void normalizeFullName_joinsAndNormalizes() {
        assertThat(Normalize.normalizeFullName("Trần", " Thị  Hoa ")).isEqualTo("tranthihoa");
        assertThat(Normalize.normalizeFullName(null, "Hoa")).isEqualTo("hoa");
    }

    @Test
    void normalizeTextPattern_escapesLikeWildcardsAndWraps() {
        assertThat(Normalize.normalizeTextPattern("a%b_c\\d")).isEqualTo("%a\\%b\\_c\\\\d%");
    }

    @Test
    void normalizeTextPattern_blank_returnsNull() {
        assertThat(Normalize.normalizeTextPattern(null)).isNull();
        assertThat(Normalize.normalizeTextPattern("  ")).isNull();
    }

    @Test
    void normalizeUsernamePattern_lowercasesTrimsAndKeepsSpaces() {
        assertThat(Normalize.normalizeUsernamePattern("  AbC d ")).isEqualTo("%abc d%");
        assertThat(Normalize.normalizeUsernamePattern("  ")).isNull();
    }
}
