package com.chat_socket.utils;

import com.chat_socket.dto.UserPair;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

public class Normalize {
    public static UserPair normalizeUserPair(UUID firstUserId, UUID secondUserId) {
        if (firstUserId.toString().compareTo(secondUserId.toString()) <= 0)
            return new UserPair(firstUserId, secondUserId);

        return new UserPair(secondUserId, firstUserId);
    }

    public static String normalizeFullName(String firstName, String lastName) {
        String fullName = String.join(" ", valueOrBlank(firstName), valueOrBlank(lastName));
        return normalizeSearchText(fullName);
    }

    public static String normalizeSearchText(String value) {
        if (value == null || value.isBlank()) return "";

        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT);

        return normalized.replaceAll("\\s+", "");
    }

    public static String normalizeTextPattern(String value) {
        if (value == null || value.isBlank()) return null;

        String normalized = normalizeSearchText(value);
        if (normalized.isBlank()) return null;

        return "%" + escapeLikePattern(normalized) + "%";
    }

    public static String normalizeUsernamePattern(String value) {
        if (value == null || value.isBlank()) return null;

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return null;

        return "%" + escapeLikePattern(normalized) + "%";
    }

    private static String valueOrBlank(String value) {
        return value == null ? "" : value.trim();
    }

    private static String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
